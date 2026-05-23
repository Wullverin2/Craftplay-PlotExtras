package de.craftplay.plotextras.passivewither;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.MinecraftKey;
import de.craftplay.plotextras.CraftplayPlotExtrasPlugin;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ProtocolLibPassiveWitherSoundHook implements PassiveWitherSoundPacketHook {

    private static final int WORLD_EVENT_WITHER_SPAWN = 1023;
    private static final int WORLD_EVENT_BLOCK_BREAK = 2001;
    private static final Set<PacketType> POSITIONED_SOUND_PACKETS = new HashSet<>();

    static {
        POSITIONED_SOUND_PACKETS.add(PacketType.Play.Server.NAMED_SOUND_EFFECT);
        POSITIONED_SOUND_PACKETS.add(PacketType.Play.Server.CUSTOM_SOUND_EFFECT);
    }

    private final CraftplayPlotExtrasPlugin plugin;
    private final PassiveWitherService service;
    private ProtocolManager protocolManager;
    private PacketListener listener;

    public ProtocolLibPassiveWitherSoundHook(
            final CraftplayPlotExtrasPlugin plugin,
            final PassiveWitherService service
    ) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public void enable() {
        protocolManager = ProtocolLibrary.getProtocolManager();
        final List<PacketType> packetTypes = new ArrayList<>();
        addSupportedPacket(packetTypes, PacketType.Play.Server.ENTITY_SOUND);
        addSupportedPacket(packetTypes, PacketType.Play.Server.NAMED_SOUND_EFFECT);
        addSupportedPacket(packetTypes, PacketType.Play.Server.CUSTOM_SOUND_EFFECT);
        addSupportedPacket(packetTypes, PacketType.Play.Server.WORLD_EVENT);
        addSupportedPacket(packetTypes, PacketType.Play.Server.BLOCK_BREAK);

        if (packetTypes.isEmpty()) {
            plugin.getLogger().warning("Passive-Wither-Soundfilter konnte keine unterstuetzten Pakettypen finden.");
            return;
        }

        listener = new PacketAdapter(plugin, ListenerPriority.HIGHEST, packetTypes.toArray(new PacketType[0])) {
            @Override
            public void onPacketSending(final PacketEvent event) {
                handlePacket(event);
            }
        };
        protocolManager.addPacketListener(listener);
    }

    private void addSupportedPacket(final List<PacketType> packetTypes, final PacketType packetType) {
        try {
            if (packetType != null && packetType.isSupported()) {
                packetTypes.add(packetType);
            }
        } catch (final RuntimeException ignored) {
            // ProtocolLib can expose stale packet constants on some server versions.
        }
    }

    @Override
    public void disable() {
        if (protocolManager != null && listener != null) {
            protocolManager.removePacketListener(listener);
        }
        listener = null;
        protocolManager = null;
    }

    private void handlePacket(final PacketEvent event) {
        final Player player = event.getPlayer();
        if (player == null || !service.isPassiveWitherSoundDisabled(player)) {
            return;
        }

        final PacketContainer packet = event.getPacket();
        final PacketType type = packet.getType();
        if (type == PacketType.Play.Server.ENTITY_SOUND && shouldCancelEntitySound(packet)) {
            event.setCancelled(true);
            return;
        }

        if (POSITIONED_SOUND_PACKETS.contains(type) && shouldCancelPositionedSound(player, packet)) {
            event.setCancelled(true);
            return;
        }

        if ((type == PacketType.Play.Server.WORLD_EVENT || type == PacketType.Play.Server.BLOCK_BREAK)
                && shouldCancelWorldEvent(player, packet)) {
            event.setCancelled(true);
        }
    }

    private boolean shouldCancelEntitySound(final PacketContainer packet) {
        final int entityId = readInteger(packet, 0, -1);
        return entityId >= 0 && service.isPassiveWitherEntityId(entityId);
    }

    private boolean shouldCancelPositionedSound(final Player player, final PacketContainer packet) {
        final Sound sound = readSound(packet);
        final String soundKey = readSoundKey(packet);
        final Location location = readSoundLocation(player, packet);
        if (isPassiveWitherSound(sound, soundKey)) {
            return service.shouldSuppressPassiveWitherSound(player, location);
        }
        if (isBlockBreakSound(sound, soundKey)) {
            return service.shouldSuppressPassiveBlockBreakSound(player, location);
        }
        return false;
    }

    private boolean shouldCancelWorldEvent(final Player player, final PacketContainer packet) {
        final int eventId = readInteger(packet, 0, -1);
        final Location location = readBlockPosition(player.getWorld(), packet);
        if (eventId == WORLD_EVENT_WITHER_SPAWN) {
            return service.shouldSuppressPassiveWitherSound(player, location);
        }
        if (eventId == WORLD_EVENT_BLOCK_BREAK) {
            return service.shouldSuppressPassiveBlockBreakSound(player, location);
        }
        return false;
    }

    private Sound readSound(final PacketContainer packet) {
        try {
            if (packet.getSoundEffects().size() > 0) {
                return packet.getSoundEffects().read(0);
            }
        } catch (final RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private String readSoundKey(final PacketContainer packet) {
        try {
            if (packet.getMinecraftKeys().size() > 0) {
                final MinecraftKey key = packet.getMinecraftKeys().read(0);
                return key == null ? null : key.getFullKey().toLowerCase(Locale.ROOT);
            }
        } catch (final RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private int readInteger(final PacketContainer packet, final int index, final int fallback) {
        try {
            if (packet.getIntegers().size() > index) {
                return packet.getIntegers().read(index);
            }
        } catch (final RuntimeException ignored) {
            return fallback;
        }
        return fallback;
    }

    private Location readSoundLocation(final Player player, final PacketContainer packet) {
        try {
            if (packet.getDoubles().size() >= 3) {
                return new Location(
                        player.getWorld(),
                        packet.getDoubles().read(0),
                        packet.getDoubles().read(1),
                        packet.getDoubles().read(2)
                );
            }
        } catch (final RuntimeException ignored) {
            // Older packet wrappers expose sound coordinates as fixed-point integers.
        }

        try {
            if (packet.getIntegers().size() >= 3) {
                return new Location(
                        player.getWorld(),
                        packet.getIntegers().read(0) / 8.0D,
                        packet.getIntegers().read(1) / 8.0D,
                        packet.getIntegers().read(2) / 8.0D
                );
            }
        } catch (final RuntimeException ignored) {
            return null;
        }

        return readBlockPosition(player.getWorld(), packet);
    }

    private Location readBlockPosition(final World world, final PacketContainer packet) {
        if (world == null) {
            return null;
        }
        try {
            if (packet.getBlockPositionModifier().size() > 0) {
                final BlockPosition position = packet.getBlockPositionModifier().read(0);
                return position == null ? null : position.toLocation(world).add(0.5D, 0.5D, 0.5D);
            }
        } catch (final RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private boolean isPassiveWitherSound(final Sound sound, final String soundKey) {
        if (sound == Sound.ENTITY_GENERIC_EXPLODE) {
            return true;
        }
        if (sound != null && sound.name().startsWith("ENTITY_WITHER_")) {
            return true;
        }
        return soundKey != null
                && (soundKey.startsWith("minecraft:entity.wither.")
                || soundKey.equals("minecraft:entity.generic.explode"));
    }

    private boolean isBlockBreakSound(final Sound sound, final String soundKey) {
        if (sound != null && sound.name().startsWith("BLOCK_") && sound.name().endsWith("_BREAK")) {
            return true;
        }
        return soundKey != null && soundKey.startsWith("minecraft:block.") && soundKey.endsWith(".break");
    }
}
