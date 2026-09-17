package com.osrsevents;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Mirror of the server's RuneliteName::normalize. The two must stay identical:
 * the server sends names already normalised and this decides what to report.
 */
final class NameMatcher
{
	private static final Pattern TAGS = Pattern.compile("<[^>]*>");
	private static final Pattern APOSTROPHES = Pattern.compile("['’‘`]");
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");
	private static final Pattern NUMERIC_SUFFIX = Pattern.compile("\\s*\\(\\d+\\)$");

	private NameMatcher()
	{
	}

	static String normalize(String name)
	{
		if (name == null)
		{
			return "";
		}

		String s = TAGS.matcher(name).replaceAll("");
		s = s.replace('_', ' ').replace(' ', ' ');
		s = APOSTROPHES.matcher(s).replaceAll("");
		s = WHITESPACE.matcher(s).replaceAll(" ").trim();
		s = NUMERIC_SUFFIX.matcher(s).replaceAll("").trim();
		return s.toLowerCase(Locale.ROOT);
	}
}
