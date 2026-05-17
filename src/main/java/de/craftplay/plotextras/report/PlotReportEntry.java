package de.craftplay.plotextras.report;

import java.time.Instant;
import java.util.UUID;

public record PlotReportEntry(
        String id,
        Instant createdAt,
        UUID reporterUuid,
        String reporterName,
        String world,
        String plotId,
        String plotKey,
        String ownerName,
        String reason,
        String status,
        String handledBy,
        Instant handledAt,
        String note
) {
}
