package de.craftplay.plotextras.roles;

import de.craftplay.plotextras.CraftplayPlotExtrasPlugin;
import de.craftplay.plotextras.plotsquared.PlotContext;
import de.craftplay.plotextras.plotsquared.PlotSquaredFlagService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PlotRoleService {

    private static final String OWNER_ROLE = "Owner";
    private static final String TRUSTED_ROLE = "Trusted";

    private final CraftplayPlotExtrasPlugin plugin;
    private final PlotSquaredFlagService flagService;
    private final Map<String, PlotRoleData> plots = new LinkedHashMap<>();
    private String dataFile;
    private boolean enabled;
    private boolean asyncSaves;

    public PlotRoleService(final CraftplayPlotExtrasPlugin plugin, final PlotSquaredFlagService flagService) {
        this.plugin = plugin;
        this.flagService = flagService;
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("roles.enabled", true);
        asyncSaves = plugin.getConfig().getBoolean("technical.async-yaml-saves", true);
        dataFile = plugin.getConfig().getString("roles.data-file", "plotroles.yml");
        load();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Optional<String> currentPlotKey(final Player player) {
        final Optional<PlotContext> context = flagService.currentPlotContext(player);
        if (!context.isPresent() || !context.get().isComplete()) {
            plugin.getLanguageManager().send(player, "no-plot");
            return Optional.empty();
        }
        return Optional.of(context.get().getWorldName() + ";" + context.get().getPlotId());
    }

    public List<PlotRole> roles(final Player player) {
        if (!canManage(player)) {
            return Collections.emptyList();
        }
        final Optional<String> plotKey = currentPlotKey(player);
        if (!plotKey.isPresent()) {
            return Collections.emptyList();
        }
        return roles(plotKey.get());
    }

    public List<PlotRole> roles(final String plotKey) {
        final PlotRoleData data = data(plotKey);
        final List<PlotRole> result = new ArrayList<>(data.roles.values());
        result.sort(Comparator.comparing(PlotRole::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public Map<UUID, String> members(final String plotKey) {
        return Collections.unmodifiableMap(data(plotKey).members);
    }

    public String memberRole(final String plotKey, final UUID uuid) {
        if (plotKey == null || uuid == null) {
            return "";
        }
        return data(plotKey).members.getOrDefault(uuid, "");
    }

    public boolean hasRolePermission(final Player player, final String permission) {
        if (!enabled || permission == null || permission.trim().isEmpty()) {
            return false;
        }
        final Optional<PlotContext> context = flagService.currentPlotContext(player);
        if (!context.isPresent() || !context.get().isComplete()) {
            return false;
        }
        final PlotContext plot = context.get();
        final String plotKey = plot.getWorldName() + ";" + plot.getPlotId();
        final PlotRoleData data = data(plotKey);
        String roleName = null;
        if (plot.getOwnerUuid() != null && plot.getOwnerUuid().equals(player.getUniqueId())) {
            roleName = OWNER_ROLE;
        } else {
            roleName = data.members.get(player.getUniqueId());
        }
        if (roleName == null) {
            return false;
        }
        final String key = findRoleKey(data, roleName);
        if (key == null) {
            return false;
        }
        final String required = permission.trim().toLowerCase(Locale.ROOT);
        for (final String value : data.roles.get(key).getPermissions()) {
            final String current = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            if ("*".equals(current) || current.equals(required)) {
                return true;
            }
            if (current.endsWith(".*") && required.startsWith(current.substring(0, current.length() - 1))) {
                return true;
            }
        }
        return false;
    }

    public boolean hasRolePermission(
            final String plotKey,
            final UUID playerId,
            final UUID ownerUuid,
            final String permission
    ) {
        if (!enabled || plotKey == null || playerId == null || permission == null || permission.trim().isEmpty()) {
            return false;
        }
        final PlotRoleData data = data(plotKey);
        final String roleName = ownerUuid != null && ownerUuid.equals(playerId)
                ? OWNER_ROLE
                : data.members.get(playerId);
        if (roleName == null) {
            return false;
        }
        final String key = findRoleKey(data, roleName);
        if (key == null) {
            return false;
        }
        final String required = permission.trim().toLowerCase(Locale.ROOT);
        for (final String value : data.roles.get(key).getPermissions()) {
            final String current = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            if ("*".equals(current) || current.equals(required)) {
                return true;
            }
            if (current.endsWith(".*") && required.startsWith(current.substring(0, current.length() - 1))) {
                return true;
            }
        }
        return false;
    }

    public boolean createRole(final Player player, final String roleName) {
        if (!canManage(player)) {
            return false;
        }
        final Optional<String> plotKey = currentPlotKey(player);
        if (!plotKey.isPresent()) {
            return false;
        }
        final PlotRoleData data = data(plotKey.get());
        final String normalized = normalizeRoleName(roleName);
        if (normalized.isEmpty()) {
            plugin.getLanguageManager().send(player, "role-invalid-name");
            return false;
        }
        if (findRoleKey(data, normalized) != null) {
            plugin.getLanguageManager().send(player, "role-already-exists", placeholders(normalized));
            return false;
        }
        data.roles.put(normalized.toLowerCase(Locale.ROOT), new PlotRole(normalized, Collections.emptyList(), false));
        save();
        plugin.getLanguageManager().send(player, "role-created", placeholders(normalized));
        return true;
    }

    public boolean deleteRole(final Player player, final String roleName) {
        if (!canManage(player)) {
            return false;
        }
        final Optional<String> plotKey = currentPlotKey(player);
        if (!plotKey.isPresent()) {
            return false;
        }
        final PlotRoleData data = data(plotKey.get());
        final String key = findRoleKey(data, roleName);
        if (key == null) {
            plugin.getLanguageManager().send(player, "role-not-found", placeholders(roleName));
            return false;
        }
        final PlotRole role = data.roles.get(key);
        if (role.isProtectedRole()) {
            plugin.getLanguageManager().send(player, "role-protected", placeholders(role.getName()));
            return false;
        }
        data.roles.remove(key);
        data.members.values().removeIf(value -> value.equalsIgnoreCase(role.getName()));
        save();
        plugin.getLanguageManager().send(player, "role-deleted", placeholders(role.getName()));
        return true;
    }

    public boolean renameRole(final Player player, final String oldName, final String newName) {
        if (!canManage(player)) {
            return false;
        }
        final Optional<String> plotKey = currentPlotKey(player);
        if (!plotKey.isPresent()) {
            return false;
        }
        final PlotRoleData data = data(plotKey.get());
        final String oldKey = findRoleKey(data, oldName);
        final String normalizedNew = normalizeRoleName(newName);
        if (oldKey == null || normalizedNew.isEmpty()) {
            plugin.getLanguageManager().send(player, "role-not-found", placeholders(oldName));
            return false;
        }
        if (findRoleKey(data, normalizedNew) != null) {
            plugin.getLanguageManager().send(player, "role-already-exists", placeholders(normalizedNew));
            return false;
        }
        final PlotRole oldRole = data.roles.remove(oldKey);
        data.roles.put(normalizedNew.toLowerCase(Locale.ROOT), oldRole.withName(normalizedNew));
        for (final Map.Entry<UUID, String> entry : data.members.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(oldRole.getName())) {
                entry.setValue(normalizedNew);
            }
        }
        save();
        final Map<String, String> placeholders = placeholders(normalizedNew);
        placeholders.put("old_role", oldRole.getName());
        plugin.getLanguageManager().send(player, "role-renamed", placeholders);
        return true;
    }

    public boolean assign(final Player player, final String roleName, final String targetName) {
        final OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        return assign(player, roleName, target.getUniqueId(), targetName);
    }

    public boolean assign(final Player player, final String roleName, final UUID targetUuid, final String targetName) {
        if (!canManage(player)) {
            return false;
        }
        final Optional<String> plotKey = currentPlotKey(player);
        if (!plotKey.isPresent()) {
            return false;
        }
        final PlotRoleData data = data(plotKey.get());
        final String key = findRoleKey(data, roleName);
        if (key == null) {
            plugin.getLanguageManager().send(player, "role-not-found", placeholders(roleName));
            return false;
        }
        if (targetUuid == null) {
            plugin.getLanguageManager().send(player, "role-invalid-name");
            return false;
        }
        final String displayName = targetName == null || targetName.trim().isEmpty() ? targetUuid.toString() : targetName.trim();
        data.members.put(targetUuid, data.roles.get(key).getName());
        data.memberNames.put(targetUuid, displayName);
        save();
        final Map<String, String> placeholders = placeholders(data.roles.get(key).getName());
        placeholders.put("member", displayName);
        plugin.getLanguageManager().send(player, "role-assigned", placeholders);
        return true;
    }

    public boolean unassign(final Player player, final String targetName) {
        final OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        return unassign(player, target.getUniqueId(), targetName);
    }

    public boolean unassign(final Player player, final UUID targetUuid, final String targetName) {
        if (!canManage(player)) {
            return false;
        }
        final Optional<String> plotKey = currentPlotKey(player);
        if (!plotKey.isPresent()) {
            return false;
        }
        final PlotRoleData data = data(plotKey.get());
        if (targetUuid == null) {
            plugin.getLanguageManager().send(player, "role-invalid-name");
            return false;
        }
        data.members.remove(targetUuid);
        data.memberNames.remove(targetUuid);
        save();
        final Map<String, String> placeholders = placeholders("");
        placeholders.put("member", targetName == null || targetName.trim().isEmpty() ? targetUuid.toString() : targetName.trim());
        plugin.getLanguageManager().send(player, "role-unassigned", placeholders);
        return true;
    }

    public boolean addPermission(final Player player, final String roleName, final String permission) {
        return updatePermission(player, roleName, permission, true);
    }

    public boolean removePermission(final Player player, final String roleName, final String permission) {
        return updatePermission(player, roleName, permission, false);
    }

    public Map<String, String> placeholders(final PlotRole role) {
        final Map<String, String> placeholders = placeholders(role.getName());
        placeholders.put("permissions", role.getPermissions().isEmpty() ? "-" : String.join(", ", role.getPermissions()));
        placeholders.put("protected", String.valueOf(role.isProtectedRole()));
        return placeholders;
    }

    private boolean updatePermission(final Player player, final String roleName, final String permission, final boolean add) {
        if (!canManage(player)) {
            return false;
        }
        final Optional<String> plotKey = currentPlotKey(player);
        if (!plotKey.isPresent()) {
            return false;
        }
        final PlotRoleData data = data(plotKey.get());
        final String key = findRoleKey(data, roleName);
        if (key == null) {
            plugin.getLanguageManager().send(player, "role-not-found", placeholders(roleName));
            return false;
        }
        final PlotRole role = data.roles.get(key);
        final Set<String> permissions = new LinkedHashSet<>(role.getPermissions());
        if (add) {
            permissions.add(permission);
        } else {
            permissions.remove(permission);
        }
        data.roles.put(key, role.withPermissions(new ArrayList<>(permissions)));
        save();
        final Map<String, String> placeholders = placeholders(data.roles.get(key));
        placeholders.put("permission", permission);
        plugin.getLanguageManager().send(player, add ? "role-permission-added" : "role-permission-removed", placeholders);
        return true;
    }

    private PlotRoleData data(final String plotKey) {
        return plots.computeIfAbsent(plotKey, ignored -> {
            final PlotRoleData data = new PlotRoleData();
            data.roles.put(OWNER_ROLE.toLowerCase(Locale.ROOT), new PlotRole(OWNER_ROLE,
                    plugin.getConfig().getStringList("roles.default-owner-permissions"), true));
            data.roles.put(TRUSTED_ROLE.toLowerCase(Locale.ROOT), new PlotRole(TRUSTED_ROLE,
                    plugin.getConfig().getStringList("roles.default-trusted-permissions"), true));
            return data;
        });
    }

    private String findRoleKey(final PlotRoleData data, final String roleName) {
        if (roleName == null) {
            return null;
        }
        for (final String key : data.roles.keySet()) {
            if (key.equalsIgnoreCase(roleName) || data.roles.get(key).getName().equalsIgnoreCase(roleName)) {
                return key;
            }
        }
        return null;
    }

    private void load() {
        plots.clear();
        final YamlConfiguration configuration = plugin.getStorageService().load("plotroles", dataFile);
        if (configuration.getInt("file-version", 0) < 1) {
            configuration.set("file-version", 1);
            saveConfiguration(configuration);
        }
        final ConfigurationSection section = configuration.getConfigurationSection("plots");
        if (section == null) {
            return;
        }
        for (final String safeKey : section.getKeys(false)) {
            final String plotKey = unsafeKey(safeKey);
            final PlotRoleData data = data(plotKey);
            final ConfigurationSection roleSection = configuration.getConfigurationSection("plots." + safeKey + ".roles");
            if (roleSection != null) {
                for (final String roleId : roleSection.getKeys(false)) {
                    final String path = "plots." + safeKey + ".roles." + roleId + ".";
                    final String name = configuration.getString(path + "name", roleId);
                    data.roles.put(name.toLowerCase(Locale.ROOT), new PlotRole(
                            name,
                            configuration.getStringList(path + "permissions"),
                            configuration.getBoolean(path + "protected", isProtectedName(name))
                    ));
                }
            }
            final ConfigurationSection memberSection = configuration.getConfigurationSection("plots." + safeKey + ".members");
            if (memberSection != null) {
                for (final String uuidText : memberSection.getKeys(false)) {
                    try {
                        final UUID uuid = UUID.fromString(uuidText);
                        data.members.put(uuid, configuration.getString("plots." + safeKey + ".members." + uuidText + ".role", TRUSTED_ROLE));
                        data.memberNames.put(uuid, configuration.getString("plots." + safeKey + ".members." + uuidText + ".name", ""));
                    } catch (final IllegalArgumentException ignored) {
                        // Ignore invalid UUID entries in the data file.
                    }
                }
            }
        }
    }

    private void save() {
        final YamlConfiguration configuration = new YamlConfiguration();
        configuration.options().header("Plotrollen-Datendatei von CraftplayPlotExtras.\n"
                + "Hier werden eigene Rollen, Rollenrechte und Zuweisungen pro Plot gespeichert.\n"
                + "Diese Datei wird automatisch gepflegt.");
        configuration.set("file-version", 1);
        for (final Map.Entry<String, PlotRoleData> plotEntry : plots.entrySet()) {
            final String safeKey = safeKey(plotEntry.getKey());
            final PlotRoleData data = plotEntry.getValue();
            for (final PlotRole role : data.roles.values()) {
                final String path = "plots." + safeKey + ".roles." + role.getName().toLowerCase(Locale.ROOT).replace(' ', '_') + ".";
                configuration.set(path + "name", role.getName());
                configuration.set(path + "permissions", role.getPermissions());
                configuration.set(path + "protected", role.isProtectedRole());
            }
            for (final Map.Entry<UUID, String> member : data.members.entrySet()) {
                final String path = "plots." + safeKey + ".members." + member.getKey() + ".";
                configuration.set(path + "name", data.memberNames.getOrDefault(member.getKey(), ""));
                configuration.set(path + "role", member.getValue());
            }
        }
        saveConfiguration(configuration);
    }

    private void saveConfiguration(final YamlConfiguration configuration) {
        if (asyncSaves) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                    () -> plugin.getStorageService().save("plotroles", dataFile, configuration));
            return;
        }
        plugin.getStorageService().save("plotroles", dataFile, configuration);
    }

    private String normalizeRoleName(final String roleName) {
        return roleName == null ? "" : roleName.trim();
    }

    private boolean isProtectedName(final String name) {
        return OWNER_ROLE.equalsIgnoreCase(name) || TRUSTED_ROLE.equalsIgnoreCase(name);
    }

    public boolean canManage(final Player player) {
        if (player.hasPermission("craftplayplotextras.roles.manage") || hasRolePermission(player, "manage-roles")) {
            return true;
        }
        plugin.getLanguageManager().send(player, "no-permission");
        return false;
    }

    private Map<String, String> placeholders(final String roleName) {
        final Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("role", roleName == null ? "" : roleName);
        return placeholders;
    }

    private String safeKey(final String plotKey) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(plotKey.getBytes(StandardCharsets.UTF_8));
    }

    private String unsafeKey(final String safeKey) {
        return new String(Base64.getUrlDecoder().decode(safeKey), StandardCharsets.UTF_8);
    }

    private static final class PlotRoleData {
        private final Map<String, PlotRole> roles = new LinkedHashMap<>();
        private final Map<UUID, String> members = new LinkedHashMap<>();
        private final Map<UUID, String> memberNames = new LinkedHashMap<>();
    }
}
