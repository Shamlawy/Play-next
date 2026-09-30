// Files the app's automatic problem reports (collected by Nexi's helper) as GitHub issues.
// One issue per problem, titled "... [bug:<sig>]" with the label auto-bug. A problem that happens again
// gets a comment on its issue (and the issue reopens if it was closed). Runs from .github/workflows/bugs.yml.
// The helper's BUG_KEY is sha256(CLOUDFLARE_API_TOKEN + ":play-next-bugs"); deploy-helper.yml sets the same value.
import { createHash } from "node:crypto";

const HELPER = process.env.HELPER_URL || "https://playnext-helper.hussamnabil48.workers.dev";
const API = process.env.GH_API || "https://api.github.com";
const REPO = process.env.REPO, GH = process.env.GH_TOKEN;
const CF = String(process.env.CF_TOKEN || "").replace(/\s+/g, "");
const LABEL = "auto-bug", MAX_NEW = 15;
if (!CF) { console.log("::warning::No CLOUDFLARE_API_TOKEN secret, so there's no key to read the reports with."); process.exit(0); }
const KEY = createHash("sha256").update(CF + ":play-next-bugs").digest("hex");

const helper = (path, body) => fetch(HELPER + path, { method: body ? "POST" : "GET", headers: { Authorization: "Bearer " + KEY, "Content-Type": "application/json" }, body: body && JSON.stringify(body) });
const gh = async (path, method, body) => {
  const r = await fetch(API + path, { method: method || "GET", headers: { Authorization: "Bearer " + GH, Accept: "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28", "User-Agent": "play-next-bugs" }, body: body && JSON.stringify(body) });
  if (!r.ok && r.status !== 422) throw new Error(method + " " + path + " → " + r.status + " " + (await r.text()).slice(0, 200));
  return r.status === 204 ? null : r.json();
};

const r = await helper("/bugs");
if (r.status === 503) { console.log("::warning::The helper has no BUG_KEY yet. It's set when deploy-helper runs (push to worker/** or run it by hand)."); process.exit(0); }
if (!r.ok) { console.log("::error::The helper said " + r.status + " " + (await r.text()).slice(0, 200)); process.exit(1); }
const bugs = (await r.json()).bugs || [];
console.log(bugs.length + " problem(s) with new reports");
if (!bugs.length) process.exit(0);

await gh(`/repos/${REPO}/labels`, "POST", { name: LABEL, color: "d73a4a", description: "Reported automatically by the app" });
const issues = [];
for (let page = 1; page <= 5; page++) {
  const got = await gh(`/repos/${REPO}/issues?labels=${LABEL}&state=all&per_page=100&page=${page}`);
  issues.push(...got); if (got.length < 100) break;
}
const bySig = new Map();
for (const i of issues) { const m = /\[bug:([0-9a-f]{8})\]/.exec(i.title); if (m && !bySig.has(m[1])) bySig.set(m[1], i); }

const KIND = { error: "Crash", promise: "Crash", layout: "Cut off", slow: "Slow", helper: "Helper", tour: "Tour", user: "Reported" };
const when = t => new Date(t).toISOString().replace("T", " ").slice(0, 16) + " UTC";
const fence = s => "```\n" + String(s || "").replace(/```/g, "ˋˋˋ") + "\n```";
const body = b => {
  const s = b.sample || {};
  return [
    `**${KIND[b.kind] || b.kind}** on **${s.screen || "?"}**${s.note ? `\n\n> ${s.note.replace(/\n/g, "\n> ")}` : ""}`,
    fence(b.msg),
    s.at ? `Where: \`${s.at}\`` : "",
    s.stack ? (b.kind === "user" ? "What was at the spot they tapped:\n" : b.kind === "slow" ? "Timing:\n" : "Stack:\n") + fence(s.stack) : "",
    `| | |\n|---|---|\n| Times | ${b.n} |\n| Versions | ${(b.vs || []).join(", ")} |\n| Phones | ${(b.devs || []).join(", ")} |\n| Screens | ${(b.screens || []).join(", ")} |\n| Latest | ${s.w}×${s.h} @${s.dpr}x, ${s.style || "clean"} style |\n| First / last | ${when(b.first)} / ${when(b.last)} |`,
    s.crumbs && s.crumbs.length ? "What happened just before:\n" + fence(s.crumbs.join("\n")) : "",
    "_Sent automatically by the app. No game titles, scores or notes are included._"
  ].filter(Boolean).join("\n\n");
};

const acked = {}; let made = 0;
for (const b of bugs) {
  const i = bySig.get(b.sig), s = b.sample || {};
  if (!i) {
    if (made >= MAX_NEW) continue;
    const title = `🐞 ${KIND[b.kind] || b.kind}: ${String(b.msg).replace(/\s+/g, " ").slice(0, 80)} [bug:${b.sig}]`;
    const n = await gh(`/repos/${REPO}/issues`, "POST", { title, body: body(b), labels: [LABEL] });
    console.log("new issue #" + n.number + " " + title); made++;
  } else {
    const more = b.n - (b.seen || 0);
    if (i.state === "closed") await gh(`/repos/${REPO}/issues/${i.number}`, "PATCH", { state: "open" });
    await gh(`/repos/${REPO}/issues/${i.number}/comments`, "POST", { body:
      `${i.state === "closed" ? "**Happened again after it was closed.** " : ""}Seen ${more} more time${more === 1 ? "" : "s"} (${b.n} in total). Latest: v${s.v} on ${s.dev}, ${s.screen}, ${s.w}×${s.h}.${s.note ? `\n\n> ${s.note}` : ""}` });
    console.log("updated #" + i.number);
  }
  acked[b.sig] = b.n;
}
const a = await helper("/bugs/ack", { sigs: acked });
console.log("acked", await a.text());
