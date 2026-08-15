# qa — 히스토리 "한 일 기록" 검색 검증 리포트 (2026-08-15)

**결론: 통과.** 정적 교차검증에서 불일치 0건, 회귀 0건. 런타임 검증 중 **버그 2건을 발견해 직접 수정하고 재검증까지 완료**했다.
`./gradlew test` 전체 그린 (67 tests, 이번에 13개 추가).

---

## 1. 정적 교차검증 — 불일치 없음

### 1-1. 라우트 ↔ 템플릿 URL

| 템플릿 위치 | URL | 컨트롤러 | 판정 |
|---|---|---|---|
| `fragments-daily.html:204` 검색 input `hx-get` | `/history?view=records&month=…` (+htmx가 자기 `q` 부착) | `HistoryController.list(view, month, q)` | OK |
| `:208` X 버튼 `hx-get` | `/history?view=records&month=…` (q 없음) | 동일 | OK |
| `:167,175` 월 이동 `th:href` | `/history?view=records&month=…&q=…` | 동일 | OK |
| `:259` "검색 지우기" 링크 | `/history?view=records&month=…` | 동일 | OK |
| `:249` 기록 추가 `hx-post` | `/daily-notes?view=records&month=…` | `DailyNoteController.add(form, view, week, month, q, model)` | OK |
| `:35` 기록 삭제 `hx-post` | `/daily-notes/{id}/delete?view=…&week=…&month=…&q=…` | `DailyNoteController.delete(...)` | OK |
| `:28` 인라인 수정 `hx-post` | `/daily-notes/{id}` (q 없음) | `update(...)` — `hx-swap="none"`이라 재렌더 없음 | OK(의도적) |

`X 버튼이 q를 실어 보내지 않는가`를 따로 확인했다 — `#recordsPane`은 어떤 `<form>` 안에도 들어 있지 않아
(htmx는 트리거 요소 자신의 값 + 감싸는 form만 싣는다) 버튼이 형제 input의 `q`를 끌어가지 않는다. 실측으로도 검색이 풀린다.

### 1-2. 모델 속성명 ↔ 템플릿 참조

`recordQuery` / `recordSearching` / `recordMonth` / `recordMonthLabel` / `prevMonth` / `nextMonth` /
`hasPrevMonth` / `hasNextMonth` / `recordDayGroups` / `recordCount` / `recordHoursDisplay` /
`recordDayCount` / `recordDefaultDate` — 템플릿이 참조하는 이름 전부가 `populateRecordsView`에 존재. 오타/누락 0건.
(수정 후 `recordQueryRaw` 1개 추가 — §2-1 참조)

### 1-3. 프래그먼트 시그니처 ↔ 호출부 (검증 항목 ①)

`dailyRow(note, view, week, month, q)` / `dayGroup(g, view, week, month, q)` — 호출부는
`templates/` 전체 grep 결과 **정확히 5곳뿐이고 전부 5인자로 갱신**되어 있다.

| 호출부 | 인자 | q 판정 |
|---|---|---|
| `dayGroup` → `dailyRow` | `(note, view, week, month, q)` | 전달 |
| `dashboardCard` → `dailyRow` | `(note, 'dashboard', week, null, null)` | **null 맞음** |
| `dashboardCard` → `dayGroup` | `(g, 'dashboard', week, null, null)` | **null 맞음** |
| `weekPanel` → `dayGroup` | `(g, 'entry', week, null, null)` | **null 맞음** |
| `recordsPane` → `dayGroup` | `(g, 'records', null, recordMonth, recordQuery)` | 전달 |

대시보드/작성 두 화면엔 검색이 없고, `renderView()`의 `dashboard`/`entry` 분기는 `query`를 아예 읽지 않으므로
`q=null`이 맞다. 렌더 결과 URL에 `month=&q=`가 빈 값으로 붙지만(Thymeleaf는 null/빈 값도 파라미터를 지우지 않는다)
`@DateTimeFormat` LocalDate와 String 모두 빈 값 → null/미사용으로 떨어져 무해하다.
`month=`이 이미 같은 형태로 나가고 있었으므로 **이번 변경으로 새로 생긴 문제는 아니다**.

### 1-4. 지연 로딩 / `.md` 스펙

`DailyNote`는 연관관계가 없는 엔티티라 `open-in-view: false` 함정 무관.
`MdExportService`·`docs/weekly-report-md-schema.md`는 이번 변경과 접점 없음(기록은 `.md`에 안 나간다).

---

## 2. 발견 및 수정한 버그 2건

