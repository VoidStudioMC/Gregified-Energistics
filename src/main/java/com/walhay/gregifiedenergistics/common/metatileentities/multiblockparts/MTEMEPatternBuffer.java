package com.walhay.gregifiedenergistics.common.metatileentities.multiblockparts;

import static com.walhay.gregifiedenergistics.GregifiedEnergisticsConfig.MAX_SLOTS;
import static com.walhay.gregifiedenergistics.api.mui.GregifiedEnergisticsGuis.CONTENT_TOP;
import static com.walhay.gregifiedenergistics.api.mui.GregifiedEnergisticsGuis.INVENTORY_SPACE;
import static com.walhay.gregifiedenergistics.api.mui.GregifiedEnergisticsGuis.MIN_WIDTH;
import static com.walhay.gregifiedenergistics.api.mui.GregifiedEnergisticsGuis.PADDING;
import static com.walhay.gregifiedenergistics.api.mui.GregifiedEnergisticsGuis.SLOT_SIZE;
import static gregtech.api.GTValues.ZPM;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.fluids.util.AEFluidStack;
import appeng.items.misc.ItemEncodedPattern;
import appeng.me.GridAccessException;
import appeng.util.item.AEItemStack;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.*;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.DynamicSyncedWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.glodblock.github.common.item.fake.FakeFluids;
import com.glodblock.github.common.item.fake.FakeItemRegister;
import com.walhay.gregifiedenergistics.api.capability.AbstractPatternItemHandler;
import com.walhay.gregifiedenergistics.api.capability.PatternBufferDualHandler;
import com.walhay.gregifiedenergistics.api.capability.PatternBufferStorage;
import com.walhay.gregifiedenergistics.api.metatileentity.MetaTileEntityCraftingProvider;
import com.walhay.gregifiedenergistics.api.mui.DynamicItemSlot;
import com.walhay.gregifiedenergistics.api.mui.GregifiedEnergisticsGuiTextures;
import com.walhay.gregifiedenergistics.api.mui.GregifiedEnergisticsGuis;
import com.walhay.gregifiedenergistics.api.patterns.implementations.GhostCircuitPatternWrapper;
import com.walhay.gregifiedenergistics.client.render.GregifiedEnergisticsTextures;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.impl.GhostCircuitItemStackHandler;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.GTGuis.PopupPanel;
import gregtech.api.mui.widget.GhostCircuitSlotWidget;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;

