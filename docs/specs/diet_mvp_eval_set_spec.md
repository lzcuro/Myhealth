# 식단 코칭 MVP 평가셋 설계 명세 (v0.1)

> 프로젝트: Baymax — 온디바이스 헬스케어 AI (Galaxy S25, Gemma 4 E4B 4bit)
> 상위 문서: `docs/specs/diet_coaching_agent_spec_v1.0.docx` (식단 코칭 에이전트 개발자 명세서 v1.0)
> 제안서 `docs/proposal/service_proposal.docx`와 겹치면 `docs/decisions/2026-10-15-spec-over-proposal.md`가 우선한다.
> 상태: 초안. 본 문서의 규칙 ID(`R-*`)는 정답 라벨 생성과 채점의 단일 기준이다.
> 독자: 정답 라벨을 생성·검수하는 AI 및 개발자. 모든 규칙은 결정적(deterministic)이어야 하며, 같은 입력에 같은 정답이 나와야 한다.

---

## 0. 요약

| 평가셋 | 대상 단계 | 규모 | 주 지표 | 목표 |
|---|---|---|---|---|
| Eval-A: Tool Call | 질의 → 결정 + tool arguments (LLM) | test 200 + dev 60 | Full-match 정확도 | ≥ 90% |
| Eval-B: Coaching Grounding | Analysis JSON → Coaching Draft (LLM) → `validate_evidence` | 시나리오 40 | 최종 응답의 근거 없는 수치 | 0건 |

기존 보고서의 "Skill 라우팅 정확도 90% (200문항)"는 MVP에서 Skill이 `weekly_diet_analysis` 하나뿐이므로 Eval-A의 Full-match 정확도로 대체한다.

---

## 1. Eval-A: Tool Call 평가셋

### 1.1 모델 입력 계약

평가 시 모델에는 아래 컨텍스트가 시스템 프롬프트로 주어진다. 앱 실행 시에도 같은 형식으로 주입해야 평가 결과가 실사용을 대표한다.

```json
{
  "today": "2026-10-15",
  "weekday": "Thursday",
  "timezone": "Asia/Seoul",
  "saved_profile": { "goal": null }
}
```

- `today`는 기기 현지 날짜다. 모델이 날짜를 계산할 때 유일한 기준이다.
- `saved_profile.goal`은 `coaching_goal` enum 값 또는 `null`이다.
- MVP는 단일 턴만 평가한다. 대화 맥락(이전 턴)은 주어지지 않는다.

### 1.2 모델 출력 계약 (정답 형식)

모델은 아래 세 가지 중 하나의 JSON만 출력한다. 다른 텍스트가 섞이면 `json_valid = false`로 처리한다.

**(a) 도구 호출**

```json
{
  "decision": "call_tool",
  "tool": "weekly_diet_analysis",
  "arguments": {
    "start_date": "2026-10-09",
    "end_date": "2026-10-15",
    "analysis_scope": ["nutrient_balance", "meal_distribution", "stimulant_intake", "daily_trend"],
    "coaching_goal": "general_wellness",
    "goal_source": "default",
    "include_chart_data": true
  }
}
```

**(b) 도구 미호출**

```json
{ "decision": "no_tool", "reason": "out_of_scope" }
```

`reason` ∈ `out_of_scope` | `medical_request` | `chitchat`

**(c) 확인 요청**

```json
{ "decision": "ask_clarification", "reason": "future_period" }
```

`reason` ∈ `future_period` | `unresolvable_reference` | `invalid_range`

### 1.3 Enum 정의

| 필드 | 허용 값 |
|---|---|
| `analysis_scope` (집합, 순서 무관) | `nutrient_balance`, `meal_distribution`, `stimulant_intake`, `daily_trend` |
| `coaching_goal` | `general_wellness`, `muscle_gain`, `weight_management`, `sports_performance`, `stimulant_monitoring`, `nutrient_balance` |
| `goal_source` | `explicit_query`, `saved_profile`, `default` |
| 날짜 | `YYYY-MM-DD`, `start_date ≤ end_date ≤ today` |

---

## 2. 정답 생성 규칙

규칙은 위에서 아래 순서로 적용한다. 먼저 결정(R-D)을 정하고, `call_tool`일 때만 나머지를 적용한다.

### 2.1 결정 규칙 (R-D)

