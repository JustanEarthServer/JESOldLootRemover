# JESOldLootRemover

Removes chests, barrels, shulker boxes and other containers that were placed before the season cutoff date, along with everything inside them.

A container is checked the first time it is opened, broken, emptied by a hopper, fired by a dispenser or hit by an explosion. If CoreProtect has no placement record for it after the cutoff, it gets deleted. Containers that pass are tagged and never checked again, and newly placed containers are tagged right away.

## Requirements
- Paper 26.2
- CoreProtect (API v10+)
- WorldGuard (optional, for `ignored-regions`)

## Config
```yaml
cutoff-date: "2026-11-01"
ignored-regions: [spawn, shop, casino, donators, pvparena]
ignored-worlds: []
```

## Permissions
- `oldlootremover.notify`: get alerts with coordinates when a container is removed
- `oldlootremover.admin`: `/olr reload`

If the CoreProtect database is unreachable, nothing is removed.
