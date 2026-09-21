package com.osrsevents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class OsrsEventsApiTest
{
	@Test
	public void acceptsHttpsOnly()
	{
		assertEquals("https://osrs-events.com/", OsrsEventsApi.baseUrl("https://osrs-events.com").toString());
		assertEquals("https://osrs-events.test/", OsrsEventsApi.baseUrl(" https://osrs-events.test ").toString());
		assertNull(OsrsEventsApi.baseUrl("http://osrs-events.com"));
		assertNull(OsrsEventsApi.baseUrl("http://127.0.0.1:8010"));
		assertNull(OsrsEventsApi.baseUrl("osrs-events.com"));
		assertNull(OsrsEventsApi.baseUrl(""));
	}
}
