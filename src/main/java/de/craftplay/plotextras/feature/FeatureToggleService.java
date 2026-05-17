package de.craftplay.plotextras.feature;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public final class FeatureToggleService {

    private static final Map<String, String> GUI_FEATURES = Map.ofEntries(
            Map.entry("main", "player.gui"),
            Map.entry("language", "player.language"),
            Map.entry("plot-profile", "player.plot-profile"),
            Map.entry("cleanup", "player.cleanup"),
            Map.entry("competitions", "player.competitions"),
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
            Map.entry("plot-tools", "player.tools"),
            Map.entry("plot-search", "player.plot-search"),
            Map.entry("guestbook", "player.guestbook"),
            Map.entry("requests", "player.requests"),
            Map.entry("temporary-trusts", "player.temporary-trusts"),
            Map.entry("team-requests", "team.requests"),
            Map.entry("reports", "team.reports"),
            Map.entry("build-tasks", "team.builder.tasks"),
            Map.entry("permission-check", "team.permission-checker"),
            Map.entry("performance", "team.performance"),
            Map.entry("team-moderation", "team.moderation"),
            Map.entry("config-issues", "team.config-validator"),
            Map.entry("statistics", "team.statistics"),
            Map.entry("feature-toggles", "team.feature-toggles"),
            Map.entry("backups", "team.backups"),
            Map.entry("backup-restore-confirm", "team.backups.restore"),
            Map.entry("team-inspector", "team.inspector"),
            Map.entry("team-tools", "team.tools"),
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

    public Map<String, Boolean> allFeatureToggles() {
        if (config == null) {
            reload();
        }
        final Map<String, Boolean> toggles = new LinkedHashMap<>();
        collectFeatureToggles(config.getConfigurationSection("features"), "", toggles);
        return toggles;
    }

    public boolean toggleFeature(final String feature) {
        final String normalized = normalize(feature);
        if (normalized.isBlank() || normalized.equals("enabled")) {
            return false;
        }
        final String path = "features." + normalized + ".enabled";
        final boolean newValue = !config.getBoolean(path, true);
        config.set(path, newValue);
        save();
        return newValue;
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
            case "plot-search" -> "player.plot-search";
            case "guestbook" -> "player.guestbook";
            case "plot-requests" -> "player.requests";
            case "temporary-trusts" -> "player.temporary-trusts";
            case "plot-reports" -> "team.reports";
            case "build-tasks" -> "team.builder.tasks";
            case "permission-check" -> "team.permission-checker";
            case "performance-snapshot" -> "team.performance";
            case "competitions" -> "player.competitions";
            case "config-issues" -> "team.config-validator";
            case "statistics" -> "team.statistics";
            case "feature-toggles" -> "team.feature-toggles";
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
        if (normalized.startsWith("plot_warp_click:") || normalized.startsWith("teleport_plot_warp:")) {
            return "player.plot-warps.teleport";
        }
        if (normalized.startsWith("delete_plot_warp:")) {
            return "player.plot-warps.delete";
        }
        if (normalized.equals("search_plots_prompt") || normalized.startsWith("teleport_plot_key:")) {
            return "player.plot-search";
        }
        if (normalized.equals("guestbook_sign_prompt") || normalized.startsWith("delete_guestbook_entry:")) {
            return "player.guestbook";
        }
        if (normalized.startsWith("request_prompt:")) {
            return "player.requests";
        }
        if (normalized.startsWith("temptrust_add_prompt:") || normalized.startsWith("temptrust_remove:")) {
            return "player.temporary-trusts";
        }
        if (normalized.startsWith("close_request:") || normalized.startsWith("accept_trust_request:")) {
            return "team.requests";
        }
        if (normalized.startsWith("close_report:")) {
            return "team.reports.close";
        }
        if (normalized.startsWith("create_report:")) {
            return "player.reports";
        }
        if (normalized.startsWith("player_cleanup:")) {
            return "player.cleanup";
        }
        if (normalized.equals("show_selfcheck")) {
            return "player.assistant";
        }
        if (normalized.equals("show_assistant")) {
            return "player.assistant";
        }
        if (normalized.equals("show_profile") || normalized.startsWith("set_profile_access:")) {
            return "player.plot-profile";
        }
        if (normalized.equals("show_performance")) {
            return "team.performance";
        }
        if (normalized.equals("team_mod_list")
                || normalized.startsWith("team_freeze:")
                || normalized.equals("team_unfreeze")
                || normalized.startsWith("team_cleanup:")) {
            return "team.moderation";
        }
        if (normalized.startsWith("redstone_enable")) {
            return "redstone.reactivate";
        }
        if (normalized.startsWith("redstone_teleport:")) {
            return "team.redstone-alerts";
        }
        if (normalized.startsWith("create_backup:")) {
            return "team.backups.create";
        }
        if (normalized.startsWith("permission_check:")) {
            return "team.permission-checker";
        }
        if (normalized.startsWith("competition_join:") || normalized.equals("competition_list")) {
            return "player.competitions";
        }
        if (normalized.startsWith("competition_score_prompt:")) {
            return "team.competitions.judge";
        }
        if (normalized.equals("build_task_create_prompt")) {
            return "team.builder.tasks";
        }
        if (normalized.startsWith("complete_build_task:")) {
            return "team.builder.tasks";
        }
        if (normalized.startsWith("toggle_feature:")) {
            return "team.feature-toggles";
        }
        if (normalized.startsWith("player_command:pe reports")) {
            return "team.reports";
        }
        if (normalized.startsWith("player_command:pe report")) {
            return "player.reports";
        }
        if (normalized.startsWith("player_command:pe backup create")
                || normalized.startsWith("player_command:pe backup save")
                || normalized.startsWith("player_command:pe backup sichern")) {
            return "team.backups.create";
        }
        if (normalized.startsWith("player_command:pe backup")) {
            return "team.backups";
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
        if (normalized.startsWith("player_command:pe tools")) {
            return "player.tools";
        }
        if (normalized.startsWith("player_command:pe assistant")) {
            return "player.assistant";
        }
        if (normalized.startsWith("player_command:pe profile")) {
            return "player.plot-profile";
        }
        if (normalized.startsWith("player_command:pe guestbook")) {
            return "player.guestbook";
        }
        if (normalized.startsWith("player_command:pe search")) {
            return "player.plot-search";
        }
        if (normalized.startsWith("player_command:pe cleanup")) {
            return "player.cleanup";
        }
        if (normalized.startsWith("player_command:pe favorite")) {
            return "player.plot-favorites";
        }
        if (normalized.startsWith("player_command:pe teamtools")) {
            return "team.tools";
        }
        if (normalized.startsWith("player_command:pe requests")) {
            return "team.requests";
        }
        if (normalized.startsWith("player_command:pe request")) {
            return "player.requests";
        }
        if (normalized.startsWith("player_command:pe stats")) {
            return "team.statistics";
        }
        if (normalized.startsWith("player_command:pe permcheck")) {
            return "team.permission-checker";
        }
        if (normalized.startsWith("player_command:pe buildtask") || normalized.startsWith("player_command:pe buildermode")) {
            return "team.builder";
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

    private void collectFeatureToggles(
            final ConfigurationSection section,
            final String path,
            final Map<String, Boolean> toggles
    ) {
        if (section == null) {
            return;
        }
        for (final String key : section.getKeys(false)) {
            if (key.equals("enabled")) {
                if (!path.isBlank()) {
                    toggles.put(path, section.getBoolean("enabled", true));
                }
                continue;
            }
            final ConfigurationSection child = section.getConfigurationSection(key);
            if (child != null) {
                collectFeatureToggles(child, path.isBlank() ? key : path + "." + key, toggles);
            }
        }
    }

    private void save() {
        final File file = new File(plugin.getDataFolder(), "features.yml");
        try {
            config.save(file);
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not save features.yml.", exception);
        }
    }
}
