/* What are the detached DOM nodes still alive after Settings opens + closes? Lists them by tag.class, and the first one's
   closest still-live-looking markers. (leak finder)   node tools/kit.mjs run tools/scenarios/soak-detached.mjs --widths 412 */
export default async ({ p, w }) => {
  await p.page.evaluate(() => { if (typeof PRB !== "undefined" && PRB.st) { PRB.st.done = true; prbSave(); } });
  for (let i = 0; i < 3; i++) { await p.step("o", "openSettings()", 600); await p.step("c", "closeEdit()", 400); }
  await p.cdp.send("HeapProfiler.collectGarbage");
  const proto = await p.cdp.send("Runtime.evaluate", { expression: "Node.prototype" });
  const objs = await p.cdp.send("Runtime.queryObjects", { prototypeObjectId: proto.result.objectId });
  const r = await p.cdp.send("Runtime.callFunctionOn", { objectId: objs.objects.objectId, returnByValue: true,
    functionDeclaration: `function(){ const live=new Set(); const walk=n=>{live.add(n); for(const c of n.childNodes) walk(c);}; walk(document);
      const ok=n=>{try{return n.nodeType>0;}catch(e){return false;}}; const det=[...this].filter(n=>ok(n)&&!live.has(n)); const by={}; const roots=[];
      for(const n of det){ const k=n.nodeType===1?n.tagName.toLowerCase()+(n.classList[0]?"."+n.classList[0]:""):"#"+n.nodeType; by[k]=(by[k]||0)+1; if(n.nodeType===1&&!n.parentNode) roots.push(n.tagName.toLowerCase()+(n.id?"#"+n.id:"")+(n.classList[0]?"."+n.classList[0]:"")+" ("+n.getElementsByTagName("*").length+" inside)"); }
      return { detached: det.length, top: Object.entries(by).sort((a,b)=>b[1]-a[1]).slice(0,12), roots: roots.slice(0,15) }; }` });
  console.log(JSON.stringify(r.result.value || r, null, 1).slice(0,1800));
};
