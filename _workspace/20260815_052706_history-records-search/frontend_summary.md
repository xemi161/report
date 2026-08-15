# frontend — 히스토리 "한 일 기록" 검색 UI (2026-08-15)

`/history?view=records`의 `recordsPane`에 월 범위 안 검색 입력을 붙였다.
backend가 확정한 계약(`q` 파라미터 / `recordQuery` · `recordSearching` 모델 속성)만 사용했고,
새 엔드포인트나 새 모델 속성을 가정하지 않았다.

## 1. 변경 파일

| 파일 | 변경 |
|------|------|
| `src/main/resources/templates/fragments-daily.html` | `recordsPane`에 검색 UI·범위 줄·빈 상태 분기 추가, `dailyRow`/`dayGroup` 프래그먼트에 `q` 인자 추가 |
| `src/main/resources/static/css/app.css` | `.rec-search` / `.rec-scope` / `.rec-addlock` 추가 (목업에서 이식) |
| `src/main/resources/static/js/app.js` | **변경 없음** (§5 참고) |

Java/설정 파일은 손대지 않았다.

## 2. 프래그먼트 시그니처 변경 (qa 교차검증 대상)

검색 중 삭제해도 필터가 유지되어야 하므로 삭제 URL에 `q`가 실려야 하고, 그 URL을 만드는 건
`dailyRow`다. 그래서 위치 인자를 하나씩 늘렸다 — **호출부 5곳 전부 같이 고쳤다.**

| 프래그먼트 | 변경 전 | 변경 후 |
|---|---|---|
| `dailyRow` | `(note, view, week, month)` | `(note, view, week, month, q)` |
| `dayGroup` | `(g, view, week, month)` | `(g, view, week, month, q)` |

호출부(전부 `fragments-daily.html` 안):

| 호출 위치 | 넘기는 인자 |
|---|---|
| `dayGroup` → `dailyRow` | `(${note}, ${view}, ${week}, ${month}, ${q})` |
| `dashboardCard` → `dailyRow` | `('dashboard', ${week}, null, null)` |
| `dashboardCard` → `dayGroup` | `('dashboard', ${week}, null, null)` |
| `weekPanel` → `dayGroup` | `('entry', ${week}, null, null)` |
| `recordsPane` → `dayGroup` | `('records', null, ${recordMonth}, ${recordQuery})` |

## 3. 사용한 모델 속성 (전부 backend 제공, 임의로 만든 참조 없음)

`recordQuery`(신규) · `recordSearching`(신규) · `recordMonth` · `recordMonthLabel` ·
`prevMonth` · `nextMonth` · `hasPrevMonth` · `hasNextMonth` ·
`recordDayGroups` · `recordCount` · `recordHoursDisplay` · `recordDayCount` · `recordDefaultDate`

## 4. URL / htmx 배선 (qa 교차검증 대상)

| 요소 | 방식 | URL |
|---|---|---|
| 검색 입력 `#recSearch` | `hx-get` | `/history?view=records&month={recordMonth}` (+ 자기 값 `q`를 htmx가 자동 부착) |
| 검색 지우기 X 버튼 | `hx-get` | `/history?view=records&month={recordMonth}` |
| "검색 지우기" 링크(`.rec-addlock`) | `th:href` | `/history?view=records&month={recordMonth}` |
| 이전/다음 달 | `th:href` | `/history?view=records&month={prev/nextMonth}&q={recordQuery}` |
| 기록 추가 | `hx-post` | `/daily-notes?view=records&month={recordMonth}` (검색 중엔 폼 자체가 없음) |
| 기록 삭제 | `hx-post` | `/daily-notes/{id}/delete?view=records&week=&month={month}&q={q}` |

검색 스왑은 `hx-target="#recordsPane" hx-swap="outerHTML"` — 기존 "구조가 바뀌는 동작은 블록 통째 교체" 규약 그대로.
트리거는 `hx-trigger="input changed delay:300ms, search"`.

### ⚠️ 이 프로젝트에 처음 들어온 htmx 속성: `hx-select`

`GET /history`는 **페이지 전체**를 돌려준다(backend가 프래그먼트 전용 GET 라우트를 만들지 않았고,
검색만을 위해 새 라우트를 요구하지 않기로 함). 그래서 응답에서 `#recordsPane`만 떼어 쓰려고
`hx-select="#recordsPane"`을 썼다. 응답 안의 `id="recordsPane"`은 정확히 1개라 선택이 모호하지 않고,
스왑 후 브라우저에서 `header`·`.hist-switch`·`#recordsPane`이 각각 1개임을 확인했다(중복 삽입 없음).

