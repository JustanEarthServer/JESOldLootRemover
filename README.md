# JESOldLootRemover

Removes chests, barrels, shulker boxes and other containers that were placed before the season cutoff date, along with everything inside them. Also limits netherite and gold from structure loot.

A container is checked the first time it is opened, broken, emptied by a hopper, fired by a dispenser or hit by an explosion. If CoreProtect has no placement record for it after the cutoff, it gets deleted. Containers that pass are tagged and never checked again. Newly placed containers, containers in newly generated chunks and unopened structure chests are tagged right away.

Structure loot (bastions, ruined portals, fortresses...) is limited when it is generated, so breaking or hoppering the chest gives the same result as opening it. See `loot` in config.yml.

## Requirements
- Paper 26.2
- CoreProtect (API v10+)
- WorldGuard (optional, for `ignored-regions`)

## Commands
- `/olr reload`: reload config.yml
- `/olr debug`: toggle debug logging to console

## Permissions
- `oldlootremover.notify`: get alerts with coordinates when a container is removed
- `oldlootremover.admin`: `/olr reload` and `/olr debug`

If the CoreProtect database is unreachable, nothing is removed.
