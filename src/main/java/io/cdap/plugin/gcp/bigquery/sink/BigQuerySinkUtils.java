/*
 * Copyright © 2021 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.cdap.plugin.gcp.bigquery.sink;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.Dataset;
import com.google.cloud.bigquery.DatasetId;
import com.google.cloud.bigquery.DatasetInfo;
import com.google.cloud.bigquery.EncryptionConfiguration;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.LegacySQLTypeName;
import com.google.cloud.bigquery.Table;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.hadoop.io.bigquery.BigQueryFileFormat;
import com.google.cloud.hadoop.io.bigquery.output.BigQueryOutputConfiguration;
import com.google.cloud.hadoop.io.bigquery.output.BigQueryTableFieldSchema;
import com.google.cloud.hadoop.io.bigquery.output.BigQueryTableSchema;
import com.google.cloud.kms.v1.CryptoKeyName;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.plugin.gcp.bigquery.util.BigQueryConstants;
import io.cdap.plugin.gcp.bigquery.util.BigQueryTypeSize.Numeric;
import io.cdap.plugin.gcp.bigquery.util.BigQueryUtil;
import io.cdap.plugin.gcp.common.GCPUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Utility class for the BigQuery DelegatingMultiSink.
 *
 * The logic in this class has been extracted from the {@link AbstractBigQuerySink} in order to make this functionality
 * available to other classes in this package.
 */
public final class BigQuerySinkUtils {

  public static final String GS_PATH_FORMAT = "gs://%s/%s";
  private static final String TEMPORARY_BUCKET_FORMAT = GS_PATH_FORMAT + "/input/%s-%s";
  private static final String DATETIME = "DATETIME";
  private static final String RECORD = "RECORD";
  private static final Gson GSON = new Gson();
  private static final Type LIST_OF_FIELD_TYPE = new TypeToken<ArrayList<Field>>() { }.getType();

  // Fields used to build update/upsert queries
  private static final String CRITERIA_TEMPLATE = "T.%s = S.%s";
  private static final String SOURCE_DATA_QUERY = "(SELECT * FROM (SELECT row_number() OVER (PARTITION BY %s%s) " +
    "as rowid, * FROM %s) where rowid = 1)";
  private static final String UPDATE_QUERY = "UPDATE %s T SET %s FROM %s S WHERE %s";
  private static final String UPSERT_QUERY = "MERGE %s T USING %s S ON %s WHEN MATCHED THEN UPDATE SET %s " +
    "WHEN NOT MATCHED THEN INSERT (%s) VALUES(%s)";
  private static final List<String> COMPARISON_OPERATORS =
    Arrays.asList("=", "<", ">", "<=", ">=", "!=", "<>",
                  "LIKE", "NOT LIKE", "BETWEEN", "NOT BETWEEN", "IN", "NOT IN", "IS NULL", "IS NOT NULL",
                  "IS TRUE", "IS NOT TRUE", "IS FALSE", "IS NOT FALSE");

