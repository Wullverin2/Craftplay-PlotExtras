package de.craftplay.plotextras.storage;

import org.bukkit.configuration.file.YamlConfiguration;

public interface StorageBackend extends AutoCloseable {

    StorageType getType();

    void initialize();

    YamlConfiguration load(String namespace, String yamlFile);

    void save(String namespace, String yamlFile, YamlConfiguration configuration);

    @Override
    void close();
}
