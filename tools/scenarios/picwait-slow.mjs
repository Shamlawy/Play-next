/* #333: the "a picture took over 2.5s" check (picWait) still catches a slow picture now that it finds on-screen pictures with an IntersectionObserver.
   A picture from a fake host answers after 3.5s; it's put on Home, then Home is opened.
     node tools/kit.mjs run tools/scenarios/picwait-slow.mjs --widths 412,859 */
export default async ({ p, w }) => {
  const png = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==", "base64");
  /* the app's picture fit swaps it for a sized copy (wsrv.nl): that copy is just as slow here */
  await p.page.route(u => /slowpic\.test/.test(decodeURIComponent(u.href)), async r => { await new Promise(res => setTimeout(res, 3500)); r.fulfill({ body: png, contentType: "image/png", headers: { "access-control-allow-origin": "*" } }); });
  await p.page.waitForTimeout(6000);   /* the checks start 8s in */
  await p.step("games", `goTab("games")`, 1500);
  await p.step("plant", `{ const i = document.createElement("img"); i.src = "https://slowpic.test/a.png?" + Date.now(); i.style.cssText = "position:fixed;left:40px;top:120px;width:120px;height:120px;z-index:99"; document.body.appendChild(i); goTab("home"); }`, 6000);
  await p.flush();
  const hit = p.reports.filter(r => /picture took/.test(r.msg || r.message || ""));
  console.log(`  ${hit.length ? "✓" : "✗"} ${w}px: slow picture ${hit.length ? "reported: " + (hit[0].msg || hit[0].message) : "NOT reported"}`);
  return hit.length > 0;
};
