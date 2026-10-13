#!/usr/bin/env node
/* Bug finder for index.html (v279, the owner: "kill all bugs and make sure no new ones emerge"). ESLint's correctness rules run
   over every <script> block. The blocks share one global scope (a classic script sees every other script's top-level names),
   so the names declared at the top level of any block are known globals: a name that is in none of them and not in the
   browser is a real "not defined" crash waiting to happen.

     node tools/lint.mjs              every finding, grouped by rule (exit 1 if any)
     node tools/lint.mjs --count      just the number (tools/audit.mjs counts it as `lint`)
     node tools/lint.mjs --file x     another copy

   ESLint isn't part of the repo: it's found in node_modules, the global modules, or installed with
   `npm i --no-save eslint@9.36.0 globals@16.4.0` (app-check.yml does that). */
import fs from "fs";
import path from "path";
import { createRequire } from "module";
import { execFileSync } from "child_process";
import { fileURLToPath } from "url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const args = process.argv.slice(2), opt = k => { const i = args.indexOf("--" + k); return i >= 0 ? args[i + 1] : undefined; };
const FILE = path.resolve(ROOT, opt("file") || "index.html");

function need(name) {
  const tries = [path.join(ROOT, "node_modules"), "/tmp/claude-0/lint/node_modules"];
  try { tries.push(execFileSync("npm", ["root", "-g"], { encoding: "utf8" }).trim()); } catch (e) {}
  tries.push("/opt/node22/lib/node_modules");
  for (const t of tries) { try { return createRequire(path.join(t, "noop.js"))(name); } catch (e) {} }
  console.error(`${name} not found: npm i --no-save eslint@9.36.0 globals@16.4.0`); process.exit(2);
}
const { Linter } = need("eslint"), globals = need("globals");
const acorn = need("acorn");

/* the RULES: only ones that mean "this is a bug", none about style */
const RULES = {
  "no-undef": "error", "no-unused-vars": ["error", { vars: "local", args: "none", caughtErrors: "none", ignoreRestSiblings: true }],
  "no-dupe-keys": "error", "no-duplicate-case": "error", "no-unreachable": "error", "no-self-assign": "error", "no-self-compare": "error",
  "valid-typeof": "error", "no-cond-assign": ["error", "except-parens"], "no-const-assign": "error", "no-dupe-else-if": "error",
  "no-unsafe-negation": "error", "no-unsafe-optional-chaining": "error", "use-isnan": "error",
  "no-loss-of-precision": "error", "no-shadow-restricted-names": "error", "getter-return": "error", "no-setter-return": "error",
  "no-async-promise-executor": "error", "no-compare-neg-zero": "error", "no-empty-character-class": "error", "no-invalid-regexp": "error",
  "no-useless-backreference": "error", "for-direction": "error", "no-dupe-args": "off", "no-unsafe-finally": "error",
  "no-constant-binary-expression": "error", "no-unused-private-class-members": "error", "no-dupe-class-members": "error",
  "no-redeclare": ["error", { builtinGlobals: false }], "no-misleading-character-class": "error", "no-obj-calls": "error",
};

const html = fs.readFileSync(FILE, "utf8");
const blocks = [];
for (const m of html.matchAll(/<script(\s[^>]*)?>([\s\S]*?)<\/script>/g)) {
  if (m[1] && /\bsrc=/.test(m[1])) continue;
  const at = m.index + m[0].indexOf(m[2], 7);
  blocks.push({ code: m[2], line: html.slice(0, at).split("\n").length });
}
/* every top-level name of every block, plus window.x = … and bare assignments to never-declared names at the top level */
const appGlobals = {};
for (const b of blocks) {
  let ast; try { ast = acorn.parse(b.code, { ecmaVersion: "latest", sourceType: "script" }); } catch (e) { continue; }
  for (const n of ast.body) {
    if (n.type === "FunctionDeclaration" || n.type === "ClassDeclaration") appGlobals[n.id.name] = "writable";
    if (n.type === "VariableDeclaration") for (const d of n.declarations) for (const id of names(d.id)) appGlobals[id] = "writable";
  }
  for (const m of b.code.matchAll(/\bwindow\.([A-Za-z_$][\w$]*)\s*=(?!=)/g)) appGlobals[m[1]] = "writable";
}
function names(p) { if (!p) return []; if (p.type === "Identifier") return [p.name]; if (p.type === "ObjectPattern") return p.properties.flatMap(x => names(x.value || x.argument)); if (p.type === "ArrayPattern") return p.elements.flatMap(names); if (p.type === "AssignmentPattern") return names(p.left); if (p.type === "RestElement") return names(p.argument); return []; }

const linter = new Linter();
const config = [{ languageOptions: { ecmaVersion: "latest", sourceType: "script", globals: { ...globals.browser, ...globals.serviceworker, ...appGlobals } }, rules: RULES }];
const found = [];
for (const b of blocks) {
  for (const m of linter.verify(b.code, config)) {
    if (!m.ruleId) { found.push({ rule: "parse", line: b.line + m.line - 1, msg: m.message }); continue; }
    /* a top-level name in a block that nothing else in the file uses is audit.mjs's deadCode, not a lint finding */
    found.push({ rule: m.ruleId, line: b.line + m.line - 1, msg: m.message });
  }
}
/* the other two code files: the service worker, and the helper (a Cloudflare Worker, an ES module) */
if (!opt("file")) for (const [file, sourceType, g] of [["sw.js", "script", globals.serviceworker], ["worker/helper.js", "module", { ...globals.serviceworker, ...globals.worker }]]) {
  const p = path.join(ROOT, file); if (!fs.existsSync(p)) continue;
  for (const m of linter.verify(fs.readFileSync(p, "utf8"), [{ languageOptions: { ecmaVersion: "latest", sourceType, globals: g }, rules: RULES }]))
    found.push({ rule: m.ruleId || "parse", line: file + ":" + m.line, msg: m.message });
}
if (args.includes("--count")) { console.log(found.length); process.exit(0); }
const by = new Map(); for (const f of found) (by.get(f.rule) || by.set(f.rule, []).get(f.rule)).push(f);
console.log(`\n== lint (${path.basename(FILE)}: ${blocks.length} scripts, ${Object.keys(appGlobals).length} shared names) ==`);
for (const [rule, list] of [...by].sort((a, b) => b[1].length - a[1].length)) {
  console.log(`  ${rule} ×${list.length}`);
  for (const f of list.slice(0, args.includes("--all") ? 1e9 : 25)) console.log(`      line ${f.line}: ${f.msg}`);
}
console.log(found.length ? `\n${found.length} finding(s).` : "  ✓ nothing found");
process.exit(found.length ? 1 : 0);
