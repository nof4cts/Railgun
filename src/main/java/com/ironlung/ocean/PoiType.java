package com.ironlung.ocean;

public enum PoiType {
    WRECK("Wreckage", true, 0.42, 0.10),
    RIBCAGE("Skeletal remains", true, 0.80, 0.05),
    GROWTH("Biological formation", true, 0.55, 0.55),
    SPIRE("Spire formation", true, 0.45, 0.15),
    MONOLITH("Unclassified structure", true, 0.12, 0.0),
    CARCASS("Large carcass", true, 0.50, 0.45),
    // Uncatalogued: never listed on the survey chart.
    EYE_ROCK("Uncatalogued anomaly", false, 0.30, 0.25),
    SKULL("Uncatalogued anomaly", false, 0.75, 0.05),
    FIGURE("Uncatalogued anomaly", false, 0.92, 0.0);

    public final String displayName;
    public final boolean catalogued;
    /** Base brightness of the material under the floodlight. */
    public final double albedo;
    /** 0..1, how much the material pushes the palette toward red/flesh tones. */
    public final double flesh;

    PoiType(String displayName, boolean catalogued, double albedo, double flesh) {
        this.displayName = displayName;
        this.catalogued = catalogued;
        this.albedo = albedo;
        this.flesh = flesh;
    }

    public static final PoiType[] CATALOGUED = {WRECK, RIBCAGE, GROWTH, SPIRE, MONOLITH, CARCASS};
    public static final PoiType[] HIDDEN = {EYE_ROCK, SKULL, FIGURE, GROWTH, WRECK};
}
