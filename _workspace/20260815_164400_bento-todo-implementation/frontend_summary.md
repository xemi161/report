# frontend_summary — 벤토 대시보드 + TODO 리스트 이식 (2026-08-15/16)

`./gradlew compileJava` 통과, `./gradlew test` 전부 통과.
`./gradlew bootRun`(별도 포트 9188 + 인메모리 H2)으로 실제 렌더링 + 헤드리스 크롬(CDP)으로 클라이언트 동작까지 실측 확인.
backend가 확정한 라우트/모델 속성은 **하나도 바꾸지 않았다** — 아래 표가 화면이 실제로 참조하는 최종본이다.

## 1. 변경/신규 파일

| 경로 | 상태 | 내용 |
|---|---|---|
| `src/main/resources/templates/fragments-todo.html` | **신규** | `todoRow(todo, overdue, collapsed)` / `todoCard` 두 프래그먼트 |
| `src/main/resources/templates/dashboard.html` | **전면 재작성** | 벤토 그리드(hero → 지표4 → daily → todo → hist) |
| `src/main/resources/templates/fragments-daily.html` | 부분 수정 | `dashboardCard` 루트에 `tile t-daily` 추가, 목록을 `.tile-body`로 감쌈 |
| `src/main/resources/static/css/app.css` | 부분 재작성 | `.bento`/`.tile`/`.metric`/hero 요일 스트립/`.todo-*`/`.prio` 추가, 죽은 대시보드 클래스 제거 |
| `src/main/resources/static/js/app.js` | 부분 추가 | TODO 카드 클라이언트 동작(접기·완료 여닫기·우선순위 pill·지표 동기화) |

## 2. 프래그먼트 계약 (qa 교차검증용)

### `fragments-todo :: todoCard`
- 루트: `<div id="todoCard" class="card tile todo-card t-todo" data-overdue-count data-duetoday-count>`
- **`id="todoCard"`가 모든 htmx 요청의 `hx-target`이다.** 루트에 벤토 배치 클래스(`tile t-todo`)가 붙어 있어야 한다 — `outerHTML`로 통째 교체되므로 대시보드 쪽에서 감싸 붙일 수 없다.
- `data-overdue-count` / `data-duetoday-count`는 화면용 데이터가 아니라 **지표 타일 동기화용 전달값**(아래 4절).

### `fragments-todo :: todoRow(todo, overdue, collapsed)`
| 인자 | 의미 |
|---|---|
| `todo` | `TodoItem` |
| `overdue` | 기한 지남 그룹의 행인가(그 그룹에서만 행마다 `.od` 날짜 칩) |
| `collapsed` | 접힘 상태에서 감출 행인가 → `.over-limit` 클래스 |

- 행은 **`<form>`이 아니라 `<div>`**다. 컨트롤마다 목적지가 달라 각 컨트롤이 자기 `hx-post`를 갖는다(htmx가 트리거 요소 자신의 `name` 값을 실어 보낸다).

## 3. htmx 배선 최종본 (템플릿 ↔ 라우트)

| 트리거 요소 | 메서드·경로 | 보내는 값 | hx-target / swap |
|---|---|---|---|
| `.quick-add` 폼 submit | `POST /todos` | `dueDate`(date input) · `priority`(hidden) · `text` | `#todoCard` / `outerHTML` |
| `.t-txt` textarea `change` | `POST /todos/{id}` | `text` | — / **`none`** |
| `.todo-check` checkbox `change` | `POST /todos/{id}/done` | 없음 | `#todoCard` / `outerHTML` |
| `.prio` 버튼 click | `POST /todos/{id}/priority` | 없음 | `#todoCard` / `outerHTML` |
| `.row-detail` 안 date input `change` | `POST /todos/{id}/due` | `dueDate` | `#todoCard` / `outerHTML` |
| 휴지통 `.icon-btn` click | `POST /todos/{id}/delete` | 없음 | `#todoCard` / `outerHTML` |

