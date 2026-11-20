package com.walhay.gregifiedenergistics.api.capability;

import gregtech.api.recipes.Recipe;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import javax.annotation.Nullable;

public interface INetRecipeHandler {

	void onRecipesUpdate(Set<INetRecipeHandler> seen);

	/** Signals data transfer after a consumer's available recipes change. */
	default void onRecipesTransferred() {}

	static Set<INetRecipeHandler> newTraversal() {
		return Collections.newSetFromMap(new IdentityHashMap<>());
	}

	default void onRecipesUpdate() {
		onRecipesUpdate(newTraversal());
	}

	@Nullable default Collection<Recipe> getRecipes() {
		return getRecipes(newTraversal());
	}

	@Nullable Collection<Recipe> getRecipes(Set<INetRecipeHandler> seen);
}
