package de.craftplay.plotextras.vehicles;

import com.plotsquared.core.plot.Plot;
import com.plotsquared.core.plot.flag.GlobalFlagContainer;
import de.craftplay.plotextras.CraftplayPlotExtrasPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;

public final class VehiclesIntegrationService implements Listener {

    private static final String VEHICLES_PLUGIN_NAME = "Vehicles";
    private static final String VEHICLE_PLACE_EVENT = "es.pollitoyeye.vehicles.events.VehiclePlaceEvent";
    private static final String VEHICLE_ENTER_EVENT = "es.pollitoyeye.vehicles.events.VehicleEnterEvent";

    private final CraftplayPlotExtrasPlugin plugin;
    private boolean registeredPlotSquaredFlag;
    private boolean registeredVehicleEvents;

    public VehiclesIntegrationService(final CraftplayPlotExtrasPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        registerPlotSquaredFlag();
        registerVehicleEvents();
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
        registeredVehicleEvents = false;
    }

    private void registerPlotSquaredFlag() {
        if (registeredPlotSquaredFlag || !plugin.getServer().getPluginManager().isPluginEnabled("PlotSquared")) {
            return;
        }
        try {
            GlobalFlagContainer.getInstance().addFlag(new VehiclesFlag(
                    false,
                    ChatColor.stripColor(plugin.getLanguageManager().getMessage("vehicles-flag-description"))
            ));
            registeredPlotSquaredFlag = true;
            plugin.getLogger().info("PlotSquared-Flag registriert: vehicles");
        } catch (final RuntimeException exception) {
            registeredPlotSquaredFlag = true;
            plugin.getLogger().warning("PlotSquared-Flag vehicles konnte nicht registriert werden: "
                    + exception.getMessage());
        }
    }

    private void registerVehicleEvents() {
        if (registeredVehicleEvents || !plugin.getServer().getPluginManager().isPluginEnabled(VEHICLES_PLUGIN_NAME)) {
            return;
        }

        boolean registered = false;
        registered = registerVehicleEvent(VEHICLE_PLACE_EVENT, (listener, event) -> handleVehiclePlace(event)) || registered;
        registered = registerVehicleEvent(VEHICLE_ENTER_EVENT, (listener, event) -> handleVehicleEnter(event)) || registered;
        registeredVehicleEvents = registered;
        if (registered) {
            plugin.getLogger().info("Vehicles-Integration aktiviert: PlotSquared-Flag vehicles");
        }
    }

    @SuppressWarnings("unchecked")
    private boolean registerVehicleEvent(final String className, final EventExecutor executor) {
        try {
            final Class<?> eventClass = Class.forName(className);
            if (!Event.class.isAssignableFrom(eventClass)) {
                return false;
            }
            plugin.getServer().getPluginManager().registerEvent(
                    (Class<? extends Event>) eventClass,
                    this,
                    EventPriority.HIGH,
                    executor,
                    plugin,
                    true
            );
            return true;
        } catch (final ClassNotFoundException exception) {
            return false;
        }
    }

    private void handleVehiclePlace(final Event event) {
        if (!(event instanceof Cancellable)) {
            return;
        }

        final Player player = value(event, "getOwner", Player.class);
        final Location location = value(event, "getLocation", Location.class);
        if (shouldBlockVehicles(player, location)) {
            ((Cancellable) event).setCancelled(true);
            plugin.getLanguageManager().send(player, "vehicles-plot-denied");
        }
    }

    private void handleVehicleEnter(final Event event) {
        if (!(event instanceof Cancellable)) {
            return;
        }

        final Player player = value(event, "getPlayer", Player.class);
        final Entity mainArmorStand = value(event, "getMainArmorStand", Entity.class);
        final Location location = mainArmorStand == null ? null : mainArmorStand.getLocation();
        if (shouldBlockVehicles(player, location)) {
            ((Cancellable) event).setCancelled(true);
            plugin.getLanguageManager().send(player, "vehicles-plot-denied");
        }
    }

    private boolean shouldBlockVehicles(final Player player, final Location location) {
        if (player == null || location == null || location.getWorld() == null) {
            return false;
        }

        try {
            final com.plotsquared.core.location.Location plotLocation = com.plotsquared.core.location.Location.at(
                    location.getWorld().getName(),
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ()
            );
            if (!plotLocation.isPlotArea()) {
                return false;
            }
            final Plot plot = plotLocation.getPlot();
            return plot == null || !plot.getFlag(VehiclesFlag.class);
        } catch (final Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "Vehicles-PlotSquared-Flag konnte nicht geprueft werden.", throwable);
            return true;
        }
    }

    private <T> T value(final Object target, final String methodName, final Class<T> expectedType) {
        if (target == null) {
            return null;
        }
        try {
            final Method method = target.getClass().getMethod(methodName);
            final Object value = method.invoke(target);
            if (expectedType.isInstance(value)) {
                return expectedType.cast(value);
            }
        } catch (final NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
        return null;
    }
}
