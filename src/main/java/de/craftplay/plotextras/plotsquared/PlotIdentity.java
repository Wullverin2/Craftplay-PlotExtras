package de.craftplay.plotextras.plotsquared;

import java.util.UUID;

public final class PlotIdentity {

    private final String worldName;
    private final String plotId;
    private final UUID ownerUuid;

    public PlotIdentity(final String worldName, final String plotId, final UUID ownerUuid) {
        this.worldName = worldName == null ? "" : worldName;
        this.plotId = plotId == null ? "" : plotId;
        this.ownerUuid = ownerUuid;
    }

    public String getWorldName() {
        return worldName;
    }

    public String getPlotId() {
        return plotId;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getPlotKey() {
        return worldName + ";" + plotId;
    }

    public boolean isComplete() {
        return !worldName.trim().isEmpty() && !plotId.trim().isEmpty();
    }
}
