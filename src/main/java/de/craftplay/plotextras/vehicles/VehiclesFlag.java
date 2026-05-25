package de.craftplay.plotextras.vehicles;

import com.plotsquared.core.configuration.caption.StaticCaption;
import com.plotsquared.core.plot.flag.types.BooleanFlag;

public final class VehiclesFlag extends BooleanFlag<VehiclesFlag> {

    private final String description;

    public VehiclesFlag(final boolean value, final String description) {
        super(value, StaticCaption.of(description));
        this.description = description;
    }

    @Override
    protected VehiclesFlag flagOf(final Boolean value) {
        return new VehiclesFlag(value, description);
    }
}
