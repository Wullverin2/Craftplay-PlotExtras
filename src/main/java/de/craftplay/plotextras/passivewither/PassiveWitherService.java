package de.craftplay.plotextras.passivewither;

import com.plotsquared.core.plot.Plot;
import com.plotsquared.core.plot.flag.GlobalFlagContainer;
import de.craftplay.plotextras.CraftplayPlotExtrasPlugin;
import de.craftplay.plotextras.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

public final class PassiveWitherService implements Listener {

    private static final byte TRUE = 1;
    private static final long EXPLOSION_PROTECTION_MILLIS = 1500L;
    private static final long BLOCK_DESTRUCTION_BATCH_MILLIS = 400L;
    private static final long SOUND_SUPPRESSION_BURST_MILLIS = 3000L;
    private static final double BLOCK_BREAK_SOUND_RADIUS = 3.0D;
    private static final double MIN_EXPLOSION_PROTECTION_RADIUS = 8.0D;
    private static final double PASSIVE_WITHER_SOUND_RADIUS = 96.0D;

    private final CraftplayPlotExtrasPlugin plugin;
    private final NamespacedKey passiveWitherKey;
    private final NamespacedKey passiveEggKey;
    private final NamespacedKey passiveWitherSourceKey;
    private final NamespacedKey passiveWitherOwnerKey;
    private final List<ProtectedExplosion> protectedExplosions = new ArrayList<>();
    private final List<SoundSuppressionArea> activeSoundSuppressions = new CopyOnWriteArrayList<>();
    private final List<SoundSuppressionArea> activeBlockBreakSoundSuppressions = new CopyOnWriteArrayList<>();
    private final List<ScheduledExplosionMarker> scheduledExplosions = new CopyOnWriteArrayList<>();
    private final Map<UUID, Long> effectProtectedPlayers = new HashMap<>();
    private final Map<UUID, UUID> passiveWitherOwners = new HashMap<>();
    private final Map<UUID, Location> passiveWitherAnchors = new HashMap<>();
    private final Map<UUID, Long> passiveWitherNextExplosions = new HashMap<>();
    private final Set<Integer> passiveWitherEntityIds = ConcurrentHashMap.newKeySet();
    private final Set<UUID> soundDisabledPlayers = ConcurrentHashMap.newKeySet();
    private final List<Sound> mutedFallbackSounds = new ArrayList<>();

    private YamlConfiguration data;
    private String dataFile;
    private boolean enabled;
    private boolean economyEnabled;
    private boolean registeredPlotSquaredFlag;
    private String buyPermission;
    private String commandPermission;
    private String soundPermission;
    private String freePermission;
    private String chestPermission;
    private double price;
    private Material eggMaterial;
    private float explosionPower;
    private boolean explosionSetFire;
    private int nearestInventoryRadius;
    private boolean dropOverflow;
    private Location configuredDropInventoryLocation;
    private long explosionCooldownMillis;
    private long nextBlockDestructionAllowedAtMillis;
    private long blockDestructionBatchUntilMillis;
    private UUID activeBlockDestructionSourceId;
    private long globalSoundSuppressionUntilMillis;
    private int soundSuppressTicks;
    private int passiveWitherMaintenanceTicks;
    private BukkitTask soundSuppressTask;
    private BukkitTask passiveWitherMaintenanceTask;
    private PassiveWitherSoundPacketHook soundPacketHook;
    private Object economy;
    private Method economyHasMethod;
    private Method economyWithdrawMethod;
    private Method economyFormatMethod;

    public PassiveWitherService(final CraftplayPlotExtrasPlugin plugin) {
        this.plugin = plugin;
        this.passiveWitherKey = new NamespacedKey(plugin, "passive_wither");
        this.passiveEggKey = new NamespacedKey(plugin, "passive_wither_egg");
        this.passiveWitherSourceKey = new NamespacedKey(plugin, "passive_wither_source");
        this.passiveWitherOwnerKey = new NamespacedKey(plugin, "passive_wither_owner");
        for (final Sound sound : Sound.values()) {
            if (isPassiveWitherFallbackMutedSound(sound)) {
                mutedFallbackSounds.add(sound);
            }
        }
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("passive-wither.enabled", true);
        dataFile = plugin.getConfig().getString("passive-wither.data-file", "passivewither.yml");
        data = plugin.getStorageService().load("passivewither", dataFile);
        if (data.getInt("file-version", 0) < 2) {
            data.set("file-version", 2);
            saveData();
        }
        buyPermission = plugin.getConfig().getString("passive-wither.buy-permission", "craftplayplotextras.passivewither.buy");
        commandPermission = plugin.getConfig().getString("passive-wither.command-permission", "craftplayplotextras.passivewither.command");
        soundPermission = plugin.getConfig().getString("passive-wither.sound-permission", "craftplayplotextras.passivewither.sound");
        freePermission = plugin.getConfig().getString("passive-wither.free-permission", "craftplayplotextras.passivewither.free");
        chestPermission = plugin.getConfig().getString("passive-wither.chest-permission", "craftplayplotextras.passivewither.chest");
        price = Math.max(0.0D, plugin.getConfig().getDouble("passive-wither.price", 25000.0D));
        eggMaterial = material(plugin.getConfig().getString("passive-wither.egg.material", "WITHER_SKELETON_SPAWN_EGG"), Material.WITHER_SKELETON_SPAWN_EGG);
        economyEnabled = plugin.getConfig().getBoolean("passive-wither.economy.enabled", true);
        explosionCooldownMillis = Math.max(0L, Math.round(plugin.getConfig()
                .getDouble("passive-wither.explosion-cooldown-seconds", 5.0D) * 1000.0D));
        explosionPower = (float) Math.max(0.0D, plugin.getConfig().getDouble("passive-wither.explosion.power", 4.0D));
        explosionSetFire = plugin.getConfig().getBoolean("passive-wither.explosion.set-fire", false);
        nearestInventoryRadius = Math.max(1, plugin.getConfig().getInt("passive-wither.drops.nearest-inventory-radius", 16));
        dropOverflow = plugin.getConfig().getBoolean("passive-wither.drops.drop-overflow", true);

        loadPassiveWitherOwners();
        loadConfiguredDropInventory();
        loadSoundDisabledPlayers();
        setupEconomy();
        registerPlotSquaredFlag();
        refreshPassiveWitherEntityIds();
        restartTasks();
        restartSoundPacketHook();
    }

    public void shutdown() {
        if (soundSuppressTask != null) {
            soundSuppressTask.cancel();
            soundSuppressTask = null;
        }
        if (passiveWitherMaintenanceTask != null) {
            passiveWitherMaintenanceTask.cancel();
            passiveWitherMaintenanceTask = null;
        }
        if (soundPacketHook != null) {
            soundPacketHook.disable();
            soundPacketHook = null;
        }
        passiveWitherEntityIds.clear();
        scheduledExplosions.clear();
    }

    public void runMenuCommand(final Player player, final String payload) {
        if ("buy".equalsIgnoreCase(payload) || "egg".equalsIgnoreCase(payload)) {
            buyPassiveWitherEgg(player);
            return;
        }
        if ("sound-toggle".equalsIgnoreCase(payload)) {
            if (!hasSoundPermission(player)) {
                plugin.getLanguageManager().send(player, "no-permission");
                return;
            }
            final boolean disabled = !soundDisabledPlayers.contains(player.getUniqueId());
            setPassiveWitherSoundDisabled(player, disabled);
            plugin.getLanguageManager().send(player, disabled
                    ? "passive-wither-sound-disabled"
                    : "passive-wither-sound-enabled");
            if (disabled) {
                suppressWitherSounds(player);
            }
            return;
        }
        plugin.getLanguageManager().send(player, "chat-input-invalid");
    }

