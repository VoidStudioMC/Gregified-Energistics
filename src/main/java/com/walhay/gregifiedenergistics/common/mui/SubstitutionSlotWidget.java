package com.walhay.gregifiedenergistics.common.mui;

import static com.walhay.gregifiedenergistics.api.mui.GregifiedEnergisticsGuis.CONTENT_TOP;
import static com.walhay.gregifiedenergistics.api.mui.GregifiedEnergisticsGuis.MIN_WIDTH;
import static com.walhay.gregifiedenergistics.api.mui.GregifiedEnergisticsGuis.PADDING;
import static com.walhay.gregifiedenergistics.api.mui.GregifiedEnergisticsGuis.SLOT_SIZE;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.value.ISyncOrValue;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.MouseData;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.walhay.gregifiedenergistics.api.patterns.ISubstitutionStorage;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.client.utils.TooltipHelper;
import java.io.IOException;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.NotNull;

public class SubstitutionSlotWidget extends Widget<SubstitutionSlotWidget> implements Interactable {

	private static final int CLICK = 2;
	private static final int SCROLL = 3;

	private List<ItemStack> items = java.util.Collections.emptyList();
	private ISubstitutionStorage storage;
	private SubstitutionSyncHandler syncHandler;
	private String name;
	private ItemDrawable itemPreview;

	public SubstitutionSlotWidget() {
		size(SLOT_SIZE);
		background(GTGuiTextures.SLOT);
		this.itemPreview = new ItemDrawable();
		overlay(itemPreview.asIcon().alignment(Alignment.CENTER));
		onUpdateListener(w -> {
			if (storage == null || syncHandler == null) return;
			items = storage.getCandidates(name);
			itemPreview.setItem(items.isEmpty() ? ItemStack.EMPTY : items.get(syncHandler.getOption(items.size())));
		});
	}

	public SubstitutionSlotWidget storage(ISubstitutionStorage storage, String name, PanelSyncManager manager) {
		this.name = name;
		this.storage = storage;
		this.items = storage.getCandidates(name);
		this.syncHandler = manager.getOrCreateSyncHandler(
				"substitution:" + name,
				SubstitutionSyncHandler.class,
				() -> new SubstitutionSyncHandler(storage, name));
		setSyncOrValue(syncHandler);

		return this;
	}

	@Override
	public void onInit() {
		tooltip().setAutoUpdate(true);
		tooltip().tooltipBuilder(rt -> {
			if (!items.isEmpty()) rt.addFromItem(items.get(syncHandler.getOption(items.size())));
			rt.addLine(IKey.lang("gregifiedenergistics.gui.substitution_hint"));
		});
	}

	@Override
	public boolean isValidSyncOrValue(@NotNull ISyncOrValue syncOrValue) {
		return syncOrValue instanceof SubstitutionSyncHandler;
	}

	@Override
	public SubstitutionSyncHandler getSyncHandler() {
		return syncHandler;
	}

	private IPanelHandler selectorPanel() {
		if (syncHandler.selectorPanel == null) {
			syncHandler.selectorPanel = IPanelHandler.simple(
					getPanel(),
					(parentPanel, player) -> {
						ItemDrawable preview = new ItemDrawable();
						return GTGuis.createPopupPanel("selector_panel_" + name, MIN_WIDTH, 174)
								.child(IKey.lang("gregifiedenergistics.gui.substitution")
										.asWidget()
										.pos(5, 5))
								.child(new Widget<>()
										.size(SLOT_SIZE)
										.top(CONTENT_TOP)
										.tooltipAutoUpdate(true)
										.tooltipBuilder(rt -> {
											ItemStack selected = selectedItem();
											if (!selected.isEmpty()) rt.addFromItem(selected);
										})
										.onUpdateListener(w -> preview.setItem(selectedItem()))
										.horizontalCenter()
										.background(GTGuiTextures.SLOT)
										.overlay(preview.asIcon().margin(1)))
								.child(new CandidateList().pos(PADDING, 41).size(MIN_WIDTH - 2 * PADDING, 126));
					},
					true);
		}
		return syncHandler.selectorPanel;
	}

	@Override
	public void dispose() {
		if (syncHandler.selectorPanel != null) {
			syncHandler.selectorPanel.closePanel();
		}
		super.dispose();
	}

	private class CandidateList extends ListWidget<Widget<?>, CandidateList> {
		private List<ItemStack> shown;

		@Override
		public void onUpdate() {
			super.onUpdate();
			List<ItemStack> candidates = storage.getCandidates(name);
			if (sameCandidates(shown, candidates)) return;
			shown = candidates;
			removeAll();
			child(new Grid()
					.minElementMargin(0)
					.minColWidth(SLOT_SIZE)
					.minRowHeight(SLOT_SIZE)
					.coverChildren()
					.horizontalCenter()
					.mapTo(
							Math.max(1, Math.min(9, candidates.size())),
							candidates,
							(index, item) -> candidateButton(item)));
			scheduleResize();
		}
	}

