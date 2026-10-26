# Holoprint

[Read in English](README.md)

Un mod de Fabric solo para el cliente, para Minecraft 26.3, que convierte esquemas en hologramas por los que puedes volar, que puedes mover, comprobar y construir. Piensa en Litematica, pero más sencillo: una cámara libre, una hotbar llena de herramientas y nada que configurar antes de empezar.

Holoprint es solo de cliente. No hace falta instalarlo en el servidor.

![Un esquema mostrado como holograma en el mundo](docs/images/01-hologram.jpg)

## Qué hace

- **Carga esquemas.** Archivos `.litematic`, Sponge `.schem` y `.schematic` antiguos de la carpeta `schematics`. Los de Litematica de versiones anteriores de Minecraft se actualizan al cargarlos.
- **Los muestra como holograma.** Bloques fantasma translúcidos con un contorno azul en cada bloque (se puede quitar o cambiar de color en los ajustes). Las construcciones grandes van fluidas porque los modelos se guardan en la GPU.
- **Cámara libre.** Pulsa `M` y vuela alrededor de tu construcción sin mover a tu personaje. Pulsa `M` otra vez para salir.
- **Mover, girar y espejar.** El holograma sigue a tu puntero, un clic lo fija y `R` / `Z` lo giran y lo espejan.
- **Comprueba lo que has construido.** Los bloques que faltan se ven como fantasmas; los que están mal se marcan con un contorno rojo.
- **Colocación asistida.** Apunta al holograma y coloca: el bloque entra con el estado y la orientación correctos.
- **Capas.** Mira una sola capa cada vez y sube y baja por la construcción.
- **Lista de materiales** de cada colocación, ordenable (más o menos que faltan o necesarios, por nombre) y fijable en pantalla (arriba a la derecha, también fuera del editor; asigna una tecla en los ajustes).
- **Varias colocaciones a la vez.** Cada una se puede ocultar, bloquear o quitar por separado. Una colocación se marca como completa cuando todo está construido (o a mano, con su casilla en la lista): deja de dibujarse y se queda en la lista como referencia; desmárcala para volver a editarla.
- **Selección, copiar, pegar, borrar y guardar.** Selecciona una zona del mundo, cópiala, pégala como un holograma nuevo o guárdala como esquema.
- **Construir (necesita trucos).** Construye una colocación entera de una vez, o mueve una zona real con `/clone`.

## Herramientas en la hotbar

Con la cámara libre, la hotbar se convierte en la barra de herramientas del editor. Usa las teclas numéricas o la rueda del ratón para elegir una herramienta y el clic de usar para activarla. Tu propia ranura seleccionada nunca se toca.

![Las herramientas de edición en la hotbar, con la leyenda de la herramienta Construir](docs/images/02-hotbar-build.jpg)

| Ranura | Página principal | Carpeta de edición (ranura 1) |
|---|---|---|
| 1 | Herramientas de edición (muestra el modo activo) | Mover |
| 2 | Cargar un esquema | Seleccionar |
| 3 | Colocaciones | Copiar |
| 4 | Lista de materiales | Borrar |
| 5 | Capas | Construir |
| 6 | Editor de área | |
| 7 | Ajustes | |
| 9 | | Atrás |

Mientras una herramienta está resaltada, las teclas que le corresponden aparecen sobre la hotbar, con las teclas que hayas puesto en los controles del juego. Una herramienta que no se puede usar (Mover y Construir sin una colocación activa) se salta con la rueda y las teclas, y la leyenda dice en amarillo por qué. `Ctrl` + rueda (o `,` y `.`) cambia la velocidad de vuelo. Si prefieres las herramientas en el sitio de antes, un menú abierto desde la cámara libre, desactiva las herramientas de la hotbar en los ajustes.

## Capas

Muestra una sola capa de una construcción y recórrela para ver exactamente dónde va cada bloque.

![Mostrando una sola capa de una construcción](docs/images/03-layers.jpg)

## Ventanas

Todo lo que necesita algo más que un clic se abre en una ventana, y al cerrarla vuelves a la cámara.

| | |
|---|---|
| ![Lista de materiales](docs/images/04-materials.jpg) | ![Colocaciones de esquemas](docs/images/05-placements.jpg) |
| ![Cargando un esquema](docs/images/06-load.jpg) | ![Editor de área](docs/images/07-area-editor.jpg) |

