package com.walhay.gregifiedenergistics.api.mui;

import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;

/** ItemSlot */
public class DynamicItemSlot extends ModularSlot {
	private final int handlerIndex;

	public DynamicItemSlot(IItemHandler itemHandler, int index) {
		super(itemHandler, index);
		this.handlerIndex = index;
	}

	private boolean isValidHandler() {
		return getItemHandler() != null
				&& handlerIndex >= 0
				&& handlerIndex < getItemHandler().getSlots();
	}

	private static int displayLimit(ItemStack stack) {
		return Math.min(64, stack.getMaxStackSize());
	}

	@Override
	public ItemStack getStack() {
		if (!isValidHandler()) return ItemStack.EMPTY;
		ItemStack stack = super.getStack();
		return stack.isEmpty()
				? ItemStack.EMPTY
				: ItemHandlerHelper.copyStackWithSize(stack, Math.min(stack.getCount(), displayLimit(stack)));
	}

	@Override
	public boolean canTakeStack(EntityPlayer playerIn) {
		return isValidHandler() && super.canTakeStack(playerIn);
	}

	@Override
	public boolean isItemValid(ItemStack stack) {
		if (!isValidHandler() || stack.isEmpty() || !isCanPut() || !getFilter().test(stack)) return false;
		ItemStack existing = getItemHandler().getStackInSlot(handlerIndex);
		if (!existing.isEmpty()
				&& existing.getCount() > displayLimit(existing)
				&& !ItemHandlerHelper.canItemStacksStack(existing, stack)) return false;
		return getItemHandler().isItemValid(handlerIndex, stack);
	}

	@Override
	public int getSlotStackLimit() {
		return isValidHandler() ? Math.min(64, getItemHandler().getSlotLimit(handlerIndex)) : 0;
	}

	@Override
	public int getItemStackLimit(ItemStack stack) {
		return Math.min(stack.getMaxStackSize(), getSlotStackLimit());
	}

	@Override
	public ItemStack decrStackSize(int amount) {
		if (!isValidHandler() || amount <= 0) return ItemStack.EMPTY;
		return getItemHandler().extractItem(handlerIndex, Math.min(amount, displayLimit(getStack())), false);
	}

	@Override
	public void putStack(ItemStack displayed) {
		if (!isValidHandler() || !(getItemHandler() instanceof IItemHandlerModifiable modifiable)) return;
		// Client replication replaces the display snapshot; only server clicks debit hidden contents.
		if (isInitialized()
				&& getSyncHandler().isValid()
				&& getSyncHandler().getSyncManager().isClient()) {
			super.putStack(displayed);
			return;
		}
		ItemStack actual = modifiable.getStackInSlot(handlerIndex);
		if (!actual.isEmpty() && actual.getCount() > displayLimit(actual)) {
			if (!displayed.isEmpty() && !ItemHandlerHelper.canItemStacksStack(actual, displayed)) return;
			int hidden = actual.getCount() - Math.min(actual.getCount(), displayLimit(actual));
			long count = (long) hidden + displayed.getCount();
			if (count > Integer.MAX_VALUE) return;
			displayed = ItemHandlerHelper.copyStackWithSize(actual, (int) count);
		}
		super.putStack(displayed);
	}
}
