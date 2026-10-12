# Tree Chopper

Fabric mod for Minecraft 26.3. Break a log with an axe to fell the connected trunk above the cut and its attached leaf canopy. By default you can fell without sneaking; the settings let you require sneaking instead.

- Works with any item in Minecraft's axe item tag.
- Preserves the stump below the block you cut.
- Follows diagonal branches and supports thick trunks.
- Uses leaf support distances to avoid chopping the neighboring tree's canopy.
- Caps each action at 512 logs and 1,024 leaves.

## Player-built logs are never felled

The mod remembers every log-type block placed by a player or a dispenser (logs, wood, stripped variants, stems, hyphae, nether wart blocks and shroomlight) and never fells them, whether or not sneaking is required. Breaking a log you placed is a normal break, and a log house or tree house touching a natural tree is left standing. Only wood of the same family as the cut log is ever felled, so an oak house next to a spruce is never touched.

On top of that, only real trees are felled: the logs must carry a natural canopy (leaves that decay naturally, not leaves placed by a player; wart blocks or shroomlight for nether trees). Log structures with no canopy, such as cabins from worlds that existed before the mod was installed or village houses, are left alone. Limitation: logs built against a natural tree of the same wood *before* installing the mod cannot be told apart from the tree.

## Settings

Available in Mod Menu (Mods > Tree Chopper > Configure) or in `config/treechopper.properties`. Defaults on a fresh install:

| Setting | Default | Effect |
| --- | --- | --- |
| Tree felling | ON | Master switch; OFF disables the mod entirely. |
| Allow without sneaking | ON | OFF requires sneaking to fell. |
| Nether Warped and Crimson Fungi Felling | ON | OFF leaves nether fungi trees as normal blocks. |
| Automatic sapling placement | OFF | Plants the felled tree's sapling where the trunk stood. |

Saplings are planted two seconds after the felling, so a swing that is still in motion cannot knock them out. Automatic sapling placement only happens when the whole trunk was cut (cutting partway up leaves the stump and plants nothing) and the ground accepts the sapling. It places one sapling for most trees (poplar included), a 2x2 of four for dark oak and pale oak, and four for a mega spruce or mega jungle trunk. Crimson and warped trees plant their fungus and mangroves their propagule. The saplings are placed for free; they are not taken from drops or the inventory.

## Requirements

Minecraft 26.3, Fabric Loader 0.19.5 or later, Fabric API, and Java 25. Mod Menu is optional and adds the settings button.

The server controls tree felling. In dedicated multiplayer, configure `config/treechopper.properties` on the server; changing the client settings screen does not change a remote server's settings.

## Build

Run gradlew.bat jar. The installable JAR is written to build/libs/tree-chopper-26.3.jar.
