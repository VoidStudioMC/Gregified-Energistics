package com.walhay.gregifiedenergistics.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.walhay.gregifiedenergistics.api.util.AssemblyLineFluidTransfer;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.init.Bootstrap;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AssemblyLineFluidTransferTest {
	@BeforeAll
	static void bootstrap() {
		Bootstrap.register();
	}

	@Test
	void emptySourceAndFullDestinationNeverLoseOrExtractFluid() {
		FluidStack request = new FluidStack(FluidRegistry.WATER, 1000);
		AtomicInteger extracted = new AtomicInteger();
		FluidStack full = AssemblyLineFluidTransfer.transfer(new FluidTank(0), request, null, stack -> {
			extracted.addAndGet(stack.amount);
			return stack;
		});
		assertNull(full);
		assertEquals(0, extracted.get());
		FluidStack empty = AssemblyLineFluidTransfer.transfer(new FluidTank(1000), request, null, stack -> null);
		assertNull(empty);
		assertEquals(1000, request.amount);
	}

	@Test
	void partialAcceptanceRetainsOwnedFluidAndRetryDoesNotExtractItAgain() {
		FluidStack request = new FluidStack(FluidRegistry.WATER, 1000);
		AtomicInteger extracted = new AtomicInteger();
		FluidTank changing = new FluidTank(1000) {
			@Override
			public int fill(FluidStack stack, boolean fill) {
				if (!fill) return super.fill(stack, false);
				FluidStack partial = stack.copy();
				partial.amount = Math.min(300, stack.amount);
				return super.fill(partial, true);
			}
		};
		FluidStack first = AssemblyLineFluidTransfer.transfer(changing, request, null, stack -> {
			extracted.addAndGet(stack.amount);
			return stack.copy();
		});
		assertEquals(300, changing.getFluidAmount());
		assertEquals(700, first.amount);
		assertEquals(extracted.get(), changing.getFluidAmount() + first.amount);
		request.amount -= changing.getFluidAmount();
		FluidTank retryDestination = new FluidTank(1000);
		FluidStack retry = AssemblyLineFluidTransfer.transfer(retryDestination, request, first, stack -> {
			extracted.addAndGet(stack.amount);
			return stack;
		});
		assertEquals(700, retryDestination.getFluidAmount());
		assertNull(retry);
		assertEquals(1000, extracted.get());
		assertEquals(extracted.get(), changing.getFluidAmount() + retryDestination.getFluidAmount());
	}

	@Test
	void partialSourceAndLimitedCapacityOnlyConsumeDeliveredOrRetainedQuantity() {
		FluidTank destination = new FluidTank(400);
		AtomicInteger asked = new AtomicInteger();
		FluidStack remainder = AssemblyLineFluidTransfer.transfer(
				destination, new FluidStack(FluidRegistry.WATER, 1000), null, stack -> {
					asked.set(stack.amount);
					return new FluidStack(FluidRegistry.WATER, 200);
				});
		assertEquals(400, asked.get());
		assertEquals(200, destination.getFluidAmount());
		assertNull(remainder);
	}
}
