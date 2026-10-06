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
import java.util.Collection;
import java.util.Map;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(OpticalPipeNet.class)
public abstract class OpticalPipeNetMixin extends PipeNet<OpticalPipeProperties> {

	@Shadow(remap = false)
	@Final
	private Map<BlockPos, OpticalRoutePath> NET_DATA;

	@Shadow(remap = false)
	public abstract OpticalRoutePath getNetData(BlockPos pipePos, EnumFacing facing);

	private OpticalPipeNetMixin(WorldPipeNet<OpticalPipeProperties, ? extends PipeNet<OpticalPipeProperties>> world) {
		super(world);
	}

	@Override
	protected void onNodeConnectionsUpdate() {
		super.onNodeConnectionsUpdate();
		notifyRecipeHandlers();
	}

	@Override
	public void onPipeConnectionsUpdate() {
		notifyRecipeHandlers();
		super.onPipeConnectionsUpdate();
		notifyRecipeHandlers();
	}

	@Override
	public void onNeighbourUpdate(BlockPos pos) {
		notifyRecipeHandlers();
		super.onNeighbourUpdate(pos);
		notifyRecipeHandlers();
	}

	private void notifyRecipeHandlers() {
		Collection<INetRecipeHandler> seen = new ArrayList<>();
		getAllNodes().keySet().forEach(pos -> {
			for (EnumFacing facing : EnumFacing.values()) {
				addRecipeHandler(
						seen,
						getNetData(pos, facing) instanceof IOpticalRouteAccessor accessor
								? accessor.getDataHandler()
								: null);

				TileEntity tile = getWorldData().getTileEntity(pos.offset(facing));
				if (!(tile instanceof TileEntityOpticalPipe)) {
					addRecipeHandler(
							seen,
							tile == null
									? null
									: tile.getCapability(
											GregifiedEnergisticsCapabilities.CAPABILITY_RECIPE_HANDLER,
											facing.getOpposite()));
				}
			}
		});
	}

	private void addRecipeHandler(Collection<INetRecipeHandler> seen, Object handler) {
		if (handler instanceof INetRecipeHandler recipeHandler && seen.add(recipeHandler)) {
			recipeHandler.onRecipesUpdate(seen);
		}
	}
}
