package sgdk.rescomp.tool;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import sgdk.rescomp.type.CompressionCustom;

public class CompressionCustomUsageTracker {

	private static Set<CompressionCustom> used = new HashSet<>();

	public static void markUsed (CompressionCustom cc) {
		if (cc != null && !used.contains(cc) && cc != CompressionCustom.NONE) {
			used.add(cc);
		}
	}

	public static Set<CompressionCustom> getUsed () {
		return Collections.unmodifiableSet(used);
	}
}
