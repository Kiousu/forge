Forge provides an in-game console in adventure mode.

You can access (and close) the console while exploring by pressing F9 (or Fn-F9).
The equivalent method to access the console on mobile is to hold down the character image in the right top of the screen.
Holding the character image again will close the console (as will typing `exit`).

To scroll the console window, click and drag the text box.

## Available commands

| Command Example | Description |
| -- | -- |
| resetMapQuests | Resets the map quests, resulting in all side-quest progress being lost and all side-quest types being re-picked |
| help give | Show help for a command group or specific command |
| whereami | Print current location details (overworld/map, coordinates, biome, nearest POI) |
| playerstats | Print player resources, deck slot, difficulty, flags, and progress counts |
| give gold 1000 | Give 1000 gold |
| give shards 1000 | Give 1000 shards |
| give print lea 232 | Add an alpha (LEA set code) black lotus (232 collector number) |
| give item <item id> | Adds an in game item such as leather boots |
| give set sld | Give 4 copies of every card in the Secret Lair Drop (set code SLD), flagged as having no sell value |
| give nosell card forest | Gives a forest with no sell value |
| give boosters leb | Add a booster from beta (LEB set code) |
| give quest 123 | Add the quest by its number ID |
| give life 10 | Add 10 life to yourself |
| give card forest | Adds a forest to your inventory |
| debug collision | Displays bounding boxes around entities |
| debug map | Enables debug-map mode: clicking/dragging on the minimap moves your player to that location (and enables F2 debug-zone access). |
| debug off | Turns off debugging |
| teleport to 6000 5000 | Moves you 6000 tiles east and 5000 tiles north from the left bottom corner |
| teleport to poi "Plains Capital" | Teleports you to a Point of Interest by name |
| fullHeal | Returns your health back to baseline |
| sprint 100 | Increases your speed for 100 seconds |
| setColorID R | Sets the player color identity; Probably used for testing and shops |
| clearnosell | Clears the no sell value flag from all cards you own that are not used in a deck |
| remove enemy 123 | Remove the enemy from the current map by numeric map ID (in-map only) |
| remove enemy nearest | Remove the nearest enemy |
| remove enemy all | Remove all the enemies from the map |
| dumpEnemyDeckList | Print the enemy deck lists to terminal output stream |
| getShards amount 100 | Similar to give shards command; Gives 100 shards to the player |
| resetQuests | Clears all global (player) quest flags. Can re-lock quest-gated dialog, portals, and POIs; does not clear local map flags, active quests, or events. |
| hide 100 | Enemies do not chase you for 100 seconds |
| fly 100 | You can walk over obstacles for 100 seconds |
| crack | Cracks a random item you are wearing |
| spawn Sliver | Spawn an enemy near the player (overworld only); supports optional amount like `spawn Sliver 3` |
| spawn enemy Sliver | Alias for `spawn` |
| listPOI | Prints all locations in terminal output stream as ID-type pairings |
| leave | Gets you out of the current town/dungeon/cave |
| dumpEnemyColorIdentity | Prints all enemies, their colour affinity and deck name to terminal output |
| heal amount 10 | Recover a fixed amount of health |
| heal percent 0.25 | Recover health by a percentage of max health |
| heal full | Recover your full health |
| reveal cave | Reveal unvisited Point of Interest names for one or more types (for example: cave, dungeon, town, capital, castle) |
| set event dmu Draft | Replace the local inn event with a specific block/edition and format (`Draft` or `Jumpstart`, town with inn required) |
| event reroll | Reroll the current town's local inn event (town with inn required) |
| event reroll Draft dmu | Reroll the local inn event with a chosen format and block/edition |
| dumpEnemyDeckColors | Prints all decks available to enemies and their affinities |
| reset map | Reset the current map (not overworld) after you exit it |
| exit | Close the console |
