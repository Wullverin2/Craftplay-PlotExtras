package de.craftplay.plotextras.furniture;

import com.plotsquared.core.configuration.caption.StaticCaption;
import com.plotsquared.core.plot.flag.types.BooleanFlag;

public final class FurnitureSitFlag extends BooleanFlag<FurnitureSitFlag> {

    private final String description;

    public FurnitureSitFlag(final boolean value, final String description) {
        super(value, StaticCaption.of(description));
        this.description = description;
    }

    @Override
    protected FurnitureSitFlag flagOf(final Boolean value) {
        return new FurnitureSitFlag(value, description);
    }
}
