package com.walhay.gregifiedenergistics.api.capability;

/** A hatch or bus that connects to an optical pipe as a source or consumer of recipe data. */
public interface IOpticalNetRecipeHandler extends INetRecipeHandler {

	boolean isTransmitter();
}
