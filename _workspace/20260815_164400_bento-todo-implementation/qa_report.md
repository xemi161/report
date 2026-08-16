# qa_report — 벤토 대시보드 + TODO 리스트 교차검증 (2026-08-16)

`./gradlew test --rerun-tasks` **BUILD SUCCESSFUL — 88 tests / 0 failures / 0 errors**
(기존 54건 + 이번 라운드 신규 34건: `TodoItemServiceTest` 16, `TodoItemControllerTest` 9, `DashboardControllerTest` +5)

**결론: 경계면 불일치(런타임에만 터지는 종류) 0건.** 라우트·모델 속성·엔티티 메서드·htmx 배선·목업 배치가 전부 일치한다.
발견한 것은 전부 죽은 코드/문서 드리프트 급이고, 그중 2건은 이 리포트에서 직접 고쳤다.

---

## 1. 라우트 일치 — PASS

`TodoItemController` 6개 ↔ `fragments-todo.html`의 `th:hx-post` 6종. 경로 변수 포함 전수 대조.

| 컨트롤러 | 템플릿 | target / swap | 결과 |
|---|---|---|---|
| `POST /todos` (`:50`) | `fragments-todo.html:97` `@{/todos}` | `#todoCard` / `outerHTML` | ✅ |
| `POST /todos/{id}` (`:57`) | `:46` `@{'/todos/' + ${todo.id}}` | — / **`none`** | ✅ |
| `POST /todos/{id}/done` (`:67`) | `:38` | `#todoCard` / `outerHTML` | ✅ |
| `POST /todos/{id}/priority` (`:74`) | `:53` | `#todoCard` / `outerHTML` | ✅ |
| `POST /todos/{id}/due` (`:81`) | `:70` | `#todoCard` / `outerHTML` | ✅ |
| `POST /todos/{id}/delete` (`:90`) | `:62` | `#todoCard` / `outerHTML` | ✅ |

- `/todos/{id}`와 `/todos/{id}/done` 등은 경로 깊이가 달라 Spring 매핑 충돌 없음.
- `POST /todos/{id}`가 돌려주는 `fragments-entry :: noop`은 실제로 존재한다(`fragments-entry.html:15`).
- **`week` 파라미터는 6개 라우트·6개 `hx-post` 어디에도 없다** — 규약대로. (`fragments-todo.html` 전체에 `week` 문자열 0건)
- `TodoItemControllerTest`가 프래그먼트 이름 6종을 문자열째 고정했다(리네임 시 컴파일 대신 테스트가 깨진다).

## 2. 모델 속성 일치 — PASS

`populateTodoCard()`가 넣는 **13개**(backend_summary는 "12종"이라고 적었지만 `today`까지 13개)를 템플릿 참조와 전수 대조.
`dashboard.html`·`fragments-todo.html`의 `${...}`/`#{...}` 루트 식별자를 스크립트로 뽑아 모델과 맞춰봤고, 미해결 이름 **0건**.

- 대시보드 참조: `activeProjectAvgProgress` `activeProjectCount` `heroDays` `heroItemCount` `heroLoggedDays` `heroManWeek` `heroTotalHours` `heroWeekHoursDisplay` `lowThreshold` `pastReportCount` `period` `recentReports` `report` `todoDueTodayCount` `todoOverdueCount` `week` — 전부 공급됨.
- TODO 카드 참조: `todoDayGroups` `todoDefaultDue` `todoDefaultPriority` `todoDone` `todoDoneCount` `todoDueTodayCount` `todoHiddenCount` `todoOpenCount` `todoOverdue` `todoOverdueCount` `todoPriorities` `todoVisibleLimit` — 전부 공급됨.
- `dashboard.html`에 `avgWeeks`/`activeProjects`/`avgManWeek` 참조는 **0건**(frontend 정리 완료 확인).
- 프래그먼트 시그니처도 확인: `fragments :: header(activeTab, period, prevWeek, nextWeek)`(4인자 호출 ✅), `fragments :: reportCard(r, lowThreshold)`(2인자 ✅), `fragments-todo :: todoRow(todo, overdue, collapsed)`(3인자 ✅).

