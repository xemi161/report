---
name: spring-backend-dev
description: "주간업무보고 앱의 Spring Boot 3 + JPA + H2 백엔드 작업 절차와 코드베이스 고유 관례/함정. 엔티티, 서비스, 컨트롤러, 레포지토리를 만들거나 고칠 때, 또는 Gradle 빌드/테스트를 실행할 때 사용."
---

# Spring Backend Dev — 주간업무보고 백엔드 작업 가이드

이 스킬은 이 저장소의 백엔드 구조를 처음부터 다시 파악하지 않고 바로 작업할 수 있도록, 코드에서 이미 확정된 관례와 실제로 겪은 함정을 정리한다.

## 아키텍처 스냅샷

```
domain/          엔티티: WeeklyReport, ReportItem, Project, AppSettings
domain/enums/    Group(PROJECT/DEV/ETC/VACATION), Phase(ANALYSIS_DESIGN/DEVELOPMENT/TEST), ReportStatus(DRAFT/SUBMITTED)
repository/      Spring Data JPA — WeeklyReportRepository, ReportItemRepository, ProjectRepository, AppSettingsRepository
service/         EntryService, MdExportService, CarryOverService, ManWeekService, TicketNumberService, WeekLabelService, WeekPeriod(record)
web/             EntryController, ExportController, HistoryController, OnboardingController, LayoutAdvice(@ControllerAdvice)
web/dto/         ItemForm, OnboardingForm (폼 바인딩 전용)
```

**mapper/facade 계층이 없다.** 컨트롤러가 서비스뿐 아니라 레포지토리도 직접 주입받아 쓰는 경우가 있다(예: `EntryController`). 새 기능을 추가할 때 이 구조를 존중하고, 굳이 계층을 신설하지 않는다.

**검증은 Bean Validation이 아니라 수기 로직이다.** `spring-boot-starter-validation` 의존성은 있지만 `@Valid` 애노테이션은 실질적으로 안 쓰인다 — 제출 검증은 `EntryService.validateForSubmit(WeeklyReport)`가 그룹별(PROJECT/DEV는 티켓+제목+완료율, ETC는 제목, VACATION은 날짜) 필요 필드를 수기로 체크해 한국어 에러 문자열 리스트를 반환한다. 새 필드에 필수 검증이 필요하면 이 메서드에 분기를 추가하는 기존 패턴을 따른다.

## 핵심 도메인 규칙 (재구현하지 말고 재사용할 것)

- **맨위크 계산**: `ManWeekService` — `itemTotalHours = hours × daysOrDefault()`, `itemManWeek = itemTotalHours / 40`(scale 2, HALF_UP). `days`가 null이면 `daysOrDefault()`가 1을 기본값으로 쓴다.
- **이월(carry-over)**: `CarryOverService.applyCarryOver(previous, target)` — PROJECT/DEV 그룹이면서 `completion < 100`(또는 null)인 항목만 다음 주로 복사한다. project/ticket/title/phase/completion만 이어지고 hours/days/note/dates는 리셋되며 `carriedOver=true`로 표시된다. ETC/VACATION은 이월 대상이 아니다.
- **티켓번호 자동완성**: `TicketNumberService` — 순수 숫자만 입력하면 `{prefix}/{digits}`로 변환, 숫자가 아닌 입력은 그대로 통과. prefix는 `AppSettings.ticketPrefix`(온보딩에서 입력, `application.yml`의 `weekly-report.default-ticket-prefix`는 죽은 설정이므로 참고하지 말 것 — 아무 코드도 이 키를 읽지 않는다).
- **주차 라벨**: `WeekLabelService` — 금요일 시작~목요일 종료 주 기준 "N월 M주차" 라벨. ISO 주차 번호가 아니다. `forWeekStart(LocalDate)`는 반드시 금요일이어야 한다.
- **"프로젝트 = 일감(티켓) 하나" 모델**: `ReportItem.PROJECT` 그룹 항목의 `ticket`은 저장 시점 `Project.ticket`의 복사본이다. 과거 항목은 프로젝트의 티켓이 나중에 바뀌어도 저장 당시 값을 그대로 유지한다 — 의도된 동작이며 버그가 아니다.

