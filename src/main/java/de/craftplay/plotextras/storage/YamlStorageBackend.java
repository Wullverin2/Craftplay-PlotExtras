package de.craftplay.plotextras.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public final class YamlStorageBackend implements StorageBackend {

    private final JavaPlugin plugin;

    public YamlStorageBackend(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public StorageType getType() {
        return StorageType.YAML;
    }

    @Override
    public void initialize() {
        // YAML benötigt keine Verbindung.
    }

    @Override
    public YamlConfiguration load(final String namespace, final String yamlFile) {
        final File file = file(yamlFile);
        if (!file.exists()) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public void save(final String namespace, final String yamlFile, final YamlConfiguration configuration) {
        final File file = file(yamlFile);
        final File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new StorageException("YAML-Ordner konnte nicht erstellt werden: " + parent.getPath());
        }
        try {
            configuration.save(file);
        } catch (final IOException exception) {
            throw new StorageException("YAML-Daten konnten nicht gespeichert werden: " + file.getPath(), exception);
        }
    }

    @Override
    public void close() {
        // YAML benötigt keine Verbindung.
    }

    private File file(final String yamlFile) {
        return new File(plugin.getDataFolder(), yamlFile == null || yamlFile.trim().isEmpty() ? "data.yml" : yamlFile);
    }
}
