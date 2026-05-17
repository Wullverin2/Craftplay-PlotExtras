package de.craftplay.plotextras.competition;

import java.time.Instant;
import java.util.UUID;

public record CompetitionEntry(
        String id,
        String competition,
        Instant createdAt,
        UUID ownerUuid,
        String ownerName,
        String world,
        String plotId,
        String plotKey,
        String note,
        int score,
        String scoredBy,
        String scoreNote
) {
}
