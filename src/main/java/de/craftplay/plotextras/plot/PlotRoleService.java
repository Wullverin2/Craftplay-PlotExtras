package de.craftplay.plotextras.plot;

import com.plotsquared.core.plot.Plot;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class PlotRoleService {

    public static final String OWNER_ROLE_ID = "owner";
    public static final String TRUSTED_ROLE_ID = "trusted";

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration data;

    public PlotRoleService(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/roles.yml");
    }

    public void load() {
        if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
            plugin.getLogger().warning("Could not create data folder for plot roles.");
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    public List<PlotRole> getRoles(final Plot plot) {
        ensureDefaults(plot);
        final ConfigurationSection rolesSection = data.getConfigurationSection(rolesPath(plot));
        final List<PlotRole> roles = new ArrayList<>();
        if (rolesSection == null) {
            return roles;
        }

        for (final String roleId : rolesSection.getKeys(false)) {
            final ConfigurationSection section = rolesSection.getConfigurationSection(roleId);
            if (section != null) {
                roles.add(readRole(roleId, section));
            }
        }
        roles.sort(Comparator
                .comparingInt((PlotRole role) -> roleOrder(role.id()))
                .thenComparing(PlotRole::displayName, String.CASE_INSENSITIVE_ORDER));
        return roles;
    }

    public Optional<PlotRole> getRole(final Plot plot, final String roleId) {
        ensureDefaults(plot);
        final String normalizedRoleId = normalizeRoleId(roleId);
        final ConfigurationSection section = data.getConfigurationSection(rolePath(plot, normalizedRoleId));
        return section == null ? Optional.empty() : Optional.of(readRole(normalizedRoleId, section));
    }

    public RoleResult createRole(final Plot plot, final String roleId, final String displayName) {
        final String normalizedRoleId = normalizeRoleId(roleId);
        if (!isValidRoleId(normalizedRoleId)) {
            return RoleResult.INVALID_ID;
        }
        ensureDefaults(plot);
        if (data.contains(rolePath(plot, normalizedRoleId))) {
            return RoleResult.ALREADY_EXISTS;
        }

        data.set(rolePath(plot, normalizedRoleId) + ".display", normalizeDisplayName(displayName, normalizedRoleId));
        data.set(rolePath(plot, normalizedRoleId) + ".permissions", List.of());
        save();
        return RoleResult.SUCCESS;
    }

    public RoleResult renameRole(final Plot plot, final String roleId, final String displayName) {
        final String normalizedRoleId = normalizeRoleId(roleId);
        ensureDefaults(plot);
        if (!data.contains(rolePath(plot, normalizedRoleId))) {
            return RoleResult.NOT_FOUND;
        }

        data.set(rolePath(plot, normalizedRoleId) + ".display", normalizeDisplayName(displayName, normalizedRoleId));
        save();
        return RoleResult.SUCCESS;
    }

    public RoleResult deleteRole(final Plot plot, final String roleId) {
        final String normalizedRoleId = normalizeRoleId(roleId);
        if (isProtectedRole(normalizedRoleId)) {
            return RoleResult.PROTECTED;
        }
        ensureDefaults(plot);
        if (!data.contains(rolePath(plot, normalizedRoleId))) {
            return RoleResult.NOT_FOUND;
        }

        data.set(rolePath(plot, normalizedRoleId), null);
        final ConfigurationSection playersSection = data.getConfigurationSection(playersPath(plot));
        if (playersSection != null) {
            for (final String playerId : playersSection.getKeys(false)) {
                if (normalizedRoleId.equalsIgnoreCase(playersSection.getString(playerId, ""))) {
                    playersSection.set(playerId, null);
                }
            }
        }
        save();
        return RoleResult.SUCCESS;
    }

    public RoleResult setPermission(final Plot plot, final String roleId, final PlotRolePermission permission, final boolean enabled) {
        final String normalizedRoleId = normalizeRoleId(roleId);
        if (OWNER_ROLE_ID.equals(normalizedRoleId)) {
            return RoleResult.PROTECTED_PERMISSION;
        }
        ensureDefaults(plot);
        final Optional<PlotRole> role = getRole(plot, normalizedRoleId);
        if (role.isEmpty()) {
            return RoleResult.NOT_FOUND;
        }

        final LinkedHashSet<String> permissions = new LinkedHashSet<>(role.get().permissions());
        if (enabled) {
            permissions.add(permission.key());
        } else {
            permissions.remove(permission.key());
        }
        data.set(rolePath(plot, normalizedRoleId) + ".permissions", new ArrayList<>(permissions));
        save();
        return RoleResult.SUCCESS;
    }

    public RoleResult assignRole(final Plot plot, final UUID playerId, final String roleId) {
        if (plot.isOwner(playerId)) {
            return RoleResult.TARGET_OWNER;
        }
        final String normalizedRoleId = normalizeRoleId(roleId);
        if (OWNER_ROLE_ID.equals(normalizedRoleId)) {
            return RoleResult.PROTECTED_PERMISSION;
        }
        ensureDefaults(plot);
        if (!data.contains(rolePath(plot, normalizedRoleId))) {
            return RoleResult.NOT_FOUND;
        }

        data.set(playersPath(plot) + "." + playerId, normalizedRoleId);
        save();
        return RoleResult.SUCCESS;
    }

    public RoleResult unassignRole(final Plot plot, final UUID playerId) {
        if (plot.isOwner(playerId)) {
            return RoleResult.TARGET_OWNER;
        }
        ensureDefaults(plot);
        data.set(playersPath(plot) + "." + playerId, null);
        save();
        return RoleResult.SUCCESS;
    }

    public Optional<PlotRole> getEffectiveRole(final Player player, final Plot plot) {
        return getEffectiveRole(plot, player.getUniqueId());
    }

    public Optional<PlotRole> getEffectiveRole(final Plot plot, final UUID playerId) {
        ensureDefaults(plot);
        if (plot.isOwner(playerId)) {
            return getRole(plot, OWNER_ROLE_ID);
        }
        final String assignedRole = data.getString(playersPath(plot) + "." + playerId);
        if (assignedRole != null && !assignedRole.isBlank()) {
            final Optional<PlotRole> role = getRole(plot, assignedRole);
            if (role.isPresent()) {
                return role;
            }
        }
        if (plot.getTrusted().contains(playerId)) {
            return getRole(plot, TRUSTED_ROLE_ID);
        }
        return Optional.empty();
    }

    public RoleResult promote(final Plot plot, final UUID playerId) {
        if (plot.isOwner(playerId)) {
            return RoleResult.TARGET_OWNER;
        }

        final List<PlotRole> roles = assignableRoles(plot);
        if (roles.isEmpty()) {
            return RoleResult.ALREADY_AT_LIMIT;
        }

        final String currentRoleId = getEffectiveRole(plot, playerId).map(PlotRole::id).orElse("");
        final int currentIndex = indexOfRole(roles, currentRoleId);
        if (currentIndex == 0) {
            return RoleResult.ALREADY_AT_LIMIT;
        }

        final int targetIndex = currentIndex < 0 ? roles.size() - 1 : currentIndex - 1;
        return assignRole(plot, playerId, roles.get(targetIndex).id());
    }

    public RoleResult demote(final Plot plot, final UUID playerId) {
        if (plot.isOwner(playerId)) {
            return RoleResult.TARGET_OWNER;
        }

        final List<PlotRole> roles = assignableRoles(plot);
        final String currentRoleId = getEffectiveRole(plot, playerId).map(PlotRole::id).orElse("");
        final int currentIndex = indexOfRole(roles, currentRoleId);
        if (currentIndex < 0) {
            return RoleResult.ALREADY_AT_LIMIT;
        }
        if (currentIndex + 1 >= roles.size()) {
            return unassignRole(plot, playerId);
        }
        return assignRole(plot, playerId, roles.get(currentIndex + 1).id());
    }

    public boolean hasRolePermission(final Player player, final Plot plot, final PlotRolePermission permission) {
        return getEffectiveRole(player, plot)
                .map(role -> role.hasPermission(permission))
                .orElse(false);
    }

    public String permissionSummary(final PlotRole role) {
        if (OWNER_ROLE_ID.equals(role.id()) || role.permissions().contains("*")) {
            return "Alle Rechte";
        }
        if (role.permissions().isEmpty()) {
            return "Keine Rechte";
        }

        final List<String> names = new ArrayList<>();
        for (final String permissionKey : role.permissions()) {
            names.add(PlotRolePermission.fromKey(permissionKey)
                    .map(PlotRolePermission::displayName)
                    .orElse(permissionKey));
        }
        return String.join(", ", names);
    }

    public OfflinePlayer getOfflinePlayer(final String name) {
        return Bukkit.getOfflinePlayer(name);
    }

    public List<PlotRole> assignableRoles(final Plot plot) {
        return getRoles(plot).stream()
                .filter(role -> !OWNER_ROLE_ID.equals(role.id()))
                .toList();
    }

    public static String normalizeRoleId(final String roleId) {
        if (roleId == null) {
            return "";
        }
        return roleId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
    }

    public static boolean isProtectedRole(final String roleId) {
        final String normalizedRoleId = normalizeRoleId(roleId);
        return OWNER_ROLE_ID.equals(normalizedRoleId) || TRUSTED_ROLE_ID.equals(normalizedRoleId);
    }

    private void ensureDefaults(final Plot plot) {
        if (data == null) {
            load();
        }
        ensureRole(plot, OWNER_ROLE_ID, plugin.getConfig().getString("plot-roles.default-owner-name", "Plotinhaber"), List.of("*"));
        ensureRole(plot, TRUSTED_ROLE_ID, plugin.getConfig().getString("plot-roles.default-trusted-name", "Trusted"),
                plugin.getConfig().getStringList("plot-roles.default-trusted-permissions"));
    }

    private void ensureRole(final Plot plot, final String roleId, final String displayName, final List<String> permissions) {
        final String path = rolePath(plot, roleId);
        boolean changed = false;
        if (!data.contains(path + ".display")) {
            data.set(path + ".display", normalizeDisplayName(displayName, roleId));
            changed = true;
        }
        if (!data.contains(path + ".permissions")) {
            data.set(path + ".permissions", permissions);
            changed = true;
        }
        if (changed) {
            save();
        }
    }

    private PlotRole readRole(final String roleId, final ConfigurationSection section) {
        final LinkedHashSet<String> permissions = new LinkedHashSet<>();
        if (OWNER_ROLE_ID.equals(roleId)) {
            permissions.add("*");
        } else {
            for (final String permission : section.getStringList("permissions")) {
                final String normalizedPermission = PlotRolePermission.normalizeKey(permission);
                if (PlotRolePermission.fromKey(normalizedPermission).isPresent()) {
                    permissions.add(normalizedPermission);
                }
            }
        }
        return new PlotRole(
                roleId,
                section.getString("display", roleId),
                Set.copyOf(permissions),
                isProtectedRole(roleId)
        );
    }

    private String rolePath(final Plot plot, final String roleId) {
        return rolesPath(plot) + "." + normalizeRoleId(roleId);
    }

    private String rolesPath(final Plot plot) {
        return plotPath(plot) + ".roles";
    }

    private String playersPath(final Plot plot) {
        return plotPath(plot) + ".players";
    }

    private String plotPath(final Plot plot) {
        final Plot basePlot = plot.getBasePlot(false);
        final String rawKey = basePlot.getWorldName() + ":" + basePlot.getId();
        return "plots." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(rawKey.getBytes(StandardCharsets.UTF_8));
    }

    private int roleOrder(final String roleId) {
        if (OWNER_ROLE_ID.equals(roleId)) {
            return 0;
        }
        if (TRUSTED_ROLE_ID.equals(roleId)) {
            return 1;
        }
        return 2;
    }

    private int indexOfRole(final List<PlotRole> roles, final String roleId) {
        for (int index = 0; index < roles.size(); index++) {
            if (roles.get(index).id().equalsIgnoreCase(roleId)) {
                return index;
            }
        }
        return -1;
    }

    private boolean isValidRoleId(final String roleId) {
        return roleId.matches("[a-z0-9][a-z0-9_-]{1,31}");
    }

    private String normalizeDisplayName(final String displayName, final String fallback) {
        return displayName == null || displayName.isBlank() ? fallback : displayName.trim();
    }

    private void save() {
        try {
            data.save(file);
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not save plot role data.", exception);
        }
    }

    public enum RoleResult {
        SUCCESS,
        INVALID_ID,
        ALREADY_EXISTS,
        NOT_FOUND,
        PROTECTED,
        PROTECTED_PERMISSION,
        TARGET_OWNER,
        ALREADY_AT_LIMIT,
        NO_PERMISSION
    }
}
