package com.walhay.gregifiedenergistics.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.walhay.gregifiedenergistics.api.patterns.substitutions.AESubstitutionStorage;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class SubstitutionPersistenceTest {

	@BeforeAll
	static void bootstrap() {
		Bootstrap.register();
	}

	@Test
	void choicesSurviveTwoSaveCycles() {
		AESubstitutionStorage storage = new AESubstitutionStorage(new NBTTagCompound());
		storage.setOption("circuitLuV", 2);
		storage = new AESubstitutionStorage(storage.serializeNBT());
		assertEquals(2, storage.getOption("circuitLuV"));
		storage = new AESubstitutionStorage(storage.serializeNBT());
		assertEquals(2, storage.getOption("circuitLuV"));
	}

	@Test
	void loadingAnEmptySnapshotClearsOldChoices() {
		AESubstitutionStorage storage = new AESubstitutionStorage(new NBTTagCompound());
		storage.setOption("circuitLuV", 2);
		storage.deserializeNBT(new NBTTagCompound());
		assertTrue(storage.getOptions().isEmpty());
	}

	@Test
	void selectedItemSurvivesCandidateReorderingAndReload() {
		AESubstitutionStorage storage = new AESubstitutionStorage(new NBTTagCompound());
		ItemStack first = new ItemStack(Items.DIAMOND);
		ItemStack second = new ItemStack(Items.DIAMOND);
		NBTTagCompound identity = new NBTTagCompound();
		identity.setString("variant", "second");
		second.setTagCompound(identity);
		storage.registerCandidates("variants", new ItemStack[] {first, second});
		storage.setOption("variants", 1);
		storage = new AESubstitutionStorage(storage.serializeNBT());
		storage.registerCandidates("variants", new ItemStack[] {second, first});
		assertEquals(0, storage.getOption("variants"));
		assertEquals(identity, storage.getCandidates("variants").get(0).getTagCompound());
	}

	@Test
	void invalidIndicesAndEmptyCandidatesDoNotReplaceTheChoice() {
		AESubstitutionStorage storage = new AESubstitutionStorage(new NBTTagCompound());
		storage.registerCandidates(
				"variants", new ItemStack[] {ItemStack.EMPTY, new ItemStack(Items.DIAMOND), new ItemStack(Items.COAL)});
		storage.setOption("variants", 1);
		storage.setOption("variants", -1);
		storage.setOption("variants", Integer.MAX_VALUE);
		assertEquals(1, storage.getOption("variants"));
		assertEquals(Items.COAL, storage.getCandidates("variants").get(1).getItem());
		storage.registerCandidates("empty", new ItemStack[0]);
		storage.setOption("empty", 1);
		assertEquals(0, storage.getOption("empty"));
	}
}
