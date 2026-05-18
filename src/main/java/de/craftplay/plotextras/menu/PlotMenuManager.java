package de.craftplay.plotextras.menu;

import de.craftplay.plotextras.CraftplayPlotExtrasPlugin;
import de.craftplay.plotextras.language.LanguageManager;
import de.craftplay.plotextras.plotsquared.PlotSquaredFlagService;
import de.craftplay.plotextras.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PlotMenuManager implements Listener {

    private final CraftplayPlotExtrasPlugin plugin;
    private final LanguageManager languageManager;
    private final PlotSquaredFlagService flagService;
    private final Map<Integer, MenuButton> mainButtonsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> flagButtonsBySlot = new HashMap<>();
    private final Map<Integer, Map<Integer, FlagMenuEntry>> flagsByPageAndSlot = new HashMap<>();

    private String mainTitle;
    private int mainSize;
    private ItemStack mainFiller;
    private boolean mainLoaded;
    private String flagsTitle;
    private int flagsSize;
    private ItemStack flagsFiller;
    private boolean flagsLoaded;
    private String statusEnabled;
    private String statusDisabled;
    private long reopenDelayTicks;

    public PlotMenuManager(
            final CraftplayPlotExtrasPlugin plugin,
            final LanguageManager languageManager,
            final PlotSquaredFlagService flagService
    ) {
        this.plugin = plugin;
        this.languageManager = languageManager;
        this.flagService = flagService;
    }

    public void reload() {
        mainButtonsBySlot.clear();
        flagButtonsBySlot.clear();
        flagsByPageAndSlot.clear();
        mainLoaded = false;
        flagsLoaded = false;

        final YamlConfiguration menuConfig = loadMenuConfig(plugin.getConfig().getString("gui.main-menu", "main.yml"));
        if (menuConfig == null) {
            mainTitle = Text.color("&8Plot-Menü");
            mainSize = 27;
            mainFiller = null;
            return;
        }

        mainTitle = Text.color(menuConfig.getString("title", "&8Plot-Menü"));
        mainSize = normalizeSize(menuConfig.getInt("size", 27));
        mainFiller = createFiller(menuConfig);
        loadButtons(menuConfig, mainButtonsBySlot, mainSize);
        mainLoaded = true;

        final YamlConfiguration flagsConfig = loadMenuConfig(plugin.getConfig().getString("gui.flags-menu", "flags.yml"));
        if (flagsConfig == null) {
            flagsTitle = Text.color("&8Plot-Flags");
            flagsSize = 54;
            flagsFiller = null;
            return;
        }

        flagsTitle = Text.color(flagsConfig.getString("title", "&8Plot-Flags"));
        flagsSize = normalizeSize(flagsConfig.getInt("size", 54));
        flagsFiller = createFiller(flagsConfig);
        statusEnabled = flagsConfig.getString("status.enabled", "&aAktiv");
        statusDisabled = flagsConfig.getString("status.disabled", "&cInaktiv");
        reopenDelayTicks = Math.max(1L, flagsConfig.getLong("reopen-delay-ticks", 2L));
        loadButtons(flagsConfig, flagButtonsBySlot, flagsSize);
        loadFlags(flagsConfig);
        flagsLoaded = true;
    }

    public void openMainMenu(final Player player) {
        if (!mainLoaded) {
            languageManager.send(player, "menu-missing");
            return;
        }

        final Inventory inventory = Bukkit.createInventory(new PlotMenuHolder("main"), mainSize, mainTitle);
        if (mainFiller != null) {
            for (int slot = 0; slot < mainSize; slot++) {
                inventory.setItem(slot, mainFiller);
            }
        }

        for (final MenuButton button : mainButtonsBySlot.values()) {
            if (!canSee(player, button)) {
                continue;
            }
            inventory.setItem(button.getSlot(), createButtonItem(button));
        }

        player.openInventory(inventory);
    }

    public void openFlagsMenu(final Player player) {
        openFlagsMenu(player, 1);
    }

    public void openFlagsMenu(final Player player, final int page) {
        if (!flagsLoaded) {
            languageManager.send(player, "menu-missing");
            return;
        }
        if (!flagService.isOnPlot(player)) {
            languageManager.send(player, "no-plot");
            return;
        }

        final int normalizedPage = Math.max(1, page);
        final Inventory inventory = Bukkit.createInventory(new PlotMenuHolder("flags", normalizedPage), flagsSize, flagsTitle);
        if (flagsFiller != null) {
            for (int slot = 0; slot < flagsSize; slot++) {
                inventory.setItem(slot, flagsFiller);
            }
        }

        for (final MenuButton button : flagButtonsBySlot.values()) {
            if (!canSee(player, button)) {
                continue;
            }
            inventory.setItem(button.getSlot(), createButtonItem(button));
        }

        final Map<Integer, FlagMenuEntry> flagsBySlot = flagsByPageAndSlot.getOrDefault(normalizedPage, Collections.emptyMap());
        for (final FlagMenuEntry flagEntry : flagsBySlot.values()) {
            if (!canSeeFlag(player, flagEntry)) {
                continue;
            }
            inventory.setItem(flagEntry.getSlot(), createFlagItem(player, flagEntry));
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
        final PlotMenuHolder holder = (PlotMenuHolder) event.getInventory().getHolder();
        if ("flags".equalsIgnoreCase(holder.getMenuId())) {
            handleFlagsClick(player, holder.getPage(), event.getSlot());
            return;
        }

        final MenuButton button = mainButtonsBySlot.get(event.getSlot());
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

    private void loadButtons(final YamlConfiguration menuConfig, final Map<Integer, MenuButton> target, final int menuSize) {
        final ConfigurationSection section = menuConfig.getConfigurationSection("buttons");
        if (section == null) {
            return;
        }

        for (final String id : section.getKeys(false)) {
            final String path = "buttons." + id + ".";
            final int slot = menuConfig.getInt(path + "slot", -1);
            if (slot < 0 || slot >= menuSize) {
                plugin.getLogger().warning("Menübutton '" + id + "' hat einen ungültigen Slot: " + slot);
                continue;
            }

            final Material material = material(menuConfig.getString(path + "material", "STONE_BUTTON"), Material.STONE_BUTTON);
            final String name = menuConfig.getString(path + "name", "&a" + id);
            final List<String> lore = menuConfig.getStringList(path + "lore");
            final List<String> commands = menuConfig.getStringList(path + "commands");
            final boolean close = menuConfig.getBoolean(path + "close", true);
            final String permission = menuConfig.getString(path + "permission", "");

            target.put(slot, new MenuButton(id, slot, material, name, lore, commands, close, permission));
        }
    }

    private void loadFlags(final YamlConfiguration menuConfig) {
        final ConfigurationSection section = menuConfig.getConfigurationSection("flags");
        if (section == null) {
            return;
        }

        for (final String flag : section.getKeys(false)) {
            final String path = "flags." + flag + ".";
            final int page = Math.max(1, menuConfig.getInt(path + "page", 1));
            final int slot = menuConfig.getInt(path + "slot", -1);
            if (slot < 0 || slot >= flagsSize) {
                plugin.getLogger().warning("Flag '" + flag + "' hat einen ungültigen Slot: " + slot);
                continue;
            }
            if (!flagService.isBooleanFlag(flag)) {
                plugin.getLogger().warning("Flag '" + flag + "' ist keine bekannte Boolean-Flag und wird übersprungen.");
                continue;
            }

            final Material enabledMaterial = material(menuConfig.getString(path + "enabled-material",
                    menuConfig.getString(path + "material", "LIME_DYE")), Material.LIME_DYE);
            final Material disabledMaterial = material(menuConfig.getString(path + "disabled-material",
                    menuConfig.getString(path + "material", "GRAY_DYE")), Material.GRAY_DYE);
            final String name = menuConfig.getString(path + "name", "&a" + flag);
            final List<String> lore = menuConfig.getStringList(path + "lore");
            final String permission = menuConfig.getString(path + "permission", "");
            flagsByPageAndSlot
                    .computeIfAbsent(page, ignored -> new HashMap<>())
                    .put(slot, new FlagMenuEntry(flag, page, slot, enabledMaterial, disabledMaterial, name, lore, permission));
        }
    }

    private ItemStack createFiller(final YamlConfiguration menuConfig) {
        if (!menuConfig.getBoolean("filler.enabled", true)) {
            return null;
        }

        final Material material = material(menuConfig.getString("filler.material", "BLACK_STAINED_GLASS_PANE"), Material.BLACK_STAINED_GLASS_PANE);
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(menuConfig.getString("filler.name", "&r")));
            item.setItemMeta(meta);
        }
        return item;
    }

    private YamlConfiguration loadMenuConfig(final String menuFile) {
        final String language = languageManager.getDefaultLanguage();
        final File localizedFile = new File(plugin.getDataFolder(), "gui/" + language + "/" + menuFile);
        if (localizedFile.exists()) {
            return YamlConfiguration.loadConfiguration(localizedFile);
        }

        final File fallbackFile = new File(plugin.getDataFolder(), "gui/de/" + menuFile);
        if (fallbackFile.exists()) {
            plugin.getLogger().warning("GUI-Datei für Sprache '" + language + "' fehlt. Nutze Deutsch als Fallback.");
            return YamlConfiguration.loadConfiguration(fallbackFile);
        }

        plugin.getLogger().warning("GUI-Datei fehlt: " + localizedFile.getPath());
        return null;
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

    private ItemStack createFlagItem(final Player player, final FlagMenuEntry flagEntry) {
        final boolean enabled = flagService.isFlagEnabled(player, flagEntry.getFlag());
        final ItemStack item = new ItemStack(enabled ? flagEntry.getEnabledMaterial() : flagEntry.getDisabledMaterial());
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            final Map<String, String> placeholders = flagPlaceholders(flagEntry, enabled);
            meta.setDisplayName(Text.color(applyPlaceholders(flagEntry.getName(), placeholders)));
            meta.setLore(Text.color(applyPlaceholders(flagEntry.getLore(), placeholders)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean canSee(final Player player, final MenuButton button) {
        return button.getPermission() == null
                || button.getPermission().trim().isEmpty()
                || player.hasPermission(button.getPermission());
    }

    private boolean canSeeFlag(final Player player, final FlagMenuEntry flagEntry) {
        if (flagEntry.getPermission() != null && !flagEntry.getPermission().trim().isEmpty()) {
            return player.hasPermission(flagEntry.getPermission());
        }
        return flagService.hasAnyFlagPermission(player, flagEntry.getFlag());
    }

    private void handleFlagsClick(final Player player, final int page, final int slot) {
        final MenuButton button = flagButtonsBySlot.get(slot);
        if (button != null) {
            if (!canSee(player, button)) {
                return;
            }
            if (button.isCloseInventory()) {
                player.closeInventory();
            }
            for (final String command : button.getCommands()) {
                runCommand(player, command);
            }
            return;
        }

        final Map<Integer, FlagMenuEntry> flagsBySlot = flagsByPageAndSlot.getOrDefault(page, Collections.emptyMap());
        final FlagMenuEntry flagEntry = flagsBySlot.get(slot);
        if (flagEntry == null || !canSeeFlag(player, flagEntry)) {
            return;
        }
        final boolean current = flagService.isFlagEnabled(player, flagEntry.getFlag());
        final boolean target = !current;
        if (!flagService.hasTogglePermission(player, flagEntry.getFlag(), target)) {
            languageManager.send(player, "flag-no-permission");
            return;
        }

        flagService.toggleBooleanFlag(player, flagEntry.getFlag());
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("flag", Text.color(flagEntry.getName()));
        placeholders.put("status", Text.color(target ? statusEnabled : statusDisabled));
        languageManager.send(player, "flag-toggled", placeholders);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                openFlagsMenu(player, page);
            }
        }, reopenDelayTicks);
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

        if (command.toLowerCase(Locale.ROOT).startsWith("open-menu:")) {
            final String menuId = command.substring("open-menu:".length()).trim();
            if (menuId.toLowerCase(Locale.ROOT).startsWith("flags")) {
                openFlagsMenu(player, menuPage(menuId));
            } else if ("main".equalsIgnoreCase(menuId)) {
                openMainMenu(player);
            }
            return;
        }

        player.performCommand(command);
    }

    private Map<String, String> flagPlaceholders(final FlagMenuEntry flagEntry, final boolean enabled) {
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("flag", flagEntry.getFlag());
        placeholders.put("status", enabled ? statusEnabled : statusDisabled);
        placeholders.put("next_status", enabled ? statusDisabled : statusEnabled);
        return placeholders;
    }

    private String applyPlaceholders(final String text, final Map<String, String> placeholders) {
        String replaced = text == null ? "" : text;
        for (final Map.Entry<String, String> entry : placeholders.entrySet()) {
            replaced = replaced.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return replaced;
    }

    private List<String> applyPlaceholders(final List<String> lines, final Map<String, String> placeholders) {
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyList();
        }
        final List<String> replaced = new java.util.ArrayList<>();
        for (final String line : lines) {
            replaced.add(applyPlaceholders(line, placeholders));
        }
        return replaced;
    }

    private int menuPage(final String menuId) {
        final int separator = menuId.indexOf(':');
        if (separator < 0 || separator >= menuId.length() - 1) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(menuId.substring(separator + 1)));
        } catch (final NumberFormatException exception) {
            return 1;
        }
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
