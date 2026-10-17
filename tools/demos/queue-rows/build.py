# Builds the closed Queue row demo page (published as a private claude.ai artifact).
# Pictures come from repo-root files only, logos from tools/standin-logos.mjs (made-up), titles from tools/seed.mjs:
#   python3 tools/demos/queue-rows/build.py OUT.html [round1.html]
import base64, io, json, re, subprocess, sys, colorsys
from PIL import Image
import numpy as np
R = __file__.rsplit("/tools/", 1)[0] + "/"
cast = Image.open(R + "FEFT_cast.webp").convert("RGB"); ka = Image.open(R + "fefw-key-art.jpg").convert("RGB"); W, H = ka.size
def hue(im, d):
    if not d: return im
    h, s, v = im.convert("HSV").split(); h = h.point(lambda x: (x + int(d * 255 / 360)) % 256)
    return Image.merge("HSV", (h, s, v)).convert("RGB")
def jpg(im, size, q=72):
    im = im.copy(); w, h = im.size; k = max(size[0] / w, size[1] / h); im = im.resize((round(w * k), round(h * k)), Image.LANCZOS)
    w, h = im.size; im = im.crop(((w - size[0]) // 2, (h - size[1]) // 2, (w - size[0]) // 2 + size[0], (h - size[1]) // 2 + size[1]))
    b = io.BytesIO(); im.save(b, "JPEG", quality=q, optimize=True, progressive=True); return "data:image/jpeg;base64," + base64.b64encode(b.getvalue()).decode()
def col(im):
    # roughly the app's colourFrom: the most common lively hue in the middle, kept dark enough for white text
    w_, h_ = im.size; im = im.crop((int(w_ * .2), int(h_ * .2), int(w_ * .8), int(h_ * .8)))
    a = np.asarray(im.resize((48, 64))).reshape(-1, 3).astype(float) / 255; mx, mn = a.max(1), a.min(1); s_ = (mx - mn) / (mx + 1e-6)
    ok = (s_ > .3) & (mx > .25) & (mx < .97); p = a[ok] if ok.sum() > 20 else a; wt = s_[ok] ** 2 if ok.sum() > 20 else None
    h = np.array([colorsys.rgb_to_hsv(*x)[0] for x in p]); b = np.bincount((h * 18).astype(int) % 18, weights=wt, minlength=18).argmax()
    c = p[((h * 18).astype(int) % 18) == b].mean(0); hh, ss, vv = colorsys.rgb_to_hsv(*c); c = colorsys.hsv_to_rgb(hh, max(ss, .55), min(max(vv, .55), .8))
    return "#%02x%02x%02x" % tuple(int(x * 255) for x in c)
# (hero crop, cover crop, hue turn) for 8 made-up games
SRC = [
    (ka.crop((0, int(H * .05), W, int(H * .05) + int(W * 9 / 21))), ka.crop((int(W / 2 - H / 3), 0, int(W / 2 + H / 3), H)), 0),
    (cast.crop((0, 60, 1000, 480)), cast.crop((0, 0, 375, 563)), 150),
    (cast.crop((0, 120, 1000, 540)), cast.crop((780, 160, 980, 460)), 200),
    (ka.crop((0, int(H * .3), W, int(H * .3) + int(W * 9 / 21))), ka.crop((2045, 0, 3445, 2100)), 95),
    (cast.crop((0, 30, 1000, 450)), cast.crop((300, 0, 675, 563)), 60),
    (ka.crop((0, int(H * .45), W, int(H * .45) + int(W * 9 / 21))), ka.crop((1000, 600, 2400, 2700)), 300),
    (cast.crop((0, 100, 1000, 520)), cast.crop((560, 0, 935, 563)), 250),
    (ka.crop((0, int(H * .15), W, int(H * .15) + int(W * 9 / 21))), ka.crop((0, 0, 1400, 2100)), 25),
]
m = {}
for i, (hr, cv, d) in enumerate(SRC):
    hr, cv = hue(hr, d), hue(cv, d)
    m["h%d" % i] = jpg(hr, (960, 400)); m["c%d" % i] = jpg(cv, (160, 240)); m["col%d" % i] = col(cv)
# the header card's picture (the owner likes it as it is)
m["hdr"] = jpg(ka.crop((0, int(H * .02), W, int(H * .02) + int(W * .42))), (1100, 460), 68)
logos = json.loads(subprocess.check_output(["node", "-e", "import('" + R + "tools/standin-logos.mjs').then(m => console.log(JSON.stringify(m.LOGOS.map(l => l.url))))"]))
# trim the see-through padding the way lfRule's crop would (the app crops each logo to its ink)
VB = {4: "0 22 640 100", 5: "236 332 400 206"}
from urllib.parse import unquote, quote
for i, u in enumerate(logos):
    if i in VB:
        s = unquote(u.split(",", 1)[1]); s = re.sub(r'width="\d+" height="\d+" viewBox="[^"]+"', 'width="%s" height="%s" viewBox="%s"' % (VB[i].split()[2], VB[i].split()[3], VB[i]), s, 1)
        u = "data:image/svg+xml;charset=utf-8," + quote(s)
    m["l%d" % i] = u
print({k: v for k, v in m.items() if k.startswith("col")})
s = open(__file__.rsplit("/", 1)[0] + "/" + (sys.argv[2] if len(sys.argv) > 2 else "round1.html")).read()
for k, v in m.items(): s = s.replace("{{%s}}" % k, v)
open(sys.argv[1], "w").write(s)