- `week` 파라미터는 **어디에도 붙이지 않는다**(할 일은 주에 속하지 않는다).
- `hx-confirm`은 쓰지 않았다 — 일일 기록 삭제와 같은 무게로 봤다.
- 기한 변경 패널 여닫기는 서버 왕복 없음(기존 `data-detail-toggle` 재사용, id = `todoDue{todo.id}`).

## 4. 사용한 모델 속성 (전부 backend 제공분 그대로)

**TODO 카드** — `todoOpenCount`(제목 옆 칩) · `todoOverdueCount`(기한 지남 헤더 건수 + 카드 data 속성) · `todoOverdue` · `todoDayGroups`(`label()`/`isToday()`/`startIndex()`/`todos()`) · `todoVisibleLimit`(행 감춤 판정) · `todoHiddenCount`("+N건 더 보기") · `todoDone` · `todoDoneCount` · `todoDueTodayCount`(카드 data 속성) · `todoDefaultDue` · `todoDefaultPriority`(`.name()`/`.label()`) · `todoPriorities`(숨은 라벨 목록).
`TodoItem`에서 호출: `id` · `done` · `text` · `dueDate` · `priority.name()` · `priority.label()` · `dueShortLabel()`.

**대시보드** — `period.label()`/`weekStart()`/`weekEnd()` · `report`(+`status.name()`) · `week` · `lowThreshold` · `heroLoggedDays` · `heroWeekHoursDisplay` · `heroDays`(`date()`/`dow()`/`hoursDisplay()`/`barPercent()`/`today()`/`isEmpty()`) · `heroTotalHours` · `heroManWeek` · `heroItemCount` · `todoOverdueCount` · `todoDueTodayCount` · `activeProjectCount` · `activeProjectAvgProgress` · `recentReports` · `pastReportCount`.

- **`avgWeeks`/`activeProjects` 참조는 전부 사라졌다**(요청대로 정리 완료).
- ⚠️ **`avgManWeek`/`avgWeekCount`는 이제 대시보드에서 아무 데도 안 쓴다** — 벤토에 "최근 2주 평균 맨위크" 타일이 없기 때문. backend가 유지한 속성이라 지우지 않았고 화면 오류도 없다(작성 탭의 `avgManWeek`는 `EntryController`가 주는 **다른 지표**이므로 무관). 정리할지는 backend 판단.
- "전체 보기" 링크 조건은 `pastReportCount > #lists.size(recentReports)`로 걸었다(5를 하드코딩하지 않음).

## 5. app.js에 추가한 클라이언트 전용 동작

| 동작 | 트리거 | 비고 |
|---|---|---|
| 접기/펼치기 | `[data-todo-more]` | `#todoCard`에 `.expanded` 토글. 라벨은 `data-more-label`/`data-less-label`(서버가 건수를 넣어 내려준다) |
| 완료 목록 여닫기 | `[data-todo-donetoggle]` | `.done-open` 토글. 라벨은 `data-show-label`/`data-hide-label` |
| 추가줄 우선순위 순환 | `[data-todo-newprio]` | 아직 없는 할 일이라 서버에 순환시킬 대상이 없다. 값은 hidden `[data-todo-newprio-value]`로 전송, 라벨/순서는 숨은 `[data-prio-map]`에서 읽는다(한국어 하드코딩 없음) |
| 지표 타일 동기화 | 스왑 직후 | `[data-metric-overdue]`의 숫자·sub·`.low`를 카드의 `data-*`에서 옮겨 적는다 |
| 추가 후 재포커스 | 스왑 직후 | 경로가 `/todos`(추가)일 때만 — 정규식 `^\/todos(\?|$)`로 `/todos/{id}/...`와 구분 |
| textarea 자동 높이 / Enter=편집 종료 | `.t-txt` | 기존 `.d-txt` 규칙에 합류 |

