package de.craftplay.plotextras.plotsquared;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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

    public Optional<PlotContext> currentPlotContext(final Player player) {
        final Object plot = currentPlot(player);
        if (plot == null) {
            return Optional.empty();
        }

        final World world = world(player, plot);
        final String worldName = world == null ? player.getWorld().getName() : world.getName();
        final List<PlotRegion> regions = plotRegions(plot, worldName);
        final PlotRegion bounds = PlotRegion.encompassing(worldName, regions);
        if (bounds == null) {
            return Optional.empty();
        }

        final UUID ownerUuid = ownerUuid(plot);
        final String ownerName = ownerName(ownerUuid);
        final String plotId = stringify(invokeNoArgs(plot, "getId"), "unbekannt");
        final List<String> plotIds = connectedPlotIds(plot);
        final String mergeType = mergeType(plot, plotIds);
        return Optional.of(new PlotContext(
                world,
                plotId,
                plotIds,
                ownerUuid,
                ownerName,
                mergeType,
                regions,
                bounds
        ));
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

    private World world(final Player player, final Object plot) {
        final Object worldName = invokeNoArgs(plot, "getWorldName");
        if (worldName != null) {
            final World world = Bukkit.getWorld(worldName.toString());
            if (world != null) {
                return world;
            }
        }
        return player.getWorld();
    }

    private List<PlotRegion> plotRegions(final Object plot, final String worldName) {
        final List<PlotRegion> regions = new ArrayList<>();
        final Object plotRegions = invokeNoArgs(plot, "getRegions");
        if (plotRegions instanceof Iterable) {
            for (final Object region : (Iterable<?>) plotRegions) {
                final PlotRegion converted = convertRegion(worldName, region);
                if (converted != null) {
                    regions.add(converted);
                }
            }
        }

        if (!regions.isEmpty()) {
            return regions;
        }

        final PlotRegion largest = convertRegion(worldName, invokeNoArgs(plot, "getLargestRegion"));
        if (largest != null) {
            regions.add(largest);
            return regions;
        }

        final PlotRegion extended = convertLocations(
                worldName,
                invokeNoArgs(plot, "getExtendedBottomAbs"),
                invokeNoArgs(plot, "getExtendedTopAbs")
        );
        if (extended != null) {
            regions.add(extended);
            return regions;
        }

        final Object corners = invokeNoArgs(plot, "getCorners");
        if (corners != null && corners.getClass().isArray() && java.lang.reflect.Array.getLength(corners) >= 2) {
            final Object first = java.lang.reflect.Array.get(corners, 0);
            final Object second = java.lang.reflect.Array.get(corners, 1);
            final PlotRegion fromCorners = convertLocations(worldName, first, second);
            if (fromCorners != null) {
                regions.add(fromCorners);
            }
        }
        return regions;
    }

    private PlotRegion convertRegion(final String worldName, final Object region) {
        if (region == null) {
            return null;
        }
        final Object minimum = invokeNoArgs(region, "getMinimumPoint");
        final Object maximum = invokeNoArgs(region, "getMaximumPoint");
        return convertLocations(worldName, minimum, maximum);
    }

    private PlotRegion convertLocations(final String worldName, final Object first, final Object second) {
        if (first == null || second == null) {
            return null;
        }
        final Integer minX = coordinate(first, "X");
        final Integer minY = coordinate(first, "Y");
        final Integer minZ = coordinate(first, "Z");
        final Integer maxX = coordinate(second, "X");
        final Integer maxY = coordinate(second, "Y");
        final Integer maxZ = coordinate(second, "Z");
        if (minX == null || minY == null || minZ == null || maxX == null || maxY == null || maxZ == null) {
            return null;
        }
        return new PlotRegion(worldName, minX, minY, minZ, maxX, maxY, maxZ);
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

    private List<String> connectedPlotIds(final Object plot) {
        final List<String> ids = new ArrayList<>();
        final Object connected = invokeNoArgs(plot, "getConnectedPlots");
        if (connected instanceof Iterable) {
            for (final Object connectedPlot : (Iterable<?>) connected) {
                final Object id = invokeNoArgs(connectedPlot, "getId");
                ids.add(stringify(id, "unbekannt"));
            }
        }
        if (ids.isEmpty()) {
            ids.add(stringify(invokeNoArgs(plot, "getId"), "unbekannt"));
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
                final Object id = invokeNoArgs(connectedPlot, "getId");
                final int[] coordinate = plotIdCoordinates(id);
                if (coordinate == null) {
                    continue;
                }
                final String key = coordinate[0] + ";" + coordinate[1];
                if (seen.add(key)) {
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
        final int size = plotIds == null || plotIds.isEmpty() ? coordinates.size() : plotIds.size();
        return size + " Plots";
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

    private String stringify(final Object value, final String fallback) {
        return value == null ? fallback : value.toString();
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
