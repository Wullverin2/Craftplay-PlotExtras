package de.craftplay.plotextras.plotsquared;

import org.bukkit.World;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class PlotContext {

    private final World world;
    private final String plotId;
    private final List<String> plotIds;
    private final UUID ownerUuid;
    private final String ownerName;
    private final String mergeType;
    private final List<PlotRegion> regions;
    private final PlotRegion bounds;

    public PlotContext(
            final World world,
            final String plotId,
            final List<String> plotIds,
            final UUID ownerUuid,
            final String ownerName,
            final String mergeType,
            final List<PlotRegion> regions,
            final PlotRegion bounds
    ) {
        this.world = world;
        this.plotId = plotId;
        this.plotIds = plotIds == null ? Collections.emptyList() : Collections.unmodifiableList(plotIds);
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName == null || ownerName.trim().isEmpty() ? "Unbekannt" : ownerName;
        this.mergeType = mergeType == null || mergeType.trim().isEmpty() ? "1x1" : mergeType;
        this.regions = regions == null ? Collections.emptyList() : Collections.unmodifiableList(regions);
        this.bounds = bounds;
    }

    public World getWorld() {
        return world;
    }

    public String getWorldName() {
        return world == null ? "" : world.getName();
    }

    public String getPlotId() {
        return plotId;
    }

    public List<String> getPlotIds() {
        return plotIds;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getMergeType() {
        return mergeType;
    }

    public List<PlotRegion> getRegions() {
        return regions;
    }

    public PlotRegion getBounds() {
        return bounds;
    }

    public boolean isComplete() {
        return world != null && bounds != null;
    }
}
