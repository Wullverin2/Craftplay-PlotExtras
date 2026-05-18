package de.craftplay.plotextras.backup;

import de.craftplay.plotextras.plotsquared.PlotRegion;
import org.bukkit.World;

import java.io.File;

public interface SchematicAdapter {

    boolean isAvailable();

    void save(World world, PlotRegion region, File targetFile, boolean includeEntities, boolean includeBiomes) throws Exception;

    void restore(World world, PlotRegion targetRegion, File sourceFile, boolean pasteEntities, boolean pasteBiomes, boolean ignoreAir) throws Exception;
}
