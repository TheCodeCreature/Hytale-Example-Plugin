package com.UnobstructedThirdPerson.resourcecollection;

public final class ResourceConstants {

    /**
     * Multiplier applied to natural resource drops and recipe input scaling.
     *
     * Natural blocks drop RESOURCE_MULTIPLIER × their normal quantity.
     * Recipe input quantities are scaled by RESOURCE_MULTIPLIER / output quantity,
     * so breaking a crafted block returns the correct proportion of base resources.
     */
    public static final int RESOURCE_MULTIPLIER = 12;

    private ResourceConstants() {}
}
