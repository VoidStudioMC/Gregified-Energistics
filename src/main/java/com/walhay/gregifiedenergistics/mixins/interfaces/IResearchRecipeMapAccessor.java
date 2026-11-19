package com.walhay.gregifiedenergistics.mixins.interfaces;

import gregtech.api.recipes.Recipe;

public interface IResearchRecipeMapAccessor {
	Recipe getRecipeByKey(String key);

	String getKeyFromRecipe(Recipe recipe);

	/** Legacy numeric identities are intentionally not resolved after recipe registration changes. */
	@Deprecated
	default Recipe getRecipeById(int id) {
		return null;
	}

	@Deprecated
	default int getIdFromRecipe(Recipe recipe) {
		return -1;
	}
}
