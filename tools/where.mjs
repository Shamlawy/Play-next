#!/usr/bin/env node
/* Questions about index.html's code, answered from a real parse (acorn) of its ~90 scripts. docs/MAP.md
   (node tools/map.mjs) is the overview; this answers one question at a time:
     node tools/where.mjs renderHome      where it's defined, every later script that wraps or reassigns it, `renderHome.x` uses
     node tools/where.mjs --at=17160      which script / function a line is in, and what wraps that function later
     node tools/where.mjs --check         hazards from the hard-won rules (exit 1): a call to a function that only exists
                                          inside another script's scope (v237), state read off a wrapped function (v198),
                                          assigning a top-level const (v216), wrapping goTab / goSub / openFull again
                                          instead of onNav (v269); --base=<git ref> = only the ones new since that build
     node tools/where.mjs --json [--at=N] the whole map, or the functions around line N (the learn mod reads this)
   Cached in tools/.cache/ by index.html's size + mtime. */
import fs from "fs";
import path from "path";
import { execFileSync } from "child_process";
import { createRequire } from "module";
import { fileURLToPath } from "url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
/* PN_MAP_FILE: map another copy (--check --base maps the old build with it) */
const FILE = process.env.PN_MAP_FILE || path.join(ROOT, "index.html");
const CACHE = path.join(ROOT, "tools", ".cache", process.env.PN_MAP_FILE ? "where-other.json" : "where.json");

function acorn() {
  const tries = [path.join(ROOT, "node_modules")];
  try { tries.push(execFileSync("npm", ["root", "-g"], { encoding: "utf8" }).trim()); } catch (e) {}
  tries.push("/opt/node-tools/node_modules", "/opt/node22/lib/node_modules");
  for (const t of tries) { try { return createRequire(path.join(t, "noop.js"))("acorn"); } catch (e) {} }
  throw new Error("acorn not found (npm i -g acorn)");
}

