package com.walhay.gregifiedenergistics.mixins.gtceu.pipenet.net;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.walhay.gregifiedenergistics.api.capability.GregifiedEnergisticsCapabilities;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.common.pipelike.optical.net.OpticalNetWalker;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(OpticalNetWalker.class)
public class OpticalNetWalkerMixin {

	@WrapOperation(
			method = "checkNeighbour",
			at =
					@At(
							value = "INVOKE",
							target =
									"Lnet/minecraft/tileentity/TileEntity;hasCapability(Lnet/minecraftforge/common/capabilities/Capability;Lnet/minecraft/util/EnumFacing;)Z"),
			remap = false)
	private boolean allowRecipeEndpoint(
			TileEntity tile, Capability<?> capability, EnumFacing facing, Operation<Boolean> original) {
		return original.call(tile, capability, facing)
				|| capability == GregtechTileCapabilities.CABABILITY_COMPUTATION_PROVIDER
						&& tile.hasCapability(GregifiedEnergisticsCapabilities.CAPABILITY_RECIPE_HANDLER, facing);
	}
}
