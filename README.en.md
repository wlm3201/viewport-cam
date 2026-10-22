# ViewportCam

English | [简体中文](README.md)

A Minecraft Fabric client mod that adds a modelling-tool style orbit camera: drag with the mouse to
pan, orbit and zoom, and release the crosshair so it can move freely across the screen — letting you
interact with any block on screen without turning the view. A dedicated zoom key magnifies the
pointer area for precise work.

## Keybinds

| Key | Default | Description |
| --- | --- | --- |
| Enter / exit orbit mode | **F6** | The only entry point |
| Modifier | **Left Alt** | Behaviour depends on the key mode, see below |
| Orbit | **Left mouse** | Drag to rotate the camera around the pivot |
| Pan | **Right mouse** | Drag to pan the camera |
| Zoom | **Scroll wheel** | Change the camera's distance to the pivot |
| Zoom lens | **C** | Magnify the area under the pointer; scroll to adjust |
| WASD / Space / Shift | — | Free flight, Ctrl to boost |

## Modifier modes

| Mode | Modifier | Mouse buttons |
| --- | --- | --- |
| Hold | Hold to switch | Left orbits, right pans, wheel zooms |
| Toggle | Press to switch | Same as above |
| Independent | Wheel only | Rotate/pan keys are independent of attack/break, so both work at once |
| **Hybrid** | Wheel only | Click-and-release breaks/places, drag orbits/pans |

## Configuration

- **Feel**: rotate/pan sensitivity and smoothing, zoom speed
- **Zoom lens**: focal length, transition speed, zoom speed, anchor lock
- **Flight**: horizontal/vertical speed, horizontal/vertical multiplier, accelerate time,
  decelerate time
- **Mode**: operation mode, drag threshold
- **Rendering**: pivot block, outline colour
- **Interaction**: block reach, entity reach
- **Single player**: distance sync, body follow
- **Info**: status bar, X/Y coordinates
