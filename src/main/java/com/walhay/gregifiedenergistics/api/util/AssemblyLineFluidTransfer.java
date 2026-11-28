package com.walhay.gregifiedenergistics.api.util;

import java.util.function.Function;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;

/** A queued ME request and an extracted remainder have different owners. Never extract a remainder twice. */
public final class AssemblyLineFluidTransfer {

	private AssemblyLineFluidTransfer() {}

	/**
	 * Attempts to deliver fluid already owned by the caller.
	 *
	 * @param destination input tank for this recipe position
	 * @param owned fluid already in the caller's queue
	 * @return the amount delivered and a copy of any fluid still in the caller's custody
	 */
	public static Result deliverOwned(IFluidTank destination, FluidStack owned) {
		if (destination == null || owned == null || owned.amount <= 0) return new Result(0, copy(owned));
		return deliver(destination, owned, owned);
	}

	/**
	 * Extracts only what the destination can accept, or retries with the fluid already extracted.
	 *
	 * @param destination input tank for this recipe position
	 * @param request fluid type and requested amount
	 * @param owned previously extracted remainder, or {@code null} for a new extraction
	 * @param extract source operation returning the amount actually extracted, or {@code null}
	 * @return the amount delivered and any extracted fluid that still needs delivery
	 */
	public static Result transfer(
			IFluidTank destination, FluidStack request, FluidStack owned, Function<FluidStack, FluidStack> extract) {
		if (destination == null || request == null || request.amount <= 0) return new Result(0, copy(owned));
		if (owned != null && owned.amount > 0) {
			if (!owned.isFluidEqual(request)) return new Result(0, owned.copy());
			return deliver(destination, request, owned);
		}
		int accepted = Math.min(request.amount, Math.max(0, destination.fill(request.copy(), false)));
		if (accepted == 0) return new Result(0, null);
		FluidStack offer = request.copy();
		offer.amount = accepted;
		FluidStack extracted = extract.apply(offer);
		if (extracted == null || extracted.amount <= 0) return new Result(0, null);
		if (!extracted.isFluidEqual(request)) return new Result(0, extracted.copy());
		return deliver(destination, request, extracted);
	}

	private static Result deliver(IFluidTank destination, FluidStack request, FluidStack owned) {
		FluidStack offer = owned.copy();
		offer.amount = Math.min(request.amount, owned.amount);
		int filled = Math.min(offer.amount, Math.max(0, destination.fill(offer, true)));
		FluidStack remainder = owned.copy();
		remainder.amount -= filled;
		return new Result(filled, remainder.amount > 0 ? remainder : null);
	}

	private static FluidStack copy(FluidStack stack) {
		return stack == null || stack.amount <= 0 ? null : stack.copy();
	}

	/** Outcome of one attempt to deliver fluid to a tank. */
	public static final class Result {
		/** Volume accepted by the destination, in millibuckets. */
		public final int delivered;
		/** Fluid still owned by the caller, or {@code null} if nothing is left to deliver. */
		public final FluidStack remainder;

		private Result(int delivered, FluidStack remainder) {
			this.delivered = delivered;
			this.remainder = remainder;
		}
	}
}
