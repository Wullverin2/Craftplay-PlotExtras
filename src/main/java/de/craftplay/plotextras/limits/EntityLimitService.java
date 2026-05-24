package de.craftplay.plotextras.limits;

import de.craftplay.plotextras.CraftplayPlotExtrasPlugin;
import de.craftplay.plotextras.plotsquared.PlotContext;
import de.craftplay.plotextras.plotsquared.PlotRegion;
import de.craftplay.plotextras.plotsquared.PlotSquaredFlagService;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class EntityLimitService implements Listener {

    private static final String FILE_NAME = "limits.yml";
    private static final long RECENT_CHECK_MILLIS = 1000L;

    private final CraftplayPlotExtrasPlugin plugin;
    private final PlotSquaredFlagService plotService;
    private final Map<EntityType, EntityLimit> limits = new HashMap<>();
    private final Map<String, CachedCount> countCache = new HashMap<>();
    private final Map<UUID, Long> recentChecks = new HashMap<>();
    private final Set<String> ignoredSpawnReasons = new HashSet<>();

    private boolean enabled;
    private boolean autoAddMissingEntities;
    private boolean notifyPlayer;
    private String bypassPermission;
    private long cacheMillis;
    private boolean defaultMissingEnabled;
    private int defaultMissingLimit;

    public EntityLimitService(final CraftplayPlotExtrasPlugin plugin, final PlotSquaredFlagService plotService) {
        this.plugin = plugin;
        this.plotService = plotService;
    }

    public void reload() {
        final File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists() && plugin.getResource(FILE_NAME) != null) {
            plugin.saveResource(FILE_NAME, false);
        }

        final YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        enabled = configuration.getBoolean("enabled", true);
        autoAddMissingEntities = configuration.getBoolean("auto-add-missing-entities", true);
        notifyPlayer = configuration.getBoolean("notify-player", true);
        bypassPermission = configuration.getString("bypass-permission", "craftplayplotextras.entitylimits.bypass");
        cacheMillis = Math.max(0L, Math.round(configuration.getDouble("cache-seconds", 1.0D) * 1000.0D));
        defaultMissingEnabled = configuration.getBoolean("defaults.missing-entities.enabled", false);
        defaultMissingLimit = configuration.getInt("defaults.missing-entities.limit", 100);
        ignoredSpawnReasons.clear();
        for (final String reason : configuration.getStringList("ignored-spawn-reasons")) {
            ignoredSpawnReasons.add(normalize(reason));
        }

        boolean changed = false;
        if (autoAddMissingEntities) {
            changed = addMissingEntitySections(configuration);
        }

        limits.clear();
        final ConfigurationSection section = configuration.getConfigurationSection("entities");
        if (section != null) {
            for (final String key : section.getKeys(false)) {
                final EntityType type = entityType(key);
                if (type == null) {
                    continue;
                }
                final String path = "entities." + key + ".";
                limits.put(type, new EntityLimit(
                        configuration.getBoolean(path + "enabled", false),
                        configuration.getInt(path + "limit", -1),
                        permissionLimits(configuration.getConfigurationSection(path + "permission-limits"))
                ));
            }
        }
        countCache.clear();
        recentChecks.clear();

        if (changed) {
            try {
                configuration.save(file);
            } catch (final IOException exception) {
                plugin.getLogger().log(Level.WARNING, "limits.yml konnte nicht um neue Entity-Arten ergänzt werden.", exception);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntitySpawn(final EntitySpawnEvent event) {
        if (event instanceof org.bukkit.event.entity.CreatureSpawnEvent) {
            final String reason = normalize(((org.bukkit.event.entity.CreatureSpawnEvent) event).getSpawnReason().name());
            if (ignoredSpawnReasons.contains(reason)) {
                return;
            }
        }
        if (shouldCancel(event.getEntity(), null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPlace(final EntityPlaceEvent event) {
        if (shouldCancel(event.getEntity(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingPlace(final HangingPlaceEvent event) {
        if (shouldCancel(event.getEntity(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    private boolean shouldCancel(final Entity entity, final Player actor) {
        if (!enabled || entity == null) {
            return false;
        }
        cleanupRecentChecks();
        if (recentlyChecked(entity.getUniqueId())) {
            return false;
        }

        final EntityLimit limit = limits.get(entity.getType());
        if (limit == null || !limit.isEnabled()) {
            markChecked(entity.getUniqueId());
            return false;
        }

        final Optional<PlotContext> optionalContext = plotService.plotContextAt(entity.getLocation());
        if (!optionalContext.isPresent() || !optionalContext.get().isComplete()) {
            markChecked(entity.getUniqueId());
            return false;
        }
        final PlotContext context = optionalContext.get();
        final Player owner = context.getOwnerUuid() == null ? null : plugin.getServer().getPlayer(context.getOwnerUuid());
        if (hasBypass(actor) || hasBypass(owner)) {
            markChecked(entity.getUniqueId());
            return false;
        }

        final int effectiveLimit = effectiveLimit(limit, actor, owner);
        if (effectiveLimit < 0) {
            markChecked(entity.getUniqueId());
            return false;
        }

        final int current = countEntities(context, entity.getType(), entity.getUniqueId());
        if (current >= effectiveLimit) {
            markChecked(entity.getUniqueId());
            notifyLimitReached(actor, entity.getType(), context, current, effectiveLimit);
            return true;
        }

        putCachedCount(cacheKey(context, entity.getType()), current + 1);
        markChecked(entity.getUniqueId());
        return false;
    }

    private int effectiveLimit(final EntityLimit limit, final Player actor, final Player owner) {
        int effective = limit.getLimit();
        effective = applyPermissionLimits(effective, limit, owner);
        if (actor != null && (owner == null || !actor.getUniqueId().equals(owner.getUniqueId()))) {
            effective = applyPermissionLimits(effective, limit, actor);
        }
        return effective;
    }

    private int applyPermissionLimits(final int currentLimit, final EntityLimit limit, final Player player) {
        if (player == null) {
            return currentLimit;
        }
        int effective = currentLimit;
        for (final Map.Entry<String, Integer> entry : limit.getPermissionLimits().entrySet()) {
            if (!player.hasPermission(entry.getKey())) {
                continue;
            }
            final int value = entry.getValue();
            if (value < 0) {
                return -1;
            }
            if (effective < 0 || value > effective) {
                effective = value;
            }
        }
        return effective;
    }

    private int countEntities(final PlotContext context, final EntityType type, final UUID ignoredEntityId) {
        final String key = cacheKey(context, type);
        final CachedCount cached = countCache.get(key);
        final long now = System.currentTimeMillis();
        if (cached != null && cached.isFresh(now, cacheMillis)) {
            return cached.getCount();
        }

        final World world = context.getWorld();
        if (world == null) {
            return 0;
        }

        int count = 0;
        for (final Entity entity : world.getEntities()) {
            if (entity == null || entity.isDead() || !entity.isValid() || entity.getType() != type) {
                continue;
            }
            if (ignoredEntityId != null && ignoredEntityId.equals(entity.getUniqueId())) {
                continue;
            }
            if (isInside(context, entity)) {
                count++;
            }
        }
        putCachedCount(key, count);
        return count;
    }

    private boolean isInside(final PlotContext context, final Entity entity) {
        for (final PlotRegion region : context.getRegions()) {
            if (region.contains(entity.getLocation())) {
                return true;
            }
        }
        return false;
    }

    private void notifyLimitReached(
            final Player player,
            final EntityType type,
            final PlotContext context,
            final int current,
            final int limit
    ) {
        if (!notifyPlayer || player == null) {
            return;
        }
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("entity", displayName(type));
        placeholders.put("type", type.name());
        placeholders.put("current", String.valueOf(current));
        placeholders.put("limit", String.valueOf(limit));
        placeholders.put("plot", context.getPlotId());
        placeholders.put("world", context.getWorldName());
        placeholders.put("merge", context.getMergeType());
        plugin.getLanguageManager().send(player, "entity-limit-reached", placeholders);
    }

    private boolean addMissingEntitySections(final YamlConfiguration configuration) {
        boolean changed = false;
        for (final EntityType type : EntityType.values()) {
            if (type == EntityType.UNKNOWN) {
                continue;
            }
            final String path = "entities." + type.name() + ".";
            if (!configuration.contains(path + "enabled")) {
                configuration.set(path + "enabled", defaultMissingEnabled);
                changed = true;
            }
            if (!configuration.contains(path + "limit")) {
                configuration.set(path + "limit", defaultMissingLimit);
                changed = true;
            }
            if (!configuration.isConfigurationSection(path + "permission-limits")) {
                configuration.set(path + "permission-limits", null);
                configuration.createSection(path + "permission-limits");
                changed = true;
            }
        }
        return changed;
    }

    private Map<String, Integer> permissionLimits(final ConfigurationSection section) {
        final Map<String, Integer> values = new LinkedHashMap<>();
        if (section == null) {
            return values;
        }
        for (final String permission : section.getKeys(false)) {
            values.put(permission, section.getInt(permission, -1));
        }
        return values;
    }

    private EntityType entityType(final String key) {
        if (key == null || key.trim().isEmpty()) {
            return null;
        }
        try {
            return EntityType.valueOf(key.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (final IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean hasBypass(final Player player) {
        return player != null && (player.hasPermission("craftplayplotextras.admin")
                || player.hasPermission(bypassPermission));
    }

    private String cacheKey(final PlotContext context, final EntityType type) {
        final List<String> plotIds = new ArrayList<>(context.getPlotIds());
        if (plotIds.isEmpty()) {
            plotIds.add(context.getPlotId());
        }
        plotIds.sort(String.CASE_INSENSITIVE_ORDER);
        return context.getWorldName().toLowerCase(Locale.ROOT) + ";" + String.join(",", plotIds)
                + ";" + type.name();
    }

    private void putCachedCount(final String key, final int count) {
        if (cacheMillis <= 0L) {
            return;
        }
        countCache.put(key, new CachedCount(count, System.currentTimeMillis()));
    }

    private boolean recentlyChecked(final UUID uuid) {
        final Long checkedAt = recentChecks.get(uuid);
        return checkedAt != null && System.currentTimeMillis() - checkedAt <= RECENT_CHECK_MILLIS;
    }

    private void markChecked(final UUID uuid) {
        if (uuid != null) {
            recentChecks.put(uuid, System.currentTimeMillis());
        }
    }

    private void cleanupRecentChecks() {
        final long now = System.currentTimeMillis();
        recentChecks.entrySet().removeIf(entry -> now - entry.getValue() > RECENT_CHECK_MILLIS * 5L);
    }

    private String normalize(final String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private String displayName(final EntityType type) {
        final String[] parts = type.name().toLowerCase(Locale.ROOT).split("_");
        final StringBuilder builder = new StringBuilder();
        for (final String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private static final class EntityLimit {
        private final boolean enabled;
        private final int limit;
        private final Map<String, Integer> permissionLimits;

        private EntityLimit(final boolean enabled, final int limit, final Map<String, Integer> permissionLimits) {
            this.enabled = enabled;
            this.limit = limit;
            this.permissionLimits = permissionLimits;
        }

        private boolean isEnabled() {
            return enabled;
        }

        private int getLimit() {
            return limit;
        }

        private Map<String, Integer> getPermissionLimits() {
            return permissionLimits;
        }
    }

    private static final class CachedCount {
        private final int count;
        private final long createdAt;

        private CachedCount(final int count, final long createdAt) {
            this.count = count;
            this.createdAt = createdAt;
        }

        private int getCount() {
            return count;
        }

        private boolean isFresh(final long now, final long maxAgeMillis) {
            return maxAgeMillis > 0L && now - createdAt <= maxAgeMillis;
        }
    }
}
