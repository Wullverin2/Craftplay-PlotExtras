package de.craftplay.plotextras.furniture;

import com.plotsquared.core.configuration.caption.StaticCaption;
import com.plotsquared.core.plot.flag.types.BooleanFlag;

public final class PreventCropTrampleFlag extends BooleanFlag<PreventCropTrampleFlag> {

    private final String description;

    public PreventCropTrampleFlag(final boolean value, final String description) {
        super(value, StaticCaption.of(description));
        this.description = description;
    }

    @Override
    protected PreventCropTrampleFlag flagOf(final Boolean value) {
        return new PreventCropTrampleFlag(value, description);
    }
}
