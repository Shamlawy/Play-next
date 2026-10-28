/* Gate 3's Compose stability check (docs/ANDROID-PLAN.md "CI checks"): reads the Compose compiler's own report of the
   app's composables (Gradle with -Ppn.composeReports writes android/app/build/compose-reports) and fails when
   - a composable that can restart can't be skipped (it would redraw with its parent every time), or
   - a parameter Compose can only compare by identity (an unstable type: a list, a JSON-backed Game…) isn't in
     android/compose-identity.txt. Strong skipping skips such a call only when the very same object comes back, so a new
     one is either made stable (an immutable class, @Immutable/@Stable, remember the value) or listed with the reason
     in the commit, the way the audit's budgets work. A listed one that's gone fails too: the list never goes stale.
   android-check runs it after the build; by hand, after a full compile (an incremental one reports only what it
   recompiled):
     tools/gradle-retry.sh :app:compileReleaseKotlin --rerun -Ppn.composeReports && node tools/compose-check.mjs
   --write rewrites the list from the report (then say why in the commit). */
import fs from "node:fs";
import path from "node:path";

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), "..");
const REPORT = path.join(ROOT, "android/app/build/compose-reports/app-composables.txt");
const LIST = path.join(ROOT, "android/compose-identity.txt");

if (!fs.existsSync(REPORT)) {
  console.error(`compose-check: no report at ${path.relative(ROOT, REPORT)}; build with -Ppn.composeReports first`);
  process.exit(1);
}
const lines = fs.readFileSync(REPORT, "utf8").split("\n");

const notSkippable = [];
const identity = [];
let fn = "";
let restarts = false;
for (const l of lines) {
  const head = /^((?:\w+\s+)*?)(?:scheme\("[^"]*"\)\s*)?fun ([\w.]+)\(/.exec(l);
  if (head) {
    fn = head[2].replace(/^.*\./, "");
    const words = head[1].trim().split(/\s+/);
    restarts = words.includes("restartable");
    if (restarts && !words.includes("skippable")) notSkippable.push(fn);
    continue;
  }
  const p = /^ {2}(\S.*?)\s*$/.exec(l);
  // a composable that doesn't restart (one that returns a value) is never skipped, so how it compares doesn't matter
  if (!p || !fn || !restarts) continue;
  // "stable x: T", "unused x: T", "@static …" and "@dynamic …" defaults; a bare "x: T" is compared by identity
  const first = p[1].split(/\s+/)[0];
  if (first === "stable" || first === "unused") continue;
  identity.push(`${fn}.${p[1].replace(/\s*=\s*@\w+.*$/, "")}`);
}

if (!lines.some(l => / fun [\w.]*PlayNextRoot\(/.test(l))) {
  console.error("compose-check: the report doesn't have PlayNextRoot, so it's partial (an incremental compile). Compile with --rerun.");
  process.exit(1);
}

const now = [...new Set(identity)].sort();
if (process.argv.includes("--write")) {
  const head = "# Composable parameters Compose compares by identity (unstable types; tools/compose-check.mjs). Each was\n" +
    "# checked: the caller passes the same object until it really changes (a new backup, a new day, a new list).\n";
  fs.writeFileSync(LIST, head + now.join("\n") + "\n");
  console.log(`compose-check: wrote ${now.length} parameters to ${path.relative(ROOT, LIST)}`);
  process.exit(0);
}

const listed = fs.existsSync(LIST)
  ? fs.readFileSync(LIST, "utf8").split("\n").map(s => s.trim()).filter(s => s && !s.startsWith("#"))
  : [];
const fresh = now.filter(x => !listed.includes(x));
const gone = listed.filter(x => !now.includes(x));
let bad = false;
if (notSkippable.length) {
  bad = true;
  console.log(`::error::Composables that restart but can't be skipped (they redraw with their parent): ${notSkippable.join(", ")}`);
}
if (fresh.length) {
  bad = true;
  console.log(`::error::New composable parameters Compose can only compare by identity: ${fresh.join("; ")}. Make the type stable (immutable, @Immutable/@Stable, remember it) or add the line to android/compose-identity.txt and say why in the commit.`);
}
if (gone.length) {
  bad = true;
  console.log(`::error::Listed in android/compose-identity.txt but no longer in the report: ${gone.join("; ")}. Remove them (node tools/compose-check.mjs --write).`);
}
console.log(`compose-check: ${lines.filter(l => /(^| )fun [\w.]+\(/.test(l)).length} composables, ${notSkippable.length} not skippable, ${now.length} parameters compared by identity (${listed.length} listed)`);
process.exit(bad ? 1 : 0);
