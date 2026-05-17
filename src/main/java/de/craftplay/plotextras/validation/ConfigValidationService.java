package de.craftplay.plotextras.validation;

import de.craftplay.plotextras.feature.FeatureToggleService;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
            issues.add(file.getName() + ": GUI-Groesse muss zwischen 9 und 54 liegen und durch 9 teilbar sein.");
        }
        final ConfigurationSection items = config.getConfigurationSection("items");
        final Map<Integer, String> usedSlots = new HashMap<>();
        if (items != null) {
            for (final String key : items.getKeys(false)) {
                final ConfigurationSection item = items.getConfigurationSection(key);
                final List<Integer> slots = itemSlots(item);
                if (slots.isEmpty()) {
                    issues.add(file.getName() + ": Item '" + key + "' hat keinen Slot.");
                }
                for (final int slot : slots) {
                    if (slot < 0 || slot >= size) {
                        issues.add(file.getName() + ": Item '" + key + "' nutzt ungueltigen Slot " + slot + ".");
                        continue;
                    }
                    final String previous = usedSlots.putIfAbsent(slot, key);
                    if (previous != null) {
                        issues.add(file.getName() + ": Slot " + slot + " wird mehrfach genutzt (" + previous + ", " + key + ").");
                    }
                }
                validateActions(file, item, issues);
            }
        }
        final ConfigurationSection dynamic = config.getConfigurationSection("dynamic");
        if (dynamic != null) {
            for (final int slot : itemSlots(dynamic)) {
                if (slot < 0 || slot >= size) {
                    issues.add(file.getName() + ": Dynamic nutzt ungueltigen Slot " + slot + ".");
                }
            }
            for (final String key : dynamic.getKeys(false)) {
                final ConfigurationSection child = dynamic.getConfigurationSection(key);
                validateActions(file, child, issues);
            }
        }
    }

    private List<Integer> itemSlots(final ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }
        if (section.isList("slots")) {
            return section.getIntegerList("slots");
        }
        if (section.contains("slot")) {
            return List.of(section.getInt("slot", -1));
        }
        return List.of();
    }

    private void validateActions(final File file, final ConfigurationSection section, final List<String> issues) {
        if (section == null) {
            return;
        }
        final List<String> actions = new ArrayList<>();
        if (section.isList("actions")) {
            actions.addAll(section.getStringList("actions"));
        }
        if (section.isString("action")) {
            actions.add(section.getString("action"));
        }
        actions.addAll(section.getStringList("commands").stream()
                .map(command -> command.toUpperCase(Locale.ROOT).startsWith("COMMAND:") ? command : "COMMAND:" + command)
                .toList());
        for (final String rawAction : actions) {
            final String action = rawAction == null ? "" : rawAction.trim();
            if (action.isBlank() || action.contains("{") || action.contains("%")) {
                continue;
            }
            final String lower = action.toLowerCase(Locale.ROOT);
            if (lower.startsWith("open:")) {
                final String target = action.substring(action.indexOf(':') + 1).toLowerCase(Locale.ROOT);
                if (!new File(file.getParentFile(), target + ".yml").exists()) {
                    issues.add(file.getName() + ": OPEN-Ziel fehlt: " + target + ".yml");
                }
                continue;
            }
            if (!isKnownAction(lower)) {
                issues.add(file.getName() + ": Unbekannte Aktion '" + action + "'.");
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
                if (key.toLowerCase(Locale.ROOT).endsWith("sound")) {
                    validateSound(file, key, config.getString(key, ""), issues);
                } else if (key.toLowerCase(Locale.ROOT).endsWith("enchantment")
                        || key.toLowerCase(Locale.ROOT).endsWith("enchant")) {
                    validateEnchantment(file, key, config.getString(key, ""), issues);
                }
                continue;
            }
            final String materialName = config.getString(key, "");
            if (materialName.isBlank() || materialName.contains("{") || materialName.contains("%")) {
                continue;
            }
            if (Material.matchMaterial(materialName) == null) {
                issues.add(file.getName() + ": Ungueltiges Material bei '" + key + "': " + materialName);
            }
        }
    }

    private boolean isKnownAction(final String action) {
        final List<String> exact = List.of(
                "close", "back", "open_language", "next_page", "previous_page", "teleport_plot_home", "set_plot_home",
                "show_plot_info", "role_create_prompt", "invite_member_prompt", "role_rename_prompt", "role_delete_selected",
                "unassign_selected_member_role", "promote_selected_member", "demote_selected_member",
                "untrust_selected_member_prompt", "remove_selected_member_prompt", "confirm_untrust_selected_member",
                "confirm_remove_selected_member", "restore_selected_backup", "plot_note_prompt", "team_note_prompt",
                "build_task_create_prompt", "warp_set_prompt", "set_named_home_prompt", "search_plots_prompt",
                "guestbook_sign_prompt", "mailbox_sign_prompt", "show_selfcheck", "show_assistant", "show_profile",
                "show_performance", "team_mod_list", "team_unfreeze", "redstone_enable", "competition_list",
                "undo_last_change", "random_public_plot", "backup_comment_prompt", "toggle_selected_backup_important"
        );
        if (exact.contains(action)) {
            return true;
        }
        final List<String> prefixes = List.of(
                "set_language:", "toggle_flag:", "apply_flag_preset:", "set_flag:", "set_biome:", "set_component:",
                "toggle_option_favorite:", "open:", "select_role:", "toggle_role_permission:", "select_member:",
                "assign_selected_member_role:", "select_backup:", "set_plot_status:", "set_profile_access:",
                "plot_warp_click:", "plot_home_click:", "teleport_plot_warp:", "teleport_plot_home:",
                "delete_plot_warp:", "delete_plot_home:", "set_top_mode:", "teleport_plot_key:",
                "delete_guestbook_entry:", "delete_mailbox_entry:", "request_prompt:", "temptrust_add_prompt:",
                "temptrust_remove:", "close_request:", "accept_trust_request:", "close_report:", "create_report:",
                "player_cleanup:", "team_freeze:", "team_cleanup:", "redstone_enable:", "redstone_teleport:",
                "create_backup:", "permission_check:", "competition_join:", "competition_score_prompt:",
                "complete_build_task:", "toggle_feature:", "command:", "player_command:", "console_command:", "message:"
        );
        return prefixes.stream().anyMatch(action::startsWith);
    }

    private void validateSound(final File file, final String key, final String soundName, final List<String> issues) {
        if (soundName == null || soundName.isBlank() || soundName.contains("{") || soundName.contains("%")) {
            return;
        }
        try {
            Sound.valueOf(soundName.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (final IllegalArgumentException exception) {
            issues.add(file.getName() + ": Ungueltiger Sound bei '" + key + "': " + soundName);
        }
    }

    private void validateEnchantment(final File file, final String key, final String enchantmentName, final List<String> issues) {
        if (enchantmentName == null || enchantmentName.isBlank() || enchantmentName.contains("{") || enchantmentName.contains("%")) {
            return;
        }
        final String normalized = enchantmentName.toLowerCase(Locale.ROOT).replace('-', '_');
        if (Enchantment.getByKey(NamespacedKey.minecraft(normalized)) == null) {
            issues.add(file.getName() + ": Ungueltiges Enchantment bei '" + key + "': " + enchantmentName);
        }
    }
}
