# QA 리포트 — 대시보드 재도입(3탭) + 일일 기록(DailyNote)

검증일 2026-08-15 · 대상: backend/frontend가 방금 구현한 대시보드 3탭 + DailyNote 기능 전체

| 단계 | 결과 |
|---|---|
| 1. 정적 교차검증 (라우트/모델속성/프래그먼트/.md/맨위크 분리) | **통과** — 불일치 0건. 별도 발견 1건(§5-A, 이번 변경과 무관한 기존 지표 정의 충돌) |
| 2. `./gradlew test` | **통과** — 53건 전부 성공 (기존 18건 + 이번에 보강한 35건) |
| 3. Playwright E2E (headless chromium, 시나리오 1~6) | **통과** — 98건 중 실패 0건, 브라우저 콘솔 에러 0건 |

---

## 1. 정적 교차검증 결과

### 1.1 라우트 ↔ 템플릿 URL — 통과

컨트롤러 `@GetMapping`/`@PostMapping` 22개와 템플릿의 `@{...}` 27종을 전수 대조했다.
**고아 URL(템플릿에만 있는 경로)도, 미사용 라우트도 없다.**

| 신규 라우트 | 템플릿 호출부 | `hx-target` / `hx-swap` |
|---|---|---|
| `GET /` | `fragments :: header`의 대시보드 탭 | 일반 링크 |
| `POST /daily-notes?view=dashboard&week=` | `fragments-daily :: dashboardCard`의 `.quick-add` | `#dashDaily` / `outerHTML` |
| `POST /daily-notes?view=entry&week=` | `fragments-daily :: weekPanel`의 `.quick-add` | `#weekPanel` / `outerHTML` |
| `POST /daily-notes?view=records&month=` | `fragments-daily :: recordsPane`의 `.quick-add` | `#recordsPane` / `outerHTML` |
| `POST /daily-notes/{id}` | `dailyRow` 폼 자체 | (없음) / `none` |
| `POST /daily-notes/{id}/delete?view=&week=&month=` | `dailyRow`의 휴지통 버튼 | `view`에 대응하는 3개 id / `outerHTML` |
| `GET /history?view=&month=` | 서브 세그먼트 + 월 페이저 | 일반 링크 |

- backend가 계약으로 못 박은 프래그먼트 이름 3종(`dashboardCard`/`weekPanel`/`recordsPane`)이 `fragments-daily.html`에 **그대로** 존재하고, 각 루트 element id(`#dashDaily`/`#weekPanel`/`#recordsPane`)가 htmx `hx-target`과 일치한다.
- `dailyRow`의 삭제 버튼은 `th:with="target=..."`로 view에 따라 타깃을 고르는데, `th:with`(우선순위 500)가 `th:hx-post`(700)보다 먼저 평가되므로 순서상 안전하다.
- `th:replace`를 `th:each`보다 먼저 처리하는 함정 — `fragments-daily.html`의 반복 4곳 전부 바깥 `<th:block th:each>` + 안쪽 `th:replace` 구조를 지켰다.

### 1.2 모델 속성명 ↔ 템플릿 참조 — 통과

backend_summary §4의 속성 전부가 템플릿에서 같은 이름으로 쓰이고, 템플릿이 참조하는 이름 중 컨트롤러가 안 채우는 것은 없다.

- `heroTotalHours` / `heroManWeek` / `heroItemCount` — `dashboard.html`이 이 셋만 쓰고 **`report.totalHours` / `report.totalManWeek`은 전혀 참조하지 않는다.** 금지 사항이 지켜졌다(그 필드는 제출 시점에만 채워져 draft 주에서 0). E2E에서 draft 주 hero가 실측 `20시간 / 0.50 / 3건`으로 나오는 것까지 확인했다.
- **`avgManWeek`(대시보드 2주 vs 작성 탭 4주) — 화면별로 올바르게 쓰였다.** 같은 속성명이지만 각 화면이 자기 컨트롤러가 채운 값만 읽고, 라벨이 `"최근 " + ${avgWeekCount} + "주 평균 맨위크"`(대시보드, 동적) / `"최근 4주 평균 맨위크"`(작성 탭, 고정)로 서로 달라 사용자에게도 혼동되지 않는다. 실측 대시보드 0.93(2주) / 작성 탭 0.89(4주)로 값도 정의대로 갈렸다.
- `DayGroup` 레코드 접근자 — 템플릿이 쓰는 `g.label()` / `g.isToday()` / `g.hoursDisplay()` / `g.count()` / `g.notes`가 전부 실존하고 괄호 호출 관례를 지켰다. `g.date`는 템플릿 미사용(정상 — 폼의 `workDate`는 `panelDates`/`recordDefaultDate`/`today`에서 온다).
- `ProjectProgress`/`ProjectCard` record를 `${p.project.name}`처럼 프로퍼티로 읽는 것은 Spring 6 SpEL의 record 접근자 지원에 의존하며, 기존 `ProjectCard`가 이미 같은 방식으로 동작 중이라 안전하다.

