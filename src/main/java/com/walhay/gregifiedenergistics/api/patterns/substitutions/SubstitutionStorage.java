package com.walhay.gregifiedenergistics.api.patterns.substitutions;

import com.walhay.gregifiedenergistics.api.capability.GregifiedEnergisticsCapabilities;
import com.walhay.gregifiedenergistics.api.capability.GregifiedEnergisticsDataCodes;
import com.walhay.gregifiedenergistics.api.patterns.ISubstitutionNotifiable;
import com.walhay.gregifiedenergistics.api.patterns.ISubstitutionStorage;
import gregtech.api.metatileentity.MTETrait;
import gregtech.api.metatileentity.MetaTileEntity;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.common.capabilities.Capability;
import org.jetbrains.annotations.NotNull;

public class SubstitutionStorage extends MTETrait implements ISubstitutionStorage {
	public static final String STORAGE_TAG = "SubstitutionStorage";
	private final AESubstitutionStorage data = new AESubstitutionStorage(new NBTTagCompound());
	private long syncedRevision = -1;

	public SubstitutionStorage(MetaTileEntity mte) {
		super(mte);
	}

	@Override
	public int getOption(String name) {
		return data.getOption(name);
	}

	@Override
	public void setOption(String name, int option) {
		List<ItemStack> candidates = data.getCandidates(name);
		if (option < 0 || option >= candidates.size()) return;
		long revision = data.getRevision();
		data.setOption(name, option);
		if (revision != data.getRevision()) {
			changed();
			if (metaTileEntity instanceof ISubstitutionNotifiable notifiable) notifiable.notifySubstitutionChange();
		}
	}

	@Override
	public void registerCandidates(String name, ItemStack[] candidates) {
		long revision = data.getRevision();
		data.registerCandidates(name, candidates);
		if (revision != data.getRevision()) changed();
	}

	@Override
	public List<ItemStack> getCandidates(String name) {
		return data.getCandidates(name);
	}

	@Override
	public Collection<String> getOptions() {
		return data.getOptions();
	}

	private void changed() {
		if (metaTileEntity.getWorld() != null && !metaTileEntity.getWorld().isRemote) metaTileEntity.markDirty();
	}

	@Override
	public void update() {
		if (metaTileEntity.getWorld() == null
				|| metaTileEntity.getWorld().isRemote
				|| syncedRevision == data.getRevision()) return;
		syncedRevision = data.getRevision();
		NBTTagCompound snapshot = data.serializeSyncNBT();
		writeCustomData(GregifiedEnergisticsDataCodes.SUBSTITUTION_CHANGE, buf -> buf.writeCompoundTag(snapshot));
	}

	@Override
	public <T> T getCapability(Capability<T> capability) {
		return capability == GregifiedEnergisticsCapabilities.CAPABILITY_SUBSTITUTION_STORAGE
				? GregifiedEnergisticsCapabilities.CAPABILITY_SUBSTITUTION_STORAGE.cast(this)
				: null;
	}

	@Override
	public String getName() {
		return STORAGE_TAG;
	}

	@Override
	public NBTTagCompound serializeNBT() {
		return data.serializeNBT();
	}

	@Override
	public void deserializeNBT(NBTTagCompound compound) {
		data.deserializeNBT(compound);
		changed();
	}

	@Override
	public void writeInitialSyncData(@NotNull PacketBuffer buf) {
		buf.writeCompoundTag(data.serializeSyncNBT());
	}

	@Override
	public void receiveInitialSyncData(@NotNull PacketBuffer buf) {
		readSnapshot(buf);
	}

	@Override
	public void receiveCustomData(int discriminator, @NotNull PacketBuffer buf) {
		if (discriminator == GregifiedEnergisticsDataCodes.SUBSTITUTION_CHANGE) readSnapshot(buf);
	}

	private void readSnapshot(PacketBuffer buf) {
		try {
			NBTTagCompound snapshot = buf.readCompoundTag();
			if (snapshot != null) data.deserializeSyncNBT(snapshot);
		} catch (IOException exception) {
			throw new IllegalArgumentException("Invalid substitution snapshot", exception);
		}
	}
}
