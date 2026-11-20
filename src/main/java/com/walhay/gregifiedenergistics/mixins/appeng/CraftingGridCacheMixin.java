package com.walhay.gregifiedenergistics.mixins.appeng;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.me.cache.CraftingGridCache;
import com.walhay.gregifiedenergistics.api.patterns.implementations.RecipePatternHelper;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CraftingGridCache.class)
public abstract class CraftingGridCacheMixin {
	@Shadow(remap = false)
	@Final
	private Object2ObjectMap<ICraftingPatternDetails, List<ICraftingMedium>> craftingMethods;

	@Inject(method = "getMediums", at = @At("RETURN"), remap = false, cancellable = true)
	private void gregified$findRecipeMedium(
			ICraftingPatternDetails pattern, CallbackInfoReturnable<List<ICraftingMedium>> ci) {
		if (!(pattern instanceof RecipePatternHelper task)
				|| !ci.getReturnValue().isEmpty()) return;
		// A provider can still run this recipe after its selected input items change.
		Set<ICraftingMedium> mediums = new LinkedHashSet<>();
		for (var entry : craftingMethods.object2ObjectEntrySet()) {
			if (entry.getKey() instanceof RecipePatternHelper provider && provider.getRecipe() == task.getRecipe())
				mediums.addAll(entry.getValue());
		}
		ci.setReturnValue(new ArrayList<>(mediums));
	}
}
