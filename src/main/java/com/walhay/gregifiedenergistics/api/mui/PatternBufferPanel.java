package com.walhay.gregifiedenergistics.api.mui;

import static com.walhay.gregifiedenergistics.GregifiedEnergisticsConfig.MAX_SLOTS;
import static com.walhay.gregifiedenergistics.api.mui.GregifiedEnergisticsGuis.MIN_WIDTH;
import static com.walhay.gregifiedenergistics.api.mui.GregifiedEnergisticsGuis.PADDING;
import static com.walhay.gregifiedenergistics.api.mui.GregifiedEnergisticsGuis.SLOT_SIZE;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.DynamicSyncHandler;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.ItemSlotSH;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.DynamicSyncedWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.github.bsideup.jabel.Desugar;
import com.walhay.gregifiedenergistics.api.capability.PatternBufferStorage;
import gregtech.api.capability.impl.GhostCircuitItemStackHandler;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.GTGuis.PopupPanel;
import gregtech.api.mui.widget.GhostCircuitSlotWidget;
import java.util.function.BooleanSupplier;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidTank;

/** Builds the per-pattern popup and synchronizes its layout when the buffer changes size. */
public final class PatternBufferPanel {
	private static final int SLOT_COLUMNS = 4;

	private final PatternBufferStorage storage;
	private final GhostCircuitItemStackHandler circuitInventory;
	private final BooleanSupplier pending;
	private final ClientResize resizeClientStorage;

	public PatternBufferPanel(
			PatternBufferStorage storage,
			GhostCircuitItemStackHandler circuitInventory,
			BooleanSupplier pending,
			ClientResize resizeClientStorage) {
		this.storage = storage;
		this.circuitInventory = circuitInventory;
		this.pending = pending;
		this.resizeClientStorage = resizeClientStorage;
	}

	private Widget<?> buildContents(PanelSyncManager syncManager, PacketBuffer buffer) {
		BufferLayout layout = BufferLayout.read(buffer);
		if (syncManager.isClient()) resizeClientStorage.resize(layout.itemSlots(), layout.fluidSlots());

		Flow row = Flow.row()
				.childPadding(10)
				.crossAxisAlignment(Alignment.CrossAxis.START)
				.coverChildrenHeight()
				.horizontalCenter();
		if (layout.itemSlots() > 0) {
			row.child(slotColumn(
					"gregifiedenergistics.gui.buffer_items",
					slotGrid().mapTo(SLOT_COLUMNS, layout.itemSlots(), index -> itemSlot(syncManager, index))));
		}
		if (layout.fluidSlots() > 0) {
			row.child(slotColumn(
					"gregifiedenergistics.gui.buffer_fluids",
					slotGrid().mapTo(SLOT_COLUMNS, layout.fluidSlots(), index -> fluidSlot(syncManager, index))));
		}

		if (layout.itemSlots() == 0 && layout.fluidSlots() == 0) {
			row.child(IKey.lang("gregifiedenergistics.gui.buffer_empty").asWidget());
		}

		Widget<?> contents = row.coverChildren();
		if (layout.pending()) {
			contents = Flow.column()
					.coverChildren()
					.child(IKey.lang("gregifiedenergistics.gui.buffer_pending")
							.asWidget()
							.width(180))
					.child(row.coverChildren());
		}
		return new ListWidget<>().widthRel(1f).heightRel(1f).child(contents);
	}

	private static Flow slotColumn(String titleKey, Widget<?> grid) {
		return Flow.column()
				.coverChildren()
				.child(IKey.lang(titleKey).asWidget().style(IKey.DARK_GRAY))
				.child(grid);
	}

	private static Grid slotGrid() {
		return new Grid().minColWidth(SLOT_SIZE).minRowHeight(SLOT_SIZE).coverChildrenHeight();
	}

	private ItemSlot itemSlot(PanelSyncManager syncManager, int index) {
		ItemSlotSH slot = syncManager.getOrCreateSyncHandler(
				"buffer_slot",
				index,
				ItemSlotSH.class,
				() -> new ItemSlotSH(new DynamicItemSlot(storage.items(), index)));
		IntSyncValue total = syncManager.getOrCreateSyncHandler(
				"buffer_amount", index, IntSyncValue.class, () -> itemCountSync(index));
		return new ItemSlot() {
			@Override
			public void buildTooltip(ItemStack stack, RichTooltip tooltip) {
				super.buildTooltip(stack, tooltip);
				tooltip.addLine(IKey.lang("gregifiedenergistics.gui.buffer_count", total.getIntValue()));
			}
		}.syncHandler(slot).background(GTGuiTextures.SLOT);
	}

