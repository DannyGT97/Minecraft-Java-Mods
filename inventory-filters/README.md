# Inventory Filters

Mod Fabric independiente que añade un botón de ordenado al inventario del jugador y un botón para cada sección de almacenamiento en cofres y shulkers.

- Un único tipo de ordenado, elegido en los ajustes: Tipo (por defecto), Nombre, Categoría o Rareza. El botón ya no cambia de modo en cada clic.
- Orden invertido opcional (Z-A, rareza de menor a mayor).
- Compacta stacks compatibles antes de ordenar.
- Añade botones para apilar (mueve solo los objetos que el destino ya contiene, rellenando stacks y usando huecos libres) o mover todo entre el cofre/shulker y el inventario del jugador.
- La hotbar queda intacta por defecto; se puede incluir en ordenar, mover y apilar desde los ajustes.
- Los botones tienen dos temas, Claro (por defecto) y Oscuro, elegibles en los ajustes.
- Ejecuta el ordenado en servidor. Incluye traducciones en español e inglés.

## Ajustes

Disponibles en Mod Menu (Mods > Inventory Filters > Configure) o en `config/inventoryfilters.properties`. Son ajustes del cliente: cada jugador usa los suyos, tanto en solitario como en servidores con el mod instalado.

| Ajuste | Por defecto | Efecto |
| --- | --- | --- |
| Inventory Filters | ON | Interruptor general; en OFF no se muestra ningún botón y no funciona nada. |
| Tipo de ordenación | Tipo | Tipo, Nombre, Categoría o Rareza. |
| Invertir orden | OFF | Orden inverso al elegido. |
| Incluir hotbar | OFF | Incluye la hotbar al ordenar, mover y apilar. |
| Tema de los botones | Claro | Aspecto de los botones: Claro u Oscuro. |

## Requisitos

Minecraft 26.3, Fabric Loader 0.19.5 o posterior, Fabric API y Java 25. Mod Menu es opcional y añade el botón de ajustes.

## Compilar

En Windows ejecuta gradlew.bat jar. El mod compilado queda en build/libs/inventory-filters-26.3.jar. Copia ese archivo en la carpeta mods junto con Fabric API. En multijugador debe estar instalado tanto en el cliente como en el servidor.
