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
package io.cdap.plugin.slt.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

/**
 * CDAPUtils.
 */
public class CDAPUtils {

  private static final Logger logger = Logger.getLogger(CDAPUtils.class);
  private static Properties errorProp = new Properties();
  public static Properties pluginProp = new Properties();

  static {
    try {
      errorProp.load(new FileInputStream("e2e-test/resources/error_message.properties"));
      pluginProp.load(new FileInputStream("e2e-test/resources/PluginParameters.properties"));
    } catch (IOException e) {
      logger.error("Error while reading file: " + e);
    }
  }

  public static String getPluginProp(String property) throws IOException {
    return pluginProp.getProperty(property);
  }

  public static void clearField(WebElement element) {
    while (!element.getAttribute("value").equals(StringUtils.EMPTY)) {
      element.sendKeys(Keys.BACK_SPACE);
    }
  }

  public static String getErrorProp(String errorMessage) {
    return errorProp.getProperty(errorMessage);
  }
}
