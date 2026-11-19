package com.walhay.gregifiedenergistics.common.metatileentities.multiblockparts;

import static com.walhay.gregifiedenergistics.api.mui.GregifiedEnergisticsGuiTextures.BLOCKING_MODE;
import static com.walhay.gregifiedenergistics.api.patterns.substitutions.SubstitutionStorage.STORAGE_TAG;
import static com.walhay.gregifiedenergistics.api.util.BlockingMode.BLOCKING_MODE_TAG;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.me.GridAccessException;
import appeng.util.InventoryAdaptor;
import appeng.util.inv.AdaptorItemHandler;
import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.Icon;
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
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.walhay.gregifiedenergistics.GregifiedEnergisticsConfig;
import com.walhay.gregifiedenergistics.api.capability.InfiniteItemStackHandler;
import com.walhay.gregifiedenergistics.api.metatileentity.MetaTileEntityCraftingProvider;
import com.walhay.gregifiedenergistics.api.patterns.AbstractPatternHelper;
import com.walhay.gregifiedenergistics.api.patterns.ISubstitutionNotifiable;
import com.walhay.gregifiedenergistics.api.patterns.ISubstitutionStorage;
import com.walhay.gregifiedenergistics.api.patterns.substitutions.SubstitutionStorage;
import com.walhay.gregifiedenergistics.api.util.AssemblyLineFluidTransfer;
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
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;

