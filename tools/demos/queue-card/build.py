# Builds the round-3 open Queue card demo page (published as a private claude.ai artifact).
# Pictures come from repo-root files only: python3 tools/demos/queue-card/build.py OUT.html
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
s = open(__file__.rsplit("/", 1)[0] + "/round3.html").read()
for k, v in m.items(): s = s.replace("{{%s}}" % k, v)
open(sys.argv[1], "w").write(s)