### 1.3 `.md` 내보내기에 일일 기록이 안 나가는지 — 통과 (코드·문서·실물 3중 확인)

1. **코드**: `MdExportService.java`에 `DailyNote`/`dailyNote` 참조가 **0건**. git diff상 이번 변경으로 손대지 않았다(현재 diff는 이전 세션의 JSON 블록 제거 작업).
2. **스펙 문서**: `docs/weekly-report-md-schema.md`에 "일일/기록/DailyNote" 언급 **0건** — v2 스펙 그대로다.
3. **실물**: 실제 앱에서 `/export/{id}`를 받아 본문을 확인했다. 기록 4건(스탠드업/스펙 확인/로그 분석 등)이 같은 주에 존재하는 상태에서 내보낸 결과에 **단 한 글자도 새지 않았다.**

```
# QA테스터 · 8월 2주

파트원 · 2026.08.07 (금) ~ 2026.08.13 (목)

## 프로젝트

### GTPP 결제연동

- NHNKCP-개발1팀/23 : 결제 API 연동 [개발] — 8h · 2일 · 60%
- NHNKCP-개발1팀/23 : 결제 API 설계 [설계] — 6h · 1일 · 80%
...
**합계: 26h / 0.65 맨위크**
```

라인 포맷(`- {티켓} : {제목} [구분] — {시간}h · {일수}일 · {완료율}%`), 헤더 2행 연도 포함, `[설계]` 짧은 라벨, 그룹 순서 전부 v2 스펙과 일치한다.

### 1.4 일일 기록 시간이 맨위크·총투입시간에 안 섞이는지 — 통과

- `EntryService` / `ManWeekService` 어느 쪽도 `DailyNote`를 참조하지 않는다(grep 0건). 역방향도 마찬가지 — `DailyNoteService`는 `ManWeekService`를 호출하지 않는다.
- E2E로 실증: 좌측 패널에 2h짜리 기록을 추가한 뒤 대시보드로 돌아가도 **총투입시간 20 / 맨위크 0.50 / 최근 2주 평균 0.93이 전부 그대로**였다.
- 단위 테스트 `일일_기록은_hero_통계에_전혀_섞이지_않는다`로 회귀 고정.

### 1.5 지연 로딩(open-in-view: false) — 통과

| 접근하는 지연 연관 | 조회 쿼리 | 판정 |
|---|---|---|
| `report.getItems()` (hero 계산) | `findByWeekStart` — `left join fetch w.items i left join fetch i.project` | 안전 |
| `item.getProject()` / `item.getWeeklyReport()` (진행중 프로젝트) | `findActiveProjectItemsRecentFirst` — `join fetch i.project join fetch i.weeklyReport` | 안전 |
| `recentReports` / `avgWeeks` | 스칼라 필드만 렌더(`weekLabel`, `totalManWeek`, `weekStart/End`) | 안전 |
| `DailyNote` | 연관관계 자체가 없음 | 해당 없음 |

`Project.equals/hashCode` 함정도 회피됐다 — `DashboardController`가 프로젝트를 `Map<Long, ...>`(id 키)로 모은다. `DailyNote`는 연관이 없어 equals 오버라이드 미적용이 정당하다.

### 1.6 제출=비잠금 정책과의 무충돌 — 통과

기록은 보고서 존재 여부·상태와 완전히 무관하게 동작한다. E2E로 3가지 상태를 전부 확인:
제출된 주(`/entry?week=2026-08-07`) 패널 렌더 + 추가 폼 존재 / 보고서 없는 주(`?week=2026-06-05`) 패널 렌더 + 빈 상태 화면 공존 / draft 주 정상.

### 1.7 정렬 방향 — **버그 아님(의도된 설계)**

