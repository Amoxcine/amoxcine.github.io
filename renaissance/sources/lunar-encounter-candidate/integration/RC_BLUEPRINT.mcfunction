# REFERENCE ONLY, NOT PACKAGED. Parent may stage on a fresh disposable Moon test world.
# Execute relative to the configured centre X,Y,Z in ad_astra:moon, AFTER checking claims and empty terrain.
# Replaces ONLY x/z [-32,32], y [-1,3]. Not suitable for an occupied world.
fill ~-32 ~-1 ~-32 ~32 ~-1 ~32 minecraft:polished_basalt
fill ~-32 ~ ~-32 ~32 ~3 ~32 minecraft:air
fill ~-32 ~-1 ~-32 ~32 ~-1 ~-32 minecraft:light_gray_concrete
fill ~-32 ~-1 ~32 ~32 ~-1 ~32 minecraft:light_gray_concrete
fill ~-32 ~-1 ~-32 ~-32 ~-1 ~32 minecraft:light_gray_concrete
fill ~32 ~-1 ~-32 ~32 ~-1 ~32 minecraft:light_gray_concrete
fill ~-2 ~-1 ~-2 ~2 ~-1 ~2 minecraft:cyan_concrete
setblock ~-12 ~ ~ minecraft:copper_block
setblock ~12 ~ ~ minecraft:copper_block
setblock ~ ~ ~12 minecraft:copper_block
# A, west: white floor letter 3x5 north of receiver.
fill ~-13 ~-1 ~-7 ~-13 ~-1 ~-3 minecraft:white_concrete
fill ~-11 ~-1 ~-7 ~-11 ~-1 ~-3 minecraft:white_concrete
setblock ~-12 ~-1 ~-7 minecraft:white_concrete
setblock ~-12 ~-1 ~-5 minecraft:white_concrete
# B, east.
fill ~11 ~-1 ~-7 ~11 ~-1 ~-3 minecraft:white_concrete
fill ~12 ~-1 ~-7 ~13 ~-1 ~-7 minecraft:white_concrete
fill ~12 ~-1 ~-5 ~13 ~-1 ~-5 minecraft:white_concrete
fill ~12 ~-1 ~-3 ~13 ~-1 ~-3 minecraft:white_concrete
setblock ~13 ~-1 ~-6 minecraft:white_concrete
setblock ~13 ~-1 ~-4 minecraft:white_concrete
# C, south.
fill ~-1 ~-1 ~16 ~-1 ~-1 ~20 minecraft:white_concrete
fill ~ ~-1 ~16 ~1 ~-1 ~16 minecraft:white_concrete
fill ~ ~-1 ~20 ~1 ~-1 ~20 minecraft:white_concrete
