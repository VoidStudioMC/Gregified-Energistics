package com.walhay.gregifiedenergistics.api.patterns.substitutions;

import com.walhay.gregifiedenergistics.api.patterns.ISubstitutionStorage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

/** A strongly owned snapshot of task choices, with item identities for reload-safe selection. */
public class AESubstitutionStorage implements ISubstitutionStorage {
	private final Map<String, Integer> options = new LinkedHashMap<>();
	private final Map<String, ItemStack> selected = new LinkedHashMap<>();
	private final Map<String, List<ItemStack>> candidates = new LinkedHashMap<>();
	private long revision;

	public AESubstitutionStorage(NBTTagCompound tag) {
		deserializeNBT(tag);
	}

	@Override
	public int getOption(String name) {
		List<ItemStack> stacks = candidates.get(name);
		if (stacks == null) return Math.max(0, options.getOrDefault(name, 0));
		if (stacks.isEmpty()) return 0;
		ItemStack choice = selected.get(name);
		if (choice != null) {
			for (int i = 0; i < stacks.size(); ++i) {
				if (sameItem(choice, stacks.get(i))) return i;
			}
			return 0;
		}
		int option = options.getOrDefault(name, 0);
		return option >= 0 && option < stacks.size() ? option : 0;
	}

	@Override
	public void setOption(String name, int option) {
		if (!validName(name) || option < 0) return;
		List<ItemStack> stacks = candidates.get(name);
		if (stacks != null && option >= stacks.size()) return;
		ItemStack choice = stacks == null ? null : stacks.get(option);
		if (options.getOrDefault(name, -1) == option && (choice == null || sameItem(choice, selected.get(name))))
			return;
		options.put(name, option);
		if (choice != null) selected.put(name, choice.copy());
		else selected.remove(name);
		revision++;
	}

	@Override
	public Collection<String> getOptions() {
		LinkedHashSet<String> names = new LinkedHashSet<>(options.keySet());
		names.addAll(candidates.keySet());
		return Collections.unmodifiableCollection(names);
	}

	@Override
	public void registerCandidates(String name, ItemStack[] stacks) {
		if (!validName(name)) return;
		List<ItemStack> copies = new ArrayList<>();
		if (stacks != null) {
			for (ItemStack stack : stacks) {
				if (stack == null || stack.isEmpty()) continue;
				ItemStack copy = stack.copy();
				copy.setCount(1);
				copies.add(copy);
			}
		}
		List<ItemStack> previous = candidates.put(name, copies);
		if (!sameCandidates(previous, copies)) revision++;
		int option = getOption(name);
		if (!copies.isEmpty()) setOption(name, option);
	}

	@Override
	public List<ItemStack> getCandidates(String name) {
		List<ItemStack> result = new ArrayList<>();
		for (ItemStack stack : candidates.getOrDefault(name, Collections.emptyList())) result.add(stack.copy());
		return result;
	}

	public long getRevision() {
		return revision;
	}

	@Override
	public NBTTagCompound serializeNBT() {
		return serialize(false);
	}

	public NBTTagCompound serializeSyncNBT() {
		return serialize(true);
	}

	private NBTTagCompound serialize(boolean includeCandidates) {
		NBTTagCompound compound = new NBTTagCompound();
		NBTTagList list = new NBTTagList();
		for (String name : getOptions()) {
			NBTTagCompound tag = new NBTTagCompound();
			tag.setString("Ingredient", name);
			tag.setInteger("Option", getOption(name));
			ItemStack choice = selected.get(name);
			if (choice != null && !choice.isEmpty()) tag.setTag("Selected", choice.serializeNBT());
			if (includeCandidates) {
				NBTTagList stacks = new NBTTagList();
				for (ItemStack stack : getCandidates(name)) stacks.appendTag(stack.serializeNBT());
				tag.setTag("Candidates", stacks);
			}
			list.appendTag(tag);
		}
		compound.setTag("Data", list);
		return compound;
	}

	@Override
	public void deserializeNBT(NBTTagCompound compound) {
		options.clear();
		selected.clear();
		NBTTagList list = compound.getTagList("Data", NBT.TAG_COMPOUND);
		for (int i = 0; i < list.tagCount(); ++i) {
			NBTTagCompound tag = list.getCompoundTagAt(i);
			String name = tag.getString("Ingredient");
			if (!validName(name)) continue;
			options.put(name, Math.max(0, tag.getInteger("Option")));
			if (tag.hasKey("Selected", NBT.TAG_COMPOUND)) {
				ItemStack stack = new ItemStack(tag.getCompoundTag("Selected"));
				if (!stack.isEmpty()) selected.put(name, stack);
			}
		}
		revision++;
	}

	public void deserializeSyncNBT(NBTTagCompound compound) {
		candidates.clear();
		deserializeNBT(compound);
		NBTTagList list = compound.getTagList("Data", NBT.TAG_COMPOUND);
		for (int i = 0; i < list.tagCount(); ++i) {
			NBTTagCompound tag = list.getCompoundTagAt(i);
			String name = tag.getString("Ingredient");
			if (!validName(name)) continue;
			NBTTagList stacks = tag.getTagList("Candidates", NBT.TAG_COMPOUND);
			ItemStack[] array = new ItemStack[stacks.tagCount()];
			for (int j = 0; j < array.length; ++j) array[j] = new ItemStack(stacks.getCompoundTagAt(j));
			registerCandidates(name, array);
		}
	}

	private static boolean validName(String name) {
		return name != null && !name.isEmpty() && name.length() <= 256;
	}

	private static boolean sameItem(ItemStack first, ItemStack second) {
		if (first == null || second == null) return first == second;
		ItemStack a = first.copy();
		ItemStack b = second.copy();
		a.setCount(1);
		b.setCount(1);
		return a.serializeNBT().equals(b.serializeNBT());
	}

	private static boolean sameCandidates(List<ItemStack> first, List<ItemStack> second) {
		if (first == null || first.size() != second.size()) return false;
		for (int i = 0; i < first.size(); ++i) if (!sameItem(first.get(i), second.get(i))) return false;
		return true;
	}
}
