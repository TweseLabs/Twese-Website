from __future__ import annotations

import csv
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageOps


SOURCE = Path("/home/ubuntu/webdev-static-assets/twese/drive-source")
OUTPUT = Path("/home/ubuntu/recovered-site/backups/drive-contact-sheets")
THUMB_WIDTH = 220
THUMB_HEIGHT = 148
LABEL_HEIGHT = 48
COLUMNS = 4
PER_SHEET = 32


def short_label(name: str, limit: int = 34) -> str:
    return name if len(name) <= limit else f"{name[: limit - 1]}…"


def thumbnail(path: Path) -> Image.Image:
    image = Image.open(path).convert("RGB")
    return ImageOps.contain(image, (THUMB_WIDTH, THUMB_HEIGHT))


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    font = ImageFont.load_default()
    files = sorted(path for path in SOURCE.rglob("*") if path.suffix.lower() in {".jpg", ".jpeg", ".png", ".webp"})
    records: list[tuple[Path, int, int, str]] = []
    for path in files:
        try:
            with Image.open(path) as image:
                width, height = image.size
                records.append((path, width, height, image.format or "unknown"))
        except OSError:
            continue

    with (OUTPUT / "media_inventory.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["filename", "relative_path", "width_px", "height_px", "format"])
        for path, width, height, image_format in records:
            writer.writerow([path.name, path.relative_to(SOURCE), width, height, image_format])

    for start in range(0, len(records), PER_SHEET):
        batch = records[start : start + PER_SHEET]
        rows = math.ceil(len(batch) / COLUMNS)
        canvas = Image.new("RGB", (COLUMNS * THUMB_WIDTH, rows * (THUMB_HEIGHT + LABEL_HEIGHT)), "#f8f7f5")
        draw = ImageDraw.Draw(canvas)
        for offset, (path, width, height, _) in enumerate(batch):
            row, col = divmod(offset, COLUMNS)
            x = col * THUMB_WIDTH
            y = row * (THUMB_HEIGHT + LABEL_HEIGHT)
            try:
                thumb = thumbnail(path)
                canvas.paste(thumb, (x + (THUMB_WIDTH - thumb.width) // 2, y + (THUMB_HEIGHT - thumb.height) // 2))
                draw.rectangle((x, y, x + THUMB_WIDTH - 1, y + THUMB_HEIGHT - 1), outline="#d8d3cd")
                draw.text((x + 8, y + THUMB_HEIGHT + 6), short_label(path.name), fill="#102f58", font=font)
                draw.text((x + 8, y + THUMB_HEIGHT + 22), f"{width} × {height}", fill="#77716c", font=font)
            except OSError:
                draw.text((x + 8, y + 8), f"Unreadable: {short_label(path.name)}", fill="#9b3e16", font=font)
        sheet_number = start // PER_SHEET + 1
        canvas.save(OUTPUT / f"contact_sheet_{sheet_number:02d}.jpg", quality=88)

    print(f"Cataloged {len(records)} readable images into {math.ceil(len(records) / PER_SHEET)} contact sheets.")


if __name__ == "__main__":
    main()
