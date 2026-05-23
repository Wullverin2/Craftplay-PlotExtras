package de.craftplay.plotextras.plotsquared;

import org.bukkit.Location;
import org.bukkit.World;

public final class PlotRegion {

    private final String worldName;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    public PlotRegion(
            final String worldName,
            final int minX,
            final int minY,
            final int minZ,
            final int maxX,
            final int maxY,
            final int maxZ
    ) {
        this.worldName = worldName;
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
    }

    public String getWorldName() {
        return worldName;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public boolean contains(final Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        return contains(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public boolean contains(final String worldName, final int x, final int y, final int z) {
        return this.worldName.equalsIgnoreCase(worldName)
                && x >= minX
                && x <= maxX
                && y >= minY
                && y <= maxY
                && z >= minZ
                && z <= maxZ;
    }

    public PlotRegion withWorldBounds(final World world, final int fallbackMinY) {
        final int boundedMaxY = world == null ? maxY : Math.max(fallbackMinY, world.getMaxHeight() - 1);
        return new PlotRegion(worldName, minX, fallbackMinY, minZ, maxX, boundedMaxY, maxZ);
    }

    public static PlotRegion encompassing(final String worldName, final Iterable<PlotRegion> regions) {
        boolean found = false;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (final PlotRegion region : regions) {
            if (region == null) {
                continue;
            }
            found = true;
            minX = Math.min(minX, region.getMinX());
            minY = Math.min(minY, region.getMinY());
            minZ = Math.min(minZ, region.getMinZ());
            maxX = Math.max(maxX, region.getMaxX());
            maxY = Math.max(maxY, region.getMaxY());
            maxZ = Math.max(maxZ, region.getMaxZ());
        }
        if (!found) {
            return null;
        }
        return new PlotRegion(worldName, minX, minY, minZ, maxX, maxY, maxZ);
    }
}
