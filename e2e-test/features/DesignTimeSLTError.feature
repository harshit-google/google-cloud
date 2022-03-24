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
Feature: Design Time SLT Negative Scenario

    @SLT @Design-Time @Sanity @Negative
    Scenario Outline: User provides wrong parameters and able to verify error messages
      Given Open CDF replication and initiate pipeline creation
      When User crates new MTID on "Automation" SAP
      When User updates mtid config in CDF_R_SLT_SETTINGS program
      When Source is SAP SLT fill connection parameters
      Then User is able to set SLT parameter <option> as <input> and getting row <errorMessage> for wrong input
      Examples:
        | option            | input         | errorMessage          |
        | gcsDataPath       | invalid       | invalidgcsDataPath    |
        | guid              | invalidguid   | invalidMTorGUID       |
        | massTransferId    | ZZZ66         | invalidMTorGUID       |
        | sapJcoLibGcsPath  |gs://ga_slt_cdf|invalidsapJcoLibGcsPath|
        | jco.client.ashost | invalid       | invalidIP             |
        | jco.client.sysnr  | abc           | invalidSysNr          |
        | jco.client.client | abc           | invalidClient         |
        | jco.client.lang   | invalid       | invalidLang           |
        | jco.client.user   | invalid       | invalidCred           |
        | jco.client.passwd | invalid       | invalidCred           |


