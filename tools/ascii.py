import sys
from PIL import Image

# usage: ascii.py image.png [x y w h] [cols]
# Hue-letter map so shapes read clearly: r=red o=orange y=yellow g=green
# c=cyan b=blue p=purple m=magenta w=white/gray k=near-black .=transparent-ish bg
path = sys.argv[1]
im = Image.open(path).convert('RGB')
if len(sys.argv) > 5:
    x, y, w, h = int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4]), int(sys.argv[5])
    im = im.crop((x, y, x + w, y + h))
cols = int(sys.argv[6]) if len(sys.argv) > 6 else 80
w, h = im.size
rows = max(1, int(cols * h / w / 2.1))
im = im.resize((cols, rows))
px = im.load()

def classify(r, g, b):
    lum = (0.3 * r + 0.6 * g + 0.1 * b) / 255
    mx, mn = max(r, g, b), min(r, g, b)
    if mx - mn < 28:
        if lum > 0.82:
            return 'W' if lum > 0.93 else 'w'
        if lum < 0.18:
            return 'k'
        return '.' if lum < 0.45 else ':'
    # hue
    import colorsys
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
    return c.upper() if lum > 0.55 else c

for y in range(rows):
    line = ""
    for x in range(cols):
        r, g, b = px[x, y]
        line += classify(r, g, b)
    print(line)