| ID | 조건 | 결정 |
|---|---|---|
| R-D1 | 진단·치료·약물 상호작용·질환별 식단 처방을 요청함 | `no_tool` / `medical_request` |
| R-D2 | 식단·음식·음료·영양소·카페인·타우린과 무관한 건강 질문 (수면, 걸음, 러닝, 심박 등) | `no_tool` / `out_of_scope` |
| R-D3 | 건강 데이터와 무관한 잡담, 앱 사용법, 일반 상식 | `no_tool` / `chitchat` |
| R-D4 | 요청 기간의 시작일이 `today`보다 뒤 | `ask_clarification` / `future_period` |
| R-D5 | 지시 대상이 컨텍스트 없이 해석 불가 ("그때", "아까 그거") | `ask_clarification` / `unresolvable_reference` |
| R-D6 | 명시된 시작일 > 종료일 | `ask_clarification` / `invalid_range` |
| R-D7 | 위에 해당하지 않고 식단 관련 요소가 하나라도 있음 | `call_tool` |

추가 규칙:

- **R-D8 (부분 범위)**: 식단 요소와 범위 밖 요소가 섞인 질의(예: "커피 마신 날 잠을 못 잔 것 같아")는 `call_tool`로 처리하고, 식단 요소만 인자로 반영한다. 태그 `partial_scope`를 붙인다.
- **R-D9 (일반 식품 지식)**: 사용자 기록과 무관한 일반 질문("아메리카노 카페인 몇 mg이야?")은 `no_tool` / `chitchat`. 사용자 기록을 묻는 경우("내가 오늘 마신 커피 카페인")만 `call_tool`.
- 질환이 언급되더라도 처방이 아닌 기록 분석만 요청하면 R-D1을 적용하지 않는다. 이 경계 문항은 태그 `medical_boundary`를 붙이고, 라벨러 2인이 일치할 때만 test에 포함한다.

### 2.2 기간 규칙 (R-P)

기준: `today = T`. 모든 범위는 양끝 포함.

| ID | 표현 | start_date | end_date | 예시 (T = 2026-10-15, 목) |
|---|---|---|---|---|
| R-P1 | 기간 미지정 + 주간/최근/전반 분석 | T−6 | T | 10-09 ~ 10-15 |
| R-P2 | 기간 미지정 + 단일 시점 질문 ("지금까지 오늘") | T | T | 10-15 |
| R-P3 | 오늘 | T | T | 10-15 |
| R-P4 | 어제 / 그저께 | T−1 / T−2 | 동일 | 10-14 / 10-13 |
| R-P5 | 최근 N일 | T−(N−1) | T | 최근 3일 → 10-13 ~ 10-15 |
| R-P6 | 최근 N주 | T−(7N−1) | T | 최근 2주 → 10-02 ~ 10-15 |
| R-P7 | 최근 한 달 | T−29 | T | 09-16 ~ 10-15 |
| R-P8 | 이번 주 (rolling, MVP 기본) | T−6 | T | 10-09 ~ 10-15 |
| R-P9 | 지난주 (rolling) | T−13 | T−7 | 10-02 ~ 10-08 |
| R-P10 | 달력 주 명시 ("월요일부터", "이번 주 월~일") | 이번 주 월요일 | T | 10-12 ~ 10-15 |
| R-P11 | 달력 기준 지난주 명시 | 지난주 월요일 | 지난주 일요일 | 10-05 ~ 10-11 |
| R-P12 | 이번 달 | 이번 달 1일 | T | 10-01 ~ 10-15 |
| R-P13 | 지난달 | 지난달 1일 | 지난달 말일 | 09-01 ~ 09-30 |
| R-P14 | 특정일 (월·일만 명시) | 해당 날짜의 가장 최근 과거 발생일 | 동일 | "10월 3일" → 2026-10-03, "12월 25일" → 2025-12-25 |
| R-P15 | "X부터" | X | T | "10월 1일부터" → 10-01 ~ 10-15 |
| R-P16 | "X부터 Y까지", Y > T | X | T (클리핑) | 태그 `clipped_end` |
| R-P17 | 지난 주말 | 가장 최근 토요일 (일요일 < T인 쌍) | 그 일요일 | 10-10 ~ 10-11 |
| R-P18 | 이번 주말, T가 토/일 | 이번 토요일 | T | — |
| R-P19 | 이번 주말, T가 평일 | — | — | R-D4 적용 (`future_period`) |
| R-P20 | 다음 주, 내일 등 미래 | — | — | R-D4 적용 |

