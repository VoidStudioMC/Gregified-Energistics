package com.walhay.gregifiedenergistics.common.metatileentities.multiblockparts;

import static com.walhay.gregifiedenergistics.GregifiedEnergisticsConfig.MAX_SLOTS;
import static com.walhay.gregifiedenergistics.api.mui.GregifiedEnergisticsGuiTextures.BLOCKING_MODE;
import static com.walhay.gregifiedenergistics.api.mui.GregifiedEnergisticsGuis.CONTENT_TOP;
import static com.walhay.gregifiedenergistics.api.mui.GregifiedEnergisticsGuis.GRID_VIEWPORT_HEIGHT;
import static com.walhay.gregifiedenergistics.api.mui.GregifiedEnergisticsGuis.INVENTORY_SPACE;
import static com.walhay.gregifiedenergistics.api.mui.GregifiedEnergisticsGuis.MIN_WIDTH;
import static com.walhay.gregifiedenergistics.api.mui.GregifiedEnergisticsGuis.PADDING;
import static com.walhay.gregifiedenergistics.api.mui.GregifiedEnergisticsGuis.SLOT_SIZE;
import static com.walhay.gregifiedenergistics.api.patterns.substitutions.SubstitutionStorage.STORAGE_TAG;
import static com.walhay.gregifiedenergistics.api.util.BlockingMode.BLOCKING_MODE_TAG;

import appeng.api.AEApi;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.util.InventoryAdaptor;
import appeng.util.inv.AdaptorItemHandler;
import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.DynamicSyncHandler;
import com.cleanroommc.modularui.value.sync.EnumSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.DynamicSyncedWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.walhay.gregifiedenergistics.GregifiedEnergisticsConfig;
import com.walhay.gregifiedenergistics.api.capability.AssemblyLineInputQueue;
import com.walhay.gregifiedenergistics.api.metatileentity.MetaTileEntityCraftingProvider;
import com.walhay.gregifiedenergistics.api.mui.GregifiedEnergisticsGuis;
import com.walhay.gregifiedenergistics.api.patterns.AbstractPatternHelper;
import com.walhay.gregifiedenergistics.api.patterns.ISubstitutionNotifiable;
import com.walhay.gregifiedenergistics.api.patterns.ISubstitutionStorage;
import com.walhay.gregifiedenergistics.api.patterns.substitutions.SubstitutionStorage;
import com.walhay.gregifiedenergistics.api.util.AssemblyLineInputs;
import com.walhay.gregifiedenergistics.api.util.BlockingMode;
import com.walhay.gregifiedenergistics.client.render.GregifiedEnergisticsTextures;
import com.walhay.gregifiedenergistics.common.mui.SubstitutionSlotWidget;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.impl.GhostCircuitItemStackHandler;
import gregtech.api.capability.impl.MultiblockRecipeLogic;
import gregtech.api.capability.impl.NotifiableItemStackHandler;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;

