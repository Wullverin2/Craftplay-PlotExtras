package de.craftplay.plotextras.plotsquared;

import com.plotsquared.core.plot.flag.GlobalFlagContainer;
import de.craftplay.plotextras.furniture.FurnitureInteractFlag;
import de.craftplay.plotextras.furniture.FurnitureModifyFlag;
import de.craftplay.plotextras.furniture.FurnitureSitFlag;
import de.craftplay.plotextras.furniture.PreventCropTrampleFlag;
import de.craftplay.plotextras.passivewither.PassiveWitherSpawnFlag;
import de.craftplay.plotextras.vehicles.VehiclesFlag;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlotSquaredCustomFlagRegistry {

    private PlotSquaredCustomFlagRegistry() {
    }

    public static void registerAll(final JavaPlugin plugin) {
        registerPassiveWither(plugin);
        registerVehicles(plugin);
        registerFurniture(plugin);
    }

    public static boolean registerPassiveWither(final JavaPlugin plugin) {
        return addFlag(plugin, new PassiveWitherSpawnFlag(false,
                "Erlaubt das Spawnen passiver Wither mit dem Passive-Wither-Spawnei."));
    }

    public static boolean registerVehicles(final JavaPlugin plugin) {
        return addFlag(plugin, new VehiclesFlag(false,
                "Erlaubt Fahrzeuge des Vehicles-Plugins auf diesem Plot."));
    }

    public static boolean registerFurniture(final JavaPlugin plugin) {
        boolean registered = false;
        registered = addFlag(plugin, new FurnitureInteractFlag(false,
                "Erlaubt das Benutzen erkannter Möbel auf diesem Plot.")) || registered;
        registered = addFlag(plugin, new FurnitureSitFlag(false,
                "Erlaubt das Sitzen auf erkannten Möbeln auf diesem Plot.")) || registered;
        registered = addFlag(plugin, new FurnitureModifyFlag(false,
                "Erlaubt das Bearbeiten oder Beschädigen erkannter Möbel auf diesem Plot.")) || registered;
        registered = addFlag(plugin, new PreventCropTrampleFlag(false,
                "Verhindert das Zertrampeln von Feldern auf diesem Plot.")) || registered;
        return registered;
    }

    private static boolean addFlag(final JavaPlugin plugin, final com.plotsquared.core.plot.flag.PlotFlag<?, ?> flag) {
        try {
            final GlobalFlagContainer container = GlobalFlagContainer.getInstance();
            if (container == null) {
                return false;
            }
            container.addFlag(flag);
            return true;
        } catch (final RuntimeException exception) {
            if (plugin != null) {
                plugin.getLogger().warning("PlotSquared-Flag '" + flag.getName()
                        + "' konnte nicht registriert werden: " + exception.getMessage());
            }
            return false;
        }
    }
}
