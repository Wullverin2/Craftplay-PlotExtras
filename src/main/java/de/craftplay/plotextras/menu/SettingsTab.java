package de.craftplay.plotextras.menu;

import java.util.Map;

public final class SettingsTab {

    private final String id;
    private final MenuButton selector;
    private final Map<Integer, MenuButton> buttonsBySlot;

    public SettingsTab(final String id, final MenuButton selector, final Map<Integer, MenuButton> buttonsBySlot) {
        this.id = id;
        this.selector = selector;
        this.buttonsBySlot = buttonsBySlot;
    }

    public String getId() {
        return id;
    }

    public MenuButton getSelector() {
        return selector;
    }

    public Map<Integer, MenuButton> getButtonsBySlot() {
        return buttonsBySlot;
    }
}
