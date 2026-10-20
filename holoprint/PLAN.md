# Holoprint: plan

Documento de planificación del mod. Recoge lo acordado hasta ahora; las ideas nuevas se añaden al final, en "Ideas por decidir".

Nombre provisional: **Holoprint**. Carpeta `holoprint/`, id `holoprint`, paquete `io.github.dannygt97.holoprint`, grupo de Maven `io.github.dannygt97`. Minecraft 26.3, Fabric, Java 25, solo cliente.

## 1. Qué es

Una alternativa mucho más simple e intuitiva a Litematica, escrita desde cero, para trabajar con schematics: cargarlos, verlos como holograma, moverlos, copiarlos, pegarlos y construirlos de forma asistida.

Idea central: **un control parecido a un freecam**. Para gestionar una construcción pasas a una cámara libre desde la que ves y mueves el holograma de forma visual y en tiempo real, sin cambiar de modo de juego ni mover a tu personaje.

## 2. Principios de diseño

1. **Todo por interfaz y visual. Nada por comandos.** Cargar, mover, seleccionar, copiar, ajustar teclas: todo con botones, ventanas y la propia cámara. Los comandos `/holoprint ...` actuales son solo una herramienta provisional de desarrollo y se eliminarán.
2. **Intuitivo antes que potente, hasta para quien lo usa por primera vez.** Nada de palo especial ni de combinaciones como Ctrl + rueda del ratón. Cada acción es un botón, una tecla normal o un clic, y siempre hay una leyenda o una pista en pantalla que dice qué hace cada cosa.
3. **Visual.** Para mover una construcción se habilita la cámara libre y el holograma sigue al cursor en tiempo real.
4. **Pensado para el mando, entregado primero con teclado y ratón.** Todo se diseña para que se pueda usar cómodamente con mando (junto con Controlify): cada acción es una tecla reasignable, las ventanas se pueden recorrer con los botones y ningún gesto depende solo del ratón. Pero la primera versión realmente funcional es la de teclado y ratón; el soporte de mando se construye después, sin tener que rehacer nada.
5. **El personaje manda.** La cámara libre no cambia el modo de juego ni mueve al jugador; no se envía nada al servidor al volar.
6. **Compatibilidad de archivos.** Se leen y se guardan `.litematic`, así que los schematics actuales siguen valiendo y los nuevos valen también en Litematica.
7. **Simple.** Pocas pantallas y pocos ajustes; lo avanzado, escondido hasta que haga falta. Litematica sirve de referencia de lo que se necesita, pero se simplifica a propósito.

## 3. Dónde y cómo se usa

- Un solo jugador, en **creativo** y en **supervivencia**.
- En supervivencia interesa sobre todo **mover construcciones**: trasladar todos los bloques de una selección de un lugar a otro.
- Servidores: de momento no es objetivo. Las funciones de colocar y rellenar se consideran trampas en muchos servidores, así que, si se juega en uno, tendrán que poder limitarse o desactivarse (ver riesgos).

## 4. Lo acordado, función por función

### 4.1 Schematics
- Leer y guardar `.litematic` (versiones 1 a 7).
- Cargar archivos de versiones antiguas de Minecraft actualizándolos con el actualizador de datos del propio juego (renombra bloques, arregla datos).
- Bloques que no existan en esta versión: se cargan como aire y se avisa de cuántos son.
- **Formatos compatibles (decisión de Daniel):** solo estos tres, que el selector detecta solos (ver 5.3), en este orden:
  1. `.litematic` (ya hecho)
  2. `.schematic` (formato antiguo de MCEdit/Schematica)
  3. `.schem` (WorldEdit/Sponge)
- **Fuera de alcance por ahora:** no se implementa `.nbt` ni un formato propio `.holoprint`. Más adelante se decidirá si merece la pena crear un formato propio.

### 4.2 Holograma
- Se dibuja en el mundo como bloques fantasma translúcidos con sus texturas.
- Solo se dibujan las caras visibles de la superficie, no el interior.

### 4.3 Cámara libre y mover
- Cámara libre estilo freecam: el jugador se queda quieto y en su modo de juego.
- **Se ve al jugador en tercera persona** mientras la cámara se mueve libremente (ver 5.6).
- El holograma **sigue al cursor**; un clic lo **fija** para mirarlo desde todos los lados y otro clic lo **vuelve a coger**.
- Rotar 90°, espejar y empujar un bloque con teclas normales o botones.
- Clic derecho o la tecla de mover terminan; Retroceso cancela y lo devuelve a donde estaba.
- Leyenda en pantalla con los controles.

### 4.4 Colocar y rellenar ("easy place" y variante de relleno)
- **Easy place:** colocar el bloque con el estado correcto (orientación, mitad, etc.) del schematic.
- **Relleno:** cuando el jugador está sobre un bloque del holograma y pulsa "colocar objeto", se coloca automáticamente el bloque que el schematic pide en esa posición, si el jugador lo tiene. Manteniendo pulsado va llenando los siguientes. **No hace falta apuntar a la cara exacta ni colocar el holograma en el sitio correcto del mundo.**
- En supervivencia gasta objetos del inventario y respeta el alcance normal.

### 4.5 Selección, copiar, pegar y mover
- Seleccionar una zona del mundo, copiarla, cortarla, pegarla, moverla y guardarla como schematic.
- **La selección se hace dentro de la cámara libre, sin escribir coordenadas** (ver 5.5).
- Mover en supervivencia: trasladar los bloques de una selección a otro lugar; el original se rompe con la herramienta y se reconstruye en el destino con el inventario, sin comandos (ver sección 14).

### 4.6 Mando (Controlify)
**No es prioritario construirlo ahora** (primero teclado y ratón), pero todo se hace ya con él en mente; cuando llegue su fase, la integración se apoya en lo siguiente:
- Todas las acciones son `KeyMapping` normales: Controlify las convierte en acciones asignables al mando.
- Además se registran en la API de bindings de Controlify (`onControlifyPreInit`, `registerBinding`, `keyEmulation`) para que salgan en su menú con nombre y categoría propios.
- Controlify es dependencia **opcional**: sin él, el mod funciona igual con teclado y ratón.
- La cámara libre lee primero la entrada normal del teclado, de modo que los sticks que añade Controlify entran por el mismo camino.
- Las ventanas del mod siguen las reglas de Controlify (navegación con el mando) y se podrá usar su API de guías para mostrar los botones.
- No hay forma de probar el tacto de los sticks sin un mando físico: eso lo prueba Daniel.

### 4.7 Ajustes
- Ventana de ajustes propia del mod, con la asignación de teclas y botones del mando (ver 5.4).
- Los ajustes generales siguen el diseño de ajustes del resto de mods (barra lateral de categorías, interruptor general primero, Reset y Done) cuando se abran desde Mod Menu.

## 5. Interfaz (lo que se pidió el 2026-10-14)

Referencia: capturas de Litematica 0.29.1 que ha enseñado Daniel. La idea es lo mismo con menos cosas y más fácil.

### 5.1 Foco del ratón: ¿se pueden pulsar botones y seguir con la cámara? (análisis)

**Respuesta corta: sí se puede, con matices.** Minecraft captura el ratón mientras juegas (cursor invisible y movimiento libre); para pulsar un botón hay que soltarlo, y el juego solo lo suelta cuando hay una pantalla (`Screen`) abierta. Hay dos formas de resolverlo:

**A. Modo editor (recomendada).** Mientras Holoprint está activo hay una pantalla propia, transparente y que no pausa el juego, con el cursor libre. Así:
- El cursor puede pulsar los botones de la barra al instante, sin tecla extra.
- El holograma y la selección siguen al **puntero del ratón** (no al centro de la pantalla), como en un editor 3D. Para eso se calcula el rayo desde la posición del puntero con las matrices de la cámara.
- Para mirar alrededor se mantiene pulsado el botón derecho (el cursor se oculta mientras dura) y se vuela con WASD, Saltar y Agacharse. Es el esquema de Unity, Blender y editores de mapas: se entiende sin explicación.
- Con mando, Controlify gestiona las pantallas con su ratón virtual y la navegación entre botones; los sticks mueven y giran la cámara.

**B. Tecla para soltar el cursor.** El juego sigue en modo normal y mientras se mantiene una tecla (por ejemplo Alt) el cursor se libera para pulsar botones. Es más sencillo de programar, pero poco descubrible, incómodo con una mano en el teclado y malo para el mando.

**Limitaciones a tener en cuenta con A**
1. Con una pantalla abierta, el juego deja de actualizar las teclas de movimiento. Hay que leerlas directamente de GLFW, es decir, volar, subir y bajar son nuestras propias teclas de lectura, y quedan reasignables en la ventana de teclas.
2. El movimiento del ratón al mirar se calcula con el cursor oculto y sus deltas, sin usar el atajo normal de "capturar ratón" del juego, que cierra la pantalla al activarse; habrá que llamar a la capa de bajo nivel (GLFW) o a un mixin.
3. No se puede abrir el inventario ni el chat mientras la pantalla está activa, salvo que se añadan atajos para ello (de momento no).
4. La interacción con el ratón virtual de Controlify está sin probar: es lo primero que hay que verificar con Daniel y su mando.
5. Una prueba automática no puede comprobar de verdad el cursor, el foco de la ventana ni el tacto del ratón: se prueba la lógica y se hacen capturas, pero la sensación la valida Daniel a mano.

**Plan:** hacer primero un prototipo pequeño (pantalla del modo editor + una barra con un botón) y que Daniel lo pruebe antes de construir encima.

### 5.1 bis Modos de edición (decisión de Daniel)
- El editor tiene **modos de edición** entre los que se cambia (por ejemplo: mover un holograma, seleccionar una zona, rellenar), cada uno con su comportamiento del cursor y su leyenda. Se cambia de modo con los botones de la barra y con una tecla, y debe poder hacerse también con mando.
- **Modos decididos por Daniel (2026-10-14), tres al principio:**
  1. **Seleccionar área:** clic en el punto 1 y clic en el punto 2 para marcar una zona, con la posibilidad de **exportarla como schematic, borrarla o copiarla**.
  2. **Copiar:** clic sobre un holograma (o sobre la zona) para cogerlo y clic en cualquier otro lugar para pegarlo.
  3. **Mover:** como funciona ahora (el holograma sigue al cursor, clic para fijarlo, girar, reflejar, ajustar con flechas).
  **Modo al entrar (confirmado por Daniel):** **Mover** si hay un holograma cargado; **Seleccionar área** si no lo hay. **Rellenar** (fase 3) no es un modo de la barra: se hace al colocar con el objeto en la mano.
- **Atajos de portapapeles estándar:** **Ctrl+C** copiar, **Ctrl+X** cortar y **Ctrl+V** pegar, además de los botones, en todos los modos que tengan una selección o un holograma. Reasignables en la ventana de teclas.
- **Cómo se pasa entre girar la cámara y pulsar botones: Daniel eligió la opción A por ahora, sujeta a cambios** (hay que dejar el código preparado para cambiarla fácilmente):
  - *Opción A (elegida):* el cursor está oculto y el ratón gira la cámara; **una tecla (Tab)** alterna entre "girar" y "cursor libre para botones".
  - *Opción B (alternativa para más adelante):* el cursor está siempre visible; **mantener el botón derecho** gira la cámara.
  En ambas el editor conserva su estado (ver las reglas de abajo).
- **Regla de Daniel: no empeorar la sensación de la cámara libre actual** (le gusta como se siente ahora; mejorarla está bien, empeorarla no). Para cumplirlo:
  - En el estado de volar se mantiene tal cual lo de hoy: ratón capturado y cursor oculto, giro con la misma sensibilidad (`LOOK_SPEED` 0.15) y sin retraso, mismo vuelo (WASD, Saltar, Agacharse, velocidad 0.9, rápido x2.5) y la rueda cambia la velocidad.
  - Si para tener una pantalla propia hay que leer ratón y teclas por GLFW, el resultado debe sentirse igual que ahora; si no se consigue, se prefiere otra solución antes que cambiar el tacto (por ejemplo mantener la cámara en el juego normal y abrir la pantalla solo al pulsar Tab).
  - Daniel lo valida a mano comparando con la versión actual antes de seguir construyendo encima. La opción A (Tab) encaja con esto porque volar queda igual que ahora.
- Por tanto, el prototipo previsto en 5.1 será el de **cambio de modos** con un par de botones y el cursor como se decida.

**Reglas para no perder el editor al usar los botones (preocupación de Daniel)**
Soltar el cursor no cierra el editor: solo cambia quién recibe el ratón. La cámara, la posición del holograma, el modo de edición y la selección siguen exactamente donde estaban, y el mundo se sigue dibujando detrás. Para que se note como un solo espacio de trabajo:
1. **Los botones de la derecha viven dentro de nuestra propia pantalla del editor.** Pulsar Ajustes o el selector de archivos abre un panel o ventana dentro de ella, no una pantalla distinta del juego. Al cerrarlo se vuelve al punto exacto del editor, sin recolocar nada.
2. **Con el cursor libre se puede seguir volando con el teclado** (WASD, Saltar y Agacharse se leen directamente del teclado, como en 5.1). El ratón se usa para pulsar y el teclado para moverse a la vez; solo el giro con el ratón queda en pausa hasta volver al modo de volar. Opcionalmente, el giro también se puede hacer con teclas (por ejemplo Q y E).
3. **Con el puntero sobre un botón o una ventana, el mundo no recibe el ratón:** el holograma deja de seguir al puntero (queda donde estaba) y los clics no fijan ni seleccionan nada. Así no se mueve nada por error al ir a pulsar un botón.
4. **Cambio instantáneo con una tecla** (provisionalmente Tab; después con un botón de mando) entre volar y usar la interfaz, sin pasar por menús, para ir y volver en un segundo.
5. **Esc cierra primero el panel abierto** y, si no hay ninguno, pregunta si quieres salir del editor, para no tirar el trabajo con una pulsación.
6. **Una sola limitación real:** solo puede haber una pantalla del juego abierta a la vez, y es la nuestra. Por eso las ventanas del editor tienen que ser paneles suyos y no pantallas independientes. Los ajustes que se abren desde Mod Menu, fuera de una partida, son una pantalla aparte y no afectan.
Criterio del prototipo: con la cámara volando, abrir Ajustes y cerrarlo debe dejar cámara, holograma y modo intactos; WASD debe seguir funcionando con el cursor libre; y pasar el puntero por la barra no debe mover el holograma. Lo comprobará una prueba automática en lo que sea posible y Daniel a mano en lo demás.