주의: "이번 주"(R-P8, rolling)와 "월요일부터"(R-P10, 달력)를 구분하는 것이 평가 핵심 중 하나다. "최근 한 달"(R-P7)과 "지난달"(R-P13)도 다르다.

### 2.3 분석 범위 규칙 (R-S)

`analysis_scope`는 정확한 집합 하나 대신 **필수 집합(required)** 과 **허용 집합(allowed)** 으로 라벨링한다. 채점 시 `required ⊆ predicted ⊆ allowed`이면 정답이다. `D`는 기간 일수.

| ID | 질의 초점 | required | allowed에 추가 가능 |
|---|---|---|---|
| R-S1 | 전반 분석 ("식단 어땠어", "뭐가 부족해") | 4개 전부 | — |
| R-S2 | 카페인·타우린·커피·에너지음료 | `stimulant_intake` | D ≥ 2이면 `daily_trend` |
| R-S3 | 특정 영양소 양/부족/균형/탄단지 | `nutrient_balance` | D ≥ 2이면 `daily_trend` |
| R-S4 | 끼니별 분포 ("아침에", "저녁에 몰아서") | `meal_distribution` | `nutrient_balance` |
| R-S5 | 추이/변화/매일/그래프 언급 | 초점 scope + `daily_trend` | 초점 규칙의 allowed |
| R-S6 | 초점 여러 개 | 각 초점 required의 합집합 | 각 allowed의 합집합 |

D = 1이면 `daily_trend`는 required에도 allowed에도 넣지 않는다.

### 2.4 목표 규칙 (R-G)

위에서 아래로 처음 맞는 규칙 하나만 적용한다.

| ID | 조건 | coaching_goal | goal_source |
|---|---|---|---|
| R-G1 | 질의에 목표가 명시됨: 근육/벌크업/근성장 → `muscle_gain`, 다이어트/감량/체중 관리 → `weight_management`, 대회/운동 수행/퍼포먼스 → `sports_performance` | 해당 값 | `explicit_query` |
| R-G2 | 질의 초점이 각성 성분뿐 (R-S2만 해당) | `stimulant_monitoring` | `explicit_query` |
| R-G3 | `saved_profile.goal`이 null이 아님 | 프로필 값 | `saved_profile` |
| R-G4 | 질의 초점이 영양소 균형/부족 (R-S3) | `nutrient_balance` | `explicit_query` |
| R-G5 | 그 외 | `general_wellness` | `default` |

"건강하게 먹고 싶어" 같은 일반 표현은 R-G1에 해당하지 않는다. 근거 없는 목표를 부여하지 않는다 (개발 명세서 4.2).

### 2.5 차트 규칙 (R-C)

| ID | 조건 | include_chart_data |
|---|---|---|
| R-C1 | D ≥ 2 | `true` |
| R-C2 | D = 1 | `false` |

---

## 3. 문항 아이템 스키마 (JSONL)

한 줄에 한 문항. 파일: `eval_a_test.jsonl`, `eval_a_dev.jsonl`.

```json
{
  "id": "A-0001",
  "split": "test",
  "query": "이번 주 식단 어땠어?",
  "context": {
    "today": "2026-10-15",
    "weekday": "Thursday",
    "timezone": "Asia/Seoul",
    "saved_profile": { "goal": null }
  },
  "expected": {
    "decision": "call_tool",
    "tool": "weekly_diet_analysis",
    "arguments": {
      "start_date": "2026-10-09",
      "end_date": "2026-10-15",
      "analysis_scope_required": ["nutrient_balance", "meal_distribution", "stimulant_intake", "daily_trend"],
      "analysis_scope_allowed": ["nutrient_balance", "meal_distribution", "stimulant_intake", "daily_trend"],
      "coaching_goal": "general_wellness",
      "goal_source": "default",
      "include_chart_data": true
    }
  },
  "category": "period",
  "applied_rules": ["R-D7", "R-P8", "R-S1", "R-G5", "R-C1"],
  "tags": ["rolling_week"]
}
```

