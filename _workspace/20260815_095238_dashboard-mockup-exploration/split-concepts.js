// dashboard-concepts.html(10개 갤러리)을 독립 HTML 10개로 쪼갠다.
// 배너 주석("① Hero + 스택 — ...")을 경계로 script를 분할하고,
// style 블록은 그대로 재사용(추가 oklch 없음, 라이트 전용 유지).
const fs = require("fs");
const path = require("path");

const SRC = path.join(__dirname, "dashboard-concepts.html");
const OUT_DIR = path.join(__dirname, "concepts");
const src = fs.readFileSync(SRC, "utf8");

const styleBlock = src.match(/<style>[\s\S]*?<\/style>/)[0];
const scriptContent = src.match(/<script>([\s\S]*?)<\/script>/)[1];

const bannerRe = /\/\* ={73}\n\s*(.+?)\n\s*={73} \*\//g;
const banners = [...scriptContent.matchAll(bannerRe)];
if (banners.length !== 10) throw new Error(`expected 10 concept banners, got ${banners.length}`);

const preamble = scriptContent.slice(0, banners[0].index);
const blocks = banners.map((m, i) => {
  const end = i + 1 < banners.length ? banners[i + 1].index : scriptContent.indexOf("/* ---------- 시안 목록 ----------", m.index);
  return scriptContent.slice(m.index, end).trimEnd();
});

const CONCEPTS = [
  { n: 1,  slug: "hero-stack",     fn: "c1",  wide: false, label: "① Hero+스택(현행)" },
  { n: 2,  slug: "bento-mosaic",   fn: "c2",  wide: false, label: "② 벤토 모자이크(v7)" },
  { n: 3,  slug: "narrative-brief",fn: "c3",  wide: false, label: "③ 서술형 브리핑" },
  { n: 4,  slug: "left-rail",      fn: "c4",  wide: false, label: "④ 좌측 요약 레일" },
  { n: 5,  slug: "statbar-table",  fn: "c5",  wide: false, label: "⑤ 스탯바+표형 리스트" },
  { n: 6,  slug: "calendar-strip", fn: "c6",  wide: true,  label: "⑥ 주간 캘린더 스트립" },
  { n: 7,  slug: "project-first",  fn: "c7",  wide: false, label: "⑦ 프로젝트 중심" },
  { n: 8,  slug: "timeline-feed",  fn: "c8",  wide: false, label: "⑧ 타임라인 피드" },
  { n: 9,  slug: "longform-doc",   fn: "c9",  wide: false, label: "⑨ 단일 스크롤 롱폼" },
  { n: 10, slug: "uniform-grid",   fn: "c10", wide: false, label: "⑩ 균일 위젯 그리드" },
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
  const file = path.join(OUT_DIR, `c${String(c.n).padStart(2, "0")}-${c.slug}.html`);
  fs.writeFileSync(file, body, "utf8");
  console.log("wrote", file);
});
