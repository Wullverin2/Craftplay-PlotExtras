package de.craftplay.plotextras.backup;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import de.craftplay.plotextras.plotsquared.PlotRegion;
import org.bukkit.World;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public final class WorldEditSchematicAdapter implements SchematicAdapter {

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("com.sk89q.worldedit.WorldEdit");
            Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            return true;
        } catch (final ClassNotFoundException exception) {
            return false;
        }
    }

    @Override
    public void save(
            final World world,
            final PlotRegion region,
            final File targetFile,
            final boolean includeEntities,
            final boolean includeBiomes
    ) throws Exception {
        final com.sk89q.worldedit.world.World adaptedWorld = BukkitAdapter.adapt(world);
        final CuboidRegion cuboidRegion = new CuboidRegion(
                adaptedWorld,
                vector(region.getMinX(), region.getMinY(), region.getMinZ()),
                vector(region.getMaxX(), region.getMaxY(), region.getMaxZ())
        );
        final BlockArrayClipboard clipboard = new BlockArrayClipboard(cuboidRegion);
        clipboard.setOrigin(vector(region.getMinX(), region.getMinY(), region.getMinZ()));

        try (EditSession editSession = WorldEdit.getInstance().newEditSession(adaptedWorld)) {
            final ForwardExtentCopy copy = new ForwardExtentCopy(
                    editSession,
                    cuboidRegion,
                    clipboard,
                    cuboidRegion.getMinimumPoint()
            );
            copy.setCopyingEntities(includeEntities);
            copy.setCopyingBiomes(includeBiomes);
            Operations.complete(copy);
        }

        final File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Backup-Ordner konnte nicht erstellt werden: " + parent.getPath());
        }
        try (ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_SCHEMATIC.getWriter(new FileOutputStream(targetFile))) {
            writer.write(clipboard);
        }
    }

    @Override
    public void restore(
            final World world,
            final PlotRegion targetRegion,
            final File sourceFile,
            final boolean pasteEntities,
            final boolean pasteBiomes,
            final boolean ignoreAir
    ) throws Exception {
        final ClipboardFormat format = ClipboardFormats.findByFile(sourceFile);
        if (format == null) {
            throw new IllegalStateException("Schematic-Format konnte nicht erkannt werden: " + sourceFile.getName());
        }
        final Clipboard clipboard;
        try (ClipboardReader reader = format.getReader(new FileInputStream(sourceFile))) {
            clipboard = reader.read();
        }

        final com.sk89q.worldedit.world.World adaptedWorld = BukkitAdapter.adapt(world);
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(adaptedWorld)) {
            final Operation operation = new ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(vector(targetRegion.getMinX(), targetRegion.getMinY(), targetRegion.getMinZ()))
                    .copyEntities(pasteEntities)
                    .copyBiomes(pasteBiomes)
                    .ignoreAirBlocks(ignoreAir)
                    .build();
            Operations.complete(operation);
        }
    }

    private BlockVector3 vector(final int x, final int y, final int z) {
        return BlockVector3.at(x, y, z);
    }
}
