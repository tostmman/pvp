# PvPCoreParty (addon for PvPCore 1.0.0)

Build:
    unzip PvPCoreParty.zip
    cd PvPCoreParty
    mvn -q package
Output: target/PvPCoreParty-1.0.0.jar  -> put in plugins/ next to PvPCore-1_0_0.jar
(PvPCore-1_0_0.jar is already bundled under libs/.)

## Commands
/party create
/party invite <player>   (/party add, /p invite)   - expires after invite-timeout-seconds (default 60s)
/party accept | deny
/party leave             - opens a confirm menu (click the cake). "/party leave confirm" skips it.
/party kick <player> | promote <player>
/party disband           - opens a confirm menu (click the cake). "/party disband confirm" skips it.
/party list | /party chat <msg> (or /pc <msg>) | /party menu
/party battle             - opens the gamemode select menu (Team Battle / Party FFA)
/party teambattle [kit]   (alias /party tb)  - split your own party red vs blue and fight, skipping the menu
/party ffa [kit]          - everyone in your own party fights, last one standing wins, skipping the menu

## Party item (hotbar slot 2)
Whenever a player is in the PvPCore lobby, slot 2 holds a party item next to PvPCore's own queue item:
- Not in a party, or a regular member -> a GOAT HORN (right-click opens the party menu: create/join/leave/members).
- The party LEADER -> a LECTERN instead. Only the leader ever receives it, and it is the only way to reach the
  full leader menu (invite, members, Party Battle, disband). Swaps automatically the moment someone
  becomes/stops being a leader. Neither item can be dropped, moved to another slot, or used for anything else,
  and both disappear once the player enters a duel or a party battle.

## Party rules
- Leaving a party or disbanding always goes through a confirm menu with a CAKE the player must click.
- Party LEADER leaves/disconnects -> the whole party is disbanded. Normal MEMBER leaves/is kicked/disconnects
  -> only that member is removed, the party continues.
- While in a party you cannot queue (PvPCore's queue item, its GUI, and /queue are all blocked).
- You cannot accept an invite or join another party while already in one, or while in a match.

## Party Battle - gamemode select (like mcpvp.club's)
The leader opens /party battle (or the Lectern -> Party Battle) and picks a mode. Both modes use ONLY the
leader's own party - there is no more "challenge another party"; everyone fights their own party members:
- **Team Battle**: your party is split into RED and BLUE (shuffled, as even as possible). Single round - the
  first team fully eliminated loses. No friendly fire between teammates. Needs gamemodes.team.min-players
  (default 2, so at least 1v1).
- **Party FFA**: every member of your party fights everyone else, no teams. Single round, last player
  standing wins. Needs gamemodes.ffa.min-players (default 2).

Both modes:
  - use PvPCore's own arenas (arenaManager) and kits (kitManager, including each player's personal kit layout),
  - show a dedicated sidebar SCOREBOARD for the match (kit name, RED/BLUE remaining for Team Battle, or
    "Remaining: x/y" for FFA), restored to the normal scoreboard once the match ends,
  - play sounds: a door-closing sound when the match locks everyone in, a tick each countdown second, a
    level-up style sound the instant the fight opens, and a chime when the match ends,
  - show a title/subtitle at the start and the end (winner announcement),
  - regenerate the arena through PvPCore's own regenArenaAsync() once the match ends - exactly like a normal
    PvPCore duel does, instead of a custom implementation.
  - Kit list is configurable: config.yml's gamemodes.team.kits / gamemodes.ffa.kits - leave empty to show every
    PvPCore kit (same list the PvPCore queue menu uses), or list specific kit ids to restrict that menu.

NOTE / limitation: PvPCore's Arena class only exposes two fixed spawn points (red/blue). Team Battle uses them
directly for its two teams. Party FFA (which can have more than 2 players) spreads players in a small circle
around the midpoint of those two spawns, since there is no "FFA spawn ring" in PvPCore's arena data - this is a
best-effort stand-in, not a real PvPCore feature; test it with your actual arenas and tell me if spawns end up
too close/clipped. I also could not find an actual "door" block/API on PvPCore's compiled classes to copy (no
source, .class files only) - instead, players are frozen in place during the countdown (same effect: you can't
leave your spawn until the fight starts) and a door-closing SOUND plays the moment the match locks in, to give
that "gate is sealed" feeling without an invented API call that might not compile.

## Server-wide match rules (PvPCore 1v1 duels AND party battles, both modes)
- During the countdown, between rounds, and after a match ends: no using/throwing/dropping/placing/breaking/
  eating anything.
- Ender pearls only exist while their owner is actually fighting: they vanish if the owner disconnects (the
  match ends the same way it normally would), when the round/match ends, and any pearl that survived a server
  restart is removed right after the server finishes starting.

Config: plugins/PvPCoreParty/config.yml
    prefix, max-party-size, invite-timeout-seconds, default-kit,
    gamemodes.team.kits, gamemodes.team.min-players, gamemodes.ffa.kits, gamemodes.ffa.min-players


## The "door" (gate)
PvPCore has its own elaborate gate (prefight.PreFightSkipSequence): animated fence-gate panels players
can hit to ready up early, built on packetevents and tied directly to PvPCore's own DuelMatch class. I
could not reuse that exact class - no source code is available (only compiled .class files), its
constructor requires a PvPCore DuelMatch specifically (not a Team/FFA match), and it needs packetevents
as a dependency. What's included instead is a from-scratch stand-in using only vanilla Paper entities
(BlockDisplay): a wall appears in front of every spawn the moment the countdown starts, and rises up
with a sound and disappears the instant FIGHT! hits - same idea, simpler animation, no "hit to ready up
early" part. It's purely cosmetic; movement is already locked during the countdown regardless.
Configurable in config.yml under "door": enabled, material, width, height.
