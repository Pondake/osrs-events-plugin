package com.osrsevents;

import com.google.inject.Provides;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
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
import java.awt.Color;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
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
	private static final int MAX_CONTEXT_ITEMS = 40;

	/**
	 * No colours of our own. A fixed colour cannot be readable on both an
	 * opaque black chatbox and a transparent one over a bright world, so every
	 * part of a line uses the player's own chat colours, which RuneLite already
	 * keeps in two variants for exactly that reason. Only the accent is ours,
	 * and it is a setting so it can be tuned against a real chatbox.
	 */
	/** Client ticks (20ms) without mouse or keyboard before the refresh backs off. */
	private static final int IDLE_CLIENT_TICKS = 15_000;
	private static final int IDLE_REFRESH_SECONDS = 600;
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

	@Inject
	private ConfigManager configManager;

	private volatile Set<String> watch = Collections.emptySet();
	private final ConcurrentLinkedDeque<Pending> queue = new ConcurrentLinkedDeque<>();
	private final AtomicBoolean sending = new AtomicBoolean();
	private volatile boolean rejected;
	private int ticks;
	private int retryTicks;
	private final Map<String, Integer> killCounts = new HashMap<>();
	private final Map<String, Integer> killCountTicks = new HashMap<>();
	private final Map<String, Integer> activityTicks = new HashMap<>();
	private final Map<String, Integer> activityCounts = new HashMap<>();
	private boolean loginPending = true;
	private final Set<String> seenVerdicts = new HashSet<>();
	private boolean verdictsSeeded;

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
			loginPending = false;
			refreshWatch(config.chatStatus());
		}
	}

	@Override
	protected void shutDown()
	{
		watch = Collections.emptySet();
		queue.clear();
		seenVerdicts.clear();
		killCounts.clear();
		killCountTicks.clear();
		activityTicks.clear();
		activityCounts.clear();
		verdictsSeeded = false;
		rejected = false;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!OsrsEventsConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		if ("checkConnection".equals(event.getKey()))
		{
			if ("true".equals(event.getNewValue()))
			{
				configManager.setConfiguration(OsrsEventsConfig.GROUP, "checkConnection", false);
				rejected = false;
				if (!config.enabled())
				{
					chat("Turn on Send completions first.");
				}
				refreshWatch(true);
			}
			return;
		}

		rejected = false;
		watch = Collections.emptySet();
		refreshWatch(config.chatStatus());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			loginPending = true;
		}
		// Loading screens also end in LOGGED_IN; only the first one after a login announces.
		else if (state == GameState.LOGGED_IN && loginPending)
		{
			loginPending = false;
			refreshWatch(config.chatStatus());
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		ticks++;
		if (ticks >= refreshEveryTicks())
		{
			ticks = 0;
			refreshWatch(false);
		}
		if (++retryTicks >= RETRY_TICKS)
		{
			retryTicks = 0;
			sendNext();
		}
	}

	/** A game tick is 0.6s; an idle player asks far less often. */
	private int refreshEveryTicks()
	{
		boolean idle = client.getMouseIdleTicks() > IDLE_CLIENT_TICKS && client.getKeyboardIdleTicks() > IDLE_CLIENT_TICKS;
		int seconds = idle ? IDLE_REFRESH_SECONDS : Math.min(Math.max(config.refreshSeconds(), 15), IDLE_REFRESH_SECONDS);

		return (int) Math.ceil(seconds / 0.6);
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		NPC npc = event.getNpc();
		ApiModels.Context context = context("npc_kill", npc, event.getItems());

		if (npc != null)
		{
			report("npc_kill", npc.getName(), 1, context);
		}
		reportItems(event.getItems(), context);
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		// NPC loot already arrives through NpcLootReceived; players are never a source.
		if (event.getType() == LootRecordType.NPC || event.getType() == LootRecordType.PLAYER)
		{
			return;
		}
		String source = event.getName();
		ApiModels.Context context = context("loot", null, event.getItems());
		context.npcName = source;
		context.killCount = killCountFor(String.valueOf(source));

		// The source is a claimable thing in its own right, the same way an
		// NPC is. RuneLite names these events after what was done — the loot
		// tracker's "Herbiboar", "Barrows", "Guardians of the Rift" — so a
		// square asking for the activity rather than one of its drops has
		// something to match. A name nothing watches is dropped anyway.
		report("npc_kill", source, 1, context);

		reportItems(event.getItems(), context);
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		String message = event.getMessage().replaceAll("<[^>]*>", "");

		KillCount.Parsed killCount = KillCount.parse(message);
		if (killCount != null)
		{
			killCounts.put(NameMatcher.normalize(killCount.name), killCount.count);
			killCountTicks.put(NameMatcher.normalize(killCount.name), client.getTickCount());

			// The only signal a minigame gives: Tempoross and friends drop no loot on
			// the kill itself, so without this nothing would ever claim their square.
			ApiModels.Context context = context("kill_count", null, null);
			context.npcName = killCount.name;
			context.killCount = killCount.count;
			report("npc_kill", killCount.name, 1, context);
			return;
		}

		if (message.startsWith(COLLECTION_LOG_PREFIX))
		{
			report("item", message.substring(COLLECTION_LOG_PREFIX.length()).trim(), 1, context("collection_log", null, null));
		}
	}

	private void reportItems(Collection<ItemStack> items, ApiModels.Context context)
	{
		Map<String, Integer> byName = new LinkedHashMap<>();
		for (ItemStack item : items)
		{
			String name = itemManager.getItemComposition(item.getId()).getName();
			byName.merge(name, item.getQuantity(), Integer::sum);
		}
		byName.forEach((name, quantity) -> report("item", name, quantity, context));
	}

	/**
	 * What a host can judge a claim by: what died, its level, the killcount,
	 * everything else that dropped with it, and the region. Never chat, other
	 * players or exact coordinates.
	 */
	private ApiModels.Context context(String source, NPC npc, Collection<ItemStack> items)
	{
		ApiModels.Context context = new ApiModels.Context();
		context.source = source;

		if (npc != null)
		{
			context.npcId = npc.getId();
			context.npcName = npc.getName();
			context.npcLevel = npc.getCombatLevel();
			context.killCount = killCountFor(npc.getName());
		}

		Player local = client.getLocalPlayer();
		if (local != null && local.getWorldLocation() != null)
		{
			context.regionId = local.getWorldLocation().getRegionID();
		}

		if (items != null)
		{
			context.items = items.stream()
				.limit(MAX_CONTEXT_ITEMS)
				.map(item -> new ApiModels.Item(item.getId(), itemManager.getItemComposition(item.getId()).getName(), Math.max(item.getQuantity(), 1)))
				.collect(Collectors.toList());
		}

		return context;
	}

	/**
	 * The kill count belonging to this event, or null.
	 *
	 * Only a count from the same tick counts as belonging to it. The message
	 * and the loot arrive together, and an older one is the *previous* kill —
	 * attaching it would hand the server a number it has already counted, and
	 * a square asking for five kills would sit still on the second. RuneLite
	 * expires the same association on the same rule.
	 */
	private Integer killCountFor(String name)
	{
		String key = NameMatcher.normalize(name);

		return Integer.valueOf(client.getTickCount()).equals(killCountTicks.get(key)) ? killCounts.get(key) : null;
	}

	/**
	 * Whether this activity has already been reported for the thing that just
	 * happened. One thing done is one report, and two of them do not walk a
	 * counted square two steps.
	 *
	 * The same activity reaches us by two roads and they do not arrive
	 * together:
	 *
	 * - A Herbiboar harvest raises a loot event and a "Your herbiboar harvest
	 *   count is:" message in the **same tick**.
	 * - A Guardians of the Rift game announces the closed rift when the game
	 *   ends, and the reward chest is searched **minutes later** — same
	 *   activity, far apart, so a tick is no help.
	 *
	 * So a report is suppressed when it repeats the tick, or when it repeats
	 * a kill count already reported for that name. The counter moving is what
	 * says another one was done; a second road to the same number is the same
	 * event arriving twice.
	 *
	 * An activity with no counter at all keeps working: nothing to repeat, so
	 * only the tick rule applies.
	 *
	 * Items are deliberately not covered. Two kills in one tick really are two
	 * drops, and dropping the second would lose a report that counts.
	 */
	private boolean reportedAlready(String name)
	{
		String key = NameMatcher.normalize(name);
		int tick = client.getTickCount();
		Integer count = killCounts.get(key);

		boolean sameTick = Integer.valueOf(tick).equals(activityTicks.get(key));
		boolean sameCount = count != null && count.equals(activityCounts.get(key));

		if (sameTick || sameCount)
		{
			return true;
		}

		activityTicks.put(key, tick);
		activityCounts.put(key, count);

		return false;
	}

	private void report(String kind, String name, int quantity, ApiModels.Context context)
	{
		if (!config.enabled() || rejected || !api.isConfigured() || name == null || !watch.contains(NameMatcher.normalize(name)))
		{
			return;
		}

		if ("npc_kill".equals(kind) && reportedAlready(name))
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
			Instant.now().toString(),
			context
		)));
		sendNext();
	}

	/** With announce, the result goes to chat: this is the connection check. */
	private void refreshWatch(boolean announce)
	{
		if (!config.enabled() || rejected)
		{
			return;
		}

		if (!api.isConfigured())
		{
			if (announce)
			{
				chat("Paste your plugin code and check the Server setting.");
			}
			return;
		}

		api.fetchEvents((status, body) ->
		{
			if (status == 200)
			{
				ApiModels.EventsResponse events = api.parse(body, ApiModels.EventsResponse.class);
				watch = events == null || events.watch == null ? Collections.emptySet() : new HashSet<>(events.watch);
				log.debug("osrs-events watching {} names", watch.size());
				if (events != null)
				{
					announceVerdicts(events.reviews);

					if (announce)
					{
						announceConnection(events);
					}
				}
				return;
			}

			if (status == 401 || status == 404)
			{
				stop(status, body);
			}

			if (announce)
			{
				if (status == 404)
				{
					chat("The plugin is switched off on " + config.serverUrl().trim() + ".");
				}
				else if (status == -1)
				{
					chat("No answer from " + config.serverUrl().trim() + ". Is the server running?");
				}
				else if (status != 401)
				{
					chat("The server answered " + status + ".");
				}
			}
		});
	}

	/** The first list after a start is only remembered: old verdicts are not news. */
	private void announceVerdicts(List<ApiModels.Verdict> reviews)
	{
		if (reviews == null)
		{
			return;
		}

		for (ApiModels.Verdict verdict : reviews)
		{
			if (verdict.id == null || !seenVerdicts.add(verdict.id) || !verdictsSeeded || !config.chatVerdicts())
			{
				continue;
			}

			boolean approved = "APPROVED".equals(verdict.status);

			chat(line()
				.append(config.accent(), verdict.label == null ? "Your claim" : verdict.label)
				.append(ChatColorType.NORMAL).append(" in ")
				.append(config.accent(), String.valueOf(verdict.eventTitle))
				.append(ChatColorType.NORMAL).append(" was ")
				.append(approved ? config.approvedColour() : config.rejectedColour(), approved ? "approved" : "rejected"));
		}

		verdictsSeeded = true;
	}

	private void announceConnection(ApiModels.EventsResponse events)
	{
		long eventCount = events.events == null ? 0 : events.events.stream().filter(e -> e.targets != null && !e.targets.isEmpty()).count();
		clientThread.invokeLater(() ->
		{
			Player local = client.getLocalPlayer();
			String character = local == null ? null : local.getName();

			if (events.rsn == null || events.rsn.isEmpty())
			{
				chat("Connected with " + codeHint() + ", but your account has no OSRS username. Set it on the site first.");
			}
			else if (character != null && !sameRsn(character, events.rsn))
			{
				chat("Connected as " + events.rsn + " with " + codeHint() + ", but you are logged in as " + character + ". Drops from this character will be refused.");
			}
			else if (watch.isEmpty())
			{
				chat("Connected as " + events.rsn + " with " + codeHint() + ". Nothing to watch yet: " + (events.events == null ? 0 : events.events.size()) + " running events, none with an open wiki-linked square or tile.");
			}
			else
			{
				chat("Connected as " + events.rsn + " with " + codeHint() + ". Watching " + watch.size() + " names in " + eventCount + (eventCount == 1 ? " event." : " events."));
			}
		});
	}

	/**
	 * Which code this client is actually using, by its last four characters.
	 *
	 * A code left behind from an earlier account kept working once and cost a
	 * whole test round before anyone noticed, so the check says which one it
	 * connected with. Four characters identify it against the site without
	 * putting the code itself in the chatbox, where a screenshot would carry
	 * it away.
	 */
	private String codeHint()
	{
		String token = config.token().trim();

		return token.length() <= 4 ? "your code" : "code ..." + token.substring(token.length() - 4);
	}

	static boolean sameRsn(String a, String b)
	{
		return rsnKey(a).equals(rsnKey(b));
	}

	private static String rsnKey(String rsn)
	{
		return rsn.replaceAll("[\\s_\\-\\u00A0]+", " ").trim().toLowerCase(Locale.ROOT);
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
				if (response != null && !response.duplicate)
				{
					// A counted square that moved is news even though it
					// claimed nothing: "Zalcano 2 / 5" rather than silence
					// until the fifth kill.
					if (response.progress != null)
					{
						response.progress.forEach(this::announce);
					}

					if (response.claims != null && !response.claims.isEmpty())
					{
						response.claims.forEach(this::announce);
						refreshWatch(false);
					}
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
		if (!config.chatClaims())
		{
			return;
		}

		boolean pending = "PENDING".equals(claim.status);

		ChatMessageBuilder message = line()
			.append(ChatColorType.NORMAL).append("Claimed ")
			.append(config.accent(), claim.label != null ? claim.label : claim.name)
			.append(ChatColorType.NORMAL).append(" in ")
			.append(config.accent(), String.valueOf(claim.eventTitle))
			.append(ChatColorType.NORMAL).append(" - ");

		// Only the verdict carries a colour of its own. A claim that counted
		// and a claim still waiting are the one thing worth telling apart at
		// a glance, and the rest of the line stays in the accent so a green
		// word means "done" and nothing else.
		message.append(
			pending ? config.accent() : config.approvedColour(),
			pending ? "waiting for review" : "approved");

		chat(message);
	}

	private void announce(ApiModels.Progress progress)
	{
		if (!config.chatClaims())
		{
			return;
		}

		chat(line()
			.append(config.accent(), progress.label != null ? progress.label : String.valueOf(progress.name))
			.append(ChatColorType.NORMAL).append(" ")
			.append(config.accent(), progress.done + " / " + progress.requiredCount)
			.append(ChatColorType.NORMAL).append(" in ")
			.append(config.accent(), String.valueOf(progress.eventTitle)));
	}

	/** One builder per line: appending an already-built string escapes its tags. */
	private ChatMessageBuilder line()
	{
		return new ChatMessageBuilder().append(config.accent(), "OSRS Events: ");
	}

	private void chat(String text)
	{
		chat(line().append(ChatColorType.NORMAL).append(text));
	}

	private void chat(ChatMessageBuilder message)
	{
		String formatted = message.build();

		clientThread.invokeLater(() -> chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(formatted)
			.build()));
	}

	@Provides
	OsrsEventsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(OsrsEventsConfig.class);
	}
}