/** MTEMEPatternBuffer */
public class MTEMEPatternBuffer extends MetaTileEntityCraftingProvider<IAEItemStack>
		implements IMultiblockAbilityPart<IItemHandlerModifiable> {
	private boolean workingEnabled = true;

	private final PatternHandler patternHandler;

	public MTEMEPatternBuffer(ResourceLocation metaTileEntityId) {
		super(metaTileEntityId, ZPM, false, IItemStorageChannel.class);
		this.patternHandler = new PatternHandler(36);
	}

	@Override
	public void addInformation(
			ItemStack stack, @Nullable World player, @NotNull List<String> tooltip, boolean advanced) {
		super.addInformation(stack, player, tooltip, advanced);
		tooltip.add(I18n.format("gregifiedenergistics.machine.me_pattern_buffer.tooltip"));
		if (stack.hasTagCompound() && stack.getTagCompound().hasKey("PatternInventory")) {
			tooltip.add(I18n.format("gregifiedenergistics.machine.me_pattern_buffer.retains_inventory"));
		}
	}

	@Override
	public IItemHandlerModifiable getImportItems() {
		return new ItemHandlerList(patternHandler.getContainers().stream()
				.map(PatternContainer::dualInventory)
				.collect(Collectors.toList()));
	}

	@Override
	public void addToMultiBlock(MultiblockControllerBase controller) {
		super.addToMultiBlock(controller);
		for (var container : patternHandler) {
			container.notifyController(controller);
		}
		notifyPatternChange();
	}

	@Override
	public void removeFromMultiBlock(MultiblockControllerBase controller) {
		super.removeFromMultiBlock(controller);
		for (var container : patternHandler) {
			container.clearControllerNotifications(controller);
		}
		notifyPatternChange();
	}

	@Override
	public boolean keepsInventory() {
		return true;
	}

	@Override
	public void writeItemStackData(NBTTagCompound data) {
		super.writeItemStackData(data);
		data.setBoolean("WorkingEnabled", workingEnabled);
		data.setTag("PatternInventory", patternHandler.serializeNBT());
	}

	@Override
	public void initFromItemStackData(NBTTagCompound data) {
		super.initFromItemStackData(data);
		if (data == null) return;
		if (data.hasKey("WorkingEnabled")) workingEnabled = data.getBoolean("WorkingEnabled");
		if (data.hasKey("PatternInventory", NBT.TAG_COMPOUND))
			patternHandler.deserializeNBT(data.getCompoundTag("PatternInventory"));
	}

	@Override
	public void update() {
		super.update();
		if (getWorld().isRemote || getOffsetTimer() % 5 != 0) return;
		boolean changed = false;
		for (var container : patternHandler) changed |= container.retryReconfiguration();
		if (changed) notifyPatternChange();
	}

	@Override
	public boolean isWorkingEnabled() {
		return workingEnabled;
	}

	@Override
	public void setWorkingEnabled(boolean workingEnabled) {
		if (this.workingEnabled != workingEnabled) {
			this.workingEnabled = workingEnabled;
			if (getWorld() != null && !getWorld().isRemote) {
				markDirty();
				writeCustomData(GregtechDataCodes.WORKING_ENABLED, buf -> buf.writeBoolean(workingEnabled));
			}
			notifyPatternChange();
		}
	}

	@Override
	public void provideCrafting(ICraftingProviderHelper provider) {
		if (isAttachedToMultiBlock() && isWorkingEnabled()) {
			for (var pattern : patternHandler.getAvailablePatterns()) {
				if (pattern == null) continue;

				provider.addCraftingOption(getCraftingProvider(), pattern);
			}
		}
	}

	@Override
	public boolean isBusy() {
		return !workingEnabled || !isAttachedToMultiBlock();
	}

	@Override
	public boolean pushPattern(ICraftingPatternDetails pattern, InventoryCrafting inventory) {
		if (!workingEnabled
				|| !isAttachedToMultiBlock()
				|| getWorld() == null
				|| getWorld().isRemote
				|| getProxy() == null
				|| !getProxy().isActive()) return false;
		List<ItemStack> items = new ArrayList<>();
		List<FluidStack> fluids = new ArrayList<>();
		for (int i = 0; i < inventory.getSizeInventory(); ++i) {
			ItemStack stack = inventory.getStackInSlot(i);
			if (stack.isEmpty()) continue;
			if (FakeFluids.isFluidFakeItem(stack)) {
				FluidStack fluid = FakeItemRegister.getStack(stack);
				if (fluid == null || fluid.amount <= 0) return false;
				fluids.add(fluid.copy());
			} else {
				items.add(stack.copy());
			}
		}
		for (var container : patternHandler.getContainers(pattern)) {
			if (container.storage.insertAll(items, fluids)) return true;
		}
		return false;
	}

	@Override
	@SuppressWarnings("UnstableApiUsage")
	public boolean usesMui2() {
		return true;
	}

	@Override
	@SuppressWarnings("UnstableApiUsage")
	public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings settings) {
		syncManager.registerSlotGroup("pattern_inv", 9);

		return GregifiedEnergisticsGuis.createPanel(
						this, GTGuis.DEFAULT_WIDTH, CONTENT_TOP + 4 * SLOT_SIZE + INVENTORY_SPACE)
				.child(SlotGroupWidget.builder()
						.slotGroup("pattern_inv")
						.matrix("IIIIIIIII", "IIIIIIIII", "IIIIIIIII", "IIIIIIIII")
						.key(
								'I',
								index -> new ItemSlot() {
									private final IPanelHandler panel = syncManager.syncedPanel(
											"buffer#" + index,
											true,
											(sh, ph) -> patternHandler
													.getContainers()
													.get(index)
													.buildUI(sh, index));

									@Override
									public @NotNull Result onKeyPressed(char typedChar, int keyCode) {
										if (keyCode == Keyboard.KEY_B) {
											if (!getPanel().isOpen()) return Result.ACCEPT;
											panel.togglePanel();
											return Result.SUCCESS;
										}
										return Result.ACCEPT;
									}

									@Override
									public void buildTooltip(ItemStack stack, RichTooltip tooltip) {
										super.buildTooltip(stack, tooltip);
										PatternContainer container =
												patternHandler.getContainers().get(index);

										for (int i = 0;
												i < container.inventory().getSlots();
												++i) {
											var item = container.inventory().getStackInSlot(i);
											if (item.getItem() != Items.AIR) {
												tooltip.addFromItem(item);
											}
										}
									}
								}.slot(SyncHandlers.itemSlot(patternHandler, index)
												.changeListener((newItem, onlyAmountChanged, client, init) ->
														patternHandler.onContentsChanged(index)))
										.background(
												GTGuiTextures.SLOT,
												GregifiedEnergisticsGuiTextures.PATTERN_OVERLAY
														.asIcon()
														.size(16)))
						.build()
						.disableSortButtons()
						.horizontalCenter()
						.top(CONTENT_TOP));
	}

	@Override
	public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
		return new MTEMEPatternBuffer(metaTileEntityId);
	}

	private SimpleOverlayRenderer getOverlay() {
		return isOnline
				? GregifiedEnergisticsTextures.ME_PATTERN_BUFFER_ACTIVE
				: GregifiedEnergisticsTextures.ME_PATTERN_BUFFER;
	}

	@Override
	public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
		super.renderMetaTileEntity(renderState, translation, pipeline);
		getOverlay().renderSided(getFrontFacing(), renderState, translation, pipeline);
	}

	@Override
	public @Nullable MultiblockAbility<IItemHandlerModifiable> getAbility() {
		return MultiblockAbility.IMPORT_ITEMS;
	}

	@Override
	public void registerAbilities(@NotNull AbilityInstances ability) {
		patternHandler.getContainers().stream()
				.map(PatternContainer::dualInventory)
				.forEach(ability::add);
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound data) {
		super.writeToNBT(data);

		data.setBoolean("WorkingEnabled", workingEnabled);
		data.setTag("PatternInventory", patternHandler.serializeNBT());

		return data;
	}

	@Override
	public void readFromNBT(NBTTagCompound data) {
		super.readFromNBT(data);
		if (data.hasKey("WorkingEnabled")) workingEnabled = data.getBoolean("WorkingEnabled");
		if (data.hasKey("PatternInventory")) {
			patternHandler.deserializeNBT(data.getCompoundTag("PatternInventory"));
		}
	}

	@Override
	public void writeInitialSyncData(PacketBuffer buf) {
		super.writeInitialSyncData(buf);
		buf.writeBoolean(workingEnabled);
	}

	@Override
	public void receiveInitialSyncData(PacketBuffer buf) {
		super.receiveInitialSyncData(buf);
		workingEnabled = buf.readBoolean();
	}

	@Override
	public void receiveCustomData(int dataId, PacketBuffer buf) {
		super.receiveCustomData(dataId, buf);
		if (dataId == GregtechDataCodes.WORKING_ENABLED) {
			workingEnabled = buf.readBoolean();
			scheduleRenderUpdate();
		}
	}

	private class PatternHandler extends AbstractPatternItemHandler implements Iterable<PatternContainer> {
		private final PatternContainer[] containers;

		public PatternHandler(int size) {
			super(MTEMEPatternBuffer.this, size);
			containers = new PatternContainer[size];
			for (int i = 0; i < size; ++i) containers[i] = new PatternContainer(i);
		}

		public List<PatternContainer> getContainers(ICraftingPatternDetails pattern) {
			List<PatternContainer> matches = new ArrayList<>();
			if (pattern == null) return matches;
			for (int i = 0; i < containers.length; ++i) {
				if (!containers[i].pending && Objects.equals(pattern, getPatternDetails(i))) matches.add(containers[i]);
			}
			return matches;
		}

		public Collection<ICraftingPatternDetails> getAvailablePatterns() {
			Collection<ICraftingPatternDetails> patterns = new LinkedHashSet<>();
			for (int i = 0; i < containers.length; ++i) {
				ICraftingPatternDetails pattern = getPatternDetails(i);
				if (pattern != null && !containers[i].pending) patterns.add(pattern);
			}
			return patterns;
		}

		public List<PatternContainer> getContainers() {
			return Arrays.asList(containers);
		}

		@Override
		protected void onLoad() {
			super.onLoad();
			for (int i = 0; i < containers.length; ++i) containers[i].configure(getPatternDetails(i));
		}

		@Override
		protected ICraftingPatternDetails getPatternFromStack(ItemStack stack) {
			if (stack.getItem() instanceof ItemEncodedPattern encodedPattern) {
				return GhostCircuitPatternWrapper.wrap(encodedPattern.getPatternForItem(stack, getWorld()));
			}
			return null;
		}

		@Override
		public void onContentsChanged(int slot) {
			var previous = getPatternDetails(slot);
			super.onContentsChanged(slot);
			var current = getPatternDetails(slot);
			if (previous == current) return;
			containers[slot].requestPattern(current);
			notifyPatternChange();
		}

		@Override
		protected void onPatternUpdate() {
			notifyPatternChange();
		}

		@Override
		public Iterator<PatternContainer> iterator() {
			return getContainers().iterator();
		}

		@Override
		public NBTTagCompound serializeNBT() {
			NBTTagCompound data = super.serializeNBT();
			NBTTagList entries = new NBTTagList();
			for (int i = 0; i < containers.length; ++i) {
				NBTTagCompound entry = new NBTTagCompound();
				entry.setInteger("Slot", i);
				entry.setTag("Data", containers[i].serializeNBT());
				entries.appendTag(entry);
			}
			data.setTag("Containers", entries);
			return data;
		}

		@Override
		public void deserializeNBT(NBTTagCompound data) {
			NBTTagList items = data.getTagList("Items", NBT.TAG_COMPOUND);
			for (int i = 0; i < items.tagCount(); ++i) {
				NBTTagCompound entry = items.getCompoundTagAt(i);
				int slot = entry.getInteger("Slot");
				if (slot >= containers.length && !new ItemStack(entry).isEmpty()) {
					throw new IllegalArgumentException("Saved buffer pattern slot exceeds fixed capacity: " + slot);
				}
			}
			super.deserializeNBT(data);
			NBTTagList entries = data.getTagList("Containers", NBT.TAG_COMPOUND);
			for (int i = 0; i < entries.tagCount(); ++i) {
				NBTTagCompound entry = entries.getCompoundTagAt(i);
				if (!entry.hasKey("Slot", NBT.TAG_INT) || !entry.hasKey("Data", NBT.TAG_COMPOUND)) continue;
				int slot = entry.getInteger("Slot");
				if (slot < 0 || slot >= containers.length)
					throw new IllegalArgumentException("Invalid saved buffer container: " + slot);
				containers[slot].deserializeNBT(entry.getCompoundTag("Data"));
			}
			notifyPatternChange();
		}
	}

	/**
	 * PatternContainer
	 *
	 * <p>Medium for operations on {@link IItemHandlerModifiable} and {@link IMultipleTankHandler}.
	 *
	 * <p>Implements {@link INBTSerializable} for serializing container content.
	 */
	private class PatternContainer implements INBTSerializable<NBTTagCompound> {
		private final int slot;
		private final PatternBufferStorage storage;
		private final GhostCircuitItemStackHandler circuitInventory;
		private final PatternBufferDualHandler dualHandler;
		private boolean pending;

		public PatternContainer(int slot) {
			this.slot = slot;
			storage = new PatternBufferStorage(this::contentsChanged);
			circuitInventory = new GhostCircuitItemStackHandler(MTEMEPatternBuffer.this) {
				@Override
				public void setCircuitValue(int config) {
					super.setCircuitValue(config);
					contentsChanged();
				}
			};
			dualHandler = new PatternBufferDualHandler(storage.items(), circuitInventory, storage.fluids());
			circuitInventory.addNotifiableMetaTileEntity(MTEMEPatternBuffer.this);
		}

		private void contentsChanged() {
			if (dualHandler != null) dualHandler.onContentsChanged();
			if (getWorld() != null && !getWorld().isRemote) markDirty();
		}

		private void refreshDelegates() {
			dualHandler.setFluidDelegate(storage.fluids());
			contentsChanged();
		}

		private void notifyController(MultiblockControllerBase controller) {
			dualHandler.addNotifiableMetaTileEntity(controller);
			dualHandler.addToNotifiedList(controller, dualHandler, false);
		}

		private void clearControllerNotifications(MultiblockControllerBase controller) {
			dualHandler.removeNotifiableMetaTileEntity(controller);
			controller.getNotifiedItemInputList().remove(dualHandler);
			controller.getNotifiedFluidInputList().remove(dualHandler);
		}

		private int[] shape(ICraftingPatternDetails pattern) {
			int items = 0, fluids = 0;
			if (pattern != null && pattern.getCondensedInputs() != null) {
				for (IAEItemStack input : pattern.getCondensedInputs()) {
					if (input == null) continue;
					if (FakeFluids.isFluidFakeItem(input.asItemStackRepresentation())) ++fluids;
					else ++items;
				}
			}
			return new int[] {items, fluids};
		}

		private boolean configure(ICraftingPatternDetails pattern) {
			int[] shape = shape(pattern);
			if (!storage.configure(shape[0], shape[1])) {
				pending = true;
				return false;
			}
			pending = false;
			circuitInventory.setCircuitValue(
					pattern instanceof GhostCircuitPatternWrapper wrapper ? wrapper.getCircuitValue() : -1);
			refreshDelegates();
			return true;
		}

		private void clearClientContents() {
			for (int i = 0; i < storage.items().getSlots(); ++i) storage.items().setStackInSlot(i, ItemStack.EMPTY);
			for (var tank : storage.fluids().getFluidTanks()) tank.drain(Integer.MAX_VALUE, true);
		}

		private void requestPattern(ICraftingPatternDetails pattern) {
			if (getWorld() != null && getWorld().isRemote) {
				clearClientContents();
			} else {
				refundItems();
				refundFluids();
			}
			configure(pattern);
			contentsChanged();
		}

		private boolean retryReconfiguration() {
			if (!pending) return false;
			refundItems();
			refundFluids();
			return storage.isEmpty() && configure(patternHandler.getPatternDetails(slot));
		}

		public IItemHandlerModifiable inventory() {
			return storage.items();
		}

		public IItemHandlerModifiable dualInventory() {
			return dualHandler;
		}

		private void refundItems() {
			if (getWorld() == null || getWorld().isRemote || getProxy() == null) return;
			try {
				IMEInventory<IAEItemStack> network = getProxy().getStorage().getInventory(getStorageChannel());
				if (network == null) return;
				for (int i = 0; i < storage.items().getSlots(); ++i) {
					ItemStack stack = storage.items().getStackInSlot(i);
					if (stack.isEmpty()) continue;
					IAEItemStack remainder = network.injectItems(
							AEItemStack.fromItemStack(stack.copy()), Actionable.MODULATE, getActionSource());
					int remaining = remainder == null
							? 0
							: (int) Math.min(stack.getCount(), Math.max(0L, remainder.getStackSize()));
					if (remaining != stack.getCount()) {
						ItemStack kept = stack.copy();
						kept.setCount(remaining);
						storage.items().setStackInSlot(i, remaining == 0 ? ItemStack.EMPTY : kept);
					}
				}
			} catch (GridAccessException ignored) {
				// The original contents remain owned by the buffer until storage becomes available.
			}
		}

		private void refundFluids() {
			if (getWorld() == null || getWorld().isRemote || getProxy() == null) return;
			try {
				IFluidStorageChannel channel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
				IMEInventory<IAEFluidStack> network = getProxy().getStorage().getInventory(channel);
				if (network == null) return;
				for (var tank : storage.fluids().getFluidTanks()) {
					FluidStack stack = tank.getFluid();
					if (stack == null || stack.amount <= 0) continue;
					IAEFluidStack remainder = network.injectItems(
							AEFluidStack.fromFluidStack(stack.copy()), Actionable.MODULATE, getActionSource());
					int remaining = remainder == null
							? 0
							: (int) Math.min(stack.amount, Math.max(0L, remainder.getStackSize()));
					if (remaining < stack.amount) tank.drain(stack.amount - remaining, true);
				}
			} catch (GridAccessException ignored) {
				// Never clear tanks merely because the ME network refused a refund.
			}
		}

		@Override
		public NBTTagCompound serializeNBT() {
			NBTTagCompound data = storage.serializeNBT();
			NBTTagCompound circuit = new NBTTagCompound();
			circuitInventory.write(circuit);
			data.setTag("CircuitInventory", circuit);
			data.setBoolean("PendingReconfiguration", pending);
			return data;
		}

		@Override
		public void deserializeNBT(NBTTagCompound data) {
			storage.deserializeNBT(data);
			refreshDelegates();
			if (data.hasKey("CircuitInventory", NBT.TAG_COMPOUND))
				circuitInventory.read(data.getCompoundTag("CircuitInventory"));
			ICraftingPatternDetails pattern = patternHandler.getPatternDetails(slot);
			int[] shape = shape(pattern);
			pending = data.getBoolean("PendingReconfiguration")
					|| pattern == null && !storage.isEmpty()
					|| storage.items().getSlots() != shape[0]
					|| storage.fluids().getTanks() != shape[1];
			if (storage.isEmpty()) configure(pattern);
		}

		protected Widget<?> contentBuffer(PanelSyncManager syncManager, PacketBuffer buffer) {
			int slots = buffer.readInt();
			int fluids = buffer.readInt();
			boolean waitingForRecovery = buffer.readBoolean();
			if (slots < 0 || slots > MAX_SLOTS || fluids < 0 || fluids > MAX_SLOTS)
				throw new IllegalArgumentException("Invalid buffer GUI shape");
			if (syncManager.isClient()
					&& (slots != storage.items().getSlots()
							|| fluids != storage.fluids().getTanks())) {
				clearClientContents();
				storage.configure(slots, fluids);
				refreshDelegates();
			}

			Flow row = Flow.row()
					.childPadding(10)
					.crossAxisAlignment(Alignment.CrossAxis.START)
					.coverChildrenHeight()
					.horizontalCenter();

			if (slots > 0) {
				row.child(Flow.column()
						.coverChildren()
						.child(IKey.lang("gregifiedenergistics.gui.buffer_items")
								.asWidget()
								.style(IKey.DARK_GRAY))
						.child(new Grid()
								.minColWidth(18)
								.minRowHeight(18)
								.coverChildrenHeight()
								.mapTo(4, slots, slotIndex -> {
									ModularSlot ms = new DynamicItemSlot(storage.items(), slotIndex);

									ItemSlotSH itemSyncHandler = syncManager.getOrCreateSyncHandler(
											"buffer_slot", slotIndex, ItemSlotSH.class, () -> new ItemSlotSH(ms));

									IntSyncValue total = syncManager.getOrCreateSyncHandler(
											"buffer_amount",
											slotIndex,
											IntSyncValue.class,
											() -> itemCountSync(slotIndex));
									return new ItemSlot() {
										@Override
										public void buildTooltip(
												ItemStack stack, com.cleanroommc.modularui.screen.RichTooltip tooltip) {
											super.buildTooltip(stack, tooltip);
											tooltip.addLine(IKey.lang(
													"gregifiedenergistics.gui.buffer_count", total.getIntValue()));
										}
									}.syncHandler(itemSyncHandler).background(GTGuiTextures.SLOT);
								})));
			}

			if (fluids > 0) {
				row.child(Flow.column()
						.coverChildren()
						.child(IKey.lang("gregifiedenergistics.gui.buffer_fluids")
								.asWidget()
								.style(IKey.DARK_GRAY))
						.child(new Grid()
								.minColWidth(18)
								.minRowHeight(18)
								.coverChildrenHeight()
								.mapTo(4, fluids, fluidIndex -> {
									FluidSlotSyncHandler fsh = syncManager.getOrCreateSyncHandler(
											"fluid_slot",
											fluidIndex,
											FluidSlotSyncHandler.class,
											() -> new FluidSlotSyncHandler(liveTank(fluidIndex)));

									return new FluidSlot().syncHandler(fsh);
								})));
			}

			if (slots == 0 && fluids == 0) {
				row.child(IKey.lang("gregifiedenergistics.gui.buffer_empty").asWidget());
			}

			Widget<?> contents = row.coverChildren();
			if (waitingForRecovery) {
				contents = Flow.column()
						.coverChildren()
						.child(IKey.lang("gregifiedenergistics.gui.buffer_pending")
								.asWidget()
								.width(180))
						.child(row.coverChildren());
			}
			return new com.cleanroommc.modularui.widgets.ListWidget<>()
					.widthRel(1f)
					.heightRel(1f)
					.child(contents);
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

		private IFluidTank liveTank(int index) {
			return new IFluidTank() {
				private IFluidTank current() {
					return index < storage.fluids().getTanks()
							? storage.fluids().getTankAt(index)
							: null;
				}

				public FluidStack getFluid() {
					IFluidTank tank = current();
					FluidStack fluid = tank == null ? null : tank.getFluid();
					return fluid == null ? null : fluid.copy();
				}

				public int getFluidAmount() {
					IFluidTank tank = current();
					return tank == null ? 0 : tank.getFluidAmount();
				}

				public int getCapacity() {
					IFluidTank tank = current();
					return tank == null ? 0 : tank.getCapacity();
				}

				public FluidTankInfo getInfo() {
					return new FluidTankInfo(getFluid(), getCapacity());
				}

				public int fill(FluidStack resource, boolean doFill) {
					IFluidTank tank = current();
					return tank == null ? 0 : tank.fill(resource, doFill);
				}

				public FluidStack drain(int amount, boolean doDrain) {
					IFluidTank tank = current();
					return tank == null ? null : tank.drain(amount, doDrain);
				}
			};
		}

		public PopupPanel buildUI(PanelSyncManager syncManager, int index) {
			DynamicSyncHandler dsh = new DynamicSyncHandler().widgetProvider(this::contentBuffer);
			syncManager.onServerTick(new Runnable() {
				private int sentItemSlots = -1;
				private int sentFluidSlots = -1;
				private boolean sentPending;

				@Override
				public void run() {
					int itemSlots = storage.items().getSlots();
					int fluidSlots = storage.fluids().getTanks();
					boolean waitingForRecovery = pending;
					if (sentItemSlots == itemSlots && sentFluidSlots == fluidSlots && sentPending == waitingForRecovery)
						return;
					sentItemSlots = itemSlots;
					sentFluidSlots = fluidSlots;
					sentPending = waitingForRecovery;
					dsh.notifyUpdate(
							buf -> buf.writeInt(itemSlots).writeInt(fluidSlots).writeBoolean(waitingForRecovery));
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
							.syncHandler(dsh));
		}
	}
}
