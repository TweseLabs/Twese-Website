from pathlib import Path

from PIL import Image


source = Path("/home/ubuntu/screenshots/webdev-preview-root-1787959366048737860-3459.png")
destination = Path("/home/ubuntu/screenshots/collaboration-support-section.png")

with Image.open(source) as image:
    # Crop only the Collaboration & Support photographic section, excluding Recent Work and the CTA below.
    section = image.crop((0, 2730, 1440, 3328))
    section.save(destination, quality=96)

print(destination)
