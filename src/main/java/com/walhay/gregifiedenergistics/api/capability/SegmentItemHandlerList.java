package com.walhay.gregifiedenergistics.api.capability;

import com.github.bsideup.jabel.Desugar;
import gregtech.api.capability.impl.ItemHandlerList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * SegmentItemHandlerList
 *
 * <p>Main idea of that class is to provide mutability of handler list unlike immutable {@link ItemHandlerList} which
 * caches handlers on construction.
 */
public class SegmentItemHandlerList implements IItemHandlerModifiable {

	private final List<IItemHandler> handlers = new ArrayList<>();

	public SegmentItemHandlerList(IItemHandler... handlers) {
		for (IItemHandler handler : handlers) {
			if (handler == null) continue;

			addHandler(handler);
		}
	}

	public SegmentItemHandlerList(Collection<IItemHandler> handlers) {
		handlers.stream().filter(Objects::nonNull).forEach(this::addHandler);
	}

	public void addHandler(IItemHandler handler) {
		Objects.requireNonNull(handler, "handler");
		if (handlers.contains(handler)) return;

		handlers.add(handler);
	}

	protected HandlerEntry getHandlerByGlobalIndex(int index) {
		if (index < 0) return new HandlerEntry(null, 0);
		int offset = 0;
		int localSlot = index;
		for (IItemHandler handler : handlers) {
			int slots = handler.getSlots();
			if (localSlot < slots) return new HandlerEntry(handler, offset);
			localSlot -= slots;
			offset += slots;
		}
		return new HandlerEntry(null, 0);
	}

	@Override
	public int getSlots() {
		return handlers.stream().mapToInt(IItemHandler::getSlots).sum();
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		HandlerEntry entry = getHandlerByGlobalIndex(slot);

		return entry.handler == null ? ItemStack.EMPTY : entry.handler.getStackInSlot(slot - entry.index);
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
		HandlerEntry entry = getHandlerByGlobalIndex(slot);

		return entry.handler == null ? stack : entry.handler.insertItem(slot - entry.index, stack, simulate);
	}

	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		HandlerEntry entry = getHandlerByGlobalIndex(slot);

		return entry.handler == null
				? ItemStack.EMPTY
				: entry.handler.extractItem(slot - entry.index, amount, simulate);
	}

	@Override
	public int getSlotLimit(int slot) {
		HandlerEntry entry = getHandlerByGlobalIndex(slot);

		return entry.handler == null ? 0 : entry.handler.getSlotLimit(slot - entry.index);
	}

	@Override
	public void setStackInSlot(int slot, ItemStack stack) {
		HandlerEntry entry = getHandlerByGlobalIndex(slot);

		if (entry.handler instanceof IItemHandlerModifiable modifiable) {
			modifiable.setStackInSlot(slot - entry.index, stack);
		} else if (entry.handler != null) {
			throw new UnsupportedOperationException("Delegate does not support assigning slot contents");
		}
	}

	@Desugar
	public record HandlerEntry(IItemHandler handler, int index) {}
}
