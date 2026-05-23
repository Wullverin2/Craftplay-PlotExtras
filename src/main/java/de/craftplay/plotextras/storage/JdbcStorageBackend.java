package de.craftplay.plotextras.storage;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

public final class JdbcStorageBackend implements StorageBackend {

    private final JavaPlugin plugin;
    private final StorageType type;
    private final String table;
    private String jdbcUrl;
    private String username;
    private String password;

    public JdbcStorageBackend(final JavaPlugin plugin, final StorageType type) {
        this.plugin = plugin;
        this.type = type;
        this.table = sanitize(plugin.getConfig().getString("storage.table-prefix", "cpe_")) + "data";
    }

    @Override
    public StorageType getType() {
        return type;
    }

    @Override
    public void initialize() {
        configure();
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table
                    + " (namespace VARCHAR(128) PRIMARY KEY, payload " + payloadType()
                    + " NOT NULL, updated_at BIGINT NOT NULL)");
        } catch (final SQLException exception) {
            throw new StorageException("Datenbanktabelle konnte nicht vorbereitet werden: " + table, exception);
        }
    }

    @Override
    public YamlConfiguration load(final String namespace, final String yamlFile) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT payload FROM " + table + " WHERE namespace = ?")) {
            statement.setString(1, namespace);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return new YamlConfiguration();
                }
                return fromString(resultSet.getString("payload"));
            }
        } catch (final SQLException exception) {
            throw new StorageException("Daten konnten nicht aus " + type + " geladen werden: " + namespace, exception);
        }
    }

    @Override
    public void save(final String namespace, final String yamlFile, final YamlConfiguration configuration) {
        final String sql;
        if (type == StorageType.POSTGRESQL) {
            sql = "INSERT INTO " + table + " (namespace, payload, updated_at) VALUES (?, ?, ?) "
                    + "ON CONFLICT (namespace) DO UPDATE SET payload = EXCLUDED.payload, updated_at = EXCLUDED.updated_at";
        } else if (type == StorageType.SQLITE) {
            sql = "INSERT OR REPLACE INTO " + table + " (namespace, payload, updated_at) VALUES (?, ?, ?)";
        } else {
            sql = "INSERT INTO " + table + " (namespace, payload, updated_at) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE payload = VALUES(payload), updated_at = VALUES(updated_at)";
        }
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, namespace);
            statement.setString(2, configuration.saveToString());
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw new StorageException("Daten konnten nicht in " + type + " gespeichert werden: " + namespace, exception);
        }
    }

    @Override
    public void close() {
        // JDBC nutzt kurzlebige Verbindungen.
    }

    private void configure() {
        try {
            if (type == StorageType.SQLITE) {
                Class.forName("org.sqlite.JDBC");
                final File file = new File(plugin.getDataFolder(), plugin.getConfig().getString("storage.sqlite.file", "storage/database.db"));
                final File parent = file.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new StorageException("SQLite-Ordner konnte nicht erstellt werden: " + parent.getPath());
                }
                jdbcUrl = "jdbc:sqlite:" + file.getPath();
                username = "";
                password = "";
                return;
            }
            final String section = "storage." + type.name().toLowerCase(Locale.ROOT) + ".";
            final String host = plugin.getConfig().getString(section + "host", "127.0.0.1");
            final int port = plugin.getConfig().getInt(section + "port", defaultPort());
            final String database = plugin.getConfig().getString(section + "database", "craftplay_plotextras");
            username = plugin.getConfig().getString(section + "username", "");
            password = plugin.getConfig().getString(section + "password", "");
            if (type == StorageType.POSTGRESQL) {
                Class.forName("org.postgresql.Driver");
                jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;
            } else if (type == StorageType.MARIADB) {
                Class.forName("org.mariadb.jdbc.Driver");
                jdbcUrl = "jdbc:mariadb://" + host + ":" + port + "/" + database
                        + "?useUnicode=true&characterEncoding=utf8&useSsl="
                        + plugin.getConfig().getBoolean(section + "use-ssl", false);
            } else {
                Class.forName("com.mysql.cj.jdbc.Driver");
                jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                        + "?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL="
                        + plugin.getConfig().getBoolean(section + "use-ssl", false);
            }
        } catch (final ClassNotFoundException exception) {
            throw new StorageException("Datenbanktreiber fehlt für " + type + ".", exception);
        }
    }

    private Connection connection() throws SQLException {
        if (username == null || username.isEmpty()) {
            return DriverManager.getConnection(jdbcUrl);
        }
        return DriverManager.getConnection(jdbcUrl, username, password == null ? "" : password);
    }

    private int defaultPort() {
        if (type == StorageType.POSTGRESQL) {
            return 5432;
        }
        return 3306;
    }

    private String payloadType() {
        if (type == StorageType.MYSQL || type == StorageType.MARIADB) {
            return "LONGTEXT";
        }
        return "TEXT";
    }

    private YamlConfiguration fromString(final String payload) {
        final YamlConfiguration configuration = new YamlConfiguration();
        if (payload == null || payload.trim().isEmpty()) {
            return configuration;
        }
        try {
            configuration.loadFromString(payload);
            return configuration;
        } catch (final InvalidConfigurationException exception) {
            throw new StorageException("Gespeicherte YAML-Daten sind ungültig.", exception);
        }
    }

    private String sanitize(final String value) {
        final String raw = value == null ? "" : value;
        final StringBuilder builder = new StringBuilder();
        for (int index = 0; index < raw.length(); index++) {
            final char character = raw.charAt(index);
            if ((character >= 'a' && character <= 'z')
                    || (character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9')
                    || character == '_') {
                builder.append(character);
            }
        }
        return builder.length() == 0 ? "cpe_" : builder.toString();
    }
}
