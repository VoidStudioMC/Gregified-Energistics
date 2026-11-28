package com.walhay.gregifiedenergistics.api.capability;

import gregtech.api.recipes.Recipe;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import javax.annotation.Nullable;

/** Provides assembly line research recipes and propagates recipe changes through the optical network. */
public interface INetRecipeHandler {

	/**
	 * Forwards an invalidation using the same visited set throughout the traversal.
	 *
	 * @param seen handlers already notified during this traversal
	 */
	void onRecipesUpdate(Set<INetRecipeHandler> seen);

	/** Signals data transfer after a consumer's available recipes change. */
	default void onRecipesTransferred() {}

	/** @return an identity-based set for tracking visited endpoints in a new traversal */
	static Set<INetRecipeHandler> newTraversal() {
		return Collections.newSetFromMap(new IdentityHashMap<>());
	}

	default void onRecipesUpdate() {
		onRecipesUpdate(newTraversal());
	}

	@Nullable default Collection<Recipe> getRecipes() {
		return getRecipes(newTraversal());
	}

	/**
	 * Reads recipes reachable through this handler without signalling a transfer.
	 *
	 * @param seen handlers already visited while looking up recipes
	 * @return reachable recipes, or {@code null} when this route cannot supply data
	 */
	@Nullable Collection<Recipe> getRecipes(Set<INetRecipeHandler> seen);
}
