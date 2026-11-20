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
import com.walhay.gregifiedenergistics.GregifiedEnergisticsMod;
import com.walhay.gregifiedenergistics.api.patterns.implementations.GhostCircuitPatternWrapper;
import com.walhay.gregifiedenergistics.api.patterns.implementations.RecipePatternHelper;
import com.walhay.gregifiedenergistics.api.patterns.substitutions.SubstitutionStorage;
import com.walhay.gregifiedenergistics.mixins.interfaces.ITaskProgressAccessor;
import java.util.Map;
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

@Mixin(CraftingCPUCluster.class)
public abstract class CraftingCPUClusterMixin {
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
		NBTTagList originals = data.getTagList("tasks", NBT.TAG_COMPOUND);
		NBTTagList saved = new NBTTagList();
		int index = 0;
		for (Map.Entry<ICraftingPatternDetails, Object> entry : tasks.entrySet()) {
			// Preserve AE2 and other addons' task metadata; vanilla writes these entries in map iteration order.
			NBTTagCompound item = index < originals.tagCount()
					? originals.getCompoundTagAt(index++).copy()
					: new NBTTagCompound();
			long progress = ((ITaskProgressAccessor) entry.getValue()).getValue();
			item.setLong("craftingProgress", progress);
			if (entry.getKey() instanceof RecipePatternHelper helper) {
				IAEItemStack encoded = AEItemStack.fromItemStack(helper.getPattern());
				if (encoded != null) encoded.writeToNBT(item);
				item.setTag("gregifiedRecipe", helper.writeToNBT());
				item.removeTag("gregifiedRecipeKey");
				item.removeTag("gregifiedUnsafeRecipe");
				item.removeTag("recipeId");
				item.removeTag(SubstitutionStorage.STORAGE_TAG);
			} else if (entry.getKey() instanceof GhostCircuitPatternWrapper wrapper) {
				item.setInteger("gregifiedGhostCircuit", wrapper.getCircuitValue());
			} else {
				item.setBoolean("gregifiedOrdinaryPattern", true);
			}
			saved.appendTag(item);
		}
		for (int i = 0; i < gregified$unresolvedTasks.tagCount(); ++i)
			saved.appendTag(gregified$unresolvedTasks.getCompoundTagAt(i).copy());
		data.setTag("tasks", saved);
	}

	@Inject(method = "readFromNBT", at = @At("TAIL"), remap = false)
	private void gregified$readTasks(NBTTagCompound data, CallbackInfo ci) {
		gregified$cancelPending = false;
		gregified$unresolvedTasks = new NBTTagList();
		NBTTagList saved = data.getTagList("tasks", NBT.TAG_COMPOUND);
		java.util.List<ICraftingPatternDetails> decoded = new java.util.ArrayList<>();
		java.util.Set<NBTTagCompound> ghostItems = new java.util.HashSet<>();
		for (int i = 0; i < saved.tagCount(); ++i) {
			NBTTagCompound item = saved.getCompoundTagAt(i);
			if (!item.hasKey("gregifiedGhostCircuit")) continue;
			IAEItemStack encoded = AEItemStack.fromNBT(item);
			if (encoded != null) ghostItems.add(encoded.createItemStack().serializeNBT());
		}
		// Remove our vanilla-decoded keys before restoring either normal or ghost variants.
		// They can share the same encoded item; removing during restoration would erase one variant.
		for (int i = 0; i < saved.tagCount(); ++i) {
			NBTTagCompound item = saved.getCompoundTagAt(i);
			IAEItemStack encoded = AEItemStack.fromNBT(item);
			ItemStack stack = encoded == null ? ItemStack.EMPTY : encoded.createItemStack();
			ICraftingPatternDetails original = stack.getItem() instanceof ICraftingPatternItem patternItem
					? patternItem.getPatternForItem(stack, getWorld())
					: null;
			decoded.add(original);
			boolean ordinaryCollision = encoded != null && ghostItems.contains(stack.serializeNBT());
			if (original != null
					&& ((item.getBoolean("gregifiedOrdinaryPattern")
									&& (original instanceof appeng.helpers.PatternHelper || ordinaryCollision))
							|| item.hasKey("gregifiedGhostCircuit")
							|| item.hasKey("gregifiedRecipe")
							|| item.hasKey("gregifiedRecipeKey")
							|| item.hasKey("recipeId")
							|| item.getBoolean("gregifiedUnsafeRecipe"))) tasks.remove(original);
		}
		for (int i = 0; i < saved.tagCount(); ++i) {
			NBTTagCompound item = saved.getCompoundTagAt(i);
			boolean recipeTask = item.hasKey("gregifiedRecipe")
					|| item.hasKey("gregifiedRecipeKey")
					|| item.hasKey("recipeId")
					|| item.getBoolean("gregifiedUnsafeRecipe");
			IAEItemStack encoded = AEItemStack.fromNBT(item);
			ItemStack stack = encoded == null ? ItemStack.EMPTY : encoded.createItemStack();
			ICraftingPatternDetails original = decoded.get(i);
			// Legacy unmarked circuit tasks are ambiguous: they may have consumed physical or ghost circuits.
			ICraftingPatternDetails wrapped = GhostCircuitPatternWrapper.wrap(original);
			if (!recipeTask && item.getBoolean("gregifiedOrdinaryPattern")) {
				boolean ordinaryCollision = encoded != null && ghostItems.contains(stack.serializeNBT());
				if (original == null) {
					// Opaque addon tasks may restore themselves without an encoded pattern item.
					if (stack.getItem() instanceof ICraftingPatternItem) gregified$deferCancellation(item);
					continue;
				}
				if (!(original instanceof appeng.helpers.PatternHelper) && !ordinaryCollision) continue;
				long progress = item.getLong("craftingProgress");
				if (progress < 0) gregified$deferCancellation(item);
				else addCrafting(original, progress);
				continue;
			}
			if (!recipeTask && wrapped == original && !item.hasKey("gregifiedGhostCircuit")) continue;
			if (!recipeTask && wrapped != original && !item.hasKey("gregifiedGhostCircuit")) {
				if (original != null) tasks.remove(original);
				gregified$deferCancellation(item);
				continue;
			}
			long progress = item.getLong("craftingProgress");
			try {
				ICraftingPatternDetails restored;
				if (recipeTask) {
					RecipePatternHelper helper =
							RecipePatternHelper.readFromNBT(item.getCompoundTag("gregifiedRecipe"), stack);
					if (helper == null) {
						gregified$deferCancellation(item);
						continue;
					}
					restored = helper;
				} else {
					if (!(wrapped instanceof GhostCircuitPatternWrapper wrapper)
							|| (item.hasKey("gregifiedGhostCircuit")
									&& wrapper.getCircuitValue() != item.getInteger("gregifiedGhostCircuit"))) {
						gregified$deferCancellation(item);
						continue;
					}
					restored = wrapped;
				}
				if (progress < 0) {
					gregified$deferCancellation(item);
					continue;
				}
				addCrafting(restored, progress);
			} catch (IllegalArgumentException | UnsupportedOperationException unsupported) {
				gregified$deferCancellation(item);
			}
		}
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
