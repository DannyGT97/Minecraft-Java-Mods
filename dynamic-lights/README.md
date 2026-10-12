# Dynamic Lights

Mod Fabric para Minecraft 26.3, solo cliente. Hace que los objetos y las entidades que emiten luz iluminen de verdad su entorno mientras se mueven, sin colocar bloques y sin enviar nada al servidor, así que funciona en solitario y en cualquier servidor, tenga o no el mod.

- Objetos que emiten luz, en la mano o tirados en el suelo (antorchas, linternas, piedra luminosa, cubo de lava, bayas luminosas...).
- Entidades ardiendo, calamares luminosos, blazes, bolas de fuego, cubos de magma, flechas espectrales y allays.
- Efectos puntuales: creeper hinchándose, rugido sónico del Warden, TNT encendida y el rayo de los guardianes.
- Luz que sigue suavemente al objeto o entidad, calculada en cada fotograma.
- No afecta a la aparición de mobs: es un efecto solo visual.

## Ajustes

Disponibles en Mod Menu (Mods > Dynamic Lights > Configure) o en `config/dynamiclights.json`. Todo viene activado por defecto.

- **General:** luces dinámicas para ti (lo que llevas en la mano, cuando ardes) y para el resto de entidades.
- **Entidades:** objetos en mano o en el suelo, calamares luminosos, blazes, bolas de fuego, cubos de magma, flechas espectrales y allays.
- **Efectos especiales:** entidades ardiendo, creeper hinchándose, láseres de guardián y TNT (Desactivado / Simple o Rápido / Elaborado) y rugido sónico del Warden.

## Requisitos

Minecraft 26.3, Fabric Loader 0.19.5 o posterior, Fabric API y Java 25. Mod Menu es opcional y añade el botón de ajustes.

## Compilar

En Windows ejecuta gradlew.bat jar. El mod compilado queda en build/libs/dynamic-lights-26.3.jar. Cópialo en la carpeta mods junto con Fabric API.
