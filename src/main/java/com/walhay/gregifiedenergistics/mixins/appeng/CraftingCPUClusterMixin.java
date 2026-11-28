package com.walhay.gregifiedenergistics.mixins.appeng;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.tile.crafting.TileCraftingTile;
import appeng.util.item.AEItemStack;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.walhay.gregifiedenergistics.GregifiedEnergisticsMod;
import com.walhay.gregifiedenergistics.api.patterns.implementations.GhostCircuitPatternWrapper;
import com.walhay.gregifiedenergistics.api.patterns.implementations.RecipePatternHelper;
import java.util.HashSet;
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
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Snapshots custom ME patterns and restores them with their original inputs when a CPU reloads. */
@Mixin(CraftingCPUCluster.class)
public abstract class CraftingCPUClusterMixin {
	@Unique private static final String TASKS_TAG = "tasks";

	@Unique private static final String PROGRESS_TAG = "craftingProgress";

	@Unique private static final String RECIPE_TAG = "gregifiedRecipe";

	@Unique private static final String GHOST_CIRCUIT_TAG = "gregifiedGhostCircuit";

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

	@Unique private NBTTagList gregified$unresolvedTasks = new NBTTagList();

	@ModifyVariable(
			method = "addCrafting(Lappeng/api/networking/crafting/ICraftingPatternDetails;J)V",
			at = @At("HEAD"),
			argsOnly = true,
			remap = false)
	private ICraftingPatternDetails gregified$copyRecipeTask(ICraftingPatternDetails pattern) {
		return pattern instanceof RecipePatternHelper helper ? helper.copy() : pattern;
	}

	@WrapOperation(
			method = "writeToNBT",
			at =
					@At(
							value = "INVOKE",
							target =
									"Lappeng/me/cluster/implementations/CraftingCPUCluster;writeItem(Lappeng/api/storage/data/IAEItemStack;)Lnet/minecraft/nbt/NBTTagCompound;"),
			slice =
					@Slice(
							from =
									@At(
											value = "INVOKE",
											target =
													"Lappeng/api/networking/crafting/ICraftingPatternDetails;getPattern()Lnet/minecraft/item/ItemStack;")),
			remap = false,
			require = 1,
			allow = 1)
	private NBTTagCompound gregified$writeTask(
			CraftingCPUCluster cluster,
			IAEItemStack item,
			Operation<NBTTagCompound> original,
			@Local Map.Entry<ICraftingPatternDetails, ?> entry) {
		NBTTagCompound task = original.call(cluster, item);
		if (entry.getKey() instanceof RecipePatternHelper recipe) {
			task.setTag(RECIPE_TAG, recipe.writeToNBT());
		} else if (entry.getKey() instanceof GhostCircuitPatternWrapper circuit) {
			task.setInteger(GHOST_CIRCUIT_TAG, circuit.getCircuitValue());
		}
		return task;
	}

	@Inject(method = "writeToNBT", at = @At("TAIL"), remap = false)
	private void gregified$writeUnresolvedTasks(NBTTagCompound data, CallbackInfo ci) {
		if (gregified$unresolvedTasks.isEmpty()) return;
		NBTTagList saved = data.getTagList(TASKS_TAG, NBT.TAG_COMPOUND);
		for (int i = 0; i < gregified$unresolvedTasks.tagCount(); ++i) {
			saved.appendTag(gregified$unresolvedTasks.getCompoundTagAt(i).copy());
		}
		data.setTag(TASKS_TAG, saved);
	}

