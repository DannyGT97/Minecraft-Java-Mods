# Tree Chopper

Fabric mod for Minecraft 26.3. While sneaking and using an axe, break a log to fell the connected trunk above the cut and its attached leaf canopy.

- Works with any item in Minecraft's axe item tag.
- Preserves the stump below the block you cut.
- Follows diagonal branches and supports thick trunks.
- Uses leaf support distances to avoid chopping the neighboring tree's canopy.
- Caps each action at 512 logs and 1,024 leaves.

## Requirements

Minecraft 26.3, Fabric Loader 0.19.5 or later, Fabric API, and Java 25. Install this mod on both the client and server in multiplayer.

## Build

Run gradlew.bat jar. The installable JAR is written to build/libs/tree-chopper-26.3.jar.
