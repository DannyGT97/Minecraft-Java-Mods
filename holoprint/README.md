# Holoprint (nombre provisional)

Mod Fabric de cliente para Minecraft 26.3 para usar schematics de forma simple: cargar, colocar, mover, copiar y rellenar un holograma, con una vista de cámara libre y soporte de mando (Controlify). Lee y guarda archivos `.litematic`, compatibles con Litematica.

## Estado

En desarrollo, no publicado.

- [x] Leer y guardar `.litematic` (versiones 1 a 7), con actualización de datos de versiones anteriores de Minecraft.
- [x] Holograma en el mundo (bloques fantasma translúcidos; probado con una casa de 9.000 bloques a 60 fps).
- [x] Cámara libre (el jugador no se mueve ni cambia de modo) y mover el holograma con ella: sigue al cursor, se fija con clic, rota, espeja y se empuja bloque a bloque.
- [ ] Probar el movimiento con mando real (Controlify).
- [ ] Selección, copiar, pegar y mover.
- [ ] Colocar con el estado correcto y relleno automático.
- [ ] Soporte de mando con Controlify.

## Compilar

En Windows ejecuta gradlew.bat build. El mod compilado queda en build/libs/holoprint-26.3.jar.

## Cómo se usa (provisional)

1. Copia un `.litematic` en la carpeta `schematics` del juego y escribe `/holoprint load <archivo>`.
2. Pulsa G (o `/holoprint move`): vuelas con la cámara libre sin mover al personaje.
3. El holograma sigue al cursor. Clic izquierdo lo fija o lo vuelve a coger; R rota, M espeja, flechas y Re Pág/Av Pág lo empujan un bloque; clic derecho o G terminan; Retroceso cancela.
