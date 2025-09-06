package com.zenith.util.config;

import com.zenith.util.Wait;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;

import static com.zenith.Globals.*;
import static java.lang.reflect.Modifier.isStatic;

// Verifies that all fields in the loaded configs are not null
// gson will deserialize invalid json values to null
// but this will cause issues as we expect all fields to be non-null
public final class ConfigVerifier {
    private ConfigVerifier() {}

    public static void verifyConfigs() {
        if (containsNullFields(CONFIG, Config.class)) {
            failVerify("config");
        }
        if (containsNullFields(LAUNCH_CONFIG, LaunchConfig.class)) {
            failVerify("launch_config");
        }
        for (var config : PLUGIN_MANAGER.getAllPluginConfigs()) {
            if (containsNullFields(config.instance(), config.getClass())) {
                // we could drop this down to a warning
                // plugin authors may intend for null values
                failVerify("Plugin Config", config.file().getName());
            }
        }
    }

    private static void failVerify(String configName) {
        failVerify(configName, configName);
    }

    private static void failVerify(String id, String configName) {
        DEFAULT_LOG.error("{} verification failed: null values found", id);
        DEFAULT_LOG.error("{}.json must be manually fixed or deleted", configName);
        DEFAULT_LOG.error("Shutting down in 10s");
        Wait.wait(10);
        System.exit(1);
    }

    private static boolean containsNullFields(Object obj, Class<?> clazz) {
        // recursively check all fields for null
        Field[] declaredFields = clazz.getDeclaredFields();
        for (Field field : declaredFields) {
            try {
                if (field.getType().isPrimitive()) {
                    continue;
                }
                // skip static fields
                if (isStatic(field.getModifiers())) continue;
                // skip non-accessible fields
                if (!field.canAccess(obj)) continue;
                Object value = field.get(obj);
                if (value == null && !isNullableField(field)) {
                    DEFAULT_LOG.error("Field: '{}' in '{}' is null\nIf this is intended, annotate the field with: {}", field.getName(), clazz.getName(), Nullable.class.getName());
                    return true;
                }
                if (field.getType().getName().startsWith("com.zenith")
                    && !field.getType().isEnum()) {
                    if (containsNullFields(value, field.getType())) {
                        return true;
                    }
                }
            } catch (Throwable e) {
                DEFAULT_LOG.error("Error verifying config fields", e);
            }
        }
        return false;
    }

    private static boolean isNullableField(Field field) {
        return field.getAnnotatedType().getAnnotation(Nullable.class) != null;
    }
}