### 2-1. [수정 완료] 타이핑 중 서버가 사용자가 방금 친 공백을 지운다

- **생산자**: `web/DailyNoteController.java` — `recordQuery`에 **정규화된(=trim된)** 검색어를 담아 내려줌
- **소비자**: `templates/fragments-daily.html` — 그 값을 검색 input의 `th:value`로 그대로 씀
- **재현**: `/history?view=records&month=2026-08`에서 `GTPP `까지 치고 **300ms 이상 멈춘다**
  → `hx-get`이 나가고 `#recordsPane`이 통째로 교체되는데, 서버가 `value="GTPP"`(공백 제거)를 돌려준다
  → 입력칸에서 방금 친 공백이 사라지고 캐럿이 4로 당겨진다
  → 이어서 `로그인`을 치면 `GTPP로그인`이 되어 검색이 0건으로 떨어진다.
  (curl 실측: `q=GTPP ` → `value="GTPP"`, `q= GTPP` → `value="GTPP"`)
- **왜 놓치기 쉬운가**: "타이핑을 멈춘 뒤 이어 치는" 흐름에서만 나온다. 연타 검증(`GTPP` 4글자)에서는 절대 안 나온다.
- **수정**: `recordQueryRaw`(정규화 전 원문) 모델 속성을 추가하고 **입력칸 value만** 그것을 쓰게 했다.
  월 이동 링크·범위 문구(`.rec-scope`)·`dayGroup`의 `q` 인자는 **기존대로 정규화된 `recordQuery`** 를 쓴다
  (원문이 URL로 새 나가도 서버가 다시 정규화하므로 무해하지만, 링크와 문구는 깔끔한 값이 낫다).
  - `DailyNoteController.populateRecordsView` — `model.addAttribute("recordQueryRaw", q == null ? "" : query);`
  - `fragments-daily.html` — `th:value="${recordQueryRaw}"` + 이유 주석
- **재검증**: `q=GTPP ` → `value="GTPP "`, 링크는 `…&q=GTPP`, 범위 문구는 `“GTPP”`. 통과.

### 2-2. [수정 완료] 전각 공백(U+3000)만 친 검색이 "검색 중"으로 잡힌다

- **위치**: `service/DailyNoteService.normalizeQuery` — `trim()`은 U+0020 이하만 뗀다
- **재현**: 검색칸에 한글 IME 전각 공백만 입력(`q=%E3%80%80`)
  → `normalizeQuery`가 `"　"`를 살려 보내 `recordSearching=true`
  → 검색칸은 **비어 보이는데** 통계 타일과 기록 추가 폼이 사라지고 `검색 결과가 없습니다.`만 남는다(빠져나갈 단서 없음)
  - curl 실측(수정 전): `rec-scope` 렌더 + 매칭 0건 / 수정 후: `stats` 3칸 + `quick-add` 복귀, 전체 5건
- **수정**: `trim()` → `strip()` (유니코드 공백 전부 처리). 반각 공백 동작은 그대로.
- **재검증**: 통과.

---

## 3. `hx-select="#recordsPane"` 재확인 (검증 항목 ②) — 문제 없음

**규약 충돌 아님.** 기존 규약은 "구조가 바뀌는 동작은 그 블록을 통째로 `outerHTML` 교체"이고,
이번 검색도 정확히 `hx-target="#recordsPane" hx-swap="outerHTML"`로 **블록 통째 교체**다.
`hx-select`는 스왑 단위를 바꾸는 속성이 아니라 **응답에서 그 블록만 떼어내는 응답측 필터**다 —
`GET /history`가 프래그먼트 전용 라우트 없이 페이지 전체를 주기 때문에 필요한 것이고,
"검색 하나 때문에 새 라우트를 만들지 않는다"는 선택의 대가로는 타당하다.

중복 삽입/부작용 실측(htmx 헤더 붙여 `GET /history?view=records&month=2026-08&q=GTPP`):

| 항목 | 응답 내 개수 |
|---|---|
| `id="recordsPane"` | 1 |
| `<header>` | 1 |
| `.hist-switch` | 1 |
| `id="recSearch"` | 1 |
| `id="toast"` | 1 |

선택자가 모호하지 않고(정확히 1개), 스왑 대상 밖의 것이 딸려 들어갈 여지가 없다.
`POST /daily-notes…` 응답은 프래그먼트만 오므로 `hx-select` 없이 그대로 교체된다(확인함) — 두 경로가 섞이지 않는다.

