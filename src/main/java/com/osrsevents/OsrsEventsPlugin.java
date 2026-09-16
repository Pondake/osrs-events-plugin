package com.osrsevents;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "OSRS Events",
	description = "Completes bingo squares and board tiles on osrs-events.com",
	tags = {"bingo", "clan", "events"}
)
public class OsrsEventsPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private OsrsEventsConfig config;

	@Override
	protected void startUp()
	{
		log.debug("OSRS Events started");
	}

	@Override
	protected void shutDown()
	{
		log.debug("OSRS Events stopped");
	}

	@Provides
	OsrsEventsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(OsrsEventsConfig.class);
	}
}