기존 스킬 문서엔 "`hx-include`/`hx-vals`/`hx-boost` 등은 안 쓴다"고만 적혀 있고 `hx-select`는 목록에 없다.
**docs-sync가 배선 규약(스킬/`fragments-entry.html` 주석)에 이 한 건을 명시해두는 게 좋다.**

### 포커스가 살아남는 근거 (회귀 주의)

검색어 한 글자마다 `#recordsPane`이 통째로 교체되지만 입력 포커스/커서 위치는 유지된다 —
htmx가 스왑 직전 `document.activeElement`(+ `selectionStart/End`)를 기억했다가, 그 요소가 DOM에서
사라졌고 **`id`가 있으면** 같은 id의 새 요소로 되돌려주기 때문이다(vendored `htmx.min.js` 2.0.4 코드로 확인,
브라우저로 재확인: 스왑 직후 `activeElement = {id:"recSearch", selStart:4}`).
→ **`id="recSearch"`를 지우면 한 글자 칠 때마다 포커스가 날아간다.**

## 5. app.js를 손대지 않은 이유

- debounce는 `hx-trigger`의 `delay:300ms`가 처리한다(4글자 연타 → 요청 1건, 브라우저로 확인).
- 포커스 복원은 §4대로 htmx 내장 동작이다.
- 기존 `htmx:afterSwap` 핸들러가 `config.verb !== "post"`로 걸러내므로, GET 검색 스왑이
  기록 추가 입력칸으로 포커스를 훔쳐가지 않는다(그대로 두면 맞게 동작).

## 6. 검색 중 화면 차이 (backend 권장 반영)

| 자리 | 평소 | 검색 중(`recordSearching`) |
|---|---|---|
| 통계 | `.stats` 타일 3칸 | `.rec-scope` 한 줄 — `2026년 8월에서 “GTPP” — 2건 · 5h · 2일` |
| 기록 추가 | `.quick-add` 폼 | `.rec-addlock` 안내 + "검색 지우기" 링크 |
| 빈 상태 | `이 달에 기록된 일이 없습니다.` | `검색 결과가 없습니다.` |
| 검색칸 | 아이콘 + 입력 | 아이콘 + 입력 + X(검색 지우기) 버튼 |

