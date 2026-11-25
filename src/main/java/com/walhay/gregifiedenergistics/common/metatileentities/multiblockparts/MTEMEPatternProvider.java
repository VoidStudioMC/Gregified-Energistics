package com.walhay.gregifiedenergistics.common.metatileentities.multiblockparts;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import com.glodblock.github.common.item.fake.FakeFluids;
import com.walhay.gregifiedenergistics.client.render.GregifiedEnergisticsTextures;
import gregtech.api.GTValues;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;
import java.util.List;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Tier-sized processing pattern buffer for item inputs only. */
public class MTEMEPatternProvider extends MTEMEPatternBuffer {

	public MTEMEPatternProvider(ResourceLocation metaTileEntityId, int tier) {
		super(metaTileEntityId, tier, patternSlotsForTier(tier), false);
	}

	static int patternSlotsForTier(int tier) {
		int sizeRoot = 1 + Math.min(GTValues.UHV, tier);
		return sizeRoot * sizeRoot / 9 * 9;
	}

	@Override
	public MetaTileEntity createMetaTileEntity(IGregTechTileEntity holder) {
		return new MTEMEPatternProvider(metaTileEntityId, getTier());
	}

	@Override
	protected SimpleOverlayRenderer getOverlay() {
		return isOnline
				? GregifiedEnergisticsTextures.ME_PATTERN_PROVIDER_ACTIVE
				: GregifiedEnergisticsTextures.ME_PATTERN_PROVIDER;
	}

	@Override
	protected String getTooltipKey() {
		return "gregifiedenergistics.machine.me_pattern_provider";
	}

	@Override
	protected boolean isPatternSupported(ICraftingPatternDetails pattern) {
		return pattern != null
				&& !pattern.isCraftable()
				&& !hasFluids(pattern.getInputs())
				&& !hasFluids(pattern.getOutputs());
	}

	private static boolean hasFluids(IAEItemStack[] stacks) {
		if (stacks == null) return false;
		for (IAEItemStack stack : stacks) {
			if (stack != null && FakeFluids.isFluidFakeItem(stack.asItemStackRepresentation())) return true;
		}
		return false;
	}

	@Override
	public void addInformation(
			ItemStack stack, @Nullable World player, @NotNull List<String> tooltip, boolean advanced) {
		super.addInformation(stack, player, tooltip, advanced);
		tooltip.add(I18n.format(getTooltipKey() + ".tooltip"));
		tooltip.add(I18n.format(getTooltipKey() + ".capacity", getPatternSlotCount()));
	}

	@Override
	public void readFromNBT(NBTTagCompound data) {
		super.readFromNBT(migrateLegacyInventory(data));
	}

	static NBTTagCompound migrateLegacyInventory(NBTTagCompound data) {
		NBTTagCompound patterns = data.getCompoundTag("PatternInventory");
		if (patterns.hasKey("Containers", NBT.TAG_LIST) || !data.hasKey("ImportInventory", NBT.TAG_LIST)) return data;

		NBTTagCompound migrated = data.copy();
		NBTTagCompound items = new NBTTagCompound();
		items.setTag(
				"Items", data.getTagList("ImportInventory", NBT.TAG_COMPOUND).copy());
		NBTTagCompound contents = new NBTTagCompound();
		contents.setTag("Items", items);
		contents.setBoolean("PendingReconfiguration", true);
		NBTTagCompound circuit = new NBTTagCompound();
		if (data.hasKey("GhostCircuit", NBT.TAG_ANY_NUMERIC)) {
			circuit.setInteger("GhostCircuit", data.getInteger("GhostCircuit"));
		}
		contents.setTag("CircuitInventory", circuit);
		NBTTagCompound container = new NBTTagCompound();
		container.setInteger("Slot", 0);
		container.setTag("Data", contents);
		NBTTagList containers = new NBTTagList();
		containers.appendTag(container);
		NBTTagCompound migratedPatterns = patterns.copy();
		migratedPatterns.setTag("Containers", containers);
		migrated.setTag("PatternInventory", migratedPatterns);
		migrated.removeTag("ImportInventory");
		return migrated;
	}
}
