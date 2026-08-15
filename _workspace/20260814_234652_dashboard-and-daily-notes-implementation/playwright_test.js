/**
 * 대시보드 재도입(3탭) + 일일 기록(DailyNote) E2E 검증.
 *
 * 사용자의 실제 DB는 절대 건드리지 않는다 — qa_run_app.ps1이 in-memory H2 + qa_seed.sql로
 * 띄운 포트 9411 인스턴스만 대상으로 한다.
 *
 * 실행: node playwright_test.js
 */
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const BASE = process.env.QA_BASE || 'http://localhost:9411';
const OUT = __dirname;
const SHOTS = path.join(OUT, 'screenshots');

const results = [];
const consoleErrors = [];

function record(scenario, name, ok, detail) {
  results.push({ scenario, name, ok, detail: detail || '' });
  const mark = ok ? 'PASS' : 'FAIL';
  console.log(`[${mark}] ${scenario} :: ${name}${detail ? ' — ' + detail : ''}`);
}

function check(scenario, name, cond, detail) {
  record(scenario, name, !!cond, detail);
}

/** 합격/불합격을 가르지 않고 사실만 남긴다(제품 결정이 필요한 사안). */
function note(scenario, name, detail) {
  results.push({ scenario, name, ok: true, info: true, detail: detail || '' });
  console.log(`[NOTE] ${scenario} :: ${name} — ${detail}`);
}

/** 페이지마다 콘솔 에러/페이지 에러를 전부 모은다(시나리오 6). */
function watchConsole(page) {
  page.on('console', (msg) => {
    if (msg.type() === 'error' || msg.type() === 'warning') {
      consoleErrors.push({ where: page.url(), type: msg.type(), text: msg.text() });
    }
  });
  page.on('pageerror', (err) => {
    consoleErrors.push({ where: page.url(), type: 'pageerror', text: String(err) });
  });
  page.on('requestfailed', (req) => {
    consoleErrors.push({ where: page.url(), type: 'requestfailed', text: req.url() + ' :: ' + (req.failure() && req.failure().errorText) });
  });
  page.on('response', (res) => {
    if (res.status() >= 400) {
      consoleErrors.push({ where: page.url(), type: 'http' + res.status(), text: res.url() });
    }
  });
}

