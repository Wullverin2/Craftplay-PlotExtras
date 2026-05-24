package de.craftplay.plotextras.storage;

import de.craftplay.plotextras.backup.PlotBackupMetadata;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

public final class StorageService {

    private final JavaPlugin plugin;
    private StorageBackend activeBackend;
    private String activeStorageFingerprint;

    public StorageService(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void reload() {
        final StorageType type = currentType();
        final String fingerprint = storageFingerprint(type);
        if (activeBackend != null && activeBackend.getType() == type && fingerprint.equals(activeStorageFingerprint)) {
            return;
        }
        close();
        activeBackend = createBackend(type);
        activeBackend.initialize();
        activeStorageFingerprint = fingerprint;
        plugin.getLogger().info("Storage-Backend aktiv: " + activeBackend.getType().name().toLowerCase(Locale.ROOT));
    }

    public synchronized YamlConfiguration load(final String namespace, final String yamlFile) {
        ensureBackend();
        return activeBackend.load(namespace, yamlFile);
    }

    public synchronized void save(final String namespace, final String yamlFile, final YamlConfiguration configuration) {
        ensureBackend();
        activeBackend.save(namespace, yamlFile, configuration);
    }

    public synchronized boolean migrate(final CommandSender sender, final String sourceTypeName, final String targetTypeName) {
        final StorageType sourceType;
        final StorageType targetType;
        try {
            sourceType = StorageType.parse(sourceTypeName);
            targetType = StorageType.parse(targetTypeName);
        } catch (final IllegalArgumentException exception) {
            sender.sendMessage("§cUnbekannter Storage-Typ. Erlaubt: yaml, sqlite, mysql, mariadb, postgresql, redis, mongodb");
            return true;
        }
        if (sourceType == targetType) {
            sender.sendMessage("§cQuelle und Ziel sind gleich.");
            return true;
        }

        try (StorageBackend source = createBackend(sourceType); StorageBackend target = createBackend(targetType)) {
            source.initialize();
            target.initialize();
            int migrated = 0;
            for (final StorageNamespace namespace : namespaces()) {
                YamlConfiguration configuration = source.load(namespace.getId(), namespace.getYamlFile());
                if ("plotbackups".equals(namespace.getId())) {
                    configuration = withLegacyBackupMetadata(configuration);
                }
                target.save(namespace.getId(), namespace.getYamlFile(), configuration);
                migrated++;
            }
            sender.sendMessage("§aStorage-Migration abgeschlossen: §f" + sourceType.name().toLowerCase(Locale.ROOT)
                    + " §7-> §f" + targetType.name().toLowerCase(Locale.ROOT)
                    + "§7, Bereiche: §f" + migrated);
        } catch (final RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Storage-Migration ist fehlgeschlagen.", exception);
            sender.sendMessage("§cStorage-Migration fehlgeschlagen: " + exception.getMessage());
        }
        return true;
    }

    public synchronized void close() {
        if (activeBackend != null) {
            activeBackend.close();
            activeBackend = null;
        }
        activeStorageFingerprint = null;
    }

    public List<StorageNamespace> namespaces() {
        final List<StorageNamespace> namespaces = new ArrayList<>();
        namespaces.add(new StorageNamespace("plotdata", plugin.getConfig().getString("my-plots.data-file", "plotdata.yml")));
        namespaces.add(new StorageNamespace("plotpurchase", plugin.getConfig().getString("plot-purchase.data-file", "plotpurchase.yml")));
        namespaces.add(new StorageNamespace("reports", plugin.getConfig().getString("reports.data-file", "reports.yml")));
        namespaces.add(new StorageNamespace("plotroles", plugin.getConfig().getString("roles.data-file", "plotroles.yml")));
        namespaces.add(new StorageNamespace("teamdata", plugin.getConfig().getString("team-features.data-file", "teamdata.yml")));
        namespaces.add(new StorageNamespace("futurefeatures", plugin.getConfig().getString("future.data-file", "futurefeatures.yml")));
        namespaces.add(new StorageNamespace("extras", plugin.getConfig().getString("extras.data-file", "extras.yml")));
        namespaces.add(new StorageNamespace("plotbackups", plugin.getConfig().getString("plot-backups.metadata-file", "Plotbackups/metadata.yml")));
        return namespaces;
    }

    public YamlConfiguration withLegacyBackupMetadata(final YamlConfiguration configuration) {
        if (configuration.getConfigurationSection("backups") != null) {
            return configuration;
        }
        final File backupFolder = new File(plugin.getDataFolder(), plugin.getConfig().getString("plot-backups.folder", "Plotbackups"));
        final File[] files = backupFolder.listFiles((directory, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) {
            return configuration;
        }
        configuration.set("file-version", Math.max(1, configuration.getInt("file-version", 1)));
        for (final File file : files) {
            try {
                final PlotBackupMetadata metadata = PlotBackupMetadata.load(file);
                metadata.writeTo(configuration, "backups." + metadata.getId() + ".");
            } catch (final RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Legacy-Plotbackup-Metadaten konnten nicht importiert werden: " + file.getPath(), exception);
            }
        }
        return configuration;
    }

    private StorageType currentType() {
        return StorageType.parse(plugin.getConfig().getString("storage.type", "yaml"));
    }

    private String storageFingerprint(final StorageType type) {
        final StringBuilder builder = new StringBuilder(type.name());
        builder.append("|table-prefix=").append(plugin.getConfig().getString("storage.table-prefix", "cpe_"));
        final ConfigurationSection section = plugin.getConfig().getConfigurationSection("storage."
                + type.name().toLowerCase(Locale.ROOT));
        if (section == null) {
            return builder.toString();
        }
        final Map<String, Object> values = new TreeMap<>(section.getValues(true));
        for (final Map.Entry<String, Object> entry : values.entrySet()) {
            builder.append('|').append(entry.getKey()).append('=').append(String.valueOf(entry.getValue()));
        }
        return builder.toString();
    }

    private StorageBackend createBackend(final StorageType type) {
        if (type == StorageType.YAML) {
            return new YamlStorageBackend(plugin);
        }
        if (type == StorageType.SQLITE || type == StorageType.MYSQL
                || type == StorageType.MARIADB || type == StorageType.POSTGRESQL) {
            return new JdbcStorageBackend(plugin, type);
        }
        if (type == StorageType.REDIS) {
            return new RedisStorageBackend(plugin);
        }
        if (type == StorageType.MONGODB) {
            return new MongoStorageBackend(plugin);
        }
        throw new StorageException("Nicht unterstützter Storage-Typ: " + type);
    }

    private void ensureBackend() {
        if (activeBackend == null) {
            reload();
        }
    }
}