### 5.2 Barra de botones en la cámara libre
- La barra es **extensible**: los tres botones de ejemplo (selector de archivos, ajustes y un tercero) eran solo una muestra, y se irán añadiendo más cuando haga falta, sin rehacer la barra. Cada botón es una herramienta o una ventana registrada en una lista.
- Una **columna de botones de iconos** en el lado derecho de la pantalla (la esquina superior derecha, como marcó Daniel en rojo en la primera captura), debajo de lo que ya muestra Minecraft. El panel con la leyenda de controles se queda a la izquierda.
- Botones previstos, de arriba abajo: **Selector de archivos**, **Ajustes** y un tercero por definir (por ejemplo, selección y copiar/pegar; ver preguntas). El botón activo se resalta.
- Cada botón tiene una pista (tooltip) con su nombre y su tecla, y se puede pulsar con el ratón o con el mando.
- Es la sustitución del "menú principal" de Litematica (primera captura de referencia): en vez de una pantalla llena de botones, una barra siempre visible dentro de la cámara libre.

### 5.3 Selector de archivos
Referencia: pantalla "Load Schematic" de Litematica.
- Lista de archivos de la carpeta `schematics` del juego, **detectados automáticamente** (formatos de 4.1), con un icono que indica el formato, caja de búsqueda y carpetas.
- Panel de información del archivo seleccionado: nombre, autor, fecha de creación y de modificación, número de regiones, volumen, bloques totales, tamaño total, versión del formato y versión de Minecraft con la que se creó. Si el archivo trae imagen de vista previa, se muestra.
- Botones: **Cargar**, y los extras justos (abrir la carpeta, renombrar). Lo que Litematica tiene de más (lista de materiales, renombrar el schematic, schematics cargados, "crear colocación") se simplifica: cargar un archivo ya crea el holograma y lo deja listo para mover.
- Funciona con el ratón y con el mando.

### 5.4 Ajustes y asignación de teclas y botones del mando
Referencia: pantallas "Hotkeys" y "Colors" de Litematica.
- Botón de **Ajustes** en la barra. Dentro, una sección **Teclas y mando**: una ventana propia donde se asigna a cada acción su tecla/ratón y su botón de mando.
- Una fila por acción: nombre claro (no `easyPlaceToggle` como en Litematica), botón con la tecla actual y botón con el botón de mando actual; pulsar uno y luego la tecla o botón nuevo; botón de reinicio por fila y uno general; aviso de conflictos con otra acción; caja de búsqueda.
- Con Controlify instalado, la parte de mando se enlaza con sus asignaciones; si la API no permite editarlas desde nuestra ventana, un botón abre la pantalla de asignaciones de Controlify (a comprobar).
- Sección **Apariencia**: opacidad del holograma, color de la selección y, más adelante, los colores del verificador. Cada color con casilla de texto hexadecimal, muestra del color y reinicio, como en la captura de colores de Litematica, pero con muchas menos opciones.
- El diseño de la ventana sigue el del resto de mods.

### 5.5 Seleccionar una zona sin escribir coordenadas
Referencia: el "Area Editor" de Litematica (con X, Y, Z de dos esquinas escritas a mano), que es justo lo que se quiere evitar.
- Todo se hace dentro de la cámara libre: clic en un bloque para poner la primera esquina y otro clic para la segunda; la caja aparece dibujada en el mundo con sus medidas (ancho × alto × largo).
- Después se ajusta la caja arrastrando sus caras o asas, también con el ratón o el mando.
- Botones en pantalla para **Copiar**, **Cortar**, **Mover**, **Pegar** y **Guardar como schematic** (con nombre editable en una ventana pequeña).
- Las coordenadas exactas, si hacen falta, quedan como opción avanzada escondida, no como camino principal.

### 5.6 Ver al jugador en tercera persona
- Mientras vuela la cámara se debe ver al jugador en el mundo, con su modelo, sin tener que cambiar de cámara.
- Hoy no se dibuja: el juego oculta al jugador local mientras la vista es la de primera persona. Habrá que forzar su dibujado mientras la cámara libre está activa (mixin al renderizado de entidades).

## 6. Estado actual

| Fase | Contenido | Estado |
| --- | --- | --- |
| 1 | Leer y guardar `.litematic` | Hecha y probada |
| 2a | Dibujar el holograma | Hecha y probada |
| 2b | Cámara libre y mover el holograma | Hecha y probada (24 comprobaciones con un jugador real en supervivencia) |
| 2c | Base de la interfaz: pantalla del editor con cursor libre, barra de botones con iconos, jugador visible en tercera persona, sin comandos | **Hecha y probada (2026-10-14)** |
| 2d | Selector de archivos con detección automática y panel de información | **Hecha y probada**: lista de la carpeta con buscador y datos del archivo (autor, tamaño, bloques, fechas); solo se cargan `.litematic` (los otros formatos son de la fase 5) |
| 2e | Ajustes y ventana de teclas y mando | **Hecha y probada**: configuración con opciones guardadas en `config/holoprint.json`, colores con caja hexadecimal y muestra, opacidad, límites, asignación de teclas con aviso de conflictos, buscador y reinicio por fila; el editor de área también funciona. El mando queda para la fase 6 |
| 3 | Colocar con el estado correcto y relleno | **Hecha (2026-10-19)**: easy place y relleno al mantener pulsado (ver "Fase 3" al final); falta ordenar por tandas la lista de materiales que faltan y los bloques de varias partes |
| 4 | Selección sin coordenadas, copiar, cortar, pegar, mover y guardar | Adelantada en 2c: seleccionar con dos clics, copiar, cortar, pegar, borrar y guardar. Falta mover en supervivencia, arrastrar caras y entidades / datos de bloques |
| 5 | Lista de materiales, verificador de diferencias, otros formatos de archivo (`.schematic` y `.schem`), pulido y publicación de la versión de teclado y ratón | **Hecha en parte (2026-10-19)**: lista de materiales, verificador y formatos. Pulido hecho (color de bioma, caché de mallas, cofres y carteles). Queda la publicación |
| 6 | Integración con Controlify y prueba con mando real (no prioritaria) | Por hacer, al final |

Nada está commiteado ni publicado todavía: la carpeta es local.

## 7. Controles actuales (provisionales)

| Acción | Tecla por defecto |
| --- | --- |
| Abrir / cerrar el editor | G |
| Cambiar de modo (Mover → Seleccionar área → Copiar) | N |
| Cursor libre para los botones / volver a girar | Tab |
| Rotar 90° / Espejar (Mover y Copiar) | R / M |
| Empujar un bloque (adelante, atrás, izquierda, derecha) | Flechas |
| Empujar un bloque (arriba, abajo) | Re Pág, Av Pág |
| Clic izquierdo | Mover: fijar o coger el holograma. Seleccionar: marcar esquina. Copiar: coger una copia / dejarla |
| Clic derecho | Mover: terminar. Seleccionar: quitar la selección. Copiar: soltar la copia |
| Cancelar / limpiar | Retroceso |
| Copiar, cortar, pegar | Ctrl+C, Ctrl+X, Ctrl+V |
| Borrar la selección (pide pulsar otra vez) | Supr |
| Guardar la selección como `.litematic` | O |
| Volar | WASD, Saltar, Agacharse |
| Velocidad de vuelo | Rueda del ratón |

Todas son reasignables en Controles. Cargar un schematic, de momento: `/holoprint load <archivo>` con los archivos de la carpeta `schematics` del juego (también `/holoprint unload` y `/holoprint move`). Estos comandos desaparecerán con la fase 2d. Al abrir el editor con G se entra en Mover si hay un holograma cargado y en Seleccionar área si no lo hay.

Borrar y cortar envían comandos `/fill ... air` (hacen falta permisos de operador); copiar solo lee los bloques (sin entidades ni datos de bloques como el contenido de cofres, de momento).

## 8. Arquitectura

```
holoprint/src/client/java/io/github/dannygt97/holoprint/
  schematic/   BitArray, Region, Schematic, BlockStateTags, LitematicFormat
  hologram/    Hologram (datos + rotar/espejar), HologramRenderer (dibujo)
  freecam/     Freecam (cámara), FreecamInput (entrada)
  placement/   HologramPlacement (modos y teclas), PlacementHud (leyenda)
  command/     HoloprintCommands (provisional)
  mixin/       EntityMixin, MinecraftMixin, MouseHandlerMixin, GameRendererMixin
  client/      HoloprintClient (entrada del mod)
```

Se añadirán: `ui/` (pantalla del modo editor, barra de botones, selector de archivos, ventana de ajustes, ventana de teclas), `selection/` (caja de selección y portapapeles) y `format/` (otros formatos de archivo).

- **Cámara:** `Minecraft.setCameraEntity` apunta a una entidad `Marker` que existe solo en el cliente; la entrada del jugador se sustituye por `FreecamInput`.
- **Mixins:** `Entity.turn` (giro con ratón o mando), clics y recoger bloque de `Minecraft`, rueda de `MouseHandler`, mano en primera persona de `GameRenderer`.
- **Dibujo del holograma:** `LevelRenderEvents.COLLECT_SUBMITS` con `submitCustomGeometry` y `RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS)`; los quads salen de los modelos de bloque (`BlockStateModelSet`), posiciones relativas a la cámara, `QuadInstance` con alfa y luz máxima.

## 9. Notas técnicas importantes

- **Formato de estados de bloque:** en 26.3 las utilidades del juego (`NbtUtils.read/writeBlockState`) usan `{id, properties}`, no el `{Name, Properties}` del `.litematic`. Se usa un lector propio (`BlockStateTags`) que entiende los dos y escribe el clásico. Una prueba de ida y vuelta sola lo habría escondido: hay que probar siempre con un archivo real (`Boulevardier's_Half-Timbered_House_upd.litematic`, 9.293 bloques).
- **Regiones con tamaño negativo:** la caja se extiende hacia atrás desde su posición. Orden de bloques: `(y * sizeZ + z) * sizeX + x`. Ancho de bits de la paleta: `max(2, ceil(log2(tamaño)))`, valores empaquetados de forma continua entre longs.
- **Licencia de Litematica:** es LGPL-3.0. No se copia su código; el formato `.litematic` sí se puede implementar.
- **Recorte inteligente de chunks:** se desactiva mientras vuela la cámara, para que no salgan huecos al atravesar terreno.
- **Rendimiento:** una casa de 9.000 bloques visibles va a 60 fps redibujando todo en cada fotograma, sin caché. Para schematics enormes habrá que pasar a mallas por sección.
- **Vista previa del archivo:** el `.litematic` puede guardar una imagen de vista previa en sus metadatos; el selector de archivos la mostrará si existe, y al guardar se podrá generar una.

## 10. Pendiente técnico (sin decidir el orden)

- **Antes que nada, las tareas críticas de la sección 15** (T1 a T10): chunks de la cámara libre, mover desde la cámara, flujo de ventanas y barra, y rediseño de las pantallas.

- Todo lo de la sección 5 (modo editor, barra, selector, ajustes, selección).
- Integración con la API de Controlify y prueba con mando real.
- Bloques con datos (cofres, carteles), entidades y fluidos del schematic: se leen y se guardan; el holograma muestra ya los cofres y carteles (fase 5) pero no sus objetos (ver T11 en la sección 15: se mostrarán por defecto, pausados y con una casilla para ocultar las entidades).
- Colorear las diferencias entre el holograma y lo construido (verificador).
- Lista de materiales.
- Iconos de la barra de botones.
- Icono, descripción de Modrinth y publicación (cuando Daniel lo decida).

## 11. Preguntas abiertas

1. **Mover en supervivencia:** resuelto: se rompe el original con la herramienta y se reconstruye en el destino con el inventario (ver sección 14); quedan por decidir los detalles de ritmo y de los bloques que no se pueden recoger intactos.
2. **Relleno:** ¿qué orden sigue (de abajo arriba, por cercanía)? ¿Hay un límite de velocidad?
3. **Materiales que faltan:** ¿qué pasa cuando el schematic pide un bloque que el jugador no lleva? ¿Aviso, o salta al siguiente? Aviso dsicreto y no invasivo, si se repite la interaccion aún no teniendo bloque acumular en 1 dialogo, no crear muchisimos.
4. **Servidores:** ¿hace falta un ajuste que limite o desactive el relleno?
5. **Modos de edición:** respondido: Seleccionar área, Copiar y Mover, con Ctrl+C/X/V (ver 5.1 bis). Modo al entrar confirmado (Mover con holograma, Seleccionar área sin él). Para girar la cámara se usa por ahora la **opción A (tecla Tab)**, sujeta a cambios.
6. **Formato propio:** queda pospuesto; más adelante se decide si se crea uno.
7. **Licencia** de este mod (hoy CC0 por defecto, a falta de decisión).

Respondidas: el tercer botón de la barra era solo un ejemplo (la barra será ampliable); los formatos son solo `.litematic`, `.schematic` y `.schem`; el mando no es prioritario de entrega.

## 12. Cómo se prueba

Cada cambio se comprueba arrancando el cliente de desarrollo con una prueba temporal: se crea un mundo, se usa un jugador real y se verifican los resultados con comprobaciones PASS/FAIL, además de capturas de pantalla cuando importa el aspecto. Después se borra la prueba y se recompila. Para el formato de archivo se usa siempre el schematic real de Daniel. Lo que depende de la sensación física (ratón, foco, sticks del mando) lo valida Daniel a mano.

## 13. Ideas por decidir

