package com.walhay.gregifiedenergistics.loader.recipes;

import static com.walhay.gregifiedenergistics.common.metatileentities.GregifiedEnergisticsMetaTileEntities.ME_ASSEMBLY_LINE_BUS;
import static com.walhay.gregifiedenergistics.common.metatileentities.GregifiedEnergisticsMetaTileEntities.ME_ASSEMBLY_LINE_OPTICAL_BUS;
import static com.walhay.gregifiedenergistics.common.metatileentities.GregifiedEnergisticsMetaTileEntities.ME_PATTERN_BUFFER;
import static com.walhay.gregifiedenergistics.common.metatileentities.GregifiedEnergisticsMetaTileEntities.ME_PATTERN_PROVIDER;
import static gregtech.api.GTValues.*;
import static gregtech.api.recipes.RecipeMaps.ASSEMBLER_RECIPES;
import static gregtech.api.recipes.RecipeMaps.ASSEMBLY_LINE_RECIPES;
import static gregtech.api.unification.material.Materials.*;
import static gregtech.api.unification.ore.OrePrefix.circuit;
import static gregtech.api.unification.ore.OrePrefix.wireGtQuadruple;
import static gregtech.common.blocks.MetaBlocks.OPTICAL_PIPES;
import static gregtech.common.items.MetaItems.*;
import static gregtech.common.metatileentities.MetaTileEntities.ADVANCED_DATA_ACCESS_HATCH;
import static gregtech.common.metatileentities.MetaTileEntities.DATA_ACCESS_HATCH;
import static gregtech.common.metatileentities.MetaTileEntities.FLUID_IMPORT_HATCH_ME;
import static gregtech.common.metatileentities.MetaTileEntities.HULL;
import static gregtech.common.metatileentities.MetaTileEntities.ITEM_IMPORT_BUS_ME;

