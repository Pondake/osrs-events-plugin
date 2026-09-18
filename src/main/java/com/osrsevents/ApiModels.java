package com.osrsevents;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import lombok.AllArgsConstructor;

final class ApiModels
{
	private ApiModels()
	{
	}

	@AllArgsConstructor
	static class Item
	{
		final int id;
		final String name;
		final int quantity;
	}

	static class Context
	{
		String source;
		@SerializedName("npc_id")
		Integer npcId;
		@SerializedName("npc_name")
		String npcName;
		@SerializedName("npc_level")
		Integer npcLevel;
		@SerializedName("kill_count")
		Integer killCount;
		@SerializedName("region_id")
		Integer regionId;
		List<Item> items;
	}

	@AllArgsConstructor
	static class Completion
	{
		@SerializedName("client_event_id")
		final String clientEventId;
		final String kind;
		final String name;
		final int quantity;
		final String rsn;
		@SerializedName("occurred_at")
		final String occurredAt;
		final Context context;
	}

	/** The character this client is signed in as, for POST /identity. */
	@AllArgsConstructor
	static class Identity
	{
		final String rsn;
	}

	static class EventsResponse
	{
		String mode;
		String rsn;
		/** Whether a client has ever reported this account playing `rsn`. */
		boolean proven;
		List<EventInfo> events;
		List<Verdict> reviews;
		List<String> watch;
	}

	static class EventInfo
	{
		List<Object> targets;
	}

	static class Verdict
	{
		String id;
		String label;
		@SerializedName("event_title")
		String eventTitle;
		String status;
	}

	static class CompletionResponse
	{
		boolean duplicate;
		List<Claim> claims;
		/** Counted targets this report moved without claiming: "2 / 5". */
		List<Progress> progress;
	}

	static class Progress
	{
		@SerializedName("event_title")
		String eventTitle;
		String label;
		String name;
		int done;
		@SerializedName("required_count")
		int requiredCount;
	}

	static class Claim
	{
		@SerializedName("event_title")
		String eventTitle;
		String label;
		String name;
		String status;
	}

	static class ErrorResponse
	{
		String message;
	}
}
