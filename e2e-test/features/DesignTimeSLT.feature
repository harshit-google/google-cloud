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
Feature: Design Time SLT Scenario

  @SLT @Design-Time @Sanity
  Scenario Outline: User is able to provides input to SLT parameters
    Given Open CDF replication and initiate pipeline creation
    When Source is SAP SLT
    Then User is able to set SLT parameter "<option>" as "<input>"
    Examples:
      | option            | input         |
      | gcsDataPath       | invalid       |
      | guid              | invalidguid   |
      | massTransferId    | ZZZ66         |
      | sapJcoLibGcsPath  |gs://ga_slt_cdf|
      | jco.client.ashost | invalid       |
      | jco.client.sysnr  | abc           |
      | jco.client.client | abc           |
      | jco.client.lang   | invalid       |
      | jco.client.user   | invalid       |
      | jco.client.passwd | invalid       |

  @SLT @Design-Time @Sanity
  Scenario: User is able to set Replicate Existing Data as false
    Given Open CDF replication and initiate pipeline creation
    When Source is SAP SLT
    Then Replicate Existing Data is set to false

  @SLT @Design-Time @Sanity
  Scenario: User is able to set suspend Slt Job as true
    Given Open CDF replication and initiate pipeline creation
    When Source is SAP SLT
    Then Suspend Slt Job is set to true

  @SLT @Design-Time @Sanity
  Scenario Outline: User is able to configured job for "<table>"
    Given User crates new MTID on "Automation" SAP
    When User updates mtid config in CDF_R_SLT_SETTINGS program
    Then Update mass transfer id table: "<table>" job mode to "replication"
    Given Open CDF replication and initiate pipeline creation
    When Source is SAP SLT fill connection parameters
    Then Click on next
    Then Select Table "<table>"
    Then Click on next
    Then Enter the BigQuery Properties for slt datasource
    Then Click on next
    Then Click on next
    Then Click on next
    Then Click on Deploy Replication Pipeline
    Then Update mass transfer id table: "<table>" job mode to "stop_load"
    Examples:
      | table |
      | vbap |
      | ekko  |