async function main() {
  fs.mkdirSync(SHOTS, { recursive: true });
  const browser = await chromium.launch();
  const context = await browser.newContext({ viewport: { width: 1670, height: 1000 }, locale: 'ko-KR' });
  const page = await context.newPage();
  watchConsole(page);

  // ---------- 시나리오 1: 대시보드 랜딩 ----------
  {
    const S = '1. 대시보드 랜딩';
    const resp = await page.goto(BASE + '/', { waitUntil: 'networkidle' });
    check(S, 'GET / 200', resp.status() === 200, 'status=' + resp.status());

    const tabs = await page.locator('header .seg a').allTextContents();
    check(S, '헤더 탭 3개(대시보드/작성/히스토리)',
      tabs.length === 3 && tabs[0].trim() === '대시보드' && tabs[1].trim() === '작성' && tabs[2].trim() === '히스토리',
      JSON.stringify(tabs));
    check(S, '대시보드 탭이 active', await page.locator('header .seg a.active').innerText() === '대시보드');

    check(S, 'hero 카드 렌더', await page.locator('.card.hero').count() === 1);
    const heroMetrics = await page.locator('.hero-metrics .m b').allTextContents();
    // 시드: 8h*2일 + 4h*1일 = 20시간, 맨위크 0.50, 항목 3건
    check(S, 'hero 총시간=20 (report.totalHours 0이 아니라 실측)', heroMetrics[0] === '20', 'got=' + heroMetrics[0]);
    check(S, 'hero 맨위크=0.50', heroMetrics[1] === '0.50', 'got=' + heroMetrics[1]);
    check(S, 'hero 항목수=3', heroMetrics[2] === '3', 'got=' + heroMetrics[2]);
    check(S, 'hero 상태 배지=작성중', await page.locator('.hero-title .badge.draft').count() === 1);

    // 최근 2주 평균 = (0.85 + 1.00) / 2 = 0.93 (이번 주 제외, 제출본만)
    check(S, '최근 2주 평균 맨위크 카드', (await page.locator('.dash-side .kicker').first().innerText()).includes('최근 2주 평균'));
    const avg = await page.locator('.mw-big').innerText();
    check(S, '평균 맨위크 = 0.93', avg.trim() === '0.93', 'got=' + avg);
    check(S, '평균 모집단 2주 노출', await page.locator('.mw-week').count() === 2);

    // 진행중 프로젝트: GTPP(70%) + 신규 프로젝트(0%). 정산배치(100%)와 비활성 프로젝트는 제외.
    const projNames = await page.locator('.proj-row .nm').allTextContents();
    check(S, '진행중 프로젝트 = 100% 미만 + active만',
      projNames.length === 2 && projNames.includes('GTPP 결제연동') && projNames.includes('신규 프로젝트'),
      JSON.stringify(projNames));
    const pcts = await page.locator('.proj-row .pc').allTextContents();
    // 시드의 GTPP는 8월 2주(60/80)와 8월 3주(70)에 항목이 있다 → "가장 최근 보고된 주"인 70%가 나와야 한다.
    // (완료율 평균 규칙 자체는 DashboardControllerTest의 단위 테스트가 따로 고정한다.)
    check(S, 'GTPP 진행률 = 가장 최근 보고된 주(8월 3주)의 70%', pcts.includes('70%'), JSON.stringify(pcts));
    check(S, '보고 이력 없는 프로젝트는 "아직 보고된 적 없음"',
      (await page.locator('.proj-meta').allTextContents()).some((t) => t.includes('아직 보고된 적 없음')));

    // 과거 보고서 5개 + "전체 6개 보기"
    const cards = await page.locator('.dash-list .hist-card').count();
    check(S, '과거 보고서 카드 5장(이번 주 제외, 제출본만)', cards === 5, 'got=' + cards);
    const moreLink = page.locator('.section-head .link-more');
    check(S, 'pastReportCount(6) > 5 이므로 "전체 6개 보기" 노출',
      (await moreLink.count()) === 1 && (await moreLink.innerText()).includes('전체 6개'),
      await moreLink.count() ? await moreLink.innerText() : '없음');

    // "오늘 한 일" 위젯
    check(S, '오늘 한 일 위젯(#dashDaily) 렌더', await page.locator('#dashDaily').count() === 1);
    check(S, '캡처 입력(.quick-add) 존재', await page.locator('#dashDaily .quick-add input[name=text]').count() === 1);
    check(S, '오늘 기록 없으면 빈 안내', (await page.locator('#dashDaily .empty-hint').innerText()).includes('오늘은 아직 기록이 없습니다'));
    check(S, '어제(08.14) 기록이 최근 날짜 그룹으로 노출', await page.locator('#dashDaily .day-group').count() === 1);
    const footText = await page.locator('#dashDaily .daily-foot').innerText();
    check(S, '하단 요약 "이번 주 기록 2건 · 3h"', footText.includes('2') && footText.includes('3h'), footText.replace(/\s+/g, ' '));

    await page.screenshot({ path: path.join(SHOTS, '01-dashboard-1670.png'), fullPage: true });
  }

  // ---------- 시나리오 2: 대시보드에서 기록 추가(htmx) ----------
  {
    const S = '2. 대시보드 기록 추가';
    const before = await page.locator('#dashDaily > .daily-item').count();
    let reloaded = false;
    page.once('load', () => { reloaded = true; });

    await page.fill('#dashDaily .quick-add input[name=text]', 'QA 대시보드 캡처 테스트');
    await page.fill('#dashDaily .quick-add input.qa-hours', '1.5');
    await page.click('#dashDaily .quick-add button[type=submit]');
    await page.waitForFunction(
      (n) => document.querySelectorAll('#dashDaily > .daily-item').length === n + 1,
      before, { timeout: 5000 });

    check(S, '페이지 새로고침 없이(htmx) 목록 반영', !reloaded);
    const rows = page.locator('#dashDaily > .daily-item');
    check(S, '오늘 행이 1건 늘어남', (await rows.count()) === before + 1);
    check(S, '입력한 텍스트가 그대로 렌더(UTF-8 한글)',
      (await rows.last().locator('.d-txt').inputValue()) === 'QA 대시보드 캡처 테스트');
    check(S, '입력한 시간이 hoursDisplay()로 1.5', (await rows.last().locator('.hours-input').inputValue()) === '1.5');

    const chip = await page.locator('#dashDaily .card-title .count').innerText();
    check(S, '카드 칩이 "1건 · 1.5h"로 갱신', chip.replace(/\s/g, '') === '1건·1.5h', chip);
    const foot = await page.locator('#dashDaily .daily-foot').innerText();
    check(S, '하단 요약이 "3건 · 4.5h"로 서버 재계산', foot.includes('3') && foot.includes('4.5h'), foot.replace(/\s+/g, ' '));
    check(S, '추가 후 입력칸 재포커스(app.js)',
      await page.evaluate(() => document.activeElement && document.activeElement.hasAttribute('data-daily-newtext')));

    // 인라인 시간 수정(hx-swap=none) → 칩은 app.js가 클라이언트 재계산
    await rows.last().locator('.hours-input').fill('2');
    await rows.last().locator('.hours-input').dispatchEvent('input');
    const chip2 = await page.locator('#dashDaily .card-title .count').innerText();
    check(S, '시간 인라인 수정 시 칩을 app.js가 즉시 재계산', chip2.replace(/\s/g, '') === '1건·2h', chip2);

    await page.screenshot({ path: path.join(SHOTS, '02-dashboard-note-added.png'), fullPage: true });
  }

  // ---------- 시나리오 3: 작성 탭 좌우 분할 + 기록 CRUD ----------
  {
    const S = '3. 작성 탭 좌우 분할';
    const resp = await page.goto(BASE + '/entry', { waitUntil: 'networkidle' });
    check(S, 'GET /entry 200', resp.status() === 200, 'status=' + resp.status());
    check(S, 'body.wide (분할 확장 폭)', await page.evaluate(() => document.body.classList.contains('wide')));
    check(S, '#splitLayout 존재', await page.locator('#splitLayout').count() === 1);
    check(S, '좌측 #weekPanel 존재', await page.locator('#weekPanel').count() === 1);
    check(S, '우측 #writeView 존재', await page.locator('#writeView').count() === 1);

    const cols = await page.evaluate(() => getComputedStyle(document.getElementById('splitLayout')).gridTemplateColumns);
    check(S, '1670px에서 좌우 2컬럼', cols.split(' ').length === 2, cols);
    const panelW = await page.evaluate(() => Math.round(document.getElementById('weekPanel').getBoundingClientRect().width));
    check(S, '1670px에서 패널 폭 = 680px', panelW === 680, 'got=' + panelW);

    check(S, '패널 제목 "이번 주에 한 일"', (await page.locator('#weekPanel h2').innerText()).trim() === '이번 주에 한 일');
    const dayHeads = await page.locator('#weekPanel .day-group .day-head').allTextContents();
    check(S, '요일별 날짜 그룹 렌더(08.14 기록일 + 오늘 08.15)', dayHeads.length === 2, JSON.stringify(dayHeads.map((t) => t.replace(/\s+/g, ' ').trim())));
    check(S, '오늘 그룹에 "오늘" 칩', await page.locator('#weekPanel .today-chip').count() === 1);
    const dateOptions = await page.locator('#weekPanel .quick-add select[name=workDate] option').count();
    check(S, '날짜 셀렉트에 그 주 7일', dateOptions === 7, 'got=' + dateOptions);
    const selected = await page.locator('#weekPanel .quick-add select[name=workDate]').inputValue();
    check(S, '기본 선택값 = 오늘(2026-08-15)', selected === '2026-08-15', 'got=' + selected);

    // 좌측 패널 기록 추가 왕복
    const beforeRows = await page.locator('#weekPanel .daily-item').count();
    await page.fill('#weekPanel .quick-add input[name=text]', 'QA 작성패널 기록');
    await page.fill('#weekPanel .quick-add input.qa-hours', '2');
    await page.click('#weekPanel .quick-add button[type=submit]');
    await page.waitForFunction((n) => document.querySelectorAll('#weekPanel .daily-item').length === n + 1, beforeRows, { timeout: 5000 });
    check(S, '기록 추가 → #weekPanel outerHTML 교체', (await page.locator('#weekPanel .daily-item').count()) === beforeRows + 1);
    check(S, '패널 칩 건수 갱신', (await page.locator('#weekPanel .card-title .count').innerText()).includes('4'),
      await page.locator('#weekPanel .card-title .count').innerText());

    // 정렬 방향(의도된 설계): 작성 패널은 오름차순
    const panelDates = await page.locator('#weekPanel .day-group .day-head > span:first-child').allTextContents();
    check(S, '작성 패널은 오름차순(08.14 → 08.15) [의도된 설계]',
      panelDates.length >= 2 && panelDates[0].trim() < panelDates[1].trim(), JSON.stringify(panelDates));

    // 삭제 왕복
    const rowsNow = await page.locator('#weekPanel .daily-item').count();
    await page.locator('#weekPanel .daily-item').last().locator('button.icon-btn').click();
    await page.waitForFunction((n) => document.querySelectorAll('#weekPanel .daily-item').length === n - 1, rowsNow, { timeout: 5000 });
    check(S, '기록 삭제 왕복', (await page.locator('#weekPanel .daily-item').count()) === rowsNow - 1);

    // 우측 주간보고 총 투입시간/맨위크가 기록 시간에 오염되지 않았는지
    await page.goto(BASE + '/', { waitUntil: 'networkidle' });
    const metricsAfter = await page.locator('.hero-metrics .m b').allTextContents();
    check(S, '기록 시간이 hero 총투입시간에 섞이지 않음(여전히 20)', metricsAfter[0] === '20', 'got=' + metricsAfter[0]);
    check(S, '기록 시간이 hero 맨위크에 섞이지 않음(여전히 0.50)', metricsAfter[1] === '0.50', 'got=' + metricsAfter[1]);
    check(S, '기록 시간이 최근 2주 평균 맨위크에 섞이지 않음(여전히 0.93)',
      (await page.locator('.mw-big').innerText()).trim() === '0.93');

    // ⚠️ 같은 모델 속성명(activeProjectCount)이 두 화면에서 다르게 계산된다.
    //    대시보드 = active AND 최근 진행률 < 100% (backend 확정 규칙) / 작성 탭 = 단순 active 개수.
    //    라벨도 "진행중인 프로젝트" vs "진행중 프로젝트 수"로 거의 같아 탭을 오갈 때 모순으로 읽힌다.
    //    어느 정의를 택할지는 제품 결정이라 여기서는 사실만 기록한다.
    const dashCount = (await page.locator('.dash-side .card-title .count').innerText()).replace(/\D/g, '');
    await page.goto(BASE + '/entry', { waitUntil: 'networkidle' });
    const entryCount = (await page.locator('#writeView .stats .stat').nth(1).locator('.v').innerText()).trim();
    note(S, '[불일치] "진행중 프로젝트" 수가 대시보드와 작성 탭에서 다르다',
      `대시보드=${dashCount}건(진행률<100% 규칙 적용) / 작성 탭=${entryCount}(단순 active 개수)`);
  }

  // ---------- 시나리오 4: 뷰포트별 레이아웃 ----------
  {
    const S = '4. 뷰포트별 레이아웃';
    await page.goto(BASE + '/entry', { waitUntil: 'networkidle' });

    // 이 검증이 의미를 가지려면 최악 케이스 행이 실제로 화면에 있어야 한다 —
    // 티켓 입력이 있는 dev 행에 "이월"과 "완료" 배지가 동시에 붙은 상태(worklog §9 qa).
    const worstRow = page.locator('#writeView .row-main').filter({ has: page.locator('.badge.done') })
      .filter({ has: page.locator('.badge.carry') });
    check(S, '최악 케이스 행(완료+이월 배지 동시 + 티켓 입력)이 화면에 존재',
      (await worstRow.count()) >= 1 && (await worstRow.first().locator('input.ticket').count()) === 1,
      'count=' + (await worstRow.count()));

    for (const w of [1670, 1536, 1280, 1000]) {
      await page.setViewportSize({ width: w, height: 1000 });
      await page.waitForTimeout(150);
      const m = await page.evaluate(() => {
        const split = document.getElementById('splitLayout');
        const panel = document.getElementById('weekPanel');
        const rows = Array.from(document.querySelectorAll('#writeView .row-main'));
        // ⚠️ 자식들의 top 좌표 비교로는 판정할 수 없다 — .row-main은 align-items:center라
        //    한 줄이어도 높이가 다른 자식(select 31px / badge 21px / pct-wrap 38px)의 top이 갈린다.
        //    flex-wrap:wrap이 실제로 접혔는지는 "행 높이 > 가장 높은 자식 높이"로만 알 수 있다.
        const wrapped = rows.filter((r) => {
          const kids = Array.from(r.children).filter((c) => c.offsetParent !== null);
          if (kids.length < 2) return false;
          const maxKidH = Math.max(...kids.map((c) => c.getBoundingClientRect().height));
          return r.getBoundingClientRect().height > maxKidH + 4;
        }).length;
        return {
          cols: getComputedStyle(split).gridTemplateColumns.split(' ').length,
          panelW: Math.round(panel.getBoundingClientRect().width),
          mainW: Math.round(document.querySelector('main').getBoundingClientRect().width),
          rowCount: rows.length,
          wrapped,
          bodyScrollW: document.documentElement.scrollWidth,
          clientW: document.documentElement.clientWidth,
        };
      });

      if (w >= 1281) {
        check(S, `${w}px: 좌우 2컬럼 유지`, m.cols === 2, JSON.stringify(m));
      } else {
        check(S, `${w}px: 세로 스택(1컬럼)`, m.cols === 1, JSON.stringify(m));
      }
      check(S, `${w}px: 항목 행이 두 줄로 깨지지 않음`, m.wrapped === 0,
        `행 ${m.rowCount}개 중 ${m.wrapped}개 줄바꿈`);
      check(S, `${w}px: 가로 스크롤 없음`, m.bodyScrollW <= m.clientW + 1,
        `scrollW=${m.bodyScrollW} clientW=${m.clientW}`);
      if (w === 1670) check(S, '1670px: 패널 정확히 680px', m.panelW === 680, 'got=' + m.panelW);
      if (w === 1536) check(S, '1536px: 패널이 680px보다 좁아짐(우측 하한 916px 보장)',
        m.panelW < 680 && m.panelW > 400, 'got=' + m.panelW);

      await page.screenshot({ path: path.join(SHOTS, `04-entry-${w}.png`), fullPage: false });
    }

    // 패널 숨기면 1140px로 복귀
    await page.setViewportSize({ width: 1670, height: 1000 });
    await page.click('#weekPanel [data-panel-hide]');
    await page.waitForTimeout(200);
    const hidden = await page.evaluate(() => ({
      wide: document.body.classList.contains('wide'),
      mainW: Math.round(document.querySelector('main').getBoundingClientRect().width),
      panelVisible: document.getElementById('weekPanel').offsetParent !== null,
    }));
    check(S, '패널 숨김 → body.wide 해제', !hidden.wide, JSON.stringify(hidden));
    check(S, '패널 숨김 → main 1140px 복귀', hidden.mainW === 1140, 'got=' + hidden.mainW);
    check(S, '패널 숨김 → 패널 비표시', !hidden.panelVisible);
    await page.screenshot({ path: path.join(SHOTS, '04-entry-panel-hidden.png'), fullPage: false });

    // 대시보드/히스토리는 언제나 1140px
    await page.goto(BASE + '/', { waitUntil: 'networkidle' });
    const dashW = await page.evaluate(() => Math.round(document.querySelector('main').getBoundingClientRect().width));
    check(S, '대시보드 main = 1140px', dashW === 1140, 'got=' + dashW);
    await page.goto(BASE + '/history', { waitUntil: 'networkidle' });
    const histW = await page.evaluate(() => Math.round(document.querySelector('main').getBoundingClientRect().width));
    check(S, '히스토리 main = 1140px', histW === 1140, 'got=' + histW);

    // 다시 패널 보이기(뒷 시나리오에 영향 없도록 상태 복구)
    await page.goto(BASE + '/entry', { waitUntil: 'networkidle' });
    if (await page.locator('[data-panel-show]:not([hidden])').count()) {
      await page.click('[data-panel-show]');
      await page.waitForTimeout(150);
    }
  }

  // ---------- 시나리오 5: 히스토리 탭 서브 세그먼트 + 월 네비게이션 ----------
  {
    const S = '5. 히스토리 서브뷰';
    const resp = await page.goto(BASE + '/history', { waitUntil: 'networkidle' });
    check(S, 'GET /history 200', resp.status() === 200, 'status=' + resp.status());
    const segs = await page.locator('.hist-switch .seg a').allTextContents();
    check(S, '서브 세그먼트 2개', segs.length === 2, JSON.stringify(segs));
    check(S, '"과거 보고서 · 6"', segs[0].includes('6'), segs[0]);
    check(S, '"한 일 기록 · N" 건수 노출', /한 일 기록 · \d+/.test(segs[1]), segs[1]);
    check(S, '기본 뷰 = 과거 보고서', (await page.locator('.hist-switch .seg a.active').innerText()).includes('과거 보고서'));
    check(S, '보고서 카드 6장 전부', (await page.locator('.hist-card').count()) === 6);
    check(S, '맨위크 0.80 미만 카드에 "낮음" 표시', (await page.locator('.badge.carry').count()) >= 1);

    // records 뷰로 전환
    await page.click('.hist-switch .seg a:nth-child(2)');
    await page.waitForLoadState('networkidle');
    check(S, 'view=records URL', page.url().includes('view=records'), page.url());
    check(S, '#recordsPane 렌더', (await page.locator('#recordsPane').count()) === 1);
    check(S, '한 일 기록 세그먼트가 active', (await page.locator('.hist-switch .seg a.active').innerText()).includes('한 일 기록'));
    const monthLabel = await page.locator('#recordsPane .pager.inline .label').innerText();
    check(S, '이번 달 라벨 = 2026년 8월', monthLabel.trim() === '2026년 8월', monthLabel);

    // 통계 타일 — 8월 기록: 시드 2건(3h) + 시나리오2에서 추가 1건(2h) = 3건 / 5h / 2일
    const stats = await page.locator('#recordsPane .stats .stat .v').allTextContents();
    check(S, '통계 타일 3개(기록/기록된 시간/기록한 날)', stats.length === 3, JSON.stringify(stats));
    check(S, '"기록된 시간"은 0이어도 0을 그림(칩과 다른 규칙)',
      (await page.locator('#recordsPane .stats .stat').nth(1).innerText()).trim().length > 0);

    // 정렬 방향(의도된 설계): 기록 화면은 내림차순
    const recDates = await page.locator('#recordsPane .day-group .day-head > span:first-child').allTextContents();
    check(S, '기록 화면은 내림차순(최신 위) [의도된 설계]',
      recDates.length >= 2 ? recDates[0].trim() > recDates[1].trim() : true, JSON.stringify(recDates));

    // 월 네비게이션 — 이전 달
    const prevLink = page.locator('#recordsPane .pager.inline a').first();
    check(S, '7월 기록이 있으므로 이전 달 버튼 활성', (await prevLink.count()) === 1);
    await prevLink.click();
    await page.waitForLoadState('networkidle');
    const julLabel = await page.locator('#recordsPane .pager.inline .label').innerText();
    check(S, '이전 달 이동 → 2026년 7월', julLabel.trim() === '2026년 7월', julLabel);
    check(S, '7월 URL에 month=2026-07', page.url().includes('month=2026-07'), page.url());
    const julRows = await page.locator('#recordsPane .daily-item').count();
    check(S, '7월 기록 2건 렌더', julRows === 2, 'got=' + julRows);
    check(S, '7월이 최하한이므로 이전 달 버튼 비활성(.pager-disabled)',
      (await page.locator('#recordsPane .pager.inline .pager-disabled').count()) === 1);

    // 다음 달로 복귀
    await page.locator('#recordsPane .pager.inline a').last().click();
    await page.waitForLoadState('networkidle');
    check(S, '다음 달 이동 → 2026년 8월 복귀',
      (await page.locator('#recordsPane .pager.inline .label').innerText()).trim() === '2026년 8월');
    check(S, '이번 달이 최상한이므로 다음 달 버튼 비활성',
      (await page.locator('#recordsPane .pager.inline .pager-disabled').count()) === 1);

    // 기록 화면에서 추가/삭제 왕복
    const before = await page.locator('#recordsPane .daily-item').count();
    await page.fill('#recordsPane .quick-add input[name=text]', 'QA 기록화면 추가');
    await page.click('#recordsPane .quick-add button[type=submit]');
    await page.waitForFunction((n) => document.querySelectorAll('#recordsPane .daily-item').length === n + 1, before, { timeout: 5000 });
    check(S, '기록 화면 추가 → #recordsPane 교체', (await page.locator('#recordsPane .daily-item').count()) === before + 1);
    await page.locator('#recordsPane .daily-item').first().locator('button.icon-btn').click();
    await page.waitForFunction((n) => document.querySelectorAll('#recordsPane .daily-item').length === n, before, { timeout: 5000 });
    check(S, '기록 화면 삭제 → #recordsPane 교체', (await page.locator('#recordsPane .daily-item').count()) === before);

    await page.screenshot({ path: path.join(SHOTS, '05-history-records.png'), fullPage: true });

    // 보고서가 없는 주 / 제출된 주에서도 기록이 뜨는지(제출=비잠금 정책과 무충돌)
    await page.goto(BASE + '/entry?week=2026-08-07', { waitUntil: 'networkidle' });
    check(S, '제출된 주에서도 좌측 기록 패널 렌더', (await page.locator('#weekPanel').count()) === 1);
    check(S, '제출된 주 패널 제목이 그 주 라벨', (await page.locator('#weekPanel h2').innerText()).includes('8월 2주'));
    check(S, '제출된 주에도 기록 추가 폼 존재', (await page.locator('#weekPanel .quick-add button[type=submit]').count()) === 1);
    await page.goto(BASE + '/entry?week=2026-06-05', { waitUntil: 'networkidle' });
    check(S, '보고서가 없는 주에서도 기록 패널 렌더', (await page.locator('#weekPanel').count()) === 1);
    check(S, '보고서가 없는 주는 빈 상태 화면', (await page.locator('#writeView .empty-state').count()) === 1);
  }

  // ---------- 시나리오 6: 콘솔 에러 ----------
  {
    const S = '6. 콘솔 에러';
    const fatal = consoleErrors.filter((e) => e.type !== 'warning');
    check(S, '브라우저 콘솔 에러 0건', fatal.length === 0, JSON.stringify(fatal, null, 2));
  }

  await browser.close();

  const failed = results.filter((r) => !r.ok);
  fs.writeFileSync(path.join(OUT, 'playwright_raw_results.json'),
    JSON.stringify({ results, consoleErrors }, null, 2), 'utf8');
  console.log(`\n총 ${results.length}건 / 실패 ${failed.length}건`);
  if (failed.length) {
    console.log('--- 실패 목록 ---');
    failed.forEach((f) => console.log(`  ${f.scenario} :: ${f.name} — ${f.detail}`));
  }
  process.exit(failed.length ? 1 : 0);
}

main().catch((e) => { console.error(e); process.exit(2); });
