package com.walhay.gregifiedenergistics.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.walhay.gregifiedenergistics.api.patterns.RecipeFingerprint;
import gregtech.api.recipes.ingredients.GTRecipeInput;
import gregtech.api.recipes.ingredients.GTRecipeItemInput;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class RecipeFingerprintTest {
	@BeforeAll
	static void bootstrap() {
		Bootstrap.register();
	}

	@Test
	void candidateOrderAndCompoundInsertionOrderDoNotChangeIdentity() {
		NBTTagCompound first = new NBTTagCompound();
		first.setInteger("a", 1);
		first.setString("b", "value");
		NBTTagCompound second = new NBTTagCompound();
		second.setString("b", "value");
		second.setInteger("a", 1);
		assertEquals(RecipeFingerprint.hash(first), RecipeFingerprint.hash(second));
		GTRecipeInput ordered =
				new GTRecipeItemInput(new ItemStack[] {new ItemStack(Items.DIAMOND), new ItemStack(Items.COAL)}, 2);
		GTRecipeInput reversed =
				new GTRecipeItemInput(new ItemStack[] {new ItemStack(Items.COAL), new ItemStack(Items.DIAMOND)}, 2);
		assertEquals(RecipeFingerprint.inputKey(ordered), RecipeFingerprint.inputKey(reversed));
	}

	@Test
	void consumptionAmountAndTaggedIdentityChangesCannotResolveToTheSameIngredient() {
		GTRecipeInput input = new GTRecipeItemInput(new ItemStack(Items.DIAMOND));
		String baseline = RecipeFingerprint.inputKey(input);
		ItemStack tagged = new ItemStack(Items.DIAMOND);
		NBTTagCompound identity = new NBTTagCompound();
		identity.setString("variant", "different");
		tagged.setTagCompound(identity);
		assertNotEquals(baseline, RecipeFingerprint.inputKey(new GTRecipeItemInput(tagged)));
		assertNotEquals(baseline, RecipeFingerprint.inputKey(input.copyWithAmount(2)));
		assertNotEquals(
				baseline, RecipeFingerprint.inputKey(input.copyWithAmount(1).setNonConsumable()));
	}
}
