package com.walhay.gregifiedenergistics.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.walhay.gregifiedenergistics.api.capability.InfiniteItemStackHandler;
import com.walhay.gregifiedenergistics.api.mui.DynamicItemSlot;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DynamicItemSlotConservationTest {

	@BeforeAll
	static void bootstrap() {
		Bootstrap.register();
	}

	@Test
	void pickupDebitsTheHiddenStackWhenRemainderEqualsTheDisplayLimit() {
		InfiniteItemStackHandler inventory = inventory(128);
		DynamicItemSlot slot = new DynamicItemSlot(inventory, 0);
		ItemStack displayed = slot.getStack();
		ItemStack taken = displayed.splitStack(64);
		slot.putStack(displayed);
		slot.onTake(null, taken);
		assertEquals(64, taken.getCount());
		assertEquals(64, inventory.getStackInSlot(0).getCount());
	}

	@Test
	void takingOneItemDebitsAStackCrossingIntoTheDisplayRange() {
		InfiniteItemStackHandler inventory = inventory(65);
		DynamicItemSlot slot = new DynamicItemSlot(inventory, 0);
		ItemStack displayed = slot.getStack();
		ItemStack taken = displayed.splitStack(1);
		slot.putStack(displayed);
		assertEquals(1, taken.getCount());
		assertEquals(64, inventory.getStackInSlot(0).getCount());
	}

	@Test
	void modularContainerQuickMovePreservesTotalItemsAcrossTheBoundary() {
		InfiniteItemStackHandler source = inventory(128);
		ItemStackHandler destination = new ItemStackHandler(1);
		ModularContainer container = new ModularContainer() {
			{
				addSlotToContainer(new DynamicItemSlot(source, 0));
			}

			@Override
			protected ItemStack transferItem(ModularSlot fromSlot, ItemStack fromStack) {
				return destination.insertItem(0, fromStack, false);
			}
		};
		container.transferStackInSlot(null, 0);
		assertEquals(64, destination.getStackInSlot(0).getCount());
		assertEquals(64, source.getStackInSlot(0).getCount());
		assertEquals(
				128,
				destination.getStackInSlot(0).getCount()
						+ source.getStackInSlot(0).getCount());
	}

	@Test
	void directExtractionAndOnTakeDebitOnlyTheExtractedAmount() {
		InfiniteItemStackHandler inventory = inventory(128);
		DynamicItemSlot slot = new DynamicItemSlot(inventory, 0);
		ItemStack taken = slot.decrStackSize(64);
		slot.onTake(null, taken);
		assertEquals(64, taken.getCount());
		assertEquals(64, inventory.getStackInSlot(0).getCount());
	}

	private static InfiniteItemStackHandler inventory(int count) {
		InfiniteItemStackHandler inventory = new InfiniteItemStackHandler(1);
		inventory.setStackInSlot(0, new ItemStack(Items.DIAMOND, count));
		return inventory;
	}
}
