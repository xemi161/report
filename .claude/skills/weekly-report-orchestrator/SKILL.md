---
name: weekly-report-orchestrator
description: "주간업무보고(weekly-report) Spring Boot/Thymeleaf/htmx 프로젝트의 개발 작업을 조율하는 오케스트레이터. 이 저장소(C:\\Users\\sanghwaj\\Desktop\\상화\\workspace\\intellij\\report)에서 기능 추가, 버그 수정, 엔티티/서비스/컨트롤러/템플릿/htmx/CSS/테스트/문서 변경, UI/UX 기획·화면 설계·목업·와이어프레임·레이아웃 개편을 요청받으면 반드시 사용. '~구현해줘', '~고쳐줘', '~화면에 추가해줘', '~로직 바꿔줘', '~화면 어떻게 할지 짜줘', '~시안 줘' 같은 요청은 물론, 후속 작업 — 다시 해줘/이어서/보완해줘/방금 한 거 수정/업데이트해줘 — 에도 반드시 이 스킬을 사용한다. 단순 질문(코드 설명, '이거 왜 이래?')에는 스킬 없이 직접 답해도 된다."
---

# Weekly-Report Orchestrator

주간업무보고 앱의 design/backend/frontend/qa/docs-sync 에이전트 팀(또는 단일 에이전트)을 조율해 기획/기능/버그 작업을 완결하는 통합 스킬.

## 실행 모드: 하이브리드 (요청 규모에 따라 라우팅)

이 프로젝트는 1인 개발 사내 도구다. 모든 요청에 5인 팀을 띄우면 오버헤드가 이득보다 크다. Phase 1에서 규모를 먼저 판단해 아래 3단계 중 하나로 라우팅한다.

| 단계 | 판단 기준 | 실행 방식 |
|---|---|---|
| **Tier 0 — 사소** | 한 파일 내 국소 수정, 계층 간 계약 변화 없음(CSS 색상 하나, 문구 수정, 오타) | 오케스트레이터가 직접 수정, 에이전트 스폰 없음 |
| **Tier 1 — 단일 계층** | design/backend/frontend/qa/docs 중 정확히 한 영역에만 국한된 의미 있는 변경 | 해당 에이전트 1명을 `Agent` 도구로 서브 에이전트 호출 |
| **Tier 2 — 교차 작업** | design+frontend, backend+frontend를 동시에 건드리거나, 데이터/스키마/정책/IA 계약이 바뀌는 기능 | `TeamCreate`로 에이전트 팀 구성(불가 시 순차/병렬 `Agent` 서브에이전트로 대체 — 아래 "환경 제약" 참고) |

**design 에이전트 포함 여부 판단**: 기존 화면 패턴을 그대로 따르는 기계적 변경(예: 기존 카드에 필드 하나 추가, 기존 버튼 문구 수정)은 frontend가 바로 처리하면 되므로 design을 부르지 않는다. **새 화면/컴포넌트, 정보구조(IA) 변경, 레이아웃 개편, "이 기능 어떻게 보여줄지 모르겠다"류 요청**이면 design을 frontend보다 먼저 투입한다 — 순서를 바꿔 frontend가 먼저 구현하면, design이 나중에 다른 방향을 제안할 때 재작업 비용이 크다.

## 환경 제약 (2026-08-05 실측, 세션마다 먼저 확인할 것)

이 하네스를 처음 만든 세션과 실제로 첫 기능 작업(md 내보내기 JSON 블록 제거)을 돌린 세션에서 아래 세 가지가 확인됐다. 매번 새로 시행착오를 겪지 않도록 Phase 시작 전에 한 번 점검한다:

1. **`TeamCreate`가 이 환경에 없을 수 있다.** `ToolSearch`로 `TeamCreate`/`TeamDelete`를 찾아 실제 있는지 먼저 확인한다. 없으면 Tier 2도 **순차/병렬 `Agent` 서브에이전트 호출**로 진행한다 — backend처럼 다른 팀원이 결과를 기다려야 하는 작업은 `run_in_background: false`(foreground)로 먼저 끝내고, 그 결과(정확한 라인 포맷/라우트/필드명 등)를 다음 에이전트들의 프롬프트에 **그대로 텍스트로 포함**해서 넘긴다. 서로 의존하지 않는 나머지(qa/docs-sync 등)는 `run_in_background: true`로 병렬 실행해도 된다.
2. **방금 만들었거나 고친 커스텀 에이전트 타입(`subagent_type: "backend"` 등)이 같은 세션에서 바로 인식되지 않을 수 있다.** ("Agent type 'backend' not found" 에러) 이 경우 `subagent_type: "general-purpose"`로 대체하고, 해당 `.claude/agents/{name}.md` 파일의 역할·원칙·프로토콜 내용을 프롬프트 서두에 그대로 인라인한다 ("이번 세션에서 커스텀 타입 로드가 안 돼 general-purpose로 대신 호출하는 것뿐, 역할은 `.claude/agents/{name}.md`와 동일" 같은 문구로 명시). 다음 세션에서는 커스텀 타입이 인식될 수 있으니 매번 먼저 시도해본다.
3. **서브에이전트에게는 `TaskGet`/`TaskUpdate`가 없을 수 있다.** 에이전트에게 "완료 시 TaskUpdate 호출"을 지시해도 도구가 없어 실패할 수 있다. **오케스트레이터가 각 서브에이전트의 반환 요약을 받은 뒤 직접 `TaskUpdate`로 상태를 갱신**하는 걸 기본으로 한다 — 에이전트 지시문에는 "요약을 응답에 남기라"고만 하고 TaskUpdate 호출 자체는 요구하지 않는다.

이 세 제약이 이번엔 있었지만 다음 세션엔 없을 수도 있다(플랫폼이 갱신되거나 세션이 새로 시작되면 커스텀 타입 인식이 될 가능성이 높음) — 매번 실제로 시도해보고, 위 우회로는 실패했을 때만 쓴다.

## 에이전트 구성

