package com.osrsevents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class AccountCharacterTest
{
	private static ApiModels.OsrsCharacter character(String rsn, boolean main)
	{
		ApiModels.OsrsCharacter character = new ApiModels.OsrsCharacter();
		character.rsn = rsn;
		character.main = main;
		return character;
	}

	private static final List<ApiModels.OsrsCharacter> ACCOUNT = Arrays.asList(
		character("Main Sample", true),
		character("iron_sample", false));

	@Test
	public void theMainAndItsAltsMayReport()
	{
		assertTrue(OsrsEventsPlugin.isAccountCharacter("Main Sample", ACCOUNT));
		assertTrue(OsrsEventsPlugin.isAccountCharacter("Iron Sample", ACCOUNT));
		assertFalse(OsrsEventsPlugin.findCharacter("Iron Sample", ACCOUNT).main);
	}

	@Test
	public void anotherCharacterDoesNot()
	{
		assertFalse(OsrsEventsPlugin.isAccountCharacter("Other Sample", ACCOUNT));
	}

	@Test
	public void nothingReportsBeforeTheNamesAreKnown()
	{
		assertFalse(OsrsEventsPlugin.isAccountCharacter("Main Sample", Collections.emptyList()));
		assertFalse(OsrsEventsPlugin.isAccountCharacter("Main Sample", null));
		assertFalse(OsrsEventsPlugin.isAccountCharacter(null, ACCOUNT));
	}

	@Test
	public void theMainIsTheOneMarkedMain()
	{
		assertEquals("Main Sample", OsrsEventsPlugin.mainRsn(ACCOUNT));
		assertNull(OsrsEventsPlugin.mainRsn(Collections.emptyList()));
	}

	@Test
	public void aServerWithoutAltsStillNamesTheMain()
	{
		ApiModels.EventsResponse events = new ApiModels.EventsResponse();
		events.rsn = "Main Sample";
		events.proven = true;

		List<ApiModels.OsrsCharacter> characters = OsrsEventsPlugin.charactersOf(events);

		assertEquals(1, characters.size());
		assertTrue(characters.get(0).main);
		assertTrue(characters.get(0).proven);
		assertTrue(OsrsEventsPlugin.charactersOf(new ApiModels.EventsResponse()).isEmpty());
	}

	@Test
	public void theListWinsOverTheMainFields()
	{
		ApiModels.EventsResponse events = new ApiModels.EventsResponse();
		events.rsn = "Main Sample";
		events.characters = ACCOUNT;

		assertEquals(ACCOUNT, OsrsEventsPlugin.charactersOf(events));
	}
}
