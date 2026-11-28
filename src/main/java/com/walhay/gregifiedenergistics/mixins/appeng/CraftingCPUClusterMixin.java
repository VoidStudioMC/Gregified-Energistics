package com.walhay.gregifiedenergistics.mixins.appeng;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.storage.data.IAEItemStack;
import appeng.helpers.PatternHelper;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.tile.crafting.TileCraftingTile;
import appeng.util.item.AEItemStack;
import com.walhay.gregifiedenergistics.GregifiedEnergisticsMod;
import com.walhay.gregifiedenergistics.api.patterns.implementations.GhostCircuitPatternWrapper;
import com.walhay.gregifiedenergistics.api.patterns.implementations.RecipePatternHelper;
import com.walhay.gregifiedenergistics.api.patterns.substitutions.SubstitutionStorage;
import com.walhay.gregifiedenergistics.mixins.interfaces.ITaskProgressAccessor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Snapshots custom ME patterns and restores them with their original inputs when a CPU reloads. */
@Mixin(CraftingCPUCluster.class)
public abstract class CraftingCPUClusterMixin {
	@Unique private static final String TASKS_TAG = "tasks";

	@Unique private static final String PROGRESS_TAG = "craftingProgress";

	@Unique private static final String RECIPE_TAG = "gregifiedRecipe";

	@Unique private static final String LEGACY_RECIPE_KEY_TAG = "gregifiedRecipeKey";

	@Unique private static final String LEGACY_RECIPE_ID_TAG = "recipeId";

	@Unique private static final String UNSAFE_RECIPE_TAG = "gregifiedUnsafeRecipe";

	@Unique private static final String GHOST_CIRCUIT_TAG = "gregifiedGhostCircuit";

	@Unique private static final String ORDINARY_PATTERN_TAG = "gregifiedOrdinaryPattern";

	@Shadow(remap = false)
	@Final
	private Map<ICraftingPatternDetails, Object> tasks;

	@Shadow(remap = false)
	public abstract void addCrafting(ICraftingPatternDetails pattern, long count);

	@Shadow(remap = false)
	public abstract void cancel();

	@Shadow(remap = false)
	private World getWorld() {
		throw new AssertionError();
	}

	@Shadow(remap = false)
	private TileCraftingTile getCore() {
		throw new AssertionError();
	}

	@Unique private boolean gregified$cancelPending;

	@Unique private NBTTagList gregified$unresolvedTasks = new NBTTagList();

	@ModifyVariable(
			method = "addCrafting(Lappeng/api/networking/crafting/ICraftingPatternDetails;J)V",
			at = @At("HEAD"),
			argsOnly = true,
			remap = false)
	private ICraftingPatternDetails gregified$copyRecipeTask(ICraftingPatternDetails pattern) {
		return pattern instanceof RecipePatternHelper helper ? helper.copy() : pattern;
	}

	@Inject(method = "writeToNBT", at = @At("TAIL"), remap = false)
	private void gregified$writeTasks(NBTTagCompound data, CallbackInfo ci) {
		NBTTagList originals = data.getTagList(TASKS_TAG, NBT.TAG_COMPOUND);
		NBTTagList saved = new NBTTagList();
		int index = 0;
		for (Map.Entry<ICraftingPatternDetails, Object> entry : tasks.entrySet()) {
			// Preserve AE2 and other addons' task metadata; vanilla writes these entries in map iteration order.
			NBTTagCompound item = index < originals.tagCount()
					? originals.getCompoundTagAt(index++).copy()
					: new NBTTagCompound();
			long progress = ((ITaskProgressAccessor) entry.getValue()).getValue();
			item.setLong(PROGRESS_TAG, progress);
			if (entry.getKey() instanceof RecipePatternHelper helper) {
				IAEItemStack encoded = AEItemStack.fromItemStack(helper.getPattern());
				if (encoded != null) encoded.writeToNBT(item);
				item.setTag(RECIPE_TAG, helper.writeToNBT());
				item.removeTag(LEGACY_RECIPE_KEY_TAG);
				item.removeTag(UNSAFE_RECIPE_TAG);
				item.removeTag(LEGACY_RECIPE_ID_TAG);
				item.removeTag(SubstitutionStorage.STORAGE_TAG);
			} else if (entry.getKey() instanceof GhostCircuitPatternWrapper wrapper) {
				item.setInteger(GHOST_CIRCUIT_TAG, wrapper.getCircuitValue());
			} else {
				item.setBoolean(ORDINARY_PATTERN_TAG, true);
			}
			saved.appendTag(item);
		}
		for (int i = 0; i < gregified$unresolvedTasks.tagCount(); ++i)
			saved.appendTag(gregified$unresolvedTasks.getCompoundTagAt(i).copy());
		data.setTag(TASKS_TAG, saved);
	}

