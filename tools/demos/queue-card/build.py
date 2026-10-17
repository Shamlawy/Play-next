# Builds the latest open Queue card demo page (published as a private claude.ai artifact).
# Pictures come from repo-root files only: python3 tools/demos/queue-card/build.py OUT.html [roundN.html]
import base64, io, sys
from PIL import Image, ImageFilter
import numpy as np
R = __file__.rsplit("/tools/", 1)[0] + "/"
cast = Image.open(R + "FEFT_cast.webp").convert("RGB"); ka = Image.open(R + "fefw-key-art.jpg").convert("RGB"); W, H = ka.size
def jpg(im, size, q=70):
    b = io.BytesIO(); im.resize(size, Image.LANCZOS).save(b, "JPEG", quality=q, optimize=True, progressive=True); return "data:image/jpeg;base64," + base64.b64encode(b.getvalue()).decode()
def logo():
    c = ka.crop((0, int(H * .735), W, int(H * .985))); a = np.asarray(c).astype(float)
    lum = a @ [.299, .587, .114]; mx, mn = a.max(-1), a.min(-1); sat = (mx - mn) / (mx + 1)
    al = np.clip((lum - 178) / 44, 0, 1) * np.clip((.42 - sat) / .2, 0, 1)
    A = Image.fromarray((al * 255).astype("uint8")).filter(ImageFilter.MedianFilter(5)); c.putalpha(A)
    c = c.crop(A.point(lambda v: 255 if v > 60 else 0).getbbox()); c.thumbnail((640, 200), Image.LANCZOS)
    b = io.BytesIO(); c.save(b, "WEBP", quality=88, method=6); return "data:image/webp;base64," + base64.b64encode(b.getvalue()).decode()
m = {"h1": jpg(cast, (960, 540)), "h2": jpg(cast.crop((0, 40, 560, 355)), (560, 315)), "h3": jpg(cast.crop((440, 40, 1000, 355)), (560, 315)),
     "h4": jpg(cast.crop((150, 0, 850, 394)), (560, 315)), "c1": jpg(ka.crop((int(W / 2 - H / 3), 0, int(W / 2 + H / 3), H)), (160, 240)),
     "c2": jpg(ka.crop((0, 0, 1400, 2100)), (160, 240)), "c3": jpg(ka.crop((2045, 0, 3445, 2100)), (160, 240)), "c4": jpg(ka.crop((1000, 600, 2400, 2700)), (160, 240)), "logo1": logo()}
m["hk"] = jpg(ka.crop((0, 0, W, int(W * 9 / 16))), (960, 540))
def col(im):
    # the cover's own colour, roughly like the app's colourFrom: the most common lively hue, kept dark enough for white text
    w_, h_ = im.size; im = im.crop((int(w_ * .2), int(h_ * .2), int(w_ * .8), int(h_ * .8)))  # the middle, where the subject is
    a = np.asarray(im.resize((48, 64))).reshape(-1, 3).astype(float) / 255; mx, mn = a.max(1), a.min(1); s_ = (mx - mn) / (mx + 1e-6)
    ok = (s_ > .3) & (mx > .25) & (mx < .97); p = a[ok] if ok.sum() > 20 else a
    import colorsys; h = np.array([colorsys.rgb_to_hsv(*x)[0] for x in p]); b = np.bincount((h * 18).astype(int) % 18, weights=s_[ok] ** 2 if ok.sum() > 20 else None, minlength=18).argmax()
    c = p[((h * 18).astype(int) % 18) == b].mean(0); hh, ss, vv = colorsys.rgb_to_hsv(*c); c = colorsys.hsv_to_rgb(hh, max(ss, .55), min(max(vv, .55), .8))
    return "#%02x%02x%02x" % tuple(int(x * 255) for x in c)
crops = {"c1": ka.crop((int(W / 2 - H / 3), 0, int(W / 2 + H / 3), H)), "c2": cast.crop((0, 0, 375, 563)), "c3": cast.crop((780, 160, 980, 460)), "c4": ka.crop((2045, 0, 3445, 2100))}
for k, im in crops.items(): m["col_" + k] = col(im)
for k in ("c2", "c3", "c4"): m[k + "x"] = jpg(crops[k], (160, 240))
print({k: v for k, v in m.items() if k.startswith("col_")})
s = open(__file__.rsplit("/", 1)[0] + "/" + (sys.argv[2] if len(sys.argv) > 2 else "round6.html")).read()
for k, v in m.items(): s = s.replace("{{%s}}" % k, v)
open(sys.argv[1], "w").write(s)
