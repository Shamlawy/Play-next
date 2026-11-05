// node tools/demos/now-bar/shoot.mjs → tools/.cache/shots/nowbar-A..E.png
import { createRequire } from "node:module";
const { chromium } = createRequire(import.meta.url)("playwright");
import { pathToFileURL } from "node:url";
import path from "node:path";
const file = pathToFileURL(path.resolve("tools/demos/now-bar/ideas.html")).href;
const b = await chromium.launch();
const p = await b.newPage({ viewport: { width: 950, height: 1100 }, deviceScaleFactor: 2 });
for (const i of "ABCDE") {
  await p.goto(file + "?i=" + i);
  await p.waitForTimeout(150);
  await p.screenshot({ path: `tools/.cache/shots/nowbar-${i}.png`, fullPage: true });
}
await b.close();
