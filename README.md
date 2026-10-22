# Minecraft Java Mods by DannyGT97

A collection of Fabric mods for Minecraft Java. Each project is maintained in its own folder and can be installed independently; you do not need the whole collection.

## Online play warning

Some mods in this collection change gameplay mechanics or block placement. Use those mods only in singleplayer or on small private servers where everyone involved agrees. Do not use them on large networks or on servers that prohibit these kinds of modifications. Always follow the server's rules. Dynamic Lights is visual-only and does not change the world or send gameplay changes to a server.

## Projects

### [Dynamic Lights](dynamic-lights/README.md)

Adds dynamic visual lighting: light-emitting items illuminate the surroundings while held or dropped, and selected entities and special effects also emit light. This includes burning entities, glow squids, blazes, fireballs, lit TNT, and certain attacks.

- **Type:** client-side only. It places no blocks and sends no changes to the server.
- **Settings:** configure the player's lights, other entities, held or dropped light sources, and special effects through Mod Menu or `config/dynamiclights.json`.
- **Play:** works in singleplayer and while connected to servers.

### [Hastemine](hastemine/README.md)

Changes the mining speed of selected blocks so deepslate can behave more like stone, and gives Overworld logs Bedrock-style Haste scaling. It can also enable a similar effect for cobblestone. Formerly Hastestone.

- **Features:** instant deepslate mining with Haste II; deepslate mined at stone speed with any pickaxe; instant cobblestone mining with Haste II; Bedrock-style Haste scaling for Overworld logs, so Haste II and a good axe break them at once.
- **Settings:** each behavior can be enabled separately through Mod Menu or `config/hastemine.properties`.
- **Type:** changes mining mechanics. The server controls mining results in multiplayer; compatible settings are needed on the client and server.
- **Online play:** follow the warning above and the server's rules.

### [Holoprint](holoprint/README.md)

A work-in-progress tool for loading and editing `.litematic` schematics through an in-world hologram. It includes a free camera for moving the view and positioning or transforming the hologram without moving the player.

- **Available features:** read and write `.litematic` files, render a hologram, and move, rotate, or mirror it with the free camera.
- **In development:** selection, copy and paste, block placement with the correct block states, and automatic filling. Controller support still needs testing.
- **Type:** client-side.
- **Provisional usage:** place a `.litematic` file in the game's `schematics` folder and run `/holoprint load <file>`. See its README for the current controls.

### [Inventory Filters](inventory-filters/README.md)

Adds controls for organizing the player's inventory and the contents of chests and shulker boxes.

- **Sorting:** by type, name, category, or rarity, with optional reverse order and compatible stack consolidation.
- **Transfers:** buttons to stack compatible items or move items between a container and the player's inventory. You can choose whether the hotbar is included.
- **Hotbar presets:** save up to three layouts, preview them, and load them. Matching can ignore enchantments or require the same enchantments; durability does not affect a match. Missing preset items leave their slots empty. Items displaced from the current hotbar are stored in the inventory; loading is cancelled if there is not enough room, so items are not lost.
- **Block Auto Refill:** when a block stack in the main or off hand runs out, it can be replenished with compatible blocks from the inventory.
- **Type:** inventory actions are handled by the server. In multiplayer, install the mod on both the client and server.

### [Tree Chopper](tree-chopper/README.md)

When an axe breaks a log from a natural tree, the mod fells the connected trunk above it and the attached canopy. It preserves the stump and limits how many blocks are processed per action.

- Recognizes natural trees and avoids felling logs placed by players or dispensers.
- Settings control whether sneaking is required, whether Nether fungi trees are felled, and whether a sapling is planted automatically.
- **Type:** the server controls felling. On dedicated servers, the relevant settings are in the server's `config/treechopper.properties`.
- **Online play:** follow the warning above and the server's rules.

### [Gameplay Tweaks](gameplay-tweaks/README.md)

Provides visual options and gameplay convenience features for the client.

- **Fog:** disable fog separately in the Overworld, Nether, and End.
- **Fullbright:** increase image brightness.
- **Bedrock-style bridging:** helps place blocks in front of the player while building at an edge, without sneaking.
- **Type:** client-side. Placement remains subject to the world's and server's rules and permissions.
- **Online play:** follow the warning above for bridging and respect the server's rules.

## Requirements and installation

The projects target **Minecraft Java 26.3**, **Fabric Loader 0.19.5 or newer**, **Fabric API**, and **Java 25**. Mod Menu is optional and adds access to settings for mods that support it.

1. Download the mod's JAR from that project's `build/libs` folder, or build it using that project's README.
2. Put the JAR in the `mods` folder of a Fabric installation.
3. Install Fabric API. Install Mod Menu as well if you want to open settings from the mods menu.
4. For mods that perform inventory actions or change server gameplay, install them on the server as indicated in that project's section and README.

Each project folder contains more detailed, project-specific instructions. Build each mod separately with `gradlew.bat jar` (Holoprint documents `gradlew.bat build`).

---

# Mods de Minecraft Java de DannyGT97

Colección de mods Fabric para Minecraft Java. Cada proyecto se mantiene en su propia carpeta y puede instalarse por separado; no hace falta instalar toda la colección.

## Aviso para jugar en línea

Algunos mods de esta colección cambian mecánicas del juego o la colocación de bloques. Usa esos mods solo en un mundo individual o en servidores privados pequeños donde todas las personas implicadas estén de acuerdo. No los uses en redes grandes ni en servidores que prohíban este tipo de modificaciones. Respeta siempre las reglas del servidor. Dynamic Lights es solo visual: no cambia el mundo ni envía cambios de juego al servidor.

## Proyectos

