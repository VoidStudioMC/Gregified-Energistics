package com.walhay.gregifiedenergistics.mixins.gtceu.pipenet.net;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.walhay.gregifiedenergistics.api.capability.GregifiedEnergisticsCapabilities;
import com.walhay.gregifiedenergistics.api.capability.INetRecipeHandler;
import com.walhay.gregifiedenergistics.mixins.interfaces.IOpticalRouteAccessor;
import gregtech.api.pipenet.PipeNet;
import gregtech.api.pipenet.WorldPipeNet;
import gregtech.common.pipelike.optical.OpticalPipeProperties;
import gregtech.common.pipelike.optical.net.OpticalPipeNet;
import gregtech.common.pipelike.optical.net.OpticalRoutePath;
import gregtech.common.pipelike.optical.tile.TileEntityOpticalPipe;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(OpticalPipeNet.class)
public abstract class OpticalPipeNetMixin extends PipeNet<OpticalPipeProperties> {

	@Unique private boolean gregified$notifying;

	private OpticalPipeNetMixin(WorldPipeNet<OpticalPipeProperties, ? extends PipeNet<OpticalPipeProperties>> world) {
		super(world);
	}

	@Override
	protected void onNodeConnectionsUpdate() {
		super.onNodeConnectionsUpdate();
		onPipeConnectionsUpdate();
	}

	@WrapOperation(
			method = {"onPipeConnectionsUpdate", "onNeighbourUpdate"},
			at = @At(value = "INVOKE", target = "Ljava/util/Map;clear()V"),
			require = 2,
			remap = false)
	private void notifyRecipeHandlersOnCacheClear(Map<BlockPos, OpticalRoutePath> routes, Operation<Void> clear) {
		World world = getWorldData();
		if (gregified$notifying || world == null || world.isRemote) {
			clear.call(routes);
			return;
		}
		gregified$notifying = true;
		try {
			Set<INetRecipeHandler> targets = INetRecipeHandler.newTraversal();
			// Include previously routed endpoints so disconnections invalidate their recipes too.
			for (OpticalRoutePath path : new ArrayList<>(routes.values())) {
				if (path instanceof IOpticalRouteAccessor accessor) {
					INetRecipeHandler handler = accessor.getDataHandler();
					if (handler != null) targets.add(handler);
				}
			}
			clear.call(routes);
			// Do not build routes for arbitrary facings: GregTech's route cache is keyed only by pipe position.
			for (BlockPos pos : new ArrayList<>(getAllNodes().keySet())) {
				for (EnumFacing facing : EnumFacing.values()) {
					TileEntity tile = world.getTileEntity(pos.offset(facing));
					if (tile == null || tile instanceof TileEntityOpticalPipe) continue;
					INetRecipeHandler handler = tile.getCapability(
							GregifiedEnergisticsCapabilities.CAPABILITY_RECIPE_HANDLER, facing.getOpposite());
					if (handler != null) targets.add(handler);
				}
			}
			Set<INetRecipeHandler> seen = INetRecipeHandler.newTraversal();
			for (INetRecipeHandler target : targets) {
				if (!seen.contains(target)) target.onRecipesUpdate(seen);
			}
		} finally {
			gregified$notifying = false;
		}
	}
}
