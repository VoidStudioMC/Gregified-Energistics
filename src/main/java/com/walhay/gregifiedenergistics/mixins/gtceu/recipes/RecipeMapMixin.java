package com.walhay.gregifiedenergistics.mixins.gtceu.recipes;

import com.walhay.gregifiedenergistics.api.patterns.RecipeFingerprint;
import com.walhay.gregifiedenergistics.mixins.interfaces.IResearchRecipeMapAccessor;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.machines.RecipeMapAssemblyLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(RecipeMapAssemblyLine.class)
public abstract class RecipeMapMixin implements IResearchRecipeMapAccessor {
	@Override
	@Unique public String getKeyFromRecipe(Recipe recipe) {
		return RecipeFingerprint.of(recipe);
	}

	@Override
	@Unique public Recipe getRecipeByKey(String key) {
		if (key == null || key.length() != 64) return null;
		for (Recipe recipe : ((RecipeMap<?>) (Object) this).getRecipeList()) {
			try {
				if (key.equals(RecipeFingerprint.of(recipe))) return recipe;
			} catch (IllegalArgumentException | UnsupportedOperationException unsupported) {
				// Unknown matching semantics cannot be mapped safely to a saved task.
			}
		}
		return null;
	}
}