## 3. 엔티티/서비스 메서드 일치 — PASS

템플릿이 부르는 이름이 전부 실존 시그니처다: `todo.dueShortLabel()` · `todo.priority.name()/.label()` · `todo.done`(→`isDone()`) · `g.label()/.isToday()/.startIndex()/.todos()`(record 접근자) · `d.date()/.dow()/.hoursDisplay()/.barPercent()/.today()/.isEmpty()`(`HeroDay`) · `period.label()/.weekStart()/.weekEnd()`(`WeekPeriod` record) · `heroManWeek.compareTo(lowThreshold)`.

정렬 비교자는 **Java 쪽이 맞다**(회귀 테스트로 고정):
- `OPEN_ORDER` = dueDate ASC → `priority.order()` ASC → id ASC ✅
- `DONE_ORDER` = dueDate DESC → id DESC ✅
  `Comparator.comparing(A).reversed().thenComparing(B)` 형태라 흔한 "reversed()가 뒤 비교자까지 뒤집는" 함정(`.thenComparing(B).reversed()`)에 걸리지 않았다. 확인함.
- `@Enumerated(STRING)`이라 SQL `ORDER BY priority`는 HIGH→LOW→MID가 된다는 점을 `우선순위_정렬은_문자열_순서가_아니라_선언_순서를_따른다` 테스트로 못 박았다 — 나중에 "레포지토리에서 정렬하면 되지 않나"라며 비교자를 지우면 이 테스트가 깨진다.

## 4. htmx 배선 규약 — PASS

- 구조 변경 5개(추가·완료토글·우선순위·기한·삭제) 전부 `hx-target="#todoCard" hx-swap="outerHTML"` ✅
- 텍스트 인라인 수정만 `hx-trigger="change" hx-swap="none"` ✅
- `week` 미첨부 ✅
- 프래그먼트 루트에 벤토 배치 클래스(`class="card tile todo-card t-todo" id="todoCard"`)가 붙어 있다 — `outerHTML`로 갈아끼우므로 대시보드 쪽에서 감싸면 교체 순간 사라진다. 규약대로 루트에 있음 ✅
- `th:replace`를 `th:each`와 같은 태그에 걸지 않고 바깥 `<th:block th:each>` + 안쪽 `<div th:replace>`로 나눴다(`:124-126`, `:130-140`, `:162-164`) — `EL1007E` 함정 회피 ✅
- 행이 `<form>`이 아니라 `<div>`이고 컨트롤마다 자기 `hx-post`를 갖는다. htmx는 트리거 요소가 `name`을 가진 입력이면 자기 값을 실어 보내므로 `text`(textarea)·`dueDate`(date input)는 전달되고, `done`/`priority`는 값 없이 서버가 반전·순환한다 ✅

## 5. 목업 대비 — PASS (의도된 차이 2건은 문서화됨)

- **벤토 배치 순서**: `dashboard.html` = hero(`t-hero`) → 지표 4(`t-metric`) → `fragments-daily::dashboardCard`(`t-daily`) → `fragments-todo::todoCard`(`t-todo`) → `.bento-foot > .t-hist`. 목업 `weekly-report-mockup.html:2107-2127`과 동일 ✅
- **span 값**: `t-hero 6×4` / `t-metric 3×2` / `t-daily 8×6` / `t-todo 4×6` — `app.css:360-363`과 목업 `:289-292` 완전 일치. 반응형 1180/700px 브레이크포인트도 동일 ✅
- **정렬 규칙** 미완료/완료 모두 목업 `:1855-1860`과 동일 ✅
- **기한지남 그룹 분리 / TODO_VISIBLE=8 / 완료는 삭제 아닌 접힘** 전부 반영 ✅

