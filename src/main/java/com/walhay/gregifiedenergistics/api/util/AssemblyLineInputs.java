package com.walhay.gregifiedenergistics.api.util;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import com.glodblock.github.common.item.ItemFluidPacket;
import com.glodblock.github.common.item.fake.FakeFluids;
import com.glodblock.github.common.item.fake.FakeItemRegister;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** Separates CPU-owned inputs into independently ordered item buses and fluid hatches. */
public final class AssemblyLineInputs {
	public final List<ItemStack> items = new ArrayList<>();
	public final List<FluidStack> fluids = new ArrayList<>();

	private AssemblyLineInputs() {}

	public boolean matchesFluidInputs(ICraftingPatternDetails pattern) {
		if (pattern == null) return false;
		IAEItemStack[] expectedInputs = pattern.getInputs();
		if (expectedInputs == null) return false;
		int slot = 0;
		for (IAEItemStack input : expectedInputs) {
			if (input == null || !FakeFluids.isFluidFakeItem(input.getDefinition())) continue;
			if (input.getStackSize() <= 0 || input.getStackSize() > Integer.MAX_VALUE || slot >= fluids.size())
				return false;
			if (input.getItem() instanceof ItemFluidPacket && input.getStackSize() != 1) return false;
			FluidStack expected = FakeItemRegister.getStack(input);
			if (expected == null || !expected.isFluidStackIdentical(fluids.get(slot++))) return false;
		}
		return slot == fluids.size();
	}

	@Nullable public static AssemblyLineInputs read(InventoryCrafting inventory) {
		AssemblyLineInputs inputs = new AssemblyLineInputs();
		for (int i = 0; i < inventory.getSizeInventory(); ++i) {
			ItemStack stack = inventory.getStackInSlot(i);
			if (stack.isEmpty()) continue;
			if (FakeFluids.isFluidFakeItem(stack)) {
				// AE2FC stores the entire amount inside one packet; its item count is not a fluid multiplier.
				if (stack.getItem() instanceof ItemFluidPacket && stack.getCount() != 1) return null;
				FluidStack fluid = FakeItemRegister.getStack(stack);
				if (fluid == null || fluid.amount <= 0) return null;
				inputs.fluids.add(fluid.copy());
			} else {
				if (FakeItemRegister.isFakeItem(stack)) return null;
				inputs.items.add(stack.copy());
			}
		}
		return inputs;
	}
}
