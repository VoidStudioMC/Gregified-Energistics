package com.walhay.gregifiedenergistics.api.util;

import gregtech.api.GTValues;

public class GTHelperUtility {

	public static int getTierFromName(String name) {
		if (name == null || name.isEmpty()) return -1;

		for (int i = 0; i < GTValues.VN.length; ++i) {
			if (GTValues.VN[i].equalsIgnoreCase(name)) {
				return i;
			}
		}

		return -1;
	}
}
