import os
from PIL import Image
base = os.path.dirname(__file__)
im = Image.open(os.path.join(base, "screenshots", "mockup-dashboard-v2.png"))
im.crop((150, 90, 700, 420)).save(os.path.join(base, "screenshots", "hero-crop-check.png"))
print("done", im.size)
