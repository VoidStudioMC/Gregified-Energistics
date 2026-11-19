package com.walhay.gregifiedenergistics.common.mui;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.value.ISyncOrValue;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.MouseData;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
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

	private List<ItemStack> items = java.util.Collections.emptyList();
	private ISubstitutionStorage storage;
	private SubstituionSyncHandler syncHandler;
	private String name;
	private IPanelHandler selectorPanel;
	private ItemDrawable itemPreview;

	public SubstitutionSlotWidget() {
		size(18);
		background(GTGuiTextures.SLOT);
		this.itemPreview = new ItemDrawable();
		overlay(itemPreview.asIcon().alignment(Alignment.CENTER));
		onUpdateListener(w -> {
			if (storage == null || syncHandler == null) return;
			items = storage.getCandidates(name);
			itemPreview.setItem(items.isEmpty() ? ItemStack.EMPTY : items.get(syncHandler.getOption()));
		});
	}

	public SubstitutionSlotWidget storage(ISubstitutionStorage storage, String name) {
		this.name = name;
		this.storage = storage;
		this.items = storage.getCandidates(name);
		this.syncHandler = new SubstituionSyncHandler(storage, name);
		setSyncOrValue(syncHandler);

		return this;
	}

	@Override
	public void onInit() {
		tooltip().setAutoUpdate(true);
		tooltip().tooltipBuilder(rt -> {
			if (!items.isEmpty()) rt.addFromItem(items.get(syncHandler.getOption()));
			rt.addLine(IKey.lang("gregifiedenergistics.gui.substitution_hint"));
		});
	}

	@Override
	public boolean isValidSyncOrValue(@NotNull ISyncOrValue syncOrValue) {
		return syncOrValue instanceof SubstituionSyncHandler;
	}

	@Override
	public SubstituionSyncHandler getSyncHandler() {
		return syncHandler;
	}

	private IPanelHandler selectorPanel() {
		if (selectorPanel == null) {
			selectorPanel = IPanelHandler.simple(
					getPanel(),
					(parentPanel, player) -> {
						int height = (int) Math.ceil(items.size() / 5.0f);

						var selectedItem = items.isEmpty() ? ItemStack.EMPTY : items.get(syncHandler.getOption());
						return GTGuis.createPopupPanel("selector_panel_" + name, 104, height * 18 + 50)
								.child(IKey.lang("gregifiedenergistics.gui.substitution")
										.asWidget()
										.pos(5, 5))
								.child(new Widget<>()
										.size(18)
										.top(19)
										.tooltip(rt -> rt.addFromItem(selectedItem))
										.horizontalCenter()
										.background(GTGuiTextures.SLOT)
										.overlay(itemPreview.asIcon().margin(1)))
								.child(new Grid()
										.horizontalCenter()
										.top(41)
										.minElementMargin(0)
										.minColWidth(18)
										.minRowHeight(18)
										.mapTo(
												Math.max(1, Math.min(5, items.size())),
												items,
												(index, element) -> (Widget<?>) new ButtonWidget<>()
														.background(GTGuiTextures.SLOT)
														.overlay(new ItemDrawable(element)
																.asIcon()
																.margin(1))
														.tooltip(rt -> rt.addFromItem(element))
														.onMousePressed(button ->
																this.syncHandler.setSubstitutionItem(element))));
					},
					true);
		}
		return selectorPanel;
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
			syncHandler.syncToServer(2, mouseData::writeToPacket);
		}
		return Result.SUCCESS;
	}

	@Override
	public boolean onMouseScroll(UpOrDown scrollDirection, int amount) {
		if (getPanel() == null || !getPanel().isOpen() || items.isEmpty()) return false;
		MouseData mouseData = MouseData.create(scrollDirection.modifier);
		syncHandler.syncToServer(3, mouseData::writeToPacket);
		return true;
	}

	public class SubstituionSyncHandler extends SyncHandler {

		private final ISubstitutionStorage storage;
		private final String name;
		public static final int SET_SUBSTITUTION = 0;

		public SubstituionSyncHandler(ISubstitutionStorage storage, String name) {
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
			else if (id == 2) phantomClick(MouseData.readPacket(buf));
			else if (id == 3) phantomScroll(MouseData.readPacket(buf));
		}

		private void readSelection(PacketBuffer buf, boolean respond) throws IOException {
			int requested = buf.readVarInt();
			net.minecraft.nbt.NBTTagCompound tag = buf.readCompoundTag();
			if (tag == null) return;
			ItemStack identity = new ItemStack(tag);
			List<ItemStack> candidates = storage.getCandidates(name);
			if (requested < 0 || requested >= candidates.size()) return;
			if (!sameItem(identity, candidates.get(requested))) return;
			if (respond) setSubstitution(requested);
			else storage.setOption(name, requested);
		}

		public boolean setSubstitution(int option) {
			List<ItemStack> candidates = storage.getCandidates(name);
			if (option < 0 || option >= candidates.size()) return false;
			if (getOption() == option) return false;
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
				if (sameItem(identity, candidates.get(i))) return setSubstitution(i);
			}
			return false;
		}

		private boolean sameItem(ItemStack first, ItemStack second) {
			return first != null && second != null && first.serializeNBT().equals(second.serializeNBT());
		}

		protected int getNextOption(int value) {
			int size = storage.getCandidates(name).size();
			return size == 0 ? 0 : (int) Math.max(0L, Math.min(size - 1L, (long) getOption() + value));
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

		protected int getOption() {
			int size = storage.getCandidates(name).size();
			int option = storage.getOption(name);
			return size == 0 ? 0 : MathHelper.clamp(option, 0, size - 1);
		}
	}
}
