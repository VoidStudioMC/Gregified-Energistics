package com.walhay.gregifiedenergistics.mixins.gtceu.pipenet.net;

import com.walhay.gregifiedenergistics.api.capability.INetRecipeHandler;
import com.walhay.gregifiedenergistics.api.capability.IOpticalNetRecipeHandler;
import com.walhay.gregifiedenergistics.mixins.interfaces.IOpticalRouteAccessor;
import gregtech.api.recipes.Recipe;
import gregtech.common.pipelike.optical.net.OpticalNetHandler;
import gregtech.common.pipelike.optical.net.OpticalPipeNet;
import gregtech.common.pipelike.optical.net.OpticalRoutePath;
import gregtech.common.pipelike.optical.tile.TileEntityOpticalPipe;
import java.util.Collection;
import java.util.Set;
import net.minecraft.util.EnumFacing;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(OpticalNetHandler.class)
public abstract class OpticalNetHandlerMixin implements INetRecipeHandler {
	@Shadow(remap = false)
	private TileEntityOpticalPipe pipe;

	@Shadow(remap = false)
	@Final
	private EnumFacing facing;

	@Shadow(remap = false)
	private OpticalPipeNet net;

	@Invoker(remap = false)
	protected abstract boolean callIsNetInvalidForTraversal();

	@Invoker(remap = false)
	protected abstract void callSetPipesActive();

	@Override
	@Unique public void onRecipesTransferred() {
		if (!callIsNetInvalidForTraversal()) callSetPipesActive();
	}

	@Override
	@Unique public void onRecipesUpdate(Set<INetRecipeHandler> seen) {
		if (!seen.add(this)) return;
		IOpticalNetRecipeHandler handler = getRecipeHandler();
		if (handler != null && !handler.isTransmitter()) handler.onRecipesUpdate(seen);
	}

	@Override
	@Unique public Collection<Recipe> getRecipes(Set<INetRecipeHandler> seen) {
		if (!seen.add(this)) return null;
		IOpticalNetRecipeHandler handler = getRecipeHandler();
		if (handler == null || !handler.isTransmitter()) return null;
		return handler.getRecipes(seen);
	}

	@Unique private IOpticalNetRecipeHandler getRecipeHandler() {
		if (callIsNetInvalidForTraversal()) return null;

		OpticalRoutePath route = net.getNetData(pipe.getPipePos(), facing);
		return route instanceof IOpticalRouteAccessor accessor ? accessor.getDataHandler() : null;
	}
}
