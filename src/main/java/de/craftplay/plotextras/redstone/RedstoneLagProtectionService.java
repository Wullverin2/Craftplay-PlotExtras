package de.craftplay.plotextras.redstone;

import com.plotsquared.bukkit.util.BukkitUtil;
import com.plotsquared.core.plot.Plot;
import com.plotsquared.core.plot.PlotId;
import de.craftplay.plotextras.audit.AuditLogService;
import de.craftplay.plotextras.plot.PlotService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class RedstoneLagProtectionService implements Listener {

    private final JavaPlugin plugin;
    private final PlotService plotService;
    private final AuditLogService auditLogService;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();
    private final Map<String, RedstoneAlert> alerts = new ConcurrentHashMap<>();
    private final Set<String> disabledPlotKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingAlertIds = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    private Settings settings = Settings.defaults();

    public RedstoneLagProtectionService(final JavaPlugin plugin, final PlotService plotService, final AuditLogService auditLogService) {
        this.plugin = plugin;
        this.plotService = plotService;
        this.auditLogService = auditLogService;
    }

    public void reload() {
        settings = Settings.from(plugin);
        counters.clear();
        cooldowns.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockRedstone(final BlockRedstoneEvent event) {
        if (!settings.enabled() || !settings.monitorRedstone() || event.getOldCurrent() == event.getNewCurrent()) {
            return;
        }
        if (isRuntimeDisabled(event.getBlock().getLocation())) {
            event.setNewCurrent(0);
            return;
        }
        if (handleActivity(event.getBlock(), "Redstone")) {
            event.setNewCurrent(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(final BlockPistonExtendEvent event) {
        if (!settings.enabled() || !settings.monitorPistons()) {
            return;
        }
        if (isRuntimeDisabled(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (handleActivity(event.getBlock(), "Kolben")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(final BlockPistonRetractEvent event) {
        if (!settings.enabled() || !settings.monitorPistons()) {
            return;
        }
        if (isRuntimeDisabled(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (handleActivity(event.getBlock(), "Kolben")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDispense(final BlockDispenseEvent event) {
        if (!settings.enabled() || !settings.monitorDispensers()) {
            return;
        }
        if (isRuntimeDisabled(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (handleActivity(event.getBlock(), "Werfer")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMoveItem(final InventoryMoveItemEvent event) {
        if (!settings.enabled() || !settings.monitorHoppers()) {
            return;
        }
        final Location location = event.getSource().getLocation();
        if (location == null) {
            return;
        }
        if (isRuntimeDisabled(location)) {
            event.setCancelled(true);
            return;
        }
        if (handleActivity(location.getBlock(), "Trichter")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        if (pendingAlertIds.isEmpty() || !canReceiveAlerts(event.getPlayer())) {
            return;
        }

        final List<String> queuedAlerts = new ArrayList<>(pendingAlertIds);
        queuedAlerts.sort(String.CASE_INSENSITIVE_ORDER);
        for (final String alertId : queuedAlerts) {
            final RedstoneAlert alert = alerts.get(alertId);
            if (alert != null) {
                sendAlert(event.getPlayer(), alert, true);
            }
            pendingAlertIds.remove(alertId);
        }
    }

    public boolean canAdmin(final Player player) {
        return player.hasPermission("craftplayplotextras.admin") || player.hasPermission(settings.adminPermission());
    }

    public boolean canReceiveAlerts(final Player player) {
        return canAdmin(player) || player.hasPermission(settings.notifyPermission());
    }

    public List<String> getAlertIds() {
        return alerts.values().stream()
                .sorted(Comparator.comparing(RedstoneAlert::detectedAt).reversed())
                .map(RedstoneAlert::id)
                .toList();
    }

    public boolean teleportToAlert(final Player player, final String alertId) {
        final RedstoneAlert alert = alerts.get(normalizeAlertId(alertId));
        if (alert == null) {
            return false;
        }

        final World world = Bukkit.getWorld(alert.worldName());
        if (world == null) {
            return false;
        }
        return player.teleport(new Location(
                world,
                alert.x() + 0.5D,
                alert.y() + 1.0D,
                alert.z() + 0.5D
        ));
    }

    public boolean enableRedstoneAtAlert(final Player player, final String alertId) {
        if (!canAdmin(player)) {
            return false;
        }

        final RedstoneAlert alert = alerts.get(normalizeAlertId(alertId));
        if (alert == null) {
            return false;
        }

        final Location location = alert.location();
        if (location == null) {
            return false;
        }

        final Plot plot = getPlot(location);
        if (plot == null) {
            return false;
        }
        final boolean enabled = enableRedstone(plot);
        if (enabled) {
            auditLogService.log(player, plot, "Redstone aktiviert", "Alarm " + alert.id() + " wurde wieder freigegeben.");
        }
        return enabled;
    }

    public boolean enableRedstoneAtCurrentPlot(final Player player) {
        if (!canAdmin(player)) {
            return false;
        }

        final Plot plot = plotService.getCurrentPlot(player);
        final boolean enabled = plot != null && enableRedstone(plot);
        if (enabled) {
            auditLogService.log(player, plot, "Redstone aktiviert", "Redstone wurde über den aktuellen Plot wieder freigegeben.");
        }
        return enabled;
    }

    private boolean enableRedstone(final Plot plot) {
        final boolean changed = plotService.setBooleanFlagOnConnectedPlots(plot, "redstone", true);
        final String key = plotKey(plot);
        disabledPlotKeys.remove(key);
        cooldowns.remove(key);
        counters.remove(key);
        removeAlertsForPlot(key);
        return changed;
    }

    private boolean handleActivity(final Block block, final String source) {
        final Plot plot = getPlot(block.getLocation());
        if (plot == null || !plot.hasOwner()) {
            return false;
        }

        final String key = plotKey(plot);
        if (disabledPlotKeys.contains(key)) {
            return true;
        }

        final long now = System.currentTimeMillis();
        final long cooldownUntil = cooldowns.getOrDefault(key, 0L);
        if (cooldownUntil > now) {
            return false;
        }

        final Counter counter = counters.computeIfAbsent(key, ignored -> new Counter(now));
        if (now - counter.windowStartedAt() > settings.windowMillis()) {
            counter.reset(now);
        }
        counter.increment(block.getLocation(), source);

        if (counter.events() > settings.maxEventsPerWindow()) {
            triggerProtection(plot, block.getLocation(), counter.events(), source, now);
            return true;
        }
        return false;
    }

    private void triggerProtection(
            final Plot plot,
            final Location location,
            final int eventCount,
            final String source,
            final long now
    ) {
        final String key = plotKey(plot);
        cooldowns.put(key, now + settings.cooldownMillis());
        disabledPlotKeys.add(key);
        counters.remove(key);

        final boolean disabled = plotService.setBooleanFlagOnConnectedPlots(plot, "redstone", false);
        if (!disabled) {
            plugin.getLogger().warning("Could not disable PlotSquared redstone flag for plot " + key + ".");
        }

        final Plot basePlot = plot.getBasePlot(false);
        final RedstoneAlert alert = new RedstoneAlert(
                createAlertId(basePlot),
                key,
                basePlot.getWorldName(),
                basePlot.getId().toString(),
                ownerName(basePlot.getOwnerAbs()),
                mergeSize(basePlot.getConnectedPlots()),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ(),
                eventCount,
                source,
                Instant.now()
        );
        alerts.put(alert.id(), alert);

        boolean sent = false;
        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (canReceiveAlerts(player)) {
                sendAlert(player, alert, false);
                sent = true;
            }
        }

        if (!sent) {
            pendingAlertIds.add(alert.id());
            plugin.getLogger().warning("Redstone lag alert queued for first online team member: " + alert.id());
        }
        auditLogService.log("System", basePlot, "Redstone deaktiviert",
                "Lagmaschine erkannt: " + eventCount + " Events, Quelle: " + source + ", Alarm: " + alert.id());
    }

    private void sendAlert(final Player player, final RedstoneAlert alert, final boolean delayed) {
        Component message = Component.text("[PlotExtras] ", NamedTextColor.DARK_RED)
                .append(Component.text("Redstone-Lagmaschine erkannt", NamedTextColor.RED))
                .append(Component.text(delayed ? " (nachgereicht)" : "", NamedTextColor.GRAY))
                .append(Component.text(": Plot ", NamedTextColor.GRAY))
                .append(Component.text(alert.worldName() + " " + alert.plotId(), NamedTextColor.YELLOW))
                .append(Component.text(" | Merge ", NamedTextColor.GRAY))
                .append(Component.text(alert.mergeSize(), NamedTextColor.YELLOW))
                .append(Component.text(" | Block ", NamedTextColor.GRAY))
                .append(Component.text(alert.x() + ", " + alert.y() + ", " + alert.z(), NamedTextColor.YELLOW))
                .append(Component.text(" | Events ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(alert.eventCount()), NamedTextColor.YELLOW))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(button("Teleport", "/pe redstone tp " + alert.id(), "Zum erkannten Redstone-Block teleportieren"));

        if (canAdmin(player)) {
            message = message
                    .append(Component.text(" ", NamedTextColor.GRAY))
                    .append(button("Redstone aktivieren", "/pe redstone enable " + alert.id(), "Redstone auf diesem Plot wieder aktivieren"));
        }

        player.sendMessage(message);
    }

    private Component button(final String text, final String command, final String hover) {
        return Component.text("[" + text + "]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)));
    }

    private boolean isRuntimeDisabled(final Location location) {
        final Plot plot = getPlot(location);
        return plot != null && disabledPlotKeys.contains(plotKey(plot));
    }

    private Plot getPlot(final Location location) {
        if (location == null) {
            return null;
        }
        try {
            return Plot.getPlot(BukkitUtil.adapt(location));
        } catch (final RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not resolve PlotSquared plot for redstone protection.", exception);
            return null;
        }
    }

    private String plotKey(final Plot plot) {
        final Plot basePlot = plot.getBasePlot(false);
        return basePlot.getWorldName() + ":" + basePlot.getId().toDashSeparatedString();
    }

    private void removeAlertsForPlot(final String plotKey) {
        final Set<String> removedAlerts = new HashSet<>();
        alerts.entrySet().removeIf(entry -> {
            if (!entry.getValue().plotKey().equals(plotKey)) {
                return false;
            }
            removedAlerts.add(entry.getKey());
            return true;
        });
        pendingAlertIds.removeAll(removedAlerts);
    }

    private String createAlertId(final Plot plot) {
        return ("rs-" + sanitize(plot.getWorldName())
                + "-" + sanitize(plot.getId().toDashSeparatedString())
                + "-" + Long.toString(System.currentTimeMillis(), 36)).toLowerCase(Locale.ROOT);
    }

    private String normalizeAlertId(final String alertId) {
        return alertId == null ? "" : alertId.toLowerCase(Locale.ROOT).trim();
    }

    private String ownerName(final UUID ownerUuid) {
        if (ownerUuid == null) {
            return "-";
        }
        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(ownerUuid);
        return offlinePlayer.getName() == null ? ownerUuid.toString() : offlinePlayer.getName();
    }

    private String mergeSize(final Set<Plot> plots) {
        if (plots.isEmpty()) {
            return "1x1";
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (final Plot connectedPlot : plots) {
            final PlotId id = connectedPlot.getId();
            minX = Math.min(minX, id.getX());
            maxX = Math.max(maxX, id.getX());
            minY = Math.min(minY, id.getY());
            maxY = Math.max(maxY, id.getY());
        }
        return Math.max(1, maxX - minX + 1) + "x" + Math.max(1, maxY - minY + 1);
    }

    private String sanitize(final String input) {
        return Optional.ofNullable(input)
                .orElse("")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]", "_");
    }

    private record RedstoneAlert(
            String id,
            String plotKey,
            String worldName,
            String plotId,
            String ownerName,
            String mergeSize,
            int x,
            int y,
            int z,
            int eventCount,
            String source,
            Instant detectedAt
    ) {
        private Location location() {
            final World world = Bukkit.getWorld(worldName);
            return world == null ? null : new Location(world, x, y, z);
        }
    }

    private static final class Counter {
        private long windowStartedAt;
        private int events;
        private Location lastLocation;
        private String lastSource = "Redstone";

        private Counter(final long windowStartedAt) {
            this.windowStartedAt = windowStartedAt;
        }

        private long windowStartedAt() {
            return windowStartedAt;
        }

        private int events() {
            return events;
        }

        private void reset(final long now) {
            windowStartedAt = now;
            events = 0;
            lastLocation = null;
            lastSource = "Redstone";
        }

        private void increment(final Location location, final String source) {
            events++;
            lastLocation = location;
            lastSource = source;
        }
    }

    private record Settings(
            boolean enabled,
            long windowMillis,
            int maxEventsPerWindow,
            long cooldownMillis,
            String notifyPermission,
            String adminPermission,
            boolean monitorRedstone,
            boolean monitorPistons,
            boolean monitorDispensers,
            boolean monitorHoppers
    ) {
        private static Settings defaults() {
            return new Settings(
                    true,
                    10_000L,
                    250,
                    60_000L,
                    "craftplayplotextras.redstone.notify",
                    "craftplayplotextras.redstone.admin",
                    true,
                    true,
                    true,
                    true
            );
        }

        private static Settings from(final JavaPlugin plugin) {
            final String path = "redstone-lag-protection.";
            return new Settings(
                    plugin.getConfig().getBoolean(path + "enabled", true),
                    Math.max(1L, plugin.getConfig().getLong(path + "window-seconds", 10L)) * 1000L,
                    Math.max(1, plugin.getConfig().getInt(path + "max-events-per-window", 250)),
                    Math.max(1L, plugin.getConfig().getLong(path + "cooldown-seconds", 60L)) * 1000L,
                    plugin.getConfig().getString(path + "notify-permission", "craftplayplotextras.redstone.notify"),
                    plugin.getConfig().getString(path + "admin-permission", "craftplayplotextras.redstone.admin"),
                    plugin.getConfig().getBoolean(path + "monitored-events.redstone", true),
                    plugin.getConfig().getBoolean(path + "monitored-events.pistons", true),
                    plugin.getConfig().getBoolean(path + "monitored-events.dispensers", true),
                    plugin.getConfig().getBoolean(path + "monitored-events.hoppers", true)
            );
        }
    }
}
