# NBT-only probe, runs from #minecraft:load.
#
# Three things to know before editing this file:
#  1. Never add 'tconstruct modifiers ... add <datapack modifier>' here. ModifierArgument resolves
#     during function parse, which happens before ModifierManager#apply, and the resulting
#     exception kills the whole function. Use rcon for runtime modifier commands.
#  2. Command output inside a function is suppressed, so 'data get' prints nothing to the log.
#     Assertions have to go through 'say'.
#  3. 'say' parses its text as a component, so any selector written in it is resolved to an entity
#     name and @@ escaping does not prevent that. Keep selectors out of say text.
kill @e[tag=arcana_probe]
summon minecraft:zombie 0.5 100.5 0.5 {Tags:["arcana_probe"],PersistenceRequired:1b,NoAI:1b,Silent:1b,Invulnerable:1b,HandItems:[{id:"tconstruct:pickaxe",count:1,components:{"minecraft:custom_data":{tic_materials:["arcana:crystalsong","arcana:manawoven","arcana:manawoven"]}}},{}]}
execute if data entity @e[tag=arcana_probe,limit=1] HandItems[0].components."minecraft:custom_data".tic_materials run say [arcana-selftest] PASS - probe holds a pickaxe carrying the three arcana materials
execute unless data entity @e[tag=arcana_probe,limit=1] HandItems[0].components."minecraft:custom_data".tic_materials run say [arcana-selftest] FAIL - probe lost its material data
say [arcana-selftest] read the values back over rcon: data get entity <probe> HandItems[0].components."minecraft:custom_data"