**부수 효과(신규 아님, 그대로 둠)**: `hx-select`로 `#recordsPane`만 갈아끼우므로 페이지의 나머지는 안 바뀐다.
`.hist-switch`의 `한 일 기록 · N` 카운터가 그렇다 — 다만 이 값은 그 달/검색과 무관한 전체 건수라 검색으로는 애초에 안 변하고,
추가/삭제 시 안 맞는 것은 `POST` 재렌더도 프래그먼트만 주던 **기존부터의 동작**이다.

---

## 4. 회귀 확인 (검증 항목 ③) — 이상 없음

임시 DB로 실제 기동해(`~/.weekly-report/data/db`는 건드리지 않음) 확인:

| 대상 | 결과 |
|---|---|
| 대시보드 `/` 카드 렌더 | `id="dashDaily"` 1개, 행 정상 |
| 대시보드 기록 추가 `POST ?view=dashboard` | 200 + `dashboardCard` 프래그먼트 |
| 대시보드 기록 삭제 URL | `/daily-notes/{id}/delete?view=dashboard&week=2026-08-14&month=&q=` → 200 + `dashboardCard` |
| 작성 `/entry` 좌측 패널 | `id="weekPanel"` 1개, 삭제 URL `…?view=entry&week=…&month=&q=` 정상 |
| 인라인 수정 `POST /daily-notes/{id}` | 200 · **body 길이 0**(noop) — `q` 안 받는 설계대로 |
| 히스토리 `view=reports` | 200, 기록 패널 미렌더 |
| 검색 없는 `view=records` | `stats` 3칸 + `quick-add` 정상 |

---

## 5. 추가로 확인한 경계 케이스 (검증 항목 ⑤ — frontend 시나리오 외)

| 케이스 | 결과 |
|---|---|
| `q=%` (LIKE 와일드카드) | **문제 없음.** `100% 완료 보고` 1건만 매칭 — Spring Data `Containing`이 `%`/`_`를 이스케이프해 리터럴로 나간다 |
| `q=_` | `AB_CD 테이블 정리` 1건만 매칭(`ABxCD…`는 안 걸림) — 이스케이프 확인 |
| `q=AB_CD` | 1건, 리터럴 매칭 |
| `q="><script>alert(1)</script>` | `value="&quot;&gt;&lt;script&gt;…"`, 범위 문구도 이스케이프 — XSS 없음 |
| `q=` (빈 값) / `q=   ` (반각 공백만) | `recordSearching=false`, 그 달 전체 복귀 |
| `q=　` (전각 공백만) | **버그였음 → 수정 §2-2** |
| `q=GTPP ` (뒤 공백) | **버그였음 → 수정 §2-1** |
| 다른 달로 이동 후 검색어 지우기 | 2026-07 + `q=GTPP` → X 버튼/링크 모두 `/history?view=records&month=2026-07`(q 없음) → **7월에 머문 채** 전체 복귀. 달이 8월로 튀지 않음 |
| 기록 없는 미래 달(2026-09) + 검색어 | 이전 달 링크는 `q` 달고 활성, 다음 달은 `pager-disabled` — 페이저 범위가 검색과 무관함(설계대로) |
| 검색 중 인라인 수정으로 텍스트가 검색어와 불일치 | 응답이 noop이라 **그 행은 화면에 남는다**. 다음 재렌더(추가/삭제/재검색/월 이동) 때 목록에서 빠지고 통계도 다시 계산됨(실측: `2건`→`1건`). 데이터 손실·에러 없음 |

### 남겨두는 관찰(수정 안 함)

1. **검색 중 인라인 수정 시 `.rec-scope`의 건수/시간이 즉시 갱신되지 않는다.**
   `app.js`는 날짜 헤더 합계(`.day-sum`)와 대시보드 칩만 클라이언트에서 다시 계산하고 `.stats`/`.rec-scope`는 손대지 않는다.
   이건 **검색 도입 전부터 `.stats` 타일이 갖고 있던 성질**이라 이번 회귀가 아니고, 고치려면 app.js에 새 계산 경로가 생겨
   "인라인 수정은 저장만 한다"는 규약의 예외를 늘리게 되어 그대로 뒀다.
2. **`hx-push-url`이 없어 검색 상태가 주소창에 안 남는다.** 새로고침하면 검색이 풀린다(월 이동은 링크라 남는다).
   기존 htmx 배선 어디에도 `hx-push-url`이 없어 일관되고, 로컬 단일 사용자 앱이라 실해가 없다고 판단.
