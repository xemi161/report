# backend 작업 요약 — 대시보드 재도입(3탭) + 일일 기록(DailyNote)

작업일 2026-08-14 · `./gradlew compileJava` / `./gradlew test` 통과 · Spring 컨텍스트 기동 검증 완료(임시 `@SpringBootTest`로 확인 후 삭제)

> **frontend가 템플릿을 쓰기 전에 이 문서의 §3~§5를 그대로 계약으로 삼으면 된다.** 추측할 것이 없도록 라우트·파라미터명·모델 속성명·필드명을 전부 적었다.
> **아직 템플릿이 없어 `GET /`는 500이 난다** — `dashboard.html`, `fragments-daily.html`이 frontend 작업 산출물이다.

---

## 1. 신규/변경 파일

| 파일 | 상태 |
|---|---|
| `src/main/java/com/weeklyreport/domain/DailyNote.java` | 신규 |
| `src/main/java/com/weeklyreport/repository/DailyNoteRepository.java` | 신규 |
| `src/main/java/com/weeklyreport/service/DailyNoteService.java` | 신규 (`DayGroup` 중첩 record 포함) |
| `src/main/java/com/weeklyreport/web/dto/DailyNoteForm.java` | 신규 |
| `src/main/java/com/weeklyreport/web/DashboardController.java` | 신규 (`ProjectProgress` 중첩 record 포함) |
| `src/main/java/com/weeklyreport/web/DailyNoteController.java` | 신규 |
| `src/main/java/com/weeklyreport/web/EntryController.java` | 변경 — `"/"` 매핑 제거, `DailyNoteService` 주입, 좌측 패널 모델 추가 |
| `src/main/java/com/weeklyreport/web/HistoryController.java` | 변경 — `view`/`month` 파라미터, `DailyNoteService` 주입 |
| `src/main/java/com/weeklyreport/web/LayoutAdvice.java` | 변경 — `assignableTypes`에 Dashboard/DailyNote 컨트롤러 추가 |
| `src/main/java/com/weeklyreport/service/EntryService.java` | 변경 — 메서드 2개 **추가만**(기존 4주 평균 메서드는 그대로) |
| `src/main/java/com/weeklyreport/repository/ReportItemRepository.java` | 변경 — 쿼리 1개 추가 |
| `src/test/java/com/weeklyreport/web/EntryControllerProjectCardTest.java` | 변경 — 생성자 인자 추가에 맞춰 mock 1개 추가 |

**손대지 않은 것**: `MdExportService`(일일 기록은 `.md`에 절대 안 나감), `ManWeekService`, `CarryOverService`, `WeekLabelService`, `EntryService`의 기존 로직 전부, `docs/weekly-report-md-schema.md`.

---

## 2. DailyNote 엔티티

| 필드 | 타입 | 비고 |
|---|---|---|
| `id` | `Long` | IDENTITY |
| `workDate` | `LocalDate` | 기준 날짜. **소속 주는 저장하지 않고 여기서 파생**(주 정의는 `WeekLabelService` 하나뿐) |
| `text` | `String` | **DB 컬럼명은 `note_text`** (`@Column(name="note_text", length=1000)`) — H2에서 `TEXT`는 데이터 타입 이름이라 회피. 자바 필드/게터는 그대로 `text`/`getText()` |
| `hours` | `BigDecimal` | **nullable = 정상**. `null`은 "0시간"이 아니라 "안 적음" |
| `createdAt` | `LocalDateTime` | 생성 시각(기본값 `now()`) |

- 표기 헬퍼 **`hoursDisplay()`** — `ReportItem.hoursDisplay()`와 같은 규칙(`2.00` → `2`, 미입력 → `""`). 템플릿에서 `th:value="${n.hoursDisplay()}"`로 쓸 것.
- **다른 엔티티와 FK/연관관계가 전혀 없다.** 연관이 없으므로 `equals`/`hashCode` 오버라이드도 하지 않았다(Project와 달리 Map/Set 키로 맞댈 일이 없음).
- 인덱스: `workDate`.
- `ddl-auto: update`라 기존 DB에 `daily_note` 테이블이 자동 생성된다(추가만, 기존 스키마 영향 없음).

### DailyNoteRepository

```java
List<DailyNote> findByWorkDateBetweenOrderByWorkDateAscIdAsc(from, to);   // 주 범위 — 오름차순
List<DailyNote> findByWorkDateBetweenOrderByWorkDateDescIdAsc(from, to);  // 월 범위 — 내림차순
Optional<DailyNote> findTopByOrderByWorkDateAsc();                        // 월 페이저 하한
Optional<DailyNote> findTopByOrderByWorkDateDesc();                       // 월 페이저 상한
```

> ⚠️ **주=오름차순 / 월=내림차순은 의도된 설계 차이다**(작성 패널은 한 주를 처음부터 읽는 자리, 기록 화면은 최근에서 거슬러 올라가는 자리). QA는 버그로 잡지 말 것.

---

## 3. 라우트

| 메서드 | 경로 | 요청 파라미터 | 반환 |
|---|---|---|---|
| GET | `/` | — | 뷰 `dashboard` |
| GET | `/entry` | `week` (선택, `yyyy-MM-dd`, 금요일) | 뷰 `entry` |
| GET | `/history` | `view` (선택: `reports`\|`records`), `month` (선택: `yyyy-MM`) | 뷰 `history` |
| POST | `/daily-notes` | 폼: `workDate`, `text`, `hours` / 쿼리: `view`, `week`, `month` | 화면별 프래그먼트(아래) |
| POST | `/daily-notes/{id}` | 폼: `text`, `hours` | `fragments-entry :: noop` |
| POST | `/daily-notes/{id}/delete` | 쿼리: `view`, `week`, `month` | 화면별 프래그먼트(아래) |

기존 라우트 변경 없음: `/entry/**`(항목·프로젝트 CRUD·preview·submit), `/export/{id}`, `/history/{id}`(리다이렉트), `/onboarding`.

### `view` 파라미터 ↔ 반환 프래그먼트

| `view` | 반환 | 함께 보내야 하는 파라미터 |
|---|---|---|
| `dashboard` | `fragments-daily :: dashboardCard` | `week`(그 주 금요일, 생략 시 이번 주) |
| `entry` | `fragments-daily :: weekPanel` | `week` |
| `records` | `fragments-daily :: recordsPane` | `month`(`yyyy-MM`, 생략/오형식 시 이번 달) |
| 없음 | `fragments-entry :: noop` | — (저장만) |

> **`fragments-daily.html`은 frontend가 새로 만들어야 하는 파일이다.** 위 세 프래그먼트 이름이 계약이다.
> 세 프래그먼트 모두 **`hx-swap="outerHTML"`로 통째 교체**하는 것을 전제로 만들었다(추가/삭제는 구조 변경 동작이므로 기존 규약 그대로).
> 대시보드는 목업이 `beforeend` + oob를 제안했지만 **행 단위 프래그먼트는 제공하지 않는다** — 목업 스크립트 자체도 추가 후 카드를 통째로 다시 그리고 `focus()`로 커서를 되돌린다. 같은 방식(카드 outerHTML 교체 + `app.js`에서 재포커스)으로 가면 된다. 행 프래그먼트가 꼭 필요하면 backend에 요청할 것.

### 인라인 수정(`POST /daily-notes/{id}`)의 hours 처리

`hx-trigger="change" hx-swap="none"` 대응이라 저장만 하고 빈 응답을 준다.
- `text` 파라미터를 **안 보내면** 텍스트를 건드리지 않는다.
- `hours`는 **파라미터 존재 여부**로 판단한다 — `hours=`(빈 값)를 보내면 `null`로 지워지고, 아예 안 보내면 기존 값이 유지된다.
- 행 하나를 `<form>`으로 묶어 `text`/`hours`를 함께 보내는 기존 항목 행 방식이면 그대로 동작한다.

---

## 4. 모델 속성명 (템플릿이 `${...}`로 참조할 이름)

### 4.1 `GET /` (dashboard)

| 속성 | 타입 | 내용 |
|---|---|---|
| `activeTab` | String | `"dashboard"` |
| `period` | `WeekPeriod` | 이번 주. `period.weekStart` / `period.weekEnd` / `${period.label()}` |
| `week` | `LocalDate` | 이번 주 금요일 |
| `lowThreshold` | `BigDecimal` | `0.80` (히스토리 카드와 동일 기준) |
| `report` | `WeeklyReport` \| **null** | 이번 주 보고서. null이면 "미작성", `report.status`로 DRAFT/SUBMITTED 분기 |
| `heroTotalHours` | String | 이번 주 총 투입시간(뒷자리 0 제거, 예 `"46"`) |
| `heroManWeek` | `BigDecimal` | 이번 주 맨위크(scale 2) |
| `heroItemCount` | int | 이번 주 항목 수 |
| `avgManWeek` | `BigDecimal` | **최근 2주 평균 맨위크**(scale 2, 없으면 `0.00`) |
| `avgWeeks` | `List<WeeklyReport>` | 그 평균의 모집단(최신순, 최대 2건). `w.weekLabel`, `w.totalManWeek` |
| `avgWeekCount` | int | `2` (카드 제목 "최근 N주 평균"용) |
| `activeProjects` | `List<DashboardController.ProjectProgress>` | `p.project.name`, `p.project.ticket`, `p.completion`(int %), `p.lastWeekLabel`(String, 보고 이력 없으면 null) |
| `activeProjectCount` | int | 위 목록 건수 |
| `recentReports` | `List<WeeklyReport>` | 과거 제출본 최신 5건(이번 주 제외) |
| `pastReportCount` | int | 과거 제출본 **전체** 건수 — `pastReportCount > 5`일 때만 "전체 N개 보기" 노출 |
| + §4.4의 일일 기록 카드 속성 전부 | | |
| + `settings`, `historyCount` | | `LayoutAdvice`가 채움(헤더용) |

> `heroTotalHours`/`heroManWeek`은 `report.totalHours`/`totalManWeek`을 **읽지 않고 매번 다시 계산**한다. 그 필드들은 제출 시점에만 채워져 작성중(draft) 주에서는 0이기 때문. 템플릿에서 `report.totalManWeek`을 쓰지 말 것.

### 4.2 `GET /entry` (기존 + 추가)

기존 그대로: `period`, `week`, `prevWeek`, `nextWeek`, `report`, `activeTab`(`"write"`), `projectCards`, `devItems`, `etcItems`, `vacationItems`, `avgManWeek`, `activeProjectCount`.

> ⚠️ 작성 화면의 `avgManWeek`은 **여전히 최근 4주 평균**이고 대시보드의 `avgManWeek`은 2주 평균이다 — 이름은 같지만 다른 지표이며 서로 다른 화면이라 충돌은 없다. (대시보드 재기획 문서는 작성 탭 상단 통계를 "그 주의 총 시간/맨위크/항목 수"로 바꾸자고 했는데, 그건 프론트 화면 구성 변경이라 이번 backend 범위 밖이다. 필요하면 `report`와 `ManWeekService` 값으로 frontend가 처리하거나 backend에 요청할 것.)

**추가된 속성(좌측 패널용, `report`가 null인 빈 상태에서도 항상 채워진다):**

| 속성 | 타입 | 내용 |
|---|---|---|
| `panelDayGroups` | `List<DayGroup>` | 그 주 **오름차순**. 기록이 있는 날 + (이번 주면) 오늘만. 오늘 그룹은 기록이 없어도 나오고 이때 `notes`가 빈 리스트("기록 없음" 자리) |
| `panelDates` | `List<LocalDate>` | 그 주 7일 전부(추가 폼의 날짜 셀렉트용) |
| `panelDefaultDate` | `LocalDate` | 날짜 셀렉트 기본 선택값(이번 주면 오늘, 아니면 그 주 목요일) |
| `panelIsCurrentWeek` | boolean | 카드 제목을 "이번 주에 한 일" / "N월 M주에 한 일"로 가르는 값 |
| `weekNoteCount` | int | 그 주 기록 건수 |
| `weekHoursDisplay` | String | 그 주 기록 시간 합계. **합이 0이면 빈 문자열** → 그때는 아예 렌더하지 말 것 |
| `today` | `LocalDate` | 오늘 |

### 4.3 `GET /history`

| 속성 | 타입 | 내용 |
|---|---|---|
| `activeTab` | String | `"history"` |
| `historyView` | String | `"reports"`(기본) \| `"records"` — 서브 세그먼트 활성 표시 |
| `reports` | `List<WeeklyReport>` | 제출본 전체(기존과 동일) |
| `reportCount` | int | `reports.size()` — 세그먼트 버튼 "과거 보고서 · N" |
| `dailyNoteCount` | long | 전체 기록 수 — 세그먼트 버튼 "한 일 기록 · N" |
| `lowThreshold` | `BigDecimal` | `0.80` |

