# 주간업무보고 (Weekly Report)

파트원용 주간 업무보고 프로그램. 사내 폐쇄망에서 fat jar + `.bat`으로 실행되는 로컬 Spring Boot 앱.
데이터 교환은 `.md` 파일(사람이 읽는 본문 + `<!--DATA ... DATA-->` JSON 블록)로만 이루어짐.
스키마/설계 원본 문서: `docs/project-handoff-summary.md`(배경·목표·조직구조·데이터모델·화면구성 전체 설계 요약), `docs/weekly-report-md-schema.md`(`.md` 내보내기 포맷 상세) — 파트원 화면만 확정, 파트장/팀장 화면은 범위 밖.

## 기술 스택 & 실행

- Java 17 + Spring Boot 3 + Gradle (`./gradlew bootRun`, `./gradlew test`, `./gradlew bootJar`)
- Thymeleaf 서버사이드 렌더링, H2 파일 DB(`~/.weekly-report/data/db`)
- 이 WSL 환경엔 Java/Gradle이 시스템에 없었어서 Rocky Linux에 `dnf`로 Java 17을 설치하고 Gradle 9.4.1(Windows IntelliJ 캐시에서 복사)로 wrapper를 생성해둠 — `./gradlew`만 쓰면 재현 가능
- 배포: `run.bat`이 `javaw -jar weekly-report.jar`로 콘솔 없이 실행 후 기본 브라우저 자동 오픈

## 개발 환경 (WSL)

- 이 프로젝트는 WSL 배포판 **`Rocky9`** 안, `/home/sanghwaj/project/report`에 있음.
- Windows에서 IntelliJ로 열기: `File > Open` → `\\wsl.localhost\Rocky9\home\sanghwaj\project\report` 입력 → "WSL로 열기" 선택(네트워크 폴더로 그냥 열면 느리고 일부 기능 제한). JDK를 못 찾으면 `\\wsl.localhost\Rocky9\usr\lib\jvm\java-17-openjdk-17.0.20.0.8-1.2.el9_8.x86_64` 경로로 수동 추가.
- 최근 빌드된 실행 파일(`weekly-report.jar` + `run.bat`)은 참고용으로 `C:\Users\sanghwaj\Desktop\상화\105_주간보고_260727\dist\`에도 복사해둔 적 있음(프로젝트 자체엔 없음, 재빌드 시 갱신 필요).

## 프론트엔드 (2026-08-02 이식 완료)

목업 디자인을 실제 Thymeleaf + htmx로 이식하는 작업은 **끝났고 앱에 반영되어 있음**. 아래는 그 결정 배경과 현재 구조:

- **레퍼런스**: 사용자가 제공한 Claude 아티팩트 목업(`주간업무보고 대시보드.zip`, 원본은 Desktop에만 있고 프로젝트엔 미포함)의 시각 언어 — 연회색 배경, 흰 카드(얇은 보더·큰 라운드·그림자 거의 없음), 단일 블루 포인트 컬러(`oklch(55% 0.16 260)`), 보더 없이 텍스트처럼 보이다 포커스 시 강조되는 인라인 입력, 점선 "+추가" 고스트 버튼. **다크모드는 의도적으로 배제 — 레퍼런스의 라이트 톤 하나로 고정**하기로 함(사용자 명시적 요청).
- **승인된 목업**: `design/weekly-report-mockup.html` — 순수 HTML/CSS/vanilla JS 자체완결 파일(Claude Artifact로 발행해 검토 완료, 여러 차례 피드백 반영). 실제 데이터 모델(프로젝트/티켓/구분/시간/일수/완료율/개발완료일·테스트예정일·배포예정일/비고/맨위크/이월)을 그대로 반영하되 화면 구조를 **대시보드 랜딩페이지 없이 "작성" / "히스토리" 2탭으로 단순화**함 — 기존 3화면(대시보드/등록/이력) 구조를 대체. 이 파일이 색상 토큰·컴포넌트 스타일·IA의 기준(source of truth)임.
  - **⚠️ 중요한 정책 변경**: 사용자가 명시적으로 "제출된(지난) 보고서도 항상 수정 가능해야 한다"고 요구해서, `WeeklyReport.status`(draft/submitted)는 잠금 여부가 아니라 단순 상태 표시임 — 제출된 주도 항목 추가/수정/삭제가 전부 가능하고 재제출(다시 내보내기)도 가능. **`docs/project-handoff-summary.md`의 "제출된 보고서는 고정" 규칙은 이 결정으로 폐기됨**(그 문서는 아직 옛 규칙을 담고 있으니 주의). 이식하면서 `EntryService.requireEditable()`과 `HistoryController`의 읽기전용 상세 화면은 제거했다.
  - 항목 입력 행에 컬럼 헤더(일감번호/제목/구분/투입시간/일수/진행률)를 항목이 있을 때만 표시.
  - 일정 상세 패널은 개발완료일/테스트예정일/배포예정일을 한 줄씩(세로 스택) 입력하고 비고를 마지막에 입력하는 순서로 배치.
  - **프로젝트 그룹 입력 구조 (2026-08-02 개정)**: **"프로젝트 = 일감(티켓) 하나"**로 확정 — 한 프로젝트 카드 안에 여러 일감을 두는 구조(및 "+ 일감 추가" 버튼)는 제거함. 이유: 일감 블록에 있던 "제목" 필드가 실제로는 프로젝트명과 항상 같은 값이라 중복이었고, 사용자 워크플로상 프로젝트 하나당 일감도 정확히 하나였음. 카드 헤더에는 **프로젝트명(좌측, 편집 가능) + 일감번호 입력(우측)** 만 표시하고, 그 아래 바로 세부 항목 목록이 옴(중간에 별도 일감 블록 wrapper 없음). 세부 항목은 여전히 "그때그때 추가" 방식 유지 — 구분(분석/설계/개발/테스트)은 각 항목마다 셀렉트로 고르고, **같은 구분을 여러 개 추가할 수 있음**(예: 한 프로젝트에 "로그인 기능"·"2차인증 기능"처럼 개발 항목 2개). **이건 순수 입력 UI 개편이고 데이터 스키마 변경은 아님** — md 내보내기 시에는 손댄 세부 항목마다 여전히 스키마의 flat한 `- {티켓} : {제목} [구분] — {시간}h · {완료율}%` 한 줄씩으로 풀어서 나감(제목은 세부 항목 제목이 있으면 그것, 없으면 프로젝트명). 즉 백엔드 `ReportItem`(항목 1개 = 줄 1개) 모델은 그대로 두고, Thymeleaf 이식 시 "같은 프로젝트명(=일감)을 가진 항목들을 화면에서만 프로젝트 카드 단위로 묶어 보여주는" 프레젠테이션 레이어만 추가하면 됨.
- **인터랙션 구현 방식: htmx**. React 등 SPA는 명시적으로 기각 — 별도 빌드 파이프라인/새 툴체인이 생기는 비용이 이 정도 규모의 사내 도구엔 과함(폐쇄망·전원 자바 개발자 환경이라 애초에 Thymeleaf를 고른 이유와 직결). 폐쇄망이라 CDN을 못 쓰므로 `static/js/htmx.min.js`(2.0.4)를 저장소에 직접 넣어둠.

### 현재 화면 구조

- 라우팅: `/`·`/entry?week=yyyy-MM-dd`(작성 탭) / `/history`(히스토리 탭) / `/export/{id}`(md 다운로드) / `/onboarding`. **대시보드는 삭제됨**(`DashboardController`·`dashboard.html`·`history-detail.html`·`preview.html` 제거). `/history/{id}`는 옛 링크 호환용으로 해당 주 작성 화면으로 리다이렉트만 한다.
- 주차 이동·탭 전환은 평범한 링크(전체 페이지 로드), 나머지 조작만 htmx.
- 보고서는 주차를 넘겨본다고 자동 생성되지 않음 — 없는 주는 빈 상태 화면이고 "작성 시작"을 눌러야 이월 포함 초안이 생긴다.

### htmx 배선 규약 (`fragments-entry.html` 주석에도 동일 내용 있음)

- 구조가 바뀌는 동작(추가/삭제/휴가 기간 토글/프로젝트 추가·삭제)은 `#writeView` **전체**를 다시 렌더링해 `hx-swap="outerHTML"`로 갈아끼운다. 카드별 건수·통계·빈 상태가 한 번에 맞아 프래그먼트를 잘게 쪼개는 것보다 안전함.
- 인라인 필드 수정은 `hx-trigger="change" hx-swap="none"` — 화면을 다시 그리면 입력 포커스가 날아가므로 저장만 한다. 그래서 완료율 100% "완료" 배지는 `static/js/app.js`가 클라이언트에서 즉시 붙인다(일정 패널 접기/펴기, 토스트, 모달 닫기도 여기).
- 행 하나가 통째로 `<form>`이고, `week`는 전부 **쿼리스트링**으로 넘긴다(버튼이 폼 안/밖 어디 있든 동일하게 전달됨).
- "md로 내보내기"만 htmx가 아닌 일반 폼 전송 — 응답이 첨부파일이라 브라우저가 직접 받아야 한다.

