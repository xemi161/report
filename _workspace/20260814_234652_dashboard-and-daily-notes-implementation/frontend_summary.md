# frontend 작업 요약 — 대시보드 재도입(3탭) + 일일 기록(DailyNote) 화면

작업일 2026-08-15 · 실제 앱 기동 검증 완료(임시 포트 + in-memory H2로 `/`, `/entry`, `/history`, `/history?view=records` 및 기록 CRUD 왕복 확인)

> backend가 `_workspace/.../backend_summary.md` §3~§5에 적어둔 라우트·파라미터·모델 속성명을 **그대로** 사용했다.
> 새 엔드포인트를 만들거나 데이터 shape을 가정한 곳은 없다.

---

## 1. 파일 변경

| 파일 | 상태 |
|---|---|
| `src/main/resources/templates/fragments-daily.html` | **신규** — backend 계약 프래그먼트 3종 + 공용 행/날짜그룹 |
| `src/main/resources/templates/dashboard.html` | **신규** — `GET /`의 뷰 |
| `src/main/resources/templates/fragments.html` | 변경 — 헤더 탭 3개, 페이저 조건, `reportCard` 프래그먼트 추가 |
| `src/main/resources/templates/entry.html` | 변경 — `body.wide` + `.split` 좌우 분할로 감쌈(내부 `#writeView`는 그대로) |
| `src/main/resources/templates/history.html` | 변경 — 서브 세그먼트 2개 + `reportCard` 재사용 + `recordsPane` 분기 |
| `src/main/resources/static/css/app.css` | 변경 — 대시보드/일일기록/기록화면 스타일 추가(새 토큰 없음, 다크모드 없음) |
| `src/main/resources/static/js/app.js` | 변경 — textarea 자동높이, 날짜합계 재계산, 추가 후 재포커스, 패널 숨기기 |

`fragments-entry.html`은 **손대지 않았다**(항목 행/미리보기 모달은 그대로).

---

## 2. 프래그먼트 목록 (qa 대조용)

### `fragments-daily.html` — backend 계약 3종 + 내부 공용 2종

| 프래그먼트 | 루트 element id | 누가 쓰나 |
|---|---|---|
| `fragments-daily :: dashboardCard` | `#dashDaily` | `dashboard.html`, `POST /daily-notes*?view=dashboard` 응답 |
| `fragments-daily :: weekPanel` | `#weekPanel` | `entry.html`, `POST /daily-notes*?view=entry` 응답 |
| `fragments-daily :: recordsPane` | `#recordsPane` | `history.html`(view=records), `POST /daily-notes*?view=records` 응답 |
| `fragments-daily :: dailyRow(note, view, week, month)` | `form.daily-item` | 위 3종 내부 공용 (행 = 저장 폼) |
| `fragments-daily :: dayGroup(g, view, week, month)` | `div.day-group` | 위 3종 내부 공용 (날짜 헤더 + 그날 행들) |

### `fragments.html`

| 프래그먼트 | 변경 |
|---|---|
| `fragments :: head(pageTitle)` | 변경 없음 |
| `fragments :: header(activeTab, period, prevWeek, nextWeek)` | 탭 3개(`대시보드`/`작성`/`히스토리`), 히스토리 라벨에서 건수 제거, 페이저는 `activeTab == 'write'`일 때만 |
| `fragments :: reportCard(r, lowThreshold)` | **신규** — 제출된 주 카드 1장(대시보드·히스토리 공용) |

`activeTab` 값: `dashboard`(신규) / `write` / `history`.

---

## 3. htmx 배선 (라우트 ↔ target ↔ swap)

