package de.craftplay.plotextras.plot;

import com.plotsquared.core.player.PlotPlayer;
import com.plotsquared.core.plot.Plot;
import com.plotsquared.core.plot.flag.GlobalFlagContainer;
import com.plotsquared.core.plot.flag.InternalFlag;
import com.plotsquared.core.plot.flag.PlotFlag;
import com.plotsquared.core.plot.flag.types.BooleanFlag;
import com.plotsquared.core.queue.QueueCoordinator;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.biome.BiomeTypes;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlotService {

    private final JavaPlugin plugin;
    private final PlotRoleService roleService;
    private Set<String> excludedFlags = Set.of();
    private final Map<String, YamlConfiguration> componentConfigs = new HashMap<>();

    public PlotService(final JavaPlugin plugin, final PlotRoleService roleService) {
        this.plugin = plugin;
        this.roleService = roleService;
    }

    public void reload() {
        final Set<String> configuredExclusions = new HashSet<>();
        for (final String flag : plugin.getConfig().getStringList("flags.excluded")) {
            configuredExclusions.add(normalizeFlagName(flag));
        }
        excludedFlags = configuredExclusions;
        loadComponentConfigs();
    }

    public PlotPlayer<?> getPlotPlayer(final Player player) {
        try {
            return PlotPlayer.from(player);
        } catch (final RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not wrap Bukkit player for PlotSquared: " + player.getName(), exception);
            return null;
        }
    }

    public Plot getCurrentPlot(final Player player) {
        final PlotPlayer<?> plotPlayer = getPlotPlayer(player);
        return plotPlayer == null ? null : plotPlayer.getCurrentPlot();
    }

    public boolean canModifyFlags(final Player player, final Plot plot) {
        return hasPlotPermission(player, plot, PlotRolePermission.FLAGS);
    }

    public boolean canModifyComponents(final Player player, final Plot plot) {
        return canModifyComponent(player, plot, "wall") || canModifyComponent(player, plot, "border");
    }

    public boolean canModifyComponent(final Player player, final Plot plot, final String component) {
        final String normalizedComponent = component.toLowerCase(Locale.ROOT);
        if (normalizedComponent.equals("wall")) {
            return hasPlotPermission(player, plot, PlotRolePermission.DECOR_WALL);
        }
        if (normalizedComponent.equals("border")) {
            return hasPlotPermission(player, plot, PlotRolePermission.DECOR_BORDER);
        }
        return false;
    }

    public boolean canModifySettings(final Player player, final Plot plot) {
        return canModifySetting(player, plot, "home")
                || canModifySetting(player, plot, "weather")
                || canModifySetting(player, plot, "time")
                || canModifySetting(player, plot, "biome");
    }

    public boolean canModifySetting(final Player player, final Plot plot, final String setting) {
        return switch (setting.toLowerCase(Locale.ROOT)) {
            case "home" -> hasPlotPermission(player, plot, PlotRolePermission.SETTINGS_HOME);
            case "weather" -> hasPlotPermission(player, plot, PlotRolePermission.SETTINGS_WEATHER);
            case "time" -> hasPlotPermission(player, plot, PlotRolePermission.SETTINGS_TIME);
            case "biome" -> hasPlotPermission(player, plot, PlotRolePermission.SETTINGS_BIOME);
            default -> hasPlotPermission(player, plot, PlotRolePermission.FLAGS);
        };
    }

    public boolean canManageRoles(final Player player, final Plot plot) {
        return isOwnerOfWholeConnectedPlot(player, plot) || player.hasPermission("craftplayplotextras.admin");
    }

    public boolean canPromoteMembers(final Player player, final Plot plot) {
        return hasPlotPermission(player, plot, PlotRolePermission.MEMBERS_PROMOTE);
    }

    public boolean canDemoteMembers(final Player player, final Plot plot) {
        return hasPlotPermission(player, plot, PlotRolePermission.MEMBERS_DEMOTE);
    }

    public boolean canInviteMembers(final Player player, final Plot plot) {
        return hasPlotPermission(player, plot, PlotRolePermission.MEMBERS_INVITE);
    }

    public boolean canUntrustMembers(final Player player, final Plot plot) {
        return hasPlotPermission(player, plot, PlotRolePermission.MEMBERS_UNTRUST);
    }

    private boolean hasPlotPermission(final Player player, final Plot plot, final PlotRolePermission permission) {
        if (plot == null) {
            return false;
        }
        if (player.hasPermission("craftplayplotextras.admin")) {
            return true;
        }
        if (!isConnectedPlotOwnedByOneOwner(plot)) {
            return false;
        }
        if (isOwnerOfWholeConnectedPlot(player, plot)) {
            return true;
        }
        return roleService.hasRolePermission(player, plot, permission);
    }

    private boolean isOwnerOfWholeConnectedPlot(final Player player, final Plot plot) {
        if (plot == null) {
            return false;
        }
        final UUID playerId = player.getUniqueId();
        if (!plot.isOwner(playerId)) {
            return false;
        }
        for (final Plot connectedPlot : plot.getConnectedPlots()) {
            if (!connectedPlot.isOwner(playerId)) {
                return false;
            }
        }
        return true;
    }

    private boolean isConnectedPlotOwnedByOneOwner(final Plot plot) {
        final UUID owner = plot.getOwnerAbs();
        if (owner == null) {
            return false;
        }
        for (final Plot connectedPlot : plot.getConnectedPlots()) {
            if (!connectedPlot.isOwner(owner)) {
                return false;
            }
        }
        return true;
    }

    public List<FlagEntry> getAvailableFlags(final Player player) {
        final LinkedHashSet<String> names = new LinkedHashSet<>();
        if (plugin.getConfig().getBoolean("flags.auto-detect-boolean-flags", true)) {
            for (final PlotFlag<?, ?> flag : GlobalFlagContainer.getInstance().getRecognizedPlotFlags()) {
                if (flag instanceof BooleanFlag<?> && !(flag instanceof InternalFlag)) {
                    names.add(normalizeFlagName(flag.getName()));
                }
            }
        }
        for (final String configuredFlag : plugin.getConfig().getStringList("flags.enabled")) {
            names.add(normalizeFlagName(configuredFlag));
        }
        names.removeAll(excludedFlags);

        final List<FlagEntry> entries = new ArrayList<>();
        for (final String name : names) {
            if (GlobalFlagContainer.getInstance().getFlagClassFromString(name) != null && canUseFlag(player, name)) {
                entries.add(new FlagEntry(name, getFlagDisplayName(name), getFlagDescription(name)));
            }
        }
        entries.sort(Comparator.comparing(FlagEntry::displayName, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    public boolean isFlagEnabled(final Player player, final String flagName) {
        final Plot plot = getCurrentPlot(player);
        if (plot == null) {
            return false;
        }
        return isFlagEnabled(plot, flagName);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean isFlagEnabled(final Plot plot, final String flagName) {
        final Class flagClass = GlobalFlagContainer.getInstance().getFlagClassFromString(normalizeFlagName(flagName));
        if (flagClass == null) {
            return false;
        }
        final Object value = plot.getFlag(flagClass);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public Boolean toggleFlag(final Player player, final String flagName) {
        final Plot plot = getCurrentPlot(player);
        if (!canModifyFlags(player, plot) || !canUseFlag(player, flagName)) {
            return null;
        }
        final boolean enabled = !isFlagEnabled(plot, flagName);
        if (!setFlag(plot, flagName, enabled)) {
            return null;
        }
        return enabled;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean setFlag(final Plot plot, final String flagName, final boolean enabled) {
        return setSingleBooleanFlag(plot, flagName, enabled);
    }

    public boolean setBooleanFlagOnConnectedPlots(final Plot plot, final String flagName, final boolean enabled) {
        if (plot == null) {
            return false;
        }

        boolean changed = false;
        final Plot basePlot = plot.getBasePlot(false);
        for (final Plot connectedPlot : basePlot.getConnectedPlots()) {
            changed |= setSingleBooleanFlag(connectedPlot, flagName, enabled);
        }
        return changed;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean setSingleBooleanFlag(final Plot plot, final String flagName, final boolean enabled) {
        if (plot == null) {
            return false;
        }
        final Class flagClass = GlobalFlagContainer.getInstance().getFlagClassFromString(normalizeFlagName(flagName));
        if (flagClass == null) {
            return false;
        }
        return plot.setFlag(flagClass, Boolean.toString(enabled));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean setFlagValue(final Player player, final String flagName, final String value) {
        final Plot plot = getCurrentPlot(player);
        if (!canModifySetting(player, plot, flagName)) {
            return false;
        }
        final Class flagClass = GlobalFlagContainer.getInstance().getFlagClassFromString(normalizeFlagName(flagName));
        if (flagClass == null) {
            return false;
        }
        if (isResetValue(value)) {
            plot.removeFlag(flagClass);
            return true;
        }
        return plot.setFlag(flagClass, value);
    }

    public boolean setBiome(final Player player, final String biomeName) {
        final Plot plot = getCurrentPlot(player);
        if (!canModifySetting(player, plot, "biome")) {
            return false;
        }

        final BiomeType biome = isResetValue(biomeName)
                ? plot.getArea().getPlotBiome()
                : BiomeTypes.get(biomeName.toLowerCase(Locale.ROOT));
        if (biome == null) {
            return false;
        }

        try {
            plot.getPlotModificationManager().setBiome(biome, () -> {
            });
            return true;
        } catch (final RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not set plot biome to '" + biomeName + "'.", exception);
            return false;
        }
    }

    public boolean setHome(final Player player) {
        final Plot plot = getCurrentPlot(player);
        if (!canModifySetting(player, plot, "home")) {
            return false;
        }

        final PlotPlayer<?> plotPlayer = getPlotPlayer(player);
        if (plotPlayer == null) {
            return false;
        }

        final com.plotsquared.core.location.Location playerLocation = plotPlayer.getLocationFull();
        final com.plotsquared.core.location.Location bottomLocation = plot.getBasePlot(false).getBottomAbs();
        plot.getBasePlot(false).setHome(new com.plotsquared.core.location.BlockLoc(
                playerLocation.getX() - bottomLocation.getX(),
                playerLocation.getY(),
                playerLocation.getZ() - bottomLocation.getZ(),
                playerLocation.getYaw(),
                playerLocation.getPitch()
        ));
        return true;
    }

    public boolean teleportHome(final Player player) {
        final Plot plot = getCurrentPlot(player);
        if (plot == null) {
            return false;
        }

        final com.plotsquared.core.location.Location home = plot.getHomeSynchronous();
        final org.bukkit.World world = Bukkit.getWorld(home.getWorldName());
        if (world == null) {
            return false;
        }

        return player.teleport(new org.bukkit.Location(
                world,
                home.getX() + 0.5D,
                home.getY(),
                home.getZ() + 0.5D,
                home.getYaw(),
                home.getPitch()
        ));
    }

    public boolean setComponent(final Player player, final String component, final String pattern) {
        final Plot plot = getCurrentPlot(player);
        if (!canModifyComponent(player, plot, component)) {
            return false;
        }
        final PlotPlayer<?> plotPlayer = getPlotPlayer(player);
        if (plotPlayer == null) {
            return false;
        }

        boolean runningAdded = false;
        try {
            plot.addRunning();
            runningAdded = true;
            final QueueCoordinator queue = plot.getArea().getQueue();
            queue.setCompleteTask(() -> plot.removeRunning());

            boolean changed = false;
            for (final Plot connectedPlot : plot.getConnectedPlots()) {
                changed |= connectedPlot.getPlotModificationManager().setComponent(
                        component.toLowerCase(Locale.ROOT),
                        pattern,
                        plotPlayer,
                        queue
                );
            }
            if (!changed) {
                plot.removeRunning();
                return false;
            }

            queue.enqueue();
            runningAdded = false;
            return true;
        } catch (final RuntimeException exception) {
            if (runningAdded) {
                plot.removeRunning();
            }
            plugin.getLogger().log(Level.WARNING, "Could not set plot component '" + component + "' to '" + pattern + "'.", exception);
            return false;
        }
    }

    public Map<String, String> getPlotPlaceholders(final Player player) {
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("world", player.getWorld().getName());

        final PlotPlayer<?> plotPlayer = getPlotPlayer(player);
        final Plot plot = plotPlayer == null ? null : plotPlayer.getCurrentPlot();
        final int plotLimit = getPlotLimit(player);
        placeholders.put("plot_id", plot == null ? "-" : plot.getId().toString());
        placeholders.put("plot_world", plot == null ? "-" : plot.getWorldName());
        placeholders.put("plot_owner", plot == null || plot.getOwnerAbs() == null ? "-" : getName(plot.getOwnerAbs()));
        placeholders.put("plot_members", plot == null ? "0" : String.valueOf(plot.getMembers().size()));
        placeholders.put("plot_trusted", plot == null ? "0" : String.valueOf(plot.getTrusted().size()));
        placeholders.put("plot_denied", plot == null ? "0" : String.valueOf(plot.getDenied().size()));
        final int plotsquaredMaxPlots = plotPlayer == null ? 0 : plotPlayer.getAllowedPlots();
        placeholders.put("plot_count", plotPlayer == null ? "0" : String.valueOf(plotPlayer.getPlotCount()));
        placeholders.put("plotsquared_plot_max", String.valueOf(plotsquaredMaxPlots));
        placeholders.put("configured_plot_max", String.valueOf(plotLimit));
        placeholders.put("plot_max", String.valueOf(plotLimit));
        final String roleDisplay = plot == null
                ? "-"
                : roleService.getEffectiveRole(player, plot).map(PlotRole::displayName).orElse("-");
        placeholders.put("plot_role", roleDisplay);
        return placeholders;
    }

    public List<PlotRole> getRoleEntries(final Player player) {
        final Plot plot = getCurrentPlot(player);
        return plot == null ? List.of() : roleService.getRoles(plot);
    }

    public Optional<PlotRole> getRoleEntry(final Player player, final String roleId) {
        final Plot plot = getCurrentPlot(player);
        return plot == null ? Optional.empty() : roleService.getRole(plot, roleId);
    }

    public List<PlotRole> getAssignableRoleEntries(final Player player) {
        final Plot plot = getCurrentPlot(player);
        return plot == null ? List.of() : roleService.assignableRoles(plot);
    }

    public String getMemberRoleId(final Player player, final UUID memberId) {
        final Plot plot = getCurrentPlot(player);
        return plot == null ? "-" : roleService.getEffectiveRole(plot, memberId).map(PlotRole::id).orElse("-");
    }

    public String getMemberRoleDisplay(final Player player, final UUID memberId) {
        final Plot plot = getCurrentPlot(player);
        return plot == null ? "-" : roleService.getEffectiveRole(plot, memberId).map(PlotRole::displayName).orElse("-");
    }

    public String getRolePermissionSummary(final PlotRole role) {
        return roleService.permissionSummary(role);
    }

    public PlotRoleService.RoleResult createRole(final Player player, final String roleId, final String displayName) {
        final Plot plot = getCurrentPlot(player);
        if (!canManageRoles(player, plot)) {
            return PlotRoleService.RoleResult.NO_PERMISSION;
        }
        return roleService.createRole(plot, roleId, displayName);
    }

    public PlotRoleService.RoleResult renameRole(final Player player, final String roleId, final String displayName) {
        final Plot plot = getCurrentPlot(player);
        if (!canManageRoles(player, plot)) {
            return PlotRoleService.RoleResult.NO_PERMISSION;
        }
        return roleService.renameRole(plot, roleId, displayName);
    }

    public PlotRoleService.RoleResult deleteRole(final Player player, final String roleId) {
        final Plot plot = getCurrentPlot(player);
        if (!canManageRoles(player, plot)) {
            return PlotRoleService.RoleResult.NO_PERMISSION;
        }
        return roleService.deleteRole(plot, roleId);
    }

    public PlotRoleService.RoleResult setRolePermission(
            final Player player,
            final String roleId,
            final PlotRolePermission permission,
            final boolean enabled
    ) {
        final Plot plot = getCurrentPlot(player);
        if (!canManageRoles(player, plot)) {
            return PlotRoleService.RoleResult.NO_PERMISSION;
        }
        return roleService.setPermission(plot, roleId, permission, enabled);
    }

    public PlotRoleService.RoleResult assignRole(final Player player, final UUID targetId, final String roleId) {
        final Plot plot = getCurrentPlot(player);
        if (!canManageRoles(player, plot)) {
            return PlotRoleService.RoleResult.NO_PERMISSION;
        }
        return roleService.assignRole(plot, targetId, roleId);
    }

    public PlotRoleService.RoleResult unassignRole(final Player player, final UUID targetId) {
        final Plot plot = getCurrentPlot(player);
        if (!canManageRoles(player, plot) && !canDemoteMembers(player, plot)) {
            return PlotRoleService.RoleResult.NO_PERMISSION;
        }
        return roleService.unassignRole(plot, targetId);
    }

    public PlotRoleService.RoleResult promoteMember(final Player player, final UUID targetId) {
        final Plot plot = getCurrentPlot(player);
        if (!canPromoteMembers(player, plot)) {
            return PlotRoleService.RoleResult.NO_PERMISSION;
        }
        return roleService.promote(plot, targetId);
    }

    public PlotRoleService.RoleResult demoteMember(final Player player, final UUID targetId) {
        final Plot plot = getCurrentPlot(player);
        if (!canDemoteMembers(player, plot)) {
            return PlotRoleService.RoleResult.NO_PERMISSION;
        }
        return roleService.demote(plot, targetId);
    }

    public PlotRoleService.RoleResult inviteMember(final Player player, final UUID targetId) {
        final Plot plot = getCurrentPlot(player);
        if (!canInviteMembers(player, plot)) {
            return PlotRoleService.RoleResult.NO_PERMISSION;
        }
        if (plot.isOwner(targetId)) {
            return PlotRoleService.RoleResult.TARGET_OWNER;
        }
        for (final Plot connectedPlot : plot.getConnectedPlots()) {
            connectedPlot.removeDenied(targetId);
            if (!connectedPlot.getTrusted().contains(targetId)) {
                connectedPlot.addTrusted(targetId);
            }
        }
        return PlotRoleService.RoleResult.SUCCESS;
    }

    public PlotRoleService.RoleResult untrustMember(final Player player, final UUID targetId) {
        final Plot plot = getCurrentPlot(player);
        if (!canUntrustMembers(player, plot)) {
            return PlotRoleService.RoleResult.NO_PERMISSION;
        }
        if (plot.isOwner(targetId)) {
            return PlotRoleService.RoleResult.TARGET_OWNER;
        }
        for (final Plot connectedPlot : plot.getConnectedPlots()) {
            connectedPlot.removeTrusted(targetId);
            connectedPlot.removeMember(targetId);
            connectedPlot.removeDenied(targetId);
        }
        roleService.unassignRole(plot, targetId);
        return PlotRoleService.RoleResult.SUCCESS;
    }

    public int getPlotLimit(final Player player) {
        final String patternText = plugin.getConfig().getString("plot-limits.permission-pattern", "^plots\\.plot\\.(\\d+)$");
        final Pattern pattern = Pattern.compile(patternText, Pattern.CASE_INSENSITIVE);
        int highestLimit = -1;
        for (final PermissionAttachmentInfo permissionInfo : player.getEffectivePermissions()) {
            if (!permissionInfo.getValue()) {
                continue;
            }
            final Matcher matcher = pattern.matcher(permissionInfo.getPermission());
            if (matcher.matches()) {
                highestLimit = Math.max(highestLimit, Integer.parseInt(matcher.group(1)));
            }
        }

        if (highestLimit >= 0) {
            return highestLimit;
        }

        final int scanMax = Math.max(0, plugin.getConfig().getInt("plot-limits.scan-max", 250));
        final String checkFormat = plugin.getConfig().getString("plot-limits.permission-check-format", "plots.plot.{limit}");
        for (int limit = 0; limit <= scanMax; limit++) {
            if (player.hasPermission(checkFormat.replace("{limit}", String.valueOf(limit)))) {
                highestLimit = Math.max(highestLimit, limit);
            }
        }

        if (highestLimit >= 0) {
            return highestLimit;
        }

        final PlotPlayer<?> plotPlayer = getPlotPlayer(player);
        final int fallback = plotPlayer == null ? 0 : plotPlayer.getAllowedPlots();
        return plugin.getConfig().getInt("plot-limits.default", fallback);
    }

    public boolean isAtPlotLimit(final Player player, final int plotsToAdd) {
        final PlotPlayer<?> plotPlayer = getPlotPlayer(player);
        if (plotPlayer == null) {
            return true;
        }
        return plotPlayer.getPlotCount() + plotsToAdd > getPlotLimit(player);
    }

    public List<MemberEntry> getMemberEntries(final Player player) {
        final Plot plot = getCurrentPlot(player);
        final List<MemberEntry> entries = new ArrayList<>();
        if (plot == null) {
            return entries;
        }
        if (plot.getOwnerAbs() != null) {
            entries.add(new MemberEntry("owner", plot.getOwnerAbs(), getName(plot.getOwnerAbs())));
        }
        addMembers(entries, "member", plot.getMembers());
        addMembers(entries, "trusted", plot.getTrusted());
        addMembers(entries, "denied", plot.getDenied());
        return entries;
    }

    private void addMembers(final List<MemberEntry> entries, final String type, final Set<UUID> uuids) {
        for (final UUID uuid : uuids) {
            entries.add(new MemberEntry(type, uuid, getName(uuid)));
        }
    }

    public List<ComponentOption> getComponentOptions(final String component) {
        final ConfigurationSection componentSection = getComponentSection(component);
        final ConfigurationSection optionsSection = componentSection == null ? null : componentSection.getConfigurationSection("options");
        final List<ComponentOption> options = new ArrayList<>();
        if (optionsSection == null) {
            return options;
        }
        for (final String key : optionsSection.getKeys(false)) {
            final ConfigurationSection section = optionsSection.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            if (!section.getBoolean("enabled", true)) {
                continue;
            }
            final String category = section.getString("category", "default");
            final ConfigurationSection categorySection = componentSection.getConfigurationSection("categories." + category);
            final int permissionGroup = section.getInt("permission-group", 0);
            final String permission = resolveComponentPermission(component, section, permissionGroup);
            options.add(new ComponentOption(
                    component,
                    key,
                    section.getString("display", key),
                    section.getString("pattern", section.getString("material", key)),
                    category,
                    categorySection == null ? category : categorySection.getString("display", category),
                    permissionGroup,
                    permission,
                    section
            ));
        }
        return options;
    }

    public List<ComponentCategory> getComponentCategories(final String component, final Player player) {
        final ConfigurationSection componentSection = getComponentSection(component);
        final ConfigurationSection categoriesSection = componentSection == null ? null : componentSection.getConfigurationSection("categories");
        final List<ComponentCategory> categories = new ArrayList<>();
        if (categoriesSection == null) {
            return categories;
        }

        for (final String key : categoriesSection.getKeys(false)) {
            final ConfigurationSection section = categoriesSection.getConfigurationSection(key);
            if (section == null) {
                continue;
            }

            int visibleOptions = 0;
            for (final ComponentOption option : getComponentOptions(component)) {
                if (option.category().equalsIgnoreCase(key) && canUseComponentOption(player, option)) {
                    visibleOptions++;
                }
            }
            if (visibleOptions <= 0) {
                continue;
            }

            categories.add(new ComponentCategory(
                    component,
                    key,
                    section.getString("display", key),
                    visibleOptions,
                    section
            ));
        }
        return categories;
    }

    public boolean canUseComponentOption(final Player player, final ComponentOption option) {
        if (player.hasPermission("craftplayplotextras.admin")) {
            return true;
        }
        return option.permission().isBlank() || player.hasPermission(option.permission());
    }

    public String getComponentDisplayName(final String component) {
        final ConfigurationSection section = getComponentSection(component);
        return section == null ? toTitle(component) : section.getString("display", toTitle(component));
    }

    public boolean canUseFlag(final Player player, final String flagName) {
        if (player.hasPermission("craftplayplotextras.admin")) {
            return true;
        }
        if (!plugin.getConfig().getBoolean("flags.require-permission", true)) {
            return true;
        }

        final String normalizedFlag = normalizeFlagName(flagName);
        final String dottedFlag = normalizedFlag.replace('-', '.');
        for (final String format : plugin.getConfig().getStringList("flags.permission-formats")) {
            final String permission = format
                    .replace("{flag}", normalizedFlag)
                    .replace("{flag_dot}", dottedFlag);
            if (player.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    private String getName(final UUID uuid) {
        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        final String name = offlinePlayer.getName();
        return name == null ? uuid.toString().substring(0, 8) : name;
    }

    private String getFlagDisplayName(final String name) {
        return plugin.getConfig().getString("flags.display-names." + name, toTitle(name));
    }

    private String getFlagDescription(final String name) {
        return plugin.getConfig().getString("flags.descriptions." + name, "Keine Beschreibung konfiguriert.");
    }

    private String resolveComponentPermission(final String component, final ConfigurationSection section, final int permissionGroup) {
        final String directPermission = section.getString("permission", "");
        if (!directPermission.isBlank()) {
            return directPermission;
        }
        if (permissionGroup <= 0) {
            return "";
        }
        final ConfigurationSection componentSection = getComponentSection(component);
        return componentSection == null
                ? ""
                : componentSection.getString("permission-groups." + permissionGroup + ".permission", "");
    }

    private void loadComponentConfigs() {
        componentConfigs.clear();
        for (final String component : List.of("wall", "border")) {
            final File rootFile = new File(plugin.getDataFolder(), component + ".yml");
            final File legacyFile = new File(new File(plugin.getDataFolder(), "components"), component + ".yml");
            final File file = rootFile.exists() ? rootFile : legacyFile;
            if (file.exists()) {
                componentConfigs.put(component, YamlConfiguration.loadConfiguration(file));
            }
        }
    }

    private ConfigurationSection getComponentSection(final String component) {
        final String normalizedComponent = component.toLowerCase(Locale.ROOT);
        final YamlConfiguration componentConfig = componentConfigs.get(normalizedComponent);
        if (componentConfig != null) {
            return componentConfig;
        }
        return plugin.getConfig().getConfigurationSection("plot-components." + normalizedComponent);
    }

    private String normalizeFlagName(final String flagName) {
        return flagName == null ? "" : flagName.toLowerCase(Locale.ROOT).replace('_', '-').trim();
    }

    private boolean isResetValue(final String value) {
        final String normalizedValue = value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
        return normalizedValue.equals("reset") || normalizedValue.equals("default") || normalizedValue.equals("remove");
    }

    private String toTitle(final String value) {
        final StringBuilder builder = new StringBuilder();
        for (final String part : value.replace('_', '-').split("-")) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    public record ComponentOption(
            String component,
            String id,
            String display,
            String pattern,
            String category,
            String categoryDisplay,
            int permissionGroup,
            String permission,
            ConfigurationSection section
    ) {
    }

    public record ComponentCategory(
            String component,
            String id,
            String display,
            int visibleOptions,
            ConfigurationSection section
    ) {
    }
}