_(Aquí se añaden las ideas nuevas que vayan saliendo.)_

## 14. Decisiones del 2026-10-14 (legalidad en supervivencia, colocaciones y ventanas)

- **Mundos de supervivencia legales:** en supervivencia Holoprint **nunca envía comandos ni cambia el mundo con trucos**. Cortar y borrar bloques del mundo (`/fill`) solo existen en creativo. Un holograma solo se completa colocando los bloques a mano o con el relleno automático (fase 3), jamás pegándolo "de verdad". **Mover una construcción en supervivencia sí se puede, sin comandos ni trucos** (decisión de Daniel: mover no es trampa, duplicar sí): es un asistente que **rompe el original bloque a bloque con la herramienta del jugador** (acciones normales de minería, con su tiempo y su alcance, y los objetos van al inventario) y **lo vuelve a colocar en el destino con los bloques del inventario** (el relleno de la fase 3). Como cada bloque colocado sale del inventario, nada se duplica. Detalles: el jugador debe estar al alcance (el asistente solo actúa sobre lo que alcanza y avisa de lo que queda lejos); los bloques que no se pueden recoger intactos (cristal o hielo sin Toque de seda, césped, piedra que suelta adoquín...) se avisan antes con una lista y no se mueven tal cual; el contenido de cofres y similares cae como objetos al romperlos; y todo va a un ritmo humano, sin romper de golpe. Copiar y pegar "de verdad" sigue prohibido: solo hologramas.
- **Colocaciones de schematics (como Litematica, foto 2):** cada estructura que se coloca pasa a ser una "schematic placement" de una lista con nombre, posición, rotación, espejo, activada, visible y bloqueada. Se edita desde la ventana "Colocaciones" (botón de la derecha, entrando con Tab) o directamente desde el modo de cámara libre (G). Hay un **límite** (`PlacementLimits.MAX_PLACEMENTS`, ahora 8) para no sobrecargar el mundo.
- **Ventanas dentro del editor** (`ui/panel`): Cargar schematic (funcional: lista la carpeta `schematics`, buscador, solo carga `.litematic`), Colocaciones, Configurar la colocación, Editor de área y Configuración (barra lateral con General, Aspecto, Controles y Límites). Las tres últimas y los botones de la lista de colocaciones son todavía solo diseño, sin conectar (así lo pidió Daniel).
- **Modo Borrar** (cuarto modo, tecla N lo incluye): clic sobre un holograma quita esa colocación; clic sobre la caja de la selección la quita. Solo quita cosas de Holoprint, nunca bloques del mundo.
- **Pruebas:** siempre en un mundo superplano, para que el terreno no estorbe.
- **Inventario lleno al mover en supervivencia:** el asistente nunca debe perder bloques. Antes de empezar calcula cuántos huecos hacen falta y avisa; trabaja **por tandas** (rompe una tanda, la coloca en el destino y sigue) para que el inventario no se llene; **se detiene** con un aviso en cuanto queda poco hueco (se deja una reserva) y no rompe más hasta que haya sitio; y no rompe el siguiente bloque mientras queden objetos sin recoger por el suelo (los objetos del suelo desaparecen a los 5 minutos y solo se recogen cuando el jugador pasa cerca, así que el asistente señala dónde están y espera). Los cofres, shulkers y similares no se vacían solos: el contenido cae como objetos y cuenta para el espacio.
- **Hecho (2026-10-14, tras la prueba de Daniel):** los modos giran en anillo con N (al llegar al último vuelve al primero y Mover se salta si no hay holograma); sin schematic cargado la leyenda muestra un aviso amarillo; las ventanas ya no sacan del editor (con una ventana abierta las teclas del editor no se disparan, así que escribir en el buscador no cambia de modo ni cierra nada, y el buscador de Cargar tiene el foco al abrirse); la ventana de Colocaciones es funcional (elegir la colocación activa, ocultar, bloquear, quitar, ocultar todas, quitar todas, cargar) y la de configurar la colocación también (nombre, posición, giro, espejo, activa, visible, bloqueada, quitar). Una colocación bloqueada no se mueve ni se borra.
- **Rendimiento con schematics grandes:** el holograma se corta en celdas de 16 bloques que se descartan sin mirar si están fuera de alcance o de la vista; no se dibujan las caras que miran hacia el lado contrario de la cámara; hay un tope de bloques por fotograma (los más cercanos primero) con aviso amarillo cuando se recorta; la reconstrucción usa una matriz plana en lugar de un mapa de posiciones (un bloque macizo de 8,4 millones de bloques se prepara en ~0,4 s) y se hace fuera del hilo del juego al cargar. Pendiente si hiciera falta más: guardar la geometría en un búfer de GPU por celda.
- **Fase 2 cerrada (2026-10-14):** sin comandos `/holoprint` (todo por interfaz); jugador visible en tercera persona mientras vuela la cámara libre (un mixin en `LevelExtractor` quita el filtro que oculta al jugador local cuando la cámara está en otra entidad); iconos propios en la barra de botones (dibujados con cuadrados, sin archivos de imagen) y la barra pasa a una segunda columna en pantallas bajas; las ventanas se ajustan al ancho y al alto de la pantalla; Configuración real y Editor de área real (coordenadas de las dos esquinas, mover cada cara, intercambiar, usar mi posición, nombre del área para guardarla, Mayús mueve de 8 en 8). La tecla de guardar pasó de O (abre la pantalla de amigos del juego) a K.
- **Fallos corregidos tras la prueba de Daniel (2026-10-14):** la causa de que el editor se cerrara al cargar un schematic (y de varios comportamientos raros de los clics con el cursor libre) era que en esta versión de Minecraft los botones del ratón se numeran 1 (izquierdo), 2 (central) y 3 (derecho) y el código usaba 0 y 1: un clic izquierdo en el mundo contaba como clic derecho ("terminar"). Ahora se usan las constantes del juego (`InputConstants.MOUSE_BUTTON_LEFT/RIGHT`). Además: un clic sobre una selección terminada la quita sin empezar otra; la selección pertenece al modo Seleccionar (desaparece al salir de él y solo se dibuja en él); en el modo Mover un clic sobre un holograma coge ese holograma (el que apunta la cruceta), no el último que se usó, y sigue desde donde estaba sin saltar.

## 15. Tareas pendientes críticas (apuntadas el 2026-10-15)

Origen: documento de Daniel `Escritorio\PREGUNTAS A CLAUDE\doc preguntas.docx` (con capturas). Daniel las considera **críticas**: van por delante del resto del plan (fase 3 incluida). El documento repetía los números 6 y 7; aquí están renumeradas de T1 a T10, con la numeración original entre paréntesis.

Estado de cada una: **[ ] pendiente**.

### Ventanas y barra de botones

- **[x] T1 (Tarea 1): ventanas a pantalla completa y sin estorbos. HECHA (2026-10-15).**
  - Al abrir cualquier ventana de un botón lateral se **oculta la leyenda de teclas** que queda detrás y también **la barra de botones** (como si se hubiera pulsado Tab, siempre).
  - **Aumentar bastante la transparencia** del fondo negro de la ventana para ver mejor lo que hay detrás.
  - **Aprovechar el máximo de pantalla disponible** (la ventana ya no tiene que dejar sitio a la barra).
- **[x] T2 (Tarea 4, relacionada con T1): cerrar una ventana vuelve a la cámara libre sin barra. HECHA (2026-10-15).**
  - Después de usar una opción lateral, cualquier cosa que cierre la ventana (botón de cerrar, Atrás, Esc, elegir un schematic para cargarlo…) devuelve a la **cámara libre G con la barra oculta**, como si se hubiera pulsado Tab. Ahora la ventana se cierra (bien) pero la barra se queda activa y hay que pulsar Tab otra vez, y es muy molesto.
  - Se aplica a **todas** las ventanas laterales sin excepción.
- **[x] T3 (Tarea 2): barra más pequeña y en una sola columna.**
  - Reducir el tamaño de los botones de la derecha y ponerlos **todos en vertical, en fila de uno**.
  - **Quitar los botones que no se usen o estén repetidos** (por ejemplo, los iconos de modo y de acción que se parecen: Copiar/Copiar, Borrar/Borrar la selección…; hay que revisar la lista entera).
- **[x] T4 (Tarea 7 primera): botones O, L y X de las colocaciones.** Poner **iconos** (ojo, candado, papelera) o algo que se entienda mejor que las letras.
- **[x] T5: pantallas más parecidas a las de Litematica** en diseño.
  - Fijarse en que se puede **introducir a mano la coordenada** (cuadros de texto X, Y, Z con un botón ± al lado), cosa que el Editor de área actual no permite (solo − y +).
  - Referencia: el Area Editor de Litematica (nombre de la selección con botón «Set», nombre de la caja de subregión, Corner 1 y Corner 2 con casilla, X/Y/Z en cuadros con ±, «Move to player», «Save Schematic», «Analyze Area»). La captura comparativa («Mi pantalla» / «La pantalla de referencia») está en el documento de Daniel.
  - Aplicarlo a todas las ventanas (colocaciones, configurar colocación, cargar, configuración).

### Cámara libre y holograma

- **[x] T6 (Tarea 5): ERROR CRÍTICO — los chunks no cargan como lo hacen normalmente en la cámara libre G.**
  - Estando en la cámara libre, el terreno no se carga/dibuja como en el juego normal.
  - Pistas a investigar: el servidor solo envía chunks alrededor del **jugador**, no de la cámara, así que al alejarse la cámara ve huecos; `Minecraft.smartCull` está apagado mientras vuela; el orden de compilación de secciones y el fundido de chunks dependen de la posición de la cámara; el `Marker` que hace de cámara no es el jugador.
  - Posibles salidas: limitar el vuelo al radio de chunks cargados alrededor del jugador (con aviso), o forzar que el renderizado priorice la cámara; en cualquier caso hay que **reproducirlo y medirlo en el cliente** antes de elegir.
- **[x] T7 (Tarea 3): mover un schematic grande («iron farm») se comporta de forma extraña.**
  - No parece detectar bien el movimiento del cursor y no lo mueve **desde el centro**, como antes.
  - Sospecha de Daniel: ocurre sobre todo cuando se está **flotando y alejado** con la cámara libre; tampoco parece detectar bien **poner hologramas a nivel del terreno**.
  - Regla pedida: **se debe partir siempre de donde está la cámara libre** (el holograma se coloca delante de la cámara, no según un punto lejano). Revisar el alcance del rayo (`REACH` de 256), el punto de aire cuando el rayo no toca nada (20 bloques) y el centrado en horizontal.
- **[x] T8 (Tarea 6 primera): cargar desde la ventana de colocaciones.** Al cargar un schematic desde la ventana «Schematic placements»: **cerrar la ventana, cerrar la barra derecha y poner el holograma en una posición delante de la cámara libre**; después ya se mueve como se quiera. (Mismo comportamiento que al cargar desde «Cargar schematic», que es el caso de T2.)
- **[x] T9 (Tarea 6 segunda): cámara libre ligeramente por delante del jugador** al entrar, para no ver el interior del jugador (ahora aparece justo en sus ojos).
- **[x] T10 (Tarea 7 segunda): copiar un holograma una sola vez.**
  - Tras copiar un holograma pulsando sobre él, se puede arrastrar y colocar con un clic; el problema es que **sigue mostrándose el holograma moviéndose**, como si se estuviera copiando otro.
  - Comportamiento pedido: copiarlo **una vez**, clic para ponerlo donde se hace clic y **ya no copia ninguno más** (queda como para moverlo) hasta que se haga clic en otro holograma.

- **[x] T11 (nueva, 2026-10-15): entidades y fluidos en los schematics.**
  - Los schematics deben cargar **entidades y fluidos por defecto**, mostrados **en estado pausado, sin animación** (una imagen fija: el agua y la lava como un fotograma, las entidades en reposo, sin moverse ni animarse).
  - Una **opción tipo casilla** (marcar o desmarcar) para **mostrar o no las entidades** incluidas en el holograma. Propuesta: está en Configuración (General) como ajuste global y, si se quiere, también por colocación en la ventana de configurar la colocación.
  - Estado actual: los fluidos no se dibujan (el holograma solo pinta modelos de bloque; el agua y la lava no tienen modelo de bloque, hace falta dibujarlos aparte con la textura estática) y las entidades y los datos de bloques se leen y se guardan en el archivo pero el holograma todavía no los muestra.
  - Cosas a resolver al hacerlo: dibujar las entidades solo como figura estática (sin ticks ni IA) y con el mismo aspecto fantasma translúcido; que cuenten para el tope de bloques por fotograma o tengan el suyo; y que se copien y se guarden con la selección (hoy la copia solo lee bloques).

### Orden propuesto

1. T6 (chunks) y T7 (mover desde la cámara): son fallos de base y condicionan todo lo demás.
2. T1, T2, T3 y T8 (flujo de ventanas y barra), que se resuelven juntos.
3. T10, T9 y T4.
   T11 (entidades y fluidos) después de T5, porque necesita su casilla en las ventanas rediseñadas.
4. T5 (rediseño de las ventanas con cuadros de coordenadas), que es lo más largo.

### Arreglos como consecuencia de T2 (2026-10-15)

