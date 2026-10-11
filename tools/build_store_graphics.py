"""Build the Google Play store graphics from the approved logo assets.

Google Play requires, for a TV app listing:
  * app icon        512 x 512 PNG (32-bit)
  * feature graphic 1024 x 500 PNG or JPEG (mandatory)
  * TV screenshots  1280 x 720 (at least one, up to eight)

The approved preview sheet contains the mark, a wordmark and a row of small icon variants. For the
store assets only the mark is used, isolated by splitting the sheet on its blank rows. Text in the
feature graphic is measured and shrunk until it fits the canvas, so nothing is cropped.

Output: 09-MAGAZA-GORSELLERI/
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

PACKAGE = Path(r"C:\Users\ASUS\Desktop\AirRelay")
DESIGN = Path(r"C:\Users\ASUS\Documents\AirRelay\design\airrelay-monogram\approved-preview.png")
SHOTS = PACKAGE / "06-EKRAN-GORUNTULERI"
OUT = PACKAGE / "09-MAGAZA-GORSELLERI"

BRAND_BG = (23, 25, 27)
BRAND_ACCENT = (249, 237, 223)
BRAND_TEAL = (58, 205, 183)
MUTED = (150, 158, 164)
FAINT = (110, 118, 124)

FONTS_BOLD = [r"C:\Windows\Fonts\segoeuib.ttf", r"C:\Windows\Fonts\arialbd.ttf"]
FONTS_REGULAR = [r"C:\Windows\Fonts\segoeui.ttf", r"C:\Windows\Fonts\arial.ttf"]


def font(candidates: list[str], size: int) -> ImageFont.FreeTypeFont:
    for candidate in candidates:
        if Path(candidate).is_file():
            return ImageFont.truetype(candidate, size)
    return ImageFont.load_default(size)


def rows_with_content(image: Image.Image, background: tuple[int, int, int],
                      threshold: int = 40) -> list[tuple[int, int]]:
    """Contiguous row ranges that contain anything other than the background colour."""
    pixels = image.load()
    flags = []
    for y in range(image.height):
        row_has_content = False
        for x in range(image.width):
            p = pixels[x, y]
            if abs(p[0] - background[0]) + abs(p[1] - background[1]) + abs(p[2] - background[2]) > threshold:
                row_has_content = True
                break
        flags.append(row_has_content)
    ranges, start = [], None
    for y, filled in enumerate(flags + [False]):
        if filled and start is None:
            start = y
        elif not filled and start is not None:
            if y - start > 8:
                ranges.append((start, y))
            start = None
    return ranges


def logo_mark() -> Image.Image:
    """The mark alone, as RGBA on a transparent canvas, so it can sit on any background.

    The approved preview sheet draws the mark on a slightly different dark tone than the app's brand
    background; pasting it as an opaque square left a visible seam, so the sheet's background colour
    is turned into transparency here.
    """
    sheet = Image.open(DESIGN).convert("RGB")
    background = sheet.getpixel((2, 2))
    ranges = rows_with_content(sheet, background)
    if not ranges:
        raise SystemExit("could not isolate the mark in the logo sheet")
    top, bottom = ranges[0]                      # first block on the sheet is the mark
    band = sheet.crop((0, top, sheet.width, bottom))
    columns = [x for x in range(band.width)
               if any(abs(band.getpixel((x, y))[i] - background[i]) > 40 for y in range(0, band.height, 4) for i in range(3))]
    left, right = (min(columns), max(columns) + 1) if columns else (0, band.width)
    cropped = band.crop((left, 0, right, band.height))

    mask = Image.new("L", cropped.size, 0)
    source, target = cropped.load(), mask.load()
    for y in range(cropped.height):
        for x in range(cropped.width):
            p = source[x, y]
            distance = abs(p[0] - background[0]) + abs(p[1] - background[1]) + abs(p[2] - background[2])
            target[x, y] = 255 if distance > 30 else 0

    side = max(cropped.size)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    layer = Image.new("RGBA", cropped.size, (0, 0, 0, 0))
    layer.paste(cropped, (0, 0), mask)
    canvas.paste(layer, ((side - cropped.width) // 2, (side - cropped.height) // 2), layer)
    return canvas


def on_brand(mark: Image.Image, size: int, background: tuple[int, int, int] = BRAND_BG) -> Image.Image:
    """The mark scaled onto a solid brand-coloured square (no alpha in the final asset)."""
    scaled = mark.resize((size, size), Image.LANCZOS)
    square = Image.new("RGB", (size, size), background)
    square.paste(scaled.convert("RGB"), (0, 0), scaled.split()[-1])
    return square


def fit_text(draw: ImageDraw.ImageDraw, text: str, candidates: list[str], size: int,
             max_width: int) -> ImageFont.FreeTypeFont:
    while size > 12:
        chosen = font(candidates, size)
        if draw.textlength(text, font=chosen) <= max_width:
            return chosen
        size -= 2
    return font(candidates, 12)


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    if not DESIGN.is_file():
        raise SystemExit(f"missing logo source: {DESIGN}")
    mark = logo_mark()

    # 1) Store icon 512x512.
    canvas = Image.new("RGB", (512, 512), BRAND_BG)
    canvas.paste(on_brand(mark, 420), (46, 46))
    canvas.save(OUT / "play-icon-512.png", "PNG")
    print("  play-icon-512.png            512 x 512   (mark only)")

    # 2) Feature graphic 1024x500.
    feature = Image.new("RGB", (1024, 500), BRAND_BG)
    draw = ImageDraw.Draw(feature)
    feature.paste(on_brand(mark, 260), (72, 120))
    left, right = 392, 960
    width = right - left
    draw.text((left, 150), "AirRelay", font=fit_text(draw, "AirRelay", FONTS_BOLD, 76, width), fill=BRAND_ACCENT)
    draw.text((left + 2, 244), "AirPlay receiver for Android TV",
              font=fit_text(draw, "AirPlay receiver for Android TV", FONTS_REGULAR, 30, width), fill=MUTED)
    draw.text((left + 2, 288), "Screen mirroring and audio, locally",
              font=fit_text(draw, "Screen mirroring and audio, locally", FONTS_REGULAR, 26, width), fill=FAINT)
    draw.line((left + 2, 336, right, 336), fill=BRAND_TEAL, width=3)
    draw.text((left + 2, 352), "No accounts · no ads · no tracking",
              font=fit_text(draw, "No accounts · no ads · no tracking", FONTS_REGULAR, 26, width), fill=MUTED)
    feature.save(OUT / "feature-graphic-1024x500.png", "PNG")
    print("  feature-graphic-1024x500.png 1024 x 500   (measured text, nothing cropped)")

    # 3) TV banner 320x180, used on the Android TV home screen.
    banner = Image.new("RGB", (320, 180), BRAND_BG)
    banner.paste(on_brand(mark, 104), (24, 38))
    ImageDraw.Draw(banner).text((146, 74), "AirRelay", font=font(FONTS_BOLD, 28), fill=BRAND_ACCENT)
    banner.save(OUT / "tv-banner-320x180.png", "PNG")
    print("  tv-banner-320x180.png         320 x 180")

    # 4) TV screenshots at exactly 1280x720.
    count = 0
    for shot in sorted(SHOTS.glob("*.png")):
        if shot.name.startswith("icon-"):
            continue
        with Image.open(shot) as im:
            scaled = im.convert("RGB").resize((1280, 720), Image.LANCZOS)
        count += 1
        scaled.save(OUT / f"tv-screenshot-{count}.png", "PNG")
        print(f"  tv-screenshot-{count}.png{'':<10} 1280 x 720   (from {shot.name})")
    if count == 0:
        print("  !! no screenshots found")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
