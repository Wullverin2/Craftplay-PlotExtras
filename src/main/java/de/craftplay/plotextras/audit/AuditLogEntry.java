package de.craftplay.plotextras.audit;

import java.time.Instant;

public record AuditLogEntry(
        String id,
        Instant createdAt,
        String actor,
        String action,
        String details,
        String world,
        String plotId,
        String plotKey
) {
}