	@Inject(method = "readFromNBT", at = @At("TAIL"), remap = false)
	private void gregified$readTasks(NBTTagCompound data, CallbackInfo ci) {
		gregified$unresolvedTasks = new NBTTagList();
		NBTTagList saved = data.getTagList(TASKS_TAG, NBT.TAG_COMPOUND);
		Set<ICraftingPatternDetails> replacedPatterns = new HashSet<>();
		for (int i = 0; i < saved.tagCount(); ++i) {
			NBTTagCompound task = saved.getCompoundTagAt(i);
			if (!gregified$isCustomTask(task)) continue;

			ItemStack stack = gregified$readPatternStack(task);
			ICraftingPatternDetails originalPattern = gregified$decodePattern(stack);
			if (originalPattern != null && replacedPatterns.add(originalPattern)) tasks.remove(originalPattern);
			gregified$restoreCustomTask(task, stack, originalPattern);
		}

		if (replacedPatterns.isEmpty()) return;
		// AE2 decodes a ghost-circuit task to the same key as a task using a physical circuit.
		// Restore ordinary tasks whose shared key was removed above.
		for (int i = 0; i < saved.tagCount(); ++i) {
			NBTTagCompound task = saved.getCompoundTagAt(i);
			if (gregified$isCustomTask(task)) continue;
			ICraftingPatternDetails pattern = gregified$decodePattern(gregified$readPatternStack(task));
			if (replacedPatterns.contains(pattern)) addCrafting(pattern, task.getLong(PROGRESS_TAG));
		}
	}

	@Unique private static boolean gregified$isCustomTask(NBTTagCompound task) {
		return task.hasKey(RECIPE_TAG) || task.hasKey(GHOST_CIRCUIT_TAG);
	}

	@Unique private static ItemStack gregified$readPatternStack(NBTTagCompound task) {
		IAEItemStack encoded = AEItemStack.fromNBT(task);
		return encoded == null ? ItemStack.EMPTY : encoded.createItemStack();
	}

	@Unique private ICraftingPatternDetails gregified$decodePattern(ItemStack stack) {
		return stack.getItem() instanceof ICraftingPatternItem item
				? item.getPatternForItem(stack.copy(), getWorld())
				: null;
	}

	@Unique private void gregified$restoreCustomTask(
			NBTTagCompound task, ItemStack stack, ICraftingPatternDetails originalPattern) {
		try {
			ICraftingPatternDetails pattern = task.hasKey(RECIPE_TAG)
					? RecipePatternHelper.readFromNBT(task.getCompoundTag(RECIPE_TAG), stack)
					: gregified$restoreGhostPattern(task, originalPattern);
			long progress = task.getLong(PROGRESS_TAG);
			if (pattern != null && progress >= 0) {
				addCrafting(pattern, progress);
				return;
			}
		} catch (IllegalArgumentException | UnsupportedOperationException ignored) {
			// The current recipe or pattern cannot accept the saved inputs.
		}
		gregified$unresolvedTasks.appendTag(task.copy());
	}

	@Unique private static ICraftingPatternDetails gregified$restoreGhostPattern(
			NBTTagCompound task, ICraftingPatternDetails originalPattern) {
		ICraftingPatternDetails wrapped = GhostCircuitPatternWrapper.wrap(originalPattern);
		return wrapped instanceof GhostCircuitPatternWrapper circuit
						&& circuit.getCircuitValue() == task.getInteger(GHOST_CIRCUIT_TAG)
				? wrapped
				: null;
	}

	@Inject(method = "updateCraftingLogic", at = @At("HEAD"), remap = false, cancellable = true)
	private void gregified$cancelUnrecoverableJob(
			IGrid grid, IEnergyGrid energy, CraftingGridCache cache, CallbackInfo ci) {
		if (gregified$unresolvedTasks.isEmpty()) return;
		if (grid != null && getCore() != null) {
			GregifiedEnergisticsMod.LOGGER.warn(
					"Cancelling saved crafting job whose recipe could not be restored; returning CPU-owned inputs through AE2");
			cancel();
		}
		ci.cancel();
	}

	@Inject(method = "cancel", at = @At("TAIL"), remap = false)
	private void gregified$clearDeferredCancellation(CallbackInfo ci) {
		gregified$unresolvedTasks = new NBTTagList();
	}
}
