# Gameplay Tweaks

A client-side Fabric mod for Minecraft Java 26.3 with optional visual and gameplay convenience features.

## Features

- Disable terrain fog independently in the Overworld, Nether, and End.
- Fullbright lighting.
- Optional Bedrock-style reach-around block placement for bridging without sneaking. Placement still follows the world's and server's rules and permissions.
- Optional spawn-light mesh: a red or green grid on the ground around you that shows where hostile mobs can spawn (red) and where the light keeps them away (green), following the time of day and the torches you place. Its opacity is adjustable (10 to 100 %).
- With Holoprint installed, bridging leaves alone the area in and around a hologram, where only Holoprint's assisted placement puts blocks.

## Settings

Open **Mods > Gameplay Tweaks > Configure** with Mod Menu, or edit `config/gameplaytweaks.properties`. All options are off by default.

Settings from older versions are migrated from `config/visualtweaks.properties` to the new file on first launch after updating. The old file is left intact.

## Online play warning

Use the bridging feature only in singleplayer or on small private servers where everyone involved agrees. Do not use it on large networks or on servers that disallow this kind of modification. Always follow the server's rules. Fog and fullbright are client-side visual options.

## Requirements

Minecraft 26.3, Fabric Loader 0.19.5 or newer, Fabric API, and Java 25. Mod Menu is optional and adds the settings button.

## Build

Run `gradlew.bat jar`. The JAR is written to `build/libs/gameplay-tweaks-26.3.jar`.

---

# Gameplay Tweaks

Mod Fabric para Minecraft Java 26.3, de cliente, con funciones visuales y opciones de comodidad para jugar.

## Funciones

- Desactiva la niebla del terreno por separado en el Overworld, el Nether y el End.
- Iluminación Fullbright.
- Colocación opcional de bloques delante del jugador al hacer bridging, al estilo Bedrock y sin agacharse. La colocación sigue sujeta a las reglas y permisos del mundo o servidor.
- Malla de luz opcional: una cuadrícula roja o verde en el suelo a tu alrededor que muestra dónde pueden aparecer mobs hostiles (roja) y dónde la luz los impide (verde), según la hora del día y las antorchas que pongas. Su opacidad es ajustable (del 10 al 100 %).
- Con Holoprint instalado, el bridging deja en paz la zona dentro y alrededor de un holograma, donde solo la colocación asistida de Holoprint pone bloques.

## Ajustes

Abre **Mods > Gameplay Tweaks > Configure** con Mod Menu o edita `config/gameplaytweaks.properties`. Todas las opciones vienen desactivadas por defecto.

Al iniciar por primera vez tras actualizar, los ajustes de `config/visualtweaks.properties` se migran al nuevo archivo. El archivo antiguo se conserva.

## Aviso para jugar en línea

Usa la función de bridging solo en un mundo individual o en servidores privados pequeños donde todas las personas implicadas estén de acuerdo. No la uses en redes grandes ni en servidores que no permitan este tipo de modificación. Respeta siempre las reglas del servidor. La niebla y Fullbright son opciones visuales del cliente.

## Requisitos

Minecraft 26.3, Fabric Loader 0.19.5 o posterior, Fabric API y Java 25. Mod Menu es opcional y añade el botón de ajustes.

## Compilar

Ejecuta `gradlew.bat jar`. El JAR se genera en `build/libs/gameplay-tweaks-26.3.jar`.
