package com.walhay.gregifiedenergistics.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.walhay.gregifiedenergistics.api.capability.PatternBufferStorage;
import com.walhay.gregifiedenergistics.api.mui.DynamicItemSlot;
import java.util.Arrays;
import java.util.Collections;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PatternBufferStorageTest {
	@BeforeAll
	static void bootstrap() {
		Bootstrap.register();
	}

	@Test
	void refusesCumulativeItemOverflowWithoutChangingOwnedResources() {
		PatternBufferStorage storage = new PatternBufferStorage(() -> {});
		storage.configure(1, 1);
		storage.items().setStackInSlot(0, new ItemStack(Items.DIAMOND, Integer.MAX_VALUE - 8));
		storage.fluids().fill(new FluidStack(FluidRegistry.WATER, 1000), true);
		assertFalse(storage.insertAll(
				Arrays.asList(new ItemStack(Items.DIAMOND, 5), new ItemStack(Items.DIAMOND, 5)),
				Collections.singletonList(new FluidStack(FluidRegistry.WATER, 500))));
		assertEquals(Integer.MAX_VALUE - 8, storage.items().getStackInSlot(0).getCount());
		assertEquals(1000, storage.fluids().getTankAt(0).getFluidAmount());
	}

	@Test
	void refusesCumulativeFluidOverflowBeforeCommittingItems() {
		PatternBufferStorage storage = new PatternBufferStorage(() -> {});
		storage.configure(1, 1);
		storage.items().setStackInSlot(0, new ItemStack(Items.DIAMOND, 3));
		storage.fluids().fill(new FluidStack(FluidRegistry.WATER, Integer.MAX_VALUE - 8), true);
		assertFalse(storage.insertAll(
				Collections.singletonList(new ItemStack(Items.DIAMOND, 5)),
				Arrays.asList(new FluidStack(FluidRegistry.WATER, 5), new FluidStack(FluidRegistry.WATER, 5))));
		assertEquals(3, storage.items().getStackInSlot(0).getCount());
		assertEquals(Integer.MAX_VALUE - 8, storage.fluids().getTankAt(0).getFluidAmount());
	}

	@Test
	void cannotDiscardOwnedContentsAndRestoresThemWithoutAnActivePattern() {
		PatternBufferStorage original = new PatternBufferStorage(() -> {});
		original.configure(1, 1);
		ItemStack item = new ItemStack(Items.DIAMOND, 1000);
		NBTTagCompound tag = new NBTTagCompound();
		tag.setString("Owner", "test");
		item.setTagCompound(tag);
		assertTrue(original.insertAll(
				Collections.singletonList(item), Collections.singletonList(new FluidStack(FluidRegistry.WATER, 2500))));
		assertFalse(original.configure(0, 0));
		PatternBufferStorage restored = new PatternBufferStorage(() -> {});
		restored.deserializeNBT(original.serializeNBT());
		assertEquals(1000, restored.items().getStackInSlot(0).getCount());
		assertEquals(tag, restored.items().getStackInSlot(0).getTagCompound());
		assertEquals(2500, restored.fluids().getTankAt(0).getFluidAmount());
		assertFalse(restored.configure(0, 0));
		DynamicItemSlot recoverySlot = new DynamicItemSlot(restored.items(), 0);
		ItemStack displayed = recoverySlot.getStack();
		assertEquals(64, displayed.getCount());
		assertFalse(recoverySlot.isItemValid(new ItemStack(Items.COAL)));
		ItemStack removed = displayed.splitStack(64);
		recoverySlot.putStack(displayed);
		assertEquals(64, removed.getCount());
		assertEquals(936, restored.items().getStackInSlot(0).getCount());
		recoverySlot.decrStackSize(64);
		assertEquals(872, restored.items().getStackInSlot(0).getCount());
		restored.items().extractItem(0, Integer.MAX_VALUE, false);
		restored.fluids().drain(Integer.MAX_VALUE, true);
		assertTrue(restored.configure(0, 0));
		assertTrue(restored.isEmpty());
		assertTrue(recoverySlot.getStack().isEmpty());
		assertFalse(recoverySlot.isItemValid(new ItemStack(Items.DIAMOND)));
		assertEquals(0, recoverySlot.getSlotStackLimit());
	}
}