- 통계를 타일 그대로 두지 않고 한 줄로 바꾼 건, 걸러진 값이 "이 달 전체"를 읽는 자리에 그대로
  앉으면 어느 모집단인지 알 수 없기 때문(backend 결정 #1의 취지 + 목업의 `.rec-scope` 설계).
  값(건/시간/일)은 전부 그 줄로 옮겨서 버리지 않았다.
- 시간은 `recordHoursDisplay == '0'`이면 생략한다 — `day-sum`이 0h를 안 그리는 기존 규칙과 동일.
- `.quick-add`를 숨긴 건 backend 결정 #5(검색어와 안 맞는 새 기록이 사라진 것처럼 보임) 그대로.

## 7. 디자인 출처

`design/weekly-report-mockup.html`에 이미 `.rec-search`/`.rec-scope` 스타일과 돋보기·X 아이콘이 있어
**색상 토큰·레이아웃 값 그대로 이식**했다. 목업의 `<script>` 로직은 이식하지 않았다(규약대로).
목업과 의도적으로 달라진 점:

- 목업은 검색을 "전체 기간"으로 잡았지만 실제 구현은 **그 달 안**이다(backend 결정 #4) →
  범위 문구를 `전체 기간에서` → `{월}에서`로, placeholder를 `이 달의 기록 검색`으로 맞췄다.
- `.rec-scope`에 시간·일수를 덧붙였다(목업은 건수만) — backend가 그 값들을 검색 결과 기준으로
  다시 계산해 내려주는데 버릴 이유가 없다.
- `input::-webkit-search-cancel-button`을 숨겼다 — 브라우저 기본 X와 우리 X 버튼이 둘 다 보이면
  동작이 다른 버튼 두 개가 나란히 서게 된다.
- `.rec-addlock`은 목업에 대응물이 없어 새로 만들었다(목업은 검색 중 추가 입력을 그냥 없애기만 함).
- 다크모드 관련 추가 없음.

## 8. 작업 중 정정한 것 (다음 사람이 같은 착각 안 하도록)

처음에 `th:with="qParam=${recordSearching} ? ${recordQuery} : null"`을 두고
"null이면 Thymeleaf가 `q=` 파라미터를 아예 빼준다"고 가정했는데 **틀렸다** —
Thymeleaf는 값이 null이든 빈 문자열이든 `q=`를 그대로 렌더링한다(기존 `month=`가 빈 값으로
나가고 있던 것과 동일한 동작). 그래서 그 우회 변수를 걷어내고 `${recordQuery}`를 직접 쓴다.
빈 `q=`는 backend `normalizeQuery`가 "검색 안 함"으로 정규화하므로 동작에 영향 없다.

## 9. 실제 기동 검증 (2026-08-15)

`./gradlew bootRun --args='--server.port=8123 --spring.datasource.url=…/scratchpad/testdb3/db'`로
**사용자 실데이터(`~/.weekly-report/data/db`)를 건드리지 않는 임시 DB**에 기동해서,
Playwright(Chromium) 실브라우저로 확인했다. (검증 후 서버 종료 + 임시 DB 삭제 완료)

| 케이스 | 결과 |
|---|---|
| 검색 없음(2026-08) | `.stats` 3칸 + `.quick-add` 노출, 기록 3건 |
| `GTPP` 4글자 연타 | **`/history` 요청 1건** (`?view=records&month=2026-08&q=GTPP`) — debounce 동작 |
| 필터 결과 | `gtpp 배포…` + `GTPP 로그인…` 2건 (대소문자 무시 매칭 확인) |
| 범위 줄 | `2026년 8월에서 “GTPP” — 2건 · 5h · 2일` |
| 스왑 후 포커스 | `activeElement = {id:"recSearch", value:"GTPP", selStart:4}` — 이어서 ` 로그인`을 치니 `GTPP 로그인`으로 정상 연결 |
| `hx-select` | 스왑 후 `#recordsPane` 1개 / `header` 1개 / `.hist-switch` 1개 (중복 없음) |
| 월 이동 | 이전 달 링크 `?view=records&month=2026-07&q=GTPP` → 이동 후 입력칸에 `GTPP` 유지, `2026년 7월에서 “GTPP” — 1건 · 2h · 1일` |
| 검색 지우기 X | 입력칸 `""`, `.stats` 3칸 + `.quick-add` 복귀 |
| 결과 0건 | `검색 결과가 없습니다.` (평소 문구와 분기됨) |
| 검색 중 삭제 | 필터 유지된 채 재계산 (`— 1건` → `— 0건`), 입력칸 값 `GTPP` 유지 |
| `q`가 공백만 | `recordSearching=false`로 떨어져 평소 화면 복귀 |
| 한글 검색 | `q=결제` → `2026년 8월에서 “결제” — 1건 · 4h · 1일` 정상 |
| JS 콘솔 에러 | 없음 |
| 좁은 뷰포트(520px) | `.rec-toolbar` flex-wrap으로 페이저+검색칸 정상 배치 |

### 검증 중 발견해서 고친 것

`.rec-addlock` 안내가 세 줄로 쪼개져 보였다 — `.link-more`가 `display:flex`라 문장 중간에서
줄바꿈을 일으킨 것. 줄 자체를 `display:flex; align-items:center; gap:4px`로 잡고 문구를
`검색 중에는 새 기록을 추가할 수 없습니다.` + `검색 지우기` 링크로 정리해 한 줄로 맞췄다.

## 10. 회귀 확인 필요 (qa)

`dailyRow`/`dayGroup` 인자를 늘렸으므로 **대시보드 "오늘 한 일" 카드**와 **작성 화면 좌측 패널**도
같이 봐줘야 한다. 서버 렌더링으로는 확인해뒀다:

- `/` → 삭제 URL `…/delete?view=dashboard&week=2026-08-14&month=&q=`
- `/entry` → 삭제 URL `…/delete?view=entry&week=2026-08-14&month=&q=`

즉 `q=`가 빈 값으로 붙지만(§8), 이 두 뷰의 `renderView`는 query를 아예 쓰지 않으므로 무해하다.
`month=`가 이미 같은 형태로 나가고 있던 것과 동일하다.
