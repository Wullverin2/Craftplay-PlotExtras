package de.craftplay.plotextras.validation;

import de.craftplay.plotextras.feature.FeatureToggleService;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ConfigValidationService {

    private final JavaPlugin plugin;
    private final FeatureToggleService featureToggleService;

    public ConfigValidationService(final JavaPlugin plugin, final FeatureToggleService featureToggleService) {
        this.plugin = plugin;
        this.featureToggleService = featureToggleService;
    }

    public boolean canValidate(final CommandSender sender) {
        return featureToggleService.isEnabled("team.config-validator")
                && (sender.hasPermission("craftplayplotextras.config.validate") || sender.hasPermission("craftplayplotextras.admin"));
    }

    public List<String> validate() {
        final List<String> issues = new ArrayList<>();
        validateYaml(new File(plugin.getDataFolder(), "config.yml"), issues);
        validateYaml(new File(plugin.getDataFolder(), "features.yml"), issues);
        validateYaml(new File(plugin.getDataFolder(), "wall.yml"), issues);
        validateYaml(new File(plugin.getDataFolder(), "border.yml"), issues);
        validateYaml(new File(plugin.getDataFolder(), "plot-settings.yml"), issues);
        validateYaml(new File(plugin.getDataFolder(), "limits.yml"), issues);
        validateGuiFolder(new File(plugin.getDataFolder(), "gui"), issues);
        return issues;
    }

    private void validateGuiFolder(final File folder, final List<String> issues) {
        if (!folder.exists()) {
            return;
        }
        final File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        for (final File file : files) {
            if (file.isDirectory()) {
                validateGuiFolder(file, issues);
            } else if (file.getName().endsWith(".yml")) {
                validateYaml(file, issues);
                validateGui(file, issues);
            }
        }
    }

    private void validateGui(final File file, final List<String> issues) {
        final YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        final int size = config.getInt("size", 54);
        if (size % 9 != 0 || size < 9 || size > 54) {
            issues.add(file.getName() + ": GUI-Größe muss zwischen 9 und 54 liegen und durch 9 teilbar sein.");
        }
        final ConfigurationSection items = config.getConfigurationSection("items");
        if (items == null) {
            return;
        }
        for (final String key : items.getKeys(false)) {
            final int slot = items.getInt(key + ".slot", -1);
            if (slot < 0 || slot >= size) {
                issues.add(file.getName() + ": Item '" + key + "' nutzt ungültigen Slot " + slot + ".");
            }
        }
    }

    private void validateYaml(final File file, final List<String> issues) {
        if (!file.exists()) {
            issues.add(file.getName() + ": Datei fehlt.");
            return;
        }
        final YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (final String key : config.getKeys(true)) {
            if (!key.toLowerCase(Locale.ROOT).endsWith("material")) {
                continue;
            }
            final String materialName = config.getString(key, "");
            if (materialName.isBlank() || materialName.contains("{") || materialName.contains("%")) {
                continue;
            }
            if (Material.matchMaterial(materialName) == null) {
                issues.add(file.getName() + ": Ungültiges Material bei '" + key + "': " + materialName);
            }
        }
    }
}
