package com.walhay.gregifiedenergistics.mixins.appeng;

import com.walhay.gregifiedenergistics.mixins.interfaces.ITaskProgressAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "appeng.me.cluster.implementations.CraftingCPUCluster$TaskProgress")
public class TaskProgressMixin implements ITaskProgressAccessor {

	@Shadow(remap = false)
	public long value;

	@Override
	public void setValue(long value) {
		this.value = value;
	}

	@Override
	public long getValue() {
		return value;
	}
}
