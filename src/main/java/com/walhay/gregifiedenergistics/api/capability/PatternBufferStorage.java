package com.walhay.gregifiedenergistics.api.capability;

import gregtech.api.capability.impl.FluidTankList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.items.IItemHandler;

/** Owned buffer contents. Shape changes require an empty buffer; batch insertion commits only after full preflight. */
public final class PatternBufferStorage {

	private static final int MAX_SLOTS = 4096;
	private final Runnable contentsChanged;
	private final InfiniteItemStackHandler items;
	private FluidTankList fluids = new FluidTankList(false);
	private int revision;

	public PatternBufferStorage(Runnable contentsChanged) {
		this.contentsChanged = contentsChanged;
		this.items = new InfiniteItemStackHandler(0) {
			@Override
			protected void onContentsChanged(int slot) {
				changed();
			}
		};
	}

	private void changed() {
		contentsChanged.run();
	}

	public InfiniteItemStackHandler items() {
		return items;
	}

	public FluidTankList fluids() {
		return fluids;
	}

	public int revision() {
		return revision;
	}

	public boolean isEmpty() {
		for (int i = 0; i < items.getSlots(); ++i) {
			if (!items.getStackInSlot(i).isEmpty()) return false;
		}
		for (var tank : fluids.getFluidTanks()) {
			if (tank.getFluidAmount() > 0) return false;
		}
		return true;
	}

	public boolean configure(int itemSlots, int fluidSlots) {
		checkSize(itemSlots);
		checkSize(fluidSlots);
		if (!isEmpty()) return false;
		items.setSize(itemSlots);
		fluids = createTanks(fluidSlots);
		++revision;
		changed();
		return true;
	}

	private FluidTankList createTanks(int count) {
		List<FluidTank> tanks = new ArrayList<>(count);
		for (int i = 0; i < count; ++i) {
			tanks.add(new FluidTank(Integer.MAX_VALUE) {
				@Override
				protected void onContentsChanged() {
					changed();
				}
			});
		}
		return new FluidTankList(false, tanks);
	}

	private static void checkSize(int slots) {
		if (slots < 0 || slots > MAX_SLOTS) throw new IllegalArgumentException("Invalid buffer slot count: " + slots);
	}

	public boolean insertAll(List<ItemStack> incomingItems, List<FluidStack> incomingFluids) {
		InfiniteItemStackHandler stagedItems = new InfiniteItemStackHandler(items.getSlots());
		for (int i = 0; i < items.getSlots(); ++i)
			stagedItems.setStackInSlot(i, items.getStackInSlot(i).copy());
		for (ItemStack stack : incomingItems) {
			if (stack == null || stack.isEmpty()) continue;
			if (!insert(stagedItems, stack.copy()).isEmpty()) return false;
		}
		List<FluidTank> stagedTanks = new ArrayList<>(fluids.getTanks());
		for (var tank : fluids.getFluidTanks()) {
			FluidStack current = tank.getFluid();
			stagedTanks.add(new FluidTank(current == null ? null : current.copy(), tank.getCapacity()));
		}
		FluidTankList stagedFluids = new FluidTankList(false, stagedTanks);
		for (FluidStack stack : incomingFluids) {
			if (stack == null || stack.amount <= 0) return false;
			if (stagedFluids.fill(stack.copy(), true) != stack.amount) return false;
		}
		// These are our own unfiltered Forge handlers, used on the server thread; no external insertion can intervene.
		for (int i = 0; i < items.getSlots(); ++i) {
			ItemStack result = stagedItems.getStackInSlot(i);
			if (!ItemStack.areItemStacksEqual(items.getStackInSlot(i), result)) items.setStackInSlot(i, result.copy());
		}
		for (int i = 0; i < fluids.getTanks(); ++i) {
			FluidStack result = stagedTanks.get(i).getFluid();
			FluidStack previous = fluids.getTankAt(i).getFluid();
			if (FluidStack.areFluidStackTagsEqual(previous, result)
					&& (previous == null ? result == null : result != null && previous.isFluidStackIdentical(result)))
				continue;
			fluids.getTankAt(i).drain(Integer.MAX_VALUE, true);
			if (result != null) fluids.getTankAt(i).fill(result.copy(), true);
		}
		return true;
	}

	private static ItemStack insert(IItemHandler handler, ItemStack stack) {
		for (int slot = 0; slot < handler.getSlots() && !stack.isEmpty(); ++slot)
			stack = handler.insertItem(slot, stack, false);
		return stack;
	}

	public NBTTagCompound serializeNBT() {
		NBTTagCompound data = new NBTTagCompound();
		data.setTag("Items", items.serializeNBT());
		data.setTag("Fluids", fluids.serializeNBT());
		return data;
	}

	public void deserializeNBT(NBTTagCompound data) {
		NBTTagCompound savedItems = data.getCompoundTag("Items").copy();
		NBTTagList entries = savedItems.getTagList("Items", NBT.TAG_COMPOUND);
		int itemSlots = 0;
		for (int i = 0; i < entries.tagCount(); ++i) {
			NBTTagCompound entry = entries.getCompoundTagAt(i);
			int slot = entry.getInteger("Slot");
			if (slot < 0 || InfiniteItemStackHandler.readStack(entry).isEmpty()) continue;
			if (slot >= MAX_SLOTS) throw new IllegalArgumentException("Invalid saved buffer slot: " + slot);
			itemSlots = Math.max(itemSlots, slot + 1);
		}
		// Normal empty slots are retained to keep the active recipe shape; untrusted huge Size is never allocated.
		int savedSize = savedItems.getInteger("Size");
		if (savedSize >= 0 && savedSize <= MAX_SLOTS) itemSlots = Math.max(itemSlots, savedSize);
		savedItems.setInteger("Size", itemSlots);
		NBTTagCompound savedFluids = data.getCompoundTag("Fluids");
		int fluidSlots = savedFluids.getTagList("Tanks", NBT.TAG_COMPOUND).tagCount();
		checkSize(fluidSlots);
		items.deserializeNBT(savedItems);
		fluids = createTanks(fluidSlots);
		fluids.deserializeNBT(savedFluids);
		++revision;
		changed();
	}
}
