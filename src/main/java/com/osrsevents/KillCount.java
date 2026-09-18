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
	/**
	 * Counters that do not use the sentence above at all. Each one is its own
	 * wording in the game, so each one is its own pattern; RuneLite keeps them
	 * apart for the same reason.
	 *
	 * The name is the **wiki page title**, not the name RuneLite files the
	 * count under, because the server matches a report against the title of a
	 * wiki-linked task. Those differ: RuneLite says "Hunter Rumours", the wiki
	 * page is "Hunters' Rumours", and after normalising apostrophes away that
	 * is still "hunter rumours" against "hunters rumours" — a square would
	 * never match.
	 */
	private static final Pattern RUMOURS = Pattern.compile(
		"You have completed (?:@[^@]+@)?(?<count>[0-9,]+) rumours? for the Hunter Guild");

	private static final Pattern RIFTS = Pattern.compile(
		"Amount of Rifts you have closed: (?:@[^@]+@)?(?<count>[0-9,]+)", Pattern.CASE_INSENSITIVE);

	/**
	 * A full run: the coffin is opened once at the end of one. A square that
	 * means "do the Sepulchre N times" means this.
	 */
	private static final Pattern COFFIN = Pattern.compile(
		"You have opened the Grand Hallowed Coffin (?:@[^@]+@)?(?<count>[0-9,]+) times?");

	/**
	 * One floor, counted per floor.
	 *
	 * Every floor carries its **own** total, so naming each floor separately
	 * is what keeps them apart — floor 1 at 50 and floor 5 at 50 are two
	 * different targets, not the same count twice. That makes "run floor 1
	 * twenty times" expressible, which one shared name could never be.
	 *
	 * The reported name is not a wiki page and does not have to be: a task
	 * links a wiki page, but its title is free text, and the title is what a
	 * report is matched against. A square for this carries the title
	 * "Hallowed Sepulchre Floor 1".
	 */
	private static final Pattern FLOOR = Pattern.compile(
		"You have completed Floor (?<floor>[0-9]+) of the Hallowed Sepulchre! "
			+ "Total completions: (?:@[^@]+@)?(?<count>[0-9,]+)");

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
		Parsed named = named(message);
		if (named != null)
		{
			return named;
		}

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

	/** The counters whose wording carries no name, so the name is ours to supply. */
	private static Parsed named(String message)
	{
		Parsed rumours = match(RUMOURS, message, "Hunters' Rumours");
		if (rumours != null)
		{
			return rumours;
		}

		Parsed rifts = match(RIFTS, message, "Guardians of the Rift");
		if (rifts != null)
		{
			return rifts;
		}

		Parsed coffin = match(COFFIN, message, "Grand Hallowed Coffin");
		if (coffin != null)
		{
			return coffin;
		}

		Matcher floor = FLOOR.matcher(message);

		return floor.find()
			? new Parsed("Hallowed Sepulchre Floor " + floor.group("floor"),
				Integer.parseInt(floor.group("count").replace(",", "")))
			: null;
	}

	private static Parsed match(Pattern pattern, String message, String name)
	{
		Matcher matcher = pattern.matcher(message);

		return matcher.find()
			? new Parsed(name, Integer.parseInt(matcher.group("count").replace(",", "")))
			: null;
	}
}