### [Dynamic Lights](dynamic-lights/README.md)

Añade iluminación dinámica visual: los objetos luminosos iluminan el entorno cuando están en la mano o en el suelo, y algunas entidades y efectos especiales también emiten luz. Incluye entidades en llamas, calamares luminosos, blazes, bolas de fuego, TNT encendida y ciertos ataques.

- **Tipo:** solo cliente. No coloca bloques ni envía cambios al servidor.
- **Ajustes:** configura las luces del jugador y otras entidades, los objetos luminosos en la mano o en el suelo y varios efectos especiales desde Mod Menu o `config/dynamiclights.json`.
- **Uso:** funciona en mundos individuales y al conectarse a servidores.

### [Hastemine](hastemine/README.md)

Cambia la velocidad de minado de algunos bloques para que la deepslate se comporte más como la piedra y da a los troncos del Overworld el escalado de Prisa de Bedrock. También permite activar un efecto similar para la cobblestone. Antes se llamaba Hastestone.

- **Funciones:** deepslate instantánea con Prisa II; deepslate a velocidad de piedra con cualquier pico; cobblestone instantánea con Prisa II; escalado de Prisa de Bedrock en los troncos del Overworld, de modo que Prisa II y un buen hacha los rompen de golpe.
- **Ajustes:** cada comportamiento se puede activar por separado desde Mod Menu o `config/hastemine.properties`.
- **Tipo:** cambia la mecánica de minado. El servidor controla los resultados en multijugador; el cliente y el servidor necesitan ajustes compatibles.
- **Juego en línea:** sigue el aviso anterior y las reglas del servidor.

### [Holoprint](holoprint/README.md)

Herramienta en desarrollo para cargar y editar esquemáticos `.litematic` mediante un holograma dentro del mundo. Incluye una cámara libre para mover la vista y colocar o transformar el holograma sin mover al personaje.

- **Funciones disponibles:** leer y guardar archivos `.litematic`, mostrar el holograma y moverlo, rotarlo o espejarlo con la cámara libre.
- **En desarrollo:** selección, copiar y pegar, colocación de bloques con sus estados y relleno automático. El soporte de mando aún necesita pruebas.
- **Tipo:** solo cliente.
- **Uso provisional:** coloca un archivo `.litematic` en la carpeta `schematics` y ejecuta `/holoprint load <archivo>`. Consulta su README para ver los controles actuales.

### [Inventory Filters](inventory-filters/README.md)

Añade controles para organizar el inventario del jugador y los contenidos de cofres y shulkers.

- **Ordenación:** por tipo, nombre, categoría o rareza, con orden inverso opcional y compactación de stacks compatibles.
- **Transferencias:** botones para apilar objetos compatibles o mover objetos entre un contenedor y el inventario del jugador. Se puede elegir si se incluye la hotbar.
- **Presets de hotbar:** guarda hasta tres distribuciones, muéstralas en una vista previa y cárgalas. La coincidencia puede ignorar los encantamientos o exigir los mismos; la durabilidad no afecta a la coincidencia. Si falta un objeto del preset, su espacio queda vacío. Los objetos desplazados de la hotbar se guardan en el inventario; la carga se cancela si no hay espacio suficiente para evitar que se pierdan.
- **Relleno automático de bloques:** al agotarse un stack de bloques en la mano principal o secundaria, puede reponerse con bloques compatibles del inventario.
- **Tipo:** el servidor procesa las acciones de inventario. En multijugador, instala el mod tanto en el cliente como en el servidor.

### [Tree Chopper](tree-chopper/README.md)

Al romper con un hacha un tronco de un árbol natural, corta el resto del tronco conectado y su copa. Conserva el tocón y limita la cantidad de bloques procesados en cada acción.

- Reconoce árboles naturales y evita talar troncos colocados por jugadores o dispensadores.
- Los ajustes permiten elegir si hay que agacharse, si se talan hongos del Nether y si se planta un retoño automáticamente.
- **Tipo:** el servidor controla la tala. En servidores dedicados, los ajustes relevantes están en `config/treechopper.properties` del servidor.
- **Juego en línea:** sigue el aviso anterior y las reglas del servidor.

### [Gameplay Tweaks](gameplay-tweaks/README.md)

Ofrece ajustes visuales y funciones de comodidad para el cliente.

- **Niebla:** permite desactivarla por separado en el Overworld, el Nether y el End.
- **Fullbright:** aumenta el brillo de la imagen.
- **Bridging al estilo Bedrock:** ayuda a colocar bloques delante del jugador al construir sobre un borde, sin tener que agacharse.
- **Tipo:** solo cliente. La colocación sigue sujeta a las reglas y permisos del mundo o servidor.
- **Juego en línea:** para bridging, sigue el aviso anterior y respeta las reglas del servidor.

## Requisitos e instalación

Los proyectos están preparados para **Minecraft Java 26.3**, **Fabric Loader 0.19.5 o posterior**, **Fabric API** y **Java 25**. Mod Menu es opcional y añade acceso a los ajustes de los mods que lo integran.

1. Descarga el JAR del mod que quieras usar desde la carpeta `build/libs` de su proyecto, o compílalo siguiendo el README de ese proyecto.
2. Coloca el JAR en la carpeta `mods` de una instalación con Fabric.
3. Instala Fabric API. Instala también Mod Menu si quieres abrir los ajustes desde el menú de mods.
4. Para los mods que realizan acciones de inventario o cambian mecánicas del servidor, instálalos en el servidor según se indique en la sección y el README de cada proyecto.

Cada carpeta contiene instrucciones más detalladas y específicas. Los mods se compilan por separado con `gradlew.bat jar` (Holoprint documenta `gradlew.bat build`).
