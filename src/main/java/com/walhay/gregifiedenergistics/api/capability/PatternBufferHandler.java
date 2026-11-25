package com.walhay.gregifiedenergistics.api.capability;

import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.INotifiableHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

/** Recipe inventory with controller notifications and optional fluid storage. */
public interface PatternBufferHandler extends IItemHandlerModifiable, INotifiableHandler {
	void onContentsChanged();

	/** Refreshes the fluid delegate after the buffer changes shape. */
	default void setFluidDelegate(IMultipleTankHandler fluidTank) {}
}
