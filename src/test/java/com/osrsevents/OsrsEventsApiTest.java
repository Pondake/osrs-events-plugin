package com.osrsevents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.junit.Test;

public class OsrsEventsApiTest
{
	@Test
	public void sendsTheVersionThePluginHubShows() throws IOException
	{
		Properties properties = new Properties();
		try (InputStream in = new FileInputStream("runelite-plugin.properties"))
		{
			properties.load(in);
		}

		assertEquals(properties.getProperty("version"), OsrsEventsApi.VERSION);
	}

	@Test
	public void acceptsHttpsOnlyExceptOnThisMachine()
	{
		assertEquals("https://osrs-events.com/", OsrsEventsApi.baseUrl("https://osrs-events.com").toString());
		assertEquals("https://osrs-events.test/", OsrsEventsApi.baseUrl(" https://osrs-events.test ").toString());
		assertEquals("http://127.0.0.1:8010/", OsrsEventsApi.baseUrl("http://127.0.0.1:8010").toString());
		assertNull(OsrsEventsApi.baseUrl("http://osrs-events.com"));
		assertNull(OsrsEventsApi.baseUrl("http://192.168.1.10:8010"));
		assertNull(OsrsEventsApi.baseUrl("http://localhost:8010"));
		assertNull(OsrsEventsApi.baseUrl("osrs-events.com"));
		assertNull(OsrsEventsApi.baseUrl(""));
	}

	@Test
	public void identitySpeaksTheContract()
	{
		Gson gson = new Gson();

		assertEquals("{\"rsn\":\"Iron Sample\",\"add_alt\":false}", gson.toJson(new ApiModels.Identity("Iron Sample", false)));

		ApiModels.IdentityResponse answer = gson.fromJson("{\"rsn\":\"Main Sample\",\"matched\":false,\"added\":false,\"reason\":\"limit\",\"proven\":true,"
			+ "\"characters\":[{\"rsn\":\"Main Sample\",\"main\":true,\"proven\":true}]}", ApiModels.IdentityResponse.class);
		assertEquals("limit", answer.reason);
		assertEquals("Main Sample", answer.characters.get(0).rsn);

		ApiModels.EventsResponse events = gson.fromJson("{\"rsn\":\"Main Sample\",\"max_characters\":5,\"characters\":[]}", ApiModels.EventsResponse.class);
		assertEquals(5, events.maxCharacters);
		assertNull(events.races);
	}

	@Test
	public void racesReadAsTheServerSendsThem()
	{
		ApiModels.EventsResponse events = new Gson().fromJson("{\"races\":[{\"id\":\"r\",\"title\":\"Boss of the Month\",\"type\":\"DROP_RACE\","
			+ "\"metric\":\"Zulrah\",\"unit\":\"kills\",\"rank\":null,\"entrants\":6,\"gained\":0,\"live\":0,\"leader\":null,"
			+ "\"ends_at\":\"2026-09-30T23:59:59+00:00\"}]}", ApiModels.EventsResponse.class);

		ApiModels.Race race = events.races.get(0);
		assertNull(race.rank);
		assertNull(race.leader);
		assertEquals("2026-09-30T23:59:59+00:00", race.endsAt);
	}

	@Test
	public void finishesAndOtherEventsReadAsTheServerSendsThem()
	{
		Gson gson = new Gson();

		ApiModels.CompletionResponse answer = gson.fromJson("{\"duplicate\":false,\"claims\":[],\"progress\":[],"
			+ "\"finishes\":[{\"event_id\":\"e\",\"event_title\":\"Sample bingo\",\"place\":2,\"provisional\":true,\"team\":\"Sample team\"}]}",
			ApiModels.CompletionResponse.class);
		ApiModels.FinishNews finish = answer.finishes.get(0);
		assertEquals("Sample bingo", finish.eventTitle);
		assertEquals(2, finish.place);
		assertTrue(finish.provisional);
		assertEquals("Sample team", finish.team);

		ApiModels.EventsResponse events = gson.fromJson("{\"events\":[{\"id\":\"e\",\"finish\":{\"place\":1,\"provisional\":false,\"team\":null}}],"
			+ "\"other_events\":[{\"id\":\"r\",\"type\":\"DROP_RACE\",\"status\":\"ended\",\"starts_at\":null,\"ends_at\":\"2026-09-30T23:59:59+00:00\","
			+ "\"finish\":null,\"rank\":3,\"entrants\":12},{\"id\":\"b\",\"type\":\"BINGO\",\"status\":\"paused\",\"rank\":null}]}",
			ApiModels.EventsResponse.class);
		assertEquals(1, events.events.get(0).finish.place);
		assertTrue(events.otherEvents.get(0).isRace());
		assertEquals(Integer.valueOf(3), events.otherEvents.get(0).rank);
		assertFalse(events.otherEvents.get(1).isRace());
		assertEquals("paused", events.otherEvents.get(1).status);
	}

	@Test
	public void placesReadAsOrdinals()
	{
		assertEquals("1st", OsrsEventsPlugin.ordinal(1));
		assertEquals("2nd", OsrsEventsPlugin.ordinal(2));
		assertEquals("3rd", OsrsEventsPlugin.ordinal(3));
		assertEquals("4th", OsrsEventsPlugin.ordinal(4));
		assertEquals("11th", OsrsEventsPlugin.ordinal(11));
		assertEquals("12th", OsrsEventsPlugin.ordinal(12));
		assertEquals("21st", OsrsEventsPlugin.ordinal(21));
	}

	@Test
	public void amountsShortenXpButNotKills()
	{
		assertEquals("309 kc", OsrsEventsPanel.amount(309, "kills"));
		assertEquals("1.35M xp", OsrsEventsPanel.amount(1_354_945, "xp"));
		assertEquals("41K xp", OsrsEventsPanel.amount(41_250, "xp"));
		assertEquals("950 xp", OsrsEventsPanel.amount(950, "xp"));
	}
}
