package com.walhay.gregifiedenergistics.mixins.gtceu.metatileentities;

import static gregtech.common.metatileentities.multi.electric.MetaTileEntityAssemblyLine.metaTileEntities;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.walhay.gregifiedenergistics.common.metatileentities.GregifiedEnergisticsMetaTileEntities;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.pattern.FactoryBlockPattern;
import gregtech.common.metatileentities.MetaTileEntities;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityAssemblyLine;
import java.util.Arrays;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(MetaTileEntityAssemblyLine.class)
public class AssemblyLineMixin {

	@ModifyArg(
			method = "createStructurePattern",
			at =
					@At(
							value = "INVOKE",
							target =
									"Lgregtech/api/pattern/FactoryBlockPattern;aisle([Ljava/lang/String;)Lgregtech/api/pattern/FactoryBlockPattern;"),
			remap = false)
	private String[] allowMEBusInControllerAisle(String[] aisle) {
		// GregTech marks the controller with S and the standard input bus with I.
		if (Arrays.stream(aisle).noneMatch(row -> row.indexOf('S') >= 0)) return aisle;
		String[] updated = Arrays.copyOf(aisle, aisle.length);
		for (int row = 0; row < updated.length; ++row) updated[row] = updated[row].replace('I', 'B');
		return updated;
	}

	@WrapOperation(
			method = "createStructurePattern",
			at =
					@At(
							value = "INVOKE",
							target =
									"Lgregtech/api/pattern/FactoryBlockPattern;build()Lgregtech/api/pattern/BlockPattern;"),
			remap = false)
	private BlockPattern registerMEBusPredicate(FactoryBlockPattern pattern, Operation<BlockPattern> original) {
		pattern.where(
				'B',
				metaTileEntities(
						MetaTileEntities.ITEM_IMPORT_BUS[0],
						GregifiedEnergisticsMetaTileEntities.ME_ASSEMBLY_LINE_BUS,
						GregifiedEnergisticsMetaTileEntities.ME_ASSEMBLY_LINE_OPTICAL_BUS));
		return original.call(pattern);
	}
}
