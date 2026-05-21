package de.craftplay.plotextras.reports;

import java.util.UUID;

public final class PlotReport {

    private final String id;
    private final UUID reporterUuid;
    private final String reporterName;
    private final long createdAt;
    private final String world;
    private final String plot;
    private final String plots;
    private final String merge;
    private final String owner;
    private final String category;
    private final String message;
    private final String priority;
    private final String status;
    private final String note;
    private final String handledBy;
    private final long handledAt;

    public PlotReport(
            final String id,
            final UUID reporterUuid,
            final String reporterName,
            final long createdAt,
            final String world,
            final String plot,
            final String plots,
            final String merge,
            final String owner,
            final String category,
            final String message,
            final String priority,
            final String status,
            final String note,
            final String handledBy,
            final long handledAt
    ) {
        this.id = id;
        this.reporterUuid = reporterUuid;
        this.reporterName = reporterName == null ? "" : reporterName;
        this.createdAt = createdAt;
        this.world = world == null ? "" : world;
        this.plot = plot == null ? "" : plot;
        this.plots = plots == null ? "" : plots;
        this.merge = merge == null ? "" : merge;
        this.owner = owner == null ? "" : owner;
        this.category = category == null ? "" : category;
        this.message = message == null ? "" : message;
        this.priority = priority == null || priority.trim().isEmpty() ? "normal" : priority.toLowerCase();
        this.status = status == null || status.trim().isEmpty() ? "open" : status.toLowerCase();
        this.note = note == null ? "" : note;
        this.handledBy = handledBy == null ? "" : handledBy;
        this.handledAt = handledAt;
    }

    public String getId() {
        return id;
    }

    public UUID getReporterUuid() {
        return reporterUuid;
    }

    public String getReporterName() {
        return reporterName;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public String getWorld() {
        return world;
    }

    public String getPlot() {
        return plot;
    }

    public String getPlots() {
        return plots;
    }

    public String getMerge() {
        return merge;
    }

    public String getOwner() {
        return owner;
    }

    public String getCategory() {
        return category;
    }

    public String getMessage() {
        return message;
    }

    public String getPriority() {
        return priority;
    }

    public String getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }

    public String getHandledBy() {
        return handledBy;
    }

    public long getHandledAt() {
        return handledAt;
    }

    public PlotReport withStatus(final String newStatus, final String handler, final long timestamp) {
        return new PlotReport(id, reporterUuid, reporterName, createdAt, world, plot, plots, merge, owner,
                category, message, priority, newStatus, note, handler, timestamp);
    }

    public PlotReport withPriority(final String newPriority) {
        return new PlotReport(id, reporterUuid, reporterName, createdAt, world, plot, plots, merge, owner,
                category, message, newPriority, status, note, handledBy, handledAt);
    }

    public PlotReport withNote(final String newNote, final String handler, final long timestamp) {
        return new PlotReport(id, reporterUuid, reporterName, createdAt, world, plot, plots, merge, owner,
                category, message, priority, status, newNote, handler, timestamp);
    }
}