  /**
   * Creates the given dataset and bucket if they do not already exist. If the dataset already exists but the
   * bucket does not, the bucket will be created in the same location as the dataset. If the bucket already exists
   * but the dataset does not, the dataset will attempt to be created in the same location. This may fail if the bucket
   * is in a location that BigQuery does not yet support.
   *
   * @param bigQuery the bigquery client for the project
   * @param storage the storage client for the project
   * @param datasetId the Id of the dataset
   * @param bucketName the name of the bucket
   * @param location the location of the resources, this is only applied if both the bucket and dataset do not exist
   * @param cmekKeyName the name of the cmek key
   * @throws IOException if there was an error creating or fetching any GCP resource
   */
  public static void createResources(BigQuery bigQuery, Storage storage,
                                     DatasetId datasetId, String bucketName, @Nullable String location,
                                     @Nullable CryptoKeyName cmekKeyName) throws IOException {
    Dataset dataset = bigQuery.getDataset(datasetId);
    Bucket bucket = storage.get(bucketName);

    if (dataset == null && bucket == null) {
      createBucket(storage, bucketName, location, cmekKeyName,
                   () -> String.format("Unable to create Cloud Storage bucket '%s'", bucketName));
      createDataset(bigQuery, datasetId, location, cmekKeyName,
                    () -> String.format("Unable to create BigQuery dataset '%s.%s'", datasetId.getProject(),
                                        datasetId.getDataset()));
    } else if (bucket == null) {
      createBucket(
        storage, bucketName, dataset.getLocation(), cmekKeyName,
        () -> String.format(
          "Unable to create Cloud Storage bucket '%s' in the same location ('%s') as BigQuery dataset '%s'. "
            + "Please use a bucket that is in the same location as the dataset.",
          bucketName, dataset.getLocation(), datasetId.getProject() + "." + datasetId.getDataset()));
    } else if (dataset == null) {
      createDataset(
        bigQuery, datasetId, bucket.getLocation(), cmekKeyName,
        () -> String.format(
          "Unable to create BigQuery dataset '%s' in the same location ('%s') as Cloud Storage bucket '%s'. "
            + "Please use a bucket that is in a supported location.",
          datasetId, bucket.getLocation(), bucketName));
    }
  }

  /**
   * Creates a Dataset in the specified location using the supplied BigQuery client.
   * @param bigQuery the bigQuery client.
   * @param dataset the Id of the dataset to create.
   * @param location Location for this dataset.
   * @param cmekKeyName CMEK key to use for this dataset.
   * @param errorMessage Supplier for the error message to output if the dataset could not be created.
   * @throws IOException if the dataset could not be created.
   */
  private static void createDataset(BigQuery bigQuery, DatasetId dataset, @Nullable String location,
                                   @Nullable CryptoKeyName cmekKeyName,
                                   Supplier<String> errorMessage) throws IOException {
    DatasetInfo.Builder builder = DatasetInfo.newBuilder(dataset);
    if (location != null) {
      builder.setLocation(location);
    }
    if (cmekKeyName != null) {
      builder.setDefaultEncryptionConfiguration(
        EncryptionConfiguration.newBuilder().setKmsKeyName(cmekKeyName.toString()).build());
    }
    try {
      bigQuery.create(builder.build());
    } catch (BigQueryException e) {
      if (e.getCode() != 409) {
        // A conflict means the dataset already exists (https://cloud.google.com/bigquery/troubleshooting-errors)
        // This most likely means multiple stages in the same pipeline are trying to create the same dataset.
        // Ignore this and move on, since all that matters is that the dataset exists.
        throw new IOException(errorMessage.get(), e);
      }
    }
  }

  /**
   * Creates a Dataset in the specified location using the supplied BigQuery client if it does not exist.
   * @param bigQuery the bigQuery client.
   * @param datasetId the Id of the dataset to create.
   * @param location Location for this dataset.
   * @param cmekKeyName CMEK key to use for this dataset.
   * @param errorMessage Supplier for the error message to output if the dataset could not be created.
   * @throws IOException if the dataset could not be created.
   */
  public static void createDatasetIfNotExists(BigQuery bigQuery, DatasetId datasetId, @Nullable String location,
                                              @Nullable CryptoKeyName cmekKeyName,
                                              Supplier<String> errorMessage) throws IOException {
    // Check if dataset exists
    Dataset ds = bigQuery.getDataset(datasetId);
    // Create dataset if needed
    if (ds == null) {
      createDataset(bigQuery, datasetId, location, cmekKeyName, errorMessage);
    }
  }

  /**
   * Creates the specified GCS bucket using the supplied GCS client.
   * @param storage GCS Client.
   * @param bucket Bucket Name.
   * @param location Location for this bucket.
   * @param cmekKeyName CMEK key to use for this bucket.
   * @param errorMessage Supplier for the error message to output if the bucket could not be created.
   * @throws IOException if the bucket could not be created.
   */
  private static void createBucket(Storage storage, String bucket, @Nullable String location,
                                   @Nullable CryptoKeyName cmekKeyName,
                                   Supplier<String> errorMessage) throws IOException {
    try {
      GCPUtils.createBucket(storage, bucket, location, cmekKeyName);
    } catch (StorageException e) {
      if (e.getCode() != 409) {
        // A conflict means the bucket already exists
        // This most likely means multiple stages in the same pipeline are trying to create the same dataset.
        // Ignore this and move on, since all that matters is that the dataset exists.
        throw new IOException(errorMessage.get(), e);
      }
    }
  }

