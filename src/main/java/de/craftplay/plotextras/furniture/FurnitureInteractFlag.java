package de.craftplay.plotextras.furniture;

import com.plotsquared.core.configuration.caption.StaticCaption;
import com.plotsquared.core.plot.flag.types.BooleanFlag;

public final class FurnitureInteractFlag extends BooleanFlag<FurnitureInteractFlag> {

    private final String description;

    public FurnitureInteractFlag(final boolean value, final String description) {
        super(value, StaticCaption.of(description));
        this.description = description;
    }

    @Override
    protected FurnitureInteractFlag flagOf(final Boolean value) {
        return new FurnitureInteractFlag(value, description);
    }
}