### 이식하며 실제로 밟은 함정

- **Thymeleaf는 `th:replace`를 `th:each`/`th:if`보다 먼저 처리한다.** 같은 태그에 같이 쓰면 반복/조건이 적용되기 전에 프래그먼트가 불려서 인자가 null로 들어가고 `EL1007E: Property or field 'id' cannot be found on null`이 난다. 반드시 바깥 `<th:block th:each>` + 안쪽 `<div th:replace>`로 나눌 것.
- `th:text`를 바깥 div에 걸면 안쪽 자식(예: 주차 라벨의 `.range` span)까지 지워진다.
- BigDecimal은 DB에서 `8.00`으로 돌아와 좁은 입력칸에서 잘린다 — 표기용으로 `ReportItem.hoursDisplay()` / `WeeklyReport.totalHoursDisplay()`가 뒷자리 0을 떼준다.
- `./gradlew bootRun`은 `build/resources/main`을 읽으므로, 실행 중에 `src/main/resources`의 템플릿을 고쳐도 반영되지 않는다(= 재시작 필요). `thymeleaf.cache=false`만 믿고 헤매지 말 것.

## 알아두면 좋은 것

- `Group` 등 JPA 연관관계가 있는 엔티티(`Project` 등)는 `equals()`/`hashCode()`를 id 기준으로 반드시 오버라이드해야 함 — 다른 쿼리(다른 영속성 컨텍스트)에서 로딩된 동일 row가 기본 identity equals로 인해 다른 객체로 취급되는 버그를 실제로 겪음. proxy 필드 직접 접근(`other.id`) 대신 반드시 getter(`other.getId()`) 사용 — 그렇지 않으면 Hibernate 지연 로딩 프록시에서 항상 null 반환함.
- SQL 예약어(`group` 등)를 컬럼명으로 쓰면 H2에서 구문 오류 — `@Column(name = "group_type")`처럼 명시적으로 피해야 함.
