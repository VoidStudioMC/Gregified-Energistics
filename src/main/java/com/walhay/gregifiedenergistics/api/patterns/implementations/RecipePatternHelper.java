package com.walhay.gregifiedenergistics.api.patterns.implementations;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import com.glodblock.github.common.item.fake.FakeFluids;
import com.glodblock.github.util.FluidPatternDetails;
import com.walhay.gregifiedenergistics.api.capability.InfiniteItemStackHandler;
import com.walhay.gregifiedenergistics.api.patterns.AbstractPatternHelper;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.ingredients.GTRecipeInput;
import java.util.Arrays;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.FluidStack;

/** An AE2 processing pattern backed by a GregTech assembly line recipe and its chosen inputs. */
public class RecipePatternHelper extends AbstractPatternHelper {

	private final Recipe recipe;
	private final ItemStack item;

	public RecipePatternHelper(@Nonnull Recipe recipe, @Nonnull ItemStack item) {
		this.recipe = recipe;
		this.item = item.copy();
		parseRecipe(recipe);
		// Assembly line patterns describe deterministic item outputs.
		validRecipe &= recipe.getFluidOutputs().isEmpty()
				&& recipe.getChancedOutputs().getChancedEntries().isEmpty()
				&& recipe.getChancedFluidOutputs().getChancedEntries().isEmpty();
	}

	public RecipePatternHelper(@Nonnull Recipe recipe) {
		this(recipe, ItemStack.EMPTY);
	}

	public Recipe getRecipe() {
		return recipe;
	}

	@Override
	public IAEItemStack[] getInputs() {
		// AE2FC accounts for these drops as millibuckets in the fluid storage channel.
		return Stream.concat(Arrays.stream(inputs), Arrays.stream(fluidInputs).map(FakeFluids::packFluid2AEDrops))
				.toArray(IAEItemStack[]::new);
	}

	@Override
	public IAEItemStack[] getCondensedInputs() {
		return FluidPatternDetails.condenseStacks(getInputs());
	}

	/** Active tasks own their chosen inputs, independently of provider settings. */
	public RecipePatternHelper copy() {
		RecipePatternHelper copy = new RecipePatternHelper(recipe, item);
		copy.inputs = Arrays.stream(inputs)
				.map(stack -> stack == null ? null : stack.copy())
				.toArray(IAEItemStack[]::new);
		copy.fluidInputs = Arrays.stream(fluidInputs)
				.map(stack -> stack == null ? null : stack.copy())
				.toArray(IAEFluidStack[]::new);
		copy.setPriority(getPriority());
		copy.validRecipe &= validRecipe;
		return copy;
	}

	/** @return the recipe identity and the concrete inputs reserved by the crafting CPU */
	public NBTTagCompound writeToNBT() {
		NBTTagCompound data = new NBTTagCompound();
		data.setBoolean("FluidInputsInCrafting", true);
		data.setTag("Recipe", recipeIdentity(recipe));
		NBTTagList items = new NBTTagList();
		for (int i = 0; i < inputs.length; ++i) {
			NBTTagCompound tag = InfiniteItemStackHandler.writeStack(inputs[i].createItemStack());
			tag.setBoolean("NonConsumable", recipe.getInputs().get(i).isNonConsumable());
			items.appendTag(tag);
		}
		data.setTag("Inputs", items);
		NBTTagList fluids = new NBTTagList();
		for (int i = 0; i < fluidInputs.length; ++i) {
			NBTTagCompound tag = fluidInputs[i].getFluidStack().writeToNBT(new NBTTagCompound());
			tag.setBoolean("NonConsumable", recipe.getFluidInputs().get(i).isNonConsumable());
			fluids.appendTag(tag);
		}
		data.setTag("FluidInputs", fluids);
		return data;
	}