의도된 차이(둘 다 backend가 근거를 남겨둠, 재확인함):
1. "기한 지남" 헤더 건수 — 목업은 **잘린 목록 기준**(`overdue.length`), 앱은 **전체 기한지남 건수**(`todoOverdueCount`). 지표 타일 숫자와 어긋나면 안 되므로 앱 쪽이 맞다.
2. 접힘 구현 — 목업은 서버(JS) 슬라이스, 앱은 전량 렌더 + CSS 감춤(`app.css:526-528`). "+N건 더 보기"에 서버 왕복을 만들지 않기 위함.

접힘 경계 계산을 손으로 다 밟아봤는데 **헤더만 남고 행이 전부 사라지는 고아 그룹은 발생하지 않는다** — 그룹의 첫 행 전역 순번 == `startIndex`이므로, 그룹이 보이면(`startIndex < 8`) 최소 한 행은 반드시 보인다. `todoHiddenCount = max(0, open-8)`도 실제 감춰지는 행 수와 정확히 같다(테스트로 고정).

## 6. `.md` 내보내기 무관 — PASS

`MdExportService`에 `TodoItem`/`TodoPriority`/`todo` **0건**. `WeeklyReport`/`ReportItem`/`Project`/`DailyNote` 어디에도 참조 없음.
`TodoItem`은 FK 0개·주(week) 무관·`.md` 미포함이라는 `DailyNote`와 같은 성격을 지켰다. `MdExportServiceTest` 4건 전부 통과(포맷 변화 없음) → **docs-sync가 `docs/weekly-report-md-schema.md`를 고칠 일 없음.**

## 7. 죽은 코드 — `avgManWeek`/`avgWeekCount`

**판단: 확정적으로 죽은 코드다. qa가 직접 정리했다.**

근거 3가지가 한 방향을 가리켰다.
1. `dashboard.html` 어디에도 참조 없음(§2의 전수 추출로 확인).
2. 다른 화면과 충돌 없음 — 작성 탭의 동명 `avgManWeek`는 `EntryController:253`이 `recentAverageManWeek()`(4주)로 따로 채우는 **별개 지표**다.
3. 승인된 설계가 그렇게 지시했다 — `design_summary_v3.md:87`이 목업 쪽 대응물인 `RECENT_WEEKS_FOR_AVG` 상수를 "유일 사용처였음"이라며 삭제하라고 적어뒀는데, 앱 쪽만 남아 있었다.

게다가 단순 미사용이 아니라 **비용이 있었다** — `populateManWeekAverage()`가 `recentSubmittedReports()`를 통해 제출본 전체를 조회하는데, 같은 화면의 `populatePastReports()`가 `findByStatusOrderByWeekStartDesc(SUBMITTED)`를 이미 부른다. 대시보드 진입마다 같은 테이블을 두 번 훑고 결과를 버리고 있었다.

**적용한 수정** — `web/DashboardController.java`
- `RECENT_WEEKS_FOR_AVERAGE` 상수 삭제
- `populateManWeekAverage(Model, LocalDate)` 메서드 삭제
- `dashboard()`의 호출 1줄 삭제
- 클래스 javadoc에 "평균 맨위크는 아예 계산하지 않는다"와 그 이유를 명시(타일을 되살릴 때 함께 되살려야 한다는 단서 포함)

`EntryService.averageManWeek(List)` / `recentSubmittedReports(int, LocalDate)`는 **건드리지 않았다** — `recentAverageManWeek(int)`가 계속 부르고 있고, 그 오버로드 자체가 호출부 없이 남아 있는 건 이번 라운드 이전부터의 상태라 backend 소관이다(§9 참고).

회귀 방지: `상세_타일이_빠진_뒤로는_모집단_목록도_평균_맨위크도_내려보내지_않는다` — 모델에 4개 키가 없음 + `recentSubmittedReports`/`averageManWeek`가 **호출되지 않음**까지 검증한다.

## 8. frontend가 실측으로 고친 버그 2개 — 코드 리뷰 확인 완료

