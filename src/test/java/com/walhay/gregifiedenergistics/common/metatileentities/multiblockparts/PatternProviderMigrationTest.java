package com.walhay.gregifiedenergistics.common.metatileentities.multiblockparts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.walhay.gregifiedenergistics.api.capability.PatternBufferStorage;
import gregtech.api.util.GTUtility;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PatternProviderMigrationTest {

	@BeforeAll
	static void bootstrap() {
		Bootstrap.register();
	}

	@Test
	void movesLegacyInputsToTheFirstPatternSlotForRecovery() {
		NBTTagCompound legacy = new NBTTagCompound();
		legacy.setByte("GhostCircuit", (byte) 8);
		ItemStackHandler patterns = new ItemStackHandler(1);
		patterns.setStackInSlot(0, new ItemStack(Items.PAPER));
		legacy.setTag("PatternInventory", patterns.serializeNBT());
		ItemStackHandler inputs = new ItemStackHandler(25);
		inputs.setStackInSlot(24, new ItemStack(Items.DIAMOND, 64));
		GTUtility.writeItems(inputs, "ImportInventory", legacy);
		NBTTagCompound original = legacy.copy();

		NBTTagCompound migrated = MTEMEPatternProvider.LegacyInventory.migrate(legacy);

		assertEquals(original, legacy);
		assertFalse(migrated.hasKey("ImportInventory"));
		NBTTagCompound migratedPatterns = migrated.getCompoundTag("PatternInventory");
		assertEquals(original.getCompoundTag("PatternInventory").getTag("Items"), migratedPatterns.getTag("Items"));
		var containers = migratedPatterns.getTagList("Containers", NBT.TAG_COMPOUND);
		assertEquals(1, containers.tagCount());
		NBTTagCompound container = containers.getCompoundTagAt(0);
		assertEquals(0, container.getInteger("Slot"));
		NBTTagCompound contents = container.getCompoundTag("Data");
		assertTrue(contents.getBoolean("PendingReconfiguration"));
		assertTrue(contents.getBoolean("PreserveCircuitOnReconfiguration"));
		assertEquals(8, contents.getCompoundTag("CircuitInventory").getInteger("GhostCircuit"));
		PatternBufferStorage restored = new PatternBufferStorage(() -> {});
		restored.deserializeNBT(contents);
		assertEquals(25, restored.items().getSlots());
		assertEquals(64, restored.items().getStackInSlot(24).getCount());

		// GregTech also writes ImportInventory for providers saved in the current format.
		GTUtility.writeItems(new ItemStackHandler(0), "ImportInventory", migrated);
		assertSame(migrated, MTEMEPatternProvider.LegacyInventory.migrate(migrated));
	}
}
