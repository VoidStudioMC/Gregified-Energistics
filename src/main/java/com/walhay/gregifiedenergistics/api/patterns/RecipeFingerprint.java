package com.walhay.gregifiedenergistics.api.patterns;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.chance.output.ChancedOutputLogic;
import gregtech.api.recipes.ingredients.GTRecipeInput;
import gregtech.api.recipes.ingredients.nbtmatch.ListNBTCondition;
import gregtech.api.recipes.ingredients.nbtmatch.NBTCondition;
import gregtech.api.recipes.ingredients.nbtmatch.NBTMatcher;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

/** Canonical recipe identity independent of registration order and compound-map iteration order. */
public final class RecipeFingerprint {
	private RecipeFingerprint() {}

	public static String of(Recipe recipe) {
		NBTTagCompound tag = new NBTTagCompound();
		tag.setInteger("Version", 1);
		tag.setTag("Inputs", inputs(recipe.getInputs()));
		tag.setTag("FluidInputs", inputs(recipe.getFluidInputs()));
		NBTTagList outputs = new NBTTagList();
		for (ItemStack stack : recipe.getOutputs()) outputs.appendTag(item(stack, true));
		tag.setTag("Outputs", outputs);
		NBTTagList fluids = new NBTTagList();
		for (FluidStack stack : recipe.getFluidOutputs()) fluids.appendTag(stack.writeToNBT(new NBTTagCompound()));
		tag.setTag("FluidOutputs", fluids);
		NBTTagList chances = new NBTTagList();
		for (var entry : recipe.getChancedOutputs().getChancedEntries()) {
			NBTTagCompound chance = item(entry.getIngredient(), true);
			chance.setInteger("Chance", entry.getChance());
			chance.setInteger("Boost", entry.getChanceBoost());
			chances.appendTag(chance);
		}
		tag.setTag("ChancedOutputs", chances);
		tag.setString("ChanceLogic", chanceLogic(recipe.getChancedOutputs().getChancedOutputLogic()));
		NBTTagList fluidChances = new NBTTagList();
		for (var entry : recipe.getChancedFluidOutputs().getChancedEntries()) {
			NBTTagCompound chance = entry.getIngredient().writeToNBT(new NBTTagCompound());
			chance.setInteger("Chance", entry.getChance());
			chance.setInteger("Boost", entry.getChanceBoost());
			fluidChances.appendTag(chance);
		}
		tag.setTag("ChancedFluidOutputs", fluidChances);
		tag.setString(
				"FluidChanceLogic", chanceLogic(recipe.getChancedFluidOutputs().getChancedOutputLogic()));
		tag.setInteger("Duration", recipe.getDuration());
		tag.setLong("EUt", recipe.getEUt());
		tag.setTag("Properties", recipe.propertyStorage().serializeNBT());
		return hash(tag);
	}

	public static String inputKey(GTRecipeInput input) {
		return hash(ingredient(input));
	}

	private static NBTTagList inputs(List<GTRecipeInput> inputs) {
		NBTTagList list = new NBTTagList();
		for (GTRecipeInput input : inputs) list.appendTag(ingredient(input));
		return list;
	}

	private static NBTTagCompound ingredient(GTRecipeInput input) {
		if (input == null) throw new IllegalArgumentException("Missing recipe ingredient");
		NBTTagCompound tag = new NBTTagCompound();
		tag.setString("Type", input.getClass().getName());
		tag.setInteger("Amount", input.getAmount());
		tag.setBoolean("NonConsumable", input.isNonConsumable());
		if (input.isOreDict()) tag.setString("Ore", OreDictionary.getOreName(input.getOreDict()));
		else if (input.getInputFluidStack() != null)
			tag.setTag("Fluid", input.getInputFluidStack().writeToNBT(new NBTTagCompound()));
		else {
			List<String> identities = new ArrayList<>();
			ItemStack[] stacks = input.getInputStacks();
			if (stacks != null) for (ItemStack stack : stacks) identities.add(hash(item(stack, false)));
			Collections.sort(identities);
			NBTTagList candidates = new NBTTagList();
			for (String identity : identities) candidates.appendTag(new NBTTagString(identity));
			tag.setTag("Items", candidates);
		}
		tag.setString("Matcher", matcher(input.getNBTMatcher()));
		tag.setString("Condition", condition(input.getNBTMatchingCondition()));
		return tag;
	}

	private static String matcher(NBTMatcher matcher) {
		if (matcher == null) return "none";
		if (matcher == NBTMatcher.ANY) return "any";
		if (matcher == NBTMatcher.LESS_THAN) return "less";
		if (matcher == NBTMatcher.LESS_THAN_OR_EQUAL_TO) return "less-or-equal";
		if (matcher == NBTMatcher.GREATER_THAN) return "greater";
		if (matcher == NBTMatcher.GREATER_THAN_OR_EQUAL_TO) return "greater-or-equal";
		if (matcher == NBTMatcher.EQUAL_TO) return "equal";
		if (matcher == NBTMatcher.RECURSIVE_EQUAL_TO) return "recursive-equal";
		if (matcher == NBTMatcher.NOT_PRESENT_OR_DEFAULT) return "missing-or-default";
		if (matcher == NBTMatcher.NOT_PRESENT_OR_HAS_KEY) return "missing-or-key";
		throw new IllegalArgumentException("Cannot persist an unknown NBT matcher safely");
	}

