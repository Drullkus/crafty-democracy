package us.drullk.craftydemocracy;

import java.util.*;

public class StringUtil {

	public static boolean isAlphaNumeric(String str) {
		return str.matches("[A-Za-z0-9]+");
	}

	public static List<String> splitWhitespace(String str) {
		return Arrays.asList(str.split("\\s+"));
	}

}
