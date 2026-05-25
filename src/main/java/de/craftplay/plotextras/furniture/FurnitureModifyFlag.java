package de.craftplay.plotextras.furniture;

import com.plotsquared.core.configuration.caption.StaticCaption;
import com.plotsquared.core.plot.flag.types.BooleanFlag;

public final class FurnitureModifyFlag extends BooleanFlag<FurnitureModifyFlag> {

    private final String description;

    public FurnitureModifyFlag(final boolean value, final String description) {
        super(value, StaticCaption.of(description));
        this.description = description;
    }

    @Override
    protected FurnitureModifyFlag flagOf(final Boolean value) {
        return new FurnitureModifyFlag(value, description);
    }
}
