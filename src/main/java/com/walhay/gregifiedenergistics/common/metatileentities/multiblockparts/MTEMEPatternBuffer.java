package com.walhay.gregifiedenergistics.common.metatileentities.multiblockparts;

import static gregtech.api.GTValues.ZPM;

import com.walhay.gregifiedenergistics.api.metatileentity.MetaTileEntityPatternBuffer;
import com.walhay.gregifiedenergistics.client.render.GregifiedEnergisticsTextures;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;
import net.minecraft.util.ResourceLocation;

/** Processing pattern buffer for item and fluid inputs. */
public class MTEMEPatternBuffer extends MetaTileEntityPatternBuffer {
	public MTEMEPatternBuffer(ResourceLocation metaTileEntityId) {
		super(metaTileEntityId, ZPM, 36, true);
	}

	@Override
	public MetaTileEntity createMetaTileEntity(IGregTechTileEntity holder) {
		return new MTEMEPatternBuffer(metaTileEntityId);
	}

	@Override
	protected String getTooltipKey() {
		return "gregifiedenergistics.machine.me_pattern_buffer";
	}

	@Override
	protected SimpleOverlayRenderer getOverlay() {
		return isOnline
				? GregifiedEnergisticsTextures.ME_PATTERN_BUFFER_ACTIVE
				: GregifiedEnergisticsTextures.ME_PATTERN_BUFFER;
	}
}
