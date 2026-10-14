# Hastestone

Mod Fabric para Minecraft 26.3 que hace que minar deepslate (y, si quieres, cobblestone) con Prisa II sea tan instantáneo como minar piedra, y que permite minar la deepslate a la velocidad de la piedra con cualquier pico.

- **Deepslate instantánea con Prisa II** (activada por defecto): con Prisa II (un beacon, una poción...), la deepslate se rompe de golpe siempre que la piedra lo haría con el mismo pico y los mismos encantamientos.
- **Deepslate a velocidad de piedra** (desactivada por defecto): la deepslate tarda exactamente lo mismo que la piedra con la herramienta que lleves, con o sin beacon. Cada pico (madera, piedra, hierro, diamante, netherite) conserva su propio tiempo de piedra.
- **Cobblestone instantánea con Prisa II** (desactivada por defecto): igual que en Bedrock Edition, la cobblestone se rompe de golpe con Prisa II siempre que la piedra lo haría. En Java la cobblestone es más dura que la piedra y no se rompe al instante.
- Interruptor general para desactivar el mod: con él apagado, el minado es el de vanilla.

Solo se tocan la deepslate y la cobblestone. Prisa I no basta para la opción instantánea, y las herramientas que no son picos no ganan nada.

## Ajustes

Disponibles en Mod Menu (Mods > Hastestone > Configure) o en `config/hastestone.properties`.

| Ajuste | Por defecto |
| --- | --- |
| Hastestone (interruptor general) | ON |
| Deepslate instantánea con Prisa II | ON |
| Deepslate a velocidad de piedra | OFF |
| Cobblestone instantánea con Prisa II | OFF |

El cálculo del minado lo hacen tanto el cliente (para predecir) como el servidor (para decidir), así que en multijugador mandan los ajustes del servidor y los clientes con el mod deberían tener los mismos.

## Requisitos

Minecraft 26.3, Fabric Loader 0.19.5 o posterior, Fabric API y Java 25. Mod Menu es opcional.

## Compilar

En Windows ejecuta gradlew.bat jar. El mod compilado queda en build/libs/hastestone-26.3.jar.
