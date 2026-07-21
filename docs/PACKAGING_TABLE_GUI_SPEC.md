# Packaging Table GUI Specification

Status: v0.8.0E asset framework contract

## Texture Contract

| Field | Value |
| --- | --- |
| Texture resource location | `butchercraft:textures/gui/packaging_table.png` |
| Source file | `src/main/resources/assets/butchercraft/textures/gui/packaging_table.png` |
| Full texture canvas | 256x256 PNG |
| Active GUI region | 176x166 pixels from UV `0,0` |
| Transparency | Allowed outside the active GUI region. Active region should remain opaque enough for readable slots and text. |
| Current status | Placeholder, framework ready |

The screen class uses `PackagingTableGuiLayout` as the source of truth for the active texture dimensions and replacement-safe GUI bounds.

## Active Regions

| Region | X | Y | Width | Height | Notes |
| --- | ---: | ---: | ---: | ---: | --- |
| Full visible GUI | 0 | 0 | 176 | 166 | Drawn from `packaging_table.png`. |
| Workstation panel | 6 | 16 | 164 | 60 | Background area behind workstation slots and status. |
| Player inventory | 8 | 84 | 162 | 54 | Vanilla player inventory slots from the menu. |
| Hotbar | 8 | 142 | 162 | 18 | Vanilla hotbar slots from the menu. |
| Unused texture region | 176 | 0 | 80 | 256 | Reserved for future GUI elements. |
| Unused texture region | 0 | 166 | 176 | 90 | Reserved for future GUI elements. |

## Slot Coordinates

Coordinates are relative to the top-left of the visible GUI. Slot coordinates are the actual item-slot positions; the code renders a one-pixel frame around each slot.

| Slot | Role | X | Y | Width | Height |
| --- | --- | ---: | ---: | ---: | ---: |
| 0 | Meat input | 32 | 25 | 18 | 18 |
| 1 | Tray supply input | 62 | 25 | 18 | 18 |
| 2 | Wrap supply input | 92 | 25 | 18 | 18 |
| 3 | Result output | 62 | 55 | 18 | 18 |

## Progress Indicator

| Field | Value |
| --- | --- |
| X | 116 |
| Y | 39 |
| Width | 20 |
| Height | 6 |
| Direction | Left to right |
| Fill source | Code-rendered rectangular fill using synchronized menu progress |
| Background source | Code-rendered rectangular background |

Progress clipping is integer-based: `width * progressPercent / 100`. Final GUI artwork must leave this region unobstructed unless the code-rendered progress style is deliberately changed in a later UI sprint.

## Text Positions

| Text | X | Y | Width | Notes |
| --- | ---: | ---: | ---: | --- |
| Title | inherited `titleLabelX` | inherited `titleLabelY` | dynamic | Drawn by screen font. |
| Meat label center | 40 | 17 | dynamic | Centered text. |
| Tray label center | 70 | 17 | dynamic | Centered text. |
| Wrap label center | 100 | 17 | dynamic | Centered text. |
| Result label center | 70 | 75 | dynamic | Centered text. |
| Status | 108 | 52 | 60 | Clipped to width. |
| Player inventory title | inherited `inventoryLabelX` | inherited `inventoryLabelY` | dynamic | Drawn by screen font. |

## Acceptance Criteria

- The GUI opens without missing textures or resource-location errors.
- Slot frames align exactly with menu slot positions.
- Progress fill remains inside the documented 20x6 region.
- Foreground text does not overlap item slots or progress.
- The active GUI region remains readable at common GUI scales.
- Replacing `assets/butchercraft/textures/gui/packaging_table.png` with final art does not require gameplay, menu, or workstation changes.
- Final art must not imply unimplemented labels, freshness, pricing, automation, or business systems.