1. **htmx `attributesToSettle`로 인한 `.expanded` 롤백** — `app.js:377-379`에 `htmx:afterSettle` 리스너가 있고 `event.target.id === "todoCard"`일 때 `applyTodoState()`를 다시 부른다. `afterSwap`(`:352-355`)과 이중으로 걸어 라벨은 즉시, 클래스는 settle 후에 맞춘다. `:369-376` 주석에 원인(class는 settle 대상, `data-*`는 아님)까지 남아 있다. **정합함.**
2. **접힌 textarea의 `scrollHeight` 0** — `autoGrow()`(`:80-84`)가 `height > 0 ? height + "px" : ""`로 0일 때 인라인 높이를 **지운다**(`0px`으로 굳지 않는다). `applyTodoState()`(`:157-165`)는 `classList.toggle` **뒤에** `autoGrowAll(card)`를 부른다 — 순서가 중요한데(보이게 된 다음에 재야 한다) 맞게 돼 있다. **정합함.**

⚠️ 한계 명시: 둘 다 **코드 리뷰로만** 확인했다. 이 환경에선 브라우저를 띄울 수 없어 런타임 재현은 frontend의 헤드리스 크롬 실측에 의존한다. 특히 `outerHTML` 스왑에서 `htmx:afterSwap`/`afterSettle`의 `event.target`이 새 `#todoCard`로 잡힌다는 전제는 frontend 실측 결과이고, 이 전제가 틀리면 지표 타일 동기화와 `.expanded` 복원이 **둘 다** 조용히 죽는다(에러 없이 동작만 안 함). 회귀가 의심되면 이 지점부터 보면 된다.

---

## 9. 남겨둔 것 (수정 안 함 — 보고만)

| # | 위치 | 내용 | 판단 |
|---|---|---|---|
| A | `domain/TodoItem.java:107` | **`isOverdue(LocalDate)`에 호출부가 없다.** `TodoItemService.overdue()/upcoming()`(`:69`, `:74`)이 같은 조건을 인라인으로 다시 쓴다. 게다가 null 처리가 다르다 — 엔티티는 `dueDate == null`을 방어하지만 서비스는 `t.getDueDate().isBefore(today)`로 NPE를 낸다 | **backend 판단 사항.** 서비스가 `t.isOverdue(today)`를 쓰게 하면 중복이 사라지지만, null dueDate가 NPE 대신 "오늘 이후" 그룹으로 조용히 섞이는 쪽으로 실패 양상이 바뀐다. 어느 쪽이 나은지는 설계 선택이라 qa가 임의로 바꾸지 않았다. 대신 두 판정이 같은 답을 내는지 테스트로 고정해뒀다(`엔티티의_기한지남_판정은_미완료일_때만_참이다`) |
| B | `web/TodoItemController.java:118` | `model.addAttribute("today", today)`를 넣지만 `fragments-todo.html`은 `today`를 읽지 않는다(같은 페이지의 `fragments-daily`가 자기 몫으로 따로 넣는다) | 무해한 중복. 값도 동일하다. 카드가 나중에 "오늘" 표시를 쓸 여지가 있어 두는 편이 낫다고 봄 |
| C | `service/TodoItemService.java:36` | `dueDate`/`priority`가 DB에서 null로 올라오면 정렬 비교자·그룹핑·템플릿(`todo.priority.label()`)이 NPE. 현재 코드 경로로는 null이 생길 수 없다(`add()`가 오늘로 채우고, `updateDueDate(null)`은 무시, `orDefault`가 MID 보장) | 실사용 위험 없음. 신규 테이블이라 레거시 행도 없다. 나중에 수기 SQL이나 임포트 경로가 생기면 다시 볼 것 |
| D | `EntryService.java:263` | `recentAverageManWeek(int)`가 이번 정리 이후 호출부 0이 됐다(원래도 `DashboardController`가 부르던 건 `averageManWeek(List)` 쪽이라 이 오버로드는 이전부터 미사용) | backend 소관. 서비스 공개 API 삭제라 qa가 손대지 않음 |

