# Builds the "what a game builds in you" demo page (a private claude.ai artifact) from shoot.mjs's screenshots:
#   SHOTS=tools/.cache/skills node tools/kit.mjs run tools/demos/skills/shoot.mjs --widths 412,859 --set '{"bubOff":true}'
#   python3 tools/demos/skills/build.py tools/.cache/skills OUT.html
import base64, io, sys, json
from PIL import Image
d, out = sys.argv[1], sys.argv[2]
def jpg(name, w):
    im = Image.open(f"{d}/{name}.png").convert("RGB"); im = im.resize((w, round(im.height * w / im.width)), Image.LANCZOS)
    b = io.BytesIO(); im.save(b, "JPEG", quality=72, optimize=True, progressive=True); return "data:image/jpeg;base64," + base64.b64encode(b.getvalue()).decode()
img = {}
for wd, px in (("412", 720), ("859", 1100)):
    for k in "ABC":
        for st in ("played", "notplayed"): img[f"{wd}-{k}-{st}"] = jpg(f"{wd}-{k}-{st}", px)
    img[f"{wd}-insights"] = jpg(f"{wd}-insights", px)
s = open(__file__.rsplit("/", 1)[0] + "/page.html").read().replace("{{IMG}}", json.dumps(img))
open(out, "w").write(s)