| 팀원 | 파일 | 역할 | 스킬 |
|---|---|---|---|
| design | `.claude/agents/design.md` | UI/UX 기획, `design/weekly-report-mockup.html` 갱신, Artifact 발행 | `weekly-report-design` |
| backend | `.claude/agents/backend.md` | 엔티티/서비스/컨트롤러/레포지토리 | `spring-backend-dev` |
| frontend | `.claude/agents/frontend.md` | Thymeleaf 템플릿/htmx/CSS/JS | `thymeleaf-htmx-dev` |
| qa | `.claude/agents/qa.md` | 경계면 교차검증 + 테스트 | `weekly-report-testing` |
| docs-sync | `.claude/agents/docs-sync.md` | CLAUDE.md / docs/*.md 갱신 | `weekly-report-docs-sync` |

작업 순서상 **design → backend/frontend → qa → docs-sync**가 기본이다(design이 필요 없는 작업은 건너뛴다). backend와 frontend는 서로 독립적이면 병렬 진행 가능하지만, frontend가 design이 확정한 화면을 구현하는 경우엔 design 완료(및 필요 시 사용자 승인) 후 시작한다.

모든 Agent/TeamCreate 호출에 `model: "opus"`를 명시한다.

## 워크플로우

### Phase 0: 컨텍스트 확인 (후속 작업 지원)

1. `_workspace/` 디렉토리 존재 여부와, 사용자 요청이 "방금 한 것/아까 그거/이전 작업"을 가리키는 표현을 포함하는지 확인한다.
2. 후속 작업이면 `_workspace/` 하위에서 가장 최근 타임스탬프 폴더(`{YYYYMMDD_HHMMSS}_{slug}/`)를 찾아 그 안의 `*_summary.md`를 Read로 먼저 읽고, 어느 에이전트를 재소집할지 판단한다. 부분 수정이면 관련 에이전트만 재호출(Tier 1 방식)하고, 이전 산출물 경로를 프롬프트에 포함해 "기존 결과를 읽고 피드백만 반영하라"고 지시한다.
3. 새 기능 요청이면 Phase 1로 진행해 새 워크스페이스를 만든다. 기존 `_workspace/*` 폴더는 감사 추적용이므로 지우지 않는다.

### Phase 1: 범위 판단 및 준비

1. 요청을 분석해 위 Tier 표에 따라 규모를 정한다. 애매하면 더 무거운 쪽(Tier를 한 단계 올림)으로 판단한다 — 팀 통신 비용보다 계약 불일치로 인한 재작업 비용이 이 프로젝트에서 더 크다(과거 "프로젝트=일감" 리모델링, md 스키마처럼 계약 변경이 실제로 반복됐다).
2. Tier 1/2면 `_workspace/{YYYYMMDD_HHMMSS}_{slug}/`를 새로 만든다(`slug`는 요청 내용을 짧은 kebab-case로 요약).
3. **Tier 0이면 Phase 2~5를 건너뛰고 직접 수정한다.** 수정 후 영향받는 최소 범위만 검증(예: CSS만 바꿨으면 브라우저 확인 불필요 언급, backend 컴파일이 걸리면 `./gradlew compileJava`)하고 종료.

### Phase 2A: Tier 1 — 단일 에이전트 호출

```
Agent(
  description: "{짧은 설명}",
  subagent_type: "{design|backend|frontend|qa|docs-sync}",
  model: "opus",
  prompt: "{요청 전문 + 관련 파일 경로 + 완료 시 _workspace/.../summary.md에 요약 저장 지시}"
)
```

단일 에이전트이므로 팀 통신은 불필요하다. 완료 후 Phase 4(검증)로 이동하되, qa 재호출이 필요한 변경(엔티티/컨트롤러/템플릿)이면 qa를 Tier 1로 한 번 더 호출한다. **design만 단독으로 부르는 경우**(예: "이 화면 시안 좀 짜줘" — 아직 구현까지는 요청받지 않음)도 흔하다 — 이때는 목업 갱신 + Artifact 발행까지만 하고, frontend/qa/docs-sync는 사용자가 승인하고 "이제 구현해줘"라고 후속 요청할 때 별도 실행으로 진행한다.

### Phase 2B: Tier 2 — 팀 구성

```
TeamCreate(
  team_name: "weekly-report-team",
  members: [
    { name: "design", agent_type: "design", model: "opus", prompt: "{UI/UX 기획 범위 + weekly-report-design 스킬 로드 + 확정되면 Artifact 발행 후 frontend에 통지하라는 지시}" },
    { name: "backend", agent_type: "backend", model: "opus", prompt: "{기능 요청 중 백엔드 범위 + spring-backend-dev 스킬 로드 지시}" },
    { name: "frontend", agent_type: "frontend", model: "opus", prompt: "{기능 요청 중 프론트 범위 + design/backend 완료 통지를 기다리라는 지시}" },
    { name: "qa", agent_type: "qa", model: "opus", prompt: "{backend/frontend 각각 완료 통지 시 즉시 incremental 검증하라는 지시}" },
    { name: "docs-sync", agent_type: "docs-sync", model: "opus", prompt: "{팀 전원 완료 후 CLAUDE.md/docs 갱신 지시}" }
  ]
)
```

기능마다 필요한 팀원만 포함한다(5명 고정이 아니다) — 예: 순수 UI 개편이면 design+frontend+qa만, 데이터 모델은 그대로인 화면 추가면 design+frontend+qa, 새 필드 추가처럼 화면 패턴 변경이 없으면 design 없이 backend+frontend+qa+docs-sync만.

작업 등록:

```
TaskCreate(tasks: [
  { title: "design: {구체 작업}", assignee: "design" },  // 필요 없으면 생략
  { title: "backend: {구체 작업}", assignee: "backend" },  // design과 독립이면 병렬 가능
  { title: "frontend: {구체 작업}", assignee: "frontend", depends_on: ["design: ...", "backend: ..."] },  // 없는 선행 작업은 생략
  { title: "qa: 경계면 교차검증 + 테스트", assignee: "qa", depends_on: ["backend: ...", "frontend: ..."] },
  { title: "docs-sync: 문서 갱신", assignee: "docs-sync", depends_on: ["qa: ..."] }
])
```

### Phase 3: 팀원 자체 조율 (Tier 2만 해당)

**실행 모드:** 에이전트 팀

- design이 목업을 확정하면 Artifact를 발행하고 SendMessage로 frontend에게 통지(목업 경로+설계 근거 포함) — design이 필요한 작업인데 **사용자 승인이 아직이면 frontend는 구현을 시작하지 않고 대기**한다. 승인 대기가 필요한지 애매하면 리더가 사용자에게 직접 확인한다(작은 조정이면 승인 없이 진행해도 되지만, 새 화면/IA 변경이면 반드시 승인을 받는다)
- backend가 라우트/모델 속성명을 확정하면 SendMessage로 frontend에게 통지(추측으로 먼저 만들지 않도록)
- qa는 backend 또는 frontend 완료 통지를 받을 때마다 **즉시** incremental 검증을 수행한다 — 전체 완성 후 1회가 아니다. 불일치 발견 시 해당 에이전트(들)에게 파일:라인 + 수정 방법을 SendMessage. design이 포함된 작업이면 "구현이 목업/설계 근거와 실제로 일치하는가"도 검증 항목에 넣는다
- 리더(오케스트레이터)는 팀원 유휴 알림을 받으면 진행 상황을 확인하고, 막힌 팀원에게 SendMessage로 지시하거나 작업을 재할당한다
- 산출물: 각 팀원이 `_workspace/{run}/{agent}_summary.md`에 요약 저장

### Phase 4: 검증 통합

1. Tier 2면 `TaskGet`으로 전원 완료 확인, qa의 최종 검증 리포트를 Read로 수집
2. Tier 1이면 해당 에이전트 반환값 확인
3. `./gradlew test` 결과와 qa 리포트에 실패/미검증 항목이 있으면 사용자에게 명시(숨기지 않는다)

### Phase 5: 정리 및 보고

1. Tier 2면 팀원들에게 SendMessage로 종료 통지 후 `TeamDelete`
2. `_workspace/` 보존(감사 추적용, 삭제하지 않음)
3. 사용자에게 변경 요약 + 남은 이슈(있다면) 보고
4. 결과에 대한 피드백 기회 제공 — 강요하지 않되 짧게 물어본다

## 데이터 흐름

```
[오케스트레이터] → Phase 1 규모 판단
   ├─ Tier 0 → 직접 수정
   ├─ Tier 1 → Agent(단일) → 반환값 수집
   └─ Tier 2 → TeamCreate
                  ├─ design ──(Artifact 발행 → 사용자 승인)── SendMessage ──┐
                  └─ backend ─────────────────────── SendMessage ──────────┼──→ frontend
                                                                            ↓
                                                          design_summary.md / frontend_summary.md / backend_summary.md
                                                                            │
                                                                    qa (incremental 검증)
                                                                            │
                                                                       qa_report.md
                                                                            │
                                                                     docs-sync (마지막)
                                                                            │
                                                             CLAUDE.md / docs/*.md 갱신
```

## 에러 핸들링

| 상황 | 전략 |
|---|---|
| 단일 에이전트(Tier 1) 실패 | 1회 재시도 후 재실패 시 사용자에게 실패 내용 보고, 임의로 우회하지 않음 |
| 팀원 1명 실패/유휴 정지(Tier 2) | 리더가 SendMessage로 상태 확인 → 재시작. backend 실패 시 frontend/qa는 대기(의존성) |
| qa가 경계면 불일치 발견 | 관련 에이전트에게 즉시 통지, 수정 후 qa가 재검증 — 최대 2회까지 자동 반복, 그래도 남으면 사용자에게 보고 |
| `./gradlew test` 실패 | qa가 원인을 backend/frontend 중 특정해서 리포트, 임의로 테스트를 삭제/skip 처리하지 않음 |
| CLAUDE.md "다음에 할 일"에 있는 미결 정책과 충돌하는 요청 | 코드부터 짜지 않고 사용자에게 먼저 확인(AskUserQuestion) — 예: dev 그룹 티켓 필수 여부, v1 `.md`(JSON 블록 포함) 하위호환 처리 여부 |
| 팀원 간 데이터 충돌(예: backend/frontend가 서로 다른 필드명을 가정) | 삭제하지 않고 양쪽 다 보고, qa가 실제 코드 기준으로 정답 판단 |
| design 산출물(목업)에 대한 사용자 승인이 오래 안 옴 | frontend를 무기한 대기시키지 않는다 — 리더가 사용자에게 상태를 물어보고, 급하지 않으면 design 결과만 보고하고 이번 실행은 종료(후속 요청 때 재개) |
| design이 기존 IA/디자인 언어를 깨는 제안을 하려 함(다크모드, 3번째 탭 등) | design 에이전트 정의상 임의 진행 금지 항목이므로, 이 경우 design이 먼저 사용자에게 AskUserQuestion으로 확인 — 리더가 대신 판단하지 않는다 |

## 테스트 시나리오

### 정상 흐름 (Tier 2)
1. 사용자: "개발 항목에 담당자 필드 추가해줘"
2. Phase 0: `_workspace/` 없음 → 초기 실행
3. Phase 1: backend+frontend+qa+docs-sync 모두 필요 → Tier 2
4. Phase 2B: 4인 팀 구성, `_workspace/20260805_143000_add-assignee-field/` 생성
5. Phase 3: backend가 `ReportItem.assignee` 필드+검증 추가 후 frontend에 통지 → frontend가 템플릿에 입력 필드 추가 → qa가 즉시 라우트/필드명 대조 및 신규 테스트 → docs-sync가 CLAUDE.md 갱신
6. Phase 4: `./gradlew test` 통과 확인
7. Phase 5: 팀 정리, 사용자에게 요약 보고

### 에러 흐름
1. 사용자: "완료율 100%인 항목 자동 이월 안 되게 해줘" (Tier 1, backend만)
2. Phase 2A: backend 서브 에이전트 호출 → `CarryOverService` 수정
3. `./gradlew test` 실행 결과 `ManWeekServiceTest` 무관 실패 하나 발견
4. Phase 4: 원인이 이번 변경과 무관한 기존 실패인지 backend에게 재확인 요청(1회 재시도)
5. 무관한 기존 실패로 판명 시 이를 명시하고 이번 변경 자체는 완료로 보고, 기존 실패는 별도 이슈로 사용자에게 알림

### 후속 작업 흐름
1. 사용자: "아까 추가한 담당자 필드, 필수값으로 바꿔줘"
2. Phase 0: `_workspace/`에서 `add-assignee-field` 관련 최신 폴더 발견 → 후속 작업으로 판단
3. `backend_summary.md`를 읽고 backend 에이전트만 Tier 1로 재호출(검증 로직 추가), 이후 qa로 재검증

### design 포함 흐름
1. 사용자: "히스토리 탭에 주별 맨위크 추이를 보여주는 그래프를 넣고 싶은데 어떻게 할지 짜줘"
2. Phase 1: 새 컴포넌트 + 레이아웃 판단 필요 → design 우선 투입 (구현까지 요청했으므로 Tier 2, design+frontend+qa)
3. Phase 2B: design이 `design/weekly-report-mockup.html`에 히스토리 화면 상단 그래프 영역을 프로토타입하고 `Artifact`로 발행 → 사용자에게 URL 제시
4. 사용자가 목업을 보고 피드백("그래프보다 최근 4주 평균 숫자만 크게 보여줘") → design이 목업 재수정 후 재발행
5. 사용자 승인 → design이 frontend에 SendMessage로 통지(목업 경로 + 설계 근거: "최근 4주 평균 맨위크를 stat 카드로, 그래프 없음")
6. frontend가 Thymeleaf로 구현(그래프 라이브러리 도입 없이 서버에서 평균 계산해 stat 카드 렌더링) → qa가 구현이 승인된 설계와 일치하는지 확인
7. Phase 5: 사용자에게 최종 결과 보고
