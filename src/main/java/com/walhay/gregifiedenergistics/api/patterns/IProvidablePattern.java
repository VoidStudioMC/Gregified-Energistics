package com.walhay.gregifiedenergistics.api.patterns;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import java.util.Collection;
import java.util.Collections;

/** A provider entry that can publish one pattern or expand research into several recipe patterns. */
public interface IProvidablePattern extends ICraftingPatternDetails {

	void providePatterns(ICraftingMedium medium, ICraftingProviderHelper helper);

	default Collection<IProvidablePattern> getProvidedPatterns() {
		return Collections.singleton(this);
	}
}