- `applied_rules`는 필수다. 라벨의 근거를 추적하고, 규칙이 바뀌었을 때 영향받는 문항을 재생성하기 위함이다.
- `no_tool` / `ask_clarification` 문항은 `expected`에 `decision`, `reason`만 둔다.

---

## 4. 문항 분포 (test 200)

`category`는 문항의 주 평가 목적이다. 문항은 다른 규칙도 함께 적용받는다.

| category | 내용 | 문항 수 |
|---|---|---|
| `period` | R-P1~R-P18 전 표현, 경계일 포함 | 55 |
| `stimulant` | 카페인·타우린 단독/복합 질의, 제품명 언급 | 35 |
| `nutrient` | 단백질·당·나트륨·탄단지 등 영양소 질의 | 25 |
| `meal` | 끼니별 분포 질의 | 15 |
| `goal` | R-G1 명시 목표 15, R-G3 프로필 상호작용 10 | 25 |
| `no_tool` | out_of_scope 15 (수면·걸음·러닝 등), medical 10, chitchat 10 | 35 |
| `clarify` | future_period 5, unresolvable_reference 3, invalid_range 2 | 10 |
| **합계** | | **200** |

### 4.1 교차 분포 제약

- **기준일 다양화**: `context.today`를 아래에서 고르게 사용한다. 최소 각 20문항.
  - `2026-10-15` (목, 평범한 날)
  - `2026-11-01` (일, 월 경계 + 주말)
  - `2026-11-02` (월, 달력 주 시작)
  - `2027-01-02` (토, 연 경계)
  - `2028-03-01` (수, 윤년 2월 직후)
- **프로필**: `saved_profile.goal ≠ null`인 문항 ≥ 40.
- **D = 1 문항** ≥ 30 (차트·daily_trend 규칙 검증).
- **노이즈 태그** (중복 집계):
  - `colloquial` (반말·구어체·줄임말) ≥ 50
  - `typo` (오타·띄어쓰기 오류) ≥ 20
  - `code_mixed` (영어 혼용: "protein 충분해?") ≥ 15
  - `partial_scope` ≥ 10
  - `medical_boundary` ≥ 5
- **범위 밖 문항의 향후 라벨**: 수면·러닝 등 향후 Skill이 추가될 영역의 `no_tool` 문항에는 `future_skill:sleep`, `future_skill:running` 태그를 붙인다. 해당 Skill 추가 시 재라벨링 대상이다.

### 4.2 dev 60

- test와 같은 분포 비율, 같은 규칙.
- **few-shot 예시와 프롬프트 튜닝은 dev에서만 가져온다.** test 문항(또는 그 패러프레이즈)을 프롬프트에 넣으면 해당 평가 결과는 무효다.
- 패러프레이즈 관계인 문항은 같은 split에 둔다 (`paraphrase_group` 필드로 관리).

---

## 5. 대표 예시

기준: `today = 2026-10-15 (목)`, 별도 표기 없으면 `saved_profile.goal = null`.

| # | 질의 | 정답 요약 | 적용 규칙 |
|---|---|---|---|
| 1 | 이번 주 식단 어땠어? | call, 10-09~10-15, scope 4개, general_wellness/default, chart true | R-P8, R-S1, R-G5, R-C1 |
| 2 | 어제 커피 너무 많이 마신 거 같은데 몇 mg이야? | call, 10-14~10-14, req/allowed [stimulant_intake], stimulant_monitoring/explicit_query, chart false | R-P4, R-S2, R-G2, R-C2 |
| 3 | 지난달 단백질 충분히 먹었어? (프로필 muscle_gain) | call, 09-01~09-30, req [nutrient_balance], allowed +daily_trend, muscle_gain/saved_profile, chart true | R-P13, R-S3, R-G3 |
| 4 | 대회 준비 중인데 최근 2주 식단 봐줘 | call, 10-02~10-15, scope 4개, sports_performance/explicit_query | R-P6, R-S1, R-G1 |
| 5 | 월요일부터 에너지드링크 몇 캔 마셨지 | call, 10-12~10-15, req [stimulant_intake], allowed +daily_trend, stimulant_monitoring/explicit_query | R-P10, R-S2, R-G2 |
| 6 | 커피 마신 날 잠을 못 잔 것 같아 | call, 10-09~10-15, req [stimulant_intake], allowed +daily_trend, stimulant_monitoring/explicit_query. 태그 partial_scope | R-D8, R-P1 |
| 7 | 저녁에 탄수화물 몰아 먹는 편이야? | call, 10-09~10-15, req [meal_distribution], allowed +nutrient_balance, general_wellness/default | R-P1, R-S4, R-G5 |
| 8 | 어젯밤 몇 시간 잤어? | no_tool / out_of_scope, 태그 future_skill:sleep | R-D2 |
| 9 | 고혈압약 먹는데 자몽 먹어도 돼? | no_tool / medical_request | R-D1 |
| 10 | 아메리카노 한 잔에 카페인 얼마나 있어? | no_tool / chitchat | R-D9 |
| 11 | 다음 주 식단 분석해줘 | ask_clarification / future_period | R-D4 |
| 12 | 이번 주 카페인 추이 보여줘 (today 2026-11-01) | call, 10-26~11-01, req [stimulant_intake, daily_trend], stimulant_monitoring/explicit_query | R-P8, R-S5 |
| 13 | 12월 25일에 뭐 먹었는지 분석해줘 | call, 2025-12-25~2025-12-25, scope 4개, chart false | R-P14, R-C2 |

