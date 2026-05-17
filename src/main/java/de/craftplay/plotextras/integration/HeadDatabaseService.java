package de.craftplay.plotextras.integration;

import de.craftplay.plotextras.feature.FeatureToggleService;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.logging.Level;

public final class HeadDatabaseService {

    private final JavaPlugin plugin;
    private final FeatureToggleService featureToggleService;
    private boolean available;
    private Object api;
    private Method getItemHeadMethod;

    public HeadDatabaseService(final JavaPlugin plugin, final FeatureToggleService featureToggleService) {
        this.plugin = plugin;
        this.featureToggleService = featureToggleService;
    }

    public void reload() {
        available = featureToggleService.isEnabled("integrations.head-database")
                && plugin.getServer().getPluginManager().isPluginEnabled("HeadDatabase");
        api = null;
        getItemHeadMethod = null;
        if (!available) {
            return;
        }

        try {
            final Class<?> apiClass = Class.forName("me.arcaniax.hdb.api.HeadDatabaseAPI");
            api = apiClass.getDeclaredConstructor().newInstance();
            getItemHeadMethod = apiClass.getMethod("getItemHead", String.class);
        } catch (final ReflectiveOperationException exception) {
            available = false;
            plugin.getLogger().log(Level.WARNING, "HeadDatabase was found but could not be hooked.", exception);
        }
    }

    public Optional<ItemStack> getHead(final String id) {
        if (!available || api == null || getItemHeadMethod == null || id == null || id.isBlank()) {
            return Optional.empty();
        }

        try {
            final Object item = getItemHeadMethod.invoke(api, id);
            if (item instanceof ItemStack itemStack) {
                return Optional.of(itemStack.clone());
            }
        } catch (final ReflectiveOperationException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not load HeadDatabase head " + id + ".", exception);
        }
        return Optional.empty();
    }
}