    public boolean handleCommand(final CommandSender sender, final String label, final String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("sound")) {
            return handleSoundCommand(sender, label, args);
        }
        if (args.length > 0 && (args[0].equalsIgnoreCase("chest") || args[0].equalsIgnoreCase("truhe"))) {
            return handleChestCommand(sender, label, args);
        }

        if (!sender.hasPermission(commandPermission)) {
            plugin.getLanguageManager().send(sender, "no-permission");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadPlugin();
            plugin.getLanguageManager().send(sender, "passive-wither-reloaded");
            return true;
        }

        if (args.length > 0 && !args[0].equalsIgnoreCase("egg") && !args[0].equalsIgnoreCase("give")) {
            sendUsage(sender, label);
            return true;
        }

        final Player target;
        int amount = 1;
        if (args.length >= 2) {
            target = plugin.getServer().getPlayerExact(args[1]);
            if (target == null) {
                plugin.getLanguageManager().send(sender, "passive-wither-player-not-found");
                return true;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            plugin.getLanguageManager().send(sender, "only-players");
            return true;
        }

        if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
            } catch (final NumberFormatException ignored) {
                plugin.getLanguageManager().send(sender, "passive-wither-invalid-amount");
                return true;
            }
        }

        givePassiveWitherEgg(target, amount);
        plugin.getLanguageManager().send(sender, "passive-wither-egg-given");
        return true;
    }

    private boolean handleChestCommand(final CommandSender sender, final String label, final String[] args) {
        if (chestPermission != null && !chestPermission.trim().isEmpty() && !sender.hasPermission(chestPermission.trim())) {
            plugin.getLanguageManager().send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            sendChestUsage(sender, label);
            return true;
        }

        final String mode = args[1].toLowerCase(Locale.ROOT);
        if (mode.equals("set") || mode.equals("setzen")) {
            if (!(sender instanceof Player)) {
                plugin.getLanguageManager().send(sender, "only-players");
                return true;
            }
            final Player player = (Player) sender;
            final Block targetBlock = player.getTargetBlockExact(8);
            if (targetBlock == null || !(targetBlock.getState() instanceof InventoryHolder)) {
                plugin.getLanguageManager().send(player, "passive-wither-chest-not-found");
                return true;
            }
            configuredDropInventoryLocation = targetBlock.getLocation();
            saveConfiguredDropInventory();
            plugin.getLanguageManager().send(player, "passive-wither-chest-set",
                    locationPlaceholders(configuredDropInventoryLocation));
            return true;
        }
        if (mode.equals("clear") || mode.equals("remove") || mode.equals("entfernen")) {
            configuredDropInventoryLocation = null;
            if (data != null) {
                data.set("target-inventory", null);
                saveData();
            }
            plugin.getLanguageManager().send(sender, "passive-wither-chest-cleared");
            return true;
        }
        if (mode.equals("info")) {
            if (configuredDropInventoryLocation == null) {
                plugin.getLanguageManager().send(sender, "passive-wither-chest-info-empty");
            } else {
                plugin.getLanguageManager().send(sender, "passive-wither-chest-info",
                        locationPlaceholders(configuredDropInventoryLocation));
            }
            return true;
        }
        sendChestUsage(sender, label);
        return true;
    }

    private boolean handleSoundCommand(final CommandSender sender, final String label, final String[] args) {
        if (!(sender instanceof Player)) {
            plugin.getLanguageManager().send(sender, "only-players");
            return true;
        }
        final Player player = (Player) sender;
        if (!hasSoundPermission(player)) {
            plugin.getLanguageManager().send(player, "no-permission");
            return true;
        }
        if (args.length == 1) {
            plugin.getLanguageManager().send(player, soundDisabledPlayers.contains(player.getUniqueId())
                    ? "passive-wither-sound-status-disabled"
                    : "passive-wither-sound-status-enabled");
            return true;
        }

        final String mode = args[1].toLowerCase(Locale.ROOT);
        if (mode.equals("off") || mode.equals("disable") || mode.equals("aus")) {
            setPassiveWitherSoundDisabled(player, true);
            plugin.getLanguageManager().send(player, "passive-wither-sound-disabled");
            suppressWitherSounds(player);
            return true;
        }
        if (mode.equals("on") || mode.equals("enable") || mode.equals("an")) {
            setPassiveWitherSoundDisabled(player, false);
            plugin.getLanguageManager().send(player, "passive-wither-sound-enabled");
            return true;
        }
        if (mode.equals("toggle")) {
            final boolean disabled = !soundDisabledPlayers.contains(player.getUniqueId());
            setPassiveWitherSoundDisabled(player, disabled);
            plugin.getLanguageManager().send(player, disabled
                    ? "passive-wither-sound-disabled"
                    : "passive-wither-sound-enabled");
            if (disabled) {
                suppressWitherSounds(player);
            }
            return true;
        }
        sendSoundUsage(player, label);
        return true;
    }

    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        if (args.length == 1) {
            final List<String> values = new ArrayList<>();
            addIfMatches(values, "egg", args[0]);
            addIfMatches(values, "give", args[0]);
            addIfMatches(values, "reload", args[0]);
            addIfMatches(values, "sound", args[0]);
            addIfMatches(values, "chest", args[0]);
            return values;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sound")) {
            final List<String> values = new ArrayList<>();
            addIfMatches(values, "on", args[1]);
            addIfMatches(values, "off", args[1]);
            addIfMatches(values, "toggle", args[1]);
            return values;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("chest") || args[0].equalsIgnoreCase("truhe"))) {
            final List<String> values = new ArrayList<>();
            addIfMatches(values, "set", args[1]);
            addIfMatches(values, "clear", args[1]);
            addIfMatches(values, "info", args[1]);
            return values;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("egg") || args[0].equalsIgnoreCase("give"))) {
            final String input = args[1].toLowerCase(Locale.ROOT);
            final List<String> values = new ArrayList<>();
            for (final Player player : plugin.getServer().getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(input)) {
                    values.add(player.getName());
                }
            }
            return values;
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("egg") || args[0].equalsIgnoreCase("give"))) {
            final List<String> values = new ArrayList<>();
            for (final String option : new String[]{"1", "8", "16", "64"}) {
                addIfMatches(values, option, args[2]);
            }
            return values;
        }
        return Collections.emptyList();
    }

    private void addIfMatches(final List<String> values, final String option, final String input) {
        if (option.startsWith(input.toLowerCase(Locale.ROOT))) {
            values.add(option);
        }
    }

    public void buyPassiveWitherEgg(final Player player) {
        if (!enabled) {
            plugin.getLanguageManager().send(player, "passive-wither-disabled");
            return;
        }
        if (buyPermission != null && !buyPermission.trim().isEmpty() && !player.hasPermission(buyPermission.trim())) {
            plugin.getLanguageManager().send(player, "no-permission");
            return;
        }
        final double finalPrice = player.hasPermission(freePermission) ? 0.0D : price;
        final Map<String, String> placeholders = pricePlaceholders(finalPrice);
        if (!withdraw(player, finalPrice, placeholders)) {
            return;
        }
        givePassiveWitherEgg(player, 1);
        plugin.getLanguageManager().send(player, "passive-wither-purchased", placeholders);
    }

    public String getPurchasePriceText() {
        return formatMoney(price);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnEggUse(final PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        final ItemStack item = event.getItem();
        if (!isPassiveWitherEgg(item)) {
            return;
        }
        event.setCancelled(true);

        final Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }
        final Location spawnLocation = clickedBlock.getRelative(event.getBlockFace()).getLocation().add(0.5D, 0.0D, 0.5D);
        spawnLocation.setYaw(event.getPlayer().getLocation().getYaw());
        if (!canSpawnPassiveWither(event.getPlayer(), spawnLocation)) {
            return;
        }
        if (spawnPassiveWither(spawnLocation, event.getPlayer())) {
            consumeOneEgg(event.getPlayer(), item);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPassiveWitherPickup(final PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getPlayer().isSneaking()
                || !isPassiveWither(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        final UUID owner = getPassiveWitherOwner(event.getRightClicked());
        if (owner == null) {
            plugin.getLanguageManager().send(event.getPlayer(), "passive-wither-pickup-unknown-owner");
            return;
        }
        if (!owner.equals(event.getPlayer().getUniqueId())) {
            plugin.getLanguageManager().send(event.getPlayer(), "passive-wither-pickup-not-owner");
            return;
        }
        event.getRightClicked().remove();
        passiveWitherEntityIds.remove(event.getRightClicked().getEntityId());
        removePassiveWitherOwner(event.getRightClicked().getUniqueId());
        givePassiveWitherEgg(event.getPlayer(), 1);
        plugin.getLanguageManager().send(event.getPlayer(), "passive-wither-pickup-success");
    }

    @EventHandler(ignoreCancelled = true)
    public void onPassiveWitherDeath(final EntityDeathEvent event) {
        if (!isPassiveWither(event.getEntity())) {
            return;
        }
        triggerPassiveWitherSoundSuppression(event.getEntity().getLocation());
        passiveWitherEntityIds.remove(event.getEntity().getEntityId());
        removePassiveWitherOwner(event.getEntity().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPassiveWitherTargetLivingEntity(final EntityTargetLivingEntityEvent event) {
        if (!isPassiveWither(event.getEntity()) || event.getTarget() == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getEntity() instanceof Mob) {
            ((Mob) event.getEntity()).setTarget(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPassiveWitherProjectileLaunch(final ProjectileLaunchEvent event) {
        final Projectile projectile = event.getEntity();
        final ProjectileSource shooter = projectile.getShooter();
        if (shooter instanceof Entity && isPassiveWither((Entity) shooter)) {
            event.setCancelled(true);
            projectile.remove();
            triggerPassiveWitherSoundSuppression(projectile.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPassiveWitherExplosionPrime(final ExplosionPrimeEvent event) {
        final UUID sourceId = getPassiveWitherExplosionSourceId(event.getEntity());
        if (sourceId == null) {
            return;
        }
        if (findScheduledExplosion(sourceId, event.getEntity().getLocation()) == null) {
            event.setCancelled(true);
            triggerPassiveWitherSoundSuppression(event.getEntity().getLocation());
            return;
        }
        rememberExplosion(event.getEntity().getLocation(), event.getRadius());
        triggerPassiveWitherSoundSuppression(event.getEntity().getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPassiveWitherExplosion(final EntityExplodeEvent event) {
        UUID sourceId = getPassiveWitherExplosionSourceId(event.getEntity());
        ScheduledExplosionMarker scheduledExplosion = findScheduledExplosion(sourceId, event.getLocation());
        if (sourceId == null && scheduledExplosion != null) {
            sourceId = scheduledExplosion.sourceId;
        }
        if (sourceId == null) {
            return;
        }
        if (scheduledExplosion == null) {
            event.blockList().clear();
            event.setYield(0.0F);
        } else {
            scheduledExplosions.remove(scheduledExplosion);
            routePassiveExplosionDrops(event);
            for (final Block block : event.blockList()) {
                rememberPassiveBlockBreakSound(block.getLocation());
            }
        }
        rememberExplosion(event.getLocation(), MIN_EXPLOSION_PROTECTION_RADIUS);
        triggerPassiveWitherSoundSuppression(event.getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPassiveWitherChangeBlock(final EntityChangeBlockEvent event) {
        if (!isPassiveWither(event.getEntity())) {
            return;
        }
        if (!tryUseBlockDestruction(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        triggerPassiveWitherSoundSuppression(event.getBlock().getLocation());
        rememberPassiveBlockBreakSound(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPassiveWitherDamageLivingEntity(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        final LivingEntity livingEntity = (LivingEntity) event.getEntity();
        final boolean passiveWitherTarget = isPassiveWither(livingEntity);
        if (passiveWitherTarget) {
            triggerPassiveWitherSoundSuppression(livingEntity.getLocation());
            event.setCancelled(true);
            return;
        }
        if (event instanceof EntityDamageByEntityEvent
                && isPassiveWitherDamageSource(((EntityDamageByEntityEvent) event).getDamager())) {
            event.setCancelled(true);
            protectFromPassiveWitherEffects(livingEntity);
            return;
        }
        final EntityDamageEvent.DamageCause cause = event.getCause();
        if ((cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)
                && isInProtectedExplosion(livingEntity.getLocation())) {
            event.setCancelled(true);
            protectFromPassiveWitherEffects(livingEntity);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPassiveWitherEffectLivingEntity(final EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        final PotionEffect effect = event.getNewEffect();
        if (effect == null || effect.getType() != PotionEffectType.WITHER) {
            return;
        }
        final Long protectedUntil = effectProtectedPlayers.get(event.getEntity().getUniqueId());
        if (protectedUntil == null) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (protectedUntil < now) {
            effectProtectedPlayers.remove(event.getEntity().getUniqueId());
            return;
        }
        event.setCancelled(true);
    }

    private ItemStack createPassiveWitherEgg(final int amount) {
        final ItemStack egg = new ItemStack(eggMaterial, amount);
        final ItemMeta meta = egg.getItemMeta();
        if (meta != null) {
            final Map<String, String> placeholders = pricePlaceholders(price);
            meta.setDisplayName(Text.color(apply(plugin.getConfig().getString("passive-wither.egg.name", "&5Passiver Wither"), placeholders)));
            meta.setLore(Text.color(apply(plugin.getConfig().getStringList("passive-wither.egg.lore"), placeholders)));
            meta.getPersistentDataContainer().set(passiveEggKey, PersistentDataType.BYTE, TRUE);
            egg.setItemMeta(meta);
        }
        return egg;
    }

    private boolean isPassiveWitherEgg(final ItemStack item) {
        if (item == null || item.getType() != eggMaterial || !item.hasItemMeta()) {
            return false;
        }
        final ItemMeta meta = item.getItemMeta();
        return meta != null && hasMarker(meta.getPersistentDataContainer(), passiveEggKey);
    }

    private boolean spawnPassiveWither(final Location location, final Player owner) {
        final World world = location.getWorld();
        if (world == null) {
            return false;
        }
        final Wither wither = world.spawn(location, Wither.class);
        setupPassiveWither(wither, owner, location);
        if (wither.isValid()) {
            passiveWitherEntityIds.add(wither.getEntityId());
            savePassiveWitherOwner(wither, owner);
        }
        triggerGlobalPassiveWitherSoundSuppression();
        return wither.isValid();
    }

    private void setupPassiveWither(final Wither wither, final Player owner, final Location anchorLocation) {
        markPassive(wither);
        wither.getPersistentDataContainer().set(passiveWitherOwnerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        wither.setCustomName(Text.color(plugin.getConfig().getString("passive-wither.wither.name", "&5Passiver Wither")));
        wither.setCustomNameVisible(true);
        wither.setRemoveWhenFarAway(false);
        configureStationaryWither(wither);
        setPassiveWitherAnchor(wither, anchorLocation);
        passiveWitherNextExplosions.put(wither.getUniqueId(), System.currentTimeMillis() + explosionCooldownMillis);
        passiveWitherEntityIds.add(wither.getEntityId());
        hidePassiveWitherBossBar(wither);
    }

    private void consumeOneEgg(final Player player, final ItemStack item) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        item.setAmount(item.getAmount() - 1);
    }

    private void givePassiveWitherEgg(final Player player, final int amount) {
        final Map<Integer, ItemStack> leftovers = player.getInventory().addItem(createPassiveWitherEgg(amount));
        for (final ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private void setPassiveWitherSoundDisabled(final Player player, final boolean disabled) {
        if (disabled) {
            soundDisabledPlayers.add(player.getUniqueId());
        } else {
            soundDisabledPlayers.remove(player.getUniqueId());
        }
        saveSoundDisabledPlayers();
    }

    private void markPassive(final Entity entity) {
        entity.getPersistentDataContainer().set(passiveWitherKey, PersistentDataType.BYTE, TRUE);
    }

    private boolean isPassiveWither(final Entity entity) {
        return entity instanceof Wither && hasMarker(entity.getPersistentDataContainer(), passiveWitherKey);
    }

    private UUID getPassiveWitherOwner(final Entity entity) {
        final String ownerId = entity.getPersistentDataContainer().get(passiveWitherOwnerKey, PersistentDataType.STRING);
        if (ownerId != null) {
            try {
                final UUID owner = UUID.fromString(ownerId);
                if (!passiveWitherOwners.containsKey(entity.getUniqueId())) {
                    final String ownerName = plugin.getServer().getOfflinePlayer(owner).getName();
                    savePassiveWitherOwner(entity, owner, ownerName);
                }
                return owner;
            } catch (final IllegalArgumentException ignored) {
                return null;
            }
        }
        final UUID owner = passiveWitherOwners.get(entity.getUniqueId());
        if (owner != null) {
            entity.getPersistentDataContainer().set(passiveWitherOwnerKey, PersistentDataType.STRING, owner.toString());
        }
        return owner;
    }

    private void loadPassiveWitherOwners() {
        passiveWitherOwners.clear();
        passiveWitherAnchors.clear();
        passiveWitherNextExplosions.clear();
        if (data == null) {
            return;
        }
        final org.bukkit.configuration.ConfigurationSection section = data.getConfigurationSection("withers");
        if (section == null) {
            return;
        }
        for (final String witherIdText : section.getKeys(false)) {
            final String ownerIdText = data.getString("withers." + witherIdText + ".owner");
            if (ownerIdText == null) {
                continue;
            }
            try {
                final UUID witherId = UUID.fromString(witherIdText);
                passiveWitherOwners.put(witherId, UUID.fromString(ownerIdText));
                final Location anchor = loadLocation("withers." + witherIdText + ".anchor");
                if (anchor != null) {
                    passiveWitherAnchors.put(witherId, anchor);
                }
                passiveWitherNextExplosions.put(witherId, Math.max(
                        System.currentTimeMillis(),
                        data.getLong("withers." + witherIdText + ".next-explosion-at", System.currentTimeMillis() + explosionCooldownMillis)
                ));
            } catch (final IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ungueltiger Passive-Wither-Datensatz: " + witherIdText);
            }
        }
    }

    private void loadConfiguredDropInventory() {
        configuredDropInventoryLocation = loadLocation("target-inventory");
        if (configuredDropInventoryLocation != null
                && !(configuredDropInventoryLocation.getBlock().getState() instanceof InventoryHolder)) {
            configuredDropInventoryLocation = null;
        }
    }

    private void loadSoundDisabledPlayers() {
        soundDisabledPlayers.clear();
        for (final String uuidText : data.getStringList("sound-disabled")) {
            try {
                soundDisabledPlayers.add(UUID.fromString(uuidText));
            } catch (final IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ungueltige Passive-Wither-Sound-UUID: " + uuidText);
            }
        }
    }

    private void savePassiveWitherOwner(final Wither wither, final Player owner) {
        savePassiveWitherOwner(wither, owner.getUniqueId(), owner.getName());
    }

    private void savePassiveWitherOwner(final Entity wither, final UUID owner, final String ownerName) {
        final UUID witherId = wither.getUniqueId();
        final Location anchor = getPassiveWitherAnchor(wither);
        final long nextExplosionAt = passiveWitherNextExplosions.containsKey(witherId)
                ? passiveWitherNextExplosions.get(witherId)
                : System.currentTimeMillis() + explosionCooldownMillis;
        passiveWitherNextExplosions.put(witherId, nextExplosionAt);
        passiveWitherOwners.put(wither.getUniqueId(), owner);
        data.set("withers." + witherId + ".owner", owner.toString());
        data.set("withers." + witherId + ".owner-name", ownerName == null ? "unknown" : ownerName);
        data.set("withers." + witherId + ".world", wither.getWorld().getName());
        if (!data.contains("withers." + witherId + ".spawned-at")) {
            data.set("withers." + witherId + ".spawned-at", System.currentTimeMillis());
        }
        data.set("withers." + witherId + ".next-explosion-at", nextExplosionAt);
        saveLocation("withers." + witherId + ".anchor", anchor);
        saveData();
    }

    private void removePassiveWitherOwner(final UUID witherId) {
        passiveWitherOwners.remove(witherId);
        passiveWitherAnchors.remove(witherId);
        passiveWitherNextExplosions.remove(witherId);
        if (data != null) {
            data.set("withers." + witherId, null);
            saveData();
        }
    }

    private void savePassiveWitherNextExplosion(final UUID witherId, final long nextExplosionAt) {
        passiveWitherNextExplosions.put(witherId, nextExplosionAt);
        if (data != null) {
            data.set("withers." + witherId + ".next-explosion-at", nextExplosionAt);
            saveData();
        }
    }

    private void saveConfiguredDropInventory() {
        if (configuredDropInventoryLocation == null) {
            data.set("target-inventory", null);
        } else {
            saveLocation("target-inventory", configuredDropInventoryLocation);
        }
        saveData();
    }

    private void saveSoundDisabledPlayers() {
        final List<String> disabled = new ArrayList<>();
        for (final UUID uuid : soundDisabledPlayers) {
            disabled.add(uuid.toString());
        }
        Collections.sort(disabled);
        data.set("sound-disabled", disabled);
        saveData();
    }

    private void registerPlotSquaredFlag() {
        if (registeredPlotSquaredFlag || !plugin.getServer().getPluginManager().isPluginEnabled("PlotSquared")) {
            return;
        }
        try {
            GlobalFlagContainer.getInstance().addFlag(new PassiveWitherSpawnFlag(
                    false,
                    ChatColor.stripColor(plugin.getLanguageManager().getMessage("passive-wither-flag-description"))
            ));
            registeredPlotSquaredFlag = true;
            plugin.getLogger().info("PlotSquared-Flag registriert: passive-wither-spawn");
        } catch (final RuntimeException exception) {
            registeredPlotSquaredFlag = true;
            plugin.getLogger().warning("PlotSquared-Flag passive-wither-spawn konnte nicht registriert werden: "
                    + exception.getMessage());
        }
    }

    private boolean canSpawnPassiveWither(final Player player, final Location location) {
        if (!enabled) {
            plugin.getLanguageManager().send(player, "passive-wither-disabled");
            return false;
        }
        try {
            final com.plotsquared.core.location.Location plotLocation = com.plotsquared.core.location.Location.at(
                    location.getWorld().getName(),
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ()
            );
            if (!plotLocation.isPlotArea()) {
                return true;
            }
            final Plot plot = plotLocation.getPlot();
            if (plot == null || !plot.getFlag(PassiveWitherSpawnFlag.class)) {
                plugin.getLanguageManager().send(player, "passive-wither-deny-plotsquared");
                return false;
            }
        } catch (final Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "Passive-Wither-PlotSquared-Flag konnte nicht geprueft werden.", throwable);
            final Map<String, String> placeholders = new HashMap<>();
            placeholders.put("plugin", "PlotSquared");
            plugin.getLanguageManager().send(player, "passive-wither-protection-check-failed", placeholders);
            return false;
        }
        return true;
    }

    private UUID getPassiveWitherExplosionSourceId(final Entity entity) {
        if (entity == null) {
            return null;
        }
        if (isPassiveWither(entity)) {
            return entity.getUniqueId();
        }
        if (hasMarker(entity.getPersistentDataContainer(), passiveWitherKey)) {
            final String sourceId = entity.getPersistentDataContainer().get(passiveWitherSourceKey, PersistentDataType.STRING);
            if (sourceId != null) {
                try {
                    return UUID.fromString(sourceId);
                } catch (final IllegalArgumentException ignored) {
                    return entity.getUniqueId();
                }
            }
            return entity.getUniqueId();
        }
        if (entity instanceof Projectile) {
            final ProjectileSource shooter = ((Projectile) entity).getShooter();
            if (shooter instanceof Entity && isPassiveWither((Entity) shooter)) {
                return ((Entity) shooter).getUniqueId();
            }
        }
        return null;
    }

    private ScheduledExplosionMarker findScheduledExplosion(final UUID sourceId, final Location location) {
        if (location == null) {
            return null;
        }
        final long now = System.currentTimeMillis();
        scheduledExplosions.removeIf(marker -> marker.expiresAtMillis < now);
        for (final ScheduledExplosionMarker marker : scheduledExplosions) {
            if ((sourceId == null || marker.sourceId.equals(sourceId)) && marker.isInside(location)) {
                return marker;
            }
        }
        return null;
    }

    private void routePassiveExplosionDrops(final EntityExplodeEvent event) {
        final List<Block> blocks = event.blockList();
        final DropTarget dropTarget = findDropTarget(event.getLocation(), blocks);
        if (dropTarget != null) {
            blocks.removeIf(block -> isSameBlock(block, dropTarget.block));
        }

        final List<ItemStack> drops = new ArrayList<>();
        for (final Block block : blocks) {
            final BlockState state = block.getState();
            if (state instanceof InventoryHolder) {
                for (final ItemStack content : ((InventoryHolder) state).getInventory().getContents()) {
                    if (content != null && content.getType() != Material.AIR) {
                        drops.add(content.clone());
                    }
                }
            }
            for (final ItemStack drop : block.getDrops()) {
                if (drop != null && drop.getType() != Material.AIR) {
                    drops.add(drop.clone());
                }
            }
        }

        event.setYield(0.0F);
        if (drops.isEmpty()) {
            return;
        }

        if (dropTarget == null) {
            if (dropOverflow) {
                dropItems(event.getLocation(), drops);
            }
            return;
        }

        for (final ItemStack drop : drops) {
            final Map<Integer, ItemStack> leftovers = dropTarget.inventory.addItem(drop);
            if (dropOverflow) {
                dropItems(dropTarget.block.getLocation().add(0.5D, 1.0D, 0.5D), new ArrayList<>(leftovers.values()));
            }
        }
    }

    private DropTarget findDropTarget(final Location location, final List<Block> explodingBlocks) {
        final DropTarget configuredTarget = getConfiguredDropTarget();
        if (configuredTarget != null) {
            return configuredTarget;
        }
        final World world = location.getWorld();
        if (world == null) {
            return null;
        }
        final Set<String> ignoredBlocks = new HashSet<>();
        for (final Block block : explodingBlocks) {
            if (block.getWorld().getUID().equals(world.getUID())) {
                ignoredBlocks.add(blockKey(block));
            }
        }
        DropTarget nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        final int centerX = location.getBlockX();
        final int centerY = location.getBlockY();
        final int centerZ = location.getBlockZ();
        final int minY = Math.max(0, centerY - nearestInventoryRadius);
        final int maxY = Math.min(world.getMaxHeight() - 1, centerY + nearestInventoryRadius);
        for (int x = centerX - nearestInventoryRadius; x <= centerX + nearestInventoryRadius; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = centerZ - nearestInventoryRadius; z <= centerZ + nearestInventoryRadius; z++) {
                    final Block block = world.getBlockAt(x, y, z);
                    if (ignoredBlocks.contains(blockKey(block))) {
                        continue;
                    }
                    final BlockState state = block.getState();
                    if (!(state instanceof InventoryHolder)) {
                        continue;
                    }
                    final double distance = block.getLocation().add(0.5D, 0.5D, 0.5D).distanceSquared(location);
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearest = new DropTarget(block, ((InventoryHolder) state).getInventory());
                    }
                }
            }
        }
        return nearest;
    }

    private DropTarget getConfiguredDropTarget() {
        if (configuredDropInventoryLocation == null || configuredDropInventoryLocation.getWorld() == null) {
            return null;
        }
        final Block block = configuredDropInventoryLocation.getBlock();
        final BlockState state = block.getState();
        if (!(state instanceof InventoryHolder)) {
            return null;
        }
        return new DropTarget(block, ((InventoryHolder) state).getInventory());
    }

    private void dropItems(final Location location, final List<ItemStack> items) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        for (final ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
                world.dropItemNaturally(location, item);
            }
        }
    }

    private boolean isSameBlock(final Block first, final Block second) {
        return first != null && second != null
                && first.getWorld().getUID().equals(second.getWorld().getUID())
                && first.getX() == second.getX()
                && first.getY() == second.getY()
                && first.getZ() == second.getZ();
    }

    private String blockKey(final Block block) {
        return block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private boolean tryUseBlockDestruction(final UUID sourceId) {
        if (sourceId == null) {
            return false;
        }
        final long now = System.currentTimeMillis();
        if (sourceId.equals(activeBlockDestructionSourceId) && now <= blockDestructionBatchUntilMillis) {
            return true;
        }
        if (now < nextBlockDestructionAllowedAtMillis) {
            return false;
        }
        activeBlockDestructionSourceId = sourceId;
        blockDestructionBatchUntilMillis = now + BLOCK_DESTRUCTION_BATCH_MILLIS;
        nextBlockDestructionAllowedAtMillis = now + explosionCooldownMillis;
        return true;
    }

    private boolean isPassiveWitherDamageSource(final Entity entity) {
        if (entity == null) {
            return false;
        }
        if (isPassiveWither(entity) || hasMarker(entity.getPersistentDataContainer(), passiveWitherKey)) {
            return true;
        }
        if (entity instanceof Projectile) {
            final ProjectileSource shooter = ((Projectile) entity).getShooter();
            return shooter instanceof Entity && isPassiveWither((Entity) shooter);
        }
        return false;
    }

    private boolean isPlayerDamageSource(final Entity entity) {
        if (entity instanceof Player) {
            return true;
        }
        if (entity instanceof Projectile) {
            return ((Projectile) entity).getShooter() instanceof Player;
        }
        return entity instanceof TNTPrimed && ((TNTPrimed) entity).getSource() != null
                && isPlayerDamageSource(((TNTPrimed) entity).getSource());
    }

    private boolean hasMarker(final PersistentDataContainer container, final NamespacedKey key) {
        final Byte value = container.get(key, PersistentDataType.BYTE);
        return value != null && value == TRUE;
    }

    private boolean isBlockDamageCause(final EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.SUFFOCATION
                || cause == EntityDamageEvent.DamageCause.CONTACT
                || cause == EntityDamageEvent.DamageCause.FALLING_BLOCK
                || cause == EntityDamageEvent.DamageCause.HOT_FLOOR;
    }

    private void rememberExplosion(final Location location, final double radius) {
        final long now = System.currentTimeMillis();
        protectedExplosions.removeIf(explosion -> explosion.expiresAtMillis < now);
        final double protectionRadius = Math.max(MIN_EXPLOSION_PROTECTION_RADIUS, radius + 2.0D);
        protectedExplosions.add(new ProtectedExplosion(location, protectionRadius, now + EXPLOSION_PROTECTION_MILLIS));
    }

    private boolean isInProtectedExplosion(final Location location) {
        final long now = System.currentTimeMillis();
        protectedExplosions.removeIf(explosion -> explosion.expiresAtMillis < now);
        for (final ProtectedExplosion explosion : protectedExplosions) {
            if (explosion.isInside(location)) {
                return true;
            }
        }
        return false;
    }

    private void protectFromPassiveWitherEffects(final LivingEntity livingEntity) {
        effectProtectedPlayers.put(livingEntity.getUniqueId(), System.currentTimeMillis() + EXPLOSION_PROTECTION_MILLIS);
    }

    private void restartSoundPacketHook() {
        if (soundPacketHook != null) {
            soundPacketHook.disable();
            soundPacketHook = null;
        }
        if (!enabled || !plugin.getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            if (enabled) {
                plugin.getLogger().warning("ProtocolLib wurde nicht gefunden. Passive-Wither-Sounds werden nur nachtraeglich gestoppt.");
            }
            return;
        }
        try {
            soundPacketHook = new ProtocolLibPassiveWitherSoundHook(plugin, this);
            soundPacketHook.enable();
            plugin.getLogger().info("Passive-Wither-ProtocolLib-Soundfilter aktiviert.");
        } catch (final Throwable throwable) {
            soundPacketHook = null;
            plugin.getLogger().warning("Passive-Wither-Soundfilter konnte nicht aktiviert werden: " + throwable.getMessage());
        }
    }

    private void refreshPassiveWitherEntityIds() {
        passiveWitherEntityIds.clear();
        if (!enabled) {
            return;
        }
        for (final World world : plugin.getServer().getWorlds()) {
            for (final Entity entity : world.getEntities()) {
                if (isPassiveWither(entity)) {
                    passiveWitherEntityIds.add(entity.getEntityId());
                    final Wither wither = (Wither) entity;
                    getPassiveWitherOwner(wither);
                    configureStationaryWither(wither);
                    getPassiveWitherAnchor(wither);
                    hidePassiveWitherBossBar(wither);
                }
            }
        }
    }

    private void restartTasks() {
        if (soundSuppressTask != null) {
            soundSuppressTask.cancel();
            soundSuppressTask = null;
        }
        if (passiveWitherMaintenanceTask != null) {
            passiveWitherMaintenanceTask.cancel();
            passiveWitherMaintenanceTask = null;
        }
        if (!enabled) {
            return;
        }
        soundSuppressTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::runSoundSuppressionTick, 1L, 1L);
        passiveWitherMaintenanceTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::runPassiveWitherMaintenanceTick, 1L, 1L);
    }

    private void runPassiveWitherMaintenanceTick() {
        passiveWitherMaintenanceTicks++;
        if (passiveWitherMaintenanceTicks >= 20) {
            passiveWitherMaintenanceTicks = 0;
            refreshPassiveWitherEntityIds();
        }

        final long now = System.currentTimeMillis();
        scheduledExplosions.removeIf(marker -> marker.expiresAtMillis < now);
        for (final UUID witherId : new HashSet<>(passiveWitherOwners.keySet())) {
            final Entity entity = Bukkit.getEntity(witherId);
            if (!(entity instanceof Wither) || !entity.isValid() || entity.isDead() || !isPassiveWither(entity)) {
                continue;
            }
            final Wither wither = (Wither) entity;
            keepPassiveWitherStationary(wither);
            final long nextExplosionAt = passiveWitherNextExplosions.containsKey(witherId)
                    ? passiveWitherNextExplosions.get(witherId)
                    : now + explosionCooldownMillis;
            if (!passiveWitherNextExplosions.containsKey(witherId)) {
                savePassiveWitherNextExplosion(witherId, nextExplosionAt);
            }
            if (now >= nextExplosionAt) {
                triggerPassiveWitherExplosion(wither, now);
            }
        }
    }

    private void configureStationaryWither(final Wither wither) {
        wither.setTarget(null);
        wither.setAI(false);
        wither.setGravity(false);
        wither.setInvulnerable(true);
        wither.setCollidable(false);
        wither.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
    }

    private void setPassiveWitherAnchor(final Wither wither, final Location location) {
        final Location anchor = location.clone();
        anchor.setYaw(0.0F);
        anchor.setPitch(0.0F);
        passiveWitherAnchors.put(wither.getUniqueId(), anchor);
        wither.teleport(anchor);
    }

    private Location getPassiveWitherAnchor(final Entity entity) {
        Location anchor = passiveWitherAnchors.get(entity.getUniqueId());
        if (anchor == null || anchor.getWorld() == null) {
            anchor = entity.getLocation().clone();
            anchor.setYaw(0.0F);
            anchor.setPitch(0.0F);
            passiveWitherAnchors.put(entity.getUniqueId(), anchor);
        }
        return anchor.clone();
    }

    private void keepPassiveWitherStationary(final Wither wither) {
        configureStationaryWither(wither);
        final Location anchor = getPassiveWitherAnchor(wither);
        final Location current = wither.getLocation();
        if (current.getWorld() == null || anchor.getWorld() == null
                || !current.getWorld().getUID().equals(anchor.getWorld().getUID())
                || current.distanceSquared(anchor) > 0.0001D
                || Math.abs(current.getYaw() - anchor.getYaw()) > 0.01F
                || Math.abs(current.getPitch() - anchor.getPitch()) > 0.01F) {
            wither.teleport(anchor);
        }
        wither.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
    }

    private void triggerPassiveWitherExplosion(final Wither wither, final long now) {
        final Location anchor = getPassiveWitherAnchor(wither);
        final World world = anchor.getWorld();
        if (world == null) {
            return;
        }
        final long nextExplosionAt = now + explosionCooldownMillis;
        savePassiveWitherNextExplosion(wither.getUniqueId(), nextExplosionAt);
        scheduledExplosions.add(new ScheduledExplosionMarker(
                wither.getUniqueId(),
                anchor,
                Math.max(MIN_EXPLOSION_PROTECTION_RADIUS, explosionPower + 2.0D),
                now + EXPLOSION_PROTECTION_MILLIS
        ));
        rememberExplosion(anchor, Math.max(MIN_EXPLOSION_PROTECTION_RADIUS, explosionPower));
        triggerPassiveWitherSoundSuppression(anchor);
        createPassiveWitherExplosion(world, anchor, wither);
        keepPassiveWitherStationary(wither);
    }

    private void createPassiveWitherExplosion(final World world, final Location location, final Wither source) {
        try {
            final Method method = world.getClass().getMethod(
                    "createExplosion",
                    double.class,
                    double.class,
                    double.class,
                    float.class,
                    boolean.class,
                    boolean.class,
                    Entity.class
            );
            method.invoke(world, location.getX(), location.getY(), location.getZ(), explosionPower, explosionSetFire, true, source);
            return;
        } catch (final NoSuchMethodException ignored) {
            // Spigot 1.16 exposes both variants depending on the server implementation.
        } catch (final IllegalAccessException | InvocationTargetException exception) {
            plugin.getLogger().log(Level.WARNING, "Passive-Wither-Explosion konnte nicht mit Quelle erstellt werden.", exception);
        }
        world.createExplosion(location.getX(), location.getY(), location.getZ(), explosionPower, explosionSetFire, true);
    }

    private void hidePassiveWitherBossBar(final Wither wither) {
        try {
            wither.getBossBar().removeAll();
            wither.getBossBar().setVisible(false);
        } catch (final Throwable ignored) {
            // Older server builds may not expose the boss bar API on Wither.
        }
    }

    public boolean isPassiveWitherSoundDisabled(final Player player) {
        return player != null && soundDisabledPlayers.contains(player.getUniqueId());
    }

    public boolean isPassiveWitherEntityId(final int entityId) {
        return passiveWitherEntityIds.contains(entityId);
    }

    public boolean shouldSuppressPassiveWitherSound(final Player player, final Location location) {
        if (!isPassiveWitherSoundDisabled(player)) {
            return false;
        }
        final long now = System.currentTimeMillis();
        activeSoundSuppressions.removeIf(area -> area.expiresAtMillis < now);
        if (now <= globalSoundSuppressionUntilMillis) {
            return true;
        }
        return location != null && isInsideActiveSoundSuppression(location);
    }

    public boolean shouldSuppressPassiveBlockBreakSound(final Player player, final Location location) {
        if (!isPassiveWitherSoundDisabled(player) || location == null) {
            return false;
        }
        final long now = System.currentTimeMillis();
        activeBlockBreakSoundSuppressions.removeIf(area -> area.expiresAtMillis < now);
        return isInsideActiveBlockBreakSoundSuppression(location);
    }

    private void runSoundSuppressionTick() {
        if (soundDisabledPlayers.isEmpty()) {
            return;
        }
        final long now = System.currentTimeMillis();
        soundSuppressTicks++;
        if (soundSuppressTicks >= 20) {
            soundSuppressTicks = 0;
            refreshPassiveWitherEntityIds();
        }
        final boolean suppressGlobally = now <= globalSoundSuppressionUntilMillis;
        activeSoundSuppressions.removeIf(area -> area.expiresAtMillis < now);
        activeBlockBreakSoundSuppressions.removeIf(area -> area.expiresAtMillis < now);
        for (final Player player : plugin.getServer().getOnlinePlayers()) {
            if (soundDisabledPlayers.contains(player.getUniqueId())
                    && (suppressGlobally || isInsideActiveSoundSuppression(player.getLocation()) || isNearPassiveWither(player))) {
                suppressWitherSounds(player);
            }
        }
    }

    private boolean isNearPassiveWither(final Player player) {
        for (final Entity entity : player.getNearbyEntities(PASSIVE_WITHER_SOUND_RADIUS, PASSIVE_WITHER_SOUND_RADIUS, PASSIVE_WITHER_SOUND_RADIUS)) {
            if (isPassiveWither(entity)) {
                return true;
            }
        }
        return false;
    }

    private void suppressPassiveWitherSoundsNear(final Location location, final double radius) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        final double radiusSquared = radius * radius;
        for (final Player player : world.getPlayers()) {
            if (soundDisabledPlayers.contains(player.getUniqueId())
                    && player.getLocation().distanceSquared(location) <= radiusSquared) {
                suppressWitherSounds(player);
            }
        }
    }

    private void triggerPassiveWitherSoundSuppression(final Location location) {
        final long now = System.currentTimeMillis();
        activeSoundSuppressions.removeIf(area -> area.expiresAtMillis < now);
        activeSoundSuppressions.add(new SoundSuppressionArea(location, PASSIVE_WITHER_SOUND_RADIUS, now + SOUND_SUPPRESSION_BURST_MILLIS));
        suppressPassiveWitherSoundsNear(location, PASSIVE_WITHER_SOUND_RADIUS);
    }

    private void rememberPassiveBlockBreakSound(final Location location) {
        final long now = System.currentTimeMillis();
        activeBlockBreakSoundSuppressions.removeIf(area -> area.expiresAtMillis < now);
        activeBlockBreakSoundSuppressions.add(new SoundSuppressionArea(location, BLOCK_BREAK_SOUND_RADIUS, now + SOUND_SUPPRESSION_BURST_MILLIS));
    }

    private void triggerGlobalPassiveWitherSoundSuppression() {
        globalSoundSuppressionUntilMillis = Math.max(
                globalSoundSuppressionUntilMillis,
                System.currentTimeMillis() + SOUND_SUPPRESSION_BURST_MILLIS
        );
        suppressPassiveWitherSoundsForDisabledPlayers();
    }

    private boolean isInsideActiveSoundSuppression(final Location location) {
        for (final SoundSuppressionArea area : activeSoundSuppressions) {
            if (area.isInside(location)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInsideActiveBlockBreakSoundSuppression(final Location location) {
        for (final SoundSuppressionArea area : activeBlockBreakSoundSuppressions) {
            if (area.isInside(location)) {
                return true;
            }
        }
        return false;
    }

    private void suppressPassiveWitherSoundsForDisabledPlayers() {
        for (final Player player : plugin.getServer().getOnlinePlayers()) {
            if (soundDisabledPlayers.contains(player.getUniqueId())) {
                suppressWitherSounds(player);
            }
        }
    }

    private void suppressWitherSounds(final Player player) {
        for (final Sound sound : mutedFallbackSounds) {
            player.stopSound(sound);
        }
    }

    private boolean hasSoundPermission(final Player player) {
        return soundPermission == null || soundPermission.trim().isEmpty() || player.hasPermission(soundPermission.trim());
    }

    private void setupEconomy() {
        economy = null;
        economyHasMethod = null;
        economyWithdrawMethod = null;
        economyFormatMethod = null;
        if (!economyEnabled) {
            return;
        }
        try {
            final Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            @SuppressWarnings({"rawtypes", "unchecked"})
            final RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) economyClass);
            if (registration == null) {
                return;
            }
            economy = registration.getProvider();
            economyHasMethod = economyClass.getMethod("has", OfflinePlayer.class, double.class);
            economyWithdrawMethod = economyClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            economyFormatMethod = economyClass.getMethod("format", double.class);
        } catch (final ClassNotFoundException | NoSuchMethodException exception) {
            plugin.getLogger().fine("Vault-Economy ist fuer Passive-Wither nicht verfuegbar: " + exception.getMessage());
        }
    }

    private boolean withdraw(final Player player, final double amount, final Map<String, String> placeholders) {
        if (amount <= 0.0D || !economyEnabled) {
            return true;
        }
        if (economy == null || economyHasMethod == null || economyWithdrawMethod == null) {
            plugin.getLanguageManager().send(player, "passive-wither-economy-missing", placeholders);
            return false;
        }
        try {
            final Boolean hasMoney = (Boolean) economyHasMethod.invoke(economy, player, amount);
            if (hasMoney == null || !hasMoney) {
                plugin.getLanguageManager().send(player, "passive-wither-no-money", placeholders);
                return false;
            }
            final Object response = economyWithdrawMethod.invoke(economy, player, amount);
            final Method successMethod = response.getClass().getMethod("transactionSuccess");
            final Object success = successMethod.invoke(response);
            if (!(success instanceof Boolean) || !(Boolean) success) {
                plugin.getLanguageManager().send(player, "passive-wither-no-money", placeholders);
                return false;
            }
            return true;
        } catch (final ReflectiveOperationException exception) {
            plugin.getLogger().log(Level.WARNING, "Passive-Wither-Kauf konnte keine Taler abbuchen.", exception);
            plugin.getLanguageManager().send(player, "passive-wither-economy-missing", placeholders);
            return false;
        }
    }

    private Map<String, String> pricePlaceholders(final double amount) {
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("price", formatMoney(amount));
        return placeholders;
    }

    private String formatMoney(final double amount) {
        if (economy != null && economyFormatMethod != null) {
            try {
                final Object formatted = economyFormatMethod.invoke(economy, amount);
                if (formatted != null) {
                    return formatted.toString();
                }
            } catch (final IllegalAccessException | InvocationTargetException ignored) {
                // Fall back to plain formatting.
            }
        }
        return String.format(Locale.US, "%.2f", amount);
    }

    private String apply(final String text, final Map<String, String> placeholders) {
        String result = text == null ? "" : text;
        for (final Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private Material material(final String configured, final Material fallback) {
        if (configured == null || configured.trim().isEmpty()) {
            return fallback;
        }
        final Material material = Material.matchMaterial(configured.trim().toUpperCase(Locale.ROOT));
        return material == null ? fallback : material;
    }

    private List<String> apply(final List<String> lines, final Map<String, String> placeholders) {
        final List<String> result = new ArrayList<>();
        if (lines == null) {
            return result;
        }
        for (final String line : lines) {
            result.add(apply(line, placeholders));
        }
        return result;
    }

    private Location loadLocation(final String path) {
        if (data == null || !data.contains(path + ".world")) {
            return null;
        }
        final String worldName = data.getString(path + ".world", "");
        final World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            return null;
        }
        final Location location = new Location(
                world,
                data.getDouble(path + ".x"),
                data.getDouble(path + ".y"),
                data.getDouble(path + ".z")
        );
        location.setYaw((float) data.getDouble(path + ".yaw", 0.0D));
        location.setPitch((float) data.getDouble(path + ".pitch", 0.0D));
        return location;
    }

    private void saveLocation(final String path, final Location location) {
        if (location == null || location.getWorld() == null) {
            data.set(path, null);
            return;
        }
        data.set(path + ".world", location.getWorld().getName());
        data.set(path + ".x", location.getX());
        data.set(path + ".y", location.getY());
        data.set(path + ".z", location.getZ());
        data.set(path + ".yaw", (double) location.getYaw());
        data.set(path + ".pitch", (double) location.getPitch());
    }

    private Map<String, String> locationPlaceholders(final Location location) {
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("world", location.getWorld() == null ? "-" : location.getWorld().getName());
        placeholders.put("x", String.valueOf(location.getBlockX()));
        placeholders.put("y", String.valueOf(location.getBlockY()));
        placeholders.put("z", String.valueOf(location.getBlockZ()));
        return placeholders;
    }

    private void sendUsage(final CommandSender sender, final String label) {
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("label", label);
        plugin.getLanguageManager().send(sender, "passive-wither-command-usage", placeholders);
    }

    private void sendSoundUsage(final CommandSender sender, final String label) {
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("label", label);
        plugin.getLanguageManager().send(sender, "passive-wither-sound-usage", placeholders);
    }

    private void sendChestUsage(final CommandSender sender, final String label) {
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("label", label);
        plugin.getLanguageManager().send(sender, "passive-wither-chest-usage", placeholders);
    }

    private void saveData() {
        plugin.getStorageService().save("passivewither", dataFile, data);
    }

    private static boolean isPassiveWitherFallbackMutedSound(final Sound sound) {
        final String name = sound.name();
        return sound == Sound.ENTITY_GENERIC_EXPLODE
                || name.startsWith("ENTITY_WITHER_")
                || (name.startsWith("BLOCK_") && name.endsWith("_BREAK"));
    }

    private static final class ProtectedExplosion {
        private final UUID worldId;
        private final double x;
        private final double y;
        private final double z;
        private final double radiusSquared;
        private final long expiresAtMillis;

        private ProtectedExplosion(final Location location, final double radius, final long expiresAtMillis) {
            this.worldId = location.getWorld() == null ? null : location.getWorld().getUID();
            this.x = location.getX();
            this.y = location.getY();
            this.z = location.getZ();
            this.radiusSquared = radius * radius;
            this.expiresAtMillis = expiresAtMillis;
        }

        private boolean isInside(final Location location) {
            if (location.getWorld() == null || worldId == null || !worldId.equals(location.getWorld().getUID())) {
                return false;
            }
            final double dx = location.getX() - x;
            final double dy = location.getY() - y;
            final double dz = location.getZ() - z;
            return dx * dx + dy * dy + dz * dz <= radiusSquared;
        }
    }

    private static final class SoundSuppressionArea {
        private final UUID worldId;
        private final double x;
        private final double y;
        private final double z;
        private final double radiusSquared;
        private final long expiresAtMillis;

        private SoundSuppressionArea(final Location location, final double radius, final long expiresAtMillis) {
            this.worldId = location.getWorld() == null ? null : location.getWorld().getUID();
            this.x = location.getX();
            this.y = location.getY();
            this.z = location.getZ();
            this.radiusSquared = radius * radius;
            this.expiresAtMillis = expiresAtMillis;
        }

        private boolean isInside(final Location location) {
            if (location.getWorld() == null || worldId == null || !worldId.equals(location.getWorld().getUID())) {
                return false;
            }
            final double dx = location.getX() - x;
            final double dy = location.getY() - y;
            final double dz = location.getZ() - z;
            return dx * dx + dy * dy + dz * dz <= radiusSquared;
        }
    }

    private static final class ScheduledExplosionMarker {
        private final UUID sourceId;
        private final UUID worldId;
        private final double x;
        private final double y;
        private final double z;
        private final double radiusSquared;
        private final long expiresAtMillis;

        private ScheduledExplosionMarker(
                final UUID sourceId,
                final Location location,
                final double radius,
                final long expiresAtMillis
        ) {
            this.sourceId = sourceId;
            this.worldId = location.getWorld() == null ? null : location.getWorld().getUID();
            this.x = location.getX();
            this.y = location.getY();
            this.z = location.getZ();
            this.radiusSquared = radius * radius;
            this.expiresAtMillis = expiresAtMillis;
        }

        private boolean isInside(final Location location) {
            if (location.getWorld() == null || worldId == null || !worldId.equals(location.getWorld().getUID())) {
                return false;
            }
            final double dx = location.getX() - x;
            final double dy = location.getY() - y;
            final double dz = location.getZ() - z;
            return dx * dx + dy * dy + dz * dz <= radiusSquared;
        }
    }

    private static final class DropTarget {
        private final Block block;
        private final Inventory inventory;

        private DropTarget(final Block block, final Inventory inventory) {
            this.block = block;
            this.inventory = inventory;
        }
    }
}
