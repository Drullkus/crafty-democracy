package us.drullk.craftydemocracy;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.*;

public class StringUtil {

	public static boolean isAlphaNumeric(String str) {
		return str.matches("[A-Za-z0-9]+");
	}

	public static List<String> splitWhitespace(String str) throws CommandSyntaxException {
		StringReader reader = new StringReader(str);
		List<String> result = new ArrayList<>();
		while (reader.canRead()) {
			reader.skipWhitespace();
			if (!reader.canRead()) break;
			result.add(reader.readString());
		}
		return result;
	}

}