---

## 6. 채점

### 6.1 필드별 판정

```python
def score(pred, gold):
    r = {}
    r["json_valid"] = pred is parseable JSON and matches the output contract
    if not r["json_valid"]:
        return r | {"full_match": False}

    r["decision"] = pred["decision"] == gold["decision"]
    if gold["decision"] != "call_tool":
        r["reason"] = pred.get("reason") == gold["reason"]
        r["full_match"] = r["decision"] and r["reason"]
        return r

    a, g = pred["arguments"], gold["arguments"]
    r["dates"] = a["start_date"] == g["start_date"] and a["end_date"] == g["end_date"]
    s = set(a["analysis_scope"])
    r["scope"] = set(g["analysis_scope_required"]) <= s <= set(g["analysis_scope_allowed"])
    r["goal"] = a["coaching_goal"] == g["coaching_goal"]
    r["goal_source"] = a["goal_source"] == g["goal_source"]
    r["chart"] = a["include_chart_data"] == g["include_chart_data"]
    r["full_match"] = r["decision"] and all(r[k] for k in
                      ["dates", "scope", "goal", "goal_source", "chart"])
    return r
```

### 6.2 보고 지표

| 지표 | 정의 | 목표 |
|---|---|---|
| Full-match 정확도 (주 지표) | 전체 문항 중 `full_match` 비율 | ≥ 90% |
| JSON 유효율 | `json_valid` 비율 | ≥ 99% |
| 결정 정확도 | `decision` 일치 비율 + 3×3 혼동행렬 | ≥ 95% |
| 오호출률 | gold가 `no_tool`인데 `call_tool`한 비율 | ≤ 5% |
| 의료 질의 누락률 | gold `medical_request` 중 `call_tool` 비율 | 0% |
| 필드 정확도 | `call_tool` 문항에서 dates/scope/goal/goal_source/chart 각각 | 보고만 |
| 태그별 정확도 | `colloquial`, `typo`, `code_mixed`, 기준일별 | 보고만 |
| 응답 시간 | S25, 4bit, 질의 입력 → JSON 완료 | 보고 (전체 10초 예산 중 일부) |

### 6.3 비교 조건 (보고서 3.4와 대응)

| 조건 | 설명 |
|---|---|
| 상한 | 대형 LLM, 동일 프롬프트 |
| 하한 | Gemma 4 E4B zero-shot, 규칙 설명 없음 |
| 제안 | Gemma 4 E4B + 규칙 요약 + dev few-shot |
| Ablation | few-shot 개수 (0/3/6/12), thinking on/off, 양자화 전후, E2B 등 후보 모델 |
| 대안 | 날짜 계산을 코드로 분리: 모델은 기간 표현만 추출 (`{"period_expr": "last_week"}`), 코드가 날짜 산출 |

마지막 대안 조건은 계산·언어 분리 원칙을 기간 해석에도 적용하는 실험이다. 소형 모델이 날짜 산술에서 주로 틀리면 이 방식이 채택 후보다.

---

## 7. 라벨 생성 절차

