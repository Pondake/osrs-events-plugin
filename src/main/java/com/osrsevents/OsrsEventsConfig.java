package com.osrsevents;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(OsrsEventsConfig.GROUP)
public interface OsrsEventsConfig extends Config
{
	String GROUP = "osrs-events";

	@ConfigSection(
		name = "Account",
		description = "Which account on osrs-events this client claims for",
		position = 10
	)
	String ACCOUNT = "account";

	@ConfigSection(
		name = "Chat",
		description = "What the plugin says in game, and in which colour",
		position = 20
	)
	String CHAT = "chat";

	@ConfigSection(
		name = "Advanced",
		description = "Rarely worth changing",
		position = 30,
		closedByDefault = true
	)
	String ADVANCED = "advanced";

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
		section = ACCOUNT,
		position = 11
	)
	default String token()
	{
		return "";
	}

	@ConfigItem(
		keyName = "checkConnection",
		name = "Check connection",
		description = "Tick to test the connection now. The result appears in chat and the box unticks itself.",
		section = ACCOUNT,
		position = 12
	)
	default boolean checkConnection()
	{
		return false;
	}

	@ConfigItem(
		keyName = "chatStatus",
		name = "Connection",
		description = "Say in chat whether the plugin is connected, and what is wrong when it is not",
		section = CHAT,
		position = 21
	)
	default boolean chatStatus()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chatClaims",
		name = "Claims",
		description = "Say in chat when a drop or kill claims a square or tile",
		section = CHAT,
		position = 22
	)
	default boolean chatClaims()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chatVerdicts",
		name = "Approvals",
		description = "Say in chat when a host approves or rejects one of your claims",
		section = CHAT,
		position = 23
	)
	default boolean chatVerdicts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "accent",
		name = "Accent colour",
		description = "Colour for the OSRS Events prefix and the names it mentions. The rest follows your own chat colours.",
		section = CHAT,
		position = 24
	)
	default Color accent()
	{
		return new Color(0xC86414);
	}

	@ConfigItem(
		keyName = "approvedColour",
		name = "Approved colour",
		description = "Colour for a claim that counted. Only the verdict uses it, so an approval is readable at a glance.",
		section = CHAT,
		position = 25
	)
	default Color approvedColour()
	{
		return new Color(0x2E8B2E);
	}

	@ConfigItem(
		keyName = "rejectedColour",
		name = "Rejected colour",
		description = "Colour for a claim a host turned down",
		section = CHAT,
		position = 26
	)
	default Color rejectedColour()
	{
		return new Color(0xB83232);
	}

	@ConfigItem(
		keyName = "serverUrl",
		name = "Server",
		description = "Only change this when testing against another environment",
		section = ADVANCED,
		position = 31
	)
	default String serverUrl()
	{
		return "https://osrs-events.com";
	}

	@ConfigItem(
		keyName = "refreshSeconds",
		name = "Refresh every (seconds)",
		description = "How often to ask the site what to watch and whether a claim was reviewed. While you sit idle this backs off to ten minutes.",
		section = ADVANCED,
		position = 32
	)
	@Range(min = 15, max = 600)
	default int refreshSeconds()
	{
		return 60;
	}
}
