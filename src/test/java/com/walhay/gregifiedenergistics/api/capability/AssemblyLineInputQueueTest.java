package com.walhay.gregifiedenergistics.api.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.util.inv.AdaptorItemHandler;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AssemblyLineInputQueueTest {

	@BeforeAll
	static void bootstrap() {
		Bootstrap.register();
	}

	@Test
	void restoresOnlyOwnedLegacyFluidAndKeepsItsDestinationAfterPartialDelivery() {
		NBTTagCompound legacy = new NBTTagCompound();
		legacy.setTag("FluidToSend", fluidEntries(0, 1000));
		legacy.setTag("ExtractedFluids", fluidEntries(1, 750));
		AtomicInteger changes = new AtomicInteger();
		AssemblyLineInputQueue queue = new AssemblyLineInputQueue(changes::incrementAndGet);
		queue.readFromNBT(legacy);

		FluidTank first = new FluidTank(1000);
		FluidTank second = new FluidTank(300);
		queue.deliverFluids(Arrays.asList(first, second));
		assertEquals(1, changes.get());
		assertEquals(0, first.getFluidAmount());
		assertEquals(300, second.getFluidAmount());

		queue.writeToNBT(legacy);
		assertFalse(legacy.hasKey("ExtractedFluids"));
		assertEquals(1, legacy.getInteger("FluidQueueVersion"));
		AssemblyLineInputQueue reloaded = new AssemblyLineInputQueue(changes::incrementAndGet);
		reloaded.readFromNBT(legacy);
		reloaded.deliverFluids(Collections.singletonList(first));
		assertEquals(1, changes.get());
		FluidTank replacement = new FluidTank(1000);
		reloaded.deliverFluids(Arrays.asList(first, replacement));
		assertEquals(2, changes.get());
		assertEquals(0, first.getFluidAmount());
		assertEquals(450, replacement.getFluidAmount());
		assertFalse(reloaded.hasFluids());
	}

	@Test
	void keepsTheFullItemCountUntilItsDestinationCanAcceptIt() {
		NBTTagCompound data = new NBTTagCompound();
		NBTTagCompound item = InfiniteItemStackHandler.writeStack(new ItemStack(Items.PAPER, 200));
		item.setInteger("Slot", 1);
		NBTTagList entries = new NBTTagList();
		entries.appendTag(item);
		data.setTag("ItemToSend", entries);
		AtomicInteger changes = new AtomicInteger();
		AssemblyLineInputQueue queue = new AssemblyLineInputQueue(changes::incrementAndGet);
		queue.readFromNBT(data);
		ItemStackHandler first = new ItemStackHandler(4);
		ItemStackHandler second = new ItemStackHandler(1);
		queue.deliverItems(Collections.singletonList(new AdaptorItemHandler(first)));
		assertEquals(0, changes.get());
		queue.deliverItems(Arrays.asList(new AdaptorItemHandler(first), new AdaptorItemHandler(second)));
		assertEquals(1, changes.get());
		assertTrue(first.getStackInSlot(0).isEmpty());
		assertEquals(64, second.getStackInSlot(0).getCount());

		queue.writeToNBT(data);
		AssemblyLineInputQueue reloaded = new AssemblyLineInputQueue(changes::incrementAndGet);
		reloaded.readFromNBT(data);
		second = new ItemStackHandler(3);
		reloaded.deliverItems(Arrays.asList(new AdaptorItemHandler(first), new AdaptorItemHandler(second)));
		assertEquals(2, changes.get());
		assertEquals(64, second.getStackInSlot(0).getCount());
		assertEquals(64, second.getStackInSlot(1).getCount());
		assertEquals(8, second.getStackInSlot(2).getCount());
		assertFalse(reloaded.hasItems());
	}

	private static NBTTagList fluidEntries(int slot, int amount) {
		NBTTagList entries = new NBTTagList();
		NBTTagCompound entry = new FluidStack(FluidRegistry.WATER, amount).writeToNBT(new NBTTagCompound());
		entry.setInteger("Slot", slot);
		entries.appendTag(entry);
		return entries;
	}
}