## 반드시 지킬 것 (실제로 겪은 버그의 원인)

1. **연관관계를 갖는 엔티티는 `equals()`/`hashCode()`를 id 기준으로 오버라이드한다.** `Project.java`가 예시. 다른 쿼리(다른 영속성 컨텍스트)에서 로딩된 동일 row가 기본 identity equals 때문에 다른 객체로 취급되는 버그를 실제로 겪었다. 비교 시 `other.id` 같은 필드 직접 접근이 아니라 반드시 `other.getId()`를 쓴다 — Hibernate 지연 로딩 프록시는 필드 직접 접근 시 항상 null을 반환한다.
2. **SQL 예약어를 컬럼명으로 쓰지 않는다.** `Group` enum 필드는 `@Column(name = "group_type")`로 H2 예약어(`group`) 충돌을 피했다. 새 컬럼명이 예약어(`order`, `group`, `user` 등)와 겹치면 같은 방식으로 명시적 `@Column(name=...)`을 쓴다.
3. **`open-in-view: false`이므로 지연 로딩 연관관계는 트랜잭션 안에서 미리 로드한다.** `WeeklyReportRepository`는 `findByWeekStart`/`findWithItemsById`에서 `items`와 `items.project`를 JOIN FETCH한다. 새 쿼리에서 컨트롤러/템플릿/서비스가 지연 연관관계에 접근한다면 같은 방식으로 fetch 전략을 챙긴다 — 안 그러면 `LazyInitializationException`이 요청 처리 중(트랜잭션 밖)에서 터진다.
4. **BigDecimal 표시값은 뒷자리 0을 뗀다.** DB에서 `8.00`으로 돌아오면 좁은 입력칸에서 잘린다. `ReportItem.hoursDisplay()` / `WeeklyReport.totalHoursDisplay()`가 이미 이 문제를 처리한다 — 새 BigDecimal 표시 필드를 추가하면 같은 스트리핑 로직을 재사용하거나 유사 헬퍼를 추가한다.

## Gradle 명령

```bash
./gradlew bootRun        # 로컬 실행 (server.port=8099, H2 file DB: ~/.weekly-report/data/db)
./gradlew test           # JUnit Platform 단위 테스트
./gradlew bootJar         # weekly-report.jar 생성 (run.bat이 javaw로 실행)
./gradlew compileJava     # 빠른 컴파일 검증
```

`./gradlew bootRun`은 `build/resources/main`을 읽으므로, `src/main/resources`의 템플릿을 고쳐도 재시작 전엔 반영 안 된다(`thymeleaf.cache=false`는 클래스패스 리소스 자체가 갱신되지 않으면 무의미).

## 현재 열려 있는 아키텍처 결정 (구현 전 CLAUDE.md 재확인)

CLAUDE.md의 "다음에 할 일" 절에 아래 미결 사항이 있다 — 관련 작업을 받으면 코드부터 짜지 말고 먼저 이 절이 최신 상태인지 확인하고, 결정이 필요하면 사용자에게 확인을 요청한다:
- ~~`.md` 내보내기의 `<!--DATA...DATA-->` JSON 블록 제거~~ — **2026-08-05 완료**. 이제 마크다운 본문이 곧 파싱 대상이니 `MdExportService` 출력 포맷을 바꾸면 `docs/weekly-report-md-schema.md`(v2)도 함께 고쳐야 한다. 남은 미결은 v1(JSON 포함) 파일의 하위호환 처리 여부
- 개발(DEV) 그룹 티켓번호를 optional로 풀지 여부 — 현재 `validateForSubmit`은 필수로 강제하지만 승인된 목업엔 티켓 없는 개발 항목 예시가 있다
- `Project.active`(프로젝트 종료)를 히스토리/통계 집계에 어떻게 반영할지
