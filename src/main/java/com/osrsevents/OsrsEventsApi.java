package com.osrsevents;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** The osrs-events plugin API. Every call is enqueued; callbacks run on the OkHttp pool. */
@Slf4j
@Singleton
class OsrsEventsApi
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	/** status is -1 when the request never got an answer. */
	interface Result
	{
		void done(int status, String body);
	}

	@Inject
	private OkHttpClient http;

	@Inject
	private Gson gson;

	@Inject
	private OsrsEventsConfig config;

	/** The plugin code goes in a header, so it only ever travels over https. Anything else is not a server. */
	static HttpUrl baseUrl(String serverUrl)
	{
		HttpUrl url = HttpUrl.parse(serverUrl.trim());

		return url != null && url.isHttps() ? url : null;
	}

	boolean isConfigured()
	{
		return !config.token().trim().isEmpty() && baseUrl(config.serverUrl()) != null;
	}

	void fetchEvents(Result result)
	{
		Request request = request("events");
		if (request == null)
		{
			return;
		}
		send(request.newBuilder().get().build(), result);
	}

	void postCompletion(ApiModels.Completion completion, Result result)
	{
		Request request = request("completions");
		if (request == null)
		{
			result.done(-1, null);
			return;
		}
		send(request.newBuilder().post(RequestBody.create(JSON, gson.toJson(completion))).build(), result);
	}

	void postIdentity(ApiModels.Identity identity, Result result)
	{
		Request request = request("identity");
		if (request == null)
		{
			result.done(-1, null);
			return;
		}
		send(request.newBuilder().post(RequestBody.create(JSON, gson.toJson(identity))).build(), result);
	}

	<T> T parse(String body, Class<T> type)
	{
		try
		{
			return gson.fromJson(body, type);
		}
		catch (JsonParseException e)
		{
			log.debug("Unreadable response from osrs-events", e);
			return null;
		}
	}

	void message(String body, Consumer<String> onMessage)
	{
		ApiModels.ErrorResponse error = parse(body, ApiModels.ErrorResponse.class);
		if (error != null && error.message != null)
		{
			onMessage.accept(error.message);
		}
	}

	private Request request(String path)
	{
		String token = config.token().trim();
		HttpUrl base = baseUrl(config.serverUrl());
		if (token.isEmpty() || base == null)
		{
			return null;
		}

		HttpUrl url = base.newBuilder()
			.addPathSegments("api/plugin/v1")
			.addPathSegment(path)
			.build();

		return new Request.Builder()
			.url(url)
			.header("Authorization", "Bearer " + token)
			.header("Accept", "application/json")
			.build();
	}

	private void send(Request request, Result result)
	{
		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("osrs-events request failed", e);
				result.done(-1, null);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					result.done(response.code(), body == null ? null : body.string());
				}
				catch (IOException e)
				{
					log.debug("osrs-events response unreadable", e);
					result.done(-1, null);
				}
			}
		});
	}
}
