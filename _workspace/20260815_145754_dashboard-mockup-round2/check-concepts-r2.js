const fs = require("fs");
const path = require("path");
const dir = path.join(__dirname, "concepts");
for (const f of fs.readdirSync(dir).filter(f => f.endsWith(".html")).sort()) {
  const html = fs.readFileSync(path.join(dir, f), "utf8");
  const m = html.match(/<script>([\s\S]*?)<\/script>/);
  if (!m) { console.log(f, ": NO SCRIPT FOUND"); continue; }
  const js = m[1];
  try {
    const stubDoc = { getElementById: () => ({ set innerHTML(v) { this._v = v; } }) };
    const fn = new Function("document", js);
    fn(stubDoc);
    console.log(f, ": OK");
  } catch (e) {
    console.log(f, ": ERROR ->", e.message);
  }
}