3. **`hx-sync`가 없어 이론상 응답 역전이 가능하다.** vendored htmx 2.0.4 소스 확인 결과 `hx-sync` 속성이 없으면
   동일 요소의 요청이 겹칠 수 있고 나중에 도착한 응답이 이긴다. 다만 300ms 디바운스 + 로컬 + 한 달 최대 ~60건이라
   실측으로는 재현되지 않았다. 굳이 굳힌다면 `hx-sync="this:replace"` 한 줄이지만, frontend가 브라우저로 검증을 끝낸
   배선을 재검증 없이 바꾸는 쪽이 위험이 커서 손대지 않았다.

---

## 6. 테스트 보강

`./gradlew test` — **67 tests, 전부 통과** (기존 54 + 13).

| 파일 | 추가/변경 |
|---|---|
| `src/test/java/com/weeklyreport/service/DailyNoteServiceTest.java` | +3 — `normalizeQuery` 규칙(전각 공백 포함), 검색어 없을 때 월 전체 쿼리로 떨어지는지, 검색이 **그 달 범위 안에서만** 걸리는지 |
| `src/test/java/com/weeklyreport/web/DailyNoteControllerViewTest.java` | +6 — 검색/비검색 모델 계약, 공백만 친 검색어, 통계 재계산, **월 페이저 범위는 검색과 무관**, 입력칸은 원문 echo, 추가/삭제가 `q`를 들고 다시 그리는지 |
| `src/test/java/com/weeklyreport/web/HistoryControllerTest.java` | **신규 4** — `/history`의 `q` 전달, 빈 `q` 처리, 뷰 분기, month 기본값 (알려진 커버리지 공백이었던 `HistoryController` 첫 테스트) |

`DailyNoteControllerViewTest`의 기존 `findByMonth(any())` **1-인자 스텁이 죽어 있던 것도 고쳤다** —
컨트롤러가 2-인자 오버로드를 부르게 바뀌었는데 스텁은 1-인자 그대로라, Mockito 기본값(빈 리스트) 덕에
테스트가 "통과는 하지만 아무것도 검증하지 않는" 상태였다. 2-인자 스텁으로 교체.

새 테스트 2개는 §2의 버그를 실제로 잡는다(수정 전 코드에서는 실패한다):
`입력칸에_돌려주는_검색어는_정규화_전_원문이다`, `공백만_친_검색어는_검색으로_치지_않는다`.

---

## 7. 변경한 파일

| 파일 | 변경 |
|---|---|
| `src/main/java/com/weeklyreport/service/DailyNoteService.java` | `normalizeQuery`: `trim()` → `strip()` + 이유 주석 |
| `src/main/java/com/weeklyreport/web/DailyNoteController.java` | `recordQueryRaw` 모델 속성 추가 + 이유 주석 |
| `src/main/resources/templates/fragments-daily.html` | 검색 input `th:value` → `${recordQueryRaw}` + 이유 주석 |
| `src/test/java/com/weeklyreport/service/DailyNoteServiceTest.java` | 테스트 +3 |
| `src/test/java/com/weeklyreport/web/DailyNoteControllerViewTest.java` | 스텁 수정 + 테스트 +6 |
| `src/test/java/com/weeklyreport/web/HistoryControllerTest.java` | 신규 |

---

## 8. docs-sync에게

1. **`recordQueryRaw`가 모델 계약에 추가됐다** — 입력칸 value 전용, 나머지는 `recordQuery`(정규화). backend_summary §5 표에 반영 필요.
2. **`hx-select`가 이 프로젝트 htmx 배선 규약에 처음 들어왔다**(frontend도 이미 지적) — CLAUDE.md의 "htmx 배선 규약"과
   `skills/thymeleaf-htmx-dev`에 한 줄 필요. 표현은 *"스왑 단위는 여전히 블록 통째 `outerHTML`이고, `hx-select`는
   페이지 전체를 주는 GET 라우트에서 그 블록만 떼어내는 응답측 필터"* 정도가 정확하다.
3. **"검색 중에는 새 기록을 추가할 수 없다"**는 사용자에게 보이는 정책이므로 기록 기능 문서에 남길 가치가 있다.

## 9. 미검증으로 남긴 것

- 한글 IME 조합 중(`ㄱ`→`겨`→`결`) 스왑이 일어날 때의 조합 버퍼 거동 — curl로는 재현 불가, 실브라우저+IME가 필요하다.
  frontend가 `q=결제`로 완성형 검색은 확인했으나 **조합 중 300ms 정지**는 양쪽 다 검증하지 않았다. 추측으로 단정하지 않고 남겨둔다.
- 시각적 레이아웃(`.rec-scope`/`.rec-addlock` 렌더 모양) — frontend가 Playwright로 확인 완료라 중복하지 않았다.
