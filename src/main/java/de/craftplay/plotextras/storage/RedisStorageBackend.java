package de.craftplay.plotextras.storage;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.Jedis;

public final class RedisStorageBackend implements StorageBackend {

    private final JavaPlugin plugin;
    private String host;
    private int port;
    private int database;
    private int timeoutMillis;
    private String password;
    private String keyPrefix;

    public RedisStorageBackend(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public StorageType getType() {
        return StorageType.REDIS;
    }

    @Override
    public void initialize() {
        host = plugin.getConfig().getString("storage.redis.host", "127.0.0.1");
        port = plugin.getConfig().getInt("storage.redis.port", 6379);
        database = plugin.getConfig().getInt("storage.redis.database", 0);
        timeoutMillis = plugin.getConfig().getInt("storage.redis.timeout-millis", 2000);
        password = plugin.getConfig().getString("storage.redis.password", "");
        keyPrefix = plugin.getConfig().getString("storage.redis.key-prefix", "craftplayplotextras:");
        try (Jedis jedis = jedis()) {
            jedis.ping();
        }
    }

    @Override
    public YamlConfiguration load(final String namespace, final String yamlFile) {
        try (Jedis jedis = jedis()) {
            return fromString(jedis.get(key(namespace)));
        } catch (final RuntimeException exception) {
            throw new StorageException("Redis-Daten konnten nicht geladen werden: " + namespace, exception);
        }
    }

    @Override
    public void save(final String namespace, final String yamlFile, final YamlConfiguration configuration) {
        try (Jedis jedis = jedis()) {
            jedis.set(key(namespace), configuration.saveToString());
        } catch (final RuntimeException exception) {
            throw new StorageException("Redis-Daten konnten nicht gespeichert werden: " + namespace, exception);
        }
    }

    @Override
    public void close() {
        // Redis-Verbindungen werden pro Operation kurz geöffnet.
    }

    private Jedis jedis() {
        final Jedis jedis = new Jedis(host, port, timeoutMillis);
        if (password != null && !password.trim().isEmpty()) {
            jedis.auth(password);
        }
        if (database > 0) {
            jedis.select(database);
        }
        return jedis;
    }

    private String key(final String namespace) {
        return keyPrefix + namespace;
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
}