## 10. 문서 드리프트 (docs-sync 참고)

- **[qa가 고침]** `DashboardController` 클래스 javadoc이 TODO 타일을 `4×10`이라고 적고 있었다 → `4×6`으로 정정. 실제 CSS(`app.css:362`)와 목업(`:292`)은 둘 다 `grid-row: span 6`이다. `4×10`은 목업 탐색 중간 단계(`:1825` 주석의 "4×10 = 746px")에서 흘러들어온 값으로 보인다.
- `backend_summary.md` §6이 TODO 카드 모델을 "12종"으로 세는데 실제로는 `today` 포함 **13개**다(§2).
- `CLAUDE.md`의 "현재 화면 구조"는 아직 대시보드를 "이번 주 hero + 최근 2주 평균 맨위크 + 진행중인 프로젝트 + 과거 제출본 5건 + 오늘 한 일"로 서술한다 — **벤토 개편으로 큰 타일 2개가 빠지고 TODO 리스트가 추가된 것이 미반영**이다. `Project.active` 집계 규칙 문단도 "대시보드와 작성 탭이 같은 건수"라는 핵심은 유효하지만, 대시보드 쪽이 이제 목록이 아니라 건수+평균 진행률 타일이라는 점은 갱신이 필요하다.
- `CLAUDE.md`에 **TodoItem 관련 서술이 아직 전혀 없다** — `DailyNote`와 같은 급의 "주간보고와 완전히 분리된 데이터"이고, 정렬 방향이 미완료/완료가 반대인 것·접힘이 서버 왕복 없이 CSS로만 되는 것·`week`가 붙지 않는 것이 전부 "버그로 잡지 말 것" 부류라 등재 가치가 있다.

## 11. 추가한 테스트

| 파일 | 건수 | 무엇을 고정했나 |
|---|---|---|
| `src/test/java/com/weeklyreport/service/TodoItemServiceTest.java` | 16 | 미완료/완료 정렬(문자열 정렬 회귀 포함), 기한지남·오늘이후 경계(오늘은 "지남"이 아니다), 오늘 마감 건수, `groupByDueDate`의 `startIndex` 누적·라벨·순서 보존, `add()`의 빈 텍스트/기한 null/우선순위 null, `toggleDone` 반전, 우선순위 3단 순환, `updateDueDate(null)` 무시, `updateText(null)` 무시 + trim |
| `src/test/java/com/weeklyreport/web/TodoItemControllerTest.java` | 9 | 라우트 6종의 반환 프래그먼트 문자열, 텍스트 수정만 `noop`, 완료/우선순위가 값을 받지 않음, 폼 → 서비스 인자 전달, 모델 13개 속성명, 추가줄 기본값·`todoPriorities` 순서, 기한지남 분리와 `startIndex`, `todoHiddenCount` 경계(9건→1 / 3건→0), 완료본이 미완료 건수·overdue 건수에 안 섞임 |
| `src/test/java/com/weeklyreport/web/DashboardControllerTest.java` | +5 | hero 요일 스트립 7칸(금~목, 기록 0건이어도), `barPercent` 경계(0.5h→최소 8%, 12h→100% 상한, 0h→0), 진행중 프로젝트 평균 진행률 반올림·빈 목록 0, `avgManWeek`/`avgWeekCount`/`avgWeeks`/`activeProjects` 미노출 + 헛조회 없음, 대시보드 전체 모델 속성명 |

전부 기존 관례를 따랐다 — JUnit5 + AssertJ + Mockito, `@SpringBootTest`/`MockMvc` 없이 서비스는 `new TodoItemService(repo)`, 컨트롤러는 목 주입, private 메서드는 `ReflectionTestUtils.invokeMethod`, id 강제 세팅은 `ReflectionTestUtils.setField`, 메서드명은 한국어 전체 문장형.
