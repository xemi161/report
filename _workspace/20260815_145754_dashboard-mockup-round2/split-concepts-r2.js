// dashboard-concepts-r2.html(2라운드 10개 갤러리)을 독립 HTML 10개로 쪼갠다.
// round1의 split-concepts.js와 동일한 방식 — 배너 주석을 경계로 script를 분할.
const fs = require("fs");
const path = require("path");

const SRC = path.join(__dirname, "dashboard-concepts-r2.html");
const OUT_DIR = path.join(__dirname, "concepts");
const src = fs.readFileSync(SRC, "utf8");

const styleBlock = src.match(/<style>[\s\S]*?<\/style>/)[0];
const scriptContent = src.match(/<script>([\s\S]*?)<\/script>/)[1];

const bannerRe = /\/\* ={73}\n\s*(.+?)\n\s*={73} \*\//g;
const banners = [...scriptContent.matchAll(bannerRe)];
if (banners.length !== 10) throw new Error(`expected 10 concept banners, got ${banners.length}`);

// c12/c20 등 일부 시안의 초기 상태 변수가 함수 밖 "시안별 상호작용 상태" 섹션에
// 선언돼 있어(마지막 시안 블록 뒤, CONCEPTS 배열 앞) 독립 파일에는 preamble에 수동으로 끼워 넣는다.
const preamble = scriptContent.slice(0, banners[0].index) +
  '\nlet accOrder = ["week","daily","proj","mw","hist"];\nlet accOpen = new Set(["daily"]);\nlet accPin = null;\n' +
  'let deckOrder = [0,1,2,3];\nlet c20Mode = "note";\n';
const stateSectionMarker = "/* ---------- 시안별 상호작용 상태";
const blocks = banners.map((m, i) => {
  const end = i + 1 < banners.length
    ? banners[i + 1].index
    : scriptContent.indexOf(stateSectionMarker, m.index);
  return scriptContent.slice(m.index, end).trimEnd();
});

const CONCEPTS = [
  { n: 11, slug: "focus-mode",     fn: "c11", wide: false, label: "⑪ 포커스 모드" },
  { n: 12, slug: "accordion",      fn: "c12", wide: false, label: "⑫ 접이식 아코디언" },
  { n: 13, slug: "asymmetric-62",  fn: "c13", wide: false, label: "⑬ 62:38 비대칭" },
  { n: 14, slug: "card-deck",      fn: "c14", wide: false, label: "⑭ 카드 덱" },
  { n: 15, slug: "sticky-summary", fn: "c15", wide: false, label: "⑮ 고정 서머리 바" },
  { n: 16, slug: "icon-sidebar",   fn: "c16", wide: false, label: "⑯ 아이콘 슬림 사이드바" },
  { n: 17, slug: "carousel-lanes", fn: "c17", wide: true,  label: "⑰ 가로 레인 캐러셀" },
  { n: 18, slug: "ring-gauges",    fn: "c18", wide: false, label: "⑱ 링 게이지 계기판" },
  { n: 19, slug: "log-drawer",     fn: "c19", wide: false, label: "⑲ 활동 로그+드로어" },
  { n: 20, slug: "command-bar",    fn: "c20", wide: false, label: "⑳ 입력 우선(커맨드 바)" },
];

if (!fs.existsSync(OUT_DIR)) fs.mkdirSync(OUT_DIR);

CONCEPTS.forEach((c, i) => {
  const body = `<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>주간업무보고 · 대시보드 시안 ${c.label}</title>
${styleBlock}
</head>
<body>

<div class="appbar">
  <div class="appbar-inner">
    <div class="brand"><span class="name">주간업무보고</span><span class="who">개발1파트 · 정상화</span></div>
    <div class="seg">
      <button class="active">대시보드</button><button>작성</button><button>히스토리</button>
    </div>
  </div>
</div>

<main id="stage" class="${c.wide ? "wide" : ""}"></main>

<script>
${preamble}
${blocks[i]}

document.getElementById("stage").innerHTML = ${c.fn}();
</script>
</body>
</html>
`;
  const file = path.join(OUT_DIR, `c${c.n}-${c.slug}.html`);
  fs.writeFileSync(file, body, "utf8");
  console.log("wrote", file);
});
