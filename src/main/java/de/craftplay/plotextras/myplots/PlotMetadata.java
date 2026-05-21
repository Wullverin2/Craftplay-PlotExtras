package de.craftplay.plotextras.myplots;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PlotMetadata {

    private final String category;
    private final List<String> tags;
    private final String visibility;
    private final String note;
    private final double rating;
    private final int visits;
    private final long lastVisit;

    public PlotMetadata(
            final String category,
            final List<String> tags,
            final String visibility,
            final String note,
            final double rating,
            final int visits,
            final long lastVisit
    ) {
        this.category = category == null ? "" : category;
        this.tags = tags == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(tags));
        this.visibility = visibility == null || visibility.trim().isEmpty() ? "auto" : visibility.trim().toLowerCase();
        this.note = note == null ? "" : note;
        this.rating = rating;
        this.visits = visits;
        this.lastVisit = lastVisit;
    }

    public String getCategory() {
        return category;
    }

    public List<String> getTags() {
        return tags;
    }

    public String getVisibility() {
        return visibility;
    }

    public String getNote() {
        return note;
    }

    public double getRating() {
        return rating;
    }

    public int getVisits() {
        return visits;
    }

    public long getLastVisit() {
        return lastVisit;
    }
}
