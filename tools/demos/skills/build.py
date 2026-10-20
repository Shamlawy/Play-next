# Builds the "what a game builds in you" demo page (a private claude.ai artifact) from shoot.mjs's screenshots:
#   SHOTS=tools/.cache/skills node tools/kit.mjs run tools/demos/skills/shoot.mjs --widths 412,859 --set '{"bubOff":true}'
#   python3 tools/demos/skills/build.py tools/.cache/skills OUT.html [games.json]   (the same DATA file, for the game names)
import base64, io, sys, json
from PIL import Image
d, out = sys.argv[1], sys.argv[2]
def jpg(name, w):
    im = Image.open(f"{d}/{name}.png").convert("RGB"); im = im.resize((w, round(im.height * w / im.width)), Image.LANCZOS)
    b = io.BytesIO(); im.save(b, "JPEG", quality=72, optimize=True, progressive=True); return "data:image/jpeg;base64," + base64.b64encode(b.getvalue()).decode()
games = json.load(open(sys.argv[3]))["games"] if len(sys.argv) > 3 else [{"key": "played", "title": "Made-up RPG", "played": 1}, {"key": "notplayed", "title": "Same game, not played", "played": 0}]
img = {}
for wd, px in (("412", 720), ("859", 1100)):
    for k in "ABC":
        for g in games: img[f"{wd}-{k}-{g['key']}"] = jpg(f"{wd}-{k}-{g['key']}", px)
    img[f"{wd}-insights"] = jpg(f"{wd}-insights", px)
s = open(__file__.rsplit("/", 1)[0] + "/page.html").read().replace("{{IMG}}", json.dumps(img)).replace("{{GAMES}}", json.dumps([{"key": g["key"], "title": g.get("title", g["key"]), "played": g["played"]} for g in games]))
open(out, "w").write(s)
