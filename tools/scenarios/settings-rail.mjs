/* v296: Settings rail (look B). Settings and Customise are one scroll of open groups with an "On this page" icon rail;
   exactly one link is aria-current="location" and it is the group just under the sticky title (first group in the zone
   between the title and 40% of the sheet; the last group at the very bottom). A tap on a rail icon lands its group under
   the title. A tap on a group's title doesn't close it. Search hides the rail, Edit has none, the icons are drawn (svg).
     node tools/kit.mjs run tools/scenarios/settings-rail.mjs --widths 412,859,360      (SHOTS=dir saves pictures) */
export default async ({ p, w }) => {
  const bad = [], note = m => bad.push(m), shots = process.env.SHOTS;
  /* WebKit (webkit-check.yml's scenario input, the friend's iPhone) has no CDP touch: a click and a scroll instead */
  let touch = true;
  const tap = async (x, y) => { if (touch) try { return await p.tap(x, y); } catch (e) { touch = false; }
    /* WebKit's emulated mouse dropped later clicks altogether (no click event at all): click what sits at the spot */
    await p.page.evaluate(([x, y]) => { const el = document.elementFromPoint(x, y);   /* an svg path has no .click() in WebKit: a real click event, as a finger's would bubble */
      if (el) el.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true, clientX: x, clientY: y, view: window })); }, [x, y]); };
  const swipeUp = async () => { if (touch) try { return await p.swipe(p.w * .6, p.h * .75, p.w * .6, p.h * .3, 260); } catch (e) { touch = false; }
    await p.page.evaluate(d => document.getElementById("sheet").scrollBy(0, d), Math.round(p.h * .45)); };   /* mobile WebKit has no wheel either */
  const state = () => p.page.evaluate(() => {
    const sh = $("#sheet"), inn = $("#sheet-in"), head = inn.querySelector(".ehead"), foot = inn.querySelector(".efoot");
    const top = (parseFloat(getComputedStyle(head).top) || 0) + head.offsetHeight, bot = foot ? foot.offsetHeight : 0;
    const t0 = sh.getBoundingClientRect().top + top + 4, t1 = sh.getBoundingClientRect().top + top + Math.max(40, Math.round((sh.clientHeight - top - bot) * .4));
    const gs = [...inn.querySelectorAll(".srlbody > .setgroup")];
    const atEnd = sh.scrollTop > 0 && Math.ceil(sh.scrollTop + sh.clientHeight) >= sh.scrollHeight - 2;
    const want = atEnd ? gs[gs.length - 1] : gs.find(g => { const r = g.getBoundingClientRect(); return r.bottom > t0 && r.top < t1; });
    const cur = [...inn.querySelectorAll('.srlnav a[aria-current="location"]')];
    return { n: cur.length, cur: cur[0] && cur[0].hash.slice(1), want: want && want.id, y: Math.round(sh.scrollTop), max: sh.scrollHeight - sh.clientHeight };
  });
  const settle = () => p.page.waitForTimeout(450);

  await p.step("settings", `openSettings("app")`, 1400);
  const base = await p.page.evaluate(() => {
    const inn = $("#sheet-in"), nav = inn.querySelector(".srlnav[role=navigation]");
    return { srl: inn.classList.contains("srl"), label: nav && nav.getAttribute("aria-label"), links: nav ? nav.querySelectorAll("a").length : 0,
      groups: inn.querySelectorAll(".srlbody > .setgroup").length, closed: inn.querySelectorAll(".srlbody details.grp:not([open])").length,
      svgs: nav ? nav.querySelectorAll("a > svg").length : 0, gi: inn.querySelectorAll("details.grp .gi svg").length,
      quick: inn.querySelectorAll(".squick button > span > svg").length, sticky: nav && getComputedStyle(nav).position,
      drill: inn.classList.contains("drill"), hrefs: nav ? [...nav.querySelectorAll("a")].every(a => a.hash && document.getElementById(a.hash.slice(1))) : false };
  });
  if (!base.srl || base.label !== "On this page" || base.links < 10 || base.links !== base.groups || base.closed || base.svgs !== base.links
    || base.gi !== base.groups || base.quick < 8 || base.sticky !== "sticky" || base.drill || !base.hrefs) note("setup " + JSON.stringify(base));
  if (shots) await p.page.screenshot({ path: `${shots}/rail-top-${w}.png` });

  /* scroll down in steps: exactly one lit, the right one */
  const s0 = await state(); let hops = 0;
  for (let y = 0; y <= s0.max + 400; y += Math.max(260, Math.round(s0.max / 14))) {
    await p.page.evaluate(y => { $("#sheet").scrollTop = y; }, y); await settle();
    const s = await state();
    if (s.n !== 1) note(`${s.n} lit at y=${s.y}`);
    else if (s.want && s.cur !== s.want) note(`lit ${s.cur}, should be ${s.want} at y=${s.y}`);
    hops++;
  }
  /* swipe with a real finger too */
  await p.page.evaluate(() => { $("#sheet").scrollTop = 0; }); await settle();
  for (let k = 0; k < 4; k++) { await swipeUp(); await p.page.waitForTimeout(700); }
  { const s = await state(); if (s.n !== 1 || (s.want && s.cur !== s.want)) note(`after swipes: lit ${s.cur} (${s.n}), should be ${s.want}`); }
  if (shots) await p.page.screenshot({ path: `${shots}/rail-mid-${w}.png` });

  /* what happened, for a failure (WebKit runs on GitHub only): clicks on the rail, scrollTo calls on the sheet */
  await p.page.evaluate(() => {
    window.__srlLog = [];
    document.addEventListener("click", e => { const a = e.target.closest && e.target.closest(".srlnav a"); window.__srlLog.push("click " + (a ? a.hash : (e.target.className || e.target.tagName))); }, true);
    const sh = $("#sheet"); sh.scrollTo = function (o) { window.__srlLog.push("scrollTo " + JSON.stringify(o) + " from " + Math.round(this.scrollTop)); return Element.prototype.scrollTo.apply(this, arguments); };
  });
  const why = () => p.page.evaluate(() => { const l = window.__srlLog.splice(0); return l.join(" | ") + " | now " + Math.round($("#sheet").scrollTop) + "/" + ($("#sheet").scrollHeight - $("#sheet").clientHeight); });
  /* tap a rail icon: its group lands under the title and is the lit one */
  for (const id of ["sg-look-of-the-app", "sg-cloud-backup", "sg-nexi"]) {
    const box = await p.page.evaluate(id => { const a = document.querySelector(`.srlnav a[href="#${id}"]`); if (!a) return null; const r = a.getBoundingClientRect(); return { x: r.left + r.width / 2, y: r.top + r.height / 2 }; }, id);
    if (!box) { note("no rail link " + id); continue; }
    await tap(box.x, box.y); await p.page.waitForTimeout(1400);
    const r = await p.page.evaluate(id => {
      const g = document.getElementById(id), head = $("#sheet-in .ehead"), sh = $("#sheet");
      const top = (parseFloat(getComputedStyle(head).top) || 0) + head.offsetHeight;
      const cur = [...document.querySelectorAll('.srlnav a[aria-current="location"]')];
      const atEnd = Math.ceil(sh.scrollTop + sh.clientHeight) >= sh.scrollHeight - 2;
      return { off: Math.round(g.getBoundingClientRect().top - sh.getBoundingClientRect().top - top), n: cur.length, cur: cur[0] && cur[0].hash.slice(1), atEnd,
        tag: (document.getElementById("srltag") || {}).textContent || "" };
    }, id);
    const log = await why();
    if (r.n !== 1 || r.cur !== id) note(`tap ${id}: lit ${r.cur} (${r.n}) [${log}]`);
    if (!r.atEnd && Math.abs(r.off - 8) > 6) note(`tap ${id}: group ${r.off}px under the title (want 8) [${log}]`);
    if (!r.tag) note(`tap ${id}: no name shown`);
    if (shots && id === "sg-look-of-the-app") await p.page.screenshot({ path: `${shots}/rail-tap-${w}.png` });
  }

  /* a tap on a group's title keeps it open */
  {
    const box = await p.page.evaluate(() => { const s = document.querySelector("#sg-nexi details.grp > summary b"); const r = s.getBoundingClientRect(); return { x: r.left + 20, y: r.top + r.height / 2 }; });
    await tap(box.x, box.y); await p.page.waitForTimeout(500);
    const open = await p.page.evaluate(() => document.querySelector("#sg-nexi details.grp").open && $("#sheet-in").classList.contains("srl") && !$("#sheet-in").classList.contains("drill"));
    if (!open) note("tapping a group's title closed it");
  }

  /* search hides the rail; clearing brings it back */
  await p.page.evaluate(() => { const i = $("#sheet-in .ssch input"); i.value = "sound"; i.dispatchEvent(new Event("input")); });
  await p.page.waitForTimeout(300);
  if (await p.page.evaluate(() => getComputedStyle(document.querySelector(".srlnav")).display !== "none")) note("rail still shows while searching");
  await p.page.evaluate(() => { const i = $("#sheet-in .ssch input"); i.value = ""; i.dispatchEvent(new Event("input")); });

  /* Most used: a group shortcut scrolls there */
  await p.page.evaluate(() => { $("#sheet").scrollTop = 0; }); await settle(); await why();
  await p.page.evaluate(() => { const b = [...document.querySelectorAll(".squick button")].find(b => /Backup/.test(b.textContent)); b.click(); });
  await p.page.waitForTimeout(1500);
  { const s = await p.page.evaluate(() => { const g = $("#sg-cloud-backup"), sh = $("#sheet"); const r = g.getBoundingClientRect(); return r.top >= 0 && r.top < sh.clientHeight * .6; });
    if (!s) note(`Most used › Backup didn't bring Cloud backup into view [${await why()}]`); }

  /* a screen's Customise: its own groups only, rail beside them */
  await p.step("customise", `goTab("games"); openSettings("games")`, 1400);
  const cu = await p.page.evaluate(() => { const inn = $("#sheet-in"), nav = inn.querySelector(".srlnav"); return { srl: inn.classList.contains("srl"),
    links: nav ? nav.querySelectorAll("a").length : 0, groups: inn.querySelectorAll(".srlbody > .setgroup").length, app: inn.querySelectorAll('.setgroup[data-scope="app"]').length,
    one: inn.querySelectorAll('.srlnav a[aria-current="location"]').length }; });
  if (!cu.srl || cu.links !== cu.groups || cu.app || (cu.groups > 1 && cu.one !== 1)) note("customise " + JSON.stringify(cu));
  if (shots) await p.page.screenshot({ path: `${shots}/rail-customise-${w}.png` });

  /* Edit has no rail and still opens one section at a time */
  await p.step("edit", `closeEdit(); openEdit(S.games[0].id)`, 1400);
  const ed = await p.page.evaluate(() => ({ srl: $("#sheet-in").classList.contains("srl"), nav: !!document.querySelector("#sheet-in .srlnav"), gi: $("#sheet-in").querySelectorAll("details.grp .gi svg").length }));
  if (ed.srl || ed.nav || !ed.gi) note("edit " + JSON.stringify(ed));
  await p.step("close", `closeEdit()`, 600);

  const real = p.reports.filter(r => !/stutter|slow|lag/i.test((r.kind || "") + " " + (r.msg || r.message || "")));
  if (real.length) note("reports: " + real.map(r => r.msg || r.message).join("; "));
  if (p.errors.length) note("errors: " + p.errors.join("; "));
  console.log(`  ${bad.length ? "✗" : "✓"} ${w}px: ${base.links} groups, ${hops} scroll spots, 3 taps, customise ${cu.groups}${bad.length ? "\n    " + bad.join("\n    ") : ""}`);
  return !bad.length;
};
