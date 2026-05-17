package de.craftplay.plotextras.furniture.flag;

import com.plotsquared.core.configuration.caption.StaticCaption;
import com.plotsquared.core.plot.flag.types.BooleanFlag;

public final class FurnitureInteractFlag extends BooleanFlag<FurnitureInteractFlag> {

    public static final FurnitureInteractFlag FURNITURE_INTERACT_TRUE = new FurnitureInteractFlag(true);
    public static final FurnitureInteractFlag FURNITURE_INTERACT_FALSE = new FurnitureInteractFlag(false);

    private FurnitureInteractFlag(final boolean value) {
        super(value, StaticCaption.of("Erlaubt Gästen, erkannte Datapack-Möbel auf diesem Plot zu benutzen."));
    }

    @Override
    protected FurnitureInteractFlag flagOf(final Boolean value) {
        return Boolean.TRUE.equals(value) ? FURNITURE_INTERACT_TRUE : FURNITURE_INTERACT_FALSE;
    }
}
