package de.craftplay.plotextras.furniture;

import com.plotsquared.bukkit.util.BukkitUtil;
import com.plotsquared.core.plot.Plot;
import com.plotsquared.core.plot.flag.GlobalFlagContainer;
import com.plotsquared.core.plot.flag.PlotFlag;
import de.craftplay.plotextras.furniture.flag.FurnitureInteractFlag;
import de.craftplay.plotextras.furniture.flag.FurnitureModifyFlag;
import de.craftplay.plotextras.furniture.flag.FurnitureSitFlag;
import de.craftplay.plotextras.furniture.flag.PreventCropTrampleFlag;
import de.craftplay.plotextras.util.TextUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class FurnitureProtectionManager implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, Long> lastBlockMessage = new HashMap<>();
    private final Map<UUID, Long> lastDebugMessage = new HashMap<>();
    private FurnitureSettings settings = FurnitureSettings.defaults();

    public FurnitureProtectionManager(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerFlags() {
        final GlobalFlagContainer container = GlobalFlagContainer.getInstance();
        addFlagIfMissing(container, FurnitureInteractFlag.FURNITURE_INTERACT_FALSE);
        addFlagIfMissing(container, FurnitureSitFlag.FURNITURE_SIT_FALSE);
        addFlagIfMissing(container, FurnitureModifyFlag.FURNITURE_MODIFY_FALSE);
        addFlagIfMissing(container, PreventCropTrampleFlag.PREVENT_CROP_TRAMPLE_FALSE);
    }

    public void reload() {
        settings = FurnitureSettings.from(plugin.getConfig().getConfigurationSection("furniture-flags"));
        lastBlockMessage.clear();
        lastDebugMessage.clear();
    }

    private void addFlagIfMissing(final GlobalFlagContainer container, final PlotFlag<?, ?> flag) {
        if (container.getFlagClassFromString(flag.getName()) == null) {
            container.addFlag(flag);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onFurnitureInteract(final PlayerInteractEntityEvent event) {
        if (event instanceof PlayerInteractAtEntityEvent || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        handleFurnitureUse(event.getPlayer(), event.getRightClicked(), event::isCancelled, event::setCancelled, "interact");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onFurnitureInteractAt(final PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        handleFurnitureUse(event.getPlayer(), event.getRightClicked(), event::isCancelled, event::setCancelled, "interact-at");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onFurnitureMount(final EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        handleFurnitureUse(player, event.getMount(), event::isCancelled, event::setCancelled, "mount");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onFarmlandTrample(final EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof Player)
                || event.getBlock().getType() != Material.FARMLAND
                || event.getTo() != Material.DIRT) {
            return;
        }

        if (isCropTrampleProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    private void handleFurnitureUse(
            final Player player,
            final Entity entity,
            final CancelledState cancelledState,
            final CancelledSetter cancelledSetter,
            final String source
    ) {
        final DetectionResult detection = detectFurniture(entity);
        final PlotAccessResult access = checkFurnitureAccess(player, entity.getLocation());
        debug(player, source + " entity=" + entity.getType() + ", furniture=" + detection.furniture()
                + ", score=" + detection.score() + ", reason=" + access.reason());

        if (!detection.furniture() || !access.claimedPlot()) {
            return;
        }

        if (access.allowed()) {
            if (settings.uncancelAllowedFurniture() && cancelledState.isCancelled()) {
                cancelledSetter.setCancelled(false);
            }
            return;
        }

        cancelledSetter.setCancelled(true);
        sendBlockMessage(player);
    }

    private PlotAccessResult checkFurnitureAccess(final Player player, final Location location) {
        final Plot plot = getPlot(location);
        if (plot == null) {
            return PlotAccessResult.outside();
        }
        if (!plot.hasOwner()) {
            return PlotAccessResult.unclaimed();
        }
        if (hasBypass(player)) {
            return PlotAccessResult.allowed("bypass");
        }
        if (plot.isAdded(player.getUniqueId())) {
            return PlotAccessResult.allowed("added");
        }
        if (getBooleanFlag(plot, "furniture-interact")) {
            return PlotAccessResult.allowed("flag");
        }
        return PlotAccessResult.denied();
    }

    private boolean isCropTrampleProtected(final Location location) {
        final Plot plot = getPlot(location);
        return plot != null && plot.hasOwner() && getBooleanFlag(plot, "prevent-crop-trample");
    }

    private Plot getPlot(final Location location) {
        try {
            return Plot.getPlot(BukkitUtil.adapt(location));
        } catch (final RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not resolve PlotSquared plot at " + location, exception);
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean getBooleanFlag(final Plot plot, final String flagName) {
        final Class flagClass = GlobalFlagContainer.getInstance().getFlagClassFromString(flagName);
        if (flagClass == null) {
            return false;
        }
        try {
            final Object value = plot.getFlag(flagClass);
            if (value instanceof Boolean bool) {
                return bool;
            }
            return Boolean.parseBoolean(String.valueOf(value));
        } catch (final RuntimeException exception) {
            return false;
        }
    }

    private boolean hasBypass(final Player player) {
        if (player.hasPermission("craftplayplotextras.admin")) {
            return true;
        }
        for (final String permission : settings.bypassPermissions()) {
            if (!permission.isBlank() && player.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    private DetectionResult detectFurniture(final Entity entity) {
        int score = 0;
        if (settings.entityTypes().contains(entity.getType())) {
            score++;
        }
        if (entity.getType() == EntityType.INTERACTION) {
            score++;
        }
        if (entity instanceof ArmorStand armorStand) {
            score++;
            if (armorStand.isMarker() || !armorStand.isVisible()) {
                score++;
            }
        }

        score += keywordScore(nameOf(entity), settings.nameContains(), 3);
        score += tagScore(entity, settings.scoreboardTagContains(), 5);
        score += pdcScore(entity, settings.pdcKeyContains(), 3);

        final Entity vehicle = entity.getVehicle();
        if (vehicle != null) {
            if (settings.entityTypes().contains(vehicle.getType())) {
                score++;
            }
            score += keywordScore(nameOf(vehicle), settings.nameContains(), 2);
            score += tagScore(vehicle, settings.scoreboardTagContains(), 3);
        }

        for (final Entity passenger : entity.getPassengers()) {
            if (passenger instanceof Player) {
                score += 2;
            }
            if (settings.entityTypes().contains(passenger.getType())) {
                score++;
            }
            score += keywordScore(nameOf(passenger), settings.nameContains(), 2);
            score += tagScore(passenger, settings.scoreboardTagContains(), 3);
        }

        return new DetectionResult(score >= settings.effectiveThreshold(), score);
    }

    private int keywordScore(final String value, final List<String> keywords, final int score) {
        if (value.isBlank()) {
            return 0;
        }
        for (final String keyword : keywords) {
            if (!keyword.isBlank() && value.contains(keyword)) {
                return score;
            }
        }
        return 0;
    }

    private int tagScore(final Entity entity, final List<String> keywords, final int score) {
        for (final String tag : entity.getScoreboardTags()) {
            if (keywordScore(normalize(tag), keywords, score) > 0) {
                return score;
            }
        }
        return 0;
    }

    private int pdcScore(final Entity entity, final List<String> keywords, final int score) {
        for (final NamespacedKey key : entity.getPersistentDataContainer().getKeys()) {
            if (keywordScore(normalize(key.toString()), keywords, score) > 0) {
                return score;
            }
        }
        return 0;
    }

    private String nameOf(final Entity entity) {
        return normalize(ChatColor.stripColor(entity.getCustomName()));
    }

    private String normalize(final String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private void sendBlockMessage(final Player player) {
        if (!settings.blockMessageEnabled()) {
            return;
        }
        final long now = System.currentTimeMillis();
        final long previous = lastBlockMessage.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous < settings.blockMessageCooldownMillis()) {
            return;
        }
        lastBlockMessage.put(player.getUniqueId(), now);
        player.sendMessage(TextUtil.component(settings.blockMessage()));
    }

    private void debug(final Player player, final String message) {
        if (!settings.debug()) {
            return;
        }
        final long now = System.currentTimeMillis();
        final long previous = lastDebugMessage.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous < settings.debugCooldownMillis()) {
            return;
        }
        lastDebugMessage.put(player.getUniqueId(), now);
        plugin.getLogger().info("[FurnitureFlags] " + player.getName() + ": " + message);
    }

    @FunctionalInterface
    private interface CancelledState {
        boolean isCancelled();
    }

    @FunctionalInterface
    private interface CancelledSetter {
        void setCancelled(boolean cancelled);
    }

    private record DetectionResult(boolean furniture, int score) {
    }

    private record PlotAccessResult(boolean claimedPlot, boolean allowed, String reason) {

        private static PlotAccessResult outside() {
            return new PlotAccessResult(false, false, "outside");
        }

        private static PlotAccessResult unclaimed() {
            return new PlotAccessResult(false, false, "unclaimed");
        }

        private static PlotAccessResult allowed(final String reason) {
            return new PlotAccessResult(true, true, reason);
        }

        private static PlotAccessResult denied() {
            return new PlotAccessResult(true, false, "denied");
        }
    }

    private record FurnitureSettings(
            boolean debug,
            int threshold,
            boolean strictMode,
            boolean uncancelAllowedFurniture,
            boolean blockMessageEnabled,
            String blockMessage,
            long debugCooldownMillis,
            long blockMessageCooldownMillis,
            Set<String> bypassPermissions,
            Set<EntityType> entityTypes,
            List<String> nameContains,
            List<String> scoreboardTagContains,
            List<String> pdcKeyContains
    ) {

        private static FurnitureSettings defaults() {
            return new FurnitureSettings(
                    false,
                    4,
                    false,
                    true,
                    true,
                    "&cDu darfst diese Möbel auf diesem Plot nicht benutzen.",
                    1500L,
                    1000L,
                    Set.of("craftplayplotextras.furniture.bypass", "plotfurnitureflags.bypass", "plots.admin", "plots.admin.interact.other"),
                    EnumSet.of(EntityType.INTERACTION, EntityType.ARMOR_STAND),
                    List.of("furniture", "furnicraft", "bench", "chair", "seat", "sofa", "stool", "moebel", "möbel"),
                    List.of("furniture", "furnicraft", "bench", "chair", "seat", "moebel", "möbel"),
                    List.of("furniture", "furnicraft", "bench", "chair", "seat", "moebel", "möbel")
            );
        }

        private static FurnitureSettings from(final ConfigurationSection section) {
            final FurnitureSettings defaults = defaults();
            if (section == null) {
                return defaults;
            }

            final ConfigurationSection detectSection = section.getConfigurationSection("detect");
            return new FurnitureSettings(
                    section.getBoolean("debug", defaults.debug()),
                    Math.max(1, section.getInt("threshold", defaults.threshold())),
                    section.getBoolean("strict-mode", defaults.strictMode()),
                    section.getBoolean("uncancel-allowed-furniture", defaults.uncancelAllowedFurniture()),
                    section.getBoolean("block-message-enabled", defaults.blockMessageEnabled()),
                    section.getString("block-message", defaults.blockMessage()),
                    Math.max(0L, section.getLong("debug-cooldown-millis", defaults.debugCooldownMillis())),
                    Math.max(0L, section.getLong("block-message-cooldown-millis", defaults.blockMessageCooldownMillis())),
                    stringSet(section.getStringList("bypass-permissions"), defaults.bypassPermissions()),
                    entityTypes(detectSection, defaults.entityTypes()),
                    strings(detectSection, "name-contains", defaults.nameContains()),
                    strings(detectSection, "scoreboard-tag-contains", defaults.scoreboardTagContains()),
                    strings(detectSection, "pdc-key-contains", defaults.pdcKeyContains())
            );
        }

        private int effectiveThreshold() {
            return Math.max(1, strictMode ? threshold - 1 : threshold);
        }

        private static Set<String> stringSet(final List<String> values, final Set<String> fallback) {
            if (values.isEmpty()) {
                return fallback;
            }
            final Set<String> normalized = new HashSet<>();
            for (final String value : values) {
                if (value != null && !value.isBlank()) {
                    normalized.add(value.toLowerCase(Locale.ROOT).trim());
                }
            }
            return normalized.isEmpty() ? fallback : normalized;
        }

        private static List<String> strings(final ConfigurationSection section, final String path, final List<String> fallback) {
            if (section == null) {
                return fallback;
            }
            final List<String> configured = section.getStringList(path);
            if (configured.isEmpty()) {
                return fallback;
            }
            final List<String> values = new ArrayList<>();
            for (final String value : configured) {
                if (value != null && !value.isBlank()) {
                    values.add(value.toLowerCase(Locale.ROOT).trim());
                }
            }
            return values.isEmpty() ? fallback : List.copyOf(values);
        }

        private static Set<EntityType> entityTypes(final ConfigurationSection section, final Set<EntityType> fallback) {
            if (section == null) {
                return fallback;
            }
            final List<String> configured = section.getStringList("entity-types");
            if (configured.isEmpty()) {
                return fallback;
            }
            final Set<EntityType> types = EnumSet.noneOf(EntityType.class);
            for (final String value : configured) {
                try {
                    types.add(EntityType.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_')));
                } catch (final IllegalArgumentException ignored) {
                    // Invalid entity types are ignored so reloads stay usable.
                }
            }
            return types.isEmpty() ? fallback : types;
        }
    }
}
