package com.osrsevents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.google.gson.Gson;
import org.junit.Test;

public class OsrsEventsApiTest
{
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
	}
}
