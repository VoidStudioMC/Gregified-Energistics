package com.walhay.gregifiedenergistics.api.capability;

import static com.walhay.gregifiedenergistics.GregifiedEnergisticsConfig.MAX_SLOTS;

import appeng.util.InventoryAdaptor;
import com.walhay.gregifiedenergistics.api.util.AssemblyLineFluidTransfer;
import com.walhay.gregifiedenergistics.api.util.AssemblyLineInputs;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;

/**
 * Owns inputs accepted from the crafting CPU until the corresponding buses or hatches receive them. Destination indices
 * stay fixed when the multiblock detaches or a transfer only partially succeeds.
 */
public final class AssemblyLineInputQueue {
	public static final String ITEM_TAG = "ItemToSend";
	public static final String FLUID_TAG = "FluidToSend";
	public static final String SLOT_TAG = "Slot";
	private static final String LEGACY_OWNED_FLUID_TAG = "ExtractedFluids";
	private static final String FLUID_QUEUE_VERSION_TAG = "FluidQueueVersion";
	private static final int OWNED_FLUID_QUEUE_VERSION = 1;

	private final Int2ObjectOpenHashMap<ItemStack> items = new Int2ObjectOpenHashMap<>();
	private final Int2ObjectOpenHashMap<FluidStack> fluids = new Int2ObjectOpenHashMap<>();
	private final Runnable contentsChanged;

	public AssemblyLineInputQueue(Runnable contentsChanged) {
		this.contentsChanged = contentsChanged;
	}

	public boolean hasItems() {
		return !items.isEmpty();
	}

	public boolean hasFluids() {
		return !fluids.isEmpty();
	}

	/** Takes ownership of the CPU inputs after the bus has checked all destinations. */
	public void enqueue(AssemblyLineInputs inputs) {
		for (int slot = 0; slot < inputs.items.size(); ++slot) items.put(slot, inputs.items.get(slot));
		for (int slot = 0; slot < inputs.fluids.size(); ++slot) fluids.put(slot, inputs.fluids.get(slot));
		if (!inputs.items.isEmpty() || !inputs.fluids.isEmpty()) contentsChanged.run();
	}

	/** Missing destinations leave their inputs queued for the next delivery attempt. */
	public void deliverItems(List<InventoryAdaptor> destinations) {
		var iterator = items.int2ObjectEntrySet().iterator();
		boolean changed = false;
		while (iterator.hasNext()) {
			var entry = iterator.next();
			int slot = entry.getIntKey();
			ItemStack stack = entry.getValue();
			if (stack.isEmpty()) {
				iterator.remove();
				changed = true;
				continue;
			}
			if (slot < 0 || slot >= destinations.size()) continue;
			ItemStack remainder = destinations.get(slot).addItems(stack.copy());
			if (remainder.isEmpty()) {
				iterator.remove();
				changed = true;
			} else if (!ItemStack.areItemStacksEqual(stack, remainder)) {
				entry.setValue(remainder.copy());
				changed = true;
			}
		}
		if (changed) contentsChanged.run();
	}

	public void deliverFluids(List<IFluidTank> destinations) {
		var iterator = fluids.int2ObjectEntrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			int slot = entry.getIntKey();
			if (slot < 0 || slot >= destinations.size()) continue;
			FluidStack stack = entry.getValue();
			FluidStack remainder = AssemblyLineFluidTransfer.deliverOwned(destinations.get(slot), stack);
			if (remainder == null) iterator.remove();
			else entry.setValue(remainder);
			int remaining = remainder == null ? 0 : remainder.amount;
			if (stack != null && stack.amount > remaining) contentsChanged.run();
		}
	}

	public static boolean hasSavedInputs(NBTTagCompound data) {
		return data.hasKey(ITEM_TAG) || data.hasKey(FLUID_TAG) || data.hasKey(LEGACY_OWNED_FLUID_TAG);
	}

	public void writeToNBT(NBTTagCompound data) {
		NBTTagList itemEntries = new NBTTagList();
		for (var entry : items.int2ObjectEntrySet()) {
			NBTTagCompound item = InfiniteItemStackHandler.writeStack(entry.getValue());
			item.setInteger(SLOT_TAG, entry.getIntKey());
			itemEntries.appendTag(item);
		}
		if (itemEntries.isEmpty()) data.removeTag(ITEM_TAG);
		else data.setTag(ITEM_TAG, itemEntries);

		if (fluids.isEmpty()) {
			data.removeTag(FLUID_TAG);
			data.removeTag(FLUID_QUEUE_VERSION_TAG);
		} else {
			NBTTagList fluidEntries = new NBTTagList();
			for (var entry : fluids.int2ObjectEntrySet()) {
				FluidStack stack = entry.getValue();
				if (stack == null || stack.amount <= 0) continue;
				NBTTagCompound fluid = stack.writeToNBT(new NBTTagCompound());
				fluid.setInteger(SLOT_TAG, entry.getIntKey());
				fluidEntries.appendTag(fluid);
			}
			data.setTag(FLUID_TAG, fluidEntries);
			data.setInteger(FLUID_QUEUE_VERSION_TAG, OWNED_FLUID_QUEUE_VERSION);
		}
		data.removeTag(LEGACY_OWNED_FLUID_TAG);
	}

	public void readFromNBT(NBTTagCompound data) {
		items.clear();
		fluids.clear();
		NBTTagList itemEntries = data.getTagList(ITEM_TAG, NBT.TAG_COMPOUND);
		for (int i = 0; i < itemEntries.tagCount(); ++i) {
			NBTTagCompound entry = itemEntries.getCompoundTagAt(i);
			int slot = entry.getInteger(SLOT_TAG);
			ItemStack stack = InfiniteItemStackHandler.readStack(entry);
			if (slot >= 0 && slot < MAX_SLOTS && !stack.isEmpty()) items.put(slot, stack);
		}
		// Old saves recorded unfulfilled ME requests separately from fluids already extracted.
		String fluidTag = data.getInteger(FLUID_QUEUE_VERSION_TAG) >= OWNED_FLUID_QUEUE_VERSION
				? FLUID_TAG
				: LEGACY_OWNED_FLUID_TAG;
		NBTTagList fluidEntries = data.getTagList(fluidTag, NBT.TAG_COMPOUND);
		for (int i = 0; i < fluidEntries.tagCount(); ++i) {
			NBTTagCompound entry = fluidEntries.getCompoundTagAt(i);
			int slot = entry.getInteger(SLOT_TAG);
			FluidStack stack = FluidStack.loadFluidStackFromNBT(entry);
			if (slot >= 0 && slot < MAX_SLOTS && stack != null && stack.amount > 0) fluids.put(slot, stack);
		}
	}
}
