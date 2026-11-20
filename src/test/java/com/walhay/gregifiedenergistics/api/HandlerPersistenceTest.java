package com.walhay.gregifiedenergistics.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import com.walhay.gregifiedenergistics.api.capability.AbstractPatternItemHandler;
import com.walhay.gregifiedenergistics.api.capability.InfiniteItemStackHandler;
import com.walhay.gregifiedenergistics.api.capability.SegmentItemHandlerList;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HandlerPersistenceTest {

	@BeforeAll
	static void bootstrap() {
		Bootstrap.register();
	}

	@Test
	void preservesLargeTaggedStacks() {
		for (int count : new int[] {64, 127, 128, 255, 256, 1000, Integer.MAX_VALUE}) {
			InfiniteItemStackHandler original = new InfiniteItemStackHandler(1);
			ItemStack stack = new ItemStack(Items.DIAMOND, count, 3);
			NBTTagCompound tag = new NBTTagCompound();
			tag.setString("Owner", "test");
			stack.setTagCompound(tag);
			original.setStackInSlot(0, stack);

			InfiniteItemStackHandler restored = new InfiniteItemStackHandler(0);
			restored.deserializeNBT(original.serializeNBT());
			ItemStack actual = restored.getStackInSlot(0);
			assertEquals(count, actual.getCount());
			assertEquals(stack.getItem(), actual.getItem());
			assertEquals(stack.getMetadata(), actual.getMetadata());
			assertEquals(tag, actual.getTagCompound());
		}
	}

	@Test
	void readsLegacyStacksWithoutInventingOverflowCounts() {
		ItemStackHandler legacy = new ItemStackHandler(1);
		legacy.setStackInSlot(0, new ItemStack(Items.DIAMOND, 64));
		InfiniteItemStackHandler restored = new InfiniteItemStackHandler(0);
		restored.deserializeNBT(legacy.serializeNBT());
		assertEquals(64, restored.getStackInSlot(0).getCount());
		legacy.setStackInSlot(0, new ItemStack(Items.DIAMOND, 128));
		restored.deserializeNBT(legacy.serializeNBT());
		assertEquals(0, restored.getStackInSlot(0).getCount());
	}

	@Test
	void retainsOccupiedOverflowSlotsAndAllowsEmptyCapacityToShrink() {
		ItemStackHandler saved = new ItemStackHandler(36);
		saved.setStackInSlot(35, new ItemStack(Items.DIAMOND, 4));
		PatternHandler smaller = new PatternHandler(16);
		smaller.deserializeNBT(saved.serializeNBT());
		assertEquals(36, smaller.getSlots());
		assertEquals(4, smaller.getStackInSlot(35).getCount());
		assertEquals(36, smaller.getPatterns().size());

		PatternHandler loadedAgain = new PatternHandler(16);
		loadedAgain.deserializeNBT(smaller.serializeNBT());
		assertEquals(4, loadedAgain.getStackInSlot(35).getCount());
		saved.setStackInSlot(35, ItemStack.EMPTY);
		smaller.deserializeNBT(saved.serializeNBT());
		assertEquals(16, smaller.getSlots());
		assertEquals(16, smaller.getPatterns().size());

		PatternHandler larger = new PatternHandler(48);
		larger.deserializeNBT(saved.serializeNBT());
		assertEquals(48, larger.getSlots());
		assertEquals(48, larger.getPatterns().size());
	}

	@Test
	void ignoresUntrustedEmptySavedCapacityAndRejectsHugeOccupiedIndices() {
		NBTTagCompound data = new NBTTagCompound();
		data.setInteger("Size", Integer.MAX_VALUE);
		PatternHandler handler = new PatternHandler(16);
		handler.deserializeNBT(data);
		assertEquals(16, handler.getSlots());
		NBTTagList entries = new NBTTagList();
		NBTTagCompound entry = new ItemStack(Items.DIAMOND).writeToNBT(new NBTTagCompound());
		entry.setInteger("Slot", Integer.MAX_VALUE);
		entries.appendTag(entry);
		data.setTag("Items", entries);
		assertThrows(IllegalArgumentException.class, () -> handler.deserializeNBT(data));
	}

	@Test
	void immutableDelegateReplacementCannotDiscardExistingItems() {
		ItemStackHandler delegate = new ItemStackHandler(1);
		delegate.setStackInSlot(0, new ItemStack(Items.DIAMOND, 10));
		IItemHandler immutable = new IItemHandler() {
			public int getSlots() {
				return delegate.getSlots();
			}

			public ItemStack getStackInSlot(int slot) {
				return delegate.getStackInSlot(slot);
			}

			public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
				return stack.getItem() == Items.DIAMOND ? delegate.insertItem(slot, stack, simulate) : stack;
			}

			public ItemStack extractItem(int slot, int amount, boolean simulate) {
				return delegate.extractItem(slot, amount, simulate);
			}

			public int getSlotLimit(int slot) {
				return delegate.getSlotLimit(slot);
			}
		};
		SegmentItemHandlerList combined = new SegmentItemHandlerList(immutable);
		assertThrows(
				UnsupportedOperationException.class, () -> combined.setStackInSlot(0, new ItemStack(Items.COAL, 5)));
		assertFalse(delegate.getStackInSlot(0).isEmpty());
		assertEquals(10, delegate.getStackInSlot(0).getCount());
	}

	private static class PatternHandler extends AbstractPatternItemHandler {
		PatternHandler(int size) {
			super(null, size);
		}

		@Override
		protected ICraftingPatternDetails getPatternFromStack(ItemStack stack) {
			return null;
		}
	}
}
