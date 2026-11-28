package com.walhay.gregifiedenergistics.api.util;

import java.util.function.Function;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
import org.jetbrains.annotations.Nullable;

/** A queued ME request and an extracted remainder have different owners. Never extract a remainder twice. */
public final class AssemblyLineFluidTransfer {

	private AssemblyLineFluidTransfer() {}

	/**
	 * Attempts to deliver fluid already owned by the caller.
	 *
	 * @param destination input tank for this recipe position
	 * @param owned fluid already in the caller's queue
	 * @return a copy of any fluid still in the caller's custody, or {@code null} if none remains
	 */
	@Nullable public static FluidStack deliverOwned(IFluidTank destination, FluidStack owned) {
		if (owned == null || owned.amount <= 0) return null;
		if (destination == null) return owned.copy();
		return deliver(destination, owned, owned);
	}

	/**
	 * Extracts only what the destination can accept, or retries with the fluid already extracted.
	 *
	 * @param destination input tank for this recipe position
	 * @param request fluid type and requested amount
	 * @param owned previously extracted remainder, or {@code null} for a new extraction
	 * @param extract source operation returning the amount actually extracted, or {@code null}
	 * @return a copy of any extracted fluid that still needs delivery, or {@code null} if none remains
	 */
	@Nullable public static FluidStack transfer(
			IFluidTank destination, FluidStack request, FluidStack owned, Function<FluidStack, FluidStack> extract) {
		if (destination == null || request == null || request.amount <= 0) return copy(owned);
		if (owned != null && owned.amount > 0) {
			if (!owned.isFluidEqual(request)) return owned.copy();
			return deliver(destination, request, owned);
		}
		int accepted = Math.min(request.amount, Math.max(0, destination.fill(request.copy(), false)));
		if (accepted == 0) return null;
		FluidStack offer = request.copy();
		offer.amount = accepted;
		FluidStack extracted = extract.apply(offer);
		if (extracted == null || extracted.amount <= 0) return null;
		if (!extracted.isFluidEqual(request)) return extracted.copy();
		return deliver(destination, request, extracted);
	}

	@Nullable private static FluidStack deliver(IFluidTank destination, FluidStack request, FluidStack owned) {
		FluidStack offer = owned.copy();
		offer.amount = Math.min(request.amount, owned.amount);
		int filled = Math.min(offer.amount, Math.max(0, destination.fill(offer, true)));
		int remaining = owned.amount - filled;
		if (remaining == 0) return null;
		FluidStack remainder = owned.copy();
		remainder.amount = remaining;
		return remainder;
	}

	@Nullable private static FluidStack copy(FluidStack stack) {
		return stack == null || stack.amount <= 0 ? null : stack.copy();
	}
}
