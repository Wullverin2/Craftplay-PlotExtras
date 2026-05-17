package de.craftplay.plotextras.resource;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.logging.Level;

public final class ResourceInstaller {

    private static final List<String> DEFAULT_RESOURCES = List.of(
            "config.yml",
            "wall.yml",
            "border.yml",
            "limits.yml",
            "language/de.yml",
            "language/en.yml",
            "gui/de/main.yml",
            "gui/de/plot-dashboard.yml",
            "gui/de/flags.yml",
            "gui/de/decor.yml",
            "gui/de/decor-border.yml",
            "gui/de/decor-wall-stone.yml",
            "gui/de/decor-wall-wood.yml",
            "gui/de/decor-wall-nether.yml",
            "gui/de/decor-wall-precious.yml",
            "gui/de/decor-wall-slabs.yml",
            "gui/de/decor-border-natural.yml",
            "gui/de/decor-border-path.yml",
            "gui/de/decor-border-stone.yml",
            "gui/de/decor-border-color.yml",
            "gui/de/decor-border-slabs.yml",
            "gui/de/decor-border-special.yml",
            "gui/de/members.yml",
            "gui/de/roles.yml",
            "gui/de/role-edit.yml",
            "gui/de/member-roles.yml",
            "gui/de/member-remove-confirm.yml",
            "gui/de/backups.yml",
            "gui/de/backup-restore-confirm.yml",
            "gui/de/team-inspector.yml",
            "gui/de/audit-log.yml",
            "gui/de/redstone-alerts.yml",
            "gui/de/settings.yml",
            "gui/de/settings-weather.yml",
            "gui/de/settings-time.yml",
            "gui/de/settings-biome.yml",
            "gui/de/entity-limits.yml",
            "gui/de/language.yml",
            "gui/en/main.yml",
            "gui/en/plot-dashboard.yml",
            "gui/en/flags.yml",
            "gui/en/decor.yml",
            "gui/en/decor-border.yml",
            "gui/en/decor-wall-stone.yml",
            "gui/en/decor-wall-wood.yml",
            "gui/en/decor-wall-nether.yml",
            "gui/en/decor-wall-precious.yml",
            "gui/en/decor-wall-slabs.yml",
            "gui/en/decor-border-natural.yml",
            "gui/en/decor-border-path.yml",
            "gui/en/decor-border-stone.yml",
            "gui/en/decor-border-color.yml",
            "gui/en/decor-border-slabs.yml",
            "gui/en/decor-border-special.yml",
            "gui/en/members.yml",
            "gui/en/roles.yml",
            "gui/en/role-edit.yml",
            "gui/en/member-roles.yml",
            "gui/en/member-remove-confirm.yml",
            "gui/en/backups.yml",
            "gui/en/backup-restore-confirm.yml",
            "gui/en/team-inspector.yml",
            "gui/en/audit-log.yml",
            "gui/en/redstone-alerts.yml",
            "gui/en/settings.yml",
            "gui/en/settings-weather.yml",
            "gui/en/settings-time.yml",
            "gui/en/settings-biome.yml",
            "gui/en/entity-limits.yml",
            "gui/en/language.yml"
    );

    private ResourceInstaller() {
    }

    public static void installDefaults(final JavaPlugin plugin) {
        final String currentVersion = plugin.getDescription().getVersion();
        final File versionFile = new File(plugin.getDataFolder(), "version.yml");
        final YamlConfiguration versionConfig = YamlConfiguration.loadConfiguration(versionFile);
        final String previousVersion = versionConfig.getString("version", "");
        final boolean versionUpgrade = !previousVersion.isBlank() && !previousVersion.equalsIgnoreCase(currentVersion);
        final File backupFolder = versionUpgrade ? new File(plugin.getDataFolder(), "backups/" + previousVersion) : null;

        for (final String resourcePath : DEFAULT_RESOURCES) {
            final File target = new File(plugin.getDataFolder(), resourcePath);
            if (!target.exists()) {
                plugin.saveResource(resourcePath, false);
                continue;
            }

            if (versionUpgrade) {
                backup(plugin, target, new File(backupFolder, resourcePath));
            }
            mergeMissingDefaults(plugin, resourcePath, target);
        }

        versionConfig.set("version", currentVersion);
        try {
            versionConfig.save(versionFile);
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not write version.yml.", exception);
        }
    }

    private static void backup(final JavaPlugin plugin, final File source, final File target) {
        try {
            final File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create backup folder: " + parent.getPath());
                return;
            }
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not backup config file: " + source.getPath(), exception);
        }
    }

    private static void mergeMissingDefaults(final JavaPlugin plugin, final String resourcePath, final File target) {
        try (InputStream inputStream = plugin.getResource(resourcePath)) {
            if (inputStream == null) {
                return;
            }

            final YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            final YamlConfiguration existing = YamlConfiguration.loadConfiguration(target);
            boolean changed = false;
            for (final String key : defaults.getKeys(true)) {
                if (existing.contains(key)) {
                    continue;
                }
                if (defaults.isConfigurationSection(key)) {
                    existing.createSection(key);
                } else {
                    existing.set(key, defaults.get(key));
                }
                changed = true;
            }

            if (changed) {
                existing.save(target);
            }
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not merge defaults into " + target.getPath(), exception);
        }
    }
}
