package com.walhay.gregifiedenergistics.api.patterns;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.INBTSerializable;

/** Associates recipe ingredient identifiers with the selected item and the choices currently available. */
public interface ISubstitutionStorage extends INBTSerializable<NBTTagCompound> {

	/**
	 * Selects an item by its index in the current candidate list.
	 *
	 * @param input ingredient identifier
	 * @param option zero-based index in the candidate list
	 */
	void setOption(String input, int option);

	/**
	 * @param input ingredient identifier
	 * @return the selected index in the corresponding candidate list
	 */
	int getOption(String input);

	Collection<String> getOptions();

	default void registerCandidates(String name, ItemStack[] candidates) {}

	default List<ItemStack> getCandidates(String name) {
		return Collections.emptyList();
	}
}
