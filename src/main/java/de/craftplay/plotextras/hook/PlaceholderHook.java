package de.craftplay.plotextras.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public final class PlaceholderHook {

    private final JavaPlugin plugin;

    private Method setPlaceholdersMethod;
    private boolean missingWarned;
    private boolean errorWarned;

    public PlaceholderHook(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public String apply(final Player player, final String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        if (text.indexOf('%') < 0) {
            return text;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return text;
        }
        if (!loadApi()) {
            return text;
        }
        try {
            final Object result = setPlaceholdersMethod.invoke(null, player, text);
            if (result instanceof String) {
                return (String) result;
            }
        } catch (final IllegalAccessException | InvocationTargetException exception) {
            warnError("PlaceholderAPI-Platzhalter konnten nicht ersetzt werden.", exception);
        }
        return text;
    }

    public List<String> apply(final Player player, final List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return lines;
        }
        final List<String> replaced = new ArrayList<>();
        for (final String line : lines) {
            replaced.add(apply(player, line));
        }
        return replaced;
    }

    private boolean loadApi() {
        if (setPlaceholdersMethod != null) {
            return true;
        }
        try {
            final Class<?> apiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            for (final Method method : apiClass.getMethods()) {
                if (!"setPlaceholders".equals(method.getName()) || !Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                final Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != 2 || !String.class.equals(parameterTypes[1])) {
                    continue;
                }
                if (parameterTypes[0].isAssignableFrom(Player.class)) {
                    setPlaceholdersMethod = method;
                    return true;
                }
            }
            warnMissing();
            return false;
        } catch (final ClassNotFoundException exception) {
            warnMissing();
            return false;
        }
    }

    private void warnMissing() {
        if (missingWarned) {
            return;
        }
        missingWarned = true;
        plugin.getLogger().warning("PlaceholderAPI ist nicht geladen. Platzhalter in GUIs bleiben unverändert.");
    }

    private void warnError(final String message, final Exception exception) {
        if (errorWarned) {
            return;
        }
        errorWarned = true;
        plugin.getLogger().log(Level.WARNING, message, exception);
    }
}