	/**
	 * Restores a plan when exactly one current recipe matches its settings and chosen inputs.
	 *
	 * @param data saved recipe plan from the crafting CPU
	 * @param pattern item that supplied the recipe, or {@link ItemStack#EMPTY} for an optical source
	 * @return the restored pattern, or {@code null} if the job must be replanned
	 */
	@Nullable public static RecipePatternHelper readFromNBT(NBTTagCompound data, ItemStack pattern) {
		NBTTagList items = data.getTagList("Inputs", NBT.TAG_COMPOUND);
		NBTTagList fluids = data.getTagList("FluidInputs", NBT.TAG_COMPOUND);
		// Older jobs never reserved their fluid inputs in the CPU. Replanning must include them.
		if (!fluids.isEmpty() && !data.getBoolean("FluidInputsInCrafting")) return null;
		ItemStack[] savedItems = new ItemStack[items.tagCount()];
		Arrays.setAll(savedItems, i -> InfiniteItemStackHandler.readStack(items.getCompoundTagAt(i)));
		FluidStack[] savedFluids = new FluidStack[fluids.tagCount()];
		Arrays.setAll(savedFluids, i -> FluidStack.loadFluidStackFromNBT(fluids.getCompoundTagAt(i)));
		NBTTagCompound savedRecipe = data.getCompoundTag("Recipe");
		RecipePatternHelper restored = null;
		for (Recipe recipe : RecipeMaps.ASSEMBLY_LINE_RECIPES.getRecipeList()) {
			if (recipe.getInputs().size() != savedItems.length
					|| recipe.getFluidInputs().size() != savedFluids.length
					|| !recipeIdentity(recipe).equals(savedRecipe)
					|| !matchesItemInputs(recipe, items, savedItems)
					|| !matchesFluidInputs(recipe, fluids, savedFluids)) continue;
			RecipePatternHelper helper = new RecipePatternHelper(recipe, pattern);
			if (!helper.isValidPattern()) continue;
			// Never choose arbitrarily between recipes that accept the same saved plan.
			if (restored != null) return null;
			Arrays.setAll(helper.inputs, i -> itemChannel.createStack(savedItems[i]));
			Arrays.setAll(helper.fluidInputs, i -> fluidChannel.createStack(savedFluids[i]));
			restored = helper;
		}
		return restored;
	}

	private static boolean matchesItemInputs(Recipe recipe, NBTTagList entries, ItemStack[] savedItems) {
		for (int i = 0; i < savedItems.length; ++i) {
			GTRecipeInput input = recipe.getInputs().get(i);
			ItemStack stack = savedItems[i];
			if (stack.isEmpty()
					|| stack.getCount() != input.getAmount()
					|| entries.getCompoundTagAt(i).getBoolean("NonConsumable") != input.isNonConsumable()
					|| !input.acceptsStack(stack)) return false;
		}
		return true;
	}

	private static boolean matchesFluidInputs(Recipe recipe, NBTTagList entries, FluidStack[] savedFluids) {
		for (int i = 0; i < savedFluids.length; ++i) {
			GTRecipeInput input = recipe.getFluidInputs().get(i);
			FluidStack stack = savedFluids[i];
			if (stack == null
					|| stack.amount != input.getAmount()
					|| entries.getCompoundTagAt(i).getBoolean("NonConsumable") != input.isNonConsumable()
					|| !input.acceptsFluid(stack)) return false;
		}
		return true;
	}

	private static NBTTagCompound recipeIdentity(Recipe recipe) {
		NBTTagCompound data = new NBTTagCompound();
		NBTTagList outputs = new NBTTagList();
		for (ItemStack output : recipe.getOutputs()) outputs.appendTag(InfiniteItemStackHandler.writeStack(output));
		data.setTag("Outputs", outputs);
		data.setInteger("Duration", recipe.getDuration());
		data.setLong("EUt", recipe.getEUt());
		data.setTag("Properties", recipe.propertyStorage().serializeNBT());
		return data;
	}

	@Override
	public ItemStack getPattern() {
		return item;
	}

	@Override
	public int hashCode() {
		return System.identityHashCode(recipe);
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof RecipePatternHelper helper
				&& recipe == helper.recipe
				&& Arrays.equals(inputs, helper.inputs)
				&& Arrays.equals(fluidInputs, helper.fluidInputs);
	}
}
