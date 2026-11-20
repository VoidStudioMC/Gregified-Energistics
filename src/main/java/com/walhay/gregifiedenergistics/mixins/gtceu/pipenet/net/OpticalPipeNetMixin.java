package com.walhay.gregifiedenergistics.mixins.gtceu.pipenet.net;

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
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OpticalPipeNet.class)
public abstract class OpticalPipeNetMixin extends PipeNet<OpticalPipeProperties> {

	@Shadow(remap = false)
	@Final
	private Map<BlockPos, OpticalRoutePath> NET_DATA;

	@Unique private boolean gregified$notifying;

	private OpticalPipeNetMixin(WorldPipeNet<OpticalPipeProperties, ? extends PipeNet<OpticalPipeProperties>> world) {
		super(world);
	}

	@Override
	protected void onNodeConnectionsUpdate() {
		super.onNodeConnectionsUpdate();
		notifyRecipeHandlers();
	}

	@Inject(
			method = {"onPipeConnectionsUpdate", "onNeighbourUpdate"},
			at = @At("HEAD"),
			remap = false)
	private void notifyConnectionChanges(CallbackInfo ci) {
		notifyRecipeHandlers();
	}

	private void notifyRecipeHandlers() {
		if (gregified$notifying || getWorldData() == null || getWorldData().isRemote) return;
		gregified$notifying = true;
		try {
			Set<INetRecipeHandler> targets = INetRecipeHandler.newTraversal();
			// Include previously routed endpoints so disconnections invalidate their recipes too.
			for (OpticalRoutePath path : new ArrayList<>(NET_DATA.values())) {
				if (path instanceof IOpticalRouteAccessor accessor && accessor.getDataHandler() != null)
					targets.add(accessor.getDataHandler());
			}
			NET_DATA.clear();
			// Do not build routes for arbitrary facings: GregTech's route cache is keyed only by pipe position.
			for (BlockPos pos : new ArrayList<>(getAllNodes().keySet())) {
				for (EnumFacing facing : EnumFacing.values()) {
					TileEntity tile = getWorldData().getTileEntity(pos.offset(facing));
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
