# Copyright © 2021 Cask Data, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.
Feature:Run-Time SLT Scenario

  @SLT @Run-Time @Sanity
  Scenario: User is able to create MTID and update CDF_R_SLT_SETTINGS program
    Given User crates new MTID on "Automation" SAP
    When User updates mtid config in CDF_R_SLT_SETTINGS program

  @SLT @Run-Time @Sanity
  Scenario Outline: User configured SLT job for table containing all data types
    Given User crates new MTID on "Automation" SAP
    When User updates mtid config in CDF_R_SLT_SETTINGS program
    Then Update mass transfer id table: "<table>" job mode to "stop_load"
    Given Open CDF replication and initiate pipeline creation
    When User fetches the record count of table : "<table>" from SAP
    Then Delete GCS folder for mass transfer id table "<table>"
    Then Drop target BigQuery table "<table>"
    Then Update mass transfer id table: "<table>" job mode to "<mode>"
    When Source is SAP SLT fill connection parameters
    Then Click on next
    Then Select Table "<table>"
    Then Click on next
    Then Enter the BigQuery Properties for slt datasource
    Then Click on next
    Then Click on next
    Then Click on next
    Then Click on Deploy Replication Pipeline
    Then Run the slt Pipeline in Runtime
    Then Open Logs of SLT Pipeline
    Then Wait till SLT pipeline is in running state and no error occurs
    Then Close logs and stop the pipeline
    Then Get Count of no of records transferred from SLT to BigQuery in "<table>"
    Then User is able to validate record count in BQ matches with count in SAP
    Examples:
      | table       | mode            |
      | ZDATA_TYPE  | replication     |

    @SLT @Run-Time @Sanity
    Scenario Outline: User configured SLT job in LTRC and executes pipeline in Initial Load Mode
    Given User crates new MTID on "Automation" SAP
    When User updates mtid config in CDF_R_SLT_SETTINGS program
    Then Update mass transfer id table: "<table>" job mode to "stop_load"
    Given Open CDF replication and initiate pipeline creation
    When User fetches the record count of table : "<table>" from SAP
    Then Delete GCS folder for mass transfer id table "<table>"
    Then Drop target BigQuery table "<table>"
    Then Update mass transfer id table: "<table>" job mode to "<mode>"
    When Source is SAP SLT fill connection parameters
    Then Click on next
    Then Select Table "<table>"
    Then Click on next
    Then Enter the BigQuery Properties for slt datasource
    Then Click on next
    Then Click on next
    Then Click on next
    Then Click on Deploy Replication Pipeline
    Then Run the slt Pipeline in Runtime
    Then Open Logs of SLT Pipeline
    Then Wait till SLT pipeline is in running state and no error occurs
    Then Close logs and stop the pipeline
    Then Get Count of no of records transferred from SLT to BigQuery in "<table>"
    Then User is able to validate record count in BQ matches with count in SAP
    Examples:
      | table   | mode            |
      | adr6    | initial_load    |
      | t001    | initial_load    |
      | t006a   | initial_load    |
      | t023t   | initial_load    |
      | t023    | initial_load    |


  @SLT @Run-Time
    Scenario Outline: User configured SLT job in LTRC and executes pipeline in Replication
    Given User crates new MTID on "Automation" SAP
    When User updates mtid config in CDF_R_SLT_SETTINGS program
    Then Update mass transfer id table: "<table>" job mode to "stop_load"
    Given Open CDF replication and initiate pipeline creation
    When User fetches the record count of table : "<table>" from SAP
    Then Delete GCS folder for mass transfer id table "<table>"
    Then Drop target BigQuery table "<table>"
    Then Update mass transfer id table: "<table>" job mode to "<mode>"
    When Source is SAP SLT fill connection parameters
    Then Click on next
    Then Select Table "<table>"
    Then Click on next
    Then Enter the BigQuery Properties for slt datasource
    Then Click on next
    Then Click on next
    Then Click on next
    Then Click on Deploy Replication Pipeline
    Then Run the slt Pipeline in Runtime
    Then Open Logs of SLT Pipeline
    Then Wait till SLT pipeline is in running state and no error occurs
    Then Get Count of no of records transferred from SLT to BigQuery in "<table>"
    Then User is able to validate record count in BQ matches with count in SAP
    Then "<cud_type>" the "<count>" records with "<rfc>" in the sap table
    Then Wait till SLT pipeline is in running state and no error occurs
    Then Close logs and stop the pipeline
    Then Get Count of no of records transferred from SLT to BigQuery in "<table>"
    Then User is able to validate record count in BQ matches with count in SAP
    Then "<cud_type>" the "<count>" records with "<rfc>" in the sap table
    Then Run the slt Pipeline in Runtime
    Then Open Logs of SLT Pipeline
    Then Wait till SLT pipeline is in running state and no error occurs
    Then Close logs and stop the pipeline
    When User fetches the record count of table : "<table>" from SAP
    Then Get Count of no of records transferred from SLT to BigQuery in "<table>"
    Then User is able to validate record count in BQ matches with count in SAP
    Then Update mass transfer id table: "<table>" job mode to "stop_load"
    Examples:
    | table   | mode            | count | cud_type| rfc             |
    | mara    | replication     | 1     | create  | rfc_matnr       |

  @SLT @Run-Time
  Scenario Outline: User is able to verify only replication data transfers when Replicate Existing Data is false
    Given User crates new MTID on "Automation" SAP
    When User updates mtid config in CDF_R_SLT_SETTINGS program
    Then Update mass transfer id table: "<table>" job mode to "stop_load"
    Given Open CDF replication and initiate pipeline creation
    When User fetches the record count of table : "<table>" from SAP
    Then Delete GCS folder for mass transfer id table "<table>"
    Then Drop target BigQuery table "<table>"
    Then Update mass transfer id table: "<table>" job mode to "<mode>"
    When Source is SAP SLT fill connection parameters
    Then Replicate Existing Data is set to false
    Then Click on next
    Then Select Table "<table>"
    Then Click on next
    Then Enter the BigQuery Properties for slt datasource
    Then Click on next
    Then Click on next
    Then Click on next
    Then Click on Deploy Replication Pipeline
    Then "<cud_type>" the "<count>" records with "<rfc>" in the sap table
    Then Run the slt Pipeline in Runtime
    Then Open Logs of SLT Pipeline
    Then Wait till SLT pipeline is in running state and no error occurs
    Then Close logs and stop the pipeline
    When User fetches the record count of table : "<table>" from SAP
    Then Get Count of no of records transferred from SLT to BigQuery in "<table>"
    Then User is able to validate only replication data is loaded ie "<count>"
    Then Update mass transfer id table: "<table>" job mode to "stop_load"
    Examples:
      | table   | mode            | count | cud_type| rfc             |
      | mara    | replication     | 1     | create  | rfc_matnr       |

  @SLT @Run-Time
  Scenario: User is able to delete MTID
  Then User deletes the mtid