  /**
   * Updates {@link Configuration} with bucket details.
   * Uses provided bucket, otherwise uses provided runId as a bucket name.
   *
   * @return bucket name
   */
  public static String configureBucket(Configuration baseConfiguration, @Nullable String bucket, String runId) {
    boolean deleteBucket = false;
    // If the bucket is null, assign the run ID as the bucket name and mark the bucket for deletion.
    if (bucket == null) {
      bucket = runId;
      deleteBucket = true;
    }
    return configureBucket(baseConfiguration, bucket, runId, deleteBucket);
  }

  /**
   * Updates {@link Configuration} with bucket details.
   * Uses provided bucket, otherwise uses provided runId as a bucket name.
   *
   * @return bucket name
   */
  public static String configureBucket(Configuration baseConfiguration,
                                       String bucket,
                                       String runId,
                                       boolean deleteBucket) {
    if (deleteBucket) {
      // By default, this option is false, meaning the job can not delete the bucket.
      // So enable it only when bucket name is not provided.
      baseConfiguration.setBoolean("fs.gs.bucket.delete.enable", true);
    }
    baseConfiguration.set("fs.default.name", String.format(GS_PATH_FORMAT, bucket, runId));
    baseConfiguration.setBoolean("fs.gs.impl.disable.cache", true);
    baseConfiguration.setBoolean("fs.gs.metadata.cache.enable", false);
    return bucket;
  }

  /**
   * Configures output for Sink
   *
   * @param configuration Hadoop configuration instance
   * @param datasetId id of the dataset to use
   * @param tableName name of the table to use
   * @param gcsPath GCS path to use for output
   * @param fields list of BigQuery table fields
   * @throws IOException if the output cannot be configured
   */
  public static void configureOutput(Configuration configuration,
                                     DatasetId datasetId,
                                     String tableName,
                                     String gcsPath,
                                     List<BigQueryTableFieldSchema> fields) throws IOException {

    // Set up table schema
    BigQueryTableSchema outputTableSchema = new BigQueryTableSchema();
    if (!fields.isEmpty()) {
      outputTableSchema.setFields(fields);
    }

    BigQueryFileFormat fileFormat = getFileFormat(fields);
    BigQueryOutputConfiguration.configure(
      configuration,
      String.format("%s:%s.%s", datasetId.getProject(), datasetId.getDataset(), tableName),
      outputTableSchema,
      gcsPath,
      fileFormat,
      getOutputFormat(fileFormat));
  }

  /**
   * Configures output for MultiSink
   *
   * @param configuration Hadoop configuration instance
   * @param datasetId name of the dataset to use
   * @param tableName name of the table to use
   * @param gcsPath GCS path to use for output
   * @param fields list of BigQuery table fields
   * @throws IOException if the output cannot be configured
   */
  public static void configureMultiSinkOutput(Configuration configuration,
                                              DatasetId datasetId,
                                              String tableName,
                                              String gcsPath,
                                              List<BigQueryTableFieldSchema> fields) throws IOException {
    configureOutput(configuration,
                    datasetId,
                    tableName,
                    gcsPath,
                    fields);

    // Set operation as Insertion. Currently the BQ MultiSink can only support the insertion operation.
    configuration.set(BigQueryConstants.CONFIG_OPERATION, Operation.INSERT.name());
  }

  public static String getTemporaryGcsPath(String bucket, String pathPrefix, String tableName) {
    return String.format(TEMPORARY_BUCKET_FORMAT, bucket, pathPrefix, tableName, pathPrefix);
  }