| 트리거 | 요청 | hx-target | hx-swap |
|---|---|---|---|
| 대시보드 캡처 입력(폼 submit) | `POST /daily-notes?view=dashboard&week={week}` | `#dashDaily` | `outerHTML` |
| 작성 패널 추가(폼 submit) | `POST /daily-notes?view=entry&week={week}` | `#weekPanel` | `outerHTML` |
| 기록 화면 추가(폼 submit) | `POST /daily-notes?view=records&month={recordMonth}` | `#recordsPane` | `outerHTML` |
| 기록 행 삭제(휴지통) | `POST /daily-notes/{id}/delete?view=&week=&month=` | 위 3개 중 `view`에 대응하는 id | `outerHTML` |
| 기록 행 텍스트·시간 수정 | `POST /daily-notes/{id}` (`hx-trigger="change"`) | — | `none` |
| 월 이동(이전/다음 달) | `GET /history?view=records&month=` **일반 링크**(htmx 아님) | — | — |
| 탭 전환 | `GET /` · `/entry` · `/history` **일반 링크** | — | — |

- 행 하나가 통째로 `<form>`이라 텍스트/시간이 함께 전송된다 → `hours=`(빈 값)가 항상 실려 backend의 "빈 문자열이면 null로 지움" 규칙과 맞는다.
- 삭제 버튼은 행 폼 안의 `type="button"` + 자체 `hx-post`(기존 항목 행의 `deleteItemBtn`과 같은 패턴).
- `view`가 `entry`/`dashboard`일 때 `month=`가, `records`일 때 `week=`가 빈 값으로 실려 나간다 — Spring이 빈 문자열을 null로 바인딩해 정상 동작함을 실제 요청으로 확인했다(`/daily-notes/1/delete?view=records&week=&month=2026-08` → 200).
- **추가 폼에 `type="submit"` 버튼을 넣었다**(목업 패널엔 없던 요소). 입력 칸이 둘 이상인 폼은 submit 버튼이 없으면 브라우저가 Enter 암묵적 전송을 하지 않아, 목업의 "Enter로 추가"가 그대로는 동작하지 않기 때문이다.

## 4. 사용한 모델 속성 (템플릿별)

### `dashboard.html`
`period`(`period.label()`, `period.weekStart()`, `period.weekEnd()`), `week`, `lowThreshold`, `report`(`report.status.name()`만 참조), `heroTotalHours`, `heroManWeek`, `heroItemCount`, `avgWeekCount`, `avgManWeek`, `avgWeeks`(`w.weekLabel`, `w.totalManWeek`), `activeProjects`(`p.project.name`, `p.project.ticket`, `p.completion`, `p.lastWeekLabel`), `activeProjectCount`, `recentReports`, `pastReportCount`
+ `fragments-daily :: dashboardCard`가 쓰는 속성 전부(아래)
+ 헤더용 `settings`

> ⚠️ **`report.totalHours` / `report.totalManWeek`은 대시보드에서 쓰지 않았다**(draft 주에서 0이라 backend가 금지). hero 숫자는 전부 `hero*` 속성이다.

### `fragments-daily :: dashboardCard`
`today`, `week`, `todayNotes`, `todayNoteCount`, `todayHoursDisplay`, `recentDayGroups`, `weekNoteCount`, `weekHoursDisplay`

### `fragments-daily :: weekPanel`
`panelIsCurrentWeek`, `period`(비-이번주 제목), `weekNoteCount`, `weekHoursDisplay`, `panelDayGroups`, `panelDates`, `panelDefaultDate`, `week`

### `fragments-daily :: recordsPane`
`hasPrevMonth`, `prevMonth`, `hasNextMonth`, `nextMonth`, `recordMonthLabel`, `recordMonth`, `recordCount`, `recordHoursDisplay`, `recordDayCount`, `recordDefaultDate`, `recordDayGroups`

### `dailyRow` / `dayGroup`
`note.id`, `note.text`, `note.hoursDisplay()` / `g.label()`, `g.isToday()`, `g.hoursDisplay()`, `g.count()`, `g.notes`
(`g.date`는 쓰지 않았다 — 추가 폼의 `workDate`는 `panelDates`/`recordDefaultDate`/`today`에서 온다.)

### `history.html`
`historyView`, `reportCount`, `dailyNoteCount`, `reports`, `lowThreshold`

