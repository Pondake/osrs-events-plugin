package com.osrsevents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 * The wordings here are the ones RuneLite's own ChatCommandsPlugin matches
 * (client 1.12.39). They are not guesses, and a change to this parser starts
 * by reading that file again.
 */
public class KillCountTest
{
	private static KillCount.Parsed parse(String message)
	{
		// The plugin strips tags before parsing; these are the stripped lines.
		return KillCount.parse(message.replaceAll("<[^>]*>", ""));
	}

	@Test
	public void readsAPlainKillCount()
	{
		KillCount.Parsed parsed = parse("Your Zalcano kill count is: 383.");

		assertNotNull(parsed);
		assertEquals("Zalcano", parsed.name);
		assertEquals(383, parsed.count);
	}

	@Test
	public void readsAColouredCount()
	{
		assertEquals(1234, parse("Your Zalcano kill count is: <col=ff0000>1,234</col>.").count);
		assertEquals(1234, parse("Your Zalcano kill count is: @red@1,234</col>.").count);
	}

	/** The descriptor sits in front of the name as often as behind it. */
	@Test
	public void readsTheDescriptorsThatComeFirst()
	{
		assertEquals("Chambers of Xeric", parse("Your completed Chambers of Xeric count is: 5.").name);
		assertEquals("Wintertodt", parse("Your subdued Wintertodt count is: 50.").name);
		assertEquals("Barrows", parse("Your completion count for Barrows is: 12.").name);
	}

	/** "kill" is not the only middle word. */
	@Test
	public void readsTheOtherMiddleWords()
	{
		assertEquals("Barrows chest", parse("Your Barrows chest count is: 12.").name);
		assertEquals("Tempoross", parse("Your Tempoross kill count is: 100.").name);
		assertEquals("Hespori", parse("Your Hespori harvest count is: 8.").name);
		assertEquals("Prifddinas Agility Course", parse("Your Prifddinas Agility Course lap count is: 200.").name);
		assertEquals("Gauntlet", parse("Your Gauntlet completion count is: 30.").name);
		assertEquals("Yama", parse("Your Yama success count is: 4.").name);
	}

	/** A line with no descriptor at all is some other message, not a kill count. */
	@Test
	public void ignoresALineThatOnlyLooksLikeOne()
	{
		assertNull(parse("Your reward is: 500 coins."));
		assertNull(parse("You have killed 5 chickens."));
	}

	/**
	 * Counters with a wording of their own. The name is the wiki page title,
	 * because that is what a task links and what the server matches against —
	 * RuneLite's own "Hunter Rumours" would never match a square titled after
	 * the wiki's "Hunters' Rumours".
	 */
	@Test
	public void readsTheCountersWithTheirOwnWording()
	{
		KillCount.Parsed rumours = parse("You have completed @red@12</col> rumours for the Hunter Guild.");
		assertEquals("Hunters' Rumours", rumours.name);
		assertEquals(12, rumours.count);

		assertEquals("Hunters' Rumours", parse("You have completed @red@1</col> rumour for the Hunter Guild.").name);

		KillCount.Parsed rifts = parse("Amount of Rifts you have closed: <col=ff0000>1,234</col>.");
		assertEquals("Guardians of the Rift", rifts.name);
		assertEquals(1234, rifts.count);

		KillCount.Parsed coffin = parse("You have opened the Grand Hallowed Coffin <col=ff0000>57</col> times!");
		assertEquals("Grand Hallowed Coffin", coffin.name);
		assertEquals(57, coffin.count);

		assertEquals(1, parse("You have opened the Grand Hallowed Coffin <col=ff0000>1</col> time!").count);
	}

	/**
	 * Each floor counts on its own, which is what makes "run floor 1 twenty
	 * times" expressible. The name is not a wiki page title and does not need
	 * to be — a task links a page, but its title is free text and the title
	 * is what a report is matched against.
	 */
	@Test
	public void countsEachSepulchreFloorSeparately()
	{
		KillCount.Parsed first = parse("You have completed Floor 1 of the Hallowed Sepulchre! Total completions: <col=ff0000>50</col>.");
		assertEquals("Hallowed Sepulchre Floor 1", first.name);
		assertEquals(50, first.count);

		KillCount.Parsed fifth = parse("You have completed Floor 5 of the Hallowed Sepulchre! Total completions: <col=ff0000>50</col>.");
		assertEquals("Hallowed Sepulchre Floor 5", fifth.name);
		assertEquals(50, fifth.count);
	}
}
