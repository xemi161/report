# backend — 히스토리 "한 일 기록" 검색 (2026-08-15)

`/history?view=records`의 월 단위 기록 목록에 **그 달 안에서의 본문 검색**을 추가했다.
검색 파라미터명은 `q`, 템플릿이 다시 쓸 모델 속성명은 `recordQuery`(+ 보조 플래그 `recordSearching`).

## 1. 변경 파일

| 파일 | 변경 |
|------|------|
| `src/main/java/com/weeklyreport/repository/DailyNoteRepository.java` | 월 범위 + 본문 부분일치 파생 쿼리 1개 추가 |
| `src/main/java/com/weeklyreport/service/DailyNoteService.java` | `findByMonth(YearMonth, String)` 오버로드 + `normalizeQuery(String)` 추가 |
| `src/main/java/com/weeklyreport/web/DailyNoteController.java` | `populateRecordsView` 4-arg 오버로드, `add`/`delete`/`renderView`에 `q` 추가 |
| `src/main/java/com/weeklyreport/web/HistoryController.java` | `GET /history`에 `q` 파라미터 추가 후 그대로 전달 |
| `src/test/java/com/weeklyreport/web/DailyNoteControllerViewTest.java` | 시그니처 변경에 맞춰 호출부에 `null` 인자 삽입(동작 변경 없음) |

엔티티(`DailyNote`) 필드 변경 **없음**. 스키마 변경 없음. `MdExportService` 무관(손대지 않음).

## 2. 레포지토리

```java
List<DailyNote> findByWorkDateBetweenAndTextContainingIgnoreCaseOrderByWorkDateDescIdAsc(
        LocalDate from, LocalDate to, String text);
```

- 기존 월 조회(`findByWorkDateBetweenOrderByWorkDateDescIdAsc`)와 **정렬 완전 동일**(날짜 내림차순 + 같은 날은 id 오름차순).
- 대소문자 무시는 파생 쿼리 `IgnoreCase`에 위임(H2에서 `LOWER(note_text) LIKE ?`로 나감). 한 달 최대 ~60건이라 인덱스 불필요.
- 컬럼명은 이미 `@Column(name = "note_text")`라 예약어 충돌 없음.

## 3. 서비스 공개 메서드

| 시그니처 | 설명 |
|---|---|
| `List<DailyNote> findByMonth(YearMonth month)` | 기존 그대로(변경 없음) |
| `List<DailyNote> findByMonth(YearMonth month, String query)` | **신규.** query가 null/공백이면 위와 동일 결과 |
| `static String normalizeQuery(String query)` | **신규.** trim 후 비면 `null`(= 검색 안 함) |

## 4. 컨트롤러 라우트 / 파라미터

| 메서드 | 경로 | 파라미터 | 반환 |
|---|---|---|---|
| GET | `/history` | `view`, `month`, **`q`(신규, optional)** | `history` |
| POST | `/daily-notes` | `view`, `week`, `month`, **`q`(신규, optional)** + 폼(`workDate`/`text`/`hours`) | view=records → `fragments-daily :: recordsPane` |
| POST | `/daily-notes/{id}/delete` | `view`, `week`, `month`, **`q`(신규, optional)** | view=records → `fragments-daily :: recordsPane` |
| POST | `/daily-notes/{id}` | (변경 없음) | `fragments-entry :: noop` |

`POST /daily-notes/{id}`(인라인 수정)는 `hx-swap="none"`이라 재렌더링이 없으므로 `q`를 받지 않는다.

내부 시그니처 변경(테스트가 직접 호출함):
- `DailyNoteController.populateRecordsView(Model, DailyNoteService, YearMonth)` — 유지(내부에서 `query=null`로 위임)
- `DailyNoteController.populateRecordsView(Model, DailyNoteService, YearMonth, String query)` — 신규
- `DailyNoteController.add(...)` / `delete(...)` — `Model` 앞에 `String q` 추가
- `DailyNoteController.renderView(...)` — private, `String query` 추가

## 5. 모델 속성 (`recordsPane` 프래그먼트가 쓰는 값)

