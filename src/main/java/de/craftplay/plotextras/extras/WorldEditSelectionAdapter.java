package de.craftplay.plotextras.extras;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
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

    private BlockVector3 vector(final Location location) {
        return BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
