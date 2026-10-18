/* For a .heapsnapshot: finds detached DOM nodes and prints the shortest retainer chains from the GC roots (what keeps
   them alive). Leak finder: node tools/heap-retain.mjs file.heapsnapshot [how many chains] */
import fs from "node:fs";
const f = process.argv[2], want = +process.argv[3] || 5;
const s = JSON.parse(fs.readFileSync(f, "utf8"));
const m = s.snapshot.meta, NF = m.node_fields.length, EF = m.edge_fields.length;
const nt = m.node_types[0], et = m.edge_types[0];
const N = s.nodes, E = s.edges, S = s.strings, n = N.length / NF;
const ix = k => m.node_fields.indexOf(k), ex = k => m.edge_fields.indexOf(k);
const first = new Uint32Array(n + 1); { let e = 0; for (let i = 0; i < n; i++) { first[i] = e; e += N[i * NF + ix("edge_count")]; } first[n] = e; }
const name = i => S[N[i * NF + ix("name")]], type = i => nt[N[i * NF + ix("type")]];
const dist = new Int32Array(n).fill(-1), par = new Int32Array(n).fill(-1), pedge = new Int32Array(n).fill(-1);
const q = [0]; dist[0] = 0;
for (let h = 0; h < q.length; h++) { const u = q[h];
  for (let e = first[u]; e < first[u + 1]; e++) { const t = et[E[e * EF + ex("type")]]; if (t === "weak" || t === "shortcut") continue;
    const v = E[e * EF + ex("to_node")] / NF; if (dist[v] < 0) { dist[v] = dist[u] + 1; par[v] = u; pedge[v] = e; q.push(v); } } }
const label = i => { const nm = name(i); return `${type(i)}:${nm.length > 60 ? nm.slice(0, 60) + "…" : nm}`; };
const edgeName = e => { const t = et[E[e * EF + ex("type")]], x = E[e * EF + ex("name_or_index")]; return t === "element" || t === "hidden" ? `[${x}]` : S[x]; };
const det = []; for (let i = 0; i < n; i++) if (N[i * NF + ix("detachedness")] === 2 && /^(HTML|SVG|Text|Comment|Document)/.test(name(i).replace(/^Detached /, "")) && dist[i] >= 0) det.push(i);
console.log(`${det.length} detached DOM nodes reachable. Shortest chains (root → … → node):`);
const seen = new Set(); let shown = 0;
for (const d of det.sort((a, b) => dist[a] - dist[b])) {
  const chain = []; for (let v = d; v > 0; v = par[v]) chain.push(`${label(v)} <-${edgeName(pedge[v])}-`);
  const key = chain.slice(1, 4).join("|"); if (seen.has(key)) continue; seen.add(key);
  console.log("\n  " + chain.reverse().join("\n  ")); if (++shown >= want) break; }
