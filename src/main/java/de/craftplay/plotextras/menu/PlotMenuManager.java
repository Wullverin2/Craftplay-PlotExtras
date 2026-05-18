package de.craftplay.plotextras.menu;

import de.craftplay.plotextras.CraftplayPlotExtrasPlugin;
import de.craftplay.plotextras.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PlotMenuManager implements Listener {

    private final CraftplayPlotExtrasPlugin plugin;
    private final Map<Integer, MenuButton> buttonsBySlot = new HashMap<>();

    private String title;
    private int size;
    private ItemStack filler;

    public PlotMenuManager(final CraftplayPlotExtrasPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        title = Text.color(plugin.getConfig().getString("menu.title", "&8Plot-Menü"));
        size = normalizeSize(plugin.getConfig().getInt("menu.size", 27));
        filler = createFiller();
        buttonsBySlot.clear();
        loadButtons();
    }

    public void openMainMenu(final Player player) {
        final Inventory inventory = Bukkit.createInventory(new PlotMenuHolder(), size, title);
        if (filler != null) {
            for (int slot = 0; slot < size; slot++) {
                inventory.setItem(slot, filler);
            }
        }

        for (final MenuButton button : buttonsBySlot.values()) {
            if (!canSee(player, button)) {
                continue;
            }
            inventory.setItem(button.getSlot(), createButtonItem(button));
        }

        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof PlotMenuHolder)) {
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getInventory()) {
            return;
        }

        final Player player = (Player) event.getWhoClicked();
        final MenuButton button = buttonsBySlot.get(event.getSlot());
        if (button == null || !canSee(player, button)) {
            return;
        }

        if (button.isCloseInventory()) {
            player.closeInventory();
        }

        for (final String command : button.getCommands()) {
            runCommand(player, command);
        }
    }

    @EventHandler
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PlotMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void loadButtons() {
        final ConfigurationSection section = plugin.getConfig().getConfigurationSection("menu.buttons");
        if (section == null) {
            return;
        }

        for (final String id : section.getKeys(false)) {
            final String path = "menu.buttons." + id + ".";
            final int slot = plugin.getConfig().getInt(path + "slot", -1);
            if (slot < 0 || slot >= size) {
                plugin.getLogger().warning("Menübutton '" + id + "' hat einen ungültigen Slot: " + slot);
                continue;
            }

            final Material material = material(plugin.getConfig().getString(path + "material", "STONE_BUTTON"), Material.STONE_BUTTON);
            final String name = plugin.getConfig().getString(path + "name", "&a" + id);
            final List<String> lore = plugin.getConfig().getStringList(path + "lore");
            final List<String> commands = plugin.getConfig().getStringList(path + "commands");
            final boolean close = plugin.getConfig().getBoolean(path + "close", true);
            final String permission = plugin.getConfig().getString(path + "permission", "");

            buttonsBySlot.put(slot, new MenuButton(id, slot, material, name, lore, commands, close, permission));
        }
    }

    private ItemStack createFiller() {
        if (!plugin.getConfig().getBoolean("menu.filler.enabled", true)) {
            return null;
        }

        final Material material = material(plugin.getConfig().getString("menu.filler.material", "BLACK_STAINED_GLASS_PANE"), Material.BLACK_STAINED_GLASS_PANE);
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(plugin.getConfig().getString("menu.filler.name", "&r")));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createButtonItem(final MenuButton button) {
        final ItemStack item = new ItemStack(button.getMaterial());
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(button.getName()));
            meta.setLore(Text.color(button.getLore()));
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean canSee(final Player player, final MenuButton button) {
        return button.getPermission() == null
                || button.getPermission().trim().isEmpty()
                || player.hasPermission(button.getPermission());
    }

    private void runCommand(final Player player, final String configuredCommand) {
        if (configuredCommand == null || configuredCommand.trim().isEmpty()) {
            return;
        }

        String command = configuredCommand
                .replace("{player}", player.getName())
                .replace("%player%", player.getName())
                .trim();

        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        if (command.toLowerCase(Locale.ROOT).startsWith("console:")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.substring("console:".length()).trim());
            return;
        }

        player.performCommand(command);
    }

    private int normalizeSize(final int configuredSize) {
        int normalized = Math.max(9, Math.min(54, configuredSize));
        if (normalized % 9 != 0) {
            normalized = ((normalized / 9) + 1) * 9;
        }
        return normalized;
    }

    private Material material(final String configuredMaterial, final Material fallback) {
        if (configuredMaterial == null) {
            return fallback;
        }
        final Material material = Material.matchMaterial(configuredMaterial);
        return material == null ? fallback : material;
    }
}
