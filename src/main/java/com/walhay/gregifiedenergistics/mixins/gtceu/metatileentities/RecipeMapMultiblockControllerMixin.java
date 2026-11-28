package com.walhay.gregifiedenergistics.mixins.gtceu.metatileentities;

import com.walhay.gregifiedenergistics.api.capability.IPatternBufferHandler;
import com.walhay.gregifiedenergistics.api.capability.SegmentItemHandlerList;
import com.walhay.gregifiedenergistics.mixins.interfaces.IPatternBufferController;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import java.util.Collection;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RecipeMapMultiblockController.class, remap = false)
public abstract class RecipeMapMultiblockControllerMixin implements IPatternBufferController {
	@Shadow
	protected IItemHandlerModifiable inputInventory;

	@Shadow
	protected IMultipleTankHandler extendedFluidInputs;

	@Shadow
	protected IMultipleTankHandler inputFluidInventory;

	@Shadow
	protected abstract IMultipleTankHandler extendedImportFluidList(IMultipleTankHandler fluids);

	@Inject(method = "initializeAbilities", at = @At("TAIL"))
	private void useDynamicItemSlots(CallbackInfo ci) {
		if (!(inputInventory instanceof ItemHandlerList list)) return;
		Collection<IItemHandler> handlers = list.getBackingHandlers();
		if (handlers.stream().anyMatch(IPatternBufferHandler.class::isInstance)) {
			inputInventory = new SegmentItemHandlerList(handlers);
		}
	}

	@Override
	@Unique public void refreshPatternBufferFluids() {
		extendedFluidInputs = extendedImportFluidList(inputFluidInventory);
	}
}
