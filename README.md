# OSRS Events — RuneLite plugin

Claims bingo squares and board tiles on osrs-events when the game detects the
drop. Early development; not on the Plugin Hub.

## What it reports

Only names the server lists under `watch` (`GET /api/plugin/v1/events`), so
nothing leaves the client unless an open square or tile asks for it:

- NPC kills and their drops (`NpcLootReceived`)
- other loot: clue caskets, chests, raids (`LootReceived`)
- new collection log entries (the game's chat message, which needs that
  in-game setting on)

Each report gets a UUID and keeps it through retries, so the server claims it
at most once. Name normalisation lives in `NameMatcher` and must match the
server's; `NameMatcherTest` holds the shared cases.

## Dev loop

```
./gradlew build
./gradlew run
```

`run` starts a real client in developer mode with this plugin loaded. To log in
with a Jagex Account, follow
https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts.
`credentials.properties` logs in without a password: never commit it, and
delete it when you are done.

Point **Server** in the plugin config at a local or staging instance while
testing.