세 상태(펼침·완료펼침·고른 우선순위)는 **메모리 변수**다 — 카드가 htmx로 교체되는 사이엔 유지되고 새로고침하면 초기값. 승인된 목업의 수명과 같다(취향 설정이 아니라 그때그때 열람 상태라 localStorage를 쓰지 않았다).

## 6. 실측으로 잡은 함정 2개 (재발 방지용 — 코드 주석에도 남김)

1. **htmx는 `class`를 `attributesToSettle` 대상으로 삼는다.** 스왑 직후에는 옛 요소의 class를 잠시 유지했다가 settle(기본 20ms 뒤)에 서버 값으로 되돌린다. 그래서 `htmx:afterSwap`에서 붙인 `.expanded`는 조용히 지워졌다 — **펼쳐둔 목록이 할 일 하나만 추가하면 다시 접히는데 버튼 라벨은 "접기"인 상태**(라벨은 textContent라 살아남는다). → `htmx:afterSettle`에서 클래스를 다시 입힌다. `data-*`는 settle 대상이 아니라 스왑 직후에도 새 값이다(지표 동기화가 afterSwap에서 정상 동작하는 이유).
2. **감춰진 textarea는 `scrollHeight`가 0이라 자동 높이가 `0px`로 굳는다.** 접힌 행·완료 행이 그 상태로 펼쳐지면 글자가 안 보였다. → `autoGrow`가 0이면 인라인 높이를 지우고, `applyTodoState`가 펼칠 때 다시 잰다.

## 7. CSS 메모

- 벤토 span 값(`t-hero` 6×4 / `t-metric` 3×2 / `t-daily` 8×6 / `t-todo` 4×6)과 **dashboard.html의 타일 나열 순서는 한 세트**다. `grid-auto-flow`가 dense가 아니라 순서를 바꾸면 마지막 띠에 빈 구멍이 남는다.
- 반응형은 목업 그대로 1180px(6컬럼, 행 스팬 해제, 내부 스크롤 off) / 700px(2컬럼). 1100px 실측 확인함.
- **목업에 없는 추가분 1줄**: `.hero-left{flex:1 1 240px}`. 목업 데모엔 "미작성" 주가 없어 안 드러났던 상태 때문 — 미작성이면 한 줄짜리 `.hero-sub` 대신 두세 줄짜리 `.hero-note`가 들어와 CTA가 아래로 밀리고, 격자 행 높이가 고정이라 타일이 늘어나는 대신 **요일 스트립이 잘렸다**. 왼쪽이 줄어들게 해서 해결(1440px 실측 확인).
- 제거한 죽은 클래스: `.dash-grid` `.dash-side` `.hero-metrics` `.mw-*` `.proj-*` `.bar`(가로 진행률 바). 목업에서도 같은 이유로 지워진 것들이고, 템플릿 잔재 없음을 grep으로 확인했다. 세로 막대 `.wk-day .bar-v`는 hero가 계속 쓴다.
- 다크모드는 손대지 않았다(라이트 톤 고정 정책 유지).

## 8. qa가 봐줬으면 하는 지점

- 컨트롤러 라우트 6개 ↔ `fragments-todo.html`의 `hx-post` 6종 일치(3절 표).
- `TodoItemController.populateTodoCard()`가 넣는 12개 속성 ↔ 템플릿 참조(4절).
- `DashboardControllerTest`에 벤토 마크업 기준 검증(예: `t-hero`/`t-todo` 존재, `avgWeeks` 미참조)을 넣을지 판단.
- 실측 시나리오(참고): 미완료 8건 초과 + 완료 1건 이상 + 기한 지남 1건 이상을 만들어 두고 ① "+N건 더 보기" → 펼침 유지된 채 할 일 추가 ② 기한 지난 항목 완료 → 지표 타일 숫자가 같이 줄어드는지 ③ 펼친 뒤 긴 문장 행의 textarea 높이.
