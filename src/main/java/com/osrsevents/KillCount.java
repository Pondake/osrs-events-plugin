package com.osrsevents;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The "Your X kill count is: N" family of game messages.
 *
 * The wording is not one sentence but several, and guessing at it was wrong
 * twice: the descriptor sits in front of the name as often as behind it
 * ("Your completed Chambers of Xeric count is:", "Your subdued Wintertodt
 * count is:", "Your completion count for Barrows is:"), the middle word is
 * not always "kill" (harvest, lap, completion, success, Total Ticket), and
 * the number can arrive wrapped in an @col@ marker rather than a tag.
 *
 * This mirrors KILLCOUNT_PATTERN in RuneLite's own ChatCommandsPlugin
 * (client 1.12.39), which is maintained against the live game. Do not tighten
 * it from memory — read that file.
 */
final class KillCount
{
	/**
	 * Both halves are optional on their own, and a line with neither is not a
	 * kill count at all — "Your poisoned karambwan is: ..." would otherwise
	 * parse. RuneLite draws the same line for the same reason.
	 */
	private static final Pattern PATTERN = Pattern.compile(
		"Your (?<pre>completion count for |subdued |completed )?(?<name>.+?) "
			+ "(?<post>(?:(?:kill|harvest|lap|completion|success|Total Ticket) )?(?:count )?)"
			+ "is: ?(?:@[^@]+@)?(?<count>[0-9,]+)");

	static final class Parsed
	{
		final String name;
		final int count;

		private Parsed(String name, int count)
		{
			this.name = name;
			this.count = count;
		}
	}

	private KillCount()
	{
	}

	/** The message with its tags already stripped, or null when it is not one of these. */
	static Parsed parse(String message)
	{
		Matcher matcher = PATTERN.matcher(message);

		if (!matcher.find())
		{
			return null;
		}

		String pre = matcher.group("pre");
		String post = matcher.group("post");

		if ((pre == null || pre.isEmpty()) && (post == null || post.isEmpty()))
		{
			return null;
		}

		return new Parsed(matcher.group("name"), Integer.parseInt(matcher.group("count").replace(",", "")));
	}
}
