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
		assertNull(parse("You have completed 5 rumours for the Hunter Guild."));
	}
}
