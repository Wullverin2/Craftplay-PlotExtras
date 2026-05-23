package de.craftplay.plotextras.myplots;

import java.util.UUID;

public final class PlotComment {

    private final String id;
    private final UUID authorUuid;
    private final String authorName;
    private final String message;
    private final long createdAt;

    public PlotComment(
            final String id,
            final UUID authorUuid,
            final String authorName,
            final String message,
            final long createdAt
    ) {
        this.id = id == null ? "" : id;
        this.authorUuid = authorUuid;
        this.authorName = authorName == null || authorName.trim().isEmpty() ? "Unbekannt" : authorName.trim();
        this.message = message == null ? "" : message.trim();
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public UUID getAuthorUuid() {
        return authorUuid;
    }

    public String getAuthorName() {
        return authorName;
    }

    public String getMessage() {
        return message;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
