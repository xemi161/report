import sys
from PIL import Image

path = sys.argv[1]
im = Image.open(path).convert("RGB")
w, h = im.size
px = im.load()

bg = px[10, h - 10]

def close(c1, c2, tol=4):
    return all(abs(a - b) <= tol for a, b in zip(c1, c2))

# scan upward from bottom to find last row that differs from bg
last_content_row = 0
for y in range(h - 1, -1, -1):
    row_has_content = False
    for x in range(0, w, 4):  # sample every 4px for speed
        if not close(px[x, y], bg):
            row_has_content = True
            break
    if row_has_content:
        last_content_row = y
        break

crop_h = min(h, last_content_row + 40)
im.crop((0, 0, w, crop_h)).save(path)
print(f"{path}: cropped to {w}x{crop_h}")
