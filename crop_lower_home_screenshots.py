from pathlib import Path
from PIL import Image

source = Path("/home/ubuntu/screenshots/webdev-preview-root-1787953779300805727-2577.png")
output_dir = Path("/home/ubuntu/screenshots/lower-home-review")
output_dir.mkdir(parents=True, exist_ok=True)

image = Image.open(source)
width, height = image.size

crops = {
    "recognition-band.png": (0, int(height * 0.625), width, int(height * 0.725)),
    "recent-work.png": (0, int(height * 0.685), width, int(height * 0.855)),
    "collaboration-footer-transition.png": (0, int(height * 0.805), width, height),
}

for filename, box in crops.items():
    image.crop(box).save(output_dir / filename, optimize=True)

print(f"source={source}")
print(f"dimensions={width}x{height}")
for filename in crops:
    print(output_dir / filename)
