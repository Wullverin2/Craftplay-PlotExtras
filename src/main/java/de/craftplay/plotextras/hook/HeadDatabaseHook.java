package de.craftplay.plotextras.hook;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public final class HeadDatabaseHook {

    private final JavaPlugin plugin;
    private final Map<String, ItemStack> cache = new HashMap<>();

    private Object api;
    private Method getItemHeadMethod;
    private boolean missingWarned;
    private boolean errorWarned;

    public HeadDatabaseHook(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack getHead(final String id) {
        if (id == null || id.trim().isEmpty()) {
            return null;
        }
        final String normalizedId = id.trim().toLowerCase(Locale.ROOT);
        final ItemStack cached = cache.get(normalizedId);
        if (cached != null) {
            return cached.clone();
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("HeadDatabase")) {
            warnMissing();
            return null;
        }
        if (!loadApi()) {
            return null;
        }
        try {
            final Object item = getItemHeadMethod.invoke(api, id.trim());
            if (item instanceof ItemStack) {
                final ItemStack head = ((ItemStack) item).clone();
                cache.put(normalizedId, head.clone());
                return head;
            }
        } catch (final IllegalAccessException | InvocationTargetException exception) {
            warnError("HeadDatabase-Kopf konnte nicht geladen werden: " + id, exception);
        }
        return null;
    }

    private boolean loadApi() {
        if (api != null && getItemHeadMethod != null) {
            return true;
        }
        try {
            final Class<?> apiClass = Class.forName("me.arcaniax.hdb.api.HeadDatabaseAPI");
            api = apiClass.getConstructor().newInstance();
            getItemHeadMethod = apiClass.getMethod("getItemHead", String.class);
            return true;
        } catch (final ClassNotFoundException | NoSuchMethodException | InstantiationException
                       | IllegalAccessException | InvocationTargetException exception) {
            warnError("HeadDatabase-API konnte nicht initialisiert werden.", exception);
            return false;
        }
    }

    private void warnMissing() {
        if (missingWarned) {
            return;
        }
        missingWarned = true;
        plugin.getLogger().warning("HeadDatabase ist nicht geladen. HeadDatabase-Köpfe in GUIs nutzen Material-Fallbacks.");
    }

    private void warnError(final String message, final Exception exception) {
        if (errorWarned) {
            return;
        }
        errorWarned = true;
        plugin.getLogger().log(Level.WARNING, message, exception);
    }
}