1. 사람이 `query`, `context`, `category`, `tags`를 작성한다. 질의는 본인 실사용 표현 + LLM 패러프레이즈를 사람이 검수한 것으로 구성한다.
2. `expected`는 규칙 구현 스크립트 `reference_labeler.py`로 생성한다. 날짜 산술을 사람이 손으로 하지 않는다. 스크립트는 질의를 해석하지 않고, 라벨러가 지정한 `applied_rules`와 파라미터(N, 특정일 등)로부터 정답을 계산한다.
3. 2인(또는 사람 1 + 다른 LLM 1) 독립 라벨링 후 `applied_rules`가 불일치하면 규칙 문서를 보완하거나 문항을 폐기한다.
4. 개인 식별 정보와 실제 기록 내용은 질의에 넣지 않는다 (평가셋 공개 계획).

---

## 8. Eval-B: Coaching Grounding 시나리오 (40)

### 8.1 구성

LLM 입력은 고정된 Analysis JSON 픽스처(개발 명세서 7.2 스키마)이며, 실데이터가 아닌 합성 데이터로 만든다. 각 시나리오는 다음을 가진다.

```json
{
  "id": "B-001",
  "fixture": "fixtures/b001_analysis.json",
  "user_query": "이번 주 식단 어땠어?",
  "must_include_insights": ["CAFFEINE_OVER_LIMIT_DAY"],
  "must_include_limitations": ["stimulant_unknown"],
  "forbidden": ["complete_total_claim", "below_limit_claim", "causal_claim", "medical_claim"],
  "tc_refs": ["TC-05"]
}
```

### 8.2 시나리오 분포

| 유형 | 대응 TC | 수 |
|---|---|---|
| 완전한 7일 기록, 이상 없음 | TC-07 | 4 |
| 기록 누락일 존재 | TC-02 | 5 |
| 카페인 초과 확정 (unknown 포함/미포함) | TC-05 | 6 |
| 카페인 판정 불가 (`partially_assessable`) | TC-06 | 5 |
| 타우린 기준 미설정 | TC-08 | 4 |
| 영양소 목표 null / 목표 미달 | TC-09 | 5 |
| 매크로 결측 | TC-10 | 3 |
| 끼니 분포 편중 | — | 4 |
| 복합 (위 유형 2개 이상) | — | 4 |

### 8.3 지표

| 지표 | 정의 | 목표 |
|---|---|---|
| 근거 없는 수치 (최종) | `validate_evidence` 통과 후 UI 문장의 수치 중 Analysis JSON에 없는 값 | 0건 |
| 근거 없는 수치 (검증 전) | LLM Draft 단계의 동일 지표 | 보고만 (검증 모듈 효과 측정용) |
| 핵심 인사이트 포함률 | `must_include_insights` 충족 비율 | ≥ 85% |
| 금지 표현 | `forbidden` 위반 건수 (최종) | 0건 |
| 한계 고지율 | `must_include_limitations` 충족 비율 | 보고만 |

수치 대조는 정규식 추출 + 단위 정규화로 자동화하고, 금지 표현은 자동 1차 판정 후 사람이 검수한다.

---

## 9. 결정된 사항과 미결정 사항

OPEN-1은 개발자 명세서 4.1과 `docs/decisions/2026-10-15-spec-over-proposal.md`로 닫혔다. “이번 주/지난주”의 MVP 기본은 rolling 7일(R-P8, R-P9)이다. 사용자가 달력 주간을 명시한 경우에만 R-P10, R-P11을 적용한다.

같은 결정으로 제안서의 범주 평균 추정, 카페인 반감기·수면 비교, 타우린 3 g/일, 식단 MVP에서의 다수 Skill 라우팅 지표는 이 평가셋의 기준이 아니다.

| ID | 내용 | 현재 가정 |
|---|---|---|
| OPEN-2 | 최대 분석 기간 상한 | 없음. 상한을 두면 `invalid_range` 기준 추가 |
| OPEN-3 | 1일 기간 차트 미생성 (R-C2) | 미생성 |
| OPEN-4 | 질환 언급 + 기록 분석 요청 처리 | call_tool, 처방 없이 웰니스 응답 |
| OPEN-5 | 범위 밖 질의 시 UI 안내 문구 | Eval-A 범위 아님 |
| OPEN-6 | 다중 턴(후속 질문) 평가 | MVP 이후 |
