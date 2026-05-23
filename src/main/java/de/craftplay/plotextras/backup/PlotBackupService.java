package de.craftplay.plotextras.backup;

import de.craftplay.plotextras.CraftplayPlotExtrasPlugin;
import de.craftplay.plotextras.plotsquared.PlotContext;
import de.craftplay.plotextras.plotsquared.PlotRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class PlotBackupService {

    private static final DateTimeFormatter FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final CraftplayPlotExtrasPlugin plugin;
    private final SchematicAdapter schematicAdapter;
    private final Map<UUID, PendingOperation> pendingOperations = new HashMap<>();
    private final Set<UUID> bypassNextPlotCommand = new HashSet<>();
    private final List<PlotRegion> lockedRegions = new ArrayList<>();

    private boolean enabled;
    private boolean includeEntities;
    private boolean includeBiomes;
    private boolean pasteEntities;
    private boolean pasteBiomes;
    private boolean ignoreAirOnRestore;
    private boolean fullWorldHeight;
    private boolean autoConfirmPlotAction;
    private int fallbackMinY;
    private long unlockDelayTicks;
    private File backupFolder;
    private String metadataFile;
    private YamlConfiguration metadataConfiguration;

    public PlotBackupService(final CraftplayPlotExtrasPlugin plugin) {
        this.plugin = plugin;
        this.schematicAdapter = createAdapter();
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("plot-backups.enabled", true);
        includeEntities = plugin.getConfig().getBoolean("plot-backups.include-entities", true);
        includeBiomes = plugin.getConfig().getBoolean("plot-backups.include-biomes", true);
        pasteEntities = plugin.getConfig().getBoolean("plot-backups.restore.paste-entities", true);
        pasteBiomes = plugin.getConfig().getBoolean("plot-backups.restore.paste-biomes", true);
        ignoreAirOnRestore = plugin.getConfig().getBoolean("plot-backups.restore.ignore-air", false);
        fullWorldHeight = plugin.getConfig().getBoolean("plot-backups.full-world-height", true);
        fallbackMinY = plugin.getConfig().getInt("plot-backups.fallback-min-y", 0);
        autoConfirmPlotAction = plugin.getConfig().getBoolean("plot-backups.intercept.auto-run-plotsquared-confirm", true);
        unlockDelayTicks = Math.max(1L, plugin.getConfig().getLong("plot-backups.unlock-delay-ticks", 20L));
        backupFolder = new File(plugin.getDataFolder(), plugin.getConfig().getString("plot-backups.folder", "Plotbackups"));
        if (!backupFolder.exists() && !backupFolder.mkdirs()) {
            plugin.getLogger().warning("Plotbackup-Ordner konnte nicht erstellt werden: " + backupFolder.getPath());
        }
        metadataFile = plugin.getConfig().getString("plot-backups.metadata-file", "Plotbackups/metadata.yml");
        metadataConfiguration = plugin.getStorageService().load("plotbackups", metadataFile);
        final boolean hadBackupSection = metadataConfiguration.getConfigurationSection("backups") != null;
        metadataConfiguration = plugin.getStorageService().withLegacyBackupMetadata(metadataConfiguration);
        boolean changed = !hadBackupSection && metadataConfiguration.getConfigurationSection("backups") != null;
        if (metadataConfiguration.getInt("file-version", 0) < 1) {
            metadataConfiguration.set("file-version", 1);
            changed = true;
        }
        if (changed) {
            saveMetadata();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAvailable() {
        return schematicAdapter != null && schematicAdapter.isAvailable();
    }

    public boolean requestProtectedAction(final Player player, final String action, final String originalCommand) {
        final Optional<PlotContext> context = currentContext(player);
        if (!context.isPresent()) {
            plugin.getLanguageManager().send(player, "no-plot");
            return false;
        }

        pendingOperations.put(player.getUniqueId(), PendingOperation.plotAction(action, originalCommand));
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("action", actionName(action));
        placeholders.put("command", "/" + originalCommand);
        placeholders.put("prefix", commandPrefix());
        plugin.getLanguageManager().send(player, "backup-protected-action-start", placeholders);
        plugin.getLanguageManager().send(player, "backup-confirm-question", placeholders);
        return true;
    }

    public void requestManualBackup(final Player player) {
        if (!canCreateBackup(player)) {
            plugin.getLanguageManager().send(player, "backup-no-permission");
            return;
        }
        final PlotBackupMetadata metadata = createBackup(player, "manual");
        if (metadata != null) {
            final Map<String, String> placeholders = backupPlaceholders(metadata);
            plugin.getLanguageManager().send(player, "backup-created", placeholders);
        }
    }

    public void requestRestore(final Player player, final String backupId) {
        if (!player.hasPermission("craftplayplotextras.backup.restore")) {
            plugin.getLanguageManager().send(player, "backup-no-permission");
            return;
        }
        final Optional<PlotBackupMetadata> metadata = findBackup(backupId);
        if (!metadata.isPresent()) {
            plugin.getLanguageManager().send(player, "backup-not-found");
            return;
        }
        pendingOperations.put(player.getUniqueId(), PendingOperation.restore(backupId));
        final Map<String, String> placeholders = backupPlaceholders(metadata.get());
        placeholders.put("prefix", commandPrefix());
        plugin.getLanguageManager().send(player, "backup-restore-confirm", placeholders);
    }

    public boolean confirm(final Player player) {
        final PendingOperation operation = pendingOperations.remove(player.getUniqueId());
        if (operation == null) {
            plugin.getLanguageManager().send(player, "backup-no-pending-action");
            return true;
        }
        if (operation.getType() == PendingType.RESTORE) {
            restore(player, operation.getBackupId());
            return true;
        }

        final PlotBackupMetadata metadata = createBackup(player, operation.getAction());
        if (metadata == null) {
            return true;
        }
        plugin.getLanguageManager().send(player, "backup-created", backupPlaceholders(metadata));
        bypassNextPlotCommand.add(player.getUniqueId());
        player.performCommand(stripSlash(operation.getOriginalCommand()));
        if (autoConfirmPlotAction) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.performCommand("plot confirm");
                }
            }, 2L);
        }
        return true;
    }

    public boolean cancel(final Player player) {
        if (pendingOperations.remove(player.getUniqueId()) == null) {
            plugin.getLanguageManager().send(player, "backup-no-pending-action");
            return true;
        }
        plugin.getLanguageManager().send(player, "backup-action-cancelled");
        return true;
    }

    public boolean consumeBypass(final Player player) {
        return bypassNextPlotCommand.remove(player.getUniqueId());
    }

    public boolean isLocationLocked(final Location location) {
        if (location == null || lockedRegions.isEmpty()) {
            return false;
        }
        for (final PlotRegion region : lockedRegions) {
            if (region.contains(location)) {
                return true;
            }
        }
        return false;
    }

    public List<PlotBackupMetadata> listBackups() {
        final List<PlotBackupMetadata> backups = new ArrayList<>();
        if (metadataConfiguration == null) {
            return backups;
        }
        final ConfigurationSection section = metadataConfiguration.getConfigurationSection("backups");
        if (section == null) {
            return backups;
        }
        for (final String id : section.getKeys(false)) {
            try {
                backups.add(PlotBackupMetadata.load(metadataConfiguration, "backups." + id + ".", id));
            } catch (final RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Plotbackup-Metadaten konnten nicht geladen werden: " + id, exception);
            }
        }
        backups.sort(Comparator.comparing(PlotBackupMetadata::getCreatedAt, Comparator.nullsLast(String::compareTo)).reversed());
        return backups;
    }

    public Optional<PlotBackupMetadata> findBackup(final String backupId) {
        if (backupId == null || backupId.trim().isEmpty()) {
            return Optional.empty();
        }
        for (final PlotBackupMetadata metadata : listBackups()) {
            if (backupId.equalsIgnoreCase(metadata.getId())) {
                return Optional.of(metadata);
            }
        }
        return Optional.empty();
    }

    private PlotBackupMetadata createBackup(final Player player, final String action) {
        if (!enabled) {
            plugin.getLanguageManager().send(player, "backup-disabled");
            return null;
        }
        if (!isAvailable()) {
            plugin.getLanguageManager().send(player, "backup-worldedit-missing");
            return null;
        }
        final Optional<PlotContext> optionalContext = currentContext(player);
        if (!optionalContext.isPresent()) {
            plugin.getLanguageManager().send(player, "no-plot");
            return null;
        }
        final PlotContext context = optionalContext.get();
        if (!context.isComplete()) {
            plugin.getLanguageManager().send(player, "backup-plot-read-failed");
            return null;
        }

        plugin.getLanguageManager().send(player, "backup-started");
        final PlotRegion region = backupBounds(context);
        lock(region);
        try {
            final String id = uniqueBackupId(context, action);
            final String schematicFileName = id + ".schem";
            final File schematicFile = new File(backupFolder, schematicFileName);
            schematicAdapter.save(context.getWorld(), region, schematicFile, includeEntities, includeBiomes);
            final PlotBackupMetadata metadata = PlotBackupMetadata.create(
                    id,
                    schematicFileName,
                    action,
                    player.getUniqueId(),
                    player.getName(),
                    new PlotContext(
                            context.getWorld(),
                            context.getPlotId(),
                            context.getPlotIds(),
                            context.getOwnerUuid(),
                            context.getOwnerName(),
                            context.getMergeType(),
                            context.getRegions(),
                            region
                    )
            );
            metadata.writeTo(metadataConfiguration, "backups." + id + ".");
            saveMetadata();
            return metadata;
        } catch (final Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Plotbackup konnte nicht erstellt werden.", exception);
            plugin.getLanguageManager().send(player, "backup-failed");
            return null;
        } finally {
            unlockLater(region);
        }
    }

    private void restore(final Player player, final String backupId) {
        if (!isAvailable()) {
            plugin.getLanguageManager().send(player, "backup-worldedit-missing");
            return;
        }
        final Optional<PlotBackupMetadata> optionalMetadata = findBackup(backupId);
        if (!optionalMetadata.isPresent()) {
            plugin.getLanguageManager().send(player, "backup-not-found");
            return;
        }
        final Optional<PlotContext> optionalContext = currentContext(player);
        if (!optionalContext.isPresent()) {
            plugin.getLanguageManager().send(player, "no-plot");
            return;
        }
        final PlotContext context = optionalContext.get();
        final PlotRegion targetRegion = backupBounds(context);
        final PlotBackupMetadata metadata = optionalMetadata.get();
        final File schematicFile = new File(backupFolder, metadata.getSchematicFileName());
        if (!schematicFile.exists()) {
            plugin.getLanguageManager().send(player, "backup-schematic-missing", backupPlaceholders(metadata));
            return;
        }

        plugin.getLanguageManager().send(player, "backup-restore-started", backupPlaceholders(metadata));
        lock(targetRegion);
        try {
            schematicAdapter.restore(context.getWorld(), targetRegion, schematicFile, pasteEntities, pasteBiomes, ignoreAirOnRestore);
            plugin.getLanguageManager().send(player, "backup-restored", backupPlaceholders(metadata));
        } catch (final Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Plotbackup konnte nicht wiederhergestellt werden.", exception);
            plugin.getLanguageManager().send(player, "backup-restore-failed");
        } finally {
            unlockLater(targetRegion);
        }
    }

    private Optional<PlotContext> currentContext(final Player player) {
        return plugin.getPlotSquaredFlagService().currentPlotContext(player);
    }

    private PlotRegion backupBounds(final PlotContext context) {
        if (!fullWorldHeight || context.getWorld() == null) {
            return context.getBounds();
        }
        return context.getBounds().withWorldBounds(context.getWorld(), fallbackMinY);
    }

    private void lock(final PlotRegion region) {
        if (region != null) {
            lockedRegions.add(region);
        }
    }

    private void unlockLater(final PlotRegion region) {
        if (region == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> lockedRegions.remove(region), unlockDelayTicks);
    }

    private boolean canCreateBackup(final Player player) {
        return player.hasPermission("craftplayplotextras.backup.create")
                || player.hasPermission("craftplayplotextras.admin");
    }

    private String uniqueBackupId(final PlotContext context, final String action) {
        final String prefix = sanitize(FILE_TIME_FORMAT.format(LocalDateTime.now())
                + "_" + context.getOwnerName()
                + "_" + context.getPlotId()
                + "_" + actionName(action));
        String id = prefix;
        int counter = 2;
        while (findBackup(id).isPresent() || new File(backupFolder, id + ".schem").exists()) {
            id = prefix + "_" + counter;
            counter++;
        }
        return id;
    }

    private void saveMetadata() {
        if (metadataConfiguration == null || metadataFile == null) {
            return;
        }
        plugin.getStorageService().save("plotbackups", metadataFile, metadataConfiguration);
    }

    private String sanitize(final String value) {
        return value == null ? "backup" : value
                .replace(' ', '_')
                .replaceAll("[^A-Za-z0-9_.-]", "_")
                .replaceAll("_+", "_");
    }

    private Map<String, String> backupPlaceholders(final PlotBackupMetadata metadata) {
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("id", metadata.getId());
        placeholders.put("file", metadata.getSchematicFileName());
        placeholders.put("owner", value(metadata.getOwnerName()));
        placeholders.put("created_by", value(metadata.getCreatedByName()));
        placeholders.put("created_at", value(metadata.getCreatedAt()));
        placeholders.put("action", actionName(metadata.getAction()));
        placeholders.put("world", value(metadata.getWorldName()));
        placeholders.put("plot", value(metadata.getPlotId()));
        placeholders.put("plots", metadata.getPlotIds().isEmpty() ? value(metadata.getPlotId()) : String.join(", ", metadata.getPlotIds()));
        placeholders.put("merge", value(metadata.getMergeType()));
        return placeholders;
    }

    private String actionName(final String action) {
        if (action == null) {
            return "Backup";
        }
        final String configured = plugin.getConfig().getString("plot-backups.action-names." + action.toLowerCase(Locale.ROOT), "");
        if (configured != null && !configured.trim().isEmpty()) {
            return configured;
        }
        return action;
    }

    private String value(final String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    private String stripSlash(final String command) {
        if (command == null) {
            return "";
        }
        final String trimmed = command.trim();
        return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }

    private String commandPrefix() {
        final String prefix = plugin.getConfig().getString("command.prefix", "plotextras");
        return prefix == null || prefix.trim().isEmpty() ? "plotextras" : prefix.trim();
    }

    private SchematicAdapter createAdapter() {
        try {
            final SchematicAdapter adapter = new WorldEditSchematicAdapter();
            if (adapter.isAvailable()) {
                return adapter;
            }
        } catch (final NoClassDefFoundError error) {
            plugin.getLogger().warning("WorldEdit/FAWE wurde nicht gefunden. Plotbackups sind deaktiviert, bis WorldEdit oder FAWE installiert ist.");
        }
        return null;
    }

    private enum PendingType {
        PLOT_ACTION,
        RESTORE
    }

    private static final class PendingOperation {

        private final PendingType type;
        private final String action;
        private final String originalCommand;
        private final String backupId;

        private PendingOperation(
                final PendingType type,
                final String action,
                final String originalCommand,
                final String backupId
        ) {
            this.type = type;
            this.action = action;
            this.originalCommand = originalCommand;
            this.backupId = backupId;
        }

        static PendingOperation plotAction(final String action, final String originalCommand) {
            return new PendingOperation(PendingType.PLOT_ACTION, action, originalCommand, "");
        }

        static PendingOperation restore(final String backupId) {
            return new PendingOperation(PendingType.RESTORE, "", "", backupId);
        }

        PendingType getType() {
            return type;
        }

        String getAction() {
            return action;
        }

        String getOriginalCommand() {
            return originalCommand;
        }

        String getBackupId() {
            return backupId;
        }
    }
}
