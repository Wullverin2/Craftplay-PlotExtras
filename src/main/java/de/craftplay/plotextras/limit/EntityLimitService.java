package de.craftplay.plotextras.limit;

import com.plotsquared.bukkit.util.BukkitUtil;
import com.plotsquared.core.plot.Plot;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import de.craftplay.plotextras.feature.FeatureToggleService;
import de.craftplay.plotextras.language.LanguageManager;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class EntityLimitService implements Listener {

    private final JavaPlugin plugin;
    private final LanguageManager languageManager;
    private final FeatureToggleService featureToggleService;
    private final File limitsFile;
    private Settings settings = Settings.defaults();
    private final Map<String, LimitRule> limitRules = new LinkedHashMap<>();

    public EntityLimitService(final JavaPlugin plugin, final LanguageManager languageManager, final FeatureToggleService featureToggleService) {
        this.plugin = plugin;
        this.languageManager = languageManager;
        this.featureToggleService = featureToggleService;
        this.limitsFile = new File(plugin.getDataFolder(), "limits.yml");
    }

    public void reload() {
        final YamlConfiguration config = YamlConfiguration.loadConfiguration(limitsFile);
        settings = Settings.from(config.getConfigurationSection("settings"));
        limitRules.clear();

        final ConfigurationSection limitsSection = config.getConfigurationSection("limits");
        if (limitsSection == null) {
            return;
        }

        for (final String id : limitsSection.getKeys(false)) {
            final ConfigurationSection section = limitsSection.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled", true)) {
                continue;
            }
            final LimitRule rule = LimitRule.from(id, section);
            limitRules.put(rule.id(), rule);
        }
    }

    public List<EntityLimitEntry> getEntries(final Player player) {
        final Plot plot = getCurrentPlot(player);
        final List<EntityLimitEntry> entries = new ArrayList<>();
        if (!featureToggleService.isEnabled("player.entity-limits") || !featureToggleService.isEnabled("limits.gui")) {
            return entries;
        }
        for (final LimitRule rule : limitRules.values()) {
            if (isSuppressedBroadRule(rule)) {
                continue;
            }
            final int count = plot == null ? 0 : countEntities(plot, rule, null);
            final int limit = limitFor(player, rule);
            entries.add(EntityLimitEntry.from(rule, count, limit));
        }
        return entries;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPlace(final EntityPlaceEvent event) {
        final LimitCheck check = check(event.getPlayer(), event.getEntity().getType(), event.getEntity().getUniqueId(), event.getEntity().getLocation());
        if (check.allowed()) {
            return;
        }
        event.setCancelled(true);
        sendLimitMessage(event.getPlayer(), check);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingPlace(final HangingPlaceEvent event) {
        final LimitCheck check = check(event.getPlayer(), event.getEntity().getType(), event.getEntity().getUniqueId(), event.getEntity().getLocation());
        if (check.allowed()) {
            return;
        }
        event.setCancelled(true);
        sendLimitMessage(event.getPlayer(), check);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(final CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            return;
        }
        if (!settings.limitedSpawnReasons().contains(event.getSpawnReason())) {
            return;
        }
        final LimitCheck check = check(null, event.getEntity().getType(), event.getEntity().getUniqueId(), event.getEntity().getLocation());
        if (!check.allowed()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityBreed(final EntityBreedEvent event) {
        final Player player = event.getBreeder() instanceof Player breeder ? breeder : null;
        final LimitCheck check = check(player, event.getEntity().getType(), event.getEntity().getUniqueId(), event.getEntity().getLocation());
        if (check.allowed()) {
            return;
        }
        event.setCancelled(true);
        if (player != null) {
            sendLimitMessage(player, check);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(final ItemSpawnEvent event) {
        final LimitCheck check = check(null, event.getEntity().getType(), event.getEntity().getUniqueId(), event.getLocation());
        if (!check.allowed()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunch(final ProjectileLaunchEvent event) {
        final ProjectileSource shooter = event.getEntity().getShooter();
        final Player player = shooter instanceof Player shooterPlayer ? shooterPlayer : null;
        final LimitCheck check = check(player, event.getEntity().getType(), event.getEntity().getUniqueId(), event.getEntity().getLocation());
        if (check.allowed()) {
            return;
        }
        event.setCancelled(true);
        if (player != null) {
            sendLimitMessage(player, check);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGenericEntitySpawn(final EntitySpawnEvent event) {
        if (event instanceof CreatureSpawnEvent || event instanceof ItemSpawnEvent || event instanceof ProjectileLaunchEvent) {
            return;
        }
        final LimitCheck check = check(null, event.getEntity().getType(), event.getEntity().getUniqueId(), event.getLocation());
        if (!check.allowed()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnEggUse(final PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR)) {
            return;
        }
        final EntityType entityType = entityTypeFromSpawnEgg(event.getMaterial());
        if (entityType == null) {
            return;
        }
        final Location spawnLocation = spawnEggLocation(event);
        if (spawnLocation == null) {
            return;
        }

        final LimitCheck check = check(event.getPlayer(), entityType, null, spawnLocation);
        if (check.allowed()) {
            return;
        }
        event.setCancelled(true);
        sendLimitMessage(event.getPlayer(), check);
    }

    private LimitCheck check(final Player player, final EntityType entityType, final UUID ignoredEntityId, final Location location) {
        if (!settings.enabled() || !featureToggleService.isEnabled("limits.enforce") || limitRules.isEmpty() || hasBypass(player)) {
            return LimitCheck.permitted();
        }

        final Plot plot = getPlot(location);
        if (plot == null || !plot.hasOwner() || !isCountable(entityType)) {
            return LimitCheck.permitted();
        }

        for (final LimitRule rule : matchingRules(entityType)) {
            final int limit = limitFor(player, rule);
            if (limit < 0) {
                continue;
            }
            final int current = countEntities(plot, rule, ignoredEntityId);
            if (current + 1 > limit) {
                return LimitCheck.denied(rule, current, limit);
            }
        }
        return LimitCheck.permitted();
    }

    private List<LimitRule> matchingRules(final EntityType entityType) {
        final List<LimitRule> totalRules = new ArrayList<>();
        final List<LimitRule> singleEntityRules = new ArrayList<>();
        final List<LimitRule> broadRules = new ArrayList<>();
        for (final LimitRule rule : limitRules.values()) {
            if (!rule.matches(entityType)) {
                continue;
            }
            if (rule.isTotal()) {
                totalRules.add(rule);
            } else if (rule.isSingleEntityRuleFor(entityType)) {
                singleEntityRules.add(rule);
            } else {
                broadRules.add(rule);
            }
        }

        final List<LimitRule> rules = new ArrayList<>(totalRules);
        rules.addAll(singleEntityRules);
        if (!settings.preferSingleEntityLimits() || singleEntityRules.isEmpty()) {
            rules.addAll(broadRules);
        }
        return rules;
    }

    private boolean isSuppressedBroadRule(final LimitRule rule) {
        if (!settings.preferSingleEntityLimits() || rule.isTotal() || rule.types().size() <= 1) {
            return false;
        }
        for (final EntityType type : rule.types()) {
            if (!hasSingleEntityRuleFor(type)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasSingleEntityRuleFor(final EntityType entityType) {
        for (final LimitRule rule : limitRules.values()) {
            if (rule.isSingleEntityRuleFor(entityType)) {
                return true;
            }
        }
        return false;
    }

    private int countEntities(final Plot plot, final LimitRule rule, final UUID ignoredEntityId) {
        final World world = plugin.getServer().getWorld(plot.getWorldName());
        if (world == null) {
            return 0;
        }

        int count = 0;
        for (final Entity entity : world.getEntities()) {
            if (ignoredEntityId != null && entity.getUniqueId().equals(ignoredEntityId)) {
                continue;
            }
            if (!isCountable(entity.getType()) || !rule.matches(entity.getType()) || !isInsidePlot(entity.getLocation(), plot)) {
                continue;
            }
            count++;
        }
        return count;
    }

    private boolean isInsidePlot(final Location location, final Plot plot) {
        if (location.getWorld() == null || !location.getWorld().getName().equals(plot.getWorldName())) {
            return false;
        }

        final BlockVector3 point = BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        if (!settings.countMergedPlots()) {
            return contains(plot, point);
        }
        for (final Plot connectedPlot : plot.getConnectedPlots()) {
            if (contains(connectedPlot, point)) {
                return true;
            }
        }
        return false;
    }

    private boolean contains(final Plot plot, final BlockVector3 point) {
        try {
            final CuboidRegion region = plot.getLargestRegion();
            return region != null && region.contains(point);
        } catch (final RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not read PlotSquared region for entity limit.", exception);
            return false;
        }
    }

    private Plot getCurrentPlot(final Player player) {
        try {
            return com.plotsquared.core.player.PlotPlayer.from(player).getCurrentPlot();
        } catch (final RuntimeException exception) {
            return null;
        }
    }

    private Plot getPlot(final Location location) {
        try {
            return Plot.getPlot(BukkitUtil.adapt(location));
        } catch (final RuntimeException exception) {
            return null;
        }
    }

    private boolean isCountable(final EntityType entityType) {
        return entityType != EntityType.PLAYER && !settings.excludedTypes().contains(entityType);
    }

    private EntityType entityTypeFromSpawnEgg(final Material material) {
        final String materialName = material.name();
        if (!materialName.endsWith("_SPAWN_EGG")) {
            return null;
        }
        final String entityTypeName = materialName.substring(0, materialName.length() - "_SPAWN_EGG".length());
        try {
            return EntityType.valueOf(entityTypeName);
        } catch (final IllegalArgumentException exception) {
            return null;
        }
    }

    private Location spawnEggLocation(final PlayerInteractEvent event) {
        final Block clickedBlock = event.getClickedBlock();
        if (clickedBlock != null) {
            return clickedBlock.getRelative(event.getBlockFace()).getLocation().add(0.5D, 0D, 0.5D);
        }
        return event.getPlayer().getLocation();
    }

    private int limitFor(final Player player, final LimitRule rule) {
        if (hasBypass(player)) {
            return -1;
        }
        int limit = rule.max();
        if (player == null) {
            return limit;
        }
        for (final PermissionLimit permissionLimit : rule.permissionLimits()) {
            if (!permissionLimit.permission().isBlank() && player.hasPermission(permissionLimit.permission())) {
                if (permissionLimit.limit() < 0) {
                    return -1;
                }
                limit = Math.max(limit, permissionLimit.limit());
            }
        }
        return limit;
    }

    private void sendLimitMessage(final Player player, final LimitCheck check) {
        languageManager.send(player, "entity-limit-reached", Map.of(
                "limit", check.rule().display(),
                "count", String.valueOf(check.count()),
                "max", String.valueOf(check.max())
        ));
    }

    private boolean hasBypass(final Player player) {
        return player != null
                && (player.hasPermission("craftplayplotextras.admin") || player.hasPermission(settings.bypassPermission()));
    }

    public record EntityLimitEntry(
            String id,
            String display,
            String description,
            String material,
            int count,
            String max,
            String remaining,
            boolean exceeded
    ) {

        private static EntityLimitEntry from(final LimitRule rule, final int count, final int limit) {
            final boolean unlimited = limit < 0;
            final int remaining = unlimited ? -1 : Math.max(0, limit - count);
            return new EntityLimitEntry(
                    rule.id(),
                    rule.display(),
                    rule.description(),
                    rule.material(),
                    count,
                    unlimited ? "unbegrenzt" : String.valueOf(limit),
                    unlimited ? "unbegrenzt" : String.valueOf(remaining),
                    !unlimited && count >= limit
            );
        }
    }

    private record LimitCheck(boolean allowed, LimitRule rule, int count, int max) {

        private static LimitCheck permitted() {
            return new LimitCheck(true, null, 0, 0);
        }

        private static LimitCheck denied(final LimitRule rule, final int count, final int max) {
            return new LimitCheck(false, rule, count, max);
        }
    }

    private record Settings(
            boolean enabled,
            boolean countMergedPlots,
            boolean preferSingleEntityLimits,
            String bypassPermission,
            Set<EntityType> excludedTypes,
            Set<CreatureSpawnEvent.SpawnReason> limitedSpawnReasons
    ) {

        private static Settings defaults() {
            return new Settings(
                    true,
                    true,
                    true,
                    "craftplayplotextras.entitylimit.bypass",
                    Set.of(EntityType.PLAYER, EntityType.UNKNOWN),
                    Set.of(
                            CreatureSpawnEvent.SpawnReason.EGG,
                            CreatureSpawnEvent.SpawnReason.DISPENSE_EGG,
                            CreatureSpawnEvent.SpawnReason.BUCKET,
                            CreatureSpawnEvent.SpawnReason.BREEDING,
                            CreatureSpawnEvent.SpawnReason.BUILD_IRONGOLEM,
                            CreatureSpawnEvent.SpawnReason.BUILD_SNOWMAN,
                            CreatureSpawnEvent.SpawnReason.BUILD_WITHER
                    )
            );
        }

        private static Settings from(final ConfigurationSection section) {
            final Settings defaults = defaults();
            if (section == null) {
                return defaults;
            }
            return new Settings(
                    section.getBoolean("enabled", defaults.enabled()),
                    section.getBoolean("count-merged-plots", defaults.countMergedPlots()),
                    section.getBoolean("prefer-single-entity-limits", defaults.preferSingleEntityLimits()),
                    section.getString("bypass-permission", defaults.bypassPermission()),
                    entityTypes(section.getStringList("excluded-types"), defaults.excludedTypes()),
                    spawnReasons(section.getStringList("limited-spawn-reasons"), defaults.limitedSpawnReasons())
            );
        }
    }

    private record LimitRule(
            String id,
            String display,
            String description,
            String material,
            int max,
            Set<EntityType> types,
            List<PermissionLimit> permissionLimits
    ) {

        private static LimitRule from(final String id, final ConfigurationSection section) {
            return new LimitRule(
                    id.toLowerCase(Locale.ROOT),
                    section.getString("display", id),
                    section.getString("description", ""),
                    section.getString("material", "ARMOR_STAND"),
                    section.getInt("max", 0),
                    entityTypes(section.getStringList("types"), Set.of()),
                    parsePermissionLimits(section.getConfigurationSection("permission-limits"))
            );
        }

        private boolean matches(final EntityType type) {
            return id.equals("total") || types.isEmpty() || types.contains(type);
        }

        private boolean isTotal() {
            return id.equals("total");
        }

        private boolean isSingleEntityRuleFor(final EntityType type) {
            return types.size() == 1 && types.contains(type);
        }
    }

    private record PermissionLimit(String permission, int limit) {
    }

    private static List<PermissionLimit> parsePermissionLimits(final ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }
        final List<PermissionLimit> limits = new ArrayList<>();
        for (final Map.Entry<String, Object> entry : section.getValues(false).entrySet()) {
            collectPermissionLimit(entry.getKey(), entry.getValue(), limits);
        }
        return List.copyOf(limits);
    }

    private static void collectPermissionLimit(
            final String permission,
            final Object value,
            final List<PermissionLimit> limits
    ) {
        if (value instanceof ConfigurationSection childSection) {
            for (final Map.Entry<String, Object> entry : childSection.getValues(false).entrySet()) {
                collectPermissionLimit(permission + "." + entry.getKey(), entry.getValue(), limits);
            }
            return;
        }
        if (value instanceof Number number) {
            limits.add(new PermissionLimit(permission, number.intValue()));
            return;
        }
        if (value instanceof String text) {
            try {
                limits.add(new PermissionLimit(permission, Integer.parseInt(text)));
            } catch (final NumberFormatException ignored) {
                // Invalid permission limits are ignored so one typo does not break reloads.
            }
        }
    }

    private static Set<EntityType> entityTypes(final List<String> values, final Set<EntityType> fallback) {
        if (values.isEmpty()) {
            return fallback;
        }
        final Set<EntityType> types = new HashSet<>();
        for (final String value : values) {
            try {
                types.add(EntityType.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_')));
            } catch (final IllegalArgumentException ignored) {
                // Invalid config values are ignored so reloads stay usable.
            }
        }
        return types.isEmpty() ? fallback : Set.copyOf(types);
    }

    private static Set<CreatureSpawnEvent.SpawnReason> spawnReasons(
            final List<String> values,
            final Set<CreatureSpawnEvent.SpawnReason> fallback
    ) {
        if (values.isEmpty()) {
            return fallback;
        }
        final Set<CreatureSpawnEvent.SpawnReason> reasons = new HashSet<>();
        for (final String value : values) {
            try {
                reasons.add(CreatureSpawnEvent.SpawnReason.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_')));
            } catch (final IllegalArgumentException ignored) {
                // Invalid config values are ignored so reloads stay usable.
            }
        }
        return reasons.isEmpty() ? fallback : Set.copyOf(reasons);
    }
}
