package com.example.screenrecorder;

import java.io.InputStream;
import java.util.Properties;

final class UpdateConfig {
    private static final String SYSTEM_PROPERTY = "jsr.update.url";
    private static final String ENVIRONMENT_VARIABLE = "JSR_UPDATE_URL";
    private static final String RESOURCE = "/update.properties";

    private UpdateConfig() {}

    static String manifestUrl() {
        String value = System.getProperty(SYSTEM_PROPERTY, "").trim();
        if (!value.isBlank()) return value;

        value = System.getenv(ENVIRONMENT_VARIABLE);
        if (value != null && !value.isBlank()) return value.trim();

        try (InputStream in = UpdateConfig.class.getResourceAsStream(RESOURCE)) {
            if (in == null) return "";
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty("manifest.url", "").trim();
        } catch (Exception ignored) {
            return "";
        }
    }
}
