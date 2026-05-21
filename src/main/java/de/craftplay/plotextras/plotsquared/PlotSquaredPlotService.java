package de.craftplay.plotextras.plotsquared;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class PlotSquaredPlotService {

    private final JavaPlugin plugin;
    private boolean warned;

    public PlotSquaredPlotService(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public List<OwnedPlot> ownedPlots(final Player player) {
        final Map<String, OwnedPlot> plots = new LinkedHashMap<>();
        addPlots(plots, plotsFromPlotPlayer(player), player);
        addPlots(plots, plotsFromPlotApi(player), player);
        final List<OwnedPlot> result = new ArrayList<>(plots.values());
        result.sort(Comparator.comparing(OwnedPlot::getWorldName).thenComparing(OwnedPlot::getPlotId));
        return result;
    }

    public Optional<OwnedPlot> ownedPlot(final Player player, final String key) {
        if (key == null || key.trim().isEmpty()) {
            return Optional.empty();
        }
        for (final OwnedPlot plot : ownedPlots(player)) {
            if (key.equals(plot.getKey())) {
                return Optional.of(plot);
            }
        }
        return Optional.empty();
    }

    public void teleportTo(final Player player, final OwnedPlot plot) {
        player.performCommand("plot home " + plot.getCommandId());
    }

    private Collection<?> plotsFromPlotPlayer(final Player player) {
        final Object plotPlayer = plotPlayer(player);
        if (plotPlayer == null) {
            return new ArrayList<>();
        }
        final Object plots = invokeNoArgs(plotPlayer, "getPlots");
        if (plots instanceof Collection) {
            return (Collection<?>) plots;
        }
        if (plots instanceof Iterable) {
            final List<Object> result = new ArrayList<>();
            for (final Object plot : (Iterable<?>) plots) {
                result.add(plot);
            }
            return result;
        }
        return new ArrayList<>();
    }

    private Collection<?> plotsFromPlotApi(final Player player) {
        final Object plotApi = plotApi();
        if (plotApi == null) {
            return new ArrayList<>();
        }

        final Object byUuid = invoke(plotApi, "getPlayerPlots", UUID.class, player.getUniqueId());
        if (byUuid instanceof Collection) {
            return (Collection<?>) byUuid;
        }
        final Object plotPlayer = plotPlayer(player);
        if (plotPlayer != null) {
            final Object byPlotPlayer = invokeByAssignableName(plotApi, "getPlayerPlots", plotPlayer);
            if (byPlotPlayer instanceof Collection) {
                return (Collection<?>) byPlotPlayer;
            }
        }
        return new ArrayList<>();
    }

    private void addPlots(final Map<String, OwnedPlot> target, final Collection<?> source, final Player viewer) {
        for (final Object plot : source) {
            final OwnedPlot converted = convert(plot, viewer);
            if (converted != null) {
                target.put(converted.getKey(), converted);
            }
        }
    }

    private OwnedPlot convert(final Object plot, final Player viewer) {
        if (plot == null) {
            return null;
        }
        final String worldName = string(invokeNoArgs(plot, "getWorldName"), viewer.getWorld().getName());
        final String plotId = string(invokeNoArgs(plot, "getId"), "unbekannt");
        final String commandId = worldName + ";" + plotId;
        final String alias = alias(plot);
        final String displayName = alias.isEmpty() ? plotId : alias;
        final UUID ownerUuid = ownerUuid(plot);
        final String ownerName = ownerName(ownerUuid);
        final List<String> plotIds = connectedPlotIds(plot);
        final String mergeType = mergeType(plot, plotIds);
        final int size = Math.max(1, plotIds.size());
        final long createdAt = longValue(invokeNoArgs(plot, "getTimestamp"), 0L);
        final boolean publicByFlag = booleanFlag(plot, "untrusted-visit");
        final String key = worldName + ";" + plotId;
        return new OwnedPlot(
                key,
                worldName,
                plotId,
                commandId,
                alias,
                displayName,
                ownerName,
                plotIds,
                mergeType,
                size,
                createdAt,
                publicByFlag
        );
    }

    private String alias(final Object plot) {
        final Object alias = invokeNoArgs(plot, "getAlias");
        if (alias == null) {
            return "";
        }
        final String text = alias.toString().trim();
        return text.equalsIgnoreCase("none") ? "" : text;
    }

    private boolean booleanFlag(final Object plot, final String flagName) {
        final Object flag = flag(flagName);
        if (flag == null) {
            return false;
        }
        final Object value = getFlagValue(plot, flag);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return value != null && ("true".equalsIgnoreCase(value.toString())
                || "enabled".equalsIgnoreCase(value.toString())
                || "allow".equalsIgnoreCase(value.toString())
                || "allowed".equalsIgnoreCase(value.toString()));
    }

    private List<String> connectedPlotIds(final Object plot) {
        final List<String> ids = new ArrayList<>();
        final Object connected = invokeNoArgs(plot, "getConnectedPlots");
        if (connected instanceof Iterable) {
            for (final Object connectedPlot : (Iterable<?>) connected) {
                ids.add(string(invokeNoArgs(connectedPlot, "getId"), "unbekannt"));
            }
        }
        if (ids.isEmpty()) {
            ids.add(string(invokeNoArgs(plot, "getId"), "unbekannt"));
        }
        ids.sort(Comparator.naturalOrder());
        return ids;
    }

    private String mergeType(final Object plot, final List<String> plotIds) {
        final Object connected = invokeNoArgs(plot, "getConnectedPlots");
        final Set<String> seen = new HashSet<>();
        final List<int[]> coordinates = new ArrayList<>();
        if (connected instanceof Iterable) {
            for (final Object connectedPlot : (Iterable<?>) connected) {
                final int[] coordinate = plotIdCoordinates(invokeNoArgs(connectedPlot, "getId"));
                if (coordinate != null && seen.add(coordinate[0] + ";" + coordinate[1])) {
                    coordinates.add(coordinate);
                }
            }
        }
        if (coordinates.isEmpty()) {
            final int[] coordinate = plotIdCoordinates(invokeNoArgs(plot, "getId"));
            if (coordinate != null) {
                coordinates.add(coordinate);
            }
        }
        if (coordinates.size() <= 1) {
            return "1x1";
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (final int[] coordinate : coordinates) {
            minX = Math.min(minX, coordinate[0]);
            minY = Math.min(minY, coordinate[1]);
            maxX = Math.max(maxX, coordinate[0]);
            maxY = Math.max(maxY, coordinate[1]);
        }
        if (minX != Integer.MAX_VALUE && minY != Integer.MAX_VALUE) {
            return (maxX - minX + 1) + "x" + (maxY - minY + 1);
        }
        return Math.max(1, plotIds.size()) + " Plots";
    }

    private int[] plotIdCoordinates(final Object id) {
        if (id == null) {
            return null;
        }
        final Integer x = coordinate(id, "X");
        final Integer y = coordinate(id, "Y");
        if (x != null && y != null) {
            return new int[]{x, y};
        }
        final String normalized = id.toString().replace(';', ',').replace(':', ',');
        final String[] parts = normalized.split(",");
        if (parts.length < 2) {
            return null;
        }
        try {
            return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
        } catch (final NumberFormatException ignored) {
            return null;
        }
    }

    private Integer coordinate(final Object location, final String axis) {
        final Object blockValue = invokeNoArgs(location, "getBlock" + axis);
        if (blockValue instanceof Number) {
            return ((Number) blockValue).intValue();
        }
        final Object value = invokeNoArgs(location, "get" + axis);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    private UUID ownerUuid(final Object plot) {
        final Object owner = invokeNoArgs(plot, "getOwner");
        if (owner instanceof UUID) {
            return (UUID) owner;
        }
        final Object ownerAbs = invokeNoArgs(plot, "getOwnerAbs");
        if (ownerAbs instanceof UUID) {
            return (UUID) ownerAbs;
        }
        final Object owners = invokeNoArgs(plot, "getOwners");
        if (owners instanceof Iterable) {
            for (final Object candidate : (Iterable<?>) owners) {
                if (candidate instanceof UUID) {
                    return (UUID) candidate;
                }
            }
        }
        return null;
    }

    private String ownerName(final UUID ownerUuid) {
        if (ownerUuid == null) {
            return "Unbekannt";
        }
        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(ownerUuid);
        final String name = offlinePlayer.getName();
        return name == null || name.trim().isEmpty() ? ownerUuid.toString() : name;
    }

    private Object plotApi() {
        try {
            final Class<?> plotApiClass = Class.forName("com.plotsquared.core.PlotAPI");
            final Constructor<?> constructor = plotApiClass.getConstructor();
            return constructor.newInstance();
        } catch (final ClassNotFoundException | NoSuchMethodException ignored) {
            return null;
        } catch (final ReflectiveOperationException exception) {
            warn("PlotSquared-PlotAPI konnte nicht erstellt werden.", exception);
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
            try {
                final Method method = plot.getClass().getMethod("getFlag", flagClass);
                final Object value = method.invoke(plot, flag);
                if (value instanceof Optional) {
                    final Optional<?> optional = (Optional<?>) value;
                    return optional.isPresent() ? optional.get() : Boolean.FALSE;
                }
                return value;
            } catch (final ReflectiveOperationException exception) {
                return false;
            }
        } catch (final IllegalAccessException | InvocationTargetException exception) {
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

    private Object invokeNoArgs(final Object target, final String methodName) {
        if (target == null) {
            return null;
        }
        try {
            final Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (final ReflectiveOperationException ignored) {
            return null;
        }
    }

    private Object invoke(final Object target, final String methodName, final Class<?> parameterType, final Object argument) {
        try {
            final Method method = target.getClass().getMethod(methodName, parameterType);
            return method.invoke(target, argument);
        } catch (final ReflectiveOperationException ignored) {
            return null;
        }
    }

    private Object invokeByAssignableName(final Object target, final String methodName, final Object argument) {
        for (final Method method : target.getClass().getMethods()) {
            if (!methodName.equals(method.getName()) || method.getParameterTypes().length != 1) {
                continue;
            }
            if (!method.getParameterTypes()[0].isAssignableFrom(argument.getClass())) {
                continue;
            }
            try {
                return method.invoke(target, argument);
            } catch (final ReflectiveOperationException ignored) {
                return null;
            }
        }
        return null;
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

    private String string(final Object value, final String fallback) {
        if (value == null) {
            return fallback;
        }
        final String text = value.toString().trim();
        return text.isEmpty() ? fallback : text;
    }

    private long longValue(final Object value, final long fallback) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (final NumberFormatException exception) {
            return fallback;
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
