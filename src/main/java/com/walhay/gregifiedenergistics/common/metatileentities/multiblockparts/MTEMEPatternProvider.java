package com.walhay.gregifiedenergistics.common.metatileentities.multiblockparts;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import com.glodblock.github.common.item.fake.FakeFluids;
import com.walhay.gregifiedenergistics.api.capability.PatternBufferItemHandler;
import com.walhay.gregifiedenergistics.api.capability.PatternBufferStorage;
import com.walhay.gregifiedenergistics.api.metatileentity.MTEAbstractPatternProvider;
import com.walhay.gregifiedenergistics.client.render.GregifiedEnergisticsTextures;
import gregtech.api.GTValues;
import gregtech.api.capability.impl.GhostCircuitItemStackHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;
import java.util.List;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Tier-sized processing pattern buffer for item inputs only. */
public class MTEMEPatternProvider extends MTEAbstractPatternProvider {

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
	protected PatternBufferItemHandler createPatternBufferHandler(
			PatternBufferStorage storage, GhostCircuitItemStackHandler circuitInventory) {
		return new PatternBufferItemHandler(storage.items(), circuitInventory);
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
}
