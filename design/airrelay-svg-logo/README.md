# Logo source and safe-zone rules

## Provenance (read this before using the file)

`source.svg` in this folder is a **derived** master, not the original authoring file. Earlier
documentation claimed the user's master artwork was preserved here; that file was not present in
the repository. The geometry in `source.svg` is copied from the authoritative in-app vector
drawable, `app/src/main/res/drawable/airrelay_logo_vector.xml`, so the exported assets and the
launcher icon cannot drift apart.

If the original authoring file is recovered, replace `source.svg` and re-export the assets, then
update this note. Until then, treat the app vector as the source of truth.

## Safe-zone rules for the launcher icon

Android adaptive icons are a 108dp x 108dp canvas:

- the system reserves the outer 18dp on each side for masking;
- the mask fits inside the central 72dp x 72dp;
- only the central **66dp circle** is guaranteed to be visible.

The drawn mark must therefore fit inside that 66dp circle, not merely inside a 66dp square: a
square box touches the circle's edge with its corners. The app places the mark in a **48dp square
box** (`ic_launcher_foreground.xml`), which keeps the visible mark (roughly 42dp x 35dp, the
vector carries its own padding) at a half-diagonal of about 27dp against a 33dp radius.

Keep the artwork **square**: the vector is 100dp x 100dp, and a non-square box stretches it.

## Assets to export after any geometry change

| Asset | Requirement |
|---|---|
| Play Store icon | 512 x 512 PNG, 32-bit, mark inside the central ~61% (the 66dp circle of the 108dp canvas) |
| Google TV banner | 320 x 180 PNG, logo at 82dp square on the `#17191B` background, wordmark 170dp x 64dp |
| Feature graphic | 1024 x 500 PNG, same background, do not repeat the launcher icon exactly |
| In-app brand | `ReceiverBrand()` already scales the logo with `ContentScale.Fit`; no export needed |

Do not export a store icon from a square 108dp canvas without the safe-zone padding: a full-bleed
export is what produced the cropped icon reported in the launcher and in the system's
"display over other apps" list.
