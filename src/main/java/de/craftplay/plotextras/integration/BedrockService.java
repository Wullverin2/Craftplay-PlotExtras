package de.craftplay.plotextras.integration;

import de.craftplay.plotextras.feature.FeatureToggleService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;

public final class BedrockService {

    private final JavaPlugin plugin;
    private final FeatureToggleService featureToggleService;
    private boolean floodgateAvailable;
    private Object floodgateApi;
    private Method isFloodgatePlayerMethod;

    public BedrockService(final JavaPlugin plugin, final FeatureToggleService featureToggleService) {
        this.plugin = plugin;
        this.featureToggleService = featureToggleService;
    }

    public void reload() {
        floodgateAvailable = false;
        floodgateApi = null;
        isFloodgatePlayerMethod = null;
        if (!featureToggleService.isEnabled("integrations.floodgate")
                || !plugin.getServer().getPluginManager().isPluginEnabled("floodgate")) {
            return;
        }

        try {
            final Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            floodgateApi = apiClass.getMethod("getInstance").invoke(null);
            isFloodgatePlayerMethod = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            floodgateAvailable = true;
        } catch (final ReflectiveOperationException exception) {
            plugin.getLogger().log(Level.WARNING, "Floodgate wurde gefunden, konnte aber nicht angebunden werden.", exception);
        }
    }

    public boolean isBedrockPlayer(final Player player) {
        if (!floodgateAvailable || player == null || isFloodgatePlayerMethod == null || floodgateApi == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(isFloodgatePlayerMethod.invoke(floodgateApi, player.getUniqueId()));
        } catch (final ReflectiveOperationException exception) {
            plugin.getLogger().log(Level.WARNING, "Bedrock-Status konnte nicht ueber Floodgate gelesen werden.", exception);
            return false;
        }
    }
}
