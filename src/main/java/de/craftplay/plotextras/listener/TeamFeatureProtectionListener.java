package de.craftplay.plotextras.listener;

import de.craftplay.plotextras.team.TeamFeatureService;
import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.InventoryHolder;

public final class TeamFeatureProtectionListener implements Listener {

    private final TeamFeatureService teamFeatureService;

    public TeamFeatureProtectionListener(final TeamFeatureService teamFeatureService) {
        this.teamFeatureService = teamFeatureService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(final PlayerMoveEvent event) {
        if (!changedBlock(event.getFrom(), event.getTo())) {
            return;
        }
        if (teamFeatureService.isLockedLocation(event.getTo())
                && !teamFeatureService.isLockedLocation(event.getFrom())
                && !teamFeatureService.canBypassLock(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        if (protectedFor(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(final BlockPlaceEvent event) {
        if (protectedFor(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(final PlayerBucketEmptyEvent event) {
        if (protectedFor(event.getPlayer(), event.getBlockClicked().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(final PlayerBucketFillEvent event) {
        if (protectedFor(event.getPlayer(), event.getBlockClicked().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(final PlayerInteractEvent event) {
        if (event.getClickedBlock() != null && protectedFor(event.getPlayer(), event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(final PlayerInteractEntityEvent event) {
        if (protectedFor(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(final EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && protectedFor((Player) event.getDamager(), event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(final EntityDamageEvent event) {
        if (teamFeatureService.isFrozenLocation(event.getEntity().getLocation())
                || teamFeatureService.isLockedLocation(event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(final InventoryOpenEvent event) {
        final InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof BlockState) || !(event.getPlayer() instanceof Player)) {
            return;
        }
        if (protectedFor((Player) event.getPlayer(), ((BlockState) holder).getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(final EntityChangeBlockEvent event) {
        if (teamFeatureService.isFrozenLocation(event.getBlock().getLocation())
                || teamFeatureService.isLockedLocation(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(final CreatureSpawnEvent event) {
        if (teamFeatureService.isFrozenLocation(event.getLocation()) || teamFeatureService.isLockedLocation(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(final ItemSpawnEvent event) {
        if (teamFeatureService.isFrozenLocation(event.getLocation()) || teamFeatureService.isLockedLocation(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(final BlockBurnEvent event) {
        if (teamFeatureService.isFrozenLocation(event.getBlock().getLocation())
                || teamFeatureService.isLockedLocation(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFluid(final BlockFromToEvent event) {
        if (teamFeatureService.isFrozenLocation(event.getBlock().getLocation())
                || teamFeatureService.isFrozenLocation(event.getToBlock().getLocation())
                || teamFeatureService.isLockedLocation(event.getBlock().getLocation())
                || teamFeatureService.isLockedLocation(event.getToBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(final BlockPistonExtendEvent event) {
        if (teamFeatureService.isFrozenLocation(event.getBlock().getLocation())
                || teamFeatureService.isLockedLocation(event.getBlock().getLocation())
                || event.getBlocks().stream().anyMatch(block -> teamFeatureService.isFrozenLocation(block.getLocation())
                || teamFeatureService.isLockedLocation(block.getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(final BlockPistonRetractEvent event) {
        if (teamFeatureService.isFrozenLocation(event.getBlock().getLocation())
                || teamFeatureService.isLockedLocation(event.getBlock().getLocation())
                || event.getBlocks().stream().anyMatch(block -> teamFeatureService.isFrozenLocation(block.getLocation())
                || teamFeatureService.isLockedLocation(block.getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(final BlockExplodeEvent event) {
        event.blockList().removeIf(block -> teamFeatureService.isFrozenLocation(block.getLocation())
                || teamFeatureService.isLockedLocation(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(final EntityExplodeEvent event) {
        event.blockList().removeIf(block -> teamFeatureService.isFrozenLocation(block.getLocation())
                || teamFeatureService.isLockedLocation(block.getLocation()));
    }

    private boolean protectedFor(final Player player, final Entity entity) {
        return entity != null && protectedFor(player, entity.getLocation());
    }

    private boolean protectedFor(final Player player, final Location location) {
        if (teamFeatureService.isFrozenLocation(location) && !teamFeatureService.canBypassFreeze(player)) {
            return true;
        }
        return teamFeatureService.isLockedLocation(location) && !teamFeatureService.canBypassLock(player);
    }

    private boolean changedBlock(final Location from, final Location to) {
        if (from == null || to == null) {
            return true;
        }
        if (from.getWorld() == null || to.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return true;
        }
        return from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
    }
}
