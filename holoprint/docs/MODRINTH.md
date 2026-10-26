<!--
Description for the Modrinth page (paste it into the project's description box).
Modrinth does not resolve relative paths, so the images point to the repository on GitHub:
they only show once docs/images has been pushed there.
-->

# Holoprint

Turn schematics into holograms you can fly around, move, check and build from. Like Litematica, but simpler: a free camera, a hotbar full of tools, and nothing to set up before you start.

**Client-side only.** It does not need to be installed on the server.

![A schematic shown as a hologram in the world](https://raw.githubusercontent.com/DannyGT97/Minecraft-Java-Mods/main/holoprint/docs/images/01-hologram.jpg)

## What you can do

- **Load schematics:** `.litematic`, Sponge `.schem` and legacy `.schematic`.
- **See them as a hologram,** with a blue outline on every block (optional, and you can change the colour).
- **Fly with a free camera:** press `M`, and your character stays where it is.
- **Move, rotate and mirror** the hologram: it follows your pointer and a click pins it.
- **Check your build:** what is missing shows as a ghost, and a wrong block is outlined in red.
- **Place blocks with help:** aim at the hologram and place, with the right state and orientation.
- **Look at one layer at a time,** and step up and down through the build.
- **Read the material list** of every placement.
- **Work with several placements,** copy and paste areas, and save a selection as a schematic.
- **Build a placement in one go** (needs cheats), or move a real area with `/clone`.

## The hotbar is your toolbar

In the free camera the nine slots show the editor's tools. Pick one with the number keys or the mouse wheel, and use it with the use click. Your own slot is never touched.

![The editing tools on the hotbar](https://raw.githubusercontent.com/DannyGT97/Minecraft-Java-Mods/main/holoprint/docs/images/02-hotbar-build.jpg)

| Slot | Tool |
|---|---|
| 1 | Editing tools: Move, Select, Copy, Delete and Build |
| 2 | Load a schematic |
| 3 | Placements |
| 4 | Material list |
| 5 | Layers |
| 6 | Area editor |
| 7 | Settings |

The keys for each tool show up above the hotbar, using whatever you have set in the controls.

## One layer at a time

![Showing a single layer of a build](https://raw.githubusercontent.com/DannyGT97/Minecraft-Java-Mods/main/holoprint/docs/images/03-layers.jpg)

## Windows for the rest

| | |
|---|---|
| ![Material list](https://raw.githubusercontent.com/DannyGT97/Minecraft-Java-Mods/main/holoprint/docs/images/04-materials.jpg) | ![Placements](https://raw.githubusercontent.com/DannyGT97/Minecraft-Java-Mods/main/holoprint/docs/images/05-placements.jpg) |
| ![Loading a schematic](https://raw.githubusercontent.com/DannyGT97/Minecraft-Java-Mods/main/holoprint/docs/images/06-load.jpg) | ![Area editor](https://raw.githubusercontent.com/DannyGT97/Minecraft-Java-Mods/main/holoprint/docs/images/07-area-editor.jpg) |

## Know what is left to build

What already matches the hologram disappears; what is left stays as a ghost; a block that is not the right one is outlined in red.

![A half-built hut with two wrong blocks outlined in red](https://raw.githubusercontent.com/DannyGT97/Minecraft-Java-Mods/main/holoprint/docs/images/09-verify.jpg)

## Quick start

1. Install Fabric Loader and the Fabric API for Minecraft 26.3, and put the mod in your `mods` folder.
2. Put your schematics in the game's `schematics` folder.
3. In a world, press `M`, pick **Load** (slot 2) and choose a file.
4. Click to put the hologram down. Press `M` again to leave.

## Main keys

All of them can be changed in the controls screen.

| Key | Action |
|---|---|
| `M` | Open / close the free camera |
| `1`-`9`, wheel | Pick a tool |
| `R` / `Z` | Rotate / mirror |
| `B` | Build the placement you point at (needs cheats) |
| `J` | Material list |
| Numpad `+` / `-` / `*` | Layer up / down / all or one |

## Controller

With [Controlify](https://modrinth.com/mod/controlify) installed it works with a controller too (it is optional, and only acts while you use the controller): the bumpers pick the tools on the hotbar, the triggers click, **B** opens the editor, cancels and closes it, the D-pad left rotates, and every other action is in Controlify's controls under **Holoprint**.

If you also use Litematica, a few default keys (like the numpad `-`) are shared: change one of the two.
