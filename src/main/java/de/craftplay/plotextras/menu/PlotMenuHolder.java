package de.craftplay.plotextras.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class PlotMenuHolder implements InventoryHolder {

    private final String menuId;
    private final int page;
    private final String tabId;

    public PlotMenuHolder(final String menuId) {
        this(menuId, 1, "");
    }

    public PlotMenuHolder(final String menuId, final int page) {
        this(menuId, page, "");
    }

    public PlotMenuHolder(final String menuId, final String tabId) {
        this(menuId, 1, tabId);
    }

    public PlotMenuHolder(final String menuId, final int page, final String tabId) {
        this.menuId = menuId;
        this.page = page;
        this.tabId = tabId == null ? "" : tabId;
    }

    public String getMenuId() {
        return menuId;
    }

    public int getPage() {
        return page;
    }

    public String getTabId() {
        return tabId;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
