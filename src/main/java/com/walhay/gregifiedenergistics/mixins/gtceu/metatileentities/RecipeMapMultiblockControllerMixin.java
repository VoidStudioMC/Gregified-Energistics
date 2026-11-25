package com.walhay.gregifiedenergistics.mixins.gtceu.metatileentities;

import com.walhay.gregifiedenergistics.api.capability.PatternBufferDualHandler;
import com.walhay.gregifiedenergistics.api.capability.PatternBufferItemHandler;
import com.walhay.gregifiedenergistics.api.capability.SegmentItemHandlerList;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** RecipeMapMultiblockControllerMixin */
@Mixin(value = RecipeMapMultiblockController.class, remap = false)
public abstract class RecipeMapMultiblockControllerMixin {
	@Unique private Map<PatternBufferDualHandler, List<IMultipleTankHandler.ITankEntry>> gregified$bufferTanks =
			Collections.emptyMap();

	@Shadow
	protected IItemHandlerModifiable inputInventory;

	@Shadow
	protected IMultipleTankHandler extendedFluidInputs;

	@Shadow
	protected IMultipleTankHandler inputFluidInventory;

	@Shadow
	protected abstract IMultipleTankHandler extendedImportFluidList(IMultipleTankHandler fluids);

	@Inject(method = "initializeAbilities", at = @At("TAIL"))
	public void createSegmentHandlerList(CallbackInfo ci) {
		gregified$bufferTanks = new IdentityHashMap<>();
		if (this.inputInventory instanceof ItemHandlerList list) {
			Collection<IItemHandler> handlers = list.getBackingHandlers();
			boolean hasDynamicItems = false;
			for (IItemHandler handler : handlers) {
				if (handler instanceof PatternBufferItemHandler) hasDynamicItems = true;
				if (handler instanceof PatternBufferDualHandler buffer)
					gregified$bufferTanks.put(buffer, buffer.getFluidTanks());
			}
			if (hasDynamicItems || !gregified$bufferTanks.isEmpty()) {
				this.inputInventory = new SegmentItemHandlerList(handlers);
			}
		}
	}

	@Inject(method = "resetTileAbilities", at = @At("TAIL"))
	private void clearBufferHandlers(CallbackInfo ci) {
		gregified$bufferTanks = Collections.emptyMap();
	}

	@Inject(method = "getInputFluidInventory", at = @At("HEAD"))
	private void refreshPatternBufferFluids(CallbackInfoReturnable<IMultipleTankHandler> cir) {
		// A new tank list replaces the old delegates even when the number of tanks stays the same.
		boolean changed = false;
		for (var entry : gregified$bufferTanks.entrySet()) {
			List<IMultipleTankHandler.ITankEntry> tanks = entry.getKey().getFluidTanks();
			if (tanks != entry.getValue()) {
				entry.setValue(tanks);
				changed = true;
			}
		}
		if (changed) {
			this.extendedFluidInputs = this.extendedImportFluidList(this.inputFluidInventory);
		}
	}
}
