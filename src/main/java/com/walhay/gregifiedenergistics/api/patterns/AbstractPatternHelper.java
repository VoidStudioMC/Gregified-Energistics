package com.walhay.gregifiedenergistics.api.patterns;

import appeng.api.AEApi;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.ingredients.GTRecipeInput;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

public abstract class AbstractPatternHelper implements IProvidablePattern, ISubstitutionHandler {

	protected static final IItemStorageChannel itemChannel =
			AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
	protected static final IFluidStorageChannel fluidChannel =
			AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
	protected IAEItemStack[] inputs;
	protected IAEItemStack[] outputs;
	protected IAEFluidStack[] fluidInputs;
	private int priority = 0;
	private final Map<Integer, GTRecipeInput> subMap = new HashMap<>();
	private String substitutionPrefix;
	protected ISubstitutionStorage substitutionStorage;
	protected boolean validRecipe = true;

	protected void parseRecipe(Recipe recipe) {
		validRecipe = true;
		subMap.clear();
		ItemStack output = recipe.getOutputs().isEmpty()
				? ItemStack.EMPTY
				: recipe.getOutputs().get(0);
		substitutionPrefix = "input:" + output.getItem().getRegistryName() + "@" + output.getMetadata() + ":";
		inputs = new IAEItemStack[recipe.getInputs().size()];
		for (int i = 0; i < inputs.length; ++i) {
			GTRecipeInput input = recipe.getInputs().get(i);
			ItemStack[] raw = input == null ? null : input.getInputStacks();
			ItemStack[] candidates = raw == null
					? new ItemStack[0]
					: java.util.Arrays.stream(raw)
							.filter(stack -> stack != null && !stack.isEmpty())
							.toArray(ItemStack[]::new);
			if (candidates.length == 0) {
				validRecipe = false;
				continue;
			}
			ItemStack stack = candidates[0].copy();
			stack.setCount(input.getAmount());
			inputs[i] = itemChannel.createStack(stack);
			if (candidates.length > 1) subMap.put(i, input);
		}
		outputs = recipe.getOutputs().stream()
				.filter(stack -> stack != null && !stack.isEmpty())
				.map(ItemStack::copy)
				.map(itemChannel::createStack)
				.toArray(IAEItemStack[]::new);
		fluidInputs = recipe.getFluidInputs().stream()
				.map(GTRecipeInput::getInputFluidStack)
				.filter(stack -> stack != null && stack.amount > 0)
				.map(FluidStack::copy)
				.map(fluidChannel::createStack)
				.toArray(IAEFluidStack[]::new);
		if (outputs.length == 0 || fluidInputs.length != recipe.getFluidInputs().size()) validRecipe = false;
	}

	public boolean isValidPattern() {
		return validRecipe;
	}

	@Override
	public Collection<GTRecipeInput> getSubstitutions() {
		return subMap.values();
	}

	@Override
	public void injectSubstitutions(ISubstitutionStorage storage) {
		if (storage == null) return;
		substitutionStorage = storage;
		for (Map.Entry<Integer, GTRecipeInput> entry : subMap.entrySet()) {
			GTRecipeInput input = entry.getValue();
			String name = input.isOreDict()
					? OreDictionary.getOreName(input.getOreDict())
					: substitutionPrefix + entry.getKey();
			ItemStack[] candidates = input.getInputStacks();
			storage.registerCandidates(name, candidates);
			java.util.List<ItemStack> available = storage.getCandidates(name);
			int option = storage.getOption(name);
			if (option < 0 || option >= available.size()) {
				validRecipe = false;
				continue;
			}
			ItemStack stack = available.get(option).copy();
			stack.setCount(input.getAmount());
			inputs[entry.getKey()] = itemChannel.createStack(stack);
		}
	}

	@Override
	public ISubstitutionStorage getSubstitutionStorage() {
		return substitutionStorage;
	}

	@Override
	public void providePatterns(ICraftingMedium medium, ICraftingProviderHelper helper) {
		if (helper == null || !validRecipe) return;

		helper.addCraftingOption(medium, this);
	}

	@Override
	public boolean isValidItemForSlot(int i, ItemStack itemStack, World world) {
		return false;
	}

	@Override
	public boolean isCraftable() {
		return false;
	}

	public IAEFluidStack[] getFluidInputs() {
		return fluidInputs;
	}

	@Override
	public IAEItemStack[] getInputs() {
		return inputs;
	}

	@Override
	public IAEItemStack[] getCondensedInputs() {
		return inputs;
	}

	@Override
	public IAEItemStack[] getCondensedOutputs() {
		return outputs;
	}

	@Override
	public IAEItemStack[] getOutputs() {
		return outputs;
	}

	@Override
	public boolean canSubstitute() {
		return false;
	}

	@Override
	public ItemStack getOutput(InventoryCrafting inventoryCrafting, World world) {
		return null;
	}

	@Override
	public int getPriority() {
		return priority;
	}

	@Override
	public void setPriority(int i) {
		priority = i;
	}
}
