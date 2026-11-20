package com.walhay.gregifiedenergistics.api.patterns;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.INBTSerializable;

public interface ISubstitutionStorage extends INBTSerializable<NBTTagCompound> {

	void setOption(String input, int option);

	int getOption(String input);

	Collection<String> getOptions();

	default void registerCandidates(String name, ItemStack[] candidates) {}

	default List<ItemStack> getCandidates(String name) {
		return Collections.emptyList();
	}
}
