# Block Tracker
Track tile blocking status of npcs and players. Because all entities share the same blocking grid, this can be modified in various ways. This is what allows stacking of npcs in the first place.

* Entity moves? previous tiles are released, new tiles are blocked
* Entity despawns? previous tiles are released
* Entity spawns? current tiles are blocked
* Entity attempts movement but can't move? current tiles are re-blocked (this specific situation will occur every tick)

# Player Blocking
Sometimes you want to block an npc or prevent potential pathing so knowing a players current blocking status can be beneficial.

https://github.com/user-attachments/assets/34a3d3e1-00cd-472d-9aaf-55b91aadc0fa

# Stuck Tiles
Helps identify situations where npcs will mostly not allow their tiles to be released.

Technically speaking these tiles can be released, but the associated npcs that are stuck will re-block their tiles every game tick;
This being said, npcs with higher priority (lower PID) will still be able to path onto stuck tiles, but all other npcs will be prevented from pathing.

https://github.com/user-attachments/assets/141b724e-edf6-40ab-a413-1f3b936586ba


# Self Stacking

Not exactly useful if alts are available, but a potentially practical use-case of knowing current blocking status

https://github.com/user-attachments/assets/710c6f47-3c4f-4084-9c52-7c80c7c58261


# Alt Stacking examples

# Manual Path
https://github.com/user-attachments/assets/32bbaa45-a3ba-421c-a0f6-258e10b384d2

# Follow Path with a player blocker
https://github.com/user-attachments/assets/ddd9c80d-cbcc-414d-8411-b15a405667a9

# Follow Path with a "stuck" NPC
https://github.com/user-attachments/assets/12255ea9-119a-45ff-b4e6-562eae620754