  public static List<BigQueryTableFieldSchema> getBigQueryTableFieldsFromSchema(Schema tableSchema) {
    List<Schema.Field> inputFields = Objects.requireNonNull(tableSchema.getFields(), "Schema must have fields");
    return inputFields.stream()
      .map(BigQuerySinkUtils::generateTableFieldSchema)
      .collect(Collectors.toList());
  }

  /**
   * Relaxes the Destination Table Schema based on the matching field names from the source table
   * @param bigquery BigQuery client
   * @param sourceTable source table, which contains the updated field definition
   * @param destinationTable destination table, whose fields definitions may be relaxed depending on the source table.
   */
  public static void relaxTableSchema(BigQuery bigquery,
                                      Table sourceTable,
                                      Table destinationTable) {
    List<Field> sourceFields = sourceTable.getDefinition().getSchema().getFields();
    List<Field> destinationFields = destinationTable.getDefinition().getSchema().getFields();

    relaxTableSchema(bigquery, destinationTable, sourceFields, destinationFields);
  }


  /**
   * Relaxes the Destination Table Schema based on the matching field names from the source table
   * @param bigquery BigQuery client
   * @param destinationTable destination table, whose fields definitions may be relaxed depending on the source fields.
   * @param sourceFields fields in the source table that need to be used to relax the destination table
   * @param destinationFields fields in the destination table that may be relaxed depending on the source fields
   */
  public static void relaxTableSchema(BigQuery bigquery,
                                      Table destinationTable,
                                      List<Field> sourceFields,
                                      List<Field> destinationFields) {
    // Collect all fields form the source table
    Map<String, Field> sourceFieldMap = sourceFields.stream()
      .collect(Collectors.toMap(Field::getName, x -> x));

    // Collects all fields in the destination table that are not present in the source table, in order to retain them
    // as-is in the destination schema
    List<Field> resultFieldsList = destinationFields.stream()
      .filter(field -> !sourceFieldMap.containsKey(field.getName()))
      .collect(Collectors.toList());

    // Add fields from the source table into the destination table
    resultFieldsList.addAll(sourceFields);

    // Update table definition, relaxing field definitions.
    com.google.cloud.bigquery.Schema newSchema = com.google.cloud.bigquery.Schema.of(resultFieldsList);
    bigquery.update(
      destinationTable.toBuilder().setDefinition(
        destinationTable.getDefinition().toBuilder().setSchema(newSchema).build()
      ).build()
    );
  }

  private static BigQueryTableFieldSchema generateTableFieldSchema(Schema.Field field) {
    BigQueryTableFieldSchema fieldSchema = new BigQueryTableFieldSchema();
    fieldSchema.setName(field.getName());
    fieldSchema.setMode(getMode(field.getSchema()).name());
    LegacySQLTypeName type = getTableDataType(field.getSchema());
    fieldSchema.setType(type.name());
    if (type == LegacySQLTypeName.RECORD) {
      List<Schema.Field> schemaFields;
      Schema fieldCdapSchema = BigQueryUtil.getNonNullableSchema(field.getSchema());

      // If its an Array of records we need to get the component schema of the array
      // which will be the Record. Which can itself be nullable, and then get the fields
      // of that record.
      if (Schema.Type.ARRAY == fieldCdapSchema.getType()) {
        schemaFields = Objects.requireNonNull(
            BigQueryUtil.getNonNullableSchema(fieldCdapSchema.getComponentSchema()).getFields());
      } else {
        schemaFields = fieldCdapSchema.getFields();
      }
      fieldSchema.setFields(Objects.requireNonNull(schemaFields).stream()
          .map(BigQuerySinkUtils::generateTableFieldSchema)
          .collect(Collectors.toList()));

    }
    return fieldSchema;
  }

  public static com.google.cloud.bigquery.Schema convertCdapSchemaToBigQuerySchema(Schema schema) {
    List<Schema.Field> inputFields = Objects.requireNonNull(schema.getFields(), "Schema must have fields");
    List<com.google.cloud.bigquery.Field> fields = inputFields.stream()
      .map(BigQuerySinkUtils::convertCdapFieldToBigQueryField)
      .collect(Collectors.toList());
    return com.google.cloud.bigquery.Schema.of(fields);
  }

