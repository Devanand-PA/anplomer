import sys
from PIL import Image

# usage: ascii1x.py img.png x y w h [scale]
# Crops the region, magnifies with NEAREST (so no pixel is invented), then
# maps each pixel to a hue letter. Good for judging a sprite at true 1x.
path = sys.argv[1]
x, y, w, h = (int(v) for v in sys.argv[2:6])
scale = int(sys.argv[6]) if len(sys.argv) > 6 else 1
im = Image.open(path).convert('RGB').crop((x, y, x + w, y + h))
if scale > 1:
    im = im.resize((w * scale, h * scale), Image.NEAREST)

import colorsys
px = im.load()
RW, RH = im.size
for j in range(RH):
    line = ""
    for i in range(RW):
        r, g, b = px[i, j]
        lum = (0.3 * r + 0.6 * g + 0.1 * b) / 255
        mx, mn = max(r, g, b), min(r, g, b)
        if mx - mn < 30:
            line += 'W' if lum > 0.88 else 'w' if lum > 0.7 else 'k' if lum < 0.22 else '.' if lum < 0.5 else ':'
            continue
        hh, ss, vv = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
        deg = hh * 360
        if deg < 15 or deg >= 345:
            c = 'r'
        elif deg < 45:
            c = 'o'
        elif deg < 70:
            c = 'y'
        elif deg < 160:
            c = 'g'
        elif deg < 200:
            c = 'c'
        elif deg < 260:
            c = 'b'
        elif deg < 300:
            c = 'p'
        else:
            c = 'm'
        line += c.upper() if lum > 0.5 else c
    print(line)
