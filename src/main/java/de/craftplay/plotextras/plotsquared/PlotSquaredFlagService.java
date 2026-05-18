package de.craftplay.plotextras.plotsquared;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;

public final class PlotSquaredFlagService {

    private final JavaPlugin plugin;
    private boolean warned;

    public PlotSquaredFlagService(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isOnPlot(final Player player) {
        return currentPlot(player) != null;
    }

    public boolean isBooleanFlag(final String flagName) {
        final Object flag = flag(flagName);
        return flag != null && (isBooleanFlagObject(flag) || canParseBooleanValues(flag));
    }

    public boolean isFlagEnabled(final Player player, final String flagName) {
        final Object plot = currentPlot(player);
        final Object flag = flag(flagName);
        if (plot == null || flag == null) {
            return false;
        }

        final Object value = getFlagValue(plot, flag);
        return isEnabledValue(value);
    }

    public boolean hasAnyFlagPermission(final Player player, final String flagName) {
        return hasPlotSquaredPermission(player, "plots.admin")
                || hasPlotSquaredPermission(player, "plots.admin.command.set.flag")
                || hasPlotSquaredPermission(player, "plots.set.flag")
                || hasPlotSquaredPermission(player, "plots.flag")
                || hasPlotSquaredPermission(player, "plots.flag.add")
                || hasPlotSquaredPermission(player, "plots.flag.remove")
                || hasPlotSquaredPermission(player, "plots.set.flag." + flagName)
                || hasPlotSquaredPermission(player, "plots.set.flag." + flagName + ".*")
                || hasPlotSquaredPermission(player, "plots.set.flag." + flagName + ".true")
                || hasPlotSquaredPermission(player, "plots.set.flag." + flagName + ".false");
    }

    public boolean hasTogglePermission(final Player player, final String flagName, final boolean targetValue) {
        return hasPlotSquaredPermission(player, "plots.admin")
                || hasPlotSquaredPermission(player, "plots.admin.command.set.flag")
                || hasPlotSquaredPermission(player, "plots.set.flag")
                || hasPlotSquaredPermission(player, "plots.flag")
                || hasPlotSquaredPermission(player, "plots.flag.add")
                || hasPlotSquaredPermission(player, "plots.flag.remove")
                || hasPlotSquaredPermission(player, "plots.set.flag." + flagName)
                || hasPlotSquaredPermission(player, "plots.set.flag." + flagName + ".*")
                || hasPlotSquaredPermission(player, "plots.set.flag." + flagName + "." + targetValue);
    }

    public boolean hasPermission(final Player player, final String permission) {
        return hasPlotSquaredPermission(player, permission);
    }

    public void toggleBooleanFlag(final Player player, final String flagName) {
        final boolean current = isFlagEnabled(player, flagName);
        final boolean target = !current;
        player.performCommand("plot flag set " + flagName + " " + target);
    }

    private Object currentPlot(final Player player) {
        final Object plotPlayer = plotPlayer(player);
        if (plotPlayer == null) {
            return null;
        }
        try {
            final Method method = plotPlayer.getClass().getMethod("getCurrentPlot");
            return method.invoke(plotPlayer);
        } catch (final ReflectiveOperationException exception) {
            warn("PlotSquared-Spielerobjekt konnte nicht gelesen werden.", exception);
            return null;
        }
    }

    private Object plotPlayer(final Player player) {
        final Object oldPlayer = invokeStatic(
                "com.github.intellectualsites.plotsquared.bukkit.util.BukkitUtil",
                "getPlayer",
                Player.class,
                player
        );
        if (oldPlayer != null) {
            return oldPlayer;
        }
        return invokeStatic("com.plotsquared.bukkit.util.BukkitUtil", "adapt", Player.class, player);
    }

    private boolean hasPlotSquaredPermission(final Player player, final String permission) {
        final Object plotPlayer = plotPlayer(player);
        if (plotPlayer == null) {
            return player.hasPermission(permission);
        }
        try {
            final Method method = plotPlayer.getClass().getMethod("hasPermission", String.class);
            final Object result = method.invoke(plotPlayer, permission);
            return result instanceof Boolean && (Boolean) result;
        } catch (final ReflectiveOperationException exception) {
            return player.hasPermission(permission);
        }
    }

    private Object flag(final String flagName) {
        final Object modernFlag = invokeStatic(
                "com.plotsquared.core.plot.flag.GlobalFlagContainer",
                "getInstance"
        );
        if (modernFlag != null) {
            final Object resolvedFlag = invoke(
                    modernFlag,
                    "getFlagFromString",
                    String.class,
                    flagName.toLowerCase(Locale.ROOT)
            );
            if (resolvedFlag != null) {
                return resolvedFlag;
            }
        }

        final Object oldFlag = invokeStatic(
                "com.github.intellectualsites.plotsquared.plot.flag.Flags",
                "getFlag",
                String.class,
                flagName.toLowerCase(Locale.ROOT)
        );
        if (oldFlag != null) {
            return oldFlag;
        }
        return null;
    }

    private Object getFlagValue(final Object plot, final Object flag) {
        final Class<?> flagClass = findFlagBaseClass(flag);
        if (flagClass == null) {
            return false;
        }
        try {
            final Method method = plot.getClass().getMethod("getFlag", flagClass, Object.class);
            return method.invoke(plot, flag, Boolean.FALSE);
        } catch (final NoSuchMethodException ignored) {
            return getOptionalFlagValue(plot, flag, flagClass);
        } catch (final IllegalAccessException | InvocationTargetException exception) {
            warn("PlotSquared-Flag konnte nicht gelesen werden.", exception);
            return false;
        }
    }

    private Object getOptionalFlagValue(final Object plot, final Object flag, final Class<?> flagClass) {
        try {
            final Method method = plot.getClass().getMethod("getFlag", flagClass);
            final Object value = method.invoke(plot, flag);
            if (value instanceof Optional) {
                final Optional<?> optional = (Optional<?>) value;
                return optional.isPresent() ? optional.get() : Boolean.FALSE;
            }
            return value;
        } catch (final ReflectiveOperationException exception) {
            warn("PlotSquared-Flag konnte nicht gelesen werden.", exception);
            return false;
        }
    }

    private Class<?> findFlagBaseClass(final Object flag) {
        Class<?> type = flag.getClass();
        while (type != null) {
            if ("PlotFlag".equals(type.getSimpleName()) || "Flag".equals(type.getSimpleName())) {
                return type;
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private boolean isBooleanFlagObject(final Object flag) {
        Class<?> type = flag.getClass();
        while (type != null) {
            if ("BooleanFlag".equals(type.getSimpleName())) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private boolean canParseBooleanValues(final Object flag) {
        return parseFlag(flag, "true") != null && parseFlag(flag, "false") != null;
    }

    private Object parseFlag(final Object flag, final String value) {
        try {
            final Method method = flag.getClass().getMethod("parse", String.class);
            return method.invoke(flag, value);
        } catch (final ReflectiveOperationException exception) {
            return null;
        }
    }

    private boolean isEnabledValue(final Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value == null) {
            return false;
        }
        final String normalized = value.toString().trim().toLowerCase(Locale.ROOT);
        return "true".equals(normalized)
                || "enabled".equals(normalized)
                || "enable".equals(normalized)
                || "on".equals(normalized)
                || "allow".equals(normalized)
                || "allowed".equals(normalized);
    }

    private Object invokeStatic(final String className, final String methodName) {
        try {
            final Class<?> clazz = Class.forName(className);
            final Method method = clazz.getMethod(methodName);
            return method.invoke(null);
        } catch (final ClassNotFoundException | NoSuchMethodException ignored) {
            return null;
        } catch (final IllegalAccessException | InvocationTargetException exception) {
            warn("PlotSquared-Methode konnte nicht aufgerufen werden: " + className + "#" + methodName, exception);
            return null;
        }
    }

    private Object invokeStatic(
            final String className,
            final String methodName,
            final Class<?> parameterType,
            final Object argument
    ) {
        try {
            final Class<?> clazz = Class.forName(className);
            final Method method = clazz.getMethod(methodName, parameterType);
            return method.invoke(null, argument);
        } catch (final ClassNotFoundException | NoSuchMethodException ignored) {
            return null;
        } catch (final IllegalAccessException | InvocationTargetException exception) {
            warn("PlotSquared-Methode konnte nicht aufgerufen werden: " + className + "#" + methodName, exception);
            return null;
        }
    }

    private Object invoke(
            final Object target,
            final String methodName,
            final Class<?> parameterType,
            final Object argument
    ) {
        try {
            final Method method = target.getClass().getMethod(methodName, parameterType);
            return method.invoke(target, argument);
        } catch (final NoSuchMethodException ignored) {
            return null;
        } catch (final IllegalAccessException | InvocationTargetException exception) {
            warn("PlotSquared-Methode konnte nicht aufgerufen werden: " + target.getClass().getName() + "#" + methodName, exception);
            return null;
        }
    }

    private void warn(final String message, final Exception exception) {
        if (warned) {
            return;
        }
        warned = true;
        plugin.getLogger().log(Level.WARNING, message, exception);
    }
}
