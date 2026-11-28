package com.walhay.gregifiedenergistics.api.capability;

import gregtech.api.capability.DualHandler;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.impl.GhostCircuitItemStackHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;

/** Exposes buffered items, the ghost circuit, and fluid tanks as multiblock recipe inputs. */
public class PatternBufferDualHandler extends DualHandler implements PatternBufferHandler {

	private List<IMultipleTankHandler.ITankEntry> tanks = Collections.emptyList();

	public PatternBufferDualHandler(
			@NotNull IItemHandlerModifiable itemDelegate,
			GhostCircuitItemStackHandler circuitInventory,
			@NotNull IMultipleTankHandler fluidTank) {
		super(new SegmentItemHandlerList(itemDelegate, circuitInventory), fluidTank, false);
		setFluidDelegate(fluidTank);
	}

	@Override
	public void setFluidDelegate(IMultipleTankHandler fluidTank) {
		this.fluidDelegate = fluidTank;
		List<IMultipleTankHandler.ITankEntry> replacement = new ArrayList<>(fluidTank.getTanks());
		for (var entry : fluidTank.getFluidTanks()) {
			replacement.add(new DualEntry(this, entry));
		}
		this.tanks = Collections.unmodifiableList(replacement);
	}

	@Override
	public @NotNull List<IMultipleTankHandler.ITankEntry> getFluidTanks() {
		return tanks;
	}

	@Override
	public @NotNull IMultipleTankHandler.ITankEntry getTankAt(int index) {
		return tanks.get(index);
	}
}
