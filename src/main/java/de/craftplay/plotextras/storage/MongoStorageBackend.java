package de.craftplay.plotextras.storage;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class MongoStorageBackend implements StorageBackend {

    private final JavaPlugin plugin;
    private MongoClient client;
    private MongoCollection<Document> collection;

    public MongoStorageBackend(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public StorageType getType() {
        return StorageType.MONGODB;
    }

    @Override
    public void initialize() {
        final String uri = plugin.getConfig().getString("storage.mongodb.uri", "mongodb://127.0.0.1:27017");
        final String database = plugin.getConfig().getString("storage.mongodb.database", "craftplay_plotextras");
        final String collectionName = plugin.getConfig().getString("storage.mongodb.collection", "data");
        client = MongoClients.create(uri);
        collection = client.getDatabase(database).getCollection(collectionName);
        collection.createIndex(new Document("_id", 1));
    }

    @Override
    public YamlConfiguration load(final String namespace, final String yamlFile) {
        final Document document = collection.find(Filters.eq("_id", namespace)).first();
        if (document == null) {
            return new YamlConfiguration();
        }
        return fromString(document.getString("payload"));
    }

    @Override
    public void save(final String namespace, final String yamlFile, final YamlConfiguration configuration) {
        final Document document = new Document("_id", namespace)
                .append("payload", configuration.saveToString())
                .append("updatedAt", System.currentTimeMillis());
        collection.replaceOne(Filters.eq("_id", namespace), document, new ReplaceOptions().upsert(true));
    }

    @Override
    public void close() {
        if (client != null) {
            client.close();
            client = null;
        }
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
