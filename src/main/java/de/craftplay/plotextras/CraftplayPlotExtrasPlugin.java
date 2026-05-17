package de.craftplay.plotextras;

import com.google.common.eventbus.Subscribe;
import com.plotsquared.core.PlotAPI;
import com.plotsquared.core.PlotSquared;
import com.plotsquared.core.events.PlayerEnterPlotEvent;
import com.plotsquared.core.events.PlayerLeavePlotEvent;
import com.plotsquared.core.player.PlotPlayer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CraftplayPlotExtrasPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, FlightState> rememberedFlightStates = new HashMap<>();
    private final PlotAPI plotApi = new PlotAPI();

    private boolean cmiAvailable;
    private boolean restoreFlightInsidePlotWorlds;
    private boolean disableFlightOutsidePlotWorlds;
    private boolean useCmiCommand;
    private boolean ignoreCreativeAndSpectator;
    private boolean preventNamedEntityDespawn;
    private boolean debug;
    private List<Long> restoreDelayTicks;
    private List<Long> disableDelayTicks;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();

        final PluginManager pluginManager = getServer().getPluginManager();
        cmiAvailable = pluginManager.isPluginEnabled("CMI");
        getServer().getPluginManager().registerEvents(this, this);
        plotApi.registerListener(this);
        startFlightStateScanner();

        getLogger().info("Flight stays untouched in PlotSquared worlds and is disabled in non-plot worlds.");
        if (cmiAvailable && useCmiCommand) {
            getLogger().info("CMI detected. CMI /fly will be disabled automatically outside plot worlds.");
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(final PlayerChangedWorldEvent event) {
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
        final Player player = event.getPlayer();
        if (!isPlotWorld(player.getWorld())) {
            return;
        }

        getServer().getScheduler().runTask(this, () -> rememberFlightState(player));
    }

    @EventHandler
    public void onPlayerCommandPreprocess(final PlayerCommandPreprocessEvent event) {
        final Player player = event.getPlayer();
        if (!isPlotWorld(player.getWorld()) || !isFlyCommand(event.getMessage())) {
            return;
        }

        getServer().getScheduler().runTask(this, () -> rememberFlightState(player));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteractEntity(final PlayerInteractEntityEvent event) {
        if (!preventNamedEntityDespawn) {
            return;
        }

        final Entity clickedEntity = event.getRightClicked();
        if (!isPersistentNametagTarget(clickedEntity) || !isNameTag(event.getPlayer(), event.getHand())) {
            return;
        }

        getServer().getScheduler().runTask(this, () -> makeEntityPersistentIfNamed(clickedEntity));
    }

    @Subscribe
    public void onPlayerEnterPlot(final PlayerEnterPlotEvent event) {
        protectFlightInPlotWorld(event.getPlotPlayer());
    }

    @Subscribe
    public void onPlayerLeavePlot(final PlayerLeavePlotEvent event) {
        protectFlightInPlotWorld(event.getPlotPlayer());
    }

    private boolean isPlotWorld(final World world) {
        return PlotSquared.get().getPlotAreaManager().hasPlotArea(world.getName());
    }

    private void protectFlightInPlotWorld(final PlotPlayer<?> plotPlayer) {
        if (!restoreFlightInsidePlotWorlds) {
            return;
        }

        final Player player = getBukkitPlayer(plotPlayer);
        if (player == null || !isPlotWorld(player.getWorld())) {
            return;
        }

        rememberFlightState(player);
        scheduleFlightRestore(player);
    }

    private Player getBukkitPlayer(final PlotPlayer<?> plotPlayer) {
        final Object platformPlayer = plotPlayer.getPlatformPlayer();
        if (platformPlayer instanceof Player player && player.isOnline()) {
            return player;
        }

        return null;
    }

    private void rememberFlightState(final Player player) {
        if (!player.getAllowFlight() && !player.isFlying()) {
            return;
        }

        rememberedFlightStates.put(player.getUniqueId(), new FlightState(player.getAllowFlight(), player.isFlying()));
        debug("Remembered flight state for " + player.getName() + ": allowFlight="
                + player.getAllowFlight() + ", flying=" + player.isFlying());
    }

    private void startFlightStateScanner() {
        final long interval = Math.max(1L, getConfig().getLong("flight-state-scan-interval-ticks", 20L));
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (final Player player : getServer().getOnlinePlayers()) {
                if (isPlotWorld(player.getWorld())) {
                    rememberFlightState(player);
                }
            }
        }, interval, interval);
    }

    private void scheduleFlightRestore(final Player player) {
        final UUID playerId = player.getUniqueId();
        for (final long delay : restoreDelayTicks) {
            getServer().getScheduler().runTaskLater(this, () -> restoreFlight(playerId), delay);
        }
    }

    private void restoreFlight(final UUID playerId) {
        final Player player = getServer().getPlayer(playerId);
        if (player == null || !player.isOnline() || !isPlotWorld(player.getWorld())) {
            return;
        }

        final FlightState flightState = rememberedFlightStates.get(playerId);
        if (flightState == null || !flightState.allowFlight()) {
            return;
        }

        if (useCmiCommand && cmiAvailable) {
            getServer().dispatchCommand(getServer().getConsoleSender(), "cmi fly " + player.getName() + " true -s");
        }

        player.setAllowFlight(true);
        player.setFlying(flightState.flying());
        debug("Restored flight state for " + player.getName());
    }

    private void scheduleFlightDisable(final Player player) {
        if (!disableFlightOutsidePlotWorlds) {
            return;
        }

        disableFlight(player);
        final UUID playerId = player.getUniqueId();
        for (final long delay : disableDelayTicks) {
            getServer().getScheduler().runTaskLater(this, () -> {
                final Player delayedPlayer = getServer().getPlayer(playerId);
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

        disableCmiFlight(player);
        player.setFlying(false);
        player.setAllowFlight(false);
        debug("Disabled flight for " + player.getName() + " in world " + player.getWorld().getName());
    }

    private void disableCmiFlight(final Player player) {
        if (!useCmiCommand || !cmiAvailable) {
            return;
        }

        getServer().dispatchCommand(getServer().getConsoleSender(), "cmi fly " + player.getName() + " false -s");
    }

    private boolean isFlyCommand(final String message) {
        final String command = message.toLowerCase().trim();
        return command.equals("/fly")
                || command.startsWith("/fly ")
                || command.equals("/cmi fly")
                || command.startsWith("/cmi fly ");
    }

    private void loadSettings() {
        restoreFlightInsidePlotWorlds = getConfig().getBoolean("restore-flight-inside-plot-worlds", true);
        disableFlightOutsidePlotWorlds = getConfig().getBoolean("disable-flight-outside-plot-worlds", true);
        useCmiCommand = getConfig().getBoolean("use-cmi-command", true);
        ignoreCreativeAndSpectator = getConfig().getBoolean("ignore-creative-and-spectator", true);
        preventNamedEntityDespawn = getConfig().getBoolean("prevent-named-entity-despawn", true);
        debug = getConfig().getBoolean("debug", false);
        restoreDelayTicks = getTickList("restore-delay-ticks", List.of(1L, 5L));
        disableDelayTicks = getTickList("disable-delay-ticks", List.of(1L, 5L));
    }

    private List<Long> getTickList(final String path, final List<Long> fallback) {
        final List<Integer> configuredTicks = getConfig().getIntegerList(path);
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

    private boolean isNameTag(final Player player, final EquipmentSlot hand) {
        final ItemStack item = hand == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();

        return item.getType() == Material.NAME_TAG;
    }

    private boolean isPersistentNametagTarget(final Entity entity) {
        return entity instanceof Monster || entity instanceof Animals || entity instanceof Villager;
    }

    private void makeEntityPersistentIfNamed(final Entity entity) {
        if (!(entity instanceof LivingEntity livingEntity) || livingEntity.customName() == null) {
            return;
        }

        livingEntity.setRemoveWhenFarAway(false);
        livingEntity.setPersistent(true);
        debug("Made named entity persistent: " + livingEntity.getType());
    }

    private void debug(final String message) {
        if (debug) {
            getLogger().info("[Debug] " + message);
        }
    }

    private record FlightState(boolean allowFlight, boolean flying) {
    }
}