	private Widget<?> candidateButton(ItemStack item) {
		return new ButtonWidget<>()
				.size(SLOT_SIZE)
				.background(GTGuiTextures.SLOT)
				.overlay(new ItemDrawable(item).asIcon().margin(1))
				.tooltip(rt -> rt.addFromItem(item))
				.onMousePressed(button -> syncHandler.setSubstitutionItem(item));
	}

	private ItemStack selectedItem() {
		List<ItemStack> candidates = storage.getCandidates(name);
		return candidates.isEmpty() ? ItemStack.EMPTY : candidates.get(syncHandler.getOption(candidates.size()));
	}

	private static boolean sameCandidates(List<ItemStack> first, List<ItemStack> second) {
		if (first == null || first.size() != second.size()) return false;
		for (int i = 0; i < first.size(); ++i) {
			if (!ItemStack.areItemStacksEqual(first.get(i), second.get(i))) return false;
		}
		return true;
	}

	@Override
	public @NotNull Result onMousePressed(int mouseButton) {
		// Input can arrive during the parent screen's closing animation. Do not open or
		// close a cached secondary panel after its parent has been detached.
		if (getPanel() == null || !getPanel().isOpen() || items.isEmpty()) return Result.ACCEPT;

		if (mouseButton == 0 && TooltipHelper.isShiftDown()) {
			selectorPanel().togglePanel();
		} else {
			MouseData mouseData = MouseData.create(mouseButton);
			syncHandler.syncToServer(CLICK, mouseData::writeToPacket);
		}
		return Result.SUCCESS;
	}

	@Override
	public boolean onMouseScroll(UpOrDown scrollDirection, int amount) {
		if (getPanel() == null || !getPanel().isOpen() || items.isEmpty()) return false;
		MouseData mouseData = MouseData.create(scrollDirection.modifier);
		syncHandler.syncToServer(SCROLL, mouseData::writeToPacket);
		return true;
	}

	public static class SubstitutionSyncHandler extends SyncHandler {

		// Stable across dynamic grid rebuilds; MUI identifies secondary panels by name.
		private IPanelHandler selectorPanel;

		private final ISubstitutionStorage storage;
		private final String name;
		public static final int SET_SUBSTITUTION = 0;

		public SubstitutionSyncHandler(ISubstitutionStorage storage, String name) {
			this.storage = storage;
			this.name = name;
		}

		@Override
		public void readOnClient(int id, PacketBuffer buf) throws IOException {
			if (id == SET_SUBSTITUTION) readSelection(buf, false);
		}

		@Override
		public void readOnServer(int id, PacketBuffer buf) throws IOException {
			if (id == SET_SUBSTITUTION) readSelection(buf, true);
			else if (id == CLICK) phantomClick(MouseData.readPacket(buf));
			else if (id == SCROLL) phantomScroll(MouseData.readPacket(buf));
		}

		private void readSelection(PacketBuffer buf, boolean respond) throws IOException {
			int requested = buf.readVarInt();
			net.minecraft.nbt.NBTTagCompound tag = buf.readCompoundTag();
			if (tag == null) return;
			ItemStack identity = new ItemStack(tag);
			List<ItemStack> candidates = storage.getCandidates(name);
			if (requested < 0 || requested >= candidates.size()) return;
			if (!ItemStack.areItemStacksEqual(identity, candidates.get(requested))) return;
			if (respond) setSubstitution(requested);
			else storage.setOption(name, requested);
		}

		public boolean setSubstitution(int option) {
			List<ItemStack> candidates = storage.getCandidates(name);
			if (option < 0 || option >= candidates.size()) return false;
			if (getOption(candidates.size()) == option) return false;
			storage.setOption(name, option);
			ItemStack identity = candidates.get(option);
			sync(SET_SUBSTITUTION, buf -> {
				buf.writeVarInt(option);
				buf.writeCompoundTag(identity.serializeNBT());
			});
			return true;
		}

		private boolean setSubstitutionItem(ItemStack identity) {
			List<ItemStack> candidates = storage.getCandidates(name);
			for (int i = 0; i < candidates.size(); ++i) {
				if (ItemStack.areItemStacksEqual(identity, candidates.get(i))) return setSubstitution(i);
			}
			return false;
		}

		protected int getNextOption(int value) {
			int size = storage.getCandidates(name).size();
			return size == 0 ? 0 : (int) Math.max(0L, Math.min(size - 1L, (long) getOption(size) + value));
		}

		protected void phantomClick(MouseData mouseData) {
			if (mouseData.mouseButton == 0) {
				setSubstitution(getNextOption(1));
			} else if (mouseData.mouseButton == 1) {
				setSubstitution(getNextOption(-1));
			}
		}

		protected void phantomScroll(MouseData mouseData) {
			setSubstitution(getNextOption(mouseData.mouseButton));
		}

		protected int getOption(int size) {
			int option = storage.getOption(name);
			return size == 0 ? 0 : MathHelper.clamp(option, 0, size - 1);
		}
	}
}