작성 패널 `08.14 → 08.15` 오름차순, 기록 화면 `08.15 → 08.14` 내림차순을 실측으로 확인했다. 지시대로 버그로 잡지 않았고, 오히려 **양방향을 회귀 테스트로 고정**해 두었다(`날짜별_묶음은_주어진_정렬_순서를_그대로_보존한다` + E2E 2건) — 나중에 누가 "정렬이 이상하다"며 한쪽을 뒤집으면 즉시 빨간불이 켜진다.

### 1.8 목업(v5) 대비 — 통과

목업의 클래스 어휘를 실제 템플릿/CSS와 전수 대조했다. **목업에만 있고 구현에 없는 클래스는 `rec-search` / `rec-scope` / `clear` 3개뿐**이며, 전부 사용자가 이번 범위에서 제외한 검색 UI다. CSS 계산값(`680` / `916` / `1670` / `1650` / `1280` / `day-sum padding-right:33px`)도 worklog §6 수치와 정확히 일치한다.

---

## 2. `./gradlew test` — 통과 (53건)

```
tests=53 skipped=0 failures=0 errors=0
```

기존 18건은 전부 그대로 통과했다(이번 변경으로 깨진 기존 테스트 **없음**).

### 보강한 테스트 35건

DailyNote/대시보드 로직에 테스트가 **하나도 없었기에** 기존 관례(JUnit5 + AssertJ + Mockito, 한국어 문장형 메서드명, `@SpringBootTest` 없이 직접 `new`/mock)를 그대로 따라 3개 파일을 추가했다.

| 파일 | 건수 | 고정한 계약 |
|---|---|---|
| `src/test/java/com/weeklyreport/service/DailyNoteServiceTest.java` | 14 | 시간 합계 0 → **빈 문자열**(템플릿이 `#strings.isEmpty`로 렌더 여부를 가르는 신호), null 시간은 합계에서만 0 취급, 정렬 순서 보존, 패널의 "오늘 빈 그룹", 빈 텍스트 무시, `hoursProvided` 3분기, 주 범위 = 금~목 7일 |
| `src/test/java/com/weeklyreport/web/DailyNoteControllerViewTest.java` | 13 | `view` → 프래그먼트 이름 **문자열째** 고정(오타 나도 컴파일은 통과하는 자리), 모델 속성명 전수, 통계 타일은 0일 때 `"0"`(칩과 반대 규칙), 최근 3일 최신순 제한, `parseMonth` 오형식 방어 |
| `src/test/java/com/weeklyreport/web/DashboardControllerTest.java` | 8 | hero는 저장 합계가 아니라 **매번 실측**, 진행률은 완료율의 **평균**(최댓값 아님), 100%는 진행중에서 제외, 더 오래된 주 항목 미혼입, 미보고 활성 프로젝트 0%, 과거 보고서 5개 + 전체 건수 분리, 기록/맨위크 분리 |

> 이 중 `진행률은_그_주_완료율의_평균이다_최댓값이_아니다`와 `hero_통계는_저장된_합계가_아니라_항목에서_매번_다시_계산한다`는 backend가 주석으로만 경고해둔 규칙이라, 리팩터링 중 조용히 뒤집히기 가장 쉬운 자리다.

---

## 3. Playwright E2E — 통과 (98건 / 실패 0 / 콘솔 에러 0)

실제 headless chromium으로 구동. **사용자 DB는 건드리지 않았다** — in-memory H2(`jdbc:h2:mem:qaTest`) + `ddl-auto=create` + 전용 시드로 포트 9411에 별도 기동했고, 검증 후 실제 DB 파일의 최종 수정 시각이 세션 시작 전(2026-08-14 06:33) 그대로임을 확인했다.

| 시나리오 | 결과 | 주요 실측값 |
|---|---|---|
| 1. 대시보드 랜딩 | 통과 (21) | 탭 3개, hero `20시간/0.50/3건`(작성중), 평균 0.93 + 모집단 2주, 진행중 2건, 과거 보고서 5장 + "전체 6개 보기", 오늘 한 일 위젯 |
| 2. 기록 추가(htmx) | 통과 (8) | 새로고침 없이 행 추가, 칩 `1건 · 1.5h`, 하단 요약 서버 재계산 `3건 · 4.5h`, 추가 후 재포커스, 시간 인라인 수정 시 app.js 즉시 재계산 |
| 3. 작성 탭 좌우 분할 | 통과 (19+1 NOTE) | `#weekPanel` 680px, 날짜 셀렉트 7일 + 기본값 오늘, 추가/삭제 왕복, 기록 시간이 통계에 미오염 |
| 4. 뷰포트별 레이아웃 | 통과 (18) | 아래 표 |
| 5. 히스토리 서브뷰 | 통과 (23) | 세그먼트 전환, 월 페이저 8월↔7월 왕복, 양 끝 `pager-disabled`, 기록 CRUD, 제출된 주/보고서 없는 주 |
| 6. 콘솔 에러 | 통과 (1) | `console.error` / `pageerror` / `requestfailed` / HTTP 4xx·5xx **전부 0건** |

