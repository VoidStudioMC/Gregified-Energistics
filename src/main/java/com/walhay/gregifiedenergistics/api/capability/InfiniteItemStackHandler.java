package com.walhay.gregifiedenergistics.api.capability;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

/** InfiniteStackHandler */
public class InfiniteItemStackHandler extends ItemStackHandler {
	public static final String FULL_COUNT_TAG = "FullCount";

	public InfiniteItemStackHandler(int slots) {
		super(slots);
	}

	@Override
	protected int getStackLimit(int slot, ItemStack stack) {
		return Integer.MAX_VALUE;
	}

	@Override
	public int getSlotLimit(int slot) {
		return Integer.MAX_VALUE;
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		if (getSlots() == 0) return ItemStack.EMPTY;

		return this.stacks.get(slot);
	}

	/** Stores the full count separately from the vanilla signed-byte count. */
	public static NBTTagCompound writeStack(ItemStack stack) {
		NBTTagCompound data = stack.writeToNBT(new NBTTagCompound());
		if (!stack.isEmpty()) {
			data.setByte("Count", (byte) 1);
			data.setInteger(FULL_COUNT_TAG, stack.getCount());
		}
		return data;
	}

	/** Legacy stacks retain their original vanilla count; an overflow cannot be recovered reliably. */
	public static ItemStack readStack(NBTTagCompound data) {
		if (!data.hasKey(FULL_COUNT_TAG, NBT.TAG_INT)) return new ItemStack(data);
		int count = data.getInteger(FULL_COUNT_TAG);
		if (count <= 0) return ItemStack.EMPTY;
		NBTTagCompound identity = data.copy();
		identity.setByte("Count", (byte) 1);
		ItemStack stack = new ItemStack(identity);
		if (!stack.isEmpty()) stack.setCount(count);
		return stack;
	}

	@Override
	public NBTTagCompound serializeNBT() {
		NBTTagList entries = new NBTTagList();
		for (int slot = 0; slot < getSlots(); ++slot) {
			ItemStack stack = this.stacks.get(slot);
			if (stack.isEmpty()) continue;
			NBTTagCompound entry = writeStack(stack);
			entry.setInteger("Slot", slot);
			entries.appendTag(entry);
		}
		NBTTagCompound data = new NBTTagCompound();
		data.setInteger("Size", getSlots());
		data.setTag("Items", entries);
		return data;
	}

	@Override
	public void deserializeNBT(NBTTagCompound data) {
		setSize(data.hasKey("Size", NBT.TAG_INT) ? Math.max(0, data.getInteger("Size")) : getSlots());
		NBTTagList entries = data.getTagList("Items", NBT.TAG_COMPOUND);
		for (int i = 0; i < entries.tagCount(); ++i) {
			NBTTagCompound entry = entries.getCompoundTagAt(i);
			int slot = entry.getInteger("Slot");
			if (slot >= 0 && slot < getSlots()) this.stacks.set(slot, readStack(entry));
		}
		onLoad();
	}

	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		if (amount <= 0) {
			return ItemStack.EMPTY;
		}

		validateSlotIndex(slot);

		ItemStack existing = this.stacks.get(slot);

		if (existing.isEmpty()) {
			return ItemStack.EMPTY;
		}

		if (existing.getCount() <= amount) {
			if (!simulate) {
				this.stacks.set(slot, ItemStack.EMPTY);
				onContentsChanged(slot);
			}
			return existing;
		} else {
			if (!simulate) {
				this.stacks.set(slot, ItemHandlerHelper.copyStackWithSize(existing, existing.getCount() - amount));
				onContentsChanged(slot);
			}

			return ItemHandlerHelper.copyStackWithSize(existing, amount);
		}
	}
}