- **[x] Una selección no debe desaparecer al abrir una ventana o pulsar un botón lateral.** Causa: al ocultar la barra (T1), el mismo clic que pulsaba el botón «Editor de área» caía después sobre el mundo (ya no había botón debajo) y, en el modo Seleccionar, un clic sobre una selección terminada la quita. Ahora se decide si el clic está sobre un botón o una ventana **antes** de procesarlo. Probado con un clic real: la selección se conserva.
- **[x] La selección sigue dibujándose con una ventana encima** (el fondo de las ventanas es transparente y la caja se dibuja en el mundo): comprobado que ya no desaparece, que era lo que impedía verla.
- **[x] Editor de área rediseñado como el de Litematica** (foto de referencia de Daniel): nombre de la selección con botón «Fijar», las dos esquinas una al lado de la otra con un **cuadro de texto por coordenada** (se puede escribir el número, o usar − y +, con Mayús de 8 en 8), «Mover al jugador» bajo cada esquina, «Guardar schematic», y debajo los botones de cada cara. Probado: escribir una coordenada mueve la esquina. Faltan de la referencia: modo de selección, modo de esquina, nombre de subregión y «Analizar área».
- **[x] Los cuadros de coordenadas del Editor de área no se veían sin selección** (2026-10-15): solo se creaban si ya había una selección. Ahora siempre están (seis cuadros) y, sin selección, muestran la posición del jugador; abrir la ventana no crea nada y escribir un número crea la selección.
- **[x] HECHO: «Mover al jugador» pasa a «Mover a la ubicación»** (2026-10-15, petición de Daniel). En el Editor de área, los botones de cada esquina ya no se llaman «Move to player» / «Mover al jugador» sino **«Move to location» / «Mover a la ubicación»**, y la esquina se pone en **donde está la cámara libre** (su posición actual, redondeada al bloque), no en la posición del cuerpo del jugador. Afecta al texto (`holoprint.area.move_to_player`), a `AreaEditorPanel` (usar `Freecam.eyePosition()` cuando la cámara libre está activa) y a las pruebas.

### Hecho el 2026-10-19 (T3 a T12)

Todo probado en el cliente real (superplano, sin sonido) con una prueba temporal ya retirada: 24 comprobaciones en verde.

- **T6 (chunks):** reproducido. El servidor solo envía chunks alrededor del **jugador**: con la cámara a 230 bloques no se veía nada (cielo) y a 120 se veía el borde del mundo cargado. La cámara libre queda **atada a un radio** alrededor del jugador (`Freecam.flightRadius` = mitad de la distancia de renderizado en bloques, mínimo 32; 96 con distancia 12) y en vertical a la altura del mundo + 64; al chocar con el límite sale un aviso en la barra de acción. `smartCull` sigue apagado mientras vuela.
- **T9:** la cámara aparece **2,5 bloques por delante** de los ojos del jugador (sin atravesar un muro) y se le ve el cuerpo.
- **T7 (mover desde la cámara):** causa: con la cámara lejos o arriba el rayo daba en terreno lejano y, como el holograma no bloquea el rayo, al apuntarlo se iba hacia atrás cada vez más lejos; además al cargar saltaba al punto apuntado. Ahora (`FollowCursor`, `Editor.aimHeld`): el holograma **parte de donde está** (delante de la cámara, apoyado en el suelo si lo hay debajo, más lejos cuanto más ancho) y **conserva su distancia a la cámara**; el terreno más cercano que esa distancia gana (se apoya en él) y un punto en el aire a menos de 6 bloques sobre el suelo cae al suelo. Al recoger uno colocado sigue sin saltar.
- **T8:** cargar desde «Colocaciones» ya cierra la ventana y la barra (cae en `closeAll`) y el holograma aparece delante de la cámara.
- **T10:** en el modo Copiar, un clic sobre un holograma saca **una** copia, el siguiente clic la deja donde está (queda como colocación activa) y no se hace ninguna más hasta otro clic sobre un holograma. Ctrl+V pega igual y aparece delante de la cámara.
- **T3:** la barra es **una sola columna** de botones de 18 px (se encogen en pantallas muy bajas, sin segunda columna) con solo 8 botones: los 4 modos y las 4 ventanas. Quitados Copiar, Cortar, Pegar, Borrar y Guardar (repetían los modos, las teclas Ctrl+C/X/V y el Editor de área); **Copiar, Cortar, Borrar y Guardar** están ahora en la fila de abajo del Editor de área (Cortar y Borrar solo en creativo). Pegar sigue en Ctrl+V.
- **T4:** en «Colocaciones» los botones O, L y X son iconos con ayuda al pasar el ratón: ojo (tachado en rojo si está oculta), candado (cerrado y dorado si está bloqueada) y papelera (`ui/IconButton`).
- **T5:** la ventana de configurar la colocación tiene la posición en **cuadros de texto X, Y, Z con − y +** (Mayús de 8 en 8), como el Editor de área; las demás ya seguían el estilo de Litematica (lista, barra lateral, cuadros de coordenadas).
- **T11:** los **fluidos** (agua y lava, no los bloques con agua) se dibujan como bloques de color liso y quietos (el agua con su azul, la lava con su naranja), sin las texturas animadas del juego; las **entidades** del schematic se crean como objetos reales fuera del mundo, sin ticks ni IA, y se dibujan en reposo (`hologram/GhostEntities`). Casilla **«Mostrar las entidades de los schematics»** en Configuración (General) y **«Entidades»** por colocación. Copiar y guardar una selección ahora incluyen las **entidades** del área (no jugadores) y el archivo `.litematic` las guarda y las vuelve a leer. Limitaciones: las entidades se ven opacas (no con el aspecto fantasma translúcido de los bloques), hasta 160 por fotograma, y la posición de una entidad se lee relativa a la esquina mínima de la región (puede fallar en regiones con tamaño negativo).
- **T12 (nueva, pedida el 2026-10-19):** la barra de experiencia y el número de nivel **no se dibujan mientras la cámara libre está activa** (`mixin/HudMixin`); la barra de vida y la hotbar no se tocan. Comprobado con captura.

Pendiente de revisar por Daniel con el ratón de verdad: la sensación del seguimiento del cursor (T7) y el radio de vuelo (T6), que se puede cambiar si se queda corto.

### Arreglos tras la prueba de Daniel (2026-10-19, tarde)

Probados en el cliente con su `iron farm.litematic` real (56 x 76 x 55, 35 entidades).

- **Las entidades del schematic no se veían (aldeanos del iron farm):** se creaban y se dibujaban, pero en un sitio equivocado. En el `.litematic` la posición de una entidad es relativa al campo `Position` de la región, y con tamaño **negativo** eso no es la esquina mínima. `Region.entityBase()` guarda ese `Position` y el holograma lo usa; el escritor corrige las posiciones al guardar. Ahora se ven los aldeanos, el golem y el zombi en su sitio.
- **Entidades translúcidas como el holograma:** las entidades vivas (aldeanos, animales, armor stands, golems...) se dibujan con la ruta de «invisible pero visible» del juego y un mixin (`LivingEntityRendererMixin`) cambia su color fijo por el color y la opacidad del holograma. Las que no son seres vivos (marcos, objetos, cuadros, vagonetas) siguen opacas.
- **Dirección de los fluidos:** flechas sobre las caras de arriba del agua y la lava que corren (hacia los vecinos más bajos, como el juego) y flechas hacia abajo en los lados de los bloques que caen (`Hologram.Entry.flow`).
- **Leyenda más pequeña:** se escala según la pantalla (máximo 0,8 y nunca más del 40 % del ancho ni el 30 % del alto).
- **Al volver a entrar con G el holograma ya no se arrastra:** `Editor.start` entra siempre en modo «fijo». Un schematic recién cargado sí empieza siguiendo al cursor, aunque el editor estuviera en otro modo (antes, cargar desde el modo Seleccionar lo dejaba fijo).
- **Holograma descentrado de la cruceta:** ya no se guarda el desplazamiento del primer clic. El centro del holograma va bajo la cruceta (en el suelo apoya la base y en el aire se centra en vertical) y, si el objetivo da un salto (coger uno desde el lado, apoyarse en el suelo), se desliza hasta él en unos ticks en vez de teletransportarse.
- **Flechas de fluidos (2026-10-19, petición de Daniel):** vuelven a verse **gris oscuro** (`#4A4A44`, como en la captura que le gustó) y el color es una opción: **Configuración > Aspecto > «Color de las flechas de fluidos»** (`HoloprintConfig.flowColor`, caja hexadecimal con muestra y reinicio). Mismo color para agua y lava; la opacidad sigue la del holograma con un mínimo de 0xD8.

### Fase 3 (colocar con el estado correcto y relleno) y 4.1 (formatos): hecho el 2026-10-19

Probado en el cliente real (superplano, sin sonido, jugador real en supervivencia y creativo) con una prueba temporal ya retirada: 21 comprobaciones en verde.

**4.1 Schematics**
- **Formatos:** `.litematic` (versiones 1 a 7, ya estaba), **`.schem`** (Sponge v1, v2 y v3: `SpongeFormat`) y **`.schematic`** (MCEdit/Schematica de antes de 1.13: `LegacyFormat`). El selector de archivos y «Cargar» los aceptan todos; `SchematicFormats` elige el lector por la extensión. Para los dos formatos nuevos la información del archivo (tamaño, bloques) sale de leerlo entero.
- **Versiones antiguas:** la paleta, los bloques con datos y las entidades pasan por el actualizador de datos del juego desde la versión que dice el archivo (probado: un `grass_path` de 1.16 sale como `dirt_path`). En `.schematic` cada id numérico y valor de datos se convierte con la tabla de aplanado del propio juego (`BlockStateData.getTag`); las ids antiguas de bloques con datos (`Chest`) y de entidades (`Villager`) se renombran con las tablas del juego, que el actualizador solo aplica a chunks enteros (por eso se leen por reflexión/tabla pública).
- **Bloques que no existen:** se cargan como aire y el aviso dice **cuántos bloques** eran, de cuántos tipos y los nombres de los tres primeros (`PaletteReader`, `Schematic.unknownCount`).

**Fase 3: colocar y rellenar (`placement/EasyPlace`)**
- Con la cámara libre apagada y un holograma visible, **usar un objeto** (clic derecho) mirando a un bloque que el holograma pide y que falta **coloca ese bloque con su estado** (orientación, mitad, eje...). No hace falta apuntar a la cara exacta: se sigue la línea de visión y se toma el primer hueco del holograma que se puede rellenar. El primer bloque de la línea que no se puede tener (falta el objeto) detiene la búsqueda y avisa.
- **Relleno:** **mantener pulsado** sigue con los siguientes bloques al alcance, de la capa más baja a la más alta y de más cerca a más lejos (así cada bloque ya tiene en qué apoyarse). Un bloque cada 3 ticks. Si se suelta, el siguiente clic vuelve a ser normal.
- **Cómo logra el estado:** para cada hueco prueba con el código de colocación del propio juego (`BlockItem.getPlacementState`, vía `mixin/BlockItemAccessor`) los vecinos sobre los que apoyar, la mitad de la cara que se pulsa (losas, escaleras, trampillas) y 13 direcciones de mirada, y se queda con la que da el estado pedido en las propiedades que decide la colocación (`facing`, `half`, `axis`, `face`, `orientation`, y `type` en losas). Si ninguna lo consigue desde ahí, el bloque se deja para más tarde (cuando haya vecinos). Las conexiones, el agua, las esquinas de escaleras y los goznes de puertas los resuelve el juego.
- **Dirección de mirada:** los bloques que miran hacia donde mira el jugador (dispensadores, pistones, escaleras...) necesitan que el servidor vea esa dirección. Se manda **solo para ese clic** (un paquete de rotación, que se repite cada tick mientras espera y se devuelve justo después de colocar); el servidor tarda un tick en actualizar la cabeza del jugador, así que el clic espera 2 ticks. La rotación real del jugador y su cámara no cambian.
- **Inventario:** cada bloque sale del inventario del jugador y respeta el alcance normal; si está en el inventario y no en la barra rápida se cambia con un clic de intercambio normal. En creativo el bloque se pone en la mano como hace «elegir bloque». Si falta el objeto no se coloca otra cosa: un aviso en la barra de acción (uno cada 1,5 s como mucho, con hasta 3 nombres) y el clic se queda sin efecto.
- **Qué no se coloca:** fluidos, bloques sin objeto (agua en calderos...), la mitad de arriba de puertas y plantas altas y la cabecera de las camas (las crea la primera mitad). Los bloques sobre los que se apoya no pueden ser interactivos (cofres, puertas, mesas...). Las losas dobles y los cofres dobles no se completan solos: se coloca la primera pieza.
- **Ajustes:** Configuración > General: «Colocar el bloque que pide el holograma» y «Seguir rellenando al mantener pulsado»; además la tecla «Activar / desactivar la colocación fácil» (sin asignar de serie) para encenderlo y apagarlo sin abrir menús.
- **Pendiente:** lista de materiales que faltan por tandas (fase 5), contenido de cofres y carteles al colocar, el asistente de «mover» en supervivencia (sección 14), y probar con un mando.
- **Colocar sin apoyo (2026-10-19, petición de Daniel):** con la colocación fácil activada, si un hueco del holograma no tiene ningún bloque al lado donde apoyarlo, se coloca igualmente haciendo clic sobre el propio hueco (el servidor coloca el bloque donde se le dice). Va primero siempre el apoyo normal y solo si no lo hay se usa esto. Probado con un holograma flotando en el aire: piedra, escalera boca abajo mirando al norte y dispensador mirando al oeste, todo bien. Ajuste en Configuración > General: «Colocar bloques aunque no haya nada al lado». Aviso: un servidor con anticheat puede no aceptarlo.
- **Qué bloque se coloca (2026-10-19, tras la prueba de Daniel con el iron farm):** se comprobó con el archivo real en creativo que cada bloque colocado coincide con lo que pide el holograma en esa celda (0 diferencias); lo que confundía es **qué celda se elige**: la primera celda pendiente que cruza la mirada puede ser una más cercana y translúcida (otro tipo de bloque) delante de la que se ve. Ahora (1) la celda elegida se **marca con un contorno dorado y una etiqueta «Colocar: <bloque>»** bajo la cruceta (`EasyPlace.targetCell`, `SelectionRenderer`), y (2) **el relleno al mantener pulsado solo sigue con el mismo tipo de bloque** que el primero (`fillBlock`); para otro tipo se apunta a otra celda. Un clic sin celda pendiente en la línea de visión es un clic normal del juego y coloca el bloque que se lleva en la mano.

### Fase 5: verificador y lista de materiales (2026-10-19)

Probado en el cliente real (superplano, sin sonido) con un holograma pequeño y con el iron farm.

