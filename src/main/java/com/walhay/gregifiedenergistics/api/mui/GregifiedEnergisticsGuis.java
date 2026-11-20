package com.walhay.gregifiedenergistics.api.mui;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.Icon;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;

public final class GregifiedEnergisticsGuis {

	public static final int SLOT_SIZE = 18;
	public static final int PADDING = 7;
	public static final int CONTENT_TOP = 18;
	public static final int INVENTORY_SPACE = 94;
	public static final int MIN_WIDTH = GTGuis.DEFAULT_WIDTH + SLOT_SIZE + 5;
	public static final int GRID_VIEWPORT_HEIGHT = 4 * SLOT_SIZE + 6;

	private GregifiedEnergisticsGuis() {}

	public static ModularPanel createPanel(MetaTileEntity machine, int width, int height) {
		return GTGuis.createPanel(machine, width, height)
				.child(IKey.lang(machine.getMetaFullName())
						.asWidget()
						.name("machine_title")
						.pos(5, 5))
				.child(SlotGroupWidget.playerInventory(false).left(PADDING).bottom(PADDING));
	}

	public static Flow buttonColumn() {
		return Flow.column().right(PADDING).bottom(PADDING).width(SLOT_SIZE).height(4 * SLOT_SIZE + 5);
	}

	public static ToggleButton workingButton(BooleanSyncValue value) {
		Icon detail =
				GTGuiTextures.BUTTON_POWER_DETAIL.asIcon().size(SLOT_SIZE, 6).marginTop(24);
		return new ToggleButton()
				.name("power_button")
				.size(SLOT_SIZE)
				.disableHoverBackground()
				.overlay(true, detail, GTGuiTextures.BUTTON_POWER[1])
				.overlay(false, detail, GTGuiTextures.BUTTON_POWER[0])
				.value(value)
				.tooltipAutoUpdate(true)
				.tooltipBuilder(t -> t.addLine(IKey.lang(
						value.getBoolValue()
								? "gregifiedenergistics.gui.working.enabled"
								: "gregifiedenergistics.gui.working.disabled")))
				.marginTop(4)
				.top(3 * SLOT_SIZE + 5);
	}
}
