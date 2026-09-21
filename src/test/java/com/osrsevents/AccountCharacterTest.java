package com.osrsevents;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class AccountCharacterTest
{
	@Test
	public void theCharacterOnTheAccountMayReport()
	{
		assertTrue(OsrsEventsPlugin.isAccountCharacter("Iron Sample", "iron_sample"));
		assertTrue(OsrsEventsPlugin.isAccountCharacter("Iron Sample", "Iron Sample"));
	}

	@Test
	public void anotherCharacterDoesNot()
	{
		assertFalse(OsrsEventsPlugin.isAccountCharacter("Other Sample", "Main Sample"));
	}

	@Test
	public void nothingReportsBeforeTheNameIsKnown()
	{
		assertFalse(OsrsEventsPlugin.isAccountCharacter("Main Sample", ""));
		assertFalse(OsrsEventsPlugin.isAccountCharacter("Main Sample", null));
		assertFalse(OsrsEventsPlugin.isAccountCharacter(null, "Main Sample"));
	}
}