  private static Field convertCdapFieldToBigQueryField(Schema.Field field) {
    String name = field.getName();
    LegacySQLTypeName type = getTableDataType(field.getSchema());
    Field.Mode mode = getMode(field.getSchema());

    Field.Builder fieldBuilder;

    // For record fields, we need to get all subfields and re-create the builder.
    if (type == LegacySQLTypeName.RECORD) {
      List<Schema.Field> schemaFields;
      Schema fieldCdapSchema = BigQueryUtil.getNonNullableSchema(field.getSchema());

      // If its an Array of records we need to get the component schema of the array
      // which will be the Record. Which can itself be nullable, and then get the fields
      // of that record.
      if (Schema.Type.ARRAY == fieldCdapSchema.getType()) {
        schemaFields = Objects.requireNonNull(
          BigQueryUtil.getNonNullableSchema(fieldCdapSchema.getComponentSchema()).getFields());
      } else {
        schemaFields = fieldCdapSchema.getFields();
      }

      FieldList subFields = FieldList.of(Objects.requireNonNull(schemaFields).stream()
                                           .map(BigQuerySinkUtils::convertCdapFieldToBigQueryField)
                                           .collect(Collectors.toList()));

      fieldBuilder = Field.newBuilder(name, type, subFields);
    } else {
      fieldBuilder = Field.newBuilder(name, type);
    }

    fieldBuilder.setMode(mode);

    // Set precision for numeric fields
    if (type == LegacySQLTypeName.NUMERIC || type == LegacySQLTypeName.BIGNUMERIC) {
      Schema decimalFieldSchema = BigQueryUtil.getNonNullableSchema(field.getSchema());
      fieldBuilder.setPrecision((long) decimalFieldSchema.getPrecision());
      fieldBuilder.setScale((long) decimalFieldSchema.getScale());
    }

    return fieldBuilder.build();
  }

  private static Field.Mode getMode(Schema schema) {
    boolean isNullable = schema.isNullable();
    Schema.Type nonNullableType = isNullable ? schema.getNonNullable().getType() : schema.getType();
    if (isNullable && nonNullableType != Schema.Type.ARRAY) {
      return Field.Mode.NULLABLE;
    } else if (nonNullableType == Schema.Type.ARRAY) {
      return Field.Mode.REPEATED;
    }
    return Field.Mode.REQUIRED;
  }

  /**
   * This function returns the LegacySQLTypeName that maps to the given CDAP Schema.
   * If the CDAP Schema is an Array it will return the LegacySQLTypename of the components.
   */
  private static LegacySQLTypeName getTableDataType(Schema schema) {
    schema = BigQueryUtil.getNonNullableSchema(schema);
    Schema.LogicalType logicalType = schema.getLogicalType();

    if (logicalType != null) {
      switch (logicalType) {
        case DATE:
          return LegacySQLTypeName.DATE;
        case TIME_MILLIS:
        case TIME_MICROS:
          return LegacySQLTypeName.TIME;
        case TIMESTAMP_MILLIS:
        case TIMESTAMP_MICROS:
          return LegacySQLTypeName.TIMESTAMP;
        case DECIMAL:
          // Following the restrictions given by:
          // https://cloud.google.com/bigquery/docs/reference/standard-sql/data-types#numeric_types
          if ((schema.getScale() <= Numeric.SCALE) && (schema.getPrecision() <= Numeric.PRECISION) &&
            ((schema.getPrecision() - schema.getScale()) <= (Numeric.PRECISION - Numeric.SCALE))) {
            return LegacySQLTypeName.NUMERIC;
          }
          return LegacySQLTypeName.BIGNUMERIC;
        case DATETIME:
          return LegacySQLTypeName.DATETIME;
        default:
          throw new IllegalStateException("Unsupported type " + logicalType.getToken());
      }
    }

    Schema.Type type = schema.getType();
    switch (type) {
      case INT:
      case LONG:
        return LegacySQLTypeName.INTEGER;
      case STRING:
        return LegacySQLTypeName.STRING;
      case FLOAT:
      case DOUBLE:
        return LegacySQLTypeName.FLOAT;
      case BOOLEAN:
        return LegacySQLTypeName.BOOLEAN;
      case BYTES:
        return LegacySQLTypeName.BYTES;
      case ARRAY:
        return getTableDataType(schema.getComponentSchema());
      case RECORD:
        return LegacySQLTypeName.RECORD;
      default:
        throw new IllegalStateException("Unsupported type " + type);
    }
  }

