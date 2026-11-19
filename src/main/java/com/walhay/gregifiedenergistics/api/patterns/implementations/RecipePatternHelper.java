package com.walhay.gregifiedenergistics.api.patterns.implementations;

import com.walhay.gregifiedenergistics.api.patterns.AbstractPatternHelper;
import com.walhay.gregifiedenergistics.api.patterns.RecipeFingerprint;
import com.walhay.gregifiedenergistics.api.patterns.substitutions.AESubstitutionStorage;
import gregtech.api.recipes.Recipe;
import javax.annotation.Nonnull;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class RecipePatternHelper extends AbstractPatternHelper {

	private final Recipe recipe;
	private final ItemStack item;
	private final String fingerprint;

	public RecipePatternHelper(@Nonnull Recipe recipe, @Nonnull ItemStack item) {
		this.recipe = recipe;
		this.item = item.copy();
		String key;
		try {
			key = RecipeFingerprint.of(recipe);
		} catch (IllegalArgumentException | UnsupportedOperationException unsupported) {
			key = null;
			com.walhay.gregifiedenergistics.GregifiedEnergisticsMod.LOGGER.warn(
					"Skipping crafting pattern with unsupported recipe identity: {}", unsupported.getMessage());
		}
		this.fingerprint = key;
		parseRecipe(recipe);
		if (fingerprint == null) validRecipe = false;
	}

	public RecipePatternHelper(@Nonnull Recipe recipe) {
		this(recipe, ItemStack.EMPTY);
	}

	public Recipe getRecipe() {
		return recipe;
	}

	/** Detaches an active CPU task's inputs and choices from subsequent provider edits. */
	public RecipePatternHelper snapshotForTask() {
		RecipePatternHelper snapshot = new RecipePatternHelper(recipe, item);
		snapshot.injectSubstitutions(new AESubstitutionStorage(
				substitutionStorage == null ? new NBTTagCompound() : substitutionStorage.serializeNBT()));
		snapshot.setPriority(getPriority());
		snapshot.validRecipe &= validRecipe;
		return snapshot;
	}

	@Override
	public ItemStack getPattern() {
		return item;
	}

	@Override
	public int hashCode() {
		return fingerprint == null ? System.identityHashCode(this) : fingerprint.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof RecipePatternHelper helper) {
			return this == helper || (fingerprint != null && fingerprint.equals(helper.fingerprint));
		}
		return false;
	}
}
