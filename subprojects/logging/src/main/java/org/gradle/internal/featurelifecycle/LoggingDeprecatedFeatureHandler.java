/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.featurelifecycle;

import org.gradle.internal.SystemProperties;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.logging.ConsoleRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class LoggingDeprecatedFeatureHandler implements DeprecatedFeatureHandler {
    public static final String RENDER_REPORT_SYSTEM_PROPERTY = "org.gradle.internal.deprecation.report";

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingDeprecatedFeatureHandler.class);
    private static final String ELEMENT_PREFIX = "\tat ";
    private final Map<String, DeprecatedFeatureUsage> deprecationUsages = new HashMap<String, DeprecatedFeatureUsage>();
    private UsageLocationReporter locationReporter;

    public LoggingDeprecatedFeatureHandler() {
        this(new UsageLocationReporter() {
            public void reportLocation(DeprecatedFeatureUsage usage, StringBuilder target) {
            }
        });
    }

    public LoggingDeprecatedFeatureHandler(UsageLocationReporter locationReporter) {
        this.locationReporter = locationReporter;
    }

    public void setLocationReporter(UsageLocationReporter locationReporter) {
        this.locationReporter = locationReporter;
    }

    public void deprecatedFeatureUsed(DeprecatedFeatureUsage usage) {
        if (!deprecationUsages.containsKey(usage.getMessage())) {
            usage = usage.withStackTrace();
            deprecationUsages.put(usage.getMessage(), usage);
        }
    }

    public void renderDeprecationReport(File reportLocation) {
        if (deprecationUsages.isEmpty()) {
            return;
        }
        if (!shouldRenderReport()) {
            LOGGER.warn("\nThere are {} deprecation warnings.", deprecationUsages.size());
            return;
        }

        StringBuilder report = new StringBuilder(load("/templates/deprecation-report.template"));
        String div = load("/templates/deprecation-report-div.template");

        replace(report, "${warnings}", renderWarnings(div));

        writeToFile(report.toString(), reportLocation);
        LOGGER.warn("\nThere are {} deprecation warnings. See the detailed report at: {}", deprecationUsages.size(), new ConsoleRenderer().asClickableFileUrl(reportLocation));
    }

    private void writeToFile(String content, File file) {
        try {
            Files.createDirectories(file.getParentFile().toPath());
            Files.write(file.toPath(), content.getBytes("UTF-8"));
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private boolean shouldRenderReport() {
        return "true".equals(System.getProperty(RENDER_REPORT_SYSTEM_PROPERTY, "true"));
    }

    private String renderWarnings(String divTemplate) {
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (Map.Entry<String, DeprecatedFeatureUsage> entry : deprecationUsages.entrySet()) {
            StringBuilder div = new StringBuilder(divTemplate);
            replace(div, "${message}", entry.getKey());
            replace(div, "${index}", index);
            replace(div, "${index}", index);
            replace(div, "${stacktrace}", getStacktrace(entry.getValue()));
            index++;
            sb.append(div.toString());
        }
        return sb.toString();
    }

    private String getStacktrace(DeprecatedFeatureUsage usage) {
        StringBuilder sb = new StringBuilder();
        reportLocation(usage, sb);
        sb.append(usage.getMessage());
        appendLogTraceIfNecessary(usage.getStack(), sb);
        return sb.toString();
    }

    private void reportLocation(DeprecatedFeatureUsage usage, StringBuilder message) {
        locationReporter.reportLocation(usage, message);
        if (message.length() > 0) {
            message.append(SystemProperties.getInstance().getLineSeparator());
        }
    }

    private void replace(StringBuilder sb, String target, Object replacement) {
        int startIndex = sb.indexOf(target);
        int endIndex = startIndex + target.length();
        sb.replace(startIndex, endIndex, replacement.toString());
    }

    private String load(String resourceName) {
        try {
            return new Scanner(getClass().getResourceAsStream(resourceName)).useDelimiter("\\A").next();
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private static void appendLogTraceIfNecessary(List<StackTraceElement> stack, StringBuilder message) {
        final String lineSeparator = SystemProperties.getInstance().getLineSeparator();

        // append full stack trace
        for (StackTraceElement frame : stack) {
            appendStackTraceElement(frame, message, lineSeparator);
        }
        return;
    }

    private static void appendStackTraceElement(StackTraceElement frame, StringBuilder message, String lineSeparator) {
        message.append(lineSeparator);
        message.append(ELEMENT_PREFIX);
        message.append(frame.toString());
    }
}