/* ---------- build ---------- */
function build() {
  const src = fs.readFileSync(FILE, "utf8"), lines = src.split("\n");
  const { parse } = acorn();
  const scripts = [];
  const re = /<script>([\s\S]*?)<\/script>/g;
  let m;
  while ((m = re.exec(src))) {
    const startLine = src.slice(0, m.index).split("\n").length;   /* line of <script> */
    const code = m[1], endLine = startLine + code.split("\n").length - 1;
    const head = (code.match(/\/\*\s*([^\n]{0,140})/) || [])[1] || "";
    scripts.push({ i: scripts.length + 1, start: startLine, end: endLine, kb: Math.round(code.length / 1024), head: head.replace(/\*\/.*$/, "").trim(), code, off: startLine - 1 });
  }
  const defs = {};       /* name -> [{s, line, kind, scoped}]: top-level names, and functions kept inside a script's own scope */
  const wraps = {};      /* name -> [{s, line, text, fn}]: assignments to a global name (fn: a function or a call = a wrapper) */
  const props = {};      /* name -> [{s, line, prop}]: `globalFn.prop` (v198) */
  const refs = [];       /* unresolved calls, for v237: {s, name, line, guarded} */
  const fnRanges = [];   /* {s, name, from, to} for --at */
  const constTop = {};   /* top-level const -> line (v216) */
  const add = (o, k, v) => (o[k] ||= []).push(v);
  const asts = [];
  /* names bound by a pattern (params, destructuring) */
  const bound = (x, out = []) => { if (!x) return out; if (x.type === "Identifier") out.push(x.name); else if (x.type === "ObjectPattern") x.properties.forEach(q => bound(q.value || q.argument, out));
    else if (x.type === "ArrayPattern") x.elements.forEach(e => bound(e, out)); else if (x.type === "AssignmentPattern") bound(x.left, out); else if (x.type === "RestElement") bound(x.argument, out); return out; };
  const isFn = n => n && /^(FunctionExpression|ArrowFunctionExpression)$/.test(n.type);
  const kids = n => { const o = []; for (const k in n) { if (k === "loc" || k === "start" || k === "end") continue; const v = n[k];
    if (Array.isArray(v)) v.forEach(c => c && typeof c.type === "string" && o.push(c)); else if (v && typeof v.type === "string") o.push(v); } return o; };
  /* var + function hoisting inside one function body (not into nested functions) */
  const hoisted = (body, out) => { for (const c of kids(body)) {
    if (/Function/.test(c.type) && c.type !== "FunctionDeclaration") continue;
    if (c.type === "FunctionDeclaration") { if (c.id) out.add(c.id.name); continue; }
    if (c.type === "VariableDeclaration" && c.kind === "var") c.declarations.forEach(d => bound(d.id).forEach(n => out.add(n)));
    if (c.type === "ClassExpression" || c.type === "ClassDeclaration") { if (c.type === "ClassDeclaration" && c.id) out.add(c.id.name); }
    hoisted(c, out); } return out; };
  const lexical = (stmts, out) => { for (const c of stmts || []) {
    if (c.type === "VariableDeclaration" && c.kind !== "var") c.declarations.forEach(d => bound(d.id).forEach(n => out.add(n)));
    if ((c.type === "ClassDeclaration" || c.type === "FunctionDeclaration") && c.id) out.add(c.id.name); } return out; };

  /* pass 1: top-level names of every script (the shared global scope) */
  for (const sc of scripts) {
    let ast;
    try { ast = parse(sc.code, { ecmaVersion: "latest", sourceType: "script", locations: true, allowReturnOutsideFunction: true }); }
    catch (e) { sc.parseError = e.message; asts.push(null); continue; }
    asts.push(ast);
    const L = n => n.loc.start.line + sc.off, LE = n => n.loc.end.line + sc.off;
    for (const n of ast.body) {
      if (n.type === "FunctionDeclaration" && n.id) { add(defs, n.id.name, { s: sc.i, line: L(n), kind: "function", scoped: false }); fnRanges.push({ s: sc.i, name: n.id.name, from: L(n), to: LE(n) }); }
      if (n.type === "ClassDeclaration" && n.id) add(defs, n.id.name, { s: sc.i, line: L(n), kind: "class", scoped: false });
      if (n.type === "VariableDeclaration") for (const d of n.declarations) for (const nm of bound(d.id)) {
        add(defs, nm, { s: sc.i, line: L(d), kind: isFn(d.init) ? n.kind + " fn" : n.kind, scoped: false });
        if (n.kind === "const") constTop[nm] = L(d);
        if (isFn(d.init)) fnRanges.push({ s: sc.i, name: nm, from: L(d), to: LE(d) });
      }
    }
    /* var inside a top-level block still lands on window */
    for (const n of ast.body) if (n.type !== "FunctionDeclaration" && n.type !== "VariableDeclaration") hoisted(n, new Set()).forEach(nm => { if (!defs[nm]) add(defs, nm, { s: sc.i, line: L(n), kind: "var (block)", scoped: false }); });
  }
  const global = new Set(Object.entries(defs).filter(([, a]) => a.some(d => !d.scoped)).map(([k]) => k));

  /* pass 2: walk each script with a scope chain, so a local `line` or `pace` is never mistaken for the global one */
  scripts.forEach((sc, si) => {
    const ast = asts[si]; if (!ast) return;
    const L = n => n.loc.start.line + sc.off, LE = n => n.loc.end.line + sc.off;
    const codeLines = sc.code.split("\n");
    const typeofd = new Set();
    const local = (name, chain) => chain.some(s => s.has(name));
    const visit = (n, chain, inFn) => {
      let ch = chain;
      if (/Function|Arrow/.test(n.type) && n.type !== "FunctionDeclaration" || n.type === "FunctionDeclaration") {
        const s = new Set(); n.params.forEach(p => bound(p).forEach(x => s.add(x)));
        if (n.type === "FunctionExpression" && n.id) s.add(n.id.name);
        if (n.body.type === "BlockStatement") { hoisted(n.body, s); lexical(n.body.body, s); }
        s.add("arguments");
        ch = [...chain, s];
        if (n.type === "FunctionDeclaration" && n.id && inFn) {   /* a function in a top-level { } block is global (sloppy-mode hoisting) */
          add(defs, n.id.name, { s: sc.i, line: L(n), kind: "function", scoped: true });
          fnRanges.push({ s: sc.i, name: n.id.name, from: L(n), to: LE(n) });
        }
        n.params.forEach(p => visit(p, ch, true));
        visit(n.body, ch, true);
        return;
      }
      if ((n.type === "BlockStatement" || n.type === "StaticBlock") && n !== ast) {
        const s = lexical(n.body, new Set());
        if (!inFn) hoisted(n, new Set());   /* top-level block vars are global: nothing to add */
        if (s.size) ch = [...chain, s];
        /* a { } block at the top: its const fns are private to it */
        if (chain.length === 0) for (const c of n.body) if (c.type === "VariableDeclaration" && c.kind !== "var") for (const d of c.declarations) if (isFn(d.init))
          for (const nm of bound(d.id)) { add(defs, nm, { s: sc.i, line: L(d), kind: c.kind + " fn", scoped: true }); fnRanges.push({ s: sc.i, name: nm, from: L(d), to: LE(d) }); }
      }
      if (n.type === "CatchClause" && n.param) ch = [...chain, new Set(bound(n.param))];
      if (/^For(In|Of)?Statement$/.test(n.type)) { const d = n.init || n.left; if (d && d.type === "VariableDeclaration" && d.kind !== "var") ch = [...chain, new Set(d.declarations.flatMap(x => bound(x.id)))]; }
      if (n.type === "SwitchStatement") ch = [...chain, lexical(n.cases.flatMap(c => c.consequent), new Set())];
      if (n.type === "UnaryExpression" && n.operator === "typeof" && n.argument.type === "Identifier") typeofd.add(n.argument.name);
      if (n.type === "AssignmentExpression" && n.left.type === "Identifier" && !local(n.left.name, ch)) {
        const text = (codeLines[n.loc.start.line - 1] || "").trim().slice(0, 150);
        add(wraps, n.left.name, { s: sc.i, line: L(n), text, fn: isFn(n.right) || n.right.type === "CallExpression" });
      }
      if (n.type === "AssignmentExpression" && n.left.type === "MemberExpression" && n.left.object.type === "Identifier" && n.left.object.name === "window" && !n.left.computed && n.left.property.type === "Identifier" && !global.has(n.left.property.name)) {
        global.add(n.left.property.name); add(defs, n.left.property.name, { s: sc.i, line: L(n), kind: "window.", scoped: false });
      }
      if (n.type === "MemberExpression" && !n.computed && n.object.type === "Identifier" && n.property.type === "Identifier" && !local(n.object.name, ch))
        add(props, n.object.name, { s: sc.i, line: L(n), prop: n.property.name });
      if (n.type === "CallExpression" && n.callee.type === "Identifier" && !local(n.callee.name, ch)) refs.push({ s: sc.i, name: n.callee.name, line: L(n) });
      for (const c of kids(n)) visit(c, ch, inFn);
    };
    /* the script's own top level is the global scope: chain starts empty */
    for (const n of ast.body) visit(n, [], false);
    for (const r of refs) if (r.s === sc.i && typeofd.has(r.name)) r.guarded = true;
  });

  const hazards = [];
  /* v237: calling a name that is nobody's global but lives inside another script's scope */
  for (const r of refs) {
    if (r.guarded || global.has(r.name)) continue;
    const there = (defs[r.name] || []).filter(d => d.scoped && d.s !== r.s);
    if (there.length) hazards.push({ rule: "v237", line: r.line, s: r.s, name: r.name, msg: `${r.name}() only exists inside script ${there.map(d => `#${d.s} (line ${d.line})`).join(", ")}: script #${r.s} can't reach it` });
  }
  /* v198: state kept on a function that later scripts wrap (outside its own body, the wrapper is what you reach) */
  const wrapped = new Set(Object.entries(wraps).filter(([k, a]) => global.has(k) && a.some(w => w.fn)).map(([k]) => k));
  const fnNames = new Set(Object.entries(defs).filter(([, a]) => a.some(d => /function|fn/.test(d.kind) && !d.scoped)).map(([k]) => k));
  const SAFE = new Set(["apply", "call", "bind", "name", "length", "toString", "prototype"]);
  for (const [k, a] of Object.entries(props)) if (fnNames.has(k) && wrapped.has(k))
    for (const u of a) if (!SAFE.has(u.prop)) hazards.push({ rule: "v198", line: u.line, s: u.s, name: k, msg: `${k}.${u.prop}: state on a function that later scripts wrap (code that reached the original sees a different .${u.prop})` });
  /* v216: assigning to a top-level const (a TypeError the moment it runs) */
  for (const [k, ln] of Object.entries(constTop)) for (const w of wraps[k] || [])
    hazards.push({ rule: "v216", line: w.line, s: w.s, name: k, msg: `${k} is a const (line ${ln}) and this assigns to it: TypeError when it runs; extend it instead` });
  /* v269: screen changes have one door (onNav hooks); goTab / goSub / openFull aren't wrapped any more */
  const NAV = new Set(["goTab", "goSub", "openFull"]);
  const door = (defs.onNav || []).find(d => !d.scoped);   /* the door itself wraps them once, in its own script */
  if (door) for (const k of NAV) for (const w of wraps[k] || []) if (w.fn && w.line > door.line && w.s !== door.s)
    hazards.push({ rule: "v269", line: w.line, s: w.s, name: k, msg: `${k} is wrapped here: register onNav("before"|"after", (kind, args) => …) instead (CLAUDE.md, v269)` });
  const seenH = new Set();
  hazards.splice(0, hazards.length, ...hazards.filter(h => { const k = h.line + h.msg; if (seenH.has(k)) return false; seenH.add(k); return true; }));
  const st = fs.statSync(FILE);
  const map = { key: `${st.size}:${st.mtimeMs}`, lines: lines.length, scripts: scripts.map(({ code, off, ...s }) => s), defs, wraps,
    props: Object.fromEntries(Object.entries(props).filter(([k]) => fnNames.has(k))), fnRanges, hazards };
  fs.mkdirSync(path.dirname(CACHE), { recursive: true });
  fs.writeFileSync(CACHE, JSON.stringify(map));
  return map;
}
export function load() {
  try { const st = fs.statSync(FILE), m = JSON.parse(fs.readFileSync(CACHE, "utf8")); if (m.key === `${st.size}:${st.mtimeMs}`) return m; } catch (e) {}
  return build();
}

/* ---------- views ---------- */
const scriptOf = (map, line) => map.scripts.find(s => line >= s.start && line <= s.end);
export function lookup(map, name) {
  const out = [];
  const d = map.defs[name] || [];
  if (!d.length && !(map.wraps[name] || []).length) return `${name}: not found in index.html's scripts`;
  out.push(`${name}`);
  for (const x of d) out.push(`  defined   line ${x.line} (script #${x.s}) ${x.kind}${x.scoped ? "  ⚠ scoped: other scripts can't call it" : ""}`);
  const w = (map.wraps[name] || []).filter(x => x.fn);
  if (w.length) {
    out.push(`  wrapped ${w.length}× later (edit the original → every wrapper below still runs after it; a wrapper's own logic lives in its line):`);
    for (const x of w) out.push(`    line ${x.line} (#${x.s}): ${x.text}`);
  }
  const plain = (map.wraps[name] || []).filter(x => !x.fn);
  if (plain.length) out.push(`  assigned ${plain.length}×: ` + plain.slice(0, 8).map(x => `line ${x.line}`).join(", "));
  const pr = (map.props[name] || []).filter(p => !["apply", "call", "bind"].includes(p.prop));
  if (pr.length) out.push(`  ${name}.<prop> used: ` + [...new Set(pr.map(p => p.prop))].slice(0, 12).join(", ") + (w.length ? "  ⚠ v198: a wrapper doesn't carry these" : ""));
  return out.join("\n");
}
export function at(map, line) {
  const s = scriptOf(map, line);
  if (!s) return `line ${line}: not inside a <script> (HTML or CSS)`;
  const fns = map.fnRanges.filter(f => f.s === s.i && line >= f.from && line <= f.to).sort((a, b) => (b.from - a.from));
  const out = [`line ${line}: script #${s.i} (lines ${s.start}-${s.end}, ${s.kb} KB)${s.head ? " — " + s.head : ""}`];
  if (fns.length) out.push("  inside: " + fns.map(f => `${f.name} (${f.from}-${f.to})`).join(" ⊂ "));
  for (const f of fns) { const w = (map.wraps[f.name] || []).filter(x => x.fn && x.line > line); if (w.length) out.push(`  ${f.name} is wrapped ${w.length}× after this: ` + w.map(x => `#${x.s}:${x.line}`).join(", ")); }
  return out.join("\n");
}
/* for the learn mod: the functions around a line, and how many later scripts wrap each */
export function atJson(map, line) {
  const s = scriptOf(map, line);
  if (!s) return { line, script: 0, fns: [] };
  const fns = map.fnRanges.filter(f => f.s === s.i && line >= f.from && line <= f.to).sort((a, b) => b.from - a.from);
  return { line, script: s.i, fns: fns.map(f => ({ name: f.name, from: f.from, to: f.to, wrappedAfter: (map.wraps[f.name] || []).filter(x => x.fn && x.line > f.to).map(x => x.line) })) };
}
/* ---------- CLI ---------- */
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const a = process.argv.slice(2), opt = Object.fromEntries(a.filter(x => x.startsWith("--")).map(x => { const [k, v] = x.slice(2).split("="); return [k, v ?? true]; }));
  const names = a.filter(x => !x.startsWith("--"));
  const map = opt.fresh ? build() : load();
  if (opt.json && opt.at) process.stdout.write(JSON.stringify(String(opt.at).split(",").map(n => atJson(map, +n))));
  else if (opt.json) process.stdout.write(JSON.stringify(map));
  else if (opt.at) console.log(String(opt.at).split(",").map(n => at(map, +n)).join("\n"));
  else if (opt.check) {
    const base = opt.base;   /* --base=<git ref>: only hazards that are new compared with that build */
    let hz = map.hazards;
    if (base) {
      const key = h => `${h.rule}|${h.name}|${h.msg.replace(/line \d+|#\d+|\(\d+[^)]*\)/g, "")}`;
      const tmp = path.join(ROOT, "tools", ".cache", "base.html");
      fs.writeFileSync(tmp, execFileSync("git", ["show", `${base}:index.html`], { cwd: ROOT, maxBuffer: 64 << 20 }));
      const old = JSON.parse(execFileSync(process.execPath, [fileURLToPath(import.meta.url), "--json", "--fresh"], { cwd: ROOT, env: { ...process.env, PN_MAP_FILE: tmp }, maxBuffer: 256 << 20 }).toString());
      const had = new Set(old.hazards.map(key));
      hz = hz.filter(h => !had.has(key(h)));
    }
    const parseErr = map.scripts.filter(s => s.parseError);
    for (const s of parseErr) console.log(`script #${s.i} (line ${s.start}) doesn't parse: ${s.parseError}`);
    if (!hz.length) console.log(`No ${base ? "new " : ""}hazards (${map.hazards.length} known in total).`);
    for (const h of hz.slice(0, 60)) console.log(`[${h.rule}] line ${h.line}: ${h.msg}`);
    process.exit(hz.length || parseErr.length ? 1 : 0);
  }
  else if (names.length) console.log(names.map(n => lookup(map, n)).join("\n\n"));
  else console.log(fs.readFileSync(fileURLToPath(import.meta.url), "utf8").split("\n").slice(1, 10).join("\n").replace(/^\/\* ?| ?\*\/$/gm, ""));
}
