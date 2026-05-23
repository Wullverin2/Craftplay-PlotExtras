package de.craftplay.plotextras.storage;

import java.util.Locale;

public enum StorageType {
    YAML,
    SQLITE,
    MYSQL,
    MARIADB,
    POSTGRESQL,
    REDIS,
    MONGODB;

    public static StorageType parse(final String value) {
        if (value == null || value.trim().isEmpty()) {
            return YAML;
        }
        final String normalized = value.trim()
                .replace("-", "")
                .replace("_", "")
                .toUpperCase(Locale.ROOT);
        if ("POSTGRES".equals(normalized) || "POSTGRESQL".equals(normalized)) {
            return POSTGRESQL;
        }
        if ("MONGO".equals(normalized) || "MONGODB".equals(normalized)) {
            return MONGODB;
        }
        if ("MARIA".equals(normalized) || "MARIADB".equals(normalized)) {
            return MARIADB;
        }
        return StorageType.valueOf(normalized);
    }
}
