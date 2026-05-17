package de.craftplay.plotextras.warp;

import org.bukkit.Location;

public record PlotWarpEntry(
        String id,
        String displayName,
        String plotKey,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {
    public Location toLocation(final org.bukkit.World bukkitWorld) {
        return new Location(bukkitWorld, x, y, z, yaw, pitch);
    }
}
