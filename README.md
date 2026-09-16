# OSRS Events — RuneLite plugin

Claims bingo squares and board tiles on osrs-events when the game detects the
drop. Early development; not on the Plugin Hub.

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
