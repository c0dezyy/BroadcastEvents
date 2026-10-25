# BroadcastEvents (Paper 1.19+ / Java 17)

## Build
    mvn package
Put `target/BroadcastEvents.jar` in your server's `plugins/` folder.

## Announcements  (/announce, aliases /bc /ann)  perm: broadcastevents.admin
    /announce send <chat|actionbar|bossbar|title> <text>
    /announce add <type> <text>       /announce remove <#>     /announce list
    /announce interval 2m             (fixed)     /announce interval 1m 5m   (random delay)
    /announce mode sequential|random  /announce toggle         /announce reload
Colors: &a codes and &#rrggbb. Title: `Big text||subtitle`. New line in chat: `\n`.

## Events  (/event, alias /ev)
    /event create <name> <bossbar|actionbar|chat|title> <time> [display name]
    /event setspawn <name> [x y z [world]]     (no coords = where you stand)
    /event setkit <name>                       (copies YOUR inventory as the kit)
    /event start <name>     /event stop <name>
    /event settype|settime|setdisplay|setscope <name> <value>
    /event list | info <name> | delete <name> | clearkit <name> | restore <player>
Time: 90s, 10m, 1h30m. Event ends by itself when the countdown reaches 0.

## Players  perm: broadcastevents.join (default true)
    /join [event]   - saves inventory + location, teleports, gives the kit
    /leave          - go back early

## How inventory safety works
* Snapshot is written to `plugins/BroadcastEvents/snapshots/<uuid>.yml` (+ readable `<uuid>.log.txt`)
  and verified BEFORE anything is changed. If it can't be saved/read back, the join is cancelled.
* Files are deleted only after the inventory + location were restored.
* Quit/kick/timeout -> restored immediately. Server stop -> online players restored.
* Crash, or died then disconnected -> file stays; restored on next join/respawn.
* Death in event: drops cleared (kit only), respawn at event spawn with kit (config: respawn-in-event).
* Event ends while a player is dead -> restored on respawn at their original spot.