### `entry.html`
기존 속성 그대로(`report`, `projectCards`, `devItems`, `etcItems`, `vacationItems`, `avgManWeek`(4주), `activeProjectCount`, `week`, `period`, `prevWeek`, `nextWeek`, `toast`, `error`) + 좌측 패널 속성(위 weekPanel).
**작성 탭 상단 통계 카드는 건드리지 않았다** — 여전히 "최근 4주 평균 맨위크 / 진행중 프로젝트 수"다(backend가 그 주 총시간/맨위크/항목수 속성을 `/entry`에 주지 않으므로 범위 밖).

### 더 이상 안 쓰는 속성
`historyCount`(LayoutAdvice) — 헤더 탭 라벨에서 건수를 뺐으므로 템플릿 참조가 사라졌다. 모델에는 그대로 남아 있다(제거 여부는 backend 판단).

---

## 5. CSS — 정확한 수치(반올림 금지 항목 포함)

```css
main{max-width:1140px;}                     /* 대시보드·히스토리·패널 숨긴 작성 화면 */
body.wide main{max-width:1670px;}           /* 작성 탭 + 패널 표시 중일 때만 */
body.wide .header-inner{max-width:1650px;}
.split{grid-template-columns:minmax(0,680px) minmax(916px,1fr); gap:18px; align-items:start;}
.split.panel-hidden{grid-template-columns:1fr;}   /* "숨기기" 눌렀을 때(app.js가 붙임) */
@media (max-width:1280px){ .split{grid-template-columns:1fr;} .daily-panel{position:static; max-height:none; overflow:visible;} }
.daily-panel{position:sticky; top:84px; max-height:calc(100vh - 108px); overflow:auto; padding:20px 20px 18px;}
.dash-grid{grid-template-columns:minmax(0,1fr) minmax(0,320px); gap:16px; align-items:stretch;}
@media (max-width:900px){ .dash-grid{grid-template-columns:1fr; align-items:start;} }
.day-head .day-sum{margin-left:auto; padding-right:33px;}   /* 삭제버튼25+gap6+여백2 → 아래 'h'와 세로 정렬 */
.quick-add input.qa-hours{flex:0 0 62px;}
```

- **680 / 916 / 1670 / 1650은 계산값**이다(worklog §6). 항목 행 최소 폭 752px, 완료+이월 배지 동시 860px + 카드 패딩 56 = 우측 하한 916px, 56+680+18+916 = 1670px.
- 새 색상 토큰 없음, `prefers-color-scheme` 없음, `:root[data-theme="dark"]` 그대로(라이트 고정 정책 유지).
- `.daily-item input[type=number]`를 기존 `.row-main/.simple-row` 인풋 스코프에 **추가**해 `.hours-input`을 그대로 재사용했다(새 컴포넌트 없음).
- 추가한 앱 전용 규칙: `[hidden]{display:none !important}`(패널 숨김), `a.btn`(hero CTA가 링크라서), `.pager-disabled`(월 페이저 끝), `.rec-pane`(htmx 교체 단위가 `.view`의 gap을 대신 만듦).

---

## 6. app.js가 맡은 것 (서버가 못 하는 자리)

| 동작 | 훅 |
|---|---|
| 기록 textarea 자동 높이 | `input` on `.d-txt` + 최초 1회 + `htmx:afterSwap` |
| 기록 Enter = 편집 종료(blur→change→저장) | `keydown` on `.d-txt` |
| 날짜 헤더 합계 재계산(시간 수정은 `hx-swap="none"`이라 서버가 못 갱신) | `input` on `.daily-item .hours-input` → 그 `.day-group`의 `.day-sum` 갱신/생성/삭제(합 0이면 제거) |
| 대시보드 "오늘 N건 · Xh" 칩 재계산(오늘 행은 날짜 헤더가 없음) | 같은 훅 → `.dash-daily > .daily-item` 합계 |
| 기록 추가 후 입력칸 재포커스 | `htmx:afterSwap` + `requestConfig.path`가 `/daily-notes`로 시작하고 `/delete`가 아닐 때 `[data-daily-newtext]`에 focus |
| 좌측 패널 숨기기/보이기 + `body.wide` 토글 | `[data-panel-hide]` / `[data-panel-show]` 클릭, 상태는 `localStorage("weeklyReport.dailyPanelHidden")` |

