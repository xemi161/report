---
name: weekly-report-testing
description: "주간업무보고 앱의 QA 절차 — 컨트롤러 라우트/모델 속성명/템플릿 참조/.md 스펙 교차 검증 방법론과 JUnit5+AssertJ 테스트 작성 관례. backend나 frontend 변경 후 정합성을 확인하거나, ./gradlew test를 돌리거나, 테스트를 새로 쓸 때 사용."
---

# Weekly-Report Testing — QA 절차 가이드

## 왜 교차 비교가 필요한가

이 스택(Spring MVC + Thymeleaf)은 프론트-백엔드 경계에 정적 타입 검사가 없다. 컨트롤러에서 `model.addAttribute("reportt", ...)`처럼 오타를 내도 컴파일은 통과하고, 템플릿의 `${report}`는 런타임에만 `EL1007E` 등으로 실패한다. 그래서 "존재하는가"가 아니라 "양쪽이 실제로 같은 이름/모양을 쓰는가"를 확인하는 게 QA의 핵심이다.

## 교차 검증 절차

각 항목을 검증할 때는 반드시 생산자 파일과 소비자 파일을 **동시에 열어** 대조한다.

### 1. 라우트 ↔ 템플릿 URL

```
1. web/ 하위 컨트롤러에서 @GetMapping/@PostMapping의 경로+HTTP메서드 목록 추출
2. templates/ 하위에서 th:action, th:hx-post, th:hx-get 값 전부 grep
3. 각 템플릿 URL이 실제 컨트롤러 경로와 매칭되는지 확인 (경로 변수 {id} 포함)
4. week 쿼리스트링이 전달되어야 하는 곳에서 실제로 붙어 있는지 확인
```

### 2. 모델 속성명 ↔ 템플릿 참조

```
1. 컨트롤러의 model.addAttribute("key", ...) 전부 grep
2. 해당 뷰/프래그먼트에서 ${key} 참조 및 프래그먼트 파라미터명 대조
3. 이름이 다르면(리네임 누락 등) 즉시 불일치로 리포트
```

### 3. 엔티티/서비스 메서드명 ↔ 템플릿 호출

```
1. ReportItem/WeeklyReport/Project의 공개 메서드(특히 hoursDisplay(), totalHoursDisplay(), displayTitle() 등 표시용 헬퍼) 목록
2. 템플릿에서 item.xxx()/report.xxx() 형태로 실제 호출하는 이름과 대조
3. 서비스 리팩터링으로 메서드명이 바뀌었는데 템플릿이 옛 이름을 쓰면 컴파일 에러 없이 런타임에만 깨진다 — 반드시 grep으로 전수 대조
```

### 4. `.md` 내보내기 포맷 ↔ 스펙 문서

```
1. MdExportService의 실제 출력(라인 포맷, 구분자, 그룹 순서: 프로젝트→개발→기타→휴가)을 코드에서 확인
2. docs/weekly-report-md-schema.md에 문서화된 스펙과 라인 단위로 대조
3. 불일치 발견 시 코드가 맞는지 문서가 맞는지 판단하지 말고, 둘 다 docs-sync 에이전트(또는 리더)에게 보고 — 이 포맷은 향후 파트장 툴이 파싱할 계약이므로 임의로 어느 한쪽을 고치지 않는다
```

### 5. 제출 검증 메시지 ↔ UI 노출

```
1. EntryService.validateForSubmit()이 반환하는 한국어 에러 문자열 목록
2. 템플릿의 error-banner/modal-errors가 그 문자열을 실제로 렌더링하는지 확인
```

### 6. 지연 로딩 ↔ fetch 전략

```
1. 컨트롤러/서비스/템플릿에서 접근하는 WeeklyReport.items, ReportItem.project 등 지연 연관관계 사용처 확인
2. 그 접근이 일어나는 트랜잭션 범위 안에서 레포지토리 쿼리가 JOIN FETCH로 미리 로드했는지 확인 (open-in-view: false이므로 트랜잭션 밖 접근은 LazyInitializationException)
```

## 테스트 작성 관례

기존 테스트(`src/test/java/com/weeklyreport/`)가 확립한 스타일을 그대로 따른다:

- **프레임워크**: JUnit 5 + AssertJ + Mockito. `@SpringBootTest`/`@DataJpaTest`/`@WebMvcTest`/`MockMvc`는 이 프로젝트에 하나도 없다 — 요청받지 않았으면 새로 도입하지 않는다.
- **패턴**: 서비스는 `new ServiceClass(...)`로 직접 생성(생성자 의존성만 수동 주입), 컨트롤러는 `Mockito.mock()`으로 레포/서비스를 목킹해 생성. 프라이빗 메서드 검증이 필요하면 `ReflectionTestUtils.invokeMethod`, 엔티티 id를 강제로 세팅해야 하면 `ReflectionTestUtils.setField`를 쓴다(`EntryControllerProjectCardTest` 참고).
- **메서드명**: 한국어 전체 문장형으로 동작을 서술한다. 예: `완료율_100_미만인_프로젝트_개발_항목만_이월된다`.
- **실행**: `./gradlew test`

## 알려진 커버리지 공백 (우선순위순)

1. `EntryService` — 전체 미검증. `validateForSubmit`/`submit`/`addItem`/`updateItem`/이월 연동이 특히 비어 있다.
2. `EntryController` 외 컨트롤러 — `ExportController`, `HistoryController`, `OnboardingController` 전부 테스트 없음.
3. `WeekLabelService.current()` — 정적 라벨 계산 로직 일부만 테스트됨.
4. `TicketNumberService` — 숫자/비숫자 분기 테스트 없음.

새 기능을 추가하며 이 영역을 건드리게 되면, 그 김에 최소 1개 테스트를 보강하는 걸 권장한다(강제는 아님 — 범위를 벗어난 대규모 리팩터는 사용자 확인 후).

## 검증 리포트 형식

발견한 불일치는 다음 형식으로 리더/관련 에이전트에 전달한다:

```
[불일치] {경계면 종류}
- 생산자: {파일}:{라인} — {실제 값}
- 소비자: {파일}:{라인} — {기대하는 값}
- 재현: {구체적 조건, 예: "/entry?week=2026-08-07로 접근 시 EL1007E"}
- 제안: {backend 수정 / frontend 수정 / 둘 다 확인 필요}
```
