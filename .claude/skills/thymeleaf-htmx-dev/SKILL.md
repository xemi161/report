---
name: thymeleaf-htmx-dev
description: "주간업무보고 앱의 Thymeleaf + htmx 화면 작업 절차와 코드베이스 고유 배선 규약/함정. templates/, static/css/app.css, static/js/app.js를 만들거나 고칠 때, htmx hx-post/hx-swap/hx-target을 배선할 때, 목업(design/weekly-report-mockup.html)과 실제 화면을 비교할 때 사용."
---

# Thymeleaf + htmx Dev — 주간업무보고 화면 작업 가이드

## 화면 구조 스냅샷

```
templates/fragments.html         공통 <head> 프래그먼트 + 상단 header 프래그먼트(탭/주차 이동)
templates/dashboard.html         "대시보드" 탭(2026-08-15 재도입) — 첫 진입(`/`), 서버가 계산해 렌더하는 거의 정적인 화면
templates/entry.html             "작성" 탭 — #writeView 섹션 (초기 렌더 + htmx 전체 교체 대상 겸용). 좌측엔 일일 기록 패널(`.split` 레이아웃)
templates/fragments-entry.html   entry.html이 쓰는 행/필드 프래그먼트 전부 + previewModal
templates/fragments-daily.html   일일 기록(DailyNote) 프래그먼트 — 대시보드 카드/작성 패널/히스토리 "한 일 기록" 서브뷰 3곳이 공유
templates/history.html           "히스토리" 탭 — view=reports(과거 보고서)/records(한 일 기록) 서브 세그먼트
templates/onboarding.html        최초 실행 온보딩 폼 — 순수 <form method="post">, htmx 없음, 목업에 대응물 없음
```

라우팅: `/`(대시보드) · `/entry?week=` (작성) · `/history?view=reports|records&month=&q=` (히스토리) · `/daily-notes*` (일일 기록 CRUD) · `/export/{id}` (md 다운로드) · `/onboarding`. **2026-08-02엔 대시보드를 없앴었지만 2026-08-06 재기획으로 되살아났다** — `history-detail.html`/`preview.html`은 여전히 없고 되살리지 않는다(그 둘은 지금도 안 씀), `dashboard.html`은 새 설계로 신설됐다.

## htmx 배선 규약 (fragments-entry.html 상단 주석과 동일 — 반드시 지킬 것)

이 프로젝트는 기본적으로 두 가지 패턴만 쓴다. 새 상호작용을 만들 때도 먼저 이 둘 중 하나를 고른다:

1. **구조가 바뀌는 동작(추가/삭제/토글)** → 그 화면의 블록 전체를 `hx-swap="outerHTML"`로 다시 받는다. 카드별 건수·통계·빈 상태가 한 번에 맞아떨어져서, 프래그먼트를 잘게 쪼개는 것보다 안전하기 때문에 의도적으로 선택된 방식이다. 작성 탭은 `#writeView`, 일일 기록은 화면마다 `#dashDaily`/`#weekPanel`/`#recordsPane` 하나씩. preview만 예외로 `hx-target="#modalHost" hx-swap="innerHTML"`.
2. **인라인 필드 수정** → `hx-trigger="change" hx-swap="none"`. 화면을 다시 그리면 입력 포커스가 날아가므로 저장만 하고 화면은 그대로 둔다. 그래서 완료율 100% "완료" 배지처럼 즉시 반영이 필요한 UI는 서버 응답이 아니라 `app.js`가 `input` 이벤트에서 클라이언트 측으로 직접 처리한다(`toggleDoneBadge`).

**세 번째 패턴(2026-08-15 추가) — 페이지 전체를 주는 GET 라우트에서 프래그먼트만 뽑아 쓸 때**: `hx-select="#targetId"`로 응답 중 그 블록만 추출한다. 히스토리 검색이 예시 — `GET /history`는 페이지 전체를 반환하므로 `hx-select="#recordsPane"` + `hx-target="#recordsPane" hx-swap="outerHTML"`로 쓴다. **주의**: `hx-select`는 스왑 단위를 바꾸는 게 아니라 응답 쪽 필터일 뿐이다 — 스왑 자체는 여전히 패턴 1(블록 통째 `outerHTML`)과 동일하므로 규약과 충돌하지 않는다.

그 외 규칙:
- `week`/`month`/`q` 등은 트리거 요소가 `<form>` 안이든 밖이든 항상 쿼리스트링(`@{...(week=${week})}`)으로 전달한다.
- 탭 전환·주차 이동은 htmx가 아니라 일반 `<a href>` 전체 페이지 로드다 — 렌더링되는 템플릿 자체가 바뀌기 때문에 의도적으로 htmx를 안 쓴다.
- md 내보내기 버튼만 일반 `<form method="post">` 전송이다 — 응답이 파일 첨부라 브라우저가 직접 받아야 한다.
- `hx-indicator`/`hx-boost`/`hx-push-url`/`hx-vals`/`hx-include`/`hx-on`은 이 프로젝트에서 안 쓴다(`hx-select`는 위에서 예외로 도입됨). `.htmx-indicator`/`.htmx-request` CSS는 존재하지만 죽은 스캐폴딩이니 새로 활성화하려면 실제 사용처를 먼저 만든다.
- 삭제처럼 되돌리기 힘든 동작에는 `th:attr="hx-confirm=...">`로 네이티브 확인창을 붙인다(프로젝트 삭제가 예시).
- ⚠️ **입력칸의 `th:value`에 정규화(trim/strip)된 서버 값을 그대로 쓰지 마라** — 검색 입력에서 실제로 겪은 버그: `hx-swap="none"`이 아닌 스왑(디바운스된 검색 등)이 일어나면 입력칸이 정규화된 값으로 다시 그려져, 사용자가 방금 친 공백이 사라지고 다음 글자와 붙어버린다. 정규화 전 원문을 별도 모델 속성(예: `recordQueryRaw`)으로 받아 입력칸 `value`에는 그걸 쓰고, 링크·필터링 로직에는 정규화된 값을 쓴다.

## Thymeleaf 함정 (실제로 겪은 버그)

1. **`th:replace`는 같은 태그의 `th:each`/`th:if`보다 먼저 처리된다.** 같이 쓰면 반복/조건이 적용되기 전에 프래그먼트가 호출되어 인자가 null로 들어가고 `EL1007E: Property or field 'id' cannot be found on null`이 난다. 반드시 바깥 `<th:block th:each="...">` + 안쪽 `<div th:replace="...">`로 나눈다 (`entry.html`의 기존 반복 블록들이 전부 이 패턴).
2. **`th:text`를 부모 요소에 걸면 자식 마크업이 지워진다.** 예: 주차 라벨 div에 `th:text`를 걸면 안쪽 `.range` span까지 사라진다. 부모/자식에 필요한 텍스트를 각각 별도 `th:text`로 채운다.
3. 프래그먼트 인자는 위치 기반으로 깊게 체이닝된다(예: `projectSubitemRow(item, week, projectId)` 내부에서 다시 `phaseSelect(item)`, `effortFields(item)` 등을 호출) — 새 프래그먼트를 추가할 때 인자 순서를 바꾸면 호출부 전체를 같이 고쳐야 한다.

## 목업(design/weekly-report-mockup.html)을 참고하는 범위

목업은 **색상 토큰·타이포·컴포넌트 시각 스타일의 기준**이다. `app.css`의 `:root` 커스텀 프로퍼티는 목업과 값이 동일해야 한다 — 목업을 고치면 `app.css`도 같이 맞춘다.

목업의 **`<script>` 로직은 참고하지 않는다.** 목업은 클라이언트 상태 배열(`WEEKS`)과 `data-field`/`data-add-item`/`data-remove` 같은 vanilla JS DOM 조작으로 동작하는 완전 클라이언트 SPA이지만, 실제 앱은 그 책임을 전부 서버(컨트롤러+htmx 스왑)로 옮겼다. `app.js`는 서버가 처리할 수 없는 **순수 클라이언트 동작**만 담는다:
- 토스트 표시/자동 닫힘 (`showToast`, `flushPendingToast` — 서버가 스왑된 프래그먼트에 심은 `[data-toast]` 마커를 읽는다)
- 모달 닫기 (`closeModal` — `#modalHost`를 비운다. 배경 클릭/Escape/`[data-close-modal]` 클릭)
- 완료 배지 즉시 반영 (`toggleDoneBadge` — `.completion-input`의 `input` 이벤트)
- 일정/비고 상세 패널 펴기/접기 (`[data-detail-toggle]`)

새 상호작용이 필요하면 먼저 "서버 왕복이 필요한가?"를 판단한다 — 필요하면 컨트롤러+htmx 배선(위 두 패턴 중 하나), 필요 없으면 `app.js`에 추가한다. 목업의 JS를 그대로 복붙하지 않는다.

## 색상/다크모드 정책

`--accent: oklch(55% 0.16 260)` 등 오클치 토큰 하나로 통일된 라이트 팔레트만 쓴다. `:root[data-theme="dark"]`가 정의되어 있지만 라이트 값과 완전히 동일하게 고정되어 있다 — OS 다크모드나 뷰어 설정이 강제로 다크를 걸어도 라이트 톤을 재사용하기 위한 의도된 no-op이다. **사용자가 명시적으로 요청한 정책이므로, `prefers-color-scheme: dark` 미디어쿼리나 실제 다크 팔레트를 새로 추가하지 않는다.**

## 리로드 확인

`./gradlew bootRun` 실행 중에는 `src/main/resources` 템플릿을 고쳐도 즉시 반영되지 않는다(`build/resources/main`을 읽음). 화면 변경을 실제로 확인하려면 재시작이 필요하다는 걸 사용자에게 알린다.
