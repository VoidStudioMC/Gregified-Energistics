package com.walhay.gregifiedenergistics.mixins.gtceu.metatileentities;

import com.walhay.gregifiedenergistics.api.capability.PatternBufferDualHandler;
import com.walhay.gregifiedenergistics.api.capability.SegmentItemHandlerList;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
	@Unique private List<PatternBufferDualHandler> gregified$buffers = Collections.emptyList();

	@Unique private long[] gregified$fluidRevisions = new long[0];

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
		gregified$buffers = new ArrayList<>();
		if (this.inputInventory instanceof ItemHandlerList list) {
			Collection<IItemHandler> handlers = list.getBackingHandlers();
			for (IItemHandler handler : handlers) {
				if (handler instanceof PatternBufferDualHandler buffer) gregified$buffers.add(buffer);
			}
			if (!gregified$buffers.isEmpty()) {
				this.inputInventory = new SegmentItemHandlerList(handlers);
			}
		}
		gregified$fluidRevisions = new long[gregified$buffers.size()];
		for (int i = 0; i < gregified$buffers.size(); i++)
			gregified$fluidRevisions[i] = gregified$buffers.get(i).getFluidRevision();
	}

	@Inject(method = "resetTileAbilities", at = @At("TAIL"))
	private void clearBufferHandlers(CallbackInfo ci) {
		gregified$buffers = Collections.emptyList();
		gregified$fluidRevisions = new long[0];
	}

	@Inject(method = "getInputFluidInventory", at = @At("HEAD"))
	private void refreshPatternBufferFluids(CallbackInfoReturnable<IMultipleTankHandler> cir) {
		// Pattern-buffer tanks are created when patterns change, after the controller initially forms.
		boolean changed = false;
		for (int i = 0; i < gregified$buffers.size(); i++) {
			long revision = gregified$buffers.get(i).getFluidRevision();
			if (revision != gregified$fluidRevisions[i]) {
				gregified$fluidRevisions[i] = revision;
				changed = true;
			}
		}
		if (changed) {
			this.extendedFluidInputs = this.extendedImportFluidList(this.inputFluidInventory);
		}
	}
}
