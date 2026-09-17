package com.osrsevents;

import com.google.inject.Provides;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;

@Slf4j
@PluginDescriptor(
	name = "OSRS Events",
	description = "Completes bingo squares and board tiles on osrs-events.com",
	tags = {"bingo", "clan", "events"}
)
public class OsrsEventsPlugin extends Plugin
{
	private static final String COLLECTION_LOG_PREFIX = "New item added to your collection log:";
	private static final int REFRESH_TICKS = 500;
	private static final int RETRY_TICKS = 10;
	private static final int MAX_ATTEMPTS = 30;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OsrsEventsConfig config;

	@Inject
	private OsrsEventsApi api;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	private volatile Set<String> watch = Collections.emptySet();
	private final ConcurrentLinkedDeque<Pending> queue = new ConcurrentLinkedDeque<>();
	private final AtomicBoolean sending = new AtomicBoolean();
	private volatile boolean rejected;
	private int ticks;

	private static final class Pending
	{
		final ApiModels.Completion completion;
		int attempts;

		Pending(ApiModels.Completion completion)
		{
			this.completion = completion;
		}
	}

	@Override
	protected void startUp()
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			refreshWatch();
		}
	}

	@Override
	protected void shutDown()
	{
		watch = Collections.emptySet();
		queue.clear();
		rejected = false;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!OsrsEventsConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		rejected = false;
		watch = Collections.emptySet();
		refreshWatch();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			refreshWatch();
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		ticks++;
		if (ticks % REFRESH_TICKS == 0)
		{
			refreshWatch();
		}
		if (ticks % RETRY_TICKS == 0)
		{
			sendNext();
		}
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		NPC npc = event.getNpc();
		if (npc != null)
		{
			report("npc_kill", npc.getName(), 1);
		}
		reportItems(event.getItems());
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		// NPC loot already arrives through NpcLootReceived; players are never a source.
		if (event.getType() == LootRecordType.NPC || event.getType() == LootRecordType.PLAYER)
		{
			return;
		}
		reportItems(event.getItems());
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		String message = event.getMessage().replaceAll("<[^>]*>", "");
		if (message.startsWith(COLLECTION_LOG_PREFIX))
		{
			report("item", message.substring(COLLECTION_LOG_PREFIX.length()).trim(), 1);
		}
	}

	private void reportItems(Collection<ItemStack> items)
	{
		Map<String, Integer> byName = new LinkedHashMap<>();
		for (ItemStack item : items)
		{
			String name = itemManager.getItemComposition(item.getId()).getName();
			byName.merge(name, item.getQuantity(), Integer::sum);
		}
		byName.forEach((name, quantity) -> report("item", name, quantity));
	}

	private void report(String kind, String name, int quantity)
	{
		if (!config.enabled() || rejected || !api.isConfigured() || name == null || !watch.contains(NameMatcher.normalize(name)))
		{
			return;
		}

		Player local = client.getLocalPlayer();
		if (local == null || local.getName() == null)
		{
			return;
		}

		queue.add(new Pending(new ApiModels.Completion(
			UUID.randomUUID().toString(),
			kind,
			name,
			Math.max(quantity, 1),
			local.getName(),
			Instant.now().toString()
		)));
		sendNext();
	}

	private void refreshWatch()
	{
		if (!config.enabled() || rejected)
		{
			return;
		}

		api.fetchEvents((status, body) ->
		{
			if (status == 200)
			{
				ApiModels.EventsResponse events = api.parse(body, ApiModels.EventsResponse.class);
				watch = events == null || events.watch == null ? Collections.emptySet() : new HashSet<>(events.watch);
				log.debug("osrs-events watching {} names", watch.size());
			}
			else if (status == 401 || status == 404)
			{
				stop(status, body);
			}
		});
	}

	private void sendNext()
	{
		if (!config.enabled() || rejected || !api.isConfigured() || queue.isEmpty() || !sending.compareAndSet(false, true))
		{
			return;
		}

		Pending pending = queue.poll();
		if (pending == null)
		{
			sending.set(false);
			return;
		}

		api.postCompletion(pending.completion, (status, body) ->
		{
			sending.set(false);

			if (status == 200 || status == 201)
			{
				ApiModels.CompletionResponse response = api.parse(body, ApiModels.CompletionResponse.class);
				if (response != null && !response.duplicate && response.claims != null && !response.claims.isEmpty())
				{
					response.claims.forEach(this::announce);
					refreshWatch();
				}
			}
			else if (status == 401 || status == 404)
			{
				stop(status, body);
			}
			else if (status == 422)
			{
				api.message(body, this::chat);
			}
			else if (++pending.attempts < MAX_ATTEMPTS)
			{
				// No answer, rate limited or a server error: keep the same client_event_id and try again later.
				queue.addFirst(pending);
				return;
			}

			sendNext();
		});
	}

	/** 401: the code is wrong until the config changes. 404: the API is off for now; the periodic refresh finds it again. */
	private void stop(int status, String body)
	{
		watch = Collections.emptySet();
		queue.clear();

		if (status == 401)
		{
			rejected = true;
			api.message(body, this::chat);
		}
	}

	private void announce(ApiModels.Claim claim)
	{
		String state = "PENDING".equals(claim.status) ? " (waiting for review)" : "";
		String label = claim.label != null ? claim.label : claim.name;
		chat("Claimed " + label + " in " + claim.eventTitle + state);
	}

	private void chat(String text)
	{
		clientThread.invokeLater(() -> chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage("OSRS Events: " + text)
			.build()));
	}

	@Provides
	OsrsEventsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(OsrsEventsConfig.class);
	}
}
