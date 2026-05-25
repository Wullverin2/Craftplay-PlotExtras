package de.craftplay.plotextras.nofly;

import com.google.common.eventbus.Subscribe;
import com.plotsquared.core.PlotAPI;
import com.plotsquared.core.PlotSquared;
import com.plotsquared.core.events.PlayerEnterPlotEvent;
import com.plotsquared.core.events.PlayerLeavePlotEvent;
import com.plotsquared.core.player.PlotPlayer;
import de.craftplay.plotextras.CraftplayPlotExtrasPlugin;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class PlotNoFlyService implements Listener {

    private final CraftplayPlotExtrasPlugin plugin;
    private final Map<UUID, FlightState> rememberedFlightStates = new HashMap<>();

    private boolean enabled;
    private boolean plotListenerRegistered;
    private boolean cmiAvailable;
    private boolean restoreFlightInsidePlotWorlds;
    private boolean disableFlightOutsidePlotWorlds;
    private boolean useCmiCommand;
    private boolean ignoreCreativeAndSpectator;
    private boolean debug;
    private List<Long> restoreDelayTicks = new ArrayList<>();
    private List<Long> disableDelayTicks = new ArrayList<>();
    private BukkitTask scannerTask;

    public PlotNoFlyService(final CraftplayPlotExtrasPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("plot-no-fly.enabled", true);
        restoreFlightInsidePlotWorlds = plugin.getConfig().getBoolean("plot-no-fly.restore-flight-inside-plot-worlds", true);
        disableFlightOutsidePlotWorlds = plugin.getConfig().getBoolean("plot-no-fly.disable-flight-outside-plot-worlds", true);
        useCmiCommand = plugin.getConfig().getBoolean("plot-no-fly.use-cmi-command", true);
        ignoreCreativeAndSpectator = plugin.getConfig().getBoolean("plot-no-fly.ignore-creative-and-spectator", true);
        debug = plugin.getConfig().getBoolean("plot-no-fly.debug", false);
        restoreDelayTicks = tickList("plot-no-fly.restore-delay-ticks", defaults(1L, 5L));
        disableDelayTicks = tickList("plot-no-fly.disable-delay-ticks", defaults(1L, 5L));
        cmiAvailable = plugin.getServer().getPluginManager().isPluginEnabled("CMI");

        if (!enabled) {
            rememberedFlightStates.clear();
            stopScanner();
            return;
        }

        registerPlotSquaredListener();
        restartScanner();
    }

    public void shutdown() {
        enabled = false;
        stopScanner();
        rememberedFlightStates.clear();
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onPlayerChangedWorld(final PlayerChangedWorldEvent event) {
        if (!enabled) {
            return;
        }
        final Player player = event.getPlayer();
        if (isPlotWorld(player.getWorld())) {
            rememberFlightState(player);
            return;
        }
        rememberedFlightStates.remove(player.getUniqueId());
        scheduleFlightDisable(player);
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        rememberedFlightStates.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerToggleFlight(final PlayerToggleFlightEvent event) {
        if (!enabled) {
            return;
        }
        final Player player = event.getPlayer();
        if (!isPlotWorld(player.getWorld())) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> rememberFlightState(player));
    }

    @EventHandler
    public void onPlayerCommandPreprocess(final PlayerCommandPreprocessEvent event) {
        if (!enabled) {
            return;
        }
        final Player player = event.getPlayer();
        if (!isFlyCommand(event.getMessage())) {
            return;
        }
        if (isPlotWorld(player.getWorld())) {
            plugin.getServer().getScheduler().runTask(plugin, () -> rememberFlightState(player));
        }
    }

    @Subscribe
    public void onPlayerEnterPlot(final PlayerEnterPlotEvent event) {
        protectFlightInPlotWorld(event.getPlotPlayer());
    }

    @Subscribe
    public void onPlayerLeavePlot(final PlayerLeavePlotEvent event) {
        protectFlightInPlotWorld(event.getPlotPlayer());
    }

    private void registerPlotSquaredListener() {
        if (plotListenerRegistered || !plugin.getServer().getPluginManager().isPluginEnabled("PlotSquared")) {
            return;
        }
        try {
            new PlotAPI().registerListener(this);
            plotListenerRegistered = true;
        } catch (final RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "PlotSquared-No-Fly-Listener konnte nicht registriert werden.", exception);
        }
    }

    private boolean isPlotWorld(final World world) {
        if (world == null) {
            return false;
        }
        try {
            return PlotSquared.get().getPlotAreaManager().hasPlotArea(world.getName());
        } catch (final RuntimeException exception) {
            debug("Plotwelt-Prüfung fehlgeschlagen: " + exception.getMessage());
            return false;
        }
    }

    private void protectFlightInPlotWorld(final PlotPlayer<?> plotPlayer) {
        if (!enabled || !restoreFlightInsidePlotWorlds) {
            return;
        }
        final Player player = bukkitPlayer(plotPlayer);
        if (player == null || !isPlotWorld(player.getWorld())) {
            return;
        }
        rememberFlightState(player);
        scheduleFlightRestore(player);
    }

    private Player bukkitPlayer(final PlotPlayer<?> plotPlayer) {
        if (plotPlayer == null) {
            return null;
        }
        final Object platformPlayer = plotPlayer.getPlatformPlayer();
        if (platformPlayer instanceof Player) {
            final Player player = (Player) platformPlayer;
            return player.isOnline() ? player : null;
        }
        return null;
    }

    private void rememberFlightState(final Player player) {
        if (!player.getAllowFlight() && !player.isFlying()) {
            return;
        }
        rememberedFlightStates.put(player.getUniqueId(), new FlightState(player.getAllowFlight(), player.isFlying()));
        debug("Fly-Status gespeichert für " + player.getName()
                + ": allowFlight=" + player.getAllowFlight() + ", flying=" + player.isFlying());
    }

    private void restartScanner() {
        stopScanner();
        final long interval = Math.max(1L, plugin.getConfig().getLong("plot-no-fly.flight-state-scan-interval-ticks", 20L));
        scannerTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!enabled) {
                return;
            }
            for (final Player player : plugin.getServer().getOnlinePlayers()) {
                if (isPlotWorld(player.getWorld())) {
                    rememberFlightState(player);
                }
            }
        }, interval, interval);
    }

    private void stopScanner() {
        if (scannerTask != null) {
            scannerTask.cancel();
            scannerTask = null;
        }
    }

    private void scheduleFlightRestore(final Player player) {
        final UUID playerId = player.getUniqueId();
        for (final long delay : restoreDelayTicks) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> restoreFlight(playerId), delay);
        }
    }

    private void restoreFlight(final UUID playerId) {
        final Player player = plugin.getServer().getPlayer(playerId);
        if (!enabled || player == null || !player.isOnline() || !isPlotWorld(player.getWorld())) {
            return;
        }
        final FlightState flightState = rememberedFlightStates.get(playerId);
        if (flightState == null || !flightState.allowFlight) {
            return;
        }
        if (useCmiCommand && cmiAvailable) {
            plugin.getServer().dispatchCommand((CommandSender) plugin.getServer().getConsoleSender(),
                    "cmi fly " + player.getName() + " true -s");
        }
        player.setAllowFlight(true);
        player.setFlying(flightState.flying);
        debug("Fly-Status wiederhergestellt für " + player.getName());
    }

    private void scheduleFlightDisable(final Player player) {
        if (!disableFlightOutsidePlotWorlds) {
            return;
        }
        disableFlight(player);
        final UUID playerId = player.getUniqueId();
        for (final long delay : disableDelayTicks) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                final Player delayedPlayer = plugin.getServer().getPlayer(playerId);
                if (delayedPlayer != null && delayedPlayer.isOnline() && !isPlotWorld(delayedPlayer.getWorld())) {
                    disableFlight(delayedPlayer);
                }
            }, delay);
        }
    }

    private void disableFlight(final Player player) {
        if (ignoreCreativeAndSpectator
                && (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR)) {
            return;
        }
        if (useCmiCommand && cmiAvailable) {
            plugin.getServer().dispatchCommand((CommandSender) plugin.getServer().getConsoleSender(),
                    "cmi fly " + player.getName() + " false -s");
        }
        player.setFlying(false);
        player.setAllowFlight(false);
        debug("Fly deaktiviert für " + player.getName() + " in Welt " + player.getWorld().getName());
    }

    private boolean isFlyCommand(final String message) {
        if (message == null) {
            return false;
        }
        final String command = message.toLowerCase().trim();
        return command.equals("/fly")
                || command.startsWith("/fly ")
                || command.equals("/cmi fly")
                || command.startsWith("/cmi fly ");
    }

    private List<Long> tickList(final String path, final List<Long> fallback) {
        final List<Integer> configuredTicks = plugin.getConfig().getIntegerList(path);
        if (configuredTicks.isEmpty()) {
            return fallback;
        }
        final List<Long> ticks = new ArrayList<>();
        for (final int tick : configuredTicks) {
            if (tick >= 0) {
                ticks.add((long) tick);
            }
        }
        return ticks.isEmpty() ? fallback : ticks;
    }

    private List<Long> defaults(final long first, final long second) {
        final List<Long> values = new ArrayList<>();
        values.add(first);
        values.add(second);
        return values;
    }

    private void debug(final String message) {
        if (debug) {
            plugin.getLogger().info("[PlotNoFly] " + message);
        }
    }

    private static final class FlightState {
        private final boolean allowFlight;
        private final boolean flying;

        private FlightState(final boolean allowFlight, final boolean flying) {
            this.allowFlight = allowFlight;
            this.flying = flying;
        }
    }
}