### 뷰포트별 실측

| 뷰포트 | 컬럼 | 패널 폭 | 항목 행 줄바꿈 | 가로 스크롤 |
|---|---|---|---|---|
| 1670px | 2 | **680px** (설계값 정확히 일치) | 0건 | 없음 |
| 1536px | 2 | 546px (우측 하한 916px 보장) | 0건 | 없음 |
| 1280px | **1 (세로 스택)** | 1224px | 0건 | 없음 |
| 1000px | 1 (세로 스택) | 944px | 0건 | 없음 |

- 이 검증이 의미를 가지도록 **최악 케이스 행을 일부러 만들었다** — 티켓 입력이 있는 dev 행에 `이월`과 `완료` 배지가 동시에 붙은 상태(worklog §9 qa가 지목한 그 행). 네 뷰포트 전부에서 한 줄을 유지했다.
- 패널 숨기기 → `body.wide` 해제 + `main` 1140px 복귀 확인. 대시보드/히스토리도 1140px.

---

## 4. 내가 고친 것

**앱 코드(`src/`)에서 고친 것은 없다.** 이번 구현에서 결함을 찾지 못했기 때문이다. 고친 것은 전부 내 검증 도구의 결함이다.

| 무엇 | 왜 |
|---|---|
| `playwright_test.js`의 줄바꿈 판정 로직 | 처음엔 자식 element들의 `top` 좌표가 갈리는지로 판정했는데, `.row-main`은 `align-items:center`라 **한 줄이어도** 높이가 다른 자식(select 31px / badge 21px / pct-wrap 38px)의 top이 당연히 갈린다. 그래서 1670px(설계상 절대 안 깨지는 폭)에서도 "깨졌다"는 오탐이 났다. `행 높이 > 가장 높은 자식 높이`로 바꿔 실제 flex wrap만 잡도록 수정 → 4건 오탐 해소. **앱 버그가 아니라 테스트 버그였다.** |
| `DailyNoteControllerViewTest`에 `@BeforeEach` 기본 스텁 추가 | 내가 `sumHoursDisplay`를 스텁하지 않아 Mockito가 null을 돌려줬고 컨트롤러가 NPE로 죽었다. 실제 서비스는 절대 null을 주지 않으므로(합계 0이면 빈 문자열) **프로덕션 결함이 아니다.** 목 기본값 문제라 스텁으로 해결. |
| `qa_run_app.ps1`의 절대경로 → `$PSScriptRoot` | 경로에 한글("상화")이 있는데 Windows PowerShell 5.1이 BOM 없는 UTF-8 `.ps1`을 ANSI로 읽어 한글이 깨져 `Set-Location`이 실패했다(그 탓에 첫 기동에서 시드가 통째로 안 먹었다). 파일 안에 한글 절대경로를 박지 않도록 수정 — 이제 단독 실행 가능. |
| `qa_seed.sql`에 최악 케이스 행 추가 | 위 표의 "완료+이월 동시" 행이 없으면 뷰포트 검증이 사실상 아무것도 증명하지 못한다. |

---

## 5. 남은 이슈

### A. [불일치] "진행중 프로젝트" 수가 대시보드와 작성 탭에서 다르다 — **제품 결정 필요**

```
[불일치] 모델 속성명 (같은 이름 / 다른 정의)
- 생산자 ①: DashboardController.java:161 — activeProjectCount = active AND 최근 진행률 < 100%
- 생산자 ②: EntryController.java:254 — activeProjectCount = projectRepository.findByActiveTrueOrderByNameAsc().size()
- 소비자 ①: dashboard.html:82 "진행중인 프로젝트"     → 2건
- 소비자 ②: entry.html:64   "진행중 프로젝트 수"      → 3
- 재현: 활성 프로젝트 3개 중 하나의 최근 완료율이 100%인 상태에서 대시보드 → 작성 탭 이동
- 제안: 제품 결정 후 backend 수정
```

