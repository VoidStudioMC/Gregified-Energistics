package com.walhay.gregifiedenergistics.api.capability;

import static com.walhay.gregifiedenergistics.GregifiedEnergisticsConfig.MAX_SLOTS;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.items.misc.ItemEncodedPattern;
import com.google.common.base.Objects;
import gregtech.api.items.itemhandlers.GTItemStackHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import java.util.Arrays;
import java.util.Collection;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

public abstract class AbstractPatternItemHandler extends GTItemStackHandler {

	private final int configuredSize;
	private ICraftingPatternDetails[] patterns;

	public AbstractPatternItemHandler(MetaTileEntity mte, int size) {
		super(mte, size);
		this.configuredSize = size;
		this.patterns = new ICraftingPatternDetails[size];
	}

	@Override
	public void deserializeNBT(NBTTagCompound data) {
		int requiredSize = configuredSize;
		NBTTagList items = data.getTagList("Items", NBT.TAG_COMPOUND);
		for (int i = 0; i < items.tagCount(); ++i) {
			NBTTagCompound entry = items.getCompoundTagAt(i);
			int slot = entry.getInteger("Slot");
			if (slot < 0 || new ItemStack(entry).isEmpty()) continue;
			if (slot >= Math.max(configuredSize, MAX_SLOTS)) {
				throw new IllegalArgumentException(
						"Saved pattern slot is outside the supported recovery range: " + slot);
			}
			requiredSize = Math.max(requiredSize, slot + 1);
		}
		// Ignore untrusted saved capacity, but retain every occupied overflow slot for recovery in the GUI.
		NBTTagCompound migrated = data.copy();
		migrated.setInteger("Size", requiredSize);
		this.patterns = new ICraftingPatternDetails[requiredSize];
		super.deserializeNBT(migrated);
	}

	@Override
	public void onContentsChanged(int slot) {
		super.onContentsChanged(slot);
		ItemStack stack = getStackInSlot(slot);
		ICraftingPatternDetails pattern = patterns[slot];

		if (stack.isEmpty()) {
			if (pattern != null) {
				patterns[slot] = null;
				onPatternUpdate();
			}
			return;
		}

		if (pattern != null && ItemStack.areItemStacksEqual(pattern.getPattern(), stack)) return;

		ICraftingPatternDetails newPattern = getPatternFromStack(stack);

		if (Objects.equal(pattern, newPattern)) return;

		patterns[slot] = newPattern;
		onPatternUpdate();
	}

	@Nullable public ICraftingPatternDetails getPatternDetails(int slot) {
		validateSlotIndex(slot);
		return patterns[slot];
	}

	public Collection<ICraftingPatternDetails> getPatterns() {
		return Arrays.asList(patterns);
	}

	@Override
	public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
		return stack.getItem() instanceof ItemEncodedPattern;
	}

	@Override
	protected int getStackLimit(int slot, @Nonnull ItemStack stack) {
		return 1;
	}

	protected abstract ICraftingPatternDetails getPatternFromStack(ItemStack stack);

	protected void onPatternUpdate() {}

	@Override
	protected void onLoad() {
		super.onLoad();
		for (int i = 0; i < getSlots(); ++i) {
			patterns[i] = getPatternFromStack(getStackInSlot(i));
		}
		onPatternUpdate();
	}
}
