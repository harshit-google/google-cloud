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
package io.cdap.plugin.slt.actions;

import io.cdap.e2e.utils.ElementHelper;
import io.cdap.e2e.utils.SeleniumDriver;
import io.cdap.e2e.utils.SeleniumHelper;
import io.cdap.plugin.slt.locators.SLTLocators;
import io.cdap.plugin.slt.utils.CDAPUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.io.IOException;

/**
 * SLT Actions.
 */
public class SLTActions {
    public static SLTLocators sltLocators = new SLTLocators();
    private static WebDriver CDFDriver;
    private static ElementHelper elementHelper = new ElementHelper();

    static {
        CDFDriver = SeleniumDriver.getDriver();
        sltLocators = SeleniumHelper.getPropertiesLocators(SLTLocators.class);

    }
    public static void name_fill(String name) {
        sltLocators.name.sendKeys(name);
    }
    public void required_config(String gcpProjectId,
                                String gcsDataPath,
                                String massTransferId,
                                String sapJcoLibGcsPath,
                                String jcoclienthost,
                                String jcoclient,
                                String jcoclientsysnr,
                                String jcoclientuser,
                                String jcoclientpasswd) throws IOException {
        elementHelper.replaceElementValue(sltLocators.gcpProjectId,
                CDAPUtils.getPluginProp(gcpProjectId));
        elementHelper.sendKeys(sltLocators.gcsDataPath, CDAPUtils.getPluginProp(gcsDataPath));
        elementHelper.sendKeys(sltLocators.massTransferId, massTransferId);
        elementHelper.sendKeys(sltLocators.sapJcoLibGcsPath, CDAPUtils.getPluginProp(sapJcoLibGcsPath));
        elementHelper.sendKeys(sltLocators.jcoclienthost, CDAPUtils.getPluginProp(jcoclienthost));
        elementHelper.sendKeys(sltLocators.jcoclient, CDAPUtils.getPluginProp(jcoclient));
        elementHelper.sendKeys(sltLocators.jcoclientsysnr,CDAPUtils.getPluginProp(jcoclientsysnr));
        elementHelper.sendKeys(sltLocators.jcoclientuser, CDAPUtils.getPluginProp(jcoclientuser));
        elementHelper.sendKeys(sltLocators.jcoclientpasswd, CDAPUtils.getPluginProp(jcoclientpasswd));
    }
    public void sltplugin_click() {
        elementHelper.clickOnElement(sltLocators.sltplugin);
    }

    public String rowError_color() {
        return elementHelper.getElementCssProperty(SLTLocators.rowError, "border-color");
    }

    public void next_click() {
        elementHelper.clickOnElement(sltLocators.next);
    }

    public void select_table(String tableName) {
        elementHelper.clickOnElement(sltLocators.table(tableName));
    }

    public void datasetName_fill (String stagingBucket) {
        elementHelper.sendKeys(sltLocators.datasetName, stagingBucket);
    }

    public void deployPipelineClick() {
        elementHelper.clickOnElement(sltLocators.deployPipeline);
    }

    public void startPipeline() {
        elementHelper.clickIfDisplayed(sltLocators.start, 60l);
    }

    public void stopPipeline() {
        elementHelper.clickOnElement(sltLocators.stop);
    }

    public void replicateExistingDataClick() {
        elementHelper.clickOnElement(sltLocators.replicateExistingData);
    }

    public String replicateExistingDataGetText() {
        return elementHelper.getElementText(sltLocators.replicateExistingData);
    }

    public void suspendSltJobClick() {
        elementHelper.clickOnElement(sltLocators.suspendSltJob);
    }

    public String suspendSltJobGetText() {
        return elementHelper.getElementText(sltLocators.suspendSltJob);
    }
}
