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
		/** Whether the server may add this character to the account as an alt. */
		@SerializedName("add_alt")
		final boolean addAlt;
	}

	static class IdentityResponse
	{
		/** Whether the character is on the account after the call. */
		boolean matched;
		/** Whether this call added it as an alt. */
		boolean added;
		/** Why an unknown character was not added: disabled, limit or taken. */
		String reason;
		List<OsrsCharacter> characters;
	}

	/** One OSRS character on the account. The main comes first. */
	static class OsrsCharacter
	{
		String rsn;
		boolean main;
		boolean proven;
	}

	static class EventsResponse
	{
		String mode;
		String rsn;
		/** Whether a client has ever reported this account playing `rsn`. */
		boolean proven;
		/** Every character the account may report from, main first. Null from a server that predates alts. */
		List<OsrsCharacter> characters;
		@SerializedName("max_characters")
		int maxCharacters;
		List<EventInfo> events;
		List<Verdict> reviews;
		List<String> watch;
	}

	static class EventInfo
	{
		String title;
		String url;
		List<Target> targets;
	}

	/** A square or tile the plugin can complete right now. */
	static class Target
	{
		String label;
		String name;
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
