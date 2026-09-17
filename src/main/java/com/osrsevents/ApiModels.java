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
	}

	static class EventsResponse
	{
		String mode;
		String rsn;
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
