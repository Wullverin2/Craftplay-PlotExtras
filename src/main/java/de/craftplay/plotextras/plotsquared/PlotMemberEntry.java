package de.craftplay.plotextras.plotsquared;

import java.util.UUID;

public final class PlotMemberEntry {

    private final UUID uuid;
    private final String name;
    private final PlotMemberType type;

    public PlotMemberEntry(final UUID uuid, final String name, final PlotMemberType type) {
        this.uuid = uuid;
        this.name = name == null || name.trim().isEmpty() ? uuid.toString() : name.trim();
        this.type = type;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public PlotMemberType getType() {
        return type;
    }
}