import appeng.api.AEApi;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.unification.material.MarkerMaterials.Tier;
import gregtech.api.unification.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class GregifiedEnergisticsRecipes {

	public static void load() {
		Item iface = AEApi.instance()
				.definitions()
				.blocks()
				.iface()
				.maybeItem()
				.orElseThrow(() ->
						new IllegalStateException("Gregified Energistics requires the AE2 ME interface to be enabled"));

		loadPatternProviders(iface);
		loadPatternBuffer(iface);

		ASSEMBLY_LINE_RECIPES
				.recipeBuilder()
				.input(DATA_ACCESS_HATCH)
				.input(iface, 4)
				.input(ROBOT_ARM_LuV, 2)
				.input(ELECTRIC_MOTOR_LuV, 4)
				.input(CONVEYOR_MODULE_LuV, 2)
				.input(circuit, Tier.LuV, 2)
				.input(circuit, Tier.IV, 4)
				.input(wireGtQuadruple, IndiumTinBariumTitaniumCuprate, 8)
				.fluidInputs(SolderingAlloy.getFluid(8 * L))
				.fluidInputs(VanadiumGallium.getFluid(8 * L))
				.output(ME_ASSEMBLY_LINE_BUS)
				.EUt(VA[LuV])
				.duration(1200)
				.scannerResearch(r -> r.researchStack(new ItemStack(iface), false)
						.duration(3600)
						.EUt(VA[LuV]))
				.buildAndRegister();

		ASSEMBLY_LINE_RECIPES
				.recipeBuilder()
				.input(ADVANCED_DATA_ACCESS_HATCH)
				.input(ME_ASSEMBLY_LINE_BUS)
				.input(ROBOT_ARM_ZPM, 2)
				.input(ELECTRIC_MOTOR_ZPM, 4)
				.input(CONVEYOR_MODULE_ZPM, 2)
				.input(circuit, Tier.ZPM, 2)
				.input(circuit, Tier.LuV, 4)
				.input(OPTICAL_PIPES[0], 16)
				.input(wireGtQuadruple, UraniumRhodiumDinaquadide, 8)
				.fluidInputs(SolderingAlloy.getFluid(16 * L))
				.fluidInputs(VanadiumGallium.getFluid(16 * L))
				.output(ME_ASSEMBLY_LINE_OPTICAL_BUS)
				.EUt(100000)
				.duration(1200)
				.stationResearch(r -> r.researchStack(ME_ASSEMBLY_LINE_BUS.getStackForm(), false)
						.CWUt(8)
						.EUt(VA[ZPM]))
				.buildAndRegister();
	}

	private static void loadPatternProviders(Item iface) {
		ASSEMBLER_RECIPES
				.recipeBuilder()
				.input(HULL[IV])
				.input(iface, 2)
				.input(ROBOT_ARM_IV, 2)
				.input(CONVEYOR_MODULE_IV, 2)
				.input(circuit, Tier.IV, 4)
				.fluidInputs(SolderingAlloy.getFluid(4 * L))
				.output(ME_PATTERN_PROVIDER[0])
				.duration(400)
				.EUt(VA[IV])
				.buildAndRegister();

		MetaItem<?>.MetaValueItem[] arms = {ROBOT_ARM_IV, ROBOT_ARM_LuV, ROBOT_ARM_ZPM, ROBOT_ARM_UV};
		MetaItem<?>.MetaValueItem[] conveyors = {
			CONVEYOR_MODULE_IV, CONVEYOR_MODULE_LuV, CONVEYOR_MODULE_ZPM, CONVEYOR_MODULE_UV
		};
		Material[] circuits = {Tier.IV, Tier.LuV, Tier.ZPM, Tier.UV, Tier.UHV};
		for (int i = 1; i < ME_PATTERN_PROVIDER.length; i++) {
			int tier = IV + i;
			// UHV hulls/circuits exist in standard GT progression; UHV mechanical components require high-tier mode.
			int component = Math.min(i, arms.length - 1);
			int voltage = VA[Math.min(tier, UV)];
			var previous = ME_PATTERN_PROVIDER[i - 1];
			var builder = ASSEMBLY_LINE_RECIPES
					.recipeBuilder()
					.input(previous)
					.input(HULL[tier])
					.input(iface, 2)
					.input(arms[component], 2)
					.input(conveyors[component], 2)
					.input(circuit, circuits[i], 2)
					.input(circuit, circuits[i - 1], 4)
					.fluidInputs(SolderingAlloy.getFluid(8 * (i + 1) * L))
					.output(ME_PATTERN_PROVIDER[i])
					.duration(600 * (i + 1))
					.EUt(voltage);
			if (tier == LuV) {
				builder.scannerResearch(r -> r.researchStack(previous.getStackForm(), false)
						.duration(3600)
						.EUt(voltage));
			} else {
				int computation = 8 * i;
				builder.stationResearch(r -> r.researchStack(previous.getStackForm(), false)
						.CWUt(computation)
						.EUt(voltage));
			}
			builder.buildAndRegister();
		}
	}

	private static void loadPatternBuffer(Item iface) {
		ASSEMBLY_LINE_RECIPES
				.recipeBuilder()
				.input(ITEM_IMPORT_BUS_ME)
				.input(FLUID_IMPORT_HATCH_ME)
				.input(iface, 4)
				.input(ROBOT_ARM_ZPM, 4)
				.input(ELECTRIC_PUMP_ZPM, 2)
				.input(FIELD_GENERATOR_ZPM)
				.input(circuit, Tier.ZPM, 4)
				.input(circuit, Tier.LuV, 8)
				.fluidInputs(SolderingAlloy.getFluid(16 * L))
				.fluidInputs(VanadiumGallium.getFluid(16 * L))
				.output(ME_PATTERN_BUFFER)
				.duration(1200)
				.EUt(VA[ZPM])
				.stationResearch(r -> r.researchStack(ITEM_IMPORT_BUS_ME.getStackForm(), false)
						.CWUt(8)
						.EUt(VA[ZPM]))
				.buildAndRegister();
	}
}
