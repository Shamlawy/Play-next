// Release number clash check (L27, v293): a PR that bumps APP_V must end up above main's APP_V, not on the same number.
// Two chats can both turn 290 into 291; the lines are identical, so git merges them with no conflict and one release is lost.
// Usage: node tools/appv-check.mjs --base=origin/main [--head=<sha>]   (exit 1 on a clash)
import { execFileSync } from "node:child_process";
const arg = (k, d) => (process.argv.find(a => a.startsWith(`--${k}=`)) || "").slice(k.length + 3) || d;
const base = arg("base", "origin/main"), head = arg("head", "HEAD");
const git = (...a) => execFileSync("git", a, { encoding: "utf8", stdio: ["ignore", "pipe", "ignore"], maxBuffer: 1 << 28 });
const appV = ref => { try { return +(git("show", `${ref}:index.html`).match(/^\s*const APP_V = "(\d+)"/m) || [])[1] || 0; } catch { return 0; } };
const fork = git("merge-base", base, head).trim();
const [was, mine, main] = [appV(fork), appV(head), appV(base)];
if (!mine || !main) { console.log(`appv-check: APP_V not found (head ${mine}, ${base} ${main}), skipped`); process.exit(0); }
if (mine === was) { console.log(`appv-check: no release in this branch (APP_V ${mine}), ok`); process.exit(0); }
if (mine > main) { console.log(`appv-check: this release is v${mine}, ${base} is at v${main}, ok`); process.exit(0); }
console.log(`appv-check: this branch numbers its release v${mine} (from v${was}), but ${base} is already at v${main}.`);
console.log(`Merge ${base} in, then make this release v${main + 1}: APP_V, its WHATS_NEW entries' v and its docs/LOG.md line.`);
process.exit(1);
