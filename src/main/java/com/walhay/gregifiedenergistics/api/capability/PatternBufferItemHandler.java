package com.walhay.gregifiedenergistics.api.capability;

import gregtech.api.metatileentity.MetaTileEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.items.IItemHandler;

/** Item-only recipe inventory with dynamic slots and controller notifications. */
public final class PatternBufferItemHandler extends SegmentItemHandlerList implements IPatternBufferHandler {
	private final List<MetaTileEntity> notifiableEntities = new ArrayList<>();

	public PatternBufferItemHandler(IItemHandler... handlers) {
		super(handlers);
	}

	@Override
	public void onContentsChanged() {
		for (MetaTileEntity entity : notifiableEntities) addToNotifiedList(entity, this, false);
	}

	@Override
	public void addNotifiableMetaTileEntity(MetaTileEntity entity) {
		if (entity != null && !notifiableEntities.contains(entity)) notifiableEntities.add(entity);
	}

	@Override
	public void removeNotifiableMetaTileEntity(MetaTileEntity entity) {
		notifiableEntities.remove(entity);
	}
}
