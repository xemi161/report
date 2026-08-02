# 주간 업무보고 MD 파일 스키마 (v1)

파트원이 입력화면에서 "md로 내보내기"를 누르면 생성되는 파일의 형식.
사람이 읽을 수 있는 마크다운 본문 + 프로그램이 파싱하는 JSON 데이터 블록, 두 부분으로 구성된다.

---

## 1. 파일명 규칙

```
주간보고_{이름}_{N월M주차}.md
예: 주간보고_정준호_8월1주.md
```

## 2. 사람이 읽는 영역 (마크다운 본문)

```markdown
# 정준호 · 8월 1주

파트원 · 08.01 (금) ~ 08.07 (목)

## 프로젝트

### GTPP

- NHNKCP-개발1팀/23 : 외국환 보고서 현행화 [개발] — 8h · 60%
  - 테스트예정일: 2026-08-10
  - 비고: 외부 연계사 일정으로 테스트 1주 연기

## 개발

- NHNKCP-개발1팀/26 : 2차인증 프로세스 개선 [설계] — 1h · 5% (이월)

## 기타

- 파트 주간회의 — 1h
- 문의처리 — 1h

## 휴가

- 2026-08-01 — 8h

---

**합계: 40h / 1.00 맨위크**

<!--DATA
{...}
DATA-->
```

**표기 규칙**
- 그룹 순서는 항상 프로젝트 → 개발 → 기타 → 휴가 고정
- 프로젝트 그룹은 `### 프로젝트명` 소제목으로 하위 항목을 묶음 (프로젝트 여러 개면 소제목도 여러 개)
- 항목 줄 형식: `- {티켓번호 : }{업무명} {[구분]} — {시간} · {완료율}%{ (이월)}`
  - 티켓번호가 없으면 `：` 앞부분 생략
  - 시간이 비어있으면 `—` 이후 생략
  - 이월된 항목이면 줄 끝에 `(이월)` 표시
- 일정(개발완료일/테스트예정일/배포예정일)이나 비고가 있으면 항목 아래 들여쓰기로 추가 (없는 필드는 생략)
- 기타/휴가 그룹은 구분·완료율 없이 `- {업무명 또는 날짜} — {시간}`만
- 휴가가 기간(여러 날)이면 날짜 자리에 `{시작일} ~ {종료일}`로 표기하고, 시간은 기간 전체 합산치 하나만 씀(일별로 나누지 않음)

## 3. 기계가 읽는 영역 (JSON 데이터 블록)

파일 맨 끝, `<!--DATA` 와 `DATA-->` 사이에 압축 없는 JSON 한 덩어리로 삽입.

```json
{
  "schemaVersion": 1,
  "name": "정준호",
  "role": "파트원",
  "weekLabel": "8월 1주",
  "weekStart": "2026-08-01",
  "weekEnd": "2026-08-07",
  "items": [
    {
      "group": "project",
      "project": "GTPP",
      "ticket": "NHNKCP-개발1팀/23",
      "title": "외국환 보고서 현행화",
      "phase": "개발",
      "hours": 8,
      "days": 1,
      "completion": 60,
      "devDoneDate": null,
      "testDate": "2026-08-10",
      "deployDate": null,
      "note": "외부 연계사 일정으로 테스트 1주 연기",
      "carriedOver": false
    },
    {
      "group": "dev",
      "project": null,
      "ticket": "NHNKCP-개발1팀/26",
      "title": "2차인증 프로세스 개선",
      "phase": "분석/설계",
      "hours": 1,
      "days": 1,
      "completion": 5,
      "devDoneDate": null,
      "testDate": null,
      "deployDate": null,
      "note": null,
      "carriedOver": true
    },
    {
      "group": "etc",
      "title": "파트 주간회의",
      "hours": 1
    },
    {
      "group": "vacation",
      "date": "2026-08-01",
      "endDate": null,
      "hours": 8
    },
    {
      "group": "vacation",
      "date": "2026-08-05",
      "endDate": "2026-08-06",
      "hours": 16
    }
  ],
  "totalHours": 40,
  "totalManWeek": 1.0
}
```

**필드 설명**

| 필드 | 그룹 | 필수 | 설명 |
|---|---|---|---|
| `group` | 전체 | 필수 | `project` \| `dev` \| `etc` \| `vacation` |
| `project` | project | 필수(project만) | 프로젝트명. dev/etc/vacation은 `null` |
| `ticket` | project, dev | 선택 | `NHNKCP-개발1팀/23` 형식. 팀 접두사는 설정값 기준으로 완성, 저장은 완성된 문자열로 |
| `title` | 전체 | 필수 | 업무명 (vacation은 날짜가 사실상 제목 역할이라 생략 가능) |
| `phase` | project, dev | 선택 | `분석/설계` \| `개발` \| `테스트` |
| `hours` | 전체 | 선택 | 총 투입 시간(숫자, 단위 h) |
| `days` | project, dev | 선택 | 투입 일수. 생략 시 1로 간주 |
| `completion` | project, dev | **필수** | 0~100 |
| `devDoneDate` / `testDate` / `deployDate` | project, dev | 선택 | ISO 날짜 문자열 또는 `null` |
| `note` | project, dev | 선택 | 비고(주로 일정 변경 사유) |
| `carriedOver` | project, dev | 선택(기본 false) | 지난주 미완료 항목이 이월된 것인지 |
| `date` | vacation | 필수(vacation만) | 휴가 시작일(하루짜리면 그 하루) |
| `endDate` | vacation | 선택 | 기간 휴가의 종료일. 하루짜리면 `null` |

**필수값 검증 규칙** (요청하신 대로)
- project/dev 그룹: 티켓번호, 업무명, 완료율만 필수. 시간·일수·일정·비고는 선택
- etc 그룹: 업무명 필수, 시간 선택
- vacation 그룹: 날짜(`date`) 필수, `endDate`·시간 선택. `endDate`가 있으면 기간 휴가 — 일별로 항목을 쪼개지 않고 한 항목(`hours`는 기간 전체 합산치)으로 유지

**맨위크 계산**
```
맨위크 = (1 × hours × days) / 5 / 8   // days 생략 시 1
totalManWeek = 전체 항목 시간 합 / 40
```

## 4. 파트장/팀장 프로그램에서의 처리

- 파트장 프로그램은 이 JSON 블록만 파싱해서 자기 로컬 H2에 저장 (마크다운 본문은 사람이 참고용으로 열어볼 때만 사용, 파싱 대상 아님)
- 파트장이 내보내는 파트 취합본 / 팀장이 받는 요약본도 동일한 파일명·구조 원칙(사람이 읽는 영역 + DATA 블록)을 따르되, 상세 스키마는 파트장 화면 설계 시 별도 정의
