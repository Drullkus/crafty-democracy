package us.drullk.craftydemocracy;

import java.util.Arrays;
import java.util.List;

public class StringUtil {

	public static boolean isAlphaNumeric(String str) {
		return str.matches("[a-zA-Z0-9]*");
	}

	public static List<String> splitWhitespace(String str) {
		return Arrays.asList(str.split("\\s+"));
	}

}
