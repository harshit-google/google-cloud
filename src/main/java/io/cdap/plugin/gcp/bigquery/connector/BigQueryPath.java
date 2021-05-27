/*
 *
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

package io.cdap.plugin.gcp.bigquery.connector;

import javax.annotation.Nullable;

/**
 * BigQuery Path that parses the path in the request of connection service
 */
public class BigQueryPath {
  private String dataset;
  private String table;
  private static final int NAME_MAX_LENGTH = 1024;
  private static final String VALID_NAME_REGEX = "[\\w]+";

  public BigQueryPath(String path) {
    parsePath(path);
  }

  private void parsePath(String path) {
    if (path == null) {
      throw new IllegalArgumentException("Path should not be null.");
    }

    //remove heading "/" if exists
    if (path.startsWith("/")) {
      path = path.substring(1);
    }

    // both "" and "/" are taken as root path
    if (path.isEmpty()) {
      return;
    }

    String[] parts = path.split("/", -1);

    // path should contain at most two part : dataset and table
    // if path ends with / , it's allowed and the last part will be empty string
    if (parts.length == 3 && !parts[2].isEmpty() || parts.length > 3) {
      throw new IllegalArgumentException("Path should at most contain two parts.");
    }

    dataset = parts[0];
    validateName("Dataset" , dataset);

    // if path ends with "/" , e.g. "dataset/" we allow it
    if (parts.length == 2 && !path.endsWith("/") || parts.length > 2) {
      table = parts[1];
      validateName("Table", table);
    }
  }


  /**
   * The dataset and table name must contain only letters, numbers, and underscores.
   * And it must be 1024 characters or fewer.
   */
  private void validateName(String property, String name) {
    if (name.isEmpty()) {
      throw new IllegalArgumentException(
        String.format("%s should not be empty.", property));
    }
    if (name.length() > NAME_MAX_LENGTH) {
      throw new IllegalArgumentException(
        String.format("%s is invalid, it should contain at most %d characters.", property, NAME_MAX_LENGTH));
    }
    if (!name.matches(VALID_NAME_REGEX)) {
      throw new IllegalArgumentException(
        String.format("%s is invalid, it should contain only letters, numbers, and underscores.", property));
    }
  }

  @Nullable
  public String getDataset() {
    return dataset;
  }

  @Nullable
  public String getTable() {
    return table;
  }

  public boolean isRoot() {
    return dataset == null && table == null;
  }
}