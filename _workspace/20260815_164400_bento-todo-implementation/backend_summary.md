# backend_summary — TODO 리스트 + 대시보드 벤토 데이터 (2026-08-15)

`./gradlew compileJava compileTestJava` 통과, `./gradlew test` 전부 통과.
`.md` 내보내기 포맷은 **변경 없음**(TodoItem은 `MdExportService`를 지나가지 않는다) → docs-sync가 스키마 문서를 고칠 일 없음.

## 1. 새 파일

| 경로 | 역할 |
|---|---|
| `src/main/java/com/weeklyreport/domain/enums/TodoPriority.java` | 우선순위 3단계 enum |
| `src/main/java/com/weeklyreport/domain/TodoItem.java` | 엔티티 |
| `src/main/java/com/weeklyreport/repository/TodoItemRepository.java` | `findByDoneFalse()` / `findByDoneTrue()` 둘뿐 |
| `src/main/java/com/weeklyreport/service/TodoItemService.java` | 정렬·그룹핑·CRUD |
| `src/main/java/com/weeklyreport/web/dto/TodoItemForm.java` | 폼 바인딩(`dueDate`/`text`/`priority`) |
| `src/main/java/com/weeklyreport/web/TodoItemController.java` | 라우트 6개 + `populateTodoCard()` |

수정: `web/DashboardController.java`, `src/test/java/.../DashboardControllerTest.java`(생성자 인자 추가).

## 2. 엔티티 `TodoItem`

| 필드 | 타입 | 컬럼 | 비고 |
|---|---|---|---|
| `id` | Long | `id` | IDENTITY |
| `dueDate` | LocalDate | `due_date` | 필수. 비우고 추가하면 서비스가 오늘로 채움. 인덱스 |
| `text` | String(1000) | **`todo_text`** | H2에서 `TEXT`가 타입명이라 회피(`DailyNote`와 동일) |
| `done` | boolean | `done` | 기본 false |
| `priority` | `TodoPriority` | `priority` | `@Enumerated(STRING)`, 기본 `MID` |
| `createdAt` | LocalDateTime | `created_at` | 생성 시각 |

메서드: `isOverdue(LocalDate today)`(= `!done && dueDate < today`), `dueShortLabel()`(`"08.10"` — 기한 지남 행의 날짜 칩).

`TodoPriority`: `HIGH("높음")` / `MID("보통")` / `LOW("낮음")` — `label()`, `order()`, `next()`(HIGH→MID→LOW→HIGH), `orDefault(p)`.

- `DailyNote`와 같은 성격: **FK 0개**, `.md` 미포함, 주(week)에 속하지 않음 → id 기준 equals/hashCode 오버라이드 불필요(연관관계가 없어서).
- 자동 이월 없음 — 기한이 지나도 날짜를 옮기지 않고 "기한 지남" 그룹으로 표시만 강조.
- H2 예약어 충돌: `done`/`priority`/`due_date`는 H2 키워드가 아니라 그대로 씀. `text`만 회피.

## 3. 정렬(전부 Java에서 — SQL `ORDER BY` 아님)

`@Enumerated(STRING)`이라 `ORDER BY priority`는 HIGH→LOW→MID(문자열 순)로 나온다. 그래서 `TodoItemService`가 비교자로 정렬한다.

- 미완료: `dueDate` ASC → `priority.order()`(HIGH 0, MID 1, LOW 2) → `id` ASC. 기한 지난 것이 자연히 맨 위.
- 완료: `dueDate` DESC → `id` DESC.

## 4. 서비스 공개 API — `TodoItemService`

| 시그니처 | 설명 |
|---|---|
| `List<TodoItem> findOpen()` | 미완료 전체(정렬 완료) |
| `List<TodoItem> findDone()` | 완료 전체(정렬 완료) |
| `List<TodoItem> overdue(List<TodoItem> open, LocalDate today)` | 기한 지남 |
| `List<TodoItem> upcoming(List<TodoItem> open, LocalDate today)` | 오늘 이후(오늘 포함) |
| `int dueTodayCount(List<TodoItem> open, LocalDate today)` | 오늘 마감 건수 |
| `List<TodoGroup> groupByDueDate(List<TodoItem> todos, int startIndex)` | 기한 날짜별 그룹 |
| `Optional<TodoItem> add(LocalDate dueDate, String text, TodoPriority priority)` | 빈 텍스트면 미생성, 기한 null이면 오늘, 우선순위 null이면 MID |
| `void updateText(Long id, String text)` | null이면 무시 |
| `void toggleDone(Long id)` | 서버가 현재 값 반전 |
| `void cyclePriority(Long id)` | 다음 단계로 |
| `void updateDueDate(Long id, LocalDate dueDate)` | null이면 무시 |
| `void delete(Long id)` | |

`record TodoGroup(LocalDate date, List<TodoItem> todos, int startIndex)` — `count()`, `label()`(`"08.17 (월)"`), `isToday()`.
`startIndex` = **미완료 전체 목록에서 그 그룹 첫 항목의 0-based 순번**(기한 지남 그룹이 앞에 오므로 그 건수부터 시작).

## 5. 라우트 (`TodoItemController`) — 전부 POST, `week` 파라미터 없음

| 메서드 · 경로 | 파라미터 | 반환 | htmx |
|---|---|---|---|
| `POST /todos` | `dueDate`(ISO, 선택) · `text` · `priority`(`HIGH`/`MID`/`LOW`, 선택) | `fragments-todo :: todoCard` | `hx-swap="outerHTML"` |
| `POST /todos/{id}` | `text` | `fragments-entry :: noop` | `hx-swap="none"` (포커스 유지) |
| `POST /todos/{id}/done` | 없음 | `fragments-todo :: todoCard` | outerHTML |
| `POST /todos/{id}/priority` | 없음 | `fragments-todo :: todoCard` | outerHTML |
| `POST /todos/{id}/due` | `dueDate`(ISO) | `fragments-todo :: todoCard` | outerHTML |
| `POST /todos/{id}/delete` | 없음 | `fragments-todo :: todoCard` | outerHTML |

- `done`/`priority`는 **값을 받지 않는다** — 체크 해제된 체크박스는 전송되지 않아 값을 믿을 수 없어서(서버가 반전/순환).
- **frontend가 `templates/fragments-todo.html`에 `th:fragment="todoCard"`를 만들어야 한다**(아직 없음).
- 접힘/펼침(`+N건 더 보기`, `완료 N건 보기`)은 **서버 왕복 없음** — 전체를 내려주고 app.js가 감춘다(상태 유지는 localStorage 권장, split 패널 선례).

## 6. 모델 속성 — TODO 카드 (`TodoItemController.populateTodoCard()`)

| 속성 | 타입 | 내용 |
|---|---|---|
| `today` | LocalDate | 오늘 |
| `todoOverdue` | List\<TodoItem\> | 기한 지남(미완료) |
| `todoOverdueCount` | int | 그 건수 = 지표 타일 "기한 지난 할 일" 값 |
| `todoDayGroups` | List\<TodoGroup\> | 오늘 이후 기한의 날짜별 그룹 |
| `todoOpenCount` | int | 미완료 전체(카드 제목 옆 "N건") |
| `todoDueTodayCount` | int | 오늘 마감(지표 타일 sub "오늘 마감 N건") |
| `todoDone` | List\<TodoItem\> | 완료 목록 |
| `todoDoneCount` | int | 완료 건수 |
| `todoVisibleLimit` | int | 8 (`TODO_VISIBLE`) |
| `todoHiddenCount` | int | `max(0, openCount - 8)` → "+N건 더 보기" |
| `todoDefaultDue` | LocalDate | 추가줄 기한 초기값(오늘) |
| `todoDefaultPriority` | TodoPriority | MID |
| `todoPriorities` | TodoPriority[] | 순환 순서 그대로(라벨 하드코딩 방지) |

