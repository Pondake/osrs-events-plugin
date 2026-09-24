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
		/** Running races the account is in. Null from a server that predates them. */
		List<Race> races;
		/** Null from a server that predates it. */
		@SerializedName("other_events")
		List<OtherEvent> otherEvents;
		List<Verdict> reviews;
		List<String> watch;
	}

	static class EventInfo
	{
		String id;
		String title;
		/** BINGO or SNAKES_LADDERS. */
		String type;
		String url;
		List<Target> targets;
		/** The account's (or its team's) finish, null when it has not finished. */
		Finish finish;
	}

	/** A place on an event's podium. */
	static class Finish
	{
		int place;
		/** Claims ahead of it are still in review, so the place can still change. */
		boolean provisional;
		/** The team that finished, null for a solo event. */
		String team;
	}

	/** A finish a report just caused, from POST /completions. */
	static class FinishNews extends Finish
	{
		@SerializedName("event_id")
		String eventId;
		@SerializedName("event_title")
		String eventTitle;
	}

	/** An event the account plays that is not running: upcoming, paused, or ended in the last week. */
	static class OtherEvent
	{
		String id;
		String title;
		/** BINGO, SNAKES_LADDERS, DROP_RACE or SKILL_RACE. */
		String type;
		String url;
		/** upcoming, paused or ended. */
		String status;
		@SerializedName("starts_at")
		String startsAt;
		@SerializedName("ends_at")
		String endsAt;
		/** Bingo and snakes & ladders only. */
		Finish finish;
		/** Races only. */
		Integer rank;
		Integer entrants;

		boolean isRace()
		{
			return "DROP_RACE".equals(type) || "SKILL_RACE".equals(type);
		}
	}

	/** A drop or skill race and where the account stands in it: its best character, as the race page shows. */
	static class Race
	{
		String id;
		String title;
		/** DROP_RACE or SKILL_RACE. */
		String type;
		String url;
		/** The boss or skill, as a label. */
		String metric;
		/** kills or xp. */
		String unit;
		/** Null while nothing has been measured. */
		Integer rank;
		int entrants;
		long gained;
		long live;
		/** Gained of rank 1; null when nobody is ranked. */
		Long leader;
		@SerializedName("ends_at")
		String endsAt;
	}

	/** A square or tile the plugin can complete right now. */
	static class Target
	{
		/** bingo_square, or a board tile. */
		String kind;
		int position;
		String label;
		String name;
		@SerializedName("min_quantity")
		int minQuantity;
		@SerializedName("required_count")
		int requiredCount;
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
		/** Events this report made the account (or its team) finish. */
		List<FinishNews> finishes;
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
