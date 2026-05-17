package de.craftplay.plotextras.performance;

import java.util.List;
import java.util.Map;

public record PlotPerformanceSnapshot(
        String plotKey,
        int totalEntities,
        Map<String, Integer> entityCounts,
        List<String> warnings
) {
}
