# Hastemine

Mod Fabric para Minecraft 26.3 que acerca el minado con Prisa a Bedrock Edition: deepslate (y, si quieres, cobblestone) instantáneas con Prisa II como la piedra, deepslate a velocidad de piedra con cualquier pico, y troncos del Overworld con el escalado de Prisa de Bedrock. Antes se llamaba Hastestone.

- **Deepslate instantánea con Prisa II** (activada por defecto): con Prisa II (un beacon, una poción...), la deepslate se rompe de golpe siempre que la piedra lo haría con el mismo pico y los mismos encantamientos.
- **Deepslate a velocidad de piedra** (desactivada por defecto): la deepslate tarda exactamente lo mismo que la piedra con la herramienta que lleves, con o sin beacon. Cada pico (madera, piedra, hierro, diamante, netherite) conserva su propio tiempo de piedra.
- **Cobblestone instantánea con Prisa II** (desactivada por defecto): igual que en Bedrock Edition, la cobblestone se rompe de golpe con Prisa II siempre que la piedra lo haría. En Java la cobblestone es más dura que la piedra y no se rompe al instante.
- **Losas instantáneas con Prisa II** (desactivada por defecto): cualquier losa que se mine con pico (piedra, cobblestone, ladrillo, andesita, diorita, granito, arenisca, deepslate, cobre...) se rompe de golpe con Prisa II siempre que la piedra lo haría con el mismo pico y encantamientos. Las de madera no cambian.
- **Troncos instantáneos con Prisa II** (desactivada por defecto): con Prisa II o más y cualquier hacha en la mano, los troncos y troncos pelados del Overworld se rompen de golpe.
- **Troncos con Prisa de Bedrock** (activada por defecto): los troncos y troncos pelados del Overworld se minan como en Bedrock Edition. Allí la Prisa de nivel n acelera el minado (1 + 0,2n), igual que en Java, y además 1,2^n por encima: con Prisa II son unas 2,02 veces en vez de 1,4. Con un hacha de netherite con Eficiencia V y Prisa II, los troncos se rompen de golpe; con Prisa I no. Escala con cualquier nivel de Prisa. No afecta a los bloques de madera (`*_wood`), a los troncos del Nether ni al bambú. (Bedrock 26.70 igualó después esta fórmula a la de Java.)
- Interruptor general para desactivar el mod: con él apagado, el minado es el de vanilla.

Solo se tocan la deepslate, la cobblestone y los troncos. Prisa I no basta para la opción instantánea, y las herramientas que no son picos no ganan nada.

## Ajustes

Disponibles en Mod Menu (Mods > Hastemine > Configure) o en `config/hastemine.properties`.

| Ajuste | Por defecto |
| --- | --- |
| Hastemine (interruptor general) | ON |
| Deepslate instantánea con Prisa II | ON |
| Deepslate a velocidad de piedra | OFF |
| Cobblestone instantánea con Prisa II | OFF |
| Losas instantáneas con Prisa II (con pico) | OFF |
| Troncos instantáneos con Prisa II (cualquier hacha) | OFF |
| Troncos con Prisa de Bedrock | ON |

El cálculo del minado lo hacen tanto el cliente (para predecir) como el servidor (para decidir), así que en multijugador mandan los ajustes del servidor y los clientes con el mod deberían tener los mismos.

## Requisitos

Minecraft 26.3, Fabric Loader 0.19.5 o posterior, Fabric API y Java 25. Mod Menu es opcional.

## Compilar

En Windows ejecuta gradlew.bat jar. El mod compilado queda en build/libs/hastemine-26.3.jar.