**`view=records`일 때만 추가로 채워지는 속성:**

| 속성 | 타입 | 내용 |
|---|---|---|
| `recordMonth` | String | `"2026-08"` — 링크에 그대로 실어 보낼 값 |
| `recordMonthLabel` | String | `"2026년 8월"` |
| `prevMonth` / `nextMonth` | String | `"yyyy-MM"` — `@{/history(view='records', month=${prevMonth})}` |
| `hasPrevMonth` / `hasNextMonth` | boolean | 페이저 버튼 disabled 판정(기록 존재 범위 ∪ 이번 달 기준) |
| `recordDayGroups` | `List<DayGroup>` | 그 달 **내림차순**(최신 위) |
| `recordCount` | int | 통계 "기록 N건" |
| `recordDayCount` | int | 통계 "기록한 날 N일" |
| `recordHoursDisplay` | String | 통계 "기록된 시간". **여기는 0일 때 `"0"`** (칩과 달리 통계 타일은 항상 그려야 하므로) |
| `recordDefaultDate` | `LocalDate` | 추가 폼의 날짜 기본값(이번 달이면 오늘, 아니면 1일) |
| `today` | `LocalDate` | 오늘 |

> 검색(`q`)은 이번 범위에서 **제외**했다(열린 질문, 사용자 미확정). 파라미터도 없다.

### 4.4 일일 기록 카드/패널 공통 속성

`fragments-daily :: dashboardCard`가 참조하는 속성 — `GET /`과 `POST /daily-notes(view=dashboard)` 양쪽에서 **같은 이름으로** 채워진다:

| 속성 | 타입 | 내용 |
|---|---|---|
| `today` | `LocalDate` | 오늘 |
| `week` | `LocalDate` | 그 주 금요일("주간보고 쓰면서 보기" 링크용) |
| `todayNotes` | `List<DailyNote>` | 오늘 기록(날짜 헤더 없이 바로 깔림) |
| `todayNoteCount` | int | 카드 제목 칩 "N건" |
| `todayHoursDisplay` | String | 칩의 "· Xh". **0이면 빈 문자열** |
| `recentDayGroups` | `List<DayGroup>` | 오늘 이전 날짜 중 **기록이 있는 날만**, 최신순, 최대 3개 |
| `weekNoteCount` | int | 하단 요약 "이번 주 기록 N건" |
| `weekHoursDisplay` | String | 하단 요약의 시간 합계. **0이면 빈 문자열** |

### 4.5 `DailyNoteService.DayGroup` (세 화면 공용 컴포넌트)

```java
public record DayGroup(LocalDate date, List<DailyNote> notes)
```

| 접근 | 예시 값 | 용도 |
|---|---|---|
| `${g.date}` | `2026-08-13` | 폼의 `workDate` 값 |
| `${g.notes}` | `List<DailyNote>` | 그날 기록 줄들 |
| `${g.count()}` | `2` | |
| `${g.label()}` | `"08.13 (목)"` | 날짜 헤더 기본 |
| `${g.longLabel()}` | `"2026.08.13 (목)"` | 연도가 필요한 자리 |
| `${g.isToday()}` | `true` | "오늘" 칩 |
| `${g.hoursDisplay()}` | `"6.5"` | 날짜 헤더 우측 합계. **0이면 빈 문자열 → 그때는 렌더하지 말 것** |

메서드 호출은 `${item.hoursDisplay()}` 기존 관례대로 괄호를 붙여 쓰면 안전하다.

---

## 5. 서비스 공개 메서드 시그니처

### `DailyNoteService` (신규)

```java
List<DailyNote> findByWeek(LocalDate weekStart)        // 오름차순
List<DailyNote> findByMonth(YearMonth month)           // 내림차순
List<DailyNote> findByDate(LocalDate date)
Optional<YearMonth> earliestMonth()
Optional<YearMonth> latestMonth()
long count()

List<DayGroup> groupByDate(List<DailyNote> notes)                        // 주어진 정렬 순서 보존
List<DayGroup> panelGroups(LocalDate weekStart, List<DailyNote> weekNotes) // 작성 패널용(오늘 빈 그룹 포함)
BigDecimal sumHours(List<DailyNote> notes)                               // null hours는 0 취급
String sumHoursDisplay(List<DailyNote> notes)                            // 합이 0이면 ""

Optional<DailyNote> add(LocalDate workDate, String text, BigDecimal hours) // text가 비면 아무것도 안 만듦
void update(Long id, String text, BigDecimal hours, boolean hoursProvided)
void delete(Long id)
```

