
Graph complete. Here's the summary:

---

**Graph complete. Outputs in `/home/leon/Projects/bluejay/graphify-out/`:**

- `graph.html` - interactive graph (aggregated community view: 1,638 community nodes, 2,100 cross-community edges)
- `GRAPH_REPORT.md` - audit report
- `graph.json` - raw graph data (29,793 nodes, 61,619 edges)

---

### God Nodes (most connected)
1. `s()` - 495 edges (minified JS function from YouTube plugin)
2. `a()` - 464 edges (minified JS function)
3. `a()` - 464 edges (minified JS function)
4. `a()` - 446 edges (minified JS function)
5. `c()` - 415 edges (minified JS function)

### Surprising Connections
- **Bottom Navigation Compose** → **XML Bottom Bar Architecture** (INFERRED - both related to bottom bar migration)
- **Bottom Navigation Compose** → **Bottom Bar Compose Migration Plan** (INFERRED)
- **pa()** → **c()** (indirect call between YouTube plugin and JSDOM runtime)
- **initCache()** → **Cache** (Polycentric API methods connecting to SettingsDev)

### Suggested Questions
- Why do minified functions (`i()`, `c()`, `D()`) bridge code across stable/unstable/main build variants?
- Are the 29-51 inferred relationships involving minified functions actually correct?

---

**The most interesting question this graph can answer: How does the YouTube plugin's obfuscated code (`YoutubeScript.js`) interact with the JSDOM runtime and the app's core Android classes?** Want me to trace it?