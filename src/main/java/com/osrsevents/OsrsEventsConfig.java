package com.osrsevents;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(OsrsEventsConfig.GROUP)
public interface OsrsEventsConfig extends Config
{
	String GROUP = "osrs-events";

	@ConfigItem(
		keyName = "enabled",
		name = "Send completions",
		description = "Report detected drops to osrs-events so matching squares and tiles get claimed",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		position = 0
	)
	default boolean enabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "token",
		name = "Plugin code",
		description = "The code from your settings page on osrs-events",
		secret = true,
		position = 1
	)
	default String token()
	{
		return "";
	}

	@ConfigItem(
		keyName = "serverUrl",
		name = "Server",
		description = "Only change this when testing against another environment",
		position = 2
	)
	default String serverUrl()
	{
		return "https://osrs-events.com";
	}

	@ConfigItem(
		keyName = "checkConnection",
		name = "Check connection",
		description = "Tick to test the connection now. The result appears in chat and the box unticks itself.",
		position = 3
	)
	default boolean checkConnection()
	{
		return false;
	}

	@ConfigItem(
		keyName = "chatStatus",
		name = "Chat: connection",
		description = "Say in chat whether the plugin is connected, and what is wrong when it is not",
		position = 4
	)
	default boolean chatStatus()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chatClaims",
		name = "Chat: claims",
		description = "Say in chat when a drop or kill claims a square or tile",
		position = 5
	)
	default boolean chatClaims()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chatVerdicts",
		name = "Chat: approvals",
		description = "Say in chat when a host approves or rejects one of your claims",
		position = 6
	)
	default boolean chatVerdicts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "refreshSeconds",
		name = "Refresh every (seconds)",
		description = "How often to ask the site what to watch and whether a claim was reviewed. While you sit idle this backs off to ten minutes.",
		position = 7
	)
	@Range(min = 15, max = 600)
	default int refreshSeconds()
	{
		return 60;
	}

	@ConfigItem(
		keyName = "accent",
		name = "Chat accent",
		description = "Colour for the OSRS Events prefix and the names it mentions. The rest follows your own chat colours.",
		position = 8
	)
	default Color accent()
	{
		return new Color(0xFF981F);
	}
}
