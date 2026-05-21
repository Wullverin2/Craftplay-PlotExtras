package de.craftplay.plotextras.plotsquared;

import java.util.Collections;
import java.util.List;

public final class OwnedPlot {

    private final String key;
    private final String worldName;
    private final String plotId;
    private final String commandId;
    private final String alias;
    private final String displayName;
    private final String ownerName;
    private final List<String> plotIds;
    private final String mergeType;
    private final int size;
    private final long createdAt;
    private final boolean publicByFlag;

    public OwnedPlot(
            final String key,
            final String worldName,
            final String plotId,
            final String commandId,
            final String alias,
            final String displayName,
            final String ownerName,
            final List<String> plotIds,
            final String mergeType,
            final int size,
            final long createdAt,
            final boolean publicByFlag
    ) {
        this.key = key;
        this.worldName = worldName;
        this.plotId = plotId;
        this.commandId = commandId;
        this.alias = alias;
        this.displayName = displayName;
        this.ownerName = ownerName;
        this.plotIds = plotIds == null ? Collections.emptyList() : Collections.unmodifiableList(plotIds);
        this.mergeType = mergeType;
        this.size = size;
        this.createdAt = createdAt;
        this.publicByFlag = publicByFlag;
    }

    public String getKey() {
        return key;
    }

    public String getWorldName() {
        return worldName;
    }

    public String getPlotId() {
        return plotId;
    }

    public String getCommandId() {
        return commandId;
    }

    public String getAlias() {
        return alias;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public List<String> getPlotIds() {
        return plotIds;
    }

    public String getMergeType() {
        return mergeType;
    }

    public int getSize() {
        return size;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public boolean isPublicByFlag() {
        return publicByFlag;
    }
}
