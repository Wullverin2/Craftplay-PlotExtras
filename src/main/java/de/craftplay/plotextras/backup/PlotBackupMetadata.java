package de.craftplay.plotextras.backup;

import de.craftplay.plotextras.plotsquared.PlotContext;
import de.craftplay.plotextras.plotsquared.PlotRegion;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PlotBackupMetadata {

    private final String id;
    private final String schematicFileName;
    private final String createdAt;
    private final String action;
    private final UUID createdByUuid;
    private final String createdByName;
    private final UUID ownerUuid;
    private final String ownerName;
    private final String worldName;
    private final String plotId;
    private final List<String> plotIds;
    private final String mergeType;
    private final PlotRegion bounds;

    public PlotBackupMetadata(
            final String id,
            final String schematicFileName,
            final String createdAt,
            final String action,
            final UUID createdByUuid,
            final String createdByName,
            final UUID ownerUuid,
            final String ownerName,
            final String worldName,
            final String plotId,
            final List<String> plotIds,
            final String mergeType,
            final PlotRegion bounds
    ) {
        this.id = id;
        this.schematicFileName = schematicFileName;
        this.createdAt = createdAt;
        this.action = action;
        this.createdByUuid = createdByUuid;
        this.createdByName = createdByName;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.worldName = worldName;
        this.plotId = plotId;
        this.plotIds = plotIds == null ? new ArrayList<>() : new ArrayList<>(plotIds);
        this.mergeType = mergeType;
        this.bounds = bounds;
    }

    public static PlotBackupMetadata create(
            final String id,
            final String schematicFileName,
            final String action,
            final UUID createdByUuid,
            final String createdByName,
            final PlotContext context
    ) {
        return new PlotBackupMetadata(
                id,
                schematicFileName,
                LocalDateTime.now().toString(),
                action,
                createdByUuid,
                createdByName,
                context.getOwnerUuid(),
                context.getOwnerName(),
                context.getWorldName(),
                context.getPlotId(),
                context.getPlotIds(),
                context.getMergeType(),
                context.getBounds()
        );
    }

    public void save(final File file) throws IOException {
        final YamlConfiguration configuration = new YamlConfiguration();
        configuration.options().header("Metadaten zu einem Plotbackup.\n"
                + "Die .schem-Datei enthält die Blöcke und Entities, diese Datei enthält die Informationen für GUI und Wiederherstellung.");
        writeTo(configuration, "");
        configuration.save(file);
    }

    public static PlotBackupMetadata load(final File file) {
        final YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        return load(configuration, "", file.getName().replace(".yml", ""));
    }

    public void writeTo(final YamlConfiguration configuration, final String path) {
        configuration.set(path + "file-version", 1);
        configuration.set(path + "id", id);
        configuration.set(path + "schematic-file", schematicFileName);
        configuration.set(path + "created-at", createdAt);
        configuration.set(path + "action", action);
        configuration.set(path + "created-by.uuid", createdByUuid == null ? "" : createdByUuid.toString());
        configuration.set(path + "created-by.name", createdByName);
        configuration.set(path + "plot-owner.uuid", ownerUuid == null ? "" : ownerUuid.toString());
        configuration.set(path + "plot-owner.name", ownerName);
        configuration.set(path + "plot.world", worldName);
        configuration.set(path + "plot.id", plotId);
        configuration.set(path + "plot.ids", plotIds);
        configuration.set(path + "plot.merge-type", mergeType);
        if (bounds != null) {
            configuration.set(path + "plot.bounds.min-x", bounds.getMinX());
            configuration.set(path + "plot.bounds.min-y", bounds.getMinY());
            configuration.set(path + "plot.bounds.min-z", bounds.getMinZ());
            configuration.set(path + "plot.bounds.max-x", bounds.getMaxX());
            configuration.set(path + "plot.bounds.max-y", bounds.getMaxY());
            configuration.set(path + "plot.bounds.max-z", bounds.getMaxZ());
        }
    }

    public static PlotBackupMetadata load(
            final YamlConfiguration configuration,
            final String path,
            final String fallbackId
    ) {
        final ConfigurationSection boundsSection = configuration.getConfigurationSection(path + "plot.bounds");
        final PlotRegion bounds = boundsSection == null ? null : new PlotRegion(
                configuration.getString(path + "plot.world", ""),
                boundsSection.getInt("min-x"),
                boundsSection.getInt("min-y"),
                boundsSection.getInt("min-z"),
                boundsSection.getInt("max-x"),
                boundsSection.getInt("max-y"),
                boundsSection.getInt("max-z")
        );
        return new PlotBackupMetadata(
                configuration.getString(path + "id", fallbackId),
                configuration.getString(path + "schematic-file", ""),
                configuration.getString(path + "created-at", ""),
                configuration.getString(path + "action", ""),
                uuid(configuration.getString(path + "created-by.uuid", "")),
                configuration.getString(path + "created-by.name", ""),
                uuid(configuration.getString(path + "plot-owner.uuid", "")),
                configuration.getString(path + "plot-owner.name", ""),
                configuration.getString(path + "plot.world", ""),
                configuration.getString(path + "plot.id", ""),
                configuration.getStringList(path + "plot.ids"),
                configuration.getString(path + "plot.merge-type", ""),
                bounds
        );
    }

    private static UUID uuid(final String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (final IllegalArgumentException ignored) {
            return null;
        }
    }

    public String getId() {
        return id;
    }

    public String getSchematicFileName() {
        return schematicFileName;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getAction() {
        return action;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getWorldName() {
        return worldName;
    }

    public String getPlotId() {
        return plotId;
    }

    public List<String> getPlotIds() {
        return new ArrayList<>(plotIds);
    }

    public String getMergeType() {
        return mergeType;
    }

    public PlotRegion getBounds() {
        return bounds;
    }
}