> 대시보드 하단 "이번 주 기록 N건 · Xh"는 **클라이언트에서 갱신하지 않는다** — 화면에 없는 날(최근 3일 밖)의 기록까지 포함하는 값이라 DOM만으로 계산할 수 없다. 추가/삭제 때 서버가 카드를 통째로 다시 그리면서 정확해진다. 시간만 인라인 수정한 직후에는 잠시 옛 값이 남는다(의도적 절충).

---

## 7. 기동 검증 결과

`./gradlew bootRun --args="--server.port=9112 --spring.datasource.url=jdbc:h2:mem:... --spring.jpa.hibernate.ddl-auto=create"`로 사용자 DB를 건드리지 않고 확인:

- `GET /` 200 (보고서 없음 → `미작성` 배지 / draft → `작성중` + hero 지표 / 과거 제출본 카드 렌더 / 진행중 프로젝트 행 렌더 / 맨위크 막대 `width:100.00%` 캡)
- `GET /entry` 200 (`#splitLayout` + `#weekPanel` + `#writeView` 공존, `body class="wide"`, 날짜 셀렉트 `08.15 (토)` 형식, 지난 주는 제목이 `8월 2주에 한 일`)
- `GET /history` 200 / `GET /history?view=records` 200 (월 라벨 `2026년 8월`, 양끝 달은 `pager-disabled`)
- `POST /daily-notes?view=dashboard|entry|records` 각각 200 + 해당 프래그먼트 루트 id 확인, 칩/합계 값이 같이 갱신됨(`2건 · 3.5h`, `day-sum 1.5h`)
- `POST /daily-notes/{id}` 200 + 빈 응답(0 bytes), `hours=` 빈 값으로 시간 지워짐(`placeholder="-"` 복귀)
- `POST /daily-notes/{id}/delete?view=records&week=&month=2026-08` 200 + `#recordsPane` 재렌더
- 기존 `#writeView` 스왑(`POST /entry/items`, `/entry/start`) 정상 — 분할 래퍼가 스왑 대상에 영향 없음
- UTF-8 한글 왕복 정상(퍼센트 인코딩으로 보낸 "테스트 기록"이 그대로 렌더)

## 8. qa가 알아야 할 것 / 남긴 것

1. **정렬 방향 차이는 의도된 설계다** — 작성 패널 금→목 오름차순 / 기록 화면 최신순 내림차순.
2. **월 밖 날짜로 기록을 추가하면 목록에서 사라져 보인다** — 기록 화면의 날짜 칸은 `date` 입력이라 다른 달을 고를 수 있는데, 서버는 요청에 실린 `month`(보고 있던 달)로 다시 그린다. 목업은 "적은 날짜의 달로 따라간다"였지만 backend 계약에 그 동작이 없어 구현하지 않았다. 필요하면 backend에 요청할 사항.
3. **검색(`q`)은 UI에 넣지 않았다**(요청대로). `.rec-search`/`.rec-scope` CSS도 이식하지 않았다.
4. 대시보드 hero의 "신규 작성"은 **초안을 만들지 않고** `/entry?week=`로 이동한다 — 초안 생성은 작성 화면의 `POST /entry/start`가 유일한 경로다(목업은 즉시 생성이었으나 그러려면 새 엔드포인트가 필요).
5. 뷰포트 검증 포인트: 1670px↑ 패널 680px / 1536px 약 530px / 1280px 이하 세로 스택, 그리고 **대시보드·히스토리·패널 숨긴 작성 화면은 1140px**.
6. `historyCount` 모델 속성이 이제 어느 템플릿에서도 참조되지 않는다.
