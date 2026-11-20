package com.walhay.gregifiedenergistics;

import net.minecraft.util.math.MathHelper;
import net.minecraftforge.common.config.Config;

@Config(modid = GregifiedEnergisticsMod.MOD_ID)
public class GregifiedEnergisticsConfig {

	@Config.Ignore
	public static final int MAX_SLOTS = 4096;

	@Config.Name("Multiblock parts config")
	public static MachineConfig machineConfig = new MachineConfig();

	@Config.Name("Gui Config")
	public static GuiConfig guiConfig = new GuiConfig();

	public static class MachineConfig {

		@Config.Name("Pattern Handler Size")
		@Config.Comment({"Pattern slots in the ME Assembly Line Bus", "Default: 36"})
		@Config.RangeInt(min = 1, max = MAX_SLOTS)
		@Config.RequiresMcRestart
		public int patternHandlerSize = 36;

		public int getPatternHandlerSize() {
			return MathHelper.clamp(patternHandlerSize, 1, MAX_SLOTS);
		}
	}

	public static class GuiConfig {

		private static final int MAX_COLUMNS = 16;
		private static final int DEFAULT_PATTERN_COLUMNS = 9;

		@Config.Name(value = "Slots per line in substitution grid")
		@Config.Comment("Default: 8")
		@Config.RangeInt(min = 1, max = MAX_COLUMNS)
		public int substitutionSlotsPerLine = 8;

		@Config.Name(value = "Slots per line in pattern grid")
		@Config.Comment({
			"0 = automatic, up to " + DEFAULT_PATTERN_COLUMNS + " columns",
			"Default: " + DEFAULT_PATTERN_COLUMNS
		})
		@Config.RangeInt(min = 0, max = MAX_COLUMNS)
		public int patternSlotsPerLine = DEFAULT_PATTERN_COLUMNS;

		public int getPatternColumns() {
			return patternSlotsPerLine <= 0
					? DEFAULT_PATTERN_COLUMNS
					: MathHelper.clamp(patternSlotsPerLine, 1, MAX_COLUMNS);
		}

		public int getSubstitutionColumns() {
			return MathHelper.clamp(substitutionSlotsPerLine, 1, MAX_COLUMNS);
		}
	}
}