- **Verificador (`hologram/Verifier`):** compara cada holograma visible con el mundo, un poco cada tick (hasta 40.000 celdas por tick entre todos; el iron farm, 234.000 celdas, se revisa en 6 ticks) y repite cada 2 segundos. Solo mira chunks que el cliente tiene; los demás cuentan aparte como «sin cargar». Por cada celda: **construido** (el bloque pedido, y en los bloques que se colocan girando, el mismo estado en `facing`, `half`, `axis`, `face`, `orientation` y `type` de las losas), **falta** (aire o algo reemplazable), **otro bloque** o **mismo bloque girado al revés**. Se descarta el resultado si el holograma se mueve o gira.
- **Ocultar lo ya construido:** el holograma no dibuja los bloques que ya están puestos en el mundo, así que solo se ve lo que queda por hacer (ajuste «Ocultar lo que ya está construido», activado).
- **Marcas:** contorno **rojo** en los bloques que son otro bloque, **naranja** en los que están girados al revés y, si se activa, **azul** donde el holograma no pide nada y hay un bloque (se ve el terreno, por eso viene apagado). Hasta 1.500 contornos por fotograma, los de dentro de la distancia de dibujo.
- **Lista de materiales (botón nuevo de la barra, `MaterialsPanel`):** por tipo de bloque (o de objeto, así la antorcha de pared cuenta como antorcha): cuántos pide, cuántos hay ya, cuántos faltan y cuántos lleva el jugador, ordenado por lo que falta. Una losa doble cuenta 2, la nieve sus capas, las velas sus velas; la mitad de arriba de puertas y plantas altas, la cabecera de las camas y los fluidos no cuentan. Se actualiza sola mientras está abierta («Comprobar» fuerza otra pasada), «Solo faltan» oculta lo que ya está completo y «Guardar texto» escribe `<nombre>_materials.txt` en la carpeta `schematics`.
- **Ajustes (Configuración > General):** «Ocultar lo que ya está construido», «Marcar los bloques equivocados» y «Marcar bloques donde no se pide nada».
- **Pendiente de la fase 5:** icono y descripción de Modrinth y la publicación (cuando Daniel lo decida).

### Pulido de la fase 5 (2026-10-19)

- **Color de bioma:** hierba, hojas, helechos, caña... toman el tinte del propio juego (`BlockColors.getTintSource(...).colorInWorld`) leído en la posición real del mundo. Ajuste en Aspecto: «Color de bioma en hierba y hojas» (activo de serie). El agua y la lava siguen con su color plano fijo.
- **Caché de mallas por sección:** cada celda de 16 bloques guarda (`SectionMesh`) sus caras visibles ya calculadas (vértices, uv, tinte, luz, normal, cara de recorte) y cada frame solo las repite, descartando las que miran al lado contrario de la cámara y las ya construidas. Máximo 24 celdas nuevas por frame; tope de 192 MB con descarte de las menos usadas y de las que llevan unos 1000 frames sin dibujarse. Las celdas con tinte se rehacen al mover el holograma y cada 5 s (para chunks que cargan después). Probado: la granja de hierro (6083 bloques a la vista) a 60 fps sin errores.
- **Cofres, carteles y demás bloques con datos:** se crean objetos `BlockEntity` reales desde los datos guardados (`GhostBlockEntities`, nunca se añaden al mundo ni se ejecutan) y se dibujan con el renderizador del juego: cofres, cofres de ender, carteles con su texto, estandartes, cabezas, shulkers, camas... Se giran y reflejan como el resto. Ajuste en Aspecto: «Mostrar cofres, carteles, estandartes y cabezas». Límite 400 por frame y se omiten los ya construidos.
- **Límites:** salen opacos (no translúcidos como los bloques); los objetos dentro de los cofres no se muestran (solo el cofre); el verificador no compara su contenido.

### Fixes pedidos por Daniel (2026-10-19, tras probar el pulido de la fase 5)

Se hacen todos, en este orden. Marcados con **[hecho]** cuando estén terminados y probados.

- **FIX1 (muy importante): la colocación fácil pone un bloque distinto al que dice.** Con piedra lisa en la mano y el rótulo «Colocar: Piedra lisa» sobre la celda, el clic colocó un comparador (y consumió el stack equivocado). Tiene que colocarse siempre exactamente el bloque de la celda marcada, y además tiene que poder colocarse encima, sin problemas, la redstone (polvo, comparadores, repetidores...) que va sobre un bloque, también sobre bloques recién colocados con la colocación fácil.
- **FIX2: respetar puertas, trampillas, portones y demás.** Se colocan exactamente como en el holograma, **abiertos o cerrados según diga el esquema** (hoy salen siempre cerrados). Además la colocación fácil no puede abrir ni cerrar por error ningún bloque ya colocado: con ella activa, un clic derecho sobre una puerta, trampilla o portón no debe cambiar su apertura (solo lo cambia la propia colocación fácil cuando el esquema lo pide). Se respeta siempre la orientación de todo.
- **FIX3: cancelar «Mover» deja el holograma donde estaba.** Si ya hay un esquema colocado y se pulsa Mover pero no se confirma con **clic izquierdo**, al cancelar (Esc, cerrar el editor, cambiar de modo) vuelve a su sitio. Solo el clic izquierdo confirma la nueva posición.
- **FIX4: `rustic-survival-house-8c3ec803.litematic` no se puede leer («Not in GZIP format»).** El archivo es NBT **sin comprimir** (no gzip). Hay que aceptar también NBT sin comprimir (y, de paso, zlib suelto) en todos los formatos.

**Estado de los fixes (2026-10-19), todos hechos y probados en el cliente real:**

- **FIX1 [hecho]:** la causa era que el rótulo mostraba la primera celda pendiente de la línea de visión pero el clic colocaba la primera con plan, que podía ser otra distinta (en una prueba con redstone sobre piedra lisa el rótulo decía «redstone» y se colocaba la piedra de debajo). Ahora el rótulo y el contorno son siempre la celda que se coloca; si la celda apuntada no se puede rellenar todavía, la búsqueda solo pasa al bloque justo detrás (a 2 bloques como mucho), y si no hay ninguno el rótulo sale en gris «todavía no se puede colocar». Probado: piedra lisa, polvo de redstone (con sus conexiones), repetidor (`facing=east`) y comparador (`facing=north`, modo comparar) se colocan en la celda marcada, también sobre bloques recién puestos.
- **FIX2 [hecho]:** puertas, trampillas y portones se colocan y, si el esquema los tiene abiertos, se abren con un clic más (no con Mayús pulsado, porque el juego colocaría contra ellos). Los portones se abren mirando hacia donde miran, porque si no el juego les gira la orientación (comprobado). Una puerta, trampilla o portón que ya está como el esquema no se abre ni se cierra con un clic suelto (aviso en pantalla). Las puertas prueban ambos lados al clicar para acertar la bisagra. El verificador marca en naranja los abiertos/cerrados al revés. Probado con puerta de abeto, trampilla y portón de roble.
- **FIX3 [hecho]:** `MoveTool` recuerda el holograma que se lleva y, si se sale sin confirmar con clic izquierdo (clic derecho, cancelar, otro modo, cerrar el editor o elegir otra colocación), lo devuelve a su sitio. Un holograma recién cargado se queda donde se deja. Probado.
- **FIX4 [hecho]:** `SchematicFormats.readNbt` acepta gzip, zlib y NBT sin comprimir en los tres formatos. `rustic-survival-house` carga (50x32x44, 9471 bloques, sin bloques desconocidos).

### Mejoras y fallos nuevos (2026-10-19, tras probar el jar de los fixes)

- **MEJORA: guardar las colocaciones por mundo.** Hoy los hologramas fijados viven solo en memoria y se pierden al cerrar el juego (hay que reiniciar para cambiar el jar). Hay que guardar por mundo (o servidor) qué esquema, posición, giro, espejo, nombre y opciones tiene cada colocación, y restaurarlas al entrar; con un aviso si falta el archivo del esquema. **[pendiente]**
- **FIX5 (muy importante): la colocación fácil coloca un bloque que no es** (el que se colocó justo antes, aunque ese no vaya en ese lugar). Hay que reproducirlo en supervivencia con varios bloques en la barra y en el inventario, encontrar la causa y asegurar que el bloque colocado es siempre el que pide la celda marcada. **[pendiente]**
- **FIX6: retraso al romper o cambiar bloques.** Al romper bloques colocados, durante unos segundos no se ve su holograma (los bloques «ya construidos» se ocultan con una comprobación periódica), y el estado abierto/cerrado de puertas tarda en detectarse. Los cambios del mundo tienen que reflejarse al instante en el holograma y en los contornos. **[pendiente]**

**Estado al cortarse la sesión por límite de uso (2026-10-19):**
- FIX6 (cambios del mundo al instante): código escrito y compilado (`LevelMixin` + `Verifier.blockChanged` + recomprobación a los 2 ticks), **sin probar en el juego**. Va incluido en el último jar.
- FIX5 (bloque equivocado): **sin arreglar**. Hipótesis: si la celda apuntada no se puede colocar, `EasyPlace.before` devuelve false y el juego coloca el bloque de la mano contra lo apuntado. Arreglo previsto: con la colocación fácil activa, cancelar el clic si el punto donde caería el bloque es una celda donde el holograma pide otro bloque (salvo aire y fluidos). Receta del usuario para reproducirlo: colocar valla carmesí o bloque de slime y luego usar la colocación fácil donde va piedra lisa.
- Mejora pendiente: guardar las colocaciones por mundo.

**Estado final de los fallos nuevos (2026-10-19):**
- **FIX5 [hecho, probado]:** causa real: `equip` cambiaba la ranura de la barra solo en el cliente y el juego avisa al servidor en el tick siguiente, después del clic; el servidor colocaba el bloque que creía tener en la mano (el anterior, p. ej. slime en una celda de piedra lisa). Ahora se envía `ServerboundSetCarriedItemPacket` al instante. Además, si no hay nada que colocar y el clic caería en una celda donde el holograma pide otro bloque, se cancela con aviso (`wouldPlaceWrongBlock`). Probado en supervivencia: slime → portón carmesí → piedra lisa, cada bloque en su celda. **Abierto:** con el bloque venido de la mochila (intercambio) el primer clic de piedra tras el portón a veces no coloca nada (el segundo sí); no pone un bloque equivocado, causa sin investigar.
- **FIX6 [hecho, probado]:** `LevelMixin` + `Verifier.blockChanged`: cada cambio de bloque del mundo actualiza al instante lo oculto y los contornos rojo/naranja y relanza la comprobación completa a los 2 ticks. Probado: colocado, roto, bloque incorrecto y portón cerrado en vez de abierto se reflejan en 2 ticks.
- **Mejora pendiente:** guardar las colocaciones por mundo.

### Guardar colocaciones por mundo y fallo de la piedra desde la mochila (2026-10-19)

- **Colocaciones por mundo [hecho, probado]:** `placement/PlacementStore` guarda en `config/holoprint/worlds/<mundo>/placements.json` (mundo de un jugador: nombre de su carpeta; servidor: su dirección) archivo, posición, giros, espejo, nombre, visible, activa, bloqueada, mostrar entidades y cuál es la activa. Se escribe a los 20 ticks de cualquier cambio y al salir del mundo; al entrar se restauran (leyendo los archivos en segundo plano, con un aviso «Se han restaurado N colocaciones»). Al salir del mundo se vacían los hologramas, para que un mundo no muestre los de otro. Una colocación que solo existe en memoria (pegada) se guarda como `copy-*.litematic` junto a la lista. Si falta el archivo, avisa y conserva la entrada por si vuelve. Prueba `Persistence` (archivo y copia en memoria, salir y volver a entrar, 7/7).
- **Piedra desde la mochila [no era un fallo del mod]:** se vio en el arnés que los clics de la ronda 2 sobre piedra ya puesta hacían que el juego colocara otra piedra delante (comportamiento normal de un clic sobre un bloque ya construido sin celda pendiente) y esa tapaba la línea de visión de las capas siguientes. `PlaceMix` limpia ahora todo el volumen en cada capa: 18/18. Si Daniel lo vuelve a ver en el juego, mirar si hay un bloque suelto en medio.

### Cosas por añadir bastante importantes (pedidas el 2026-10-19)

- **Vista por capas, como Litematica:** poder cambiar cómo se ve el esquema: mostrar **solo una capa** (altura) y subirla o bajarla con **+ / -** (teclas configurables en el apartado de teclas), y volver a **mostrar todo**. Idea: modos «todo», «una capa», «de abajo hasta la capa» y «de la capa hacia arriba»; la capa elegida se marca en la barra y se guarda con la colocación. **[pendiente]**
- **Mover una construcción en supervivencia (idea de Daniel):** seleccionar una zona de una construcción, llevarla con la cámara a otro sitio y que un clic izquierdo la construya entera. Respuesta: sin comandos y sin ser operador no se puede hacer al instante ni de golpe, porque un mod de cliente solo puede romper y colocar bloques uno a uno por el juego (con su tiempo de rotura, alcance y consumo de objetos), y el servidor rechaza lo demás. Lo posible es un asistente: al confirmar, rompe lo original y coloca en el destino lo que alcance, con los materiales del inventario, el jugador se va acercando. Si el jugador es operador (o en un mundo con trucos), se podría ofrecer como opción un `/clone` instantáneo. Por decidir con Daniel.

### FIX7: cofres y demás objetos con modelo propio salían opacos (2026-10-19)

- Los cofres, cofres de ender, camas, carteles... se dibujan con el renderizador del juego, que los pedía opacos. `TranslucentCollector` envuelve el recolector de dibujado y cambia esos modelos a translúcidos con el tinte y la opacidad del holograma. Probado con la granja de árboles (cofres): ya se ven translúcidos. Los modelos que el juego entrega ya con su tipo de dibujo (algunos estandartes y cabezas) siguen opacos.
- El **andamio** no era opaco: se dibuja con la misma opacidad que el resto (se midió el color de un píxel); parece más sólido porque su textura es casi toda opaca y el modelo tiene capa interior.
- **Hormigón negro de la casa rústica:** no es un fallo. El archivo `rustic-survival-house` trae 685 bloques de hormigón negro y 185 de blanco (se ven en su paleta): Litematica los dibuja sólidos y aquí salen translúcidos como todo.

