package de.craftplay.plotextras.backup;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlotBackupEntry(
        String id,
        UUID ownerUuid,
        String ownerName,
        Instant createdAt,
        String reason,
        String sourceWorld,
        String sourcePlot,
        String mergeSize,
        int plotCount,
        List<String> sourcePlots,
        File schematicFile
) {
}