/** Routes crafting CPU inputs to the assembly line in the order of its item buses and fluid hatches. */
public abstract class MTEAbstractAssemblyLineBus extends MetaTileEntityCraftingProvider<IAEFluidStack>
		implements IMultiblockAbilityPart<IItemHandlerModifiable>, ISubstitutionNotifiable {

	public static final String WORKING_ENABLED_TAG = "WorkingEnabled";
	public static final String FLUID_TO_SEND_TAG = AssemblyLineInputQueue.FLUID_TAG;
	public static final String ITEM_TO_SEND_TAG = AssemblyLineInputQueue.ITEM_TAG;
	public static final String SEND_SLOT_TAG = AssemblyLineInputQueue.SLOT_TAG;

	private final AssemblyLineInputQueue inputQueue = new AssemblyLineInputQueue(this::markDirty);
	private BlockingMode blockingMode = BlockingMode.NO_BLOCKING;
	private boolean workingEnabled = true;
	protected final ISubstitutionStorage substitutionStorage = new SubstitutionStorage(this);

	public MTEAbstractAssemblyLineBus(ResourceLocation metaTileEntityId, int tier) {
		super(metaTileEntityId, tier, false, IFluidStorageChannel.class);
	}

	@Override
	public void update() {
		super.update();
		if (getWorld().isRemote) return;

		if (isWorkingEnabled() && isAttachedToMultiBlock() && getOffsetTimer() % 5 == 0) {
			if (hasItemsToSend()) pushItemsOut();

			if (hasFluidsToSend()) pushFluidsOut();
		}
	}

	@Override
	protected IItemHandlerModifiable createImportItemHandler() {
		return new NotifiableItemStackHandler(this, 1, getController(), false);
	}

	@Override
	public void writeItemStackData(NBTTagCompound data) {
		super.writeItemStackData(data);
		// Keep queue state unchanged during break: drops may be fetched before or after clearMachineInventory.
		// Ordinary importItems still drop normally; queued items and extracted fluids exist only in this snapshot.
		inputQueue.writeToNBT(data);
	}

	@Override
	public void initFromItemStackData(NBTTagCompound data) {
		super.initFromItemStackData(data);
		if (data != null) inputQueue.readFromNBT(data);
	}

	@Override
	public void addToMultiBlock(MultiblockControllerBase controller) {
		super.addToMultiBlock(controller);
		notifyPatternChange();
	}

	@Override
	public void removeFromMultiBlock(MultiblockControllerBase controller) {
		super.removeFromMultiBlock(controller);
		notifyPatternChange();
	}

	@Override
	public void addInformation(ItemStack stack, World world, @NotNull List<String> tooltip, boolean advanced) {
		super.addInformation(stack, world, tooltip, advanced);
		if (stack.hasTagCompound() && AssemblyLineInputQueue.hasSavedInputs(stack.getTagCompound())) {
			tooltip.add(I18n.format("gregifiedenergistics.machine.me_assembly_line_bus.retains_queue"));
		}
	}

	@Override
	public void addToolUsages(ItemStack stack, World world, List<String> tooltip, boolean advanced) {
		super.addToolUsages(stack, world, tooltip, advanced);
		tooltip.add(I18n.format("gregifiedenergistics.tool_action.memory_card.copy_substitution"));
	}

	@Override
	public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager sync, UISettings settings) {

		int columns = Math.max(
				GregifiedEnergisticsConfig.guiConfig.getPatternColumns(),
				GregifiedEnergisticsConfig.guiConfig.getSubstitutionColumns());
		int width = Math.min(300, Math.max(MIN_WIDTH, columns * SLOT_SIZE + 2 * PADDING + 8));
		ModularPanel panel =
				GregifiedEnergisticsGuis.createPanel(this, width, CONTENT_TOP + GRID_VIEWPORT_HEIGHT + INVENTORY_SPACE);

		var controller = new PagedWidget.Controller();

		var tabs = Flow.row()
				.name("tab row")
				.widthRel(1f)
				.leftRel(0.5f)
				.margin(3, 0)
				.coverChildrenHeight()
				.topRel(0f, 3, 1f);

		var paged = new PagedWidget<>().horizontalCenter();

		int pageCounter = 0;

		var patternList = createPatternList(sync);
		if (patternList != null) {
			tabs.child(new PageButton(pageCounter++, controller)
					.tab(GuiTextures.TAB_TOP, 0)
					.addTooltipLine(IKey.lang("gregifiedenergistics.gui.patterns_grid"))
					.overlay(new ItemDrawable(AEApi.instance()
									.definitions()
									.items()
									.encodedPattern()
									.maybeItem()
									.orElse(Items.AIR))
							.asIcon()
							.size(16)));

			paged.addPage(patternList);
		}

		var substitutionList = createSubstitutionList(sync);
		if (substitutionList != null) {
			tabs.child(new PageButton(pageCounter++, controller)
					.tab(GuiTextures.TAB_TOP, 0)
					.addTooltipLine(IKey.lang("gregifiedenergistics.gui.substitutions_grid"))
					.overlay(new ItemDrawable(AEApi.instance()
									.definitions()
									.items()
									.memoryCard()
									.maybeItem()
									.orElse(Items.AIR))
							.asIcon()
							.size(16)));

			paged.addPage(substitutionList);
		}

		if (pageCounter > 1) {
			// Centre the panel together with GregTech's tabs above it.
			panel.topRel(0.5f, GuiTextures.TAB_TOP.getHeight() / 2, 0.5f).child(tabs);
		}
		return panel.child(createButtonBar(sync))
				.child(paged.top(CONTENT_TOP)
						.width(width - 2 * PADDING)
						.height(GRID_VIEWPORT_HEIGHT)
						.controller(controller));
	}

	@SuppressWarnings("UnstableApiUsage")
	public Flow createButtonBar(PanelSyncManager sync) {
		BooleanSyncValue workingStateValue = new BooleanSyncValue(this::isWorkingEnabled, this::setWorkingEnabled);
		EnumSyncValue<BlockingMode> blockingStateValue =
				new EnumSyncValue<>(BlockingMode.class, this::getBlockingMode, this::setBlockingMode);

		sync.syncValue("working_enabled", workingStateValue);
		sync.syncValue("blocking_mode", blockingStateValue);

		return GregifiedEnergisticsGuis.buttonColumn()
				.child(GregifiedEnergisticsGuis.workingButton(workingStateValue))
				.child(new CycleButtonWidget()
						.value(blockingStateValue)
						.tooltipAutoUpdate(true)
						.tooltipBuilder(t -> t.addLine(IKey.lang(
								switch (blockingStateValue.getValue()) {
									case NO_BLOCKING -> "gregifiedenergistics.gui.no_blocking";
									case BLOCKING_MODE -> "gregifiedenergistics.gui.blocking_mode";
									case CRAFTING_BLOCKING_MODE -> "gregifiedenergistics.gui.crafting_blocking_mode";
								})))
						.top(SLOT_SIZE)
						.stateOverlay(0, BLOCKING_MODE[0])
						.stateOverlay(1, BLOCKING_MODE[1])
						.stateOverlay(2, BLOCKING_MODE[2]))
				.child(new ItemSlot()
						.slot(importItems, 0)
						.size(SLOT_SIZE)
						.tooltip(rt -> rt.addLine(IKey.lang("gregifiedenergistics.gui.item_slot"))));
	}

	public Widget<?> createPatternList(PanelSyncManager syncHandler) {
		return null;
	}

	public Widget<?> createSubstitutionList(PanelSyncManager syncHandler) {
		DynamicSyncHandler options = new DynamicSyncHandler().widgetProvider((manager, buf) -> {
			int count = buf.readVarInt();
			if (count < 0 || count > buf.readableBytes())
				throw new IllegalArgumentException("Invalid substitution GUI size");
			List<String> names = new java.util.ArrayList<>(count);
			for (int i = 0; i < count; ++i) names.add(buf.readString(32767));
			return new Grid()
					.minElementMargin(0)
					.minColWidth(SLOT_SIZE)
					.minRowHeight(SLOT_SIZE)
					.widthRel(1f)
					.heightRel(1f)
					.scrollable()
					.mapTo(GregifiedEnergisticsConfig.guiConfig.getSubstitutionColumns(), names, (index, name) ->
							(Widget<?>) new SubstitutionSlotWidget().storage(substitutionStorage, name, manager));
		});
		// New ingredient handlers must be registered outside the handler-map iteration.
		syncHandler.onServerTick(new Runnable() {
			private List<String> sentOptions;

			@Override
			public void run() {
				List<String> names = new java.util.ArrayList<>(substitutionStorage.getOptions());
				names.removeIf(name -> name.startsWith("items:"));
				if (!names.equals(sentOptions)) {
					sentOptions = names;
					options.notifyUpdate(buf -> {
						buf.writeVarInt(names.size());
						for (String name : names) buf.writeString(name);
					});
				}
			}
		});
		syncHandler.syncValue("substitution_list", options);
		return new DynamicSyncedWidget<>()
				.widthRel(1f)
				.height(GRID_VIEWPORT_HEIGHT)
				.syncHandler(options);
	}

	@Override
	@SuppressWarnings("UnstableApiUsage")
	public boolean usesMui2() {
		return true;
	}

	@Override
	public boolean onRightClick(
			EntityPlayer player, EnumHand hand, EnumFacing facing, CuboidRayTraceResult traceResult) {
		ItemStack heldItem = player.getHeldItem(hand);
		if (AEApi.instance().definitions().items().memoryCard().isSameAs(heldItem)) {
			if (getWorld() == null || getWorld().isRemote) return true;
			if (player.isSneaking()) {
				NBTTagCompound data = cleanMemoryCardTag(heldItem);
				data.setTag(STORAGE_TAG, substitutionStorage.serializeNBT());
				heldItem.setTagCompound(data);
				player.sendStatusMessage(
						new TextComponentTranslation("gregifiedenergistics.machine.me.copy_settings"), true);
			} else {
				NBTTagCompound data = cleanMemoryCardTag(heldItem);
				if (data.hasKey(STORAGE_TAG, NBT.TAG_COMPOUND)) {
					substitutionStorage.deserializeNBT(data.getCompoundTag(STORAGE_TAG));
					notifySubstitutionChange();
					markDirty();
					player.sendStatusMessage(
							new TextComponentTranslation("gregifiedenergistics.machine.me.paste_settings"), true);
				}
			}
			return true;
		}
		return super.onRightClick(player, hand, facing, traceResult);
	}

	private static NBTTagCompound cleanMemoryCardTag(ItemStack item) {
		NBTTagCompound data = item.hasTagCompound() ? item.getTagCompound().copy() : new NBTTagCompound();
		// Flatten only the exact full-item wrappers produced by previous releases; preserve unrelated item tags.
		String itemId = item.getItem().getRegistryName().toString();
		while (data.getString("id").equals(itemId)
				&& data.hasKey("Count", NBT.TAG_BYTE)
				&& data.hasKey("Damage", NBT.TAG_SHORT)) {
			NBTTagCompound nested = data.getCompoundTag("tag").copy();
			for (String key : data.getKeySet()) {
				if (!key.equals("id") && !key.equals("Count") && !key.equals("Damage") && !key.equals("tag")) {
					nested.setTag(key, data.getTag(key).copy());
				}
			}
			data = nested;
		}
		return data;
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound data) {
		super.writeToNBT(data);
		data.setBoolean(WORKING_ENABLED_TAG, workingEnabled);
		data.setString(BLOCKING_MODE_TAG, blockingMode.name());
		data.removeTag("FluidMode");
		inputQueue.writeToNBT(data);
		return data;
	}

	@Override
	public void readFromNBT(NBTTagCompound data) {
		super.readFromNBT(data);
		workingEnabled = !data.hasKey(WORKING_ENABLED_TAG) || data.getBoolean(WORKING_ENABLED_TAG);
		blockingMode = BlockingMode.NO_BLOCKING;
		try {
			blockingMode = BlockingMode.valueOf(data.getString(BLOCKING_MODE_TAG));
		} catch (IllegalArgumentException ignored) {
			// Missing/obsolete mode names retain the default.
		}
		inputQueue.readFromNBT(data);
	}

	@Override
	public void writeInitialSyncData(PacketBuffer buf) {
		super.writeInitialSyncData(buf);
		buf.writeBoolean(workingEnabled);
		buf.writeEnumValue(blockingMode);
	}

	@Override
	public void receiveInitialSyncData(PacketBuffer buf) {
		super.receiveInitialSyncData(buf);
		workingEnabled = buf.readBoolean();
		blockingMode = buf.readEnumValue(BlockingMode.class);
	}

	@Override
	public void receiveCustomData(int descriptor, PacketBuffer buf) {
		super.receiveCustomData(descriptor, buf);
		if (descriptor == GregtechDataCodes.WORKING_ENABLED) {
			this.workingEnabled = buf.readBoolean();
			scheduleRenderUpdate();
		}
	}

	@Override
	public MultiblockAbility<IItemHandlerModifiable> getAbility() {
		return MultiblockAbility.IMPORT_ITEMS;
	}

	@Override
	public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
		abilityInstances.add(importItems);
	}

	@Override
	public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
		if (capability == GregtechTileCapabilities.CAPABILITY_CONTROLLABLE) {
			return GregtechTileCapabilities.CAPABILITY_CONTROLLABLE.cast(this);
		}
		return super.getCapability(capability, facing);
	}

	protected SimpleOverlayRenderer getOverlay() {
		if (isOnline) {
			if (isWorkingEnabled()) {
				return GregifiedEnergisticsTextures.ME_AL_HATCH_CONNECTOR_ACTIVE;
			}
			return GregifiedEnergisticsTextures.ME_AL_HATCH_CONNECTOR_WAITING;
		}
		return GregifiedEnergisticsTextures.ME_AL_HATCH_CONNECTOR_INACTIVE;
	}

	@Override
	public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
		super.renderMetaTileEntity(renderState, translation, pipeline);
		getOverlay().renderSided(getFrontFacing(), renderState, translation, pipeline);
	}

	// provide crafting patterns to the network
	@Override
	public void provideCrafting(ICraftingProviderHelper craftingHelper) {
		if (isAttachedToMultiBlock() && isWorkingEnabled()) {
			for (ICraftingPatternDetails details : getPatterns()) {
				if (details instanceof AbstractPatternHelper helper) {
					helper.providePatterns(getCraftingProvider(), craftingHelper);
				} else if (details != null) {
					craftingHelper.addCraftingOption(getCraftingProvider(), details);
				}
			}
		}
	}

	// check if hatch can be used for pushing pattern right now
	@Override
	public boolean isBusy() {
		if (!isWorkingEnabled() || !isAttachedToMultiBlock() || hasItemsToSend() || hasFluidsToSend()) {
			return true;
		}

		if (blockingMode == BlockingMode.CRAFTING_BLOCKING_MODE) {
			if (getController() != null && getController() instanceof RecipeMapMultiblockController controller) {
				MultiblockRecipeLogic workable = controller.getRecipeMapWorkable();
				if (workable.getProgressPercent() < 0.95 && workable.getProgress() > 0) {
					return true;
				}
			}
		}

		if (blockingMode.isBlockingEnabled()) {
			return getController().getAbilities(MultiblockAbility.IMPORT_ITEMS).stream()
					.map(AdaptorItemHandler::new)
					.anyMatch(InventoryAdaptor::containsItems);
		}

		return false;
	}

	// try to push pattern to import buses
	@Override
	public boolean pushPattern(ICraftingPatternDetails pattern, InventoryCrafting inventoryCrafting) {
		if (!isWorkingEnabled()
				|| !isAttachedToMultiBlock()
				|| getWorld() == null
				|| getWorld().isRemote
				|| hasItemsToSend()
				|| hasFluidsToSend()
				|| getProxy() == null
				|| !getProxy().isActive()) {
			return false;
		}

		AssemblyLineInputs inputs = AssemblyLineInputs.read(inventoryCrafting);
		if (inputs == null
				|| !inputs.matchesFluidInputs(pattern)
				|| !acceptsItems(inputs.items)
				|| !acceptsFluids(inputs.fluids)) return false;

		inputQueue.enqueue(inputs);
		if (hasFluidsToSend()) pushFluidsOut();
		if (hasItemsToSend()) pushItemsOut();
		return true;
	}

	private boolean acceptsItems(List<ItemStack> items) {
		List<InventoryAdaptor> inventoryAdaptors = getItemDestinations();
		if (items.size() > inventoryAdaptors.size() || items.size() > MAX_SLOTS) return false;
		for (int i = 0; i < items.size(); ++i)
			if (!inventoryAdaptors.get(i).simulateAdd(items.get(i).copy()).isEmpty()) return false;
		return true;
	}

	private boolean acceptsFluids(List<FluidStack> inputs) {
		List<IFluidTank> tanks = getController().getAbilities(MultiblockAbility.IMPORT_FLUIDS);
		if (inputs.size() > tanks.size() || inputs.size() > MAX_SLOTS) return false;
		for (int i = 0; i < inputs.size(); ++i)
			if (tanks.get(i).fill(inputs.get(i).copy(), false) < inputs.get(i).amount) return false;
		return true;
	}

	private List<InventoryAdaptor> getItemDestinations() {
		return getController().getAbilities(MultiblockAbility.IMPORT_ITEMS).stream()
				.filter(handler -> !(handler instanceof GhostCircuitItemStackHandler))
				.map(AdaptorItemHandler::new)
				.collect(Collectors.toList());
	}

	private void pushItemsOut() {
		if (!hasItemsToSend() || !isAttachedToMultiBlock()) return;
		inputQueue.deliverItems(getItemDestinations());
	}

	private void pushFluidsOut() {
		if (!hasFluidsToSend() || !isAttachedToMultiBlock()) return;
		inputQueue.deliverFluids(getController().getAbilities(MultiblockAbility.IMPORT_FLUIDS));
	}

	@Override
	public void notifySubstitutionChange() {
		for (ICraftingPatternDetails details : getPatterns()) {
			if (details instanceof AbstractPatternHelper helper) {
				helper.injectSubstitutions(substitutionStorage);
			}
		}

		notifyPatternChange();
	}

	public abstract Collection<? extends ICraftingPatternDetails> getPatterns();

	public boolean hasItemsToSend() {
		return inputQueue.hasItems();
	}

	public boolean hasFluidsToSend() {
		return inputQueue.hasFluids();
	}

	public void setBlockingMode(BlockingMode blockingMode) {
		if (blockingMode == null || this.blockingMode == blockingMode) return;
		this.blockingMode = blockingMode;
		if (getWorld() != null && !getWorld().isRemote) markDirty();
	}

	public BlockingMode getBlockingMode() {
		return blockingMode;
	}

	@Override
	public void setWorkingEnabled(boolean workingEnabled) {
		if (this.workingEnabled == workingEnabled) return;
		this.workingEnabled = workingEnabled;
		World world = getWorld();
		if (world != null && !world.isRemote) {
			markDirty();
			writeCustomData(GregtechDataCodes.WORKING_ENABLED, buf -> buf.writeBoolean(workingEnabled));
			notifyPatternChange();
		}
	}

	@Override
	public boolean isWorkingEnabled() {
		return workingEnabled;
	}
}
