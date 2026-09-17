package com.osrsevents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Same cases as the server's RunelitePluginApiTest::names. */
public class NameMatcherTest
{
	private static void same(String game, String wiki)
	{
		assertEquals(NameMatcher.normalize(wiki), NameMatcher.normalize(game));
	}

	private static void different(String game, String wiki)
	{
		assertNotEquals(NameMatcher.normalize(wiki), NameMatcher.normalize(game));
	}

	@Test
	public void matchesTheServer()
	{
		same("Pet snakeling", "Pet Snakeling");
		same("  Dragon   bones ", "Dragon bones");
		same("Abyssal_whip", "Abyssal whip");
		same("Karil’s coif", "Karil's coif");
		same("Karils coif", "Karil's coif");
		same("Prayer potion(4)", "Prayer potion");
		same("Games necklace (8)", "Games necklace");
		same("<col=ff9040>Giant Mole</col>", "Giant Mole");
		same("Giant Mole", "Giant Mole");
		different("Clue scroll (medium)", "Clue scroll");
		different("Berserker ring (i)", "Berserker ring");
		different("Dragon bones", "Big bones");
		assertEquals("karils coif", NameMatcher.normalize("Karil's coif"));
	}

	@Test
	public void rsnComparesLikeTheGame()
	{
		assertTrue(OsrsEventsPlugin.sameRsn("Iron Pondake", "iron_pondake"));
		assertTrue(OsrsEventsPlugin.sameRsn("Iron-Pondake", "Iron Pondake"));
		assertFalse(OsrsEventsPlugin.sameRsn("Pondake", "Zezima"));
	}
}