- **이건 회귀가 아니다.** 작성 탭의 계산식은 이번 변경 전부터 있었고, 대시보드가 새로 들어오면서 **같은 이름의 지표가 두 개**가 된 것이다.
- 다만 backend_summary §6이 "진행중 판정 기준을 `active AND 최근 진행률 < 100%`로 **확정**했다(CLAUDE.md의 `Project.active` 미결 항목 해소)"고 선언한 이상, 작성 탭의 단순 active 카운트는 그 확정 규칙과 어긋난 채 남아 있다. 라벨도 "진행중인 프로젝트" / "진행중 프로젝트 수"로 거의 같아 탭을 오가면 모순으로 읽힌다.
- `avgManWeek`(2주 vs 4주)은 라벨이 서로 달라 자체 구분되지만, 이쪽은 라벨까지 같아 더 위험하다.
- **내가 임의로 고치지 않은 이유**: 해법이 (a) 작성 탭을 확정 규칙에 맞추기 (b) 작성 탭 라벨을 "등록된 프로젝트 수"로 바꾸기 두 갈래인데, 어느 쪽이 맞는지는 오타 수정이 아니라 제품 결정이다. E2E에 `[NOTE]`로 기록해 두었다.

### B. [정리] `historyCount` 모델 속성이 완전히 죽었다 — backend 판단

`LayoutAdvice.java:38`이 여전히 채우지만 **템플릿 참조가 0건**이다(헤더 탭에서 건수를 뺐으므로). 단순 dead code가 아니라 비용이 있다 — `LayoutAdvice`는 Dashboard/Entry/History/DailyNote 컨트롤러 전부에 걸려 있어 **`POST /daily-notes` 같은 htmx 요청 하나하나마다 아무도 안 읽는 `countByStatus` COUNT 쿼리가 나간다.** frontend도 §8-6에서 같은 지적을 남겼다.

### C. [기존 확인 사항] 월 밖 날짜로 기록을 추가하면 목록에서 사라져 보인다

frontend_summary §8-2가 이미 밝혀둔 대로다(기록 화면의 날짜 칸은 `date` 입력이라 다른 달을 고를 수 있는데, 서버는 요청에 실린 `month`로 다시 그린다). 데이터는 정상 저장되고 해당 달로 가면 보인다. 목업 의도("적은 날짜의 달로 따라간다")와는 다르므로 backend 계약 확장이 필요하다 — 이번 범위 밖으로 두었다.

### D. [사소] `DailyNoteService`의 순수 함수에 트랜잭션이 걸린다

클래스 레벨 `@Transactional` 때문에 DB를 전혀 안 쓰는 `groupByDate` / `panelGroups` / `sumHours` / `sumHoursDisplay`까지 트랜잭션을 연다. 동작엔 문제없고 성능 영향도 미미하다. 고칠 가치가 있다고 판단되면 backend가.

---

## 6. 산출물

| 파일 | 내용 |
|---|---|
| `playwright_test.js` | E2E 스크립트(시나리오 1~6, 98 assertion). 재실행 가능 |
| `qa_run_app.ps1` | 격리 기동 스크립트(포트 9411 + in-memory H2 + 시드). 단독 실행 가능 |
| `qa_seed.sql` | 전용 시드(과거 제출본 6건, 진행률 분기용 프로젝트 4개, 기록 4건, 레이아웃 최악 케이스 행) |
| `playwright_raw_results.json` | 98건 원시 결과 + 수집된 콘솔 이벤트 |
| `screenshots/*.png` | 대시보드, 기록 추가 후, 작성 탭 4개 뷰포트, 패널 숨김, 기록 화면 |

**재실행 방법**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File _workspace/.../qa_run_app.ps1   # 별도 창
$env:NODE_PATH = "<playwright가 설치된 node_modules>"
node _workspace/.../playwright_test.js
```

`playwright`는 저장소에 넣지 않고 임시 디렉터리에 설치해 `NODE_PATH`로만 연결했다(폐쇄망 배포물에 개발 의존성을 남기지 않기 위해).

---

## 7. 정리 확인

- 포트 9411 임시 서버 **종료 완료**
- 테스트 DB는 in-memory였으므로 **디스크에 남은 파일 없음**
- 사용자 실제 DB `~/.weekly-report/data/db.mv.db` — 최종 수정 시각 **2026-08-14 06:33** (세션 시작 전 그대로, 미접촉)