### `EntryService` (추가만)

```java
BigDecimal recentAverageManWeek()                                       // 기존 4주 — 그대로 둠
BigDecimal recentAverageManWeek(int weeks)                              // 신규
BigDecimal averageManWeek(List<WeeklyReport> reports)                   // 신규 — 목록과 평균을 한 번의 조회로
List<WeeklyReport> recentSubmittedReports(int weeks, LocalDate excludeWeekStart) // 신규 — 최신순, 제출본만
```

### `ReportItemRepository` (추가)

```java
List<ReportItem> findActiveProjectItemsRecentFirst();
// join fetch project + weeklyReport (open-in-view=false 대비), active 프로젝트만, weekStart DESC
```

---

## 6. 결정한 것 / 남긴 것

- **"진행중인 프로젝트" 판정 기준을 확정했다**: `Project.active == true` **AND** 가장 최근 보고된 진행률 < 100%. 진행률은 그 주 세부 항목 완료율의 **평균**(최댓값 아님, 완료율 미입력 항목은 평균에서 제외, 하나도 없으면 0%). 한 번도 보고된 적 없는 활성 프로젝트는 0%로 포함되고 `lastWeekLabel`이 null이다.
  → 승인된 목업(`design/weekly-report-mockup.html`의 `activeProjects()`)의 규칙을 그대로 따랐다. 이로써 CLAUDE.md "다음에 할 일"의 **`Project.active` 집계 방식 미결 항목이 해소**된다(docs-sync가 반영할 것). 모집단에는 draft 보고서도 포함된다("최근 보고 주차"는 실제로 마지막에 적은 주를 뜻하므로).
- 대시보드의 "과거 보고서"·"평균 맨위크 모집단"은 **이번 주를 제외한 제출본만**이다(설계 문서 §4.3 확정 사항 그대로).
- **개발(DEV) 그룹 티켓 필수 여부는 손대지 않았다** — 일일 기록 설계 v3 이후 자동 생성 항목이 사라져 이 기능과 무관해졌고, 여전히 독립된 미결 UX 결정이다.
- **`.md` 내보내기·맨위크 계산은 일절 건드리지 않았다.** `DailyNoteService`의 시간 합계는 `ManWeekService`와 완전히 분리돼 있고 어느 쪽도 서로를 호출하지 않는다.
- 작성 탭 상단 통계 카드 교체(4주 평균 → 그 주 총시간/맨위크/항목수)는 frontend 화면 구성 변경이라 이번에 하지 않았다. 필요하면 요청할 것.

## 7. qa 참고

- 컨텍스트 로드 스모크 테스트(`@SpringBootTest` + `jdbc:h2:mem`)로 새 JPQL/파생 쿼리/매핑이 전부 뜨는 것을 확인한 뒤 임시 파일을 지웠다 — 상시 테스트로 추가할 가치가 있다.
- `EntryController` 생성자가 5인자로 바뀌었다(`..., DailyNoteService`). `DashboardController`는 6인자(`EntryService, DailyNoteService, ManWeekService, WeeklyReportRepository, ProjectRepository, ReportItemRepository`), `HistoryController`는 2인자(`WeeklyReportRepository, DailyNoteService`), `DailyNoteController`는 1인자(`DailyNoteService`).
- `DailyNoteController`의 모델 채우기 3종은 **package-private static 메서드**(`populateDashboardCard`, `populateWeekPanel`, `populateRecordsView`, `parseMonth`)라 같은 패키지의 테스트에서 `Model`만 넘겨 직접 호출할 수 있다.
- 검증 포인트: 기록에 시간을 넣어도 `report.totalHours`/`totalManWeek`/대시보드 `avgManWeek`이 변하지 않을 것, 보고서가 없는 주·제출된 주 모두에서 기록 CRUD가 되는 것, 주/월 정렬 방향이 서로 반대인 것(의도된 설계).
