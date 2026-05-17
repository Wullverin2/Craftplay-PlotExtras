package de.craftplay.plotextras.feature;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FeatureToggleService {

    private static final Map<String, String> GUI_FEATURES = Map.ofEntries(
            Map.entry("main", "player.gui"),
            Map.entry("language", "player.language"),
            Map.entry("flags", "player.flags"),
            Map.entry("flag-presets", "player.flag-presets"),
            Map.entry("decor", "player.decor"),
            Map.entry("decor-border", "player.decor.border"),
            Map.entry("decor-border-natural", "player.decor.border"),
            Map.entry("decor-border-path", "player.decor.border"),
            Map.entry("decor-border-stone", "player.decor.border"),
            Map.entry("decor-border-color", "player.decor.border"),
            Map.entry("decor-border-slabs", "player.decor.border"),
            Map.entry("decor-border-special", "player.decor.border"),
            Map.entry("decor-wall-stone", "player.decor.wall"),
            Map.entry("decor-wall-wood", "player.decor.wall"),
            Map.entry("decor-wall-nether", "player.decor.wall"),
            Map.entry("decor-wall-precious", "player.decor.wall"),
            Map.entry("decor-wall-slabs", "player.decor.wall"),
            Map.entry("members", "player.members"),
            Map.entry("member-roles", "player.members.roles"),
            Map.entry("member-remove-confirm", "player.members.remove"),
            Map.entry("roles", "player.roles"),
            Map.entry("role-edit", "player.roles"),
            Map.entry("settings", "player.settings"),
            Map.entry("settings-weather", "player.settings.weather"),
            Map.entry("settings-time", "player.settings.time"),
            Map.entry("settings-biome", "player.settings.biome"),
            Map.entry("entity-limits", "player.entity-limits"),
            Map.entry("plot-dashboard", "player.dashboard"),
            Map.entry("plot-warps", "player.plot-warps"),
            Map.entry("backups", "team.backups"),
            Map.entry("backup-restore-confirm", "team.backups.restore"),
            Map.entry("team-inspector", "team.inspector"),
            Map.entry("audit-log", "team.audit-log"),
            Map.entry("redstone-alerts", "team.redstone-alerts")
    );

    private final JavaPlugin plugin;
    private YamlConfiguration config;

    public FeatureToggleService(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        final File file = new File(plugin.getDataFolder(), "features.yml");
        if (!file.exists()) {
            plugin.saveResource("features.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public boolean isEnabled(final String feature) {
        if (feature == null || feature.isBlank()) {
            return true;
        }
        if (config == null) {
            reload();
        }
        if (!config.getBoolean("features.enabled", true)) {
            return false;
        }

        String path = "features";
        for (final String rawPart : normalize(feature).split("\\.")) {
            if (rawPart.isBlank()) {
                continue;
            }
            path += "." + rawPart;
            if (config.isBoolean(path) && !config.getBoolean(path, true)) {
                return false;
            }
            if (config.contains(path + ".enabled") && !config.getBoolean(path + ".enabled", true)) {
                return false;
            }
        }
        return true;
    }

    public boolean areAllEnabled(final Collection<String> features) {
        for (final String feature : features) {
            if (!isEnabled(feature)) {
                return false;
            }
        }
        return true;
    }

    public boolean sectionEnabled(final ConfigurationSection section) {
        if (section == null) {
            return true;
        }
        if (section.isList("feature")) {
            return areAllEnabled(section.getStringList("feature"));
        }
        final String feature = section.getString("feature", "");
        return feature.isBlank() || isEnabled(feature);
    }

    public boolean guiEnabled(final String guiId) {
        return isEnabled(featureForGui(guiId));
    }

    public String featureForGui(final String guiId) {
        if (guiId == null || guiId.isBlank()) {
            return "";
        }
        return GUI_FEATURES.getOrDefault(normalize(guiId), "player.gui.custom");
    }

    public String featureForDynamicType(final String type) {
        return switch (normalize(type)) {
            case "flags" -> "player.flags";
            case "component-categories", "components" -> "player.decor";
            case "config-options" -> "player.config-options";
            case "members" -> "player.members";
            case "plot-roles", "role-permissions", "member-role-options" -> "player.roles";
            case "entity-limits" -> "player.entity-limits";
            case "plot-backups" -> "team.backups";
            case "audit-log" -> "team.audit-log";
            case "redstone-alerts" -> "team.redstone-alerts";
            case "plot-warps" -> "player.plot-warps";
            case "languages" -> "player.language";
            default -> "";
        };
    }

    public String featureForAction(final String action) {
        final String normalized = normalize(action);
        if (normalized.equals("open_language") || normalized.startsWith("set_language:")) {
            return "player.language";
        }
        if (normalized.startsWith("open:")) {
            return featureForGui(action.substring(action.indexOf(':') + 1));
        }
        if (normalized.equals("teleport_plot_home")) {
            return "player.settings.home.teleport";
        }
        if (normalized.equals("set_plot_home")) {
            return "player.settings.home.set";
        }
        if (normalized.startsWith("toggle_flag:")) {
            return "player.flags";
        }
        if (normalized.startsWith("apply_flag_preset:")) {
            return "player.flag-presets";
        }
        if (normalized.startsWith("set_flag:weather:")) {
            return "player.settings.weather";
        }
        if (normalized.startsWith("set_flag:time:")) {
            return "player.settings.time";
        }
        if (normalized.startsWith("set_flag:")) {
            return "player.settings";
        }
        if (normalized.startsWith("set_biome:")) {
            return "player.settings.biome";
        }
        if (normalized.startsWith("set_component:wall:")) {
            return "player.decor.wall";
        }
        if (normalized.startsWith("set_component:border:")) {
            return "player.decor.border";
        }
        if (normalized.startsWith("set_component:")) {
            return "player.decor";
        }
        if (normalized.equals("role_create_prompt")
                || normalized.equals("role_rename_prompt")
                || normalized.equals("role_delete_selected")
                || normalized.startsWith("toggle_role_permission:")
                || normalized.startsWith("select_role:")) {
            return "player.roles";
        }
        if (normalized.equals("invite_member_prompt")) {
            return "player.members.invite";
        }
        if (normalized.startsWith("select_member:")
                || normalized.startsWith("assign_selected_member_role:")
                || normalized.equals("unassign_selected_member_role")) {
            return "player.members.roles";
        }
        if (normalized.equals("promote_selected_member")) {
            return "player.members.promote";
        }
        if (normalized.equals("demote_selected_member")) {
            return "player.members.demote";
        }
        if (normalized.equals("untrust_selected_member_prompt")
                || normalized.equals("remove_selected_member_prompt")
                || normalized.equals("confirm_untrust_selected_member")
                || normalized.equals("confirm_remove_selected_member")) {
            return "player.members.remove";
        }
        if (normalized.startsWith("select_backup:")) {
            return "team.backups";
        }
        if (normalized.equals("restore_selected_backup")) {
            return "team.backups.restore";
        }
        if (normalized.equals("plot_note_prompt")) {
            return "player.plot-notes";
        }
        if (normalized.equals("team_note_prompt")) {
            return "team.notes";
        }
        if (normalized.startsWith("set_plot_status:")) {
            return "player.plot-status";
        }
        if (normalized.equals("toggle_plot_like")) {
            return "player.plot-likes";
        }
        if (normalized.equals("warp_set_prompt")) {
            return "player.plot-warps.set";
        }
        if (normalized.startsWith("teleport_plot_warp:")) {
            return "player.plot-warps.teleport";
        }
        if (normalized.startsWith("delete_plot_warp:")) {
            return "player.plot-warps.delete";
        }
        if (normalized.startsWith("player_command:pe report")) {
            return "player.reports";
        }
        if (normalized.startsWith("player_command:pe reports")) {
            return "team.reports";
        }
        if (normalized.startsWith("player_command:pe mod")) {
            return "team.moderation";
        }
        if (normalized.startsWith("player_command:pe performance")) {
            return "team.performance";
        }
        if (normalized.startsWith("player_command:pe validate")) {
            return "team.config-validator";
        }
        if (normalized.startsWith("player_command:pe contest")) {
            return "player.competitions";
        }
        if (normalized.startsWith("command:")
                || normalized.startsWith("player_command:")
                || normalized.startsWith("console_command:")) {
            return "player.custom-buttons.commands";
        }
        if (normalized.startsWith("message:")) {
            return "player.custom-buttons.messages";
        }
        return "";
    }

    public List<String> enabledGuiIds(final Collection<String> guiIds) {
        return guiIds.stream()
                .filter(this::guiEnabled)
                .sorted()
                .toList();
    }

    private String normalize(final String value) {
        return value.toLowerCase(Locale.ROOT).trim();
    }
}