El editor de área sirve para escribir a mano las dos esquinas de una zona cuando quieres una exacta.

![Ajustes](docs/images/08-settings.jpg)

## Comprobar una construcción

Según construyes, lo que ya coincide con el holograma desaparece y lo que queda sigue como fantasma. Un bloque que está pero no es el que pide el esquema se marca con un contorno rojo.

![Una cabaña a medio construir: los bloques que coinciden están ocultos y dos bloques equivocados tienen contorno rojo](docs/images/09-verify.jpg)

El juego solo conoce el mundo cerca del jugador, y la cámara libre puede mirar mucho más lejos. Lo que está fuera de ese alcance no se puede comprobar: la fila de la colocación dice cuántos bloques quedan sin comprobar y, si sabes que está hecho, la casilla «Marcar como completa» la da por terminada.

## Controles

Todos son atajos normales, así que puedes cambiarlos en la pantalla de controles del juego.

| Tecla | Acción |
|---|---|
| `M` | Abrir / cerrar la cámara libre |
| `1`-`9`, rueda | Elegir una herramienta de la hotbar |
| Clic | Usar la herramienta; con Mover, fijar o coger el holograma |
| `R` / `Z` | Girar / espejar |
| Flechas, `Re Pág` / `Av Pág` | Mover el holograma un bloque |
| `N` | Cambiar el modo de edición (Mover, Seleccionar, Copiar, Borrar y después Construir) |
| `B` | Construir la colocación a la que apuntas (necesita trucos) |
| `J` | Lista de materiales |
| Teclado numérico `+` / `-` / `*` | Capa arriba / abajo / todas o una |
| `Ctrl` + `C` / `X` / `V` | Copiar / cortar / pegar |
| `Retroceso` | Cancelar |
| `Esc` | Cancelar lo que estás haciendo (un movimiento, una selección, una copia en la mano); si no hay nada que cancelar, abre el menú de pausa |

Con la colocación asistida activada y un bloque en la mano, hacer clic en un cofre, una mesa o un cartel de un holograma no lo abre (como en Litematica): úsalo con la mano vacía o desactiva la colocación asistida.

Si también tienes Litematica instalado, algunas de sus teclas por defecto (por ejemplo, el `-` del teclado numérico) las usan los dos mods; cambia una de ellas en la pantalla de controles.

## Mando (Controlify)

Con [Controlify](https://modrinth.com/mod/controlify) instalado, Holoprint también se maneja con mando. Es opcional y no cambia nada mientras usas teclado y ratón: solo actúa mientras hay un mando conectado y el modo de entrada de Controlify es el mando.

- **Los sticks, saltar y agacharse** mueven la cámara, igual que mueven al jugador. **Los gatillos** son el clic (atacar = clic izquierdo, usar = clic derecho).
- **Los botones superiores** (LB/RB) recorren las herramientas de la hotbar (y los botones de la hotbar eligen una), sin tocar tu propia ranura.
- **B** abre el editor. Dentro cancela lo que estás haciendo (un movimiento, una selección, una copia en la mano) y, si no hay nada que cancelar, lo cierra, como Esc. **La cruceta a la izquierda** gira el holograma.
- **B** también cierra una ventana (ajustes, lista de materiales...), como Esc.
- **Teclado y mando se asignan por separado.** Con un mando conectado, la lista de Controles de los ajustes de Holoprint tiene dos columnas por acción: la tecla y el botón del mando. Haz clic en la segunda y pulsa el botón que quieras; Reset devuelve los dos a su valor.
- **El resto de acciones** (espejar, empujar, capas, lista de materiales, construir, copiar, cortar, pegar...) están en los controles de Controlify, en **Holoprint**, listas para darles un botón, y se pueden poner en su menú radial. Copiar, cortar y pegar no necesitan Ctrl con mando.
- Las leyendas de teclas muestran el botón del mando en vez de la tecla mientras usas el mando.

## Instalación

1. Instala [Fabric Loader](https://fabricmc.net/) y la Fabric API para Minecraft 26.3.
2. Pon `holoprint-26.3.jar` en tu carpeta `mods`.
3. Pon tus esquemas en la carpeta `schematics` del juego y pulsa `M` en un mundo.

## Compilar desde el código

En Windows ejecuta `gradlew.bat build` (`./gradlew build` en otros sistemas). El mod queda en `build/libs/holoprint-26.3.jar`.

## Estado

En desarrollo, sin publicar todavía.