접힘 상태 렌더링: 행의 전역 순번이 `todoVisibleLimit` 이상이면 감춘다. 그룹 단위 판단은 `group.startIndex >= todoVisibleLimit`.
⚠️ "기한 지남" 헤더의 건수는 **접힘 여부와 무관하게 전체 기한지남 건수**다(목업 JS는 잘린 목록 기준이었지만, 지표 타일 숫자와 어긋나면 안 되므로 전체 기준으로 통일).

## 7. 대시보드 모델 변경 (`DashboardController`)

**추가**

| 속성 | 타입 | 용도 |
|---|---|---|
| `heroDays` | List\<HeroDay\> | hero 요일 스트립 7칸(금~목) |
| `heroLoggedDays` | int | "이번 주 기록 N일" |
| `heroWeekHoursDisplay` | String | 그 주 기록 시간(0이면 빈 문자열) |
| `activeProjectAvgProgress` | int | 지표 타일 sub "평균 진행률 N%" |
| (TODO 12종) | | 위 6절 전부 |

`record HeroDay(LocalDate date, String dow, String hoursDisplay, int barPercent, boolean today)` + `isEmpty()`.
`barPercent`: 0h→0(막대 미표시), 그 외 8h=100% 기준에 최소 8% 보장.

**제거**

| 속성 | 사유 |
|---|---|
| `avgWeeks` | "최근 2주 평균 맨위크" 상세 타일이 빠져 모집단 목록이 필요 없음 |
| `activeProjects` | "진행중인 프로젝트" 상세 목록 타일이 빠짐(건수+평균진행률만 남김) |

**유지**: `activeTab`, `period`, `week`, `lowThreshold`, `report`, `heroTotalHours`, `heroManWeek`, `heroItemCount`, `avgManWeek`, `avgWeekCount`, `activeProjectCount`, `recentReports`, `pastReportCount`, 일일 기록 카드 속성 전부(`todayNotes`/`todayNoteCount`/`todayHoursDisplay`/`recentDayGroups`/`weekNoteCount`/`weekHoursDisplay`).

- `EntryService.activeProjectsWithProgress()`는 **손대지 않았다**(작성 탭과 공유). 대시보드는 그 결과의 size와 completion 평균만 쓴다.
- `EntryService.recentSubmittedReports()`/`averageManWeek()`도 그대로 — `avgManWeek` 계산에 여전히 쓰인다.
- ⚠️ 현재 `templates/dashboard.html`은 아직 `avgWeeks`/`activeProjects`를 참조한다(Thymeleaf가 null을 조용히 빈 목록으로 처리해 500은 안 나지만) — frontend가 벤토로 다시 짜면서 정리해야 한다.
- ⚠️ hero 요일 스트립은 기록 추가/삭제 시 **htmx로 갱신되지 않는다**(오늘 한 일 카드만 교체됨). 대시보드 "이번 주 기록 N건"과 같은 성격의 의도된 절충.

## 8. 미결/확인 필요

- CLAUDE.md "다음에 할 일"의 세 미결(`.md` v1 하위호환, dev 그룹 티켓 필수, `Project.active` 집계)은 이번 작업과 무관 — 건드리지 않았다.
- TodoItem 전용 테스트는 작성하지 않았다(qa 담당). 커버가 필요한 지점: 정렬 비교자 2종, `add()`의 빈 텍스트/기한 null 처리, `toggleDone`/`cyclePriority` 순환, `groupByDueDate`의 `startIndex` 누적, `populateTodoCard`의 overdue/upcoming 분리.
- 테스트 힌트: `new TodoItemService(todoItemRepository)`, `new TodoItemController(todoItemService)`,
  `new DashboardController(entryService, dailyNoteService, todoItemService, manWeekService, weeklyReportRepository)`(인자 순서 주의 — TodoItemService가 3번째).
