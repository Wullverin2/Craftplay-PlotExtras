package de.craftplay.plotextras.extras;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import com.sk89q.worldedit.world.block.BlockType;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class WorldEditSelectionAdapter {

    public void setSelection(final Player player, final Location first, final Location second) {
        final com.sk89q.worldedit.world.World world = BukkitAdapter.adapt(first.getWorld());
        final LocalSession session = WorldEdit.getInstance()
                .getSessionManager()
                .get(BukkitAdapter.adapt(player));
        final CuboidRegionSelector selector = new CuboidRegionSelector(world);
        selector.selectPrimary(vector(first), null);
        selector.selectSecondary(vector(second), null);
        session.setRegionSelector(world, selector);
        session.dispatchCUISelection(BukkitAdapter.adapt(player));
    }

    public void setBlocks(final Location first, final Location second, final Material material) throws Exception {
        final com.sk89q.worldedit.world.World world = BukkitAdapter.adapt(first.getWorld());
        final BlockType blockType = BukkitAdapter.asBlockType(material);
        if (blockType == null) {
            throw new IllegalArgumentException("Material kann nicht als WorldEdit-Block genutzt werden: " + material);
        }
        final CuboidRegion region = new CuboidRegion(
                world,
                vector(
                        Math.min(first.getBlockX(), second.getBlockX()),
                        Math.min(first.getBlockY(), second.getBlockY()),
                        Math.min(first.getBlockZ(), second.getBlockZ())
                ),
                vector(
                        Math.max(first.getBlockX(), second.getBlockX()),
                        Math.max(first.getBlockY(), second.getBlockY()),
                        Math.max(first.getBlockZ(), second.getBlockZ())
                )
        );
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(world)) {
            editSession.setBlocks(region, blockType.getDefaultState());
        }
    }

    private BlockVector3 vector(final Location location) {
        return BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private BlockVector3 vector(final int x, final int y, final int z) {
        return BlockVector3.at(x, y, z);
    }
}