### Mover una construcción: decisión y diseño previo (2026-10-19)

**Decisión de Daniel:** si el mundo tiene trucos (el jugador tiene permisos), se usa `/clone`; si no, el asistente de supervivencia. Antes de implementar hay que cerrar qué pasa con los materiales.

- **Los materiales de la propia construcción se reutilizan** (es lo que distingue mover de copiar): al romper el original, lo que suelta se recoge y se usa en el destino. Límites: lo que no suelta su propio bloque (cristal, hielo, hojas, piedra que da adoquín, hierba que da tierra, minerales...) solo se recupera con una herramienta con toque de seda; el asistente la elige sola si la lleva en el inventario. Lo contrario cuenta como material que falta.
- **Solo se toca lo que cambia:** las celdas donde origen y destino ya tienen el mismo bloque (al solaparse) no se rompen ni se colocan.
- **Plan previo a empezar:** resumen con lo que hace falta, lo que se recuperará y lo que falta; si falta algo, se avisa antes de romper nada.
- **Por tandas, para no dejar la obra destruida sin poder reconstruirla:** se rompe una tanda (una capa o una sección), se recoge y se coloca en el destino, y se sigue. Si en una tanda faltan materiales, se **pausa** con un solo aviso agrupado (sin avisos repetidos); lo que queda sin romper sigue intacto en el origen, y al conseguir los materiales se reanuda desde ese punto (el holograma conserva lo pendiente).
- **Los objetos recogidos hay que pisarlos** (el jugador los recoge al acercarse): el asistente trabaja lo que alcanza y guía hacia la zona siguiente.
- **Contenido de cofres y similares:** al romper el cofre sus objetos caen al suelo; en la primera versión se avisa y no se recolocan solos.
- **Ritmo:** el que permita el juego (tiempo de rotura de cada bloque con su herramienta); en servidores, respetando alcance y ritmo normales para no saltar la protección.
- **`/clone` (solo con permisos, nivel 2):** copia y borra el origen (`replace move`) conservando datos de bloques (cofres con contenido); se parte en trozos de hasta 32768 bloques (límite por defecto). No admite giro ni espejo: con el holograma girado o espejado se usa el asistente. Necesita chunks cargados.

### Mover con /clone y construir el holograma entero (2026-10-19)

Decidido con Daniel: nada de asistente de supervivencia por ahora; todo lo que cambia el mundo de golpe exige **trucos** (permisos de operador; un mundo abierto a LAN con trucos vale y no deja marca en el mundo: el permiso es un valor en memoria del servidor integrado, no se guarda en `level.dat`). Sin trucos el mod sigue sin tocar el mundo.

- **Mover una zona real (Ctrl+X con trucos) [hecho, probado]:** con una zona seleccionada, Cortar la recoge (un holograma encima de ella que sigue al puntero); el clic izquierdo mueve los bloques reales con `/clone ... replace move` (conserva cofres con su contenido), en cajas de hasta 32x32x32, ordenadas para que ninguna caiga sobre otra aún sin mover (si no hay orden posible avisa), y teletransporta con `execute as @e[...] at @s run tp` las entidades de la zona (no jugadores). `/clone` no gira ni refleja: con giro o espejo se niega y la zona sigue en la mano. Clic derecho o Atrás cancelan sin tocar nada. Sin trucos, Cortar sigue siendo copiar y borrar solo en creativo. Prueba `CloneMove` 13/13 (bloques, cofre con diamante, armor stand, solape de 2 bloques).
- **Construir el holograma entero (tecla B, configurable) [hecho, probado]:** es una cosa distinta de copiar el holograma. Pide pulsar dos veces, y con trucos escribe todas las celdas del holograma activo en el mundo, en su estado exacto y con los datos de cofres/carteles, sin gastar materiales, sin avisar a los vecinos (el holograma ya trae las conexiones) y en tandas de 20.000 bloques por tick del servidor. Los bloques los escribe el servidor del mundo, así que solo funciona en un mundo propio (un jugador o LAN); en servidores ajenos avisa. El aire del holograma no borra nada del mundo. Las entidades del esquema no se construyen. Pruebas `BuildAll` 4/4 y `BuildFile` (la casa rústica, 9471 bloques, 0 diferencias).
- **Límites conocidos:** al copiar una zona del mundo solo se copian los estados de los bloques (el contenido de los cofres no), así que construir esa copia deja los cofres vacíos; mover con `/clone` sí lo conserva.
- **Huellas:** en la prueba el registro del juego no muestra una línea de «comando ejecutado»; los cambios al mundo, claro, quedan.

### Pendiente para más adelante (anotado el 2026-10-19, a petición de Daniel)

Todo lo que se dejó a medias o con límites. Marcar **[hecho]** al terminarlo.

**Pedido por Daniel**
- **Vista por capas, como Litematica** (importante): una sola capa, subir/bajar con + / - (teclas configurables), volver a mostrar todo; modos «todo», «una capa», «de abajo hasta la capa», «de la capa hacia arriba». **[pendiente]**
- **Controlify (fase 6):** integración y prueba con mando real; al final. **[pendiente]**
- **Publicación en Modrinth:** icono, descripción y subir; solo cuando Daniel lo decida. **[pendiente]**
- **Licencia:** «ya veremos» (hoy CC0 por defecto). **[por decidir]**