public abstract class MTEAbstractAssemblyLineBus extends MetaTileEntityCraftingProvider<IAEFluidStack>
		implements IMultiblockAbilityPart<IItemHandlerModifiable>, ISubstitutionNotifiable {

	public static final String WORKING_ENABLED_TAG = "WorkingEnabled";
	public static final String USE_FLUID_TAG = "FluidMode";
	public static final String FLUID_TO_SEND_TAG = "FluidToSend";
	public static final String ITEM_TO_SEND_TAG = "ItemToSend";
	public static final String SEND_SLOT_TAG = "Slot";
	private static final String OWNED_FLUID_TAG = "ExtractedFluids";

	private final Int2ObjectOpenHashMap<ItemStack> waitingToSend = new Int2ObjectOpenHashMap<>();
	// Requests still belong to ME. Only extractedFluids have been removed from ME storage.
	private final Int2ObjectOpenHashMap<FluidStack> fluidWaitingToSend = new Int2ObjectOpenHashMap<>();
	private final Int2ObjectOpenHashMap<FluidStack> extractedFluids = new Int2ObjectOpenHashMap<>();
	private BlockingMode blockingMode = BlockingMode.NO_BLOCKING;
	private boolean useFluids = true;
	private boolean workingEnabled = true;
	protected final ISubstitutionStorage substitutionStorage = new SubstitutionStorage(this);

	/* ###########################
	###     MTE METHODS     ###
	########################### */

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
		writeQueueData(data);
	}

	@Override
	public void initFromItemStackData(NBTTagCompound data) {
		super.initFromItemStackData(data);
		if (data != null) readQueueData(data);
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
		if (stack.hasTagCompound()
				&& (stack.getTagCompound().hasKey(ITEM_TO_SEND_TAG)
						|| stack.getTagCompound().hasKey(FLUID_TO_SEND_TAG)
						|| stack.getTagCompound().hasKey(OWNED_FLUID_TAG))) {
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

		int patternColumns = GregifiedEnergisticsConfig.guiConfig.patternSlotsPerLine;
		if (patternColumns <= 0) patternColumns = 9;
		int columns = Math.max(
				Math.min(16, patternColumns),
				Math.max(1, Math.min(16, GregifiedEnergisticsConfig.guiConfig.substitutionSlotsPerLine)));
		ModularPanel panel = GTGuis.createPanel(
				this, Math.min(300, Math.max(199, (int) Math.ceil((columns * 18 + 10) / 0.9) + 14)), 200);

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

		var patternList = createPatternList(panel, sync);
		if (patternList != null) {
			tabs.child(new PageButton(pageCounter++, controller)
					.tab(GuiTextures.TAB_TOP, 0)
					.addTooltipLine(IKey.lang("gregtech.machine.workbench.tab.workbench"))
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

		var substitutionList = createSubstitutionList(panel, sync);
		if (substitutionList != null) {
			tabs.child(new PageButton(pageCounter, controller)
					.tab(GuiTextures.TAB_TOP, 0)
					.addTooltipLine(IKey.lang("gregtech.machine.workbench.tab.item_list"))
					.addTooltipLine(
							IKey.lang("gregtech.machine.workbench.storage_note").style(TextFormatting.DARK_GRAY))
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

		return panel.child(createButtonBar(sync))
				.child(tabs)
				.child(paged.top(7).widthRel(0.9f).controller(controller));
	}

	@SuppressWarnings("UnstableApiUsage")
	public Flow createButtonBar(PanelSyncManager sync) {
		BooleanSyncValue workingStateValue = new BooleanSyncValue(this::isWorkingEnabled, this::setWorkingEnabled);
		BooleanSyncValue fluidStateValue = new BooleanSyncValue(this::getUsingFluids, this::setUsingFluids);
		EnumSyncValue<BlockingMode> blockingStateValue =
				new EnumSyncValue<>(BlockingMode.class, this::getBlockingMode, this::setBlockingMode);

		sync.syncValue("working_enabled", workingStateValue);
		sync.syncValue("fluid_mode", fluidStateValue);
		sync.syncValue("blocking_mode", blockingStateValue);

		Icon detail = GTGuiTextures.BUTTON_POWER_DETAIL.asIcon().size(18, 6).marginTop(24);

		return Flow.column()
				.right(7)
				.bottom(7)
				.width(18)
				.height(18 * 4 + 5)
				.child(new ToggleButton()
						.name("power_button")
						.size(18)
						.disableHoverBackground()
						.overlay(true, detail, GTGuiTextures.BUTTON_POWER[1])
						.overlay(false, detail, GTGuiTextures.BUTTON_POWER[0])
						.value(workingStateValue)
						.tooltipAutoUpdate(true)
						.tooltipBuilder(t -> t.addLine(IKey.lang(
								workingStateValue.getBoolValue()
										? "gregifiedenergistics.gui.working.enabled"
										: "gregifiedenergistics.gui.working.disabled")))
						.marginTop(4)
						.top(18 * 3 + 5))
				.child(new ToggleButton()
						.value(fluidStateValue)
						.tooltipAutoUpdate(true)
						.tooltipBuilder(t -> t.addLine(IKey.lang(
								fluidStateValue.getBoolValue()
										? "gregifiedenergistics.gui.fluid_mode.enabled"
										: "gregifiedenergistics.gui.fluid_mode.disabled")))
						.top(18 * 2)
						.overlay(false, new ItemDrawable(Items.BUCKET))
						.overlay(true, new ItemDrawable(Items.WATER_BUCKET)))
				.child(new CycleButtonWidget()
						.value(blockingStateValue)
						.tooltipAutoUpdate(true)
						.tooltipBuilder(t -> t.addLine(IKey.lang(
								switch (blockingStateValue.getValue()) {
									case NO_BLOCKING -> "gregifiedenergistics.gui.no_blocking";
									case BLOCKING_MODE -> "gregifiedenergistics.gui.blocking_mode";
									case CRAFTING_BLOCKING_MODE -> "gregifiedenergistics.gui.crafting_blocking_mode";
								})))
						.top(18)
						.stateOverlay(0, BLOCKING_MODE[0])
						.stateOverlay(1, BLOCKING_MODE[1])
						.stateOverlay(2, BLOCKING_MODE[2]))
				.child(new ItemSlot()
						.slot(importItems, 0)
						.size(18)
						.tooltip(rt -> rt.addLine(IKey.lang("gregifiedenergistics.gui.item_slot"))));
	}

	public Widget<?> createPatternList(ModularPanel panel, PanelSyncManager syncHandler) {
		return null;
	}

	public Widget<?> createSubstitutionList(ModularPanel panel, PanelSyncManager syncHandler) {
		DynamicSyncHandler options = new DynamicSyncHandler().widgetProvider((manager, buf) -> {
			int count = buf.readVarInt();
			if (count < 0 || count > buf.readableBytes())
				throw new IllegalArgumentException("Invalid substitution GUI size");
			List<String> names = new java.util.ArrayList<>(count);
			for (int i = 0; i < count; ++i) names.add(buf.readString(32767));
			return new Grid()
					.minElementMargin(0)
					.minColWidth(18)
					.minRowHeight(18)
					.widthRel(1f)
					.heightRel(1f)
					.scrollable()
					.mapTo(
							Math.max(1, Math.min(16, GregifiedEnergisticsConfig.guiConfig.substitutionSlotsPerLine)),
							names,
							(index, name) -> (Widget<?>)
									new SubstitutionSlotWidget().storage(substitutionStorage, name, manager));
		});
		// New ingredient handlers must be registered outside the handler-map iteration.
		syncHandler.onServerTick(new Runnable() {
			private List<String> sentOptions;

			@Override
			public void run() {
				List<String> names = new java.util.ArrayList<>(substitutionStorage.getOptions());
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
		return Flow.column()
				.left(7)
				.widthRel(1f)
				.child(IKey.lang("gregifiedenergistics.gui.substitution_list").asWidget())
				.child(new DynamicSyncedWidget<>().widthRel(1f).height(78).syncHandler(options));
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
		data.setBoolean(USE_FLUID_TAG, useFluids);
		writeQueueData(data);
		return data;
	}

	private void writeQueueData(NBTTagCompound data) {
		NBTTagList items = new NBTTagList();
		for (var entry : waitingToSend.entrySet()) {
			NBTTagCompound item = InfiniteItemStackHandler.writeStack(entry.getValue());
			item.setInteger(SEND_SLOT_TAG, entry.getKey());
			items.appendTag(item);
		}
		if (items.isEmpty()) data.removeTag(ITEM_TO_SEND_TAG);
		else data.setTag(ITEM_TO_SEND_TAG, items);
		if (fluidWaitingToSend.isEmpty()) data.removeTag(FLUID_TO_SEND_TAG);
		else data.setTag(FLUID_TO_SEND_TAG, writeFluidQueue(fluidWaitingToSend));
		if (extractedFluids.isEmpty()) data.removeTag(OWNED_FLUID_TAG);
		else data.setTag(OWNED_FLUID_TAG, writeFluidQueue(extractedFluids));
	}

	private static NBTTagList writeFluidQueue(Int2ObjectOpenHashMap<FluidStack> queue) {
		NBTTagList entries = new NBTTagList();
		for (var entry : queue.entrySet()) {
			if (entry.getValue() == null || entry.getValue().amount <= 0) continue;
			NBTTagCompound fluid = entry.getValue().writeToNBT(new NBTTagCompound());
			fluid.setInteger(SEND_SLOT_TAG, entry.getKey());
			entries.appendTag(fluid);
		}
		return entries;
	}

	private static void readFluidQueue(NBTTagList entries, Int2ObjectOpenHashMap<FluidStack> queue) {
		queue.clear();
		for (int i = 0; i < entries.tagCount(); ++i) {
			NBTTagCompound entry = entries.getCompoundTagAt(i);
			int slot = entry.getInteger(SEND_SLOT_TAG);
			FluidStack fluid = FluidStack.loadFluidStackFromNBT(entry);
			if (slot >= 0 && slot < 4096 && fluid != null && fluid.amount > 0) queue.put(slot, fluid);
		}
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
		useFluids = !data.hasKey(USE_FLUID_TAG) || data.getBoolean(USE_FLUID_TAG);
		readQueueData(data);
	}

	private void readQueueData(NBTTagCompound data) {
		waitingToSend.clear();
		NBTTagList entries = data.getTagList(ITEM_TO_SEND_TAG, NBT.TAG_COMPOUND);
		for (int i = 0; i < entries.tagCount(); ++i) {
			NBTTagCompound entry = entries.getCompoundTagAt(i);
			int slot = entry.getInteger(SEND_SLOT_TAG);
			ItemStack stack = InfiniteItemStackHandler.readStack(entry);
			if (slot >= 0 && slot < 4096 && !stack.isEmpty()) waitingToSend.put(slot, stack);
		}
		readFluidQueue(data.getTagList(FLUID_TO_SEND_TAG, NBT.TAG_COMPOUND), fluidWaitingToSend);
		readFluidQueue(data.getTagList(OWNED_FLUID_TAG, NBT.TAG_COMPOUND), extractedFluids);
		for (var entry : extractedFluids.entrySet()) {
			if (!fluidWaitingToSend.containsKey(entry.getKey()))
				fluidWaitingToSend.put(entry.getKey(), entry.getValue().copy());
		}
	}

	@Override
	public void writeInitialSyncData(PacketBuffer buf) {
		super.writeInitialSyncData(buf);
		buf.writeBoolean(workingEnabled);
		buf.writeEnumValue(blockingMode);
		buf.writeBoolean(useFluids);
	}

	@Override
	public void receiveInitialSyncData(PacketBuffer buf) {
		super.receiveInitialSyncData(buf);
		workingEnabled = buf.readBoolean();
		blockingMode = buf.readEnumValue(BlockingMode.class);
		useFluids = buf.readBoolean();
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

		IAEFluidStack[] fluids = null;
		if (pattern instanceof AbstractPatternHelper patternHelper) {
			fluids = patternHelper.getFluidInputs();
		}

		if (acceptsItems(inventoryCrafting) && (!useFluids || acceptsFluids(fluids))) {
			int slot = 0;
			for (int i = 0; i < inventoryCrafting.getSizeInventory(); ++i) {
				ItemStack stack = inventoryCrafting.getStackInSlot(i);
				if (!stack.isEmpty()) {
					addToSendList(slot++, stack.copy());
				}
			}

			if (useFluids && fluids != null) {
				slot = 0;
				for (IAEFluidStack fluidStack : fluids) {
					if (fluidStack == null) continue;
					FluidStack stack = fluidStack.getFluidStack();
					if (stack != null && stack.amount != 0) {
						addToSendList(slot++, stack.copy());
					}
				}
				if (hasFluidsToSend()) pushFluidsOut();
			}
			if (hasItemsToSend()) pushItemsOut();
			return true;
		}

		return false;
	}

	// check if buses is able to accept items from pattern
	private boolean acceptsItems(final InventoryCrafting inventoryCrafting) {
		List<InventoryAdaptor> inventoryAdaptors = getController().getAbilities(MultiblockAbility.IMPORT_ITEMS).stream()
				.filter(i -> !(i instanceof GhostCircuitItemStackHandler))
				.map(AdaptorItemHandler::new)
				.collect(Collectors.toList());
		Iterator<InventoryAdaptor> it = inventoryAdaptors.iterator();

		for (int i = 0; i < inventoryCrafting.getSizeInventory(); ++i) {
			ItemStack stack = inventoryCrafting.getStackInSlot(i);
			if (stack.isEmpty()) {
				continue;
			}

			if (!it.hasNext()) {
				return false;
			}

			if (!it.next().simulateAdd(stack).isEmpty()) {
				return false;
			}
		}

		return true;
	}

	private boolean acceptsFluids(IAEFluidStack[] inputs) {
		if (inputs == null || inputs.length == 0) return true;
		if (getProxy() == null || !isAttachedToMultiBlock()) return false;
		List<IFluidTank> tanks = getController().getAbilities(MultiblockAbility.IMPORT_FLUIDS);
		List<IAEFluidStack> cumulative = new java.util.ArrayList<>();
		int slot = 0;
		for (IAEFluidStack input : inputs) {
			if (input == null || input.getStackSize() <= 0) continue;
			if (input.getStackSize() > Integer.MAX_VALUE || slot >= tanks.size()) return false;
			FluidStack fluid = input.getFluidStack();
			if (fluid == null || tanks.get(slot++).fill(fluid.copy(), false) < input.getStackSize()) return false;
			IAEFluidStack total = null;
			for (IAEFluidStack existing : cumulative)
				if (existing.getFluidStack().isFluidEqual(fluid)) {
					total = existing;
					break;
				}
			if (total == null) cumulative.add(input.copy());
			else total.setStackSize(total.getStackSize() + input.getStackSize());
		}
		try {
			var inventory = getProxy().getStorage().getInventory(getStorageChannel());
			for (IAEFluidStack total : cumulative) {
				IAEFluidStack available = inventory.extractItems(total, Actionable.SIMULATE, getActionSource());
				if (available == null || available.getStackSize() < total.getStackSize()) return false;
			}
			return true;
		} catch (GridAccessException ignored) {
			return false;
		}
	}

	// Push items from auto-crafting to input buses
	private void pushItemsOut() {
		if (!hasItemsToSend() || !isAttachedToMultiBlock()) return;
		List<InventoryAdaptor> inventoryAdaptors = getController().getAbilities(MultiblockAbility.IMPORT_ITEMS).stream()
				.filter(i -> !(i instanceof GhostCircuitItemStackHandler))
				.map(AdaptorItemHandler::new)
				.collect(Collectors.toList());

		Iterator<Map.Entry<Integer, ItemStack>> it = waitingToSend.entrySet().iterator();
		boolean changed = false;

		while (it.hasNext()) {
			Map.Entry<Integer, ItemStack> entry = it.next();

			int index = entry.getKey();
			ItemStack stack = entry.getValue();

			if (stack.isEmpty()) {
				it.remove();
				changed = true;
				continue;
			}

			if (index >= 0 && index < inventoryAdaptors.size()) {
				ItemStack result = inventoryAdaptors.get(index).addItems(stack.copy());
				if (result.isEmpty()) {
					it.remove();
					changed = true;
				} else if (!ItemStack.areItemStacksEqual(stack, result)) {
					entry.setValue(result.copy());
					changed = true;
				}
			}
		}

		if (changed) markDirty();
	}

	private void pushFluidsOut() {
		if (!hasFluidsToSend() || !isAttachedToMultiBlock()) return;
		List<IFluidTank> tanks = getController().getAbilities(MultiblockAbility.IMPORT_FLUIDS);
		Iterator<Map.Entry<Integer, FluidStack>> iterator =
				fluidWaitingToSend.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			int slot = entry.getKey();
			FluidStack request = entry.getValue();
			if (request == null || request.amount <= 0) {
				if (!extractedFluids.containsKey(slot)) iterator.remove();
				continue;
			}
			if (slot < 0 || slot >= tanks.size()) continue;
			try {
				FluidStack owned = extractedFluids.get(slot);
				var network = owned != null || getProxy() == null
						? null
						: getProxy().getStorage().getInventory(getStorageChannel());
				AssemblyLineFluidTransfer.Result result =
						AssemblyLineFluidTransfer.transfer(tanks.get(slot), request, owned, offer -> {
							if (network == null) return null;
							IAEFluidStack extracted = network.extractItems(
									getStorageChannel().createStack(offer), Actionable.MODULATE, getActionSource());
							return extracted == null ? null : extracted.getFluidStack();
						});
				if (result.remainder == null) extractedFluids.remove(slot);
				else extractedFluids.put(slot, result.remainder);
				request.amount -= result.delivered;
				if (request.amount <= 0 && result.remainder == null) iterator.remove();
				if (result.delivered > 0
						|| (owned == null
								? result.remainder != null
								: result.remainder == null || !owned.isFluidStackIdentical(result.remainder)))
					markDirty();
			} catch (GridAccessException ignored) {
				// ME is unavailable. Keep both the outstanding request and all already extracted fluid.
			}
		}
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

	/* ###################################
	###    GETTER/SETTER METHODS    ###
	################################### */

	public abstract Collection<? extends ICraftingPatternDetails> getPatterns();

	private void addToSendList(int slot, ItemStack stack) {
		waitingToSend.put(slot, stack);
		markDirty();
	}

	public boolean hasItemsToSend() {
		return !waitingToSend.isEmpty();
	}

	private void addToSendList(int slot, FluidStack stack) {
		fluidWaitingToSend.put(slot, stack);
		markDirty();
	}

	public boolean hasFluidsToSend() {
		return !fluidWaitingToSend.isEmpty();
	}

	public void setBlockingMode(BlockingMode blockingMode) {
		if (blockingMode == null || this.blockingMode == blockingMode) return;
		this.blockingMode = blockingMode;
		if (getWorld() != null && !getWorld().isRemote) markDirty();
	}

	public BlockingMode getBlockingMode() {
		return blockingMode;
	}

	public void setUsingFluids(boolean useFluids) {
		if (this.useFluids == useFluids) return;
		this.useFluids = useFluids;
		if (getWorld() != null && !getWorld().isRemote) markDirty();
	}

	public boolean getUsingFluids() {
		return useFluids;
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