| 속성명 | 타입 | 검색 시 동작 |
|---|---|---|
| **`recordQuery`** | `String` | **신규.** 정규화된 검색어. 검색 중이 아니면 **빈 문자열**(null 아님) |
| **`recordSearching`** | `boolean` | **신규.** 검색 중이면 true(빈 상태 문구 분기용) |
| `recordDayGroups` | `List<DayGroup>` | 검색 결과 기준 |
| `recordCount` | `int` | 검색 결과 기준 |
| `recordHoursDisplay` | `String` | 검색 결과 기준(0이면 `"0"`) |
| `recordDayCount` | `int` | 검색 결과 기준 |
| `recordMonth` / `recordMonthLabel` / `prevMonth` / `nextMonth` | `String` | 변경 없음 |
| `hasPrevMonth` / `hasNextMonth` | `boolean` | **검색과 무관하게 전체 기록 기준 유지** |
| `recordDefaultDate` | `LocalDate` | 변경 없음 |

## 6. 결정 사항과 근거

1. **통계 3칸은 검색 결과 기준으로 재계산.** 걸러진 목록 위에 "이 달 전체" 합계가 남아 있으면 두 숫자가 서로 다른 모집단을 가리켜 어느 쪽을 읽어야 하는지 알 수 없다. → 프론트는 검색 중일 때 통계 타일 라벨이나 근처에 "검색 결과" 맥락을 한 줄 보여주는 것을 권장(`recordSearching`으로 분기 가능).
2. **월을 넘겨도 검색어는 유지**(월 이동 링크에 `q`를 실어 보내는 전제). 근거: 이 화면의 실제 용례가 "그거 언제 했더라"이고, 검색어를 한 번 치고 달을 넘겨 가며 훑는 것이 자연스럽다. 지우고 싶으면 검색칸을 비우면 되므로 되돌리는 비용이 낮은 반면, 자동으로 지워지면 매달 다시 타이핑해야 한다.
3. **`hasPrevMonth`/`hasNextMonth`는 검색과 무관하게 전체 기록 기준.** 검색어가 안 걸리는 달이라고 이동을 막으면 "다른 달엔 있나" 확인 자체가 불가능해진다.
4. **검색 범위는 그 달 안으로 한정**(전체 기간 검색 아님, 요구사항대로). 월 페이저가 이 화면의 축이라 검색이 축을 무시하면 페이저와 목록이 서로 다른 것을 가리킨다.
5. **추가/삭제 후에도 검색 필터가 유지된다**(`q`를 htmx 요청에 실어 보내면). ⚠️ 이 때문에 **검색 중에 추가한 기록이 검색어와 안 맞으면 목록에서 바로 사라진 것처럼 보인다** — 프론트에서 `recordSearching`이 true일 때 `.quick-add` 폼을 숨기거나 비활성화하는 것을 권장한다.

## 7. 프론트엔드가 해야 할 배선

- 검색 입력: `GET /history`로 보내는 폼(`view=records` hidden + `month` hidden + `name="q"`), 또는 htmx로 `#recordsPane` 교체.
  단순 링크/폼 전송이 기존 관례(월 이동·탭 전환은 전체 페이지 로드)와 일관된다.
- 월 이동 링크에 `q` 추가:
  `th:href="@{/history(view='records', month=${prevMonth}, q=${recordQuery})}"` (nextMonth도 동일).
- 기존 htmx 호출에 `q` 추가:
  - 추가: `@{/daily-notes(view='records', month=${recordMonth}, q=${recordQuery})}`
  - 삭제: `dayGroup`/`dailyRow` 프래그먼트에 `q` 인자를 추가로 넘겨 `@{'/daily-notes/' + ${note.id} + '/delete'(view=..., week=..., month=..., q=...)}`
- 빈 상태 문구 분기: `recordSearching`이 true면 "이 달에 기록된 일이 없습니다." 대신 검색 결과 없음 문구 + 검색 해제 링크(`@{/history(view='records', month=${recordMonth})}`).

## 8. 빌드

`./gradlew compileJava` 통과, `./gradlew test` 전체 그린.