  private static BigQueryFileFormat getFileFormat(List<BigQueryTableFieldSchema> fields) {
    for (BigQueryTableFieldSchema field : fields) {
      if (DATETIME.equals(field.getType())) {
        return BigQueryFileFormat.NEWLINE_DELIMITED_JSON;
      }
      // If the field is a record we have to check its subfields.
      if (RECORD.equals(field.getType())) {
        if (getFileFormat(field.getFields()) == BigQueryFileFormat.NEWLINE_DELIMITED_JSON) {
          return BigQueryFileFormat.NEWLINE_DELIMITED_JSON;
        }
      }
    }
    return BigQueryFileFormat.AVRO;
  }

  private static Class<? extends FileOutputFormat> getOutputFormat(BigQueryFileFormat fileFormat) {
    if (fileFormat == BigQueryFileFormat.NEWLINE_DELIMITED_JSON) {
      return TextOutputFormat.class;
    }
    return AvroOutputFormat.class;
  }

  public static String generateUpdateUpsertQuery(Operation operation,
                                                 TableId sourceTableId,
                                                 TableId destinationTableId,
                                                 List<String> tableFieldsList,
                                                 List<String> tableKeyList,
                                                 List<String> orderedByList,
                                                 String partitionFilter) {

    String source = String.format("`%s.%s.%s`",
                                  sourceTableId.getProject(),
                                  sourceTableId.getDataset(),
                                  sourceTableId.getTable());
    String destination = String.format("`%s.%s.%s`",
                                       destinationTableId.getProject(),
                                       destinationTableId.getDataset(),
                                       destinationTableId.getTable());

    String criteria = tableKeyList.stream().map(s -> String.format(CRITERIA_TEMPLATE, s, s))
      .collect(Collectors.joining(" AND "));
    criteria = partitionFilter != null ? String.format("(%s) AND %s",
                                                       formatPartitionFilter(partitionFilter), criteria) : criteria;
    String fieldsForUpdate = tableFieldsList.stream().filter(s -> !tableKeyList.contains(s))
      .map(s -> String.format(CRITERIA_TEMPLATE, s, s)).collect(Collectors.joining(", "));
    String orderedBy = orderedByList.isEmpty() ? "" : " ORDER BY " + String.join(", ", orderedByList);
    String sourceTable = String.format(SOURCE_DATA_QUERY, String.join(", ", tableKeyList), orderedBy, source);
    switch (operation) {
      case UPDATE:
        return String.format(UPDATE_QUERY, destination, fieldsForUpdate, sourceTable, criteria);
      case UPSERT:
        String insertFields = String.join(", ", tableFieldsList);
        return String.format(UPSERT_QUERY, destination, sourceTable, criteria, fieldsForUpdate,
                             insertFields, insertFields);
      default:
        return "";
    }
  }

  private static String formatPartitionFilter(String partitionFilter) {
    String[] queryWords = partitionFilter.split(" ");
    int index = 0;
    for (String word: queryWords) {
      if (COMPARISON_OPERATORS.contains(word.toUpperCase())) {
        queryWords[index - 1] = queryWords[index - 1].replace(queryWords[index - 1],
                                                              "T." + queryWords[index - 1]);
      }
      index++;
    }
    return String.join(" ", queryWords);
  }
}