	@Inject(method = "readFromNBT", at = @At("TAIL"), remap = false)
	private void gregified$readTasks(NBTTagCompound data, CallbackInfo ci) {
		gregified$cancelPending = false;
		gregified$unresolvedTasks = new NBTTagList();
		NBTTagList saved = data.getTagList(TASKS_TAG, NBT.TAG_COMPOUND);
		List<ItemStack> encodedStacks = new ArrayList<>(saved.tagCount());
		Set<NBTTagCompound> ghostPatternItems = new HashSet<>();
		for (int i = 0; i < saved.tagCount(); ++i) {
			NBTTagCompound task = saved.getCompoundTagAt(i);
			IAEItemStack encoded = AEItemStack.fromNBT(task);
			ItemStack stack = encoded == null ? null : encoded.createItemStack();
			encodedStacks.add(stack);
			if (stack != null && task.hasKey(GHOST_CIRCUIT_TAG)) ghostPatternItems.add(stack.serializeNBT());
		}

		// An ordinary task and a ghost-circuit task can share an encoded item. Remove the
		// AE2-decoded keys first so a later removal cannot erase a task we just restored.
		List<ICraftingPatternDetails> originalPatterns = new ArrayList<>(saved.tagCount());
		boolean[] ordinaryCollisions = new boolean[saved.tagCount()];
		for (int i = 0; i < saved.tagCount(); ++i) {
			NBTTagCompound task = saved.getCompoundTagAt(i);
			ItemStack stack = encodedStacks.get(i);
			boolean ordinaryCollision = stack != null && ghostPatternItems.contains(stack.serializeNBT());
			ordinaryCollisions[i] = ordinaryCollision;
			ICraftingPatternDetails original = stack != null && stack.getItem() instanceof ICraftingPatternItem pattern
					? pattern.getPatternForItem(stack.copy(), getWorld())
					: null;
			originalPatterns.add(original);
			boolean restoreOrdinary =
					task.getBoolean(ORDINARY_PATTERN_TAG) && (original instanceof PatternHelper || ordinaryCollision);
			if (original != null
					&& (restoreOrdinary || task.hasKey(GHOST_CIRCUIT_TAG) || gregified$isRecipeTask(task))) {
				tasks.remove(original);
			}
		}

		for (int i = 0; i < saved.tagCount(); ++i) {
			ItemStack stack = encodedStacks.get(i);
			gregified$restoreTask(
					saved.getCompoundTagAt(i),
					stack == null ? ItemStack.EMPTY : stack,
					originalPatterns.get(i),
					ordinaryCollisions[i]);
		}
	}

	@Unique private static boolean gregified$isRecipeTask(NBTTagCompound task) {
		return task.hasKey(RECIPE_TAG)
				|| task.hasKey(LEGACY_RECIPE_KEY_TAG)
				|| task.hasKey(LEGACY_RECIPE_ID_TAG)
				|| task.getBoolean(UNSAFE_RECIPE_TAG);
	}

	@Unique private void gregified$restoreTask(
			NBTTagCompound task, ItemStack stack, ICraftingPatternDetails original, boolean ordinaryCollision) {
		boolean recipeTask = gregified$isRecipeTask(task);
		long progress = task.getLong(PROGRESS_TAG);
		if (!recipeTask && task.getBoolean(ORDINARY_PATTERN_TAG)) {
			if (original == null) {
				// Opaque addon tasks may restore themselves without an encoded pattern item.
				if (stack.getItem() instanceof ICraftingPatternItem) gregified$deferCancellation(task);
			} else if (original instanceof PatternHelper || ordinaryCollision) {
				if (progress < 0) gregified$deferCancellation(task);
				else addCrafting(original, progress);
			}
			return;
		}

		ICraftingPatternDetails wrapped = recipeTask ? original : GhostCircuitPatternWrapper.wrap(original);
		if (!recipeTask && !task.hasKey(GHOST_CIRCUIT_TAG)) {
			if (wrapped != original) {
				// Unmarked legacy tasks may have consumed either physical or ghost circuits.
				tasks.remove(original);
				gregified$deferCancellation(task);
			}
			return;
		}
		try {
			ICraftingPatternDetails restored = recipeTask
					? RecipePatternHelper.readFromNBT(task.getCompoundTag(RECIPE_TAG), stack)
					: gregified$restoreGhostPattern(task, wrapped);
			if (restored == null || progress < 0) gregified$deferCancellation(task);
			else addCrafting(restored, progress);
		} catch (IllegalArgumentException | UnsupportedOperationException unsupported) {
			gregified$deferCancellation(task);
		}
	}

	@Unique private static ICraftingPatternDetails gregified$restoreGhostPattern(
			NBTTagCompound task, ICraftingPatternDetails wrapped) {
		return wrapped instanceof GhostCircuitPatternWrapper wrapper
						&& wrapper.getCircuitValue() == task.getInteger(GHOST_CIRCUIT_TAG)
				? wrapped
				: null;
	}

	@Unique private void gregified$deferCancellation(NBTTagCompound item) {
		gregified$cancelPending = true;
		gregified$unresolvedTasks.appendTag(item.copy());
	}

	@Inject(method = "updateCraftingLogic", at = @At("HEAD"), remap = false, cancellable = true)
	private void gregified$cancelUnrecoverableJob(
			IGrid grid, IEnergyGrid energy, CraftingGridCache cache, CallbackInfo ci) {
		if (!gregified$cancelPending) return;
		if (grid != null && getCore() != null) {
			GregifiedEnergisticsMod.LOGGER.warn(
					"Cancelling saved crafting job whose recipe could not be restored; returning CPU-owned inputs through AE2");
			cancel();
		}
		ci.cancel();
	}

	@Inject(method = "cancel", at = @At("TAIL"), remap = false)
	private void gregified$clearDeferredCancellation(CallbackInfo ci) {
		gregified$cancelPending = false;
		gregified$unresolvedTasks = new NBTTagList();
	}
}