	private FluidSlot fluidSlot(PanelSyncManager syncManager, int index) {
		FluidSlotSyncHandler slot = syncManager.getOrCreateSyncHandler(
				"fluid_slot", index, FluidSlotSyncHandler.class, () -> new FluidSlotSyncHandler(liveTank(index)));
		return new FluidSlot().syncHandler(slot);
	}

	private IntSyncValue itemCountSync(int index) {
		return new IntSyncValue(() -> index < storage.items().getSlots()
				? storage.items().getStackInSlot(index).getCount()
				: 0) {
			private boolean initialSync = true;

			@Override
			public void init(String key, PanelSyncManager manager) {
				super.init(key, manager);
				initialSync = true;
			}

			@Override
			public void detectAndSendChanges(boolean init) {
				// Dynamic handlers are registered after the panel's first sync.
				// Send the initial amount even when the constructor cache is current.
				super.detectAndSendChanges(init || initialSync);
				initialSync = false;
			}
		};
	}

	/** Keeps existing sync handlers attached to the current tank when the storage is resized. */
	private IFluidTank liveTank(int index) {
		return new IFluidTank() {
			private IFluidTank current() {
				return index < storage.fluids().getTanks() ? storage.fluids().getTankAt(index) : null;
			}

			@Override
			public FluidStack getFluid() {
				IFluidTank tank = current();
				FluidStack fluid = tank == null ? null : tank.getFluid();
				return fluid == null ? null : fluid.copy();
			}

			@Override
			public int getFluidAmount() {
				IFluidTank tank = current();
				return tank == null ? 0 : tank.getFluidAmount();
			}

			@Override
			public int getCapacity() {
				IFluidTank tank = current();
				return tank == null ? 0 : tank.getCapacity();
			}

			@Override
			public FluidTankInfo getInfo() {
				return new FluidTankInfo(getFluid(), getCapacity());
			}

			@Override
			public int fill(FluidStack resource, boolean doFill) {
				IFluidTank tank = current();
				return tank == null ? 0 : tank.fill(resource, doFill);
			}

			@Override
			public FluidStack drain(int amount, boolean doDrain) {
				IFluidTank tank = current();
				return tank == null ? null : tank.drain(amount, doDrain);
			}
		};
	}

	public PopupPanel buildUI(PanelSyncManager syncManager, int index) {
		DynamicSyncHandler contents = new DynamicSyncHandler().widgetProvider(this::buildContents);
		syncManager.onServerTick(new Runnable() {
			private BufferLayout lastSent;

			@Override
			public void run() {
				BufferLayout layout = new BufferLayout(
						storage.items().getSlots(), storage.fluids().getTanks(), pending.getAsBoolean());
				if (layout.equals(lastSent)) return;
				lastSent = layout;
				contents.notifyUpdate(layout::write);
			}
		});
		int popupWidth = MIN_WIDTH;
		int popupHeight = 174;
		int bodyWidth = popupWidth - 2 * PADDING;
		int bodyHeight = popupHeight - 44;
		int headerWidth = popupWidth - 40; // reserve the popup close button area

		return (PopupPanel) GTGuis.createPopupPanel("buffer#" + index, popupWidth, popupHeight)
				.child(Flow.row()
						.pos(PADDING, 5)
						.width(headerWidth)
						.height(18)
						.mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
						.crossAxisAlignment(Alignment.CrossAxis.CENTER)
						.child(IKey.lang("gregifiedenergistics.gui.buffer_contents", index + 1)
								.asWidget())
						.child(new GhostCircuitSlotWidget()
								.slot(circuitInventory, 0)
								.background(GTGuiTextures.SLOT, GTGuiTextures.INT_CIRCUIT_OVERLAY)))
				.child(new DynamicSyncedWidget<>()
						.pos(PADDING, 28)
						.size(bodyWidth, bodyHeight)
						.syncHandler(contents));
	}

	@FunctionalInterface
	public interface ClientResize {
		void resize(int itemSlots, int fluidSlots);
	}

	@Desugar
	private record BufferLayout(int itemSlots, int fluidSlots, boolean pending) {
		static BufferLayout read(PacketBuffer buffer) {
			int itemSlots = buffer.readInt();
			int fluidSlots = buffer.readInt();
			boolean pending = buffer.readBoolean();
			if (itemSlots < 0 || itemSlots > MAX_SLOTS || fluidSlots < 0 || fluidSlots > MAX_SLOTS) {
				throw new IllegalArgumentException("Invalid buffer GUI shape");
			}
			return new BufferLayout(itemSlots, fluidSlots, pending);
		}

		void write(PacketBuffer buffer) {
			buffer.writeInt(itemSlots);
			buffer.writeInt(fluidSlots);
			buffer.writeBoolean(pending);
		}
	}
}