**Límites y cosas a medias**
- Los objetos dentro de cofres no se ven en el holograma y el verificador no compara el contenido. **[pendiente]**
- Copiar una zona del mundo solo copia los estados de los bloques: los cofres de la copia salen vacíos (y al construirla con B también). Mover con \`/clone\` sí los conserva. **[pendiente]**
- Construir con B no construye las entidades del esquema (marcos, armor stands...) y solo funciona en un mundo propio (un jugador o LAN), no en servidores ajenos. **[pendiente]**
- Mover con \`/clone\` no admite giro ni espejo. **[pendiente]**
- Colocación fácil: ordenar por tandas la lista de materiales que faltan y afinar los bloques de varias partes (camas, puertas). **[pendiente]**
- Construir (B) solo tiene tecla: falta un botón en la barra o en el panel de la colocación. **[pendiente]**
- Arrastrar caras de la selección (fase 4). **[pendiente]**
- Los modelos que el juego entrega ya con su tipo de dibujo (algunos estandartes y cabezas) siguen opacos. **[pendiente]**
- Si Daniel vuelve a ver «el primer clic de piedra desde la mochila no coloca nada», mirar si hay un bloque suelto delante. **[a vigilar]**

### Velocidad del relleno (2026-10-19)

- **[hecho, probado]** Ajuste **Límites > Velocidad del relleno (bloques por segundo)**, de 1 a 20 (por defecto 7, como antes). El juego solo repite un botón mantenido cada 4 ticks (5 por segundo como mucho), así que \`EasyPlace.driveFill\` hace el clic siguiente él mismo en cuanto el ajuste lo permite. Tope: un bloque por tick (20 por segundo), para no pasarse de lo razonable en servidores; mantener el relleno desactivable con «Seguir rellenando al mantener pulsado». Prueba \`FillSpeed\`: 1/s → 2 bloques, 5/s → 9, 20/s → 31 en 30 ticks.

### Tarea pedida por Daniel (2026-10-19), después de los fixes del repetidor y los carteles
- **Transparencia del holograma y fondo marino:** ¿se puede evitar que, por ser translúcido, el holograma deje ver el fondo marino a través de él (foto del holograma sobre el agua)? **[pendiente, investigar]**

### Repetidores, ajustes de redstone, carteles y holograma sólido (2026-10-19)

- **Orientación mal puesta a veces [hecho, probado]:** reproducido con la prueba \`Orientation\` (repetidor, comparador, observador, pistón y escaleras, desde 20 ángulos alrededor, con y sin mover el ratón justo antes del clic): fallaba solo cuando el ratón se había movido el mismo tick del clic y la mirada caía cerca de un límite entre dos orientaciones (45°, 315°...): el juego avisa al servidor de la nueva mirada en el tick siguiente y el servidor aún tenía la vieja. Ahora los bloques cuya orientación depende de hacia dónde miras (\`facing\`, \`orientation\`, \`rotation\`) usan la mirada real solo si lleva 2 ticks quieta (\`EasyPlace.lookSettled\`); si no, se envía una mirada centrada y se espera como antes. 120/120 comprobaciones.
- **Ajustes que se cambian usando el bloque [hecho, probado]:** retardo del repetidor (1 a 4, un clic suma uno), modo del comparador, nota del bloque de notas y interruptor invertido del sensor de luz (\`Verifier.settingClicks/adjustable\`). Si difiere del holograma se marca con **contorno amarillo** (\`Result.wrongSetting\`, aparece también en la lista de materiales) y la colocación fácil lo ajusta con los clics necesarios (etiqueta «Ajustar como en el esquema»); un clic suelto sobre uno que ya está bien no lo cambia (aviso). Los demás bloques de redstone (observador, pistón, tolva, dispensador...) solo tienen orientación, que ya se comprueba y se coloca bien. Prueba \`Settings\` 8/8.
- **Carteles con su texto [hecho, probado]:** al colocar con la colocación fácil un cartel cuyo cartel del holograma tiene texto, se escribe el texto en el editor de carteles del juego y se cierra solo (se envía como si lo hubieras escrito). Solo el texto de delante; el de atrás, el encerado, el tinte y el brillo no se hacen solos. Prueba \`SignText\` 6/6 (cartel de pie y de pared, con tilde).
- **Holograma sólido [hecho]:** respuesta a «¿se puede evitar que el fondo marino se vea a través del holograma?»: se ve porque el holograma es translúcido (hasta ahora solo se podía subir la opacidad al 100 %). Nuevo ajuste **Aspecto > Holograma sólido** y tecla (sin asignar de serie) «Holograma sólido / translúcido», que dibuja los hologramas sin transparencia sea cual sea la opacidad; como lo ya construido se oculta, solo se ve lo que falta por hacer. Comprobado con capturas.
- **Aviso «límite de altura 319»:** no es de Holoprint (el mod no tiene nada con 319): es el aviso del propio juego (límite de construcción) cuando un clic intenta poner un bloque en la altura máxima del mundo. Pendiente de saber en qué situación salió.

- **Ajuste posterior (2026-10-19, noche):** al fingir la mirada en un clic de la colocación fácil ahora también se gira la cabeza del jugador (\`setYHeadRot\`), porque el juego lee de la cabeza hacia dónde se orienta un bloque (pistón, observador...) y la predicción del cliente salía con la mirada vieja (visto en la prueba: \`head=50\` con \`yaw=270\`); y el cliente podía dejar un bloque mal orientado solo en pantalla. Un fallo raro (1 de ~100, solo el pistón con el ratón movido en el mismo tick) no se pudo reproducir después de esto: 3 tandas de 26 y la batería completa, 0 fallos.
- **Aviso rojo «límite de altura 319»:** Daniel dice que salió mientras probaba yo. Las pruebas guardan ahora todos los mensajes del juego (también los de la barra de acción) como líneas \`[DEV] MSG\`; en la batería completa no apareció ninguno con 319. Sigue sin causa conocida.

### Menú de configuración: nombres, ayudas y colores (2026-10-19)

- **Nombres nuevos:** «Colocación fácil» → **Colocación asistida**; «Seguir rellenando al mantener pulsado» → **Relleno automático**; y más cortos y claros los demás (Mostrar hologramas, Marcar errores, Ocultar lo ya construido, Colocar sin bloque de apoyo...). También cambian los mensajes y el nombre de la tecla.
- **Ayuda al pasar el ratón:** cada ajuste tiene un texto explicativo (\`<clave>.tip\`) que sale al reposar el puntero sobre el botón o sobre el nombre del ajuste (colores, números, deslizador); las etiquetas sin botón usan una zona invisible (\`HoverZone\`).
- **ON en verde y OFF en rojo** en todos los interruptores (dos ajustes no mostraban ni el estado por faltar \`%s\` en su texto: color de bioma y cofres/carteles).
- **Categorías con descripción corta** bajo la seleccionada, como en el menú de Tree Chopper.
- **Pendiente:** hoy este menú solo existe dentro del editor (botón «Cf»), no hay botón de configuración en Mod Menu como en los otros mods; y en ventanas pequeñas la pestaña General no cabe entera (habría que desplazarla). **[por decidir con Daniel]**
- **Opciones tapadas por Reset/Hecho [arreglado]:** en ventanas pequeñas la pestaña General (10 ajustes) se salía por abajo y los botones de pie tapaban las últimas opciones. Ahora las opciones que no caben se desplazan con la rueda del ratón (26 px por muesca) y una barra fina a la derecha lo indica; lo que queda fuera de la franja no se dibuja. Comprobado con capturas. Sigue por decidir el botón en Mod Menu.
- **Botones con el estilo del juego [hecho]:** la barra de botones y los botones con icono (ojo, candado, papelera) eran cuadrados negros planos y desentonaban con los botones grises de los ajustes. Ahora dibujan el fondo propio del juego (\`extractDefaultSprite\`) con el icono encima: mismo aspecto, resalte al pasar el ratón y estado desactivado de siempre; el modo activo mantiene su borde dorado. Quedan los iconos de la barra como dibujos de píxeles propios (algunos podrían mejorarse).

### Ronda de ajustes de Daniel (2026-10-19, noche)

1. **Teclas para colocación asistida y relleno automático [hecho]:** nueva tecla «Activar / desactivar el relleno automático» (la de la colocación asistida y la del holograma sólido ya existían); las tres salen ahora en la pestaña Controles del menú (sin asignar de serie) y en los controles de Minecraft. **Fallo corregido:** al mantener pulsado el botón de usar con el relleno activo y apuntar fuera del holograma, el juego seguía colocando bloques por su cuenta (la repetición del botón, con la sesión de relleno caducada). Ahora una repetición del botón mantenido con un bloque en la mano nunca coloca fuera del holograma; el primer clic sí es un clic normal. Prueba \`HeldOutside\` (con relleno: 1 bloque, el del primer clic; sin relleno: 3, así que la prueba sí coloca).
2. **Fondo del océano a través del holograma [hecho]:** los bloques **sólidos** (capa sólida o recortada del juego) se dibujan primero sin transparencia (atenuados según la opacidad: 0,35 de «fondo» de la opacidad que falta) y los **translúcidos** (hielo, cristal, agua, slime) siguen translúcidos encima: se ve a través de lo translúcido hasta el primer bloque sólido, y detrás no se ve el mundo. Ajuste **Aspecto > Bloques sólidos tapan el fondo** (activado de serie); «Holograma sólido» sigue haciendo opaco todo. Comprobado con una captura sobre un estanque.
3. **Barra lateral del menú hasta el pie [hecho]:** el oscurecido de la lista de categorías llega al borde inferior de la ventana.
4. **Arrastre del holograma volando [hecho]:** con la cámara alta y sin suelo a 6 bloques, si el puntero encuentra terreno más lejos que la distancia a la que se sostiene, el holograma se asienta en ese terreno (y lo sostiene a esa distancia). Antes se quedaba colgando en el aire a la distancia inicial, con el cuerpo de una obra alta (la granja de árboles) centrado en la cruceta y la base muy por debajo. Prueba \`FlyCarry\`.
5. **Iconos [hecho]:** el sello (tercer botón) más ligero y pequeño; los botones de la barra y los de icono llevan una placa oscura dentro del marco del juego, que devuelve al icono de ajustes el contraste de antes. No se redibujó el icono de ajustes.

### Pendiente pedido por Daniel (2026-10-20), por hacer tras compactar la sesión

**FIXES**
1. **Quitar los dos ajustes «Holograma sólido» y «Bloques sólidos tapan el fondo»** (y toda su lógica: \`hologramSolid\`, \`solidBlocksHide\`, la tecla SOLID, la pasada de bloques sólidos del renderizador, textos, ayudas y pruebas). No era lo que buscaba. Lo que quiere (fotos 3, 4 y 5): el holograma translúcido (se aprecia la transparencia) pero con **la capa más baja sólida**, para que no se vea el agua del océano que hay debajo a través del holograma. Es decir: transparencia normal en todo el holograma y una capa inferior opaca como suelo, no un ajuste por tipo de bloque.
2. **Pegar varias copias y construir con B [error]:** al seleccionar, Ctrl+C y pegar varias veces (ej. 4 árboles), la tecla B solo construye una de las copias; las otras 3, aunque estén bajo la cruceta, dan «built 0 blocks». Mirar cómo se elige el holograma «activo» en \`HologramBuilder.request\` (usa \`HologramRenderer.active()\`; las copias pegadas no pasan a ser la activa al apuntarlas o al colocarlas) y construir la copia a la que se apunta o la última colocada.
3. **Fluidos con nivel y animación [mejora]:** el agua, la lava y demás fluidos del holograma deben tener **altura según su nivel** (no ajustable) y **animación** de cómo fluyen (con ajuste para desactivar las animaciones), como en Litematica (foto 3: se ve la lámina de agua con su pendiente de nivel). Hoy se dibujan como bloque plano de color con flechas.

**NUEVO**
4. **Vista por capas (Layer Mode) [importante]:** modos **Todo / Una capa** (All / Single Layer); acciones **subir capa**, **bajar capa** y **cambiar el modo de capa** (subir y bajar la altura Y solo para Una capa); todo **ajustable y asignable a una tecla** (en los controles de Minecraft y en la pestaña Controles del menú), con indicación de la capa actual en pantalla.

### Hecho de la petición de Daniel (2026-10-20)

1. **Ajustes sólidos quitados [hecho]:** fuera `hologramSolid`, `solidBlocksHide`, la tecla SOLID, la pasada de sólidos, los textos y las ayudas. En su lugar, el holograma sigue translúcido pero **su capa más baja (y = 0 del holograma) se dibuja opaca** (atenuada según la opacidad: opacidad + 0,35 × lo que falta), para que el océano de debajo no se vea a través. Los fluidos de esa capa siguen translúcidos.
2. **B con varias copias pegadas [arreglado]:** `HologramBuilder.request(mc, rayo)` construye el holograma **al que apunta el puntero** (`HologramRenderer.pick`) y lo hace el activo; si no apunta a ninguno, el último activo. La confirmación (pulsar B dos veces) es por holograma. Prueba BuildAll ampliada con dos copias.
3. **Fluidos [hecho]:** el agua y la lava se dibujan con **altura según su nivel** (la altura de las cuatro esquinas se calcula como el juego: media de los vecinos, el origen pesa 10, lleno si hay fluido encima, aire cuenta como vacío; `Hologram.Entry.heights`), con la superficie inclinada, y con **las texturas del juego** (quieta arriba, corriente en los lados y girada en el sentido del flujo arriba), que **se animan** solas. Ajuste nuevo «Animación del agua y la lava» (`fluidAnimations`, Apariencia): apagado, vuelven a ser bloques de color liso (pero con altura). Las flechas de corriente se mantienen, a la altura de la superficie.
4. **Vista por capas [hecho]:** `hologram/LayerView` (modo Todas / Una sola capa y capa actual, contadas desde la base de cada holograma, así la misma capa de todas las copias se ve a la vez) y `placement/LayerControl` (teclas y rótulo). Teclas en Controles de Minecraft y en la pestaña Controles del menú: **Subir de capa** (numérico +), **Bajar de capa** (numérico −), **Capas: todas / una sola** (numérico ×); funcionan dentro y fuera del editor. Al pasar a una sola capa empieza en la altura del jugador. Subir/bajar solo actúa en «una sola capa» (en «todas» avisa). Lo oculto no se dibuja (bloques, flechas, cofres/carteles, entidades), no se rodea con contornos del verificador y la colocación asistida no lo pide. Rótulo «Capa N / M» arriba al centro mientras hay una sola capa, y una línea en la leyenda del editor. Prueba nueva: Layers.
   - **No incluido (por si se quiere):** botones de capa en la barra del editor y capa guardada con la colocación; la lista de materiales sigue contando todo el holograma.

### Segunda ronda (2026-10-20, tras probar Daniel)

1. **Capa baja opaca quitada:** no resolvía el «rayos X» del fondo marino (foto de Daniel), así que el holograma vuelve a ser translúcido del todo. **Sin resolver:** ver el fondo marino a través del holograma. Idea no probada: pasada previa de profundidad para dibujar solo la superficie más cercana del holograma (no evita ver el mundo real a través de ella).
2. **Una sola capa con todas sus caras [arreglado]:** en modo Una capa faltaban las caras de arriba y abajo (las tapaban los bloques de las capas ocultas). `Hologram.layerSections(capa)` calcula la capa aparte, sin que los vecinos de arriba y abajo oculten caras, y la guarda para la última capa usada; el renderizador la usa en modo Una capa. Prueba Layers ampliada.
3. **Flechas de corriente desactivables [hecho]:** ajuste `showFlowArrows` («Flechas de corriente») en Apariencia.

### Ronda del 2026-10-20 (tarde): cofres, construir en servidores, mover girando, botones, capa guardada, Mod Menu

1. **Cofres [hecho, probado]:** al copiar/cortar una zona se guardan los datos de los bloques con entidad de bloque (`SelectionActions.readBlockEntities`): en un mundo propio se leen del servidor integrado (con los objetos de los cofres, barriles, hornos...); en un servidor ajeno, de lo que el cliente conoce (carteles, estandartes, cabezas sí; **los objetos de un cofre no**, el servidor no los envía). El holograma de una colocación con cofres **lista su contenido** al apuntarlo (`placement/ContainerPreview`, HUD junto a la cruceta; en el editor con el puntero, jugando a hasta 12 bloques; ajuste «Contenido de cofres», `showContainerContents`, Apariencia). Lo ya construido en el mundo no lo muestra. El verificador sigue sin comparar el contenido.
2. **Construir con B [hecho, probado]:** `HologramBuilder` ahora (a) en mundo propio escribe con el servidor, como antes, y además **crea las entidades del esquema** (marcos, soportes...) y vacía antes, si se le pide, una zona; (b) en **cualquier otro servidor** (con permiso de comandos) envía **comandos**: `/fill` de cajas de bloques iguales (hasta 32768), `/setblock` con los datos para los que los llevan (cofres con sus objetos...) y `/summon` para las entidades, al ritmo que acepta el servidor (9 de golpe y luego 1 por segundo; el mensaje dice cuántos y cuánto tarda); **pulsar B otra vez los para**. `Hologram.entityTags` da las entidades ya giradas y con su sitio. Límites: sin cargar el área no se puede escribir (comandos a chunks no cargados fallan); sin neighbor updates a medida: los comandos sí avisan a los vecinos, así que arena/gravilla sin apoyo puede caer.
3. **Mover con giro o espejo [hecho, probado]:** `SelectionActions.moveTurned`: si la copia en mano está girada o reflejada, /clone no sirve; se **vacía la zona de origen** (bloques y entidades) y se **construye la copia girada** en el destino (por servidor o por comandos). En un servidor ajeno se rechaza si la zona tiene contenedores (se vaciarían); sin giro sigue usando /clone (que conserva todo). La copia es la de cuando se cogió.
4. **Botones en la barra [hecho]:** grupo nuevo (entre los modos y las ventanas): **Construir** (martillo, igual que B: pide confirmación), **Capas todas/una sola**, **Subir capa** y **Bajar capa** (estos dos solo activos en una sola capa). Iconos propios en `Icons`.
5. **Capa guardada [hecho, probado]:** el modo y la capa se guardan en `placements.json` del mundo (`layerMode`, `layer`) y vuelven al entrar; al entrar en otro mundo se reinicia a «todas». Es **por mundo, no por cada colocación** (la vista de capas es global para que se vea la misma capa de todas las copias).
6. **Botón en Mod Menu [hecho, probado]:** `client/HoloprintModMenu` (entrada `modmenu` en fabric.mod.json) abre `ui/ConfigScreen`, que muestra el mismo `ConfigPanel` que el botón «Cf» del editor como pantalla propia.
- **Sigue pendiente:** lista de materiales según la capa; construir por comandos en bloques con zonas no cargadas; el fondo marino «rayos X».
- Pruebas nuevas: `Containers`, `MoveTurned` (y `ToolbarShot`, solo captura); `Layers` y `BuildAll` ampliadas.

### Columnas de burbujas (2026-10-20, noche)
- **[hecho, probado con la prueba BubbleColumn]** Las columnas de burbujas (`bubble_column`, agua sobre arena de almas o bloque de magma) no se veían: no tienen modelo y no son un `LiquidBlock`. `Hologram.fluidOf` las trata como agua (fuente), con la altura y la textura del agua. Visto con el esquema «automatic tree farm» (tiene columnas de arena de almas; no tiene bloques de magma).
- **Sigue sin dibujarse:** el agua de los bloques «con agua» (trampillas, losas...: `waterlogged`); solo se ve su modelo. Algas y hierba marina, igual.
- **Pregunta abierta de Daniel:** cómo saber cuál de varias colocaciones está seleccionada (ver propuesta en la conversación).

### Colocación seleccionada visible (2026-10-20, noche)
- **[hecho, probado con capturas (prueba PlacementMark)]** Fuera «HOLOPRINT -» del título de la leyenda (queda solo el modo: «Mover», «Borrar»...). La leyenda del editor lleva bajo el título `Colocación: <nombre> (n/total)` (cian) y, si hay algo copiado, `Portapapeles: <nombre> (N bloques)` (gris). En el mundo, la colocación activa lleva un **recuadro dorado** y su **nombre flotando encima** (solo en el editor; `SelectionRenderer.activePlacement`, el nombre crece con la distancia). En la lista de colocaciones, la activa lleva **★** y el nombre en dorado.
- **No hecho (por decisión de Daniel):** tecla de siguiente/anterior colocación, nombre bajo la cruceta del holograma apuntado.

## 2026-10-20 (noche) Ventanas del editor
- Back y la «x» de una ventana abierta desde la barra vuelven a la ventana padre y, desde la primera, a la barra (antes salían a la cámara libre sin barra; solo Tab/Esc sin ventana la ocultan). Prueba: PanelNav (7 comprobaciones).

## 2026-10-20 (noche) Pruebas más rápidas
- Arnés: mundo plano sin estructuras creado en pacífico, espera por chunks, varias pruebas por arranque con limpieza entre ellas, velocidad configurable (HOLOPRINT_SPEED) y pausa para capturas (HOLOPRINT_HOLD / c.hold). `quick` ≈ 45 s; las 25 pruebas pasaban en un solo arranque (735 s a velocidad normal, 209 s a velocidad 4 salvo Orientation y WorldSync, que exigen ritmo normal).
- Pendiente según lo acordado: atajo de materiales (tecla), agua en bloques con agua y algas, medir rendimiento con esquemas grandes, fondo marino.

## 2026-10-20 (noche) Ronda de mejoras acordada
- Tecla J (MATERIALS, configurable): abre/cierra la lista de materiales de la colocación activa desde la cámara libre o la barra; en leyenda y en la lista de teclas. (L era «Logros» del juego; J está libre.)
- Agua en bloques con agua y algas: `Hologram.Entry` ahora lleva `waterlogged` y `modelFaces`; losas/escaleras con agua, kelp y hierba marina dibujan su agua y su modelo; un alga rodeada de agua sigue viéndose. Prueba: Waterlogged (5).
- Rendimiento (prueba BigSchematic, no pasa/falla): terreno 120x40x120 → 350-520 fps; cubos expuestos 32 000 bloques (192 000 caras) → 80-100 fps; torre 100x120x100 (278 000 bloques) → 125 fps; torre 200x120x200 (1 036 416 bloques, 4,8 M celdas) se construye en 205 ms, 530 MB y 108+ fps. Emitir las caras cuesta 1-5 ms por fotograma. Sin cuellos de botella: no se cambia nada. (Con la distancia de dibujo por defecto, 160, un esquema ancho no se ve desde muy lejos: es el ajuste.) Aviso: el juego baja a 30 fps tras un minuto sin entrada («AFK»); el arnés lo desactiva.
- Fondo marino: reproducido con la prueba SeaFloor (holograma sobre arena bajo 8 bloques de agua real). Se ve tenue por el agua, como es de esperar; no he encontrado un fallo concreto. Pendiente de que Daniel enseñe una captura de lo que ve mal.

## 2026-10-20 (noche, 2) Caras ocultas y colocación fuera del holograma
- Caras ocultas: un bloque ocultaba la cara del vecino si este «oclúa» aunque no fuera un cubo entero (repetidor, cofre, losa...). Ahora solo lo oculta un cubo completo (`Hologram.solidByNumber`, `Block.isShapeFullBlock(getOcclusionShape())`). En la capa 6 de «automatic tree farm» eran 27 caras (repetidores y cofres). Prueba: FaceCulling.
- Colocación asistida: un clic sobre un bloque ya bien puesto (o dentro de la caja de un holograma, en un sitio donde no se pide ese bloque) ponía el bloque en mano encima o al lado, fuera del holograma. Ahora se cancela con aviso (`EasyPlace.wouldPlaceOutside`, mensaje `easy_outside`). Lejos de los hologramas el juego coloca como siempre. Prueba: OutsideClick (creativo/supervivencia, clic suelto y mantenido, autorrelleno sí/no).
- Suite completa a velocidad 4: 22/22.

## 2026-10-20 (noche, 3) Bloque equivocado en creativo
- Síntoma: el esquema pedía piedra lisa y salía otro bloque (cobblestone, piedra...). Causa: en creativo, cuando el objeto no está en el inventario, la colocación asistida lo crea con un paquete de ranura creativa, pero el servidor no responde a eso y el cliente seguía creyendo que llevaba el objeto anterior; al pedir después ese mismo objeto no lo «equipaba» (creía tenerlo) y el servidor colocaba el último creado. Arreglo: `EasyPlace.equip` cambia también la ranura del cliente. Pruebas: MixedFill (falló con «Cobblestone -> Stone» antes del arreglo, ahora 16/16 en creativo y supervivencia) y EquipSwitch.
- Con el botón mantenido el relleno se queda en un solo tipo de bloque a la vez (`fillBlock`): es el diseño, no un fallo.
- Suite completa a velocidad 4: 24/24.

## 2026-10-20 (noche, 4) Auditoría de la colocación asistida con todos los bloques
- `AllBlocks` (no entra en `all`; ~25 min por modo): todos los bloques colocables del juego (1134), uno a uno, en supervivencia y creativo, con el objeto en otra ranura (o creado en creativo) y el entorno que cada uno necesita (suelo de piedra/tierra/arena/arena de almas/netherrack/tierra de cultivo/agua, techo, paredes). Resultado: 0 bloques equivocados, 0 en otra celda y 0 orientaciones equivocadas; sin colocar solo bambú (la colocación da un brote), nenúfar y huevas de rana (ni el juego sin el asistente los coloca en ese entorno de prueba). Con `HOLOPRINT_STATES=1` prueba también todas las combinaciones de facing/half/axis/face/type: en el entorno base 2548 estados, 2183 colocados, 0 mal; los 365 restantes no se pueden poner a mano de un clic (losas dobles, mitades superiores, cabeceras de cama) o necesitan techo/agua.
- `SweepFile` (no entra en `all`): barrido con el botón mantenido sobre «automatic tree farm» entera (997 celdas, 19 min): 0 bloques de otro tipo; diferencias solo de estado propio del juego (redstone, andamios, hierba que se extiende sobre tierra). En barridos rápidos se vieron cofres dobles sin conectar o con otra orientación (1 de cada 14 por pasada) con la mirada cambiando a toda prisa; con más tiempo por celda desaparece la orientación mala pero algún cofre doble queda sin conectar. Causa no resuelta: la prueba teletransporta al jugador entre celdas y eso puede alterar la mirada falseada; pendiente de comprobar con movimiento real.

## 2026-10-20 (noche, 5) Cofres dobles, bambú, nenúfar y huevas de rana
- Cofres dobles: sin teletransportes durante la prueba (`DoubleChest`: 4 orientaciones, los dos órdenes, 5 tiempos de estancia, 4 lados) 0 de 160 parejas mal con el botón mantenido y la mirada saltando de un cofre al otro. El fallo visto en `SweepFile` era de la prueba (el teletransporte entre celdas altera la mirada falseada), no del mod.
- Bambú: el objeto da un brote donde no hay bambú; ahora `Verifier.isBuilt` lo acepta como el primer tallo (el brote crece solo) y la colocación asistida lo elige como resultado válido; encima se coloca el tallo. Lista de materiales, verificador y colocación coinciden. Prueba: `BambooPlace`.
- Nenúfar y huevas de rana (`PlaceOnWaterBlockItem`): el objeto ignora el bloque clicado y usa donde la mirada toca el agua; ahora `EasyPlace.aimAtWater` gira la mirada hacia la superficie del agua justo bajo la celda (y comprueba con el mismo rayo del juego) o no coloca si no hay agua. Prueba: `WaterPlace` (cuenca de agua real, 4 lados, supervivencia y creativo): 0 de 16 mal.
- Las pruebas nuevas no limitan la velocidad: a velocidad 4 la prueba larga de cofres tarda 2 min en vez de 10; `AllBlocks` usa velocidad 4 por defecto.

## 2026-10-20 (noche, 6) Interfaz abierta por error, bloques de más y orientación
- Interfaz abierta por error: con la colocación asistida, un cofre, dispensador, horno, palanca, puerta... que ya es parte de un holograma ya no se abre ni se acciona con el botón mantenido (ni justo después de colocar); un clic suelto y nuevo sigue usándolo (`EasyPlace.wouldOpenInterface`, mensaje `easy_no_open`). Prueba: `NoOpen` (con autorrelleno sí y no).
- Bloques de más fuera del esquema: con el autorrelleno DESACTIVADO, mantener el botón dejaba que el juego repitiera la colocación y pusiera bloques junto al holograma. Ahora, a menos de 4 bloques de la caja de un holograma, un botón mantenido solo coloca lo que pide el esquema (`nearHologram`). Lejos de todo holograma el juego coloca como siempre. Prueba: `HeldNear` (sin el arreglo ponía 2 bloques de más; con él, 0).
- Orientación de repetidores y bloques con `facing`: `LookRow` (observador, pistón, dispensador, dropper, pistón pegajoso, repetidor y comparador, también arriba y abajo) a velocidad normal: 0 mal de 205 con el botón mantenido o con toques. Los fallos que salían a velocidad 4 eran de la prueba (cliente y servidor van por hilos distintos); `LookRow` y `DoubleChest`-tipo con mirada falseada se fijan a velocidad 1. Se añade una espera de 2 ticks para bloques que miran hacia donde mira el jugador tras haber enviado una mirada (`lastRotationSent`), por si la cabeza del servidor va un tick por detrás. La causa más probable de los repetidores mal de tu captura (autorrelleno desactivado) era la repetición del juego (arriba).

## 2026-10-20 (noche, 7) Medición de tirones con esquemas grandes (`FrameSpikes`, no entra en `all`)
- Vuelo en cámara libre en círculos y con subidas/bajadas, midiendo cada fotograma. Torre sintética de 278 000 bloques: peor fotograma 32 ms (sin fotogramas de más de 33 ms). Torre de 1 M bloques: peor 26 ms. Rejilla de 24 000 cofres: sin tirones (poco concluyente).
- Esquema real más grande de TEST MODS («korbar-endhaven-black-hole.schem», 727x257x262, 230 000 bloques a dibujar): media de 9-16 ms por fotograma y, por ventana de 150 ticks, de 0 a 5 fotogramas de más de 33 ms (máximo 53 ms). Construir mallas cuesta como mucho 2-4 ms por fotograma (no es la causa); emitir las caras cuesta 8-12 ms.
- Con el límite de bloques por fotograma (`blockBudget`, por defecto 60 000) a 30 000: media ~10,6 ms, peor 16-25 ms, 0 fotogramas de más de 33 ms; a 15 000: media ~6-7 ms, peor ≤16 ms. Conclusión: el coste es el dibujado por fotograma, proporcional a los bloques dibujados; los hilos de trabajo para mallas no ayudarían. Opciones: bajar el límite (ya es un ajuste) o mallas retenidas en GPU como Litematica.

## 2026-10-20 (noche, 8) Mallas retenidas en la GPU (`hologram/RetainedMeshes`)
- Antes, cada fotograma se copiaban todas las caras visibles (hasta 60 000 bloques) al búfer de dibujo: 8-12 ms de CPU y tirones con esquemas grandes. Ahora la malla de cada sección de 16³ se sube una vez a la GPU (búferes de vértices con el formato ENTITY) y cada fotograma solo cuesta una llamada de dibujo por sección. Se dibuja al final del pase principal (`LevelRenderEvents.END_MAIN`, los otros puntos chocan con un pase abierto o dejan el holograma sin dibujar) con el mismo pipeline de entidad translúcida con caras traseras descartadas por la GPU (`entityTranslucentCull`), sin niebla, de lejos a cerca (para que las caras translúcidas se mezclen como antes), con un búfer de índices propio (el compartido del juego no crece) y con el color y la opacidad del holograma como color del modelo (`ColorModulator`) y su posición como desplazamiento de la vista. Las flechas de corriente siguen copiándose cada fotograma.
- Lo que antes se decidía cara a cara en cada fotograma se decide al construir el búfer: los bloques ya construidos (se vuelve a construir la sección cuando cambia `Hologram.builtStamp`, un contador por celda de 16³ que sube con `Verifier`) y la capa mostrada. Las caras de espaldas las descarta la GPU.
- Interruptor: `HoloprintConfig.retainedMeshes` (por defecto activo; sin él se usa el dibujado antiguo). Los límites por defecto suben a 256 bloques de distancia y 200 000 bloques por fotograma (un archivo de configuración con los valores antiguos exactos, 160 y 60 000, pasa a los nuevos). Las mallas que solo están desactualizadas (color de bioma) se rehacen como mucho 2 por fotograma.
- Mediciones (`BigSchematic`, sin VSync): terreno 120x40x120 de 350-510 a 1650-1790 fps; cubos expuestos de 80-100 a 2000-2100 fps; torre de 278 000 bloques de 127 a 1600-1800 fps (sin hologramas, 2000-2400). Vuelo sobre «korbar-endhaven-black-hole.schem» (`FrameSpikes`): media de 9-16 ms por fotograma a 0,75 ms, y de 10 a 0-1 fotogramas de más de 33 ms por ventana. Comparación visual (`RetainedLook`, `RetainedBuilt`): igual que antes en vista normal, dentro del esquema, con bloques construidos, capa suelta y transparencia mejorada.
- Pendiente conocido: al recargar recursos (F3+T) las mallas guardadas conservan las texturas antiguas hasta que se reconstruyan (ya ocurría con el dibujado antiguo).

## 2026-10-20 (noche, 9) Recarga de recursos y contorno de bloques
- **Recarga de recursos (F3+T)**: `HologramRenderer.clearCache()` (registrado como `ResourceManagerReloadListener` del cliente) vacía las partes y las mallas guardadas (`forgetMeshes`), así que el holograma se reconstruye con las texturas nuevas. Prueba: `ReloadResources`.
- **Contorno de bloques** (como Litematica: `SCHEMATIC_OVERLAY_ENABLE_OUTLINES`, color #33B3E6, 12 aristas por bloque): activado por defecto, con interruptor y color en Ajustes > Apariencia (`blockOutlines`, `outlineColor`).
  - Se guarda en la GPU como el resto: `RetainedMeshes.Gpu.lines` (un buffer de aristas por celda de 16³), con las aristas de los bloques que muestran alguna cara (sin fluidos; respeta «ocultar lo construido» y la vista por capas) y sin repetir las que comparten dos bloques. Se rehace al cambiar el color o el interruptor (`outlineArgb`).
  - Cada línea la dibuja el juego como un cuadrilátero: 4 vértices y 6 índices (patrón 0,1,2,3,2,1), así que `ExecuteInfo` lleva `indexCount` de la `MeshData` y un buffer de índices propio; con índices «0,1,2…» solo salía una parte de las aristas.
  - Se dibujan en la misma pasada, después de todas las caras, con `RenderTypes.linesDepthBias()` (no hace falta acercarlas a la cámara). Anchura 1,5.
  - Pruebas: `OutlineLook` (árbol automático con/sin contorno, de lejos y de cerca), `OutlineSmall` (bloques sueltos en el aire), `AppearanceShot` (la página de ajustes).

## 2026-10-21 Herramientas en la hotbar (experimento)
- En la cámara libre (G) la hotbar muestra los iconos de la barra (`ui/HotbarTools`): 1 Mover, 2 Seleccionar, 3 Copiar, 4 Borrar, 5 Capas, 6 Materiales, 7 Cargar, 8 Construir, 9 Menú. Ajuste `hotbarTools` (Ajustes > General); apagado, todo como antes. Tab sigue abriendo la columna de botones.
- Las teclas 1–9 se consumen en `Minecraft.handleKeybinds` (mixin) y no tocan el slot del jugador. La rueda mueve el resaltado (los modos 1–4 se activan al llegar; 5–9 esperan un clic de usar/atacar, que no llega a la herramienta; `swallow` evita repetir mientras se mantiene el botón). Velocidad: Ctrl + rueda, o las teclas `,` y `.` (`speed_down`/`speed_up`).
- Litematica (si está instalado) quita el teclado numérico «−» (`openGuiPlacementSettings`, «No placement selected»): la capa no baja. Hay que cambiar una de las dos teclas en Controles.
- Prueba: `HotbarTools` (17 comprobaciones).