	private static String chanceLogic(ChancedOutputLogic logic) {
		if (logic == ChancedOutputLogic.OR) return "or";
		if (logic == ChancedOutputLogic.AND) return "and";
		if (logic == ChancedOutputLogic.XOR) return "xor";
		if (logic == ChancedOutputLogic.NONE) return "none";
		throw new IllegalArgumentException("Cannot persist an unknown chance logic safely");
	}

	private static String condition(NBTCondition condition) {
		if (condition == null) return "none";
		NBTTagCompound tag = new NBTTagCompound();
		tag.setString("Type", condition.tagType == null ? "any" : condition.tagType.name());
		tag.setString("Key", condition.nbtKey == null ? "" : condition.nbtKey);
		if (condition instanceof ListNBTCondition list) tag.setString("ListType", list.listTagType.name());
		tag.setTag("Value", value(condition.value));
		return hash(tag);
	}

	private static NBTBase value(Object value) {
		if (value == null) return new NBTTagString("null");
		if (value instanceof NBTBase nbt) return nbt;
		if (value instanceof NBTCondition condition) return new NBTTagString(condition(condition));
		if (value instanceof String string) return new NBTTagString(string);
		if (value instanceof Boolean flag) return new NBTTagByte((byte) (flag ? 1 : 0));
		if (value instanceof Byte number) return new NBTTagByte(number);
		if (value instanceof Short number) return new NBTTagShort(number);
		if (value instanceof Integer number) return new NBTTagInt(number);
		if (value instanceof Long number) return new NBTTagLong(number);
		if (value instanceof Float number) return new NBTTagFloat(number);
		if (value instanceof Double number) return new NBTTagDouble(number);
		if (value instanceof byte[] array) return new NBTTagByteArray(array);
		if (value instanceof int[] array) return new NBTTagIntArray(array);
		if (value instanceof long[] array) return new NBTTagLongArray(array);
		if (value instanceof List<?> list) {
			NBTTagCompound values = new NBTTagCompound();
			for (int i = 0; i < list.size(); ++i) values.setTag(Integer.toString(i), value(list.get(i)));
			return values;
		}
		throw new IllegalArgumentException("Cannot persist an unknown NBT condition value safely");
	}

	private static NBTTagCompound item(ItemStack stack, boolean includeCount) {
		if (stack == null || stack.isEmpty()) return new NBTTagCompound();
		ItemStack copy = stack.copy();
		copy.setCount(1);
		NBTTagCompound tag = copy.serializeNBT();
		if (includeCount) tag.setInteger("Count", stack.getCount());
		return tag;
	}

	public static String hash(NBTBase tag) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			canonical(tag, new DataOutputStream(bytes));
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
			StringBuilder result = new StringBuilder(64);
			for (byte b : digest) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
			return result.toString();
		} catch (IOException | NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static void canonical(NBTBase tag, DataOutputStream out) throws IOException {
		out.writeByte(tag.getId());
		switch (tag.getId()) {
			case 0:
				break;
			case 1:
				out.writeByte(((NBTPrimitive) tag).getByte());
				break;
			case 2:
				out.writeShort(((NBTPrimitive) tag).getShort());
				break;
			case 3:
				out.writeInt(((NBTPrimitive) tag).getInt());
				break;
			case 4:
				out.writeLong(((NBTPrimitive) tag).getLong());
				break;
			case 5:
				out.writeFloat(((NBTPrimitive) tag).getFloat());
				break;
			case 6:
				out.writeDouble(((NBTPrimitive) tag).getDouble());
				break;
			case 7:
				byte[] bytes = ((NBTTagByteArray) tag).getByteArray();
				out.writeInt(bytes.length);
				out.write(bytes);
				break;
			case 8:
				out.writeUTF(((NBTTagString) tag).getString());
				break;
			case 9:
				NBTTagList list = (NBTTagList) tag;
				out.writeByte(list.getTagType());
				out.writeInt(list.tagCount());
				for (NBTBase entry : list) canonical(entry, out);
				break;
			case 10:
				NBTTagCompound compound = (NBTTagCompound) tag;
				List<String> keys = new ArrayList<>(compound.getKeySet());
				Collections.sort(keys);
				out.writeInt(keys.size());
				for (String key : keys) {
					out.writeUTF(key);
					canonical(compound.getTag(key), out);
				}
				break;
			case 11:
				int[] ints = ((NBTTagIntArray) tag).getIntArray();
				out.writeInt(ints.length);
				for (int value : ints) out.writeInt(value);
				break;
			case 12:
				long[] longs = ((NBTTagLongArray) tag).data;
				out.writeInt(longs.length);
				for (long value : longs) out.writeLong(value);
				break;
			default:
				throw new IllegalArgumentException("Unknown NBT type " + tag.getId());
		}
	}
}
