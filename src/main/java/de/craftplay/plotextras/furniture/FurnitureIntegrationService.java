package de.craftplay.plotextras.furniture;

import com.plotsquared.core.plot.Plot;
import com.plotsquared.core.plot.flag.GlobalFlagContainer;
import de.craftplay.plotextras.CraftplayPlotExtrasPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class FurnitureIntegrationService implements Listener {

    private static final String MOUNT_EVENT_BUKKIT = "org.bukkit.event.entity.EntityMountEvent";
    private static final String DISMOUNT_EVENT_BUKKIT = "org.bukkit.event.entity.EntityDismountEvent";
    private static final String MOUNT_EVENT_SPIGOT = "org.spigotmc.event.entity.EntityMountEvent";
    private static final String DISMOUNT_EVENT_SPIGOT = "org.spigotmc.event.entity.EntityDismountEvent";

    private final CraftplayPlotExtrasPlugin plugin;
    private final Map<UUID, Long> lastBlockMessage = new HashMap<>();
    private final Map<UUID, Long> lastDebugLog = new HashMap<>();

    private boolean enabled;
    private boolean registeredPlotSquaredFlags;
    private boolean registeredMountEvents;
    private boolean debug;
    private int threshold;
    private boolean strictMode;
    private boolean uncancelAllowedFurniture;
    private boolean blockMessageEnabled;
    private long blockMessageCooldownMillis;
    private long debugCooldownMillis;
    private Set<EntityType> entityTypes = EnumSet.noneOf(EntityType.class);
    private List<String> nameContains = Collections.emptyList();
    private List<String> scoreboardTagContains = Collections.emptyList();
    private List<String> pdcKeyContains = Collections.emptyList();
    private List<String> bypassPermissions = Collections.emptyList();

    public FurnitureIntegrationService(final CraftplayPlotExtrasPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("plot-furniture-flags.enabled", true);
        debug = plugin.getConfig().getBoolean("plot-furniture-flags.debug", false);
        threshold = Math.max(1, plugin.getConfig().getInt("plot-furniture-flags.threshold", 4));
        strictMode = plugin.getConfig().getBoolean("plot-furniture-flags.strict-mode", false);
        uncancelAllowedFurniture = plugin.getConfig().getBoolean("plot-furniture-flags.uncancel-allowed-furniture", true);
        blockMessageEnabled = plugin.getConfig().getBoolean("plot-furniture-flags.block-message-enabled", true);
        blockMessageCooldownMillis = Math.max(0L, plugin.getConfig().getLong("plot-furniture-flags.block-message-cooldown-millis", 1000L));
        debugCooldownMillis = Math.max(0L, plugin.getConfig().getLong("plot-furniture-flags.debug-cooldown-millis", 1500L));
        entityTypes = readEntityTypes(plugin.getConfig().getStringList("plot-furniture-flags.detect.entity-types"));
        nameContains = normalize(plugin.getConfig().getStringList("plot-furniture-flags.detect.name-contains"));
        scoreboardTagContains = normalize(plugin.getConfig().getStringList("plot-furniture-flags.detect.scoreboard-tag-contains"));
        pdcKeyContains = normalize(plugin.getConfig().getStringList("plot-furniture-flags.detect.pdc-key-contains"));
        bypassPermissions = normalize(plugin.getConfig().getStringList("plot-furniture-flags.bypass-permissions"));
        lastBlockMessage.clear();
        lastDebugLog.clear();

        if (!enabled) {
            return;
        }
        registerPlotSquaredFlags();
        registerMountEvents();
    }

    public void shutdown() {
        enabled = false;
        HandlerList.unregisterAll(this);
        registeredMountEvents = false;
        lastBlockMessage.clear();
        lastDebugLog.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteractEntity(final PlayerInteractEntityEvent event) {
        if (!enabled || event instanceof PlayerInteractAtEntityEvent || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        handleFurnitureEvent(event, event.getPlayer(), event.getRightClicked(), FurnitureAction.INTERACT, "PlayerInteractEntityEvent");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteractAtEntity(final PlayerInteractAtEntityEvent event) {
        if (!enabled || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        handleFurnitureEvent(event, event.getPlayer(), event.getRightClicked(), FurnitureAction.INTERACT, "PlayerInteractAtEntityEvent");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onFurnitureDamage(final EntityDamageByEntityEvent event) {
        if (!enabled || !(event.getDamager() instanceof Player)) {
            return;
        }
        handleFurnitureEvent(event, (Player) event.getDamager(), event.getEntity(), FurnitureAction.MODIFY, "EntityDamageByEntityEvent");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFarmlandTrample(final EntityChangeBlockEvent event) {
        if (!enabled || !(event.getEntity() instanceof Player) || !isFarmlandTrample(event)) {
            return;
        }
        final CropTrampleResult result = checkCropTrample(event.getBlock().getLocation());
        debugCropTrample((Player) event.getEntity(), event.getBlock(), result);
        if (result.preventCropTrample) {
            event.setCancelled(true);
        }
    }

    private void registerPlotSquaredFlags() {
        if (registeredPlotSquaredFlags || !plugin.getServer().getPluginManager().isPluginEnabled("PlotSquared")) {
            return;
        }
        try {
            final GlobalFlagContainer container = GlobalFlagContainer.getInstance();
            container.addFlag(new FurnitureInteractFlag(false, plainMessage("furniture-interact-flag-description")));
            container.addFlag(new FurnitureSitFlag(false, plainMessage("furniture-sit-flag-description")));
            container.addFlag(new FurnitureModifyFlag(false, plainMessage("furniture-modify-flag-description")));
            container.addFlag(new PreventCropTrampleFlag(false, plainMessage("prevent-crop-trample-flag-description")));
            registeredPlotSquaredFlags = true;
            plugin.getLogger().info("PlotSquared-Flags registriert: furniture-interact, furniture-sit, furniture-modify, prevent-crop-trample");
        } catch (final RuntimeException exception) {
            registeredPlotSquaredFlags = true;
            plugin.getLogger().warning("Furniture-PlotSquared-Flags konnten nicht registriert werden: " + exception.getMessage());
        }
    }

    private String plainMessage(final String key) {
        return ChatColor.stripColor(plugin.getLanguageManager().getMessage(key));
    }

    private void registerMountEvents() {
        if (registeredMountEvents) {
            return;
        }
        boolean registered = false;
        registered = registerFirstEvent(new String[]{MOUNT_EVENT_BUKKIT, MOUNT_EVENT_SPIGOT},
                (listener, event) -> handleMountEvent(event)) || registered;
        registered = registerFirstEvent(new String[]{DISMOUNT_EVENT_BUKKIT, DISMOUNT_EVENT_SPIGOT},
                (listener, event) -> handleDismountEvent(event)) || registered;
        registeredMountEvents = registered;
    }

    @SuppressWarnings("unchecked")
    private boolean registerFirstEvent(final String[] classNames, final EventExecutor executor) {
        for (final String className : classNames) {
            try {
                final Class<?> eventClass = Class.forName(className);
                if (!Event.class.isAssignableFrom(eventClass)) {
                    continue;
                }
                plugin.getServer().getPluginManager().registerEvent(
                        (Class<? extends Event>) eventClass,
                        this,
                        className.toLowerCase(Locale.ROOT).contains("dismount") ? EventPriority.MONITOR : EventPriority.HIGHEST,
                        executor,
                        plugin,
                        false
                );
                return true;
            } catch (final ClassNotFoundException ignored) {
                // Try the next class name. Bukkit moved these events between versions.
            }
        }
        return false;
    }

    private void handleMountEvent(final Event event) {
        if (!enabled || !(event instanceof Cancellable)) {
            return;
        }
        final Object entity = invokeNoArgs(event, "getEntity");
        final Object mount = invokeNoArgs(event, "getMount");
        if (!(entity instanceof Player) || !(mount instanceof Entity)) {
            return;
        }
        handleFurnitureEvent((Cancellable) event, (Player) entity, (Entity) mount, FurnitureAction.SIT, event.getEventName());
    }

    private void handleDismountEvent(final Event event) {
        if (!enabled || !debug) {
            return;
        }
        final Object entity = invokeNoArgs(event, "getEntity");
        final Object dismounted = invokeNoArgs(event, "getDismounted");
        if (!(entity instanceof Player) || !(dismounted instanceof Entity)) {
            return;
        }
        final DetectionResult detection = detect((Entity) dismounted);
        if (!detection.furniture) {
            return;
        }
        final PlotAccessResult access = checkAccess((Player) entity, ((Entity) dismounted).getLocation(), FurnitureAction.SIT);
        debugInteraction((Player) entity, event.getEventName(), (Entity) dismounted, detection, access, event instanceof Cancellable && ((Cancellable) event).isCancelled());
    }

    private void handleFurnitureEvent(
            final Cancellable event,
            final Player player,
            final Entity entity,
            final FurnitureAction action,
            final String eventName
    ) {
        if (player == null || entity == null) {
            return;
        }
        final DetectionResult detection = detect(entity);
        final PlotAccessResult access = checkAccess(player, entity.getLocation(), action);
        debugInteraction(player, eventName, entity, detection, access, event.isCancelled());
        if (!detection.furniture || !access.claimedPlot) {
            return;
        }
        if (access.allowed) {
            if (uncancelAllowedFurniture && event.isCancelled()) {
                event.setCancelled(false);
            }
            return;
        }
        event.setCancelled(true);
        sendBlockedMessage(player);
    }

    private DetectionResult detect(final Entity entity) {
        final DetectionScore score = new DetectionScore();
        if (entityTypes.contains(entity.getType())) {
            score.add(1, "configured candidate entity type " + entity.getType().name());
        }
        if ("INTERACTION".equals(entity.getType().name())) {
            score.add(1, "Interaction entity");
        }
        if (entity instanceof ArmorStand) {
            final ArmorStand armorStand = (ArmorStand) entity;
            score.add(1, "ArmorStand entity");
            if (armorStand.isMarker() || !armorStand.isVisible()) {
                score.add(1, "marker or invisible ArmorStand");
            }
        }
        scoreKeywords("custom name", stripColor(entity.getCustomName()), nameContains, 3, score);
        scoreCollection("scoreboard tag", entity.getScoreboardTags(), scoreboardTagContains, 5, score);
        scoreCollection("persistent data key", persistentDataKeys(entity), pdcKeyContains, 3, score);
        scoreRelations(entity, score);
        final int effectiveThreshold = strictMode ? Math.max(1, threshold - 1) : threshold;
        return new DetectionResult(score.value >= effectiveThreshold, score.value, effectiveThreshold, score.reasons);
    }

    private void scoreRelations(final Entity entity, final DetectionScore score) {
        final Entity vehicle = entity.getVehicle();
        if (vehicle != null) {
            if (isCandidateType(vehicle)) {
                score.add(1, "vehicle candidate type " + vehicle.getType().name());
            }
            scoreKeywords("vehicle custom name", stripColor(vehicle.getCustomName()), nameContains, 2, score);
            scoreCollection("vehicle scoreboard tag", vehicle.getScoreboardTags(), scoreboardTagContains, 3, score);
        }
        for (final Entity passenger : entity.getPassengers()) {
            if (passenger instanceof Player) {
                score.add(2, "player passenger on entity");
                continue;
            }
            if (isCandidateType(passenger)) {
                score.add(1, "passenger candidate type " + passenger.getType().name());
            }
            scoreKeywords("passenger custom name", stripColor(passenger.getCustomName()), nameContains, 2, score);
            scoreCollection("passenger scoreboard tag", passenger.getScoreboardTags(), scoreboardTagContains, 3, score);
        }
    }

    private boolean isCandidateType(final Entity entity) {
        return entityTypes.contains(entity.getType())
                || "INTERACTION".equals(entity.getType().name())
                || entity.getType() == EntityType.ARMOR_STAND;
    }

    private void scoreKeywords(
            final String source,
            final String value,
            final List<String> needles,
            final int points,
            final DetectionScore score
    ) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        final String normalized = value.toLowerCase(Locale.ROOT);
        for (final String needle : needles) {
            if (normalized.contains(needle)) {
                score.add(points, source + " contains '" + needle + "'");
                return;
            }
        }
    }

    private void scoreCollection(
            final String source,
            final Set<String> values,
            final List<String> needles,
            final int points,
            final DetectionScore score
    ) {
        for (final String value : values) {
            final String normalized = value.toLowerCase(Locale.ROOT);
            for (final String needle : needles) {
                if (normalized.contains(needle)) {
                    score.add(points, source + " contains '" + needle + "': " + value);
                    return;
                }
            }
        }
    }

    private Set<String> persistentDataKeys(final Entity entity) {
        final Set<String> keys = new HashSet<>();
        try {
            for (final NamespacedKey key : entity.getPersistentDataContainer().getKeys()) {
                keys.add(key.toString());
            }
        } catch (final RuntimeException exception) {
            debug("PersistentDataContainer konnte nicht gelesen werden: " + exception.getMessage());
        }
        return keys;
    }

    private PlotAccessResult checkAccess(final Player player, final Location location, final FurnitureAction action) {
        final Plot plot = plotAt(location);
        if (plot == null) {
            return PlotAccessResult.outside();
        }
        final String plotId = describePlot(plot);
        if (!plot.hasOwner()) {
            return PlotAccessResult.unclaimed(plotId);
        }
        final UUID uuid = player.getUniqueId();
        final boolean member = plot.isOwner(uuid) || plot.isAdded(uuid);
        final boolean bypass = hasBypass(player);
        final boolean flagEnabled = furnitureFlag(plot, action);
        return new PlotAccessResult(true, plotId, member, bypass, flagEnabled, member || bypass || flagEnabled);
    }

    private CropTrampleResult checkCropTrample(final Location location) {
        final Plot plot = plotAt(location);
        if (plot == null) {
            return CropTrampleResult.outside();
        }
        final String plotId = describePlot(plot);
        if (!plot.hasOwner()) {
            return CropTrampleResult.unclaimed(plotId);
        }
        return new CropTrampleResult(true, plotId, preventCropTrample(plot));
    }

    private Plot plotAt(final Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        try {
            final com.plotsquared.core.location.Location plotLocation = com.plotsquared.core.location.Location.at(
                    location.getWorld().getName(),
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ()
            );
            if (!plotLocation.isPlotArea()) {
                return null;
            }
            return plotLocation.getPlot();
        } catch (final Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "PlotSquared konnte für diese Location keinen Plot prüfen.", throwable);
            return null;
        }
    }

    private boolean furnitureFlag(final Plot plot, final FurnitureAction action) {
        try {
            if (action == FurnitureAction.SIT) {
                return plot.getFlag(FurnitureSitFlag.class);
            }
            if (action == FurnitureAction.MODIFY) {
                return plot.getFlag(FurnitureModifyFlag.class);
            }
            return plot.getFlag(FurnitureInteractFlag.class);
        } catch (final RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Furniture-PlotSquared-Flag konnte nicht gelesen werden.", exception);
            return false;
        }
    }

    private boolean preventCropTrample(final Plot plot) {
        try {
            return plot.getFlag(PreventCropTrampleFlag.class);
        } catch (final RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Prevent-Crop-Trample-Flag konnte nicht gelesen werden.", exception);
            return false;
        }
    }

    private boolean hasBypass(final Player player) {
        for (final String permission : bypassPermissions) {
            if (player.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    private String describePlot(final Plot plot) {
        final String world = plot.getWorldName() == null ? "unknown-world" : plot.getWorldName();
        return world + ":" + plot.getId();
    }

    private boolean isFarmlandTrample(final EntityChangeBlockEvent event) {
        return event.getBlock().getType() == Material.FARMLAND && event.getTo() == Material.DIRT;
    }

    private void sendBlockedMessage(final Player player) {
        if (!blockMessageEnabled) {
            return;
        }
        final long now = System.currentTimeMillis();
        final Long last = lastBlockMessage.get(player.getUniqueId());
        if (last != null && blockMessageCooldownMillis > 0L && now - last < blockMessageCooldownMillis) {
            return;
        }
        lastBlockMessage.put(player.getUniqueId(), now);
        plugin.getLanguageManager().send(player, "furniture-blocked");
    }

    private void debugInteraction(
            final Player player,
            final String eventName,
            final Entity entity,
            final DetectionResult detection,
            final PlotAccessResult access,
            final boolean eventCancelledBefore
    ) {
        if (!debug || !canDebug(player.getUniqueId())) {
            return;
        }
        plugin.getLogger().info("[FurnitureDebug] event=" + eventName
                + ", player=" + player.getName()
                + ", cancelledBefore=" + eventCancelledBefore
                + ", entityType=" + entity.getType()
                + ", entityUuid=" + entity.getUniqueId()
                + ", customName=" + nullSafe(entity.getCustomName())
                + ", tags=" + entity.getScoreboardTags()
                + ", pdcKeys=" + persistentDataKeys(entity)
                + ", score=" + detection.score + "/" + detection.threshold
                + ", furniture=" + detection.furniture
                + ", reasons=" + detection.reasons
                + ", plot=" + access.plotId
                + ", claimedPlot=" + access.claimedPlot
                + ", member=" + access.member
                + ", bypass=" + access.bypass
                + ", flag=" + access.flagEnabled
                + ", allowed=" + access.allowed);
    }

    private void debugCropTrample(final Player player, final Block block, final CropTrampleResult result) {
        if (!debug || !canDebug(player.getUniqueId())) {
            return;
        }
        plugin.getLogger().info("[FurnitureDebug] event=EntityChangeBlockEvent"
                + ", player=" + player.getName()
                + ", blockType=" + block.getType()
                + ", world=" + block.getWorld().getName()
                + ", x=" + block.getX()
                + ", y=" + block.getY()
                + ", z=" + block.getZ()
                + ", plot=" + result.plotId
                + ", claimedPlot=" + result.claimedPlot
                + ", preventCropTrample=" + result.preventCropTrample);
    }

    private boolean canDebug(final UUID uuid) {
        if (debugCooldownMillis <= 0L) {
            return true;
        }
        final long now = System.currentTimeMillis();
        final Long last = lastDebugLog.get(uuid);
        if (last != null && now - last < debugCooldownMillis) {
            return false;
        }
        lastDebugLog.put(uuid, now);
        return true;
    }

    private Set<EntityType> readEntityTypes(final List<String> names) {
        final Set<EntityType> types = EnumSet.noneOf(EntityType.class);
        for (final String name : names) {
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            try {
                types.add(EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (final IllegalArgumentException exception) {
                plugin.getLogger().warning("Unbekannter EntityType in plot-furniture-flags.detect.entity-types: " + name);
            }
        }
        return types;
    }

    private List<String> normalize(final List<String> input) {
        final List<String> result = new ArrayList<>();
        for (final String value : input) {
            if (value != null && !value.trim().isEmpty()) {
                result.add(value.toLowerCase(Locale.ROOT).trim());
            }
        }
        return Collections.unmodifiableList(result);
    }

    private String stripColor(final String value) {
        return value == null ? null : ChatColor.stripColor(value);
    }

    private String nullSafe(final String value) {
        return value == null ? "-" : value;
    }

    private Object invokeNoArgs(final Object target, final String methodName) {
        if (target == null) {
            return null;
        }
        try {
            final Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (final NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
    }

    private void debug(final String message) {
        if (debug) {
            plugin.getLogger().info("[FurnitureDebug] " + message);
        }
    }

    private enum FurnitureAction {
        INTERACT,
        SIT,
        MODIFY
    }

    private static final class DetectionScore {
        private int value;
        private final List<String> reasons = new ArrayList<>();

        private void add(final int points, final String reason) {
            value += points;
            reasons.add("+" + points + " " + reason);
        }
    }

    private static final class DetectionResult {
        private final boolean furniture;
        private final int score;
        private final int threshold;
        private final List<String> reasons;

        private DetectionResult(final boolean furniture, final int score, final int threshold, final List<String> reasons) {
            this.furniture = furniture;
            this.score = score;
            this.threshold = threshold;
            this.reasons = new ArrayList<>(reasons);
        }
    }

    private static final class PlotAccessResult {
        private final boolean claimedPlot;
        private final String plotId;
        private final boolean member;
        private final boolean bypass;
        private final boolean flagEnabled;
        private final boolean allowed;

        private PlotAccessResult(
                final boolean claimedPlot,
                final String plotId,
                final boolean member,
                final boolean bypass,
                final boolean flagEnabled,
                final boolean allowed
        ) {
            this.claimedPlot = claimedPlot;
            this.plotId = plotId;
            this.member = member;
            this.bypass = bypass;
            this.flagEnabled = flagEnabled;
            this.allowed = allowed;
        }

        private static PlotAccessResult outside() {
            return new PlotAccessResult(false, "-", false, false, false, true);
        }

        private static PlotAccessResult unclaimed(final String plotId) {
            return new PlotAccessResult(false, plotId, false, false, false, true);
        }
    }

    private static final class CropTrampleResult {
        private final boolean claimedPlot;
        private final String plotId;
        private final boolean preventCropTrample;

        private CropTrampleResult(final boolean claimedPlot, final String plotId, final boolean preventCropTrample) {
            this.claimedPlot = claimedPlot;
            this.plotId = plotId;
            this.preventCropTrample = preventCropTrample;
        }

        private static CropTrampleResult outside() {
            return new CropTrampleResult(false, "-", false);
        }

        private static CropTrampleResult unclaimed(final String plotId) {
            return new CropTrampleResult(false, plotId, false);
        }
    }
}
