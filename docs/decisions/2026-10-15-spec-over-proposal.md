# 식단 구현 기준은 개발자 명세서

- 날짜: 2026-10-15
- 상태: 채택
- 기준 문서: `docs/specs/diet_coaching_agent_spec_v1.0.docx` (식단 코칭 에이전트 개발자 명세서 v1.0), `docs/specs/diet_mvp_eval_set_spec.md`
- 보관 문서: `docs/proposal/service_proposal.docx`

## 결정

식단 Skill, 각성 성분 카탈로그, Insight Rules, Grounding, Eval-A·Eval-B는 개발자 명세서와 평가셋 명세를 따른다. 제안서는 그 이전 문서이므로, 아래 네 항목에서는 제안서 문장을 구현에 쓰지 않는다.

제안서 파일은 수정하지 않는다. 러닝 코치, 기기 경로(Watch4 → 삼성 헬스 → Health Connect → 온디바이스 DB), S25 오프라인 10초·피크 메모리 6GB, 8주 일정처럼 식단 명세가 다루지 않는 제품 목표는 제안서에 둔 대로 유지한다.

개발자 명세서 docx의 예전 파일명은 `diet_mvp_eval_set_spec.docx`였다. 본문이 평가셋이 아니라 개발자 명세서라서 `diet_coaching_agent_spec_v1.0.docx`로 바꿨다. 평가셋 본문은 `diet_mvp_eval_set_spec.md`다.

## 채택한 규칙

| 주제 | 구현에 쓰는 규칙 | 근거 | 제안서에서 대체된 문장 |
| --- | --- | --- | --- |
| 미매칭 각성 성분 | 브랜드·제품명·용량이 일치할 때만 `verified`. 등록된 `aliases`만 별칭으로 허용. 그 외는 `unknown`. 범주 평균이나 근사치로 채우지 않는다. | 명세서 1, 6.1, 6.3. TC-04 | 3.1 “함량을 모르는 품목은 범주 평균으로 추정하고 추정으로 표시” |
| 카페인과 수면 | `weekly_diet_analysis`는 반감기, 취침 시 잔존량, 섭취일·비섭취일 수면 비교를 계산하지 않는다. 수면 질의는 Eval-A에서 `no_tool` / `out_of_scope` (`future_skill:sleep`). 카페인과 수면의 관계를 원인으로 단정하는 문장은 `validate_evidence`가 완화한다. | 명세서 8, 9.4. 평가셋 R-D2 | 2.1·3.2의 반감기 약 5시간, 취침 시 잔존량, 수면 비교 |
| 타우린 기준 | 승인된 `consumption_limits` 항목이 없으면 섭취량만 집계하고 `over_limit`를 만들지 않는다. `decision_status`는 `intake_tracked_no_threshold_configured`. | 명세서 7.1, 8.2, 9.4. TC-08 | 3.2 “참고치 3 g/일” |
| 식단 MVP 지표 | 도구가 `weekly_diet_analysis` 하나인 동안 주 지표는 Eval-A full-match ≥ 90%다. Eval-B 최종 응답의 근거 없는 수치는 0건, 핵심 인사이트 포함률은 ≥ 85%다. | 평가셋 0, 6.2, 8.3 | 제안서의 “Skill 라우팅 정확도 90% (200문항)”를 식단 MVP 점수에 그대로 쓰지 않음 |

여러 Skill이 생긴 뒤의 라우팅 정확도는 그 시점의 평가셋으로 다시 정의한다. 이번 결정이 그 목표를 제안서에서 지우지는 않는다.

## 설정에 반영한 내용

- `config/consumption_limits.yaml`은 명세서 8.2의 식약처 안내만 둔다. 성인 400 mg/일 (`MFDS_ADULT_CAFFEINE_LIMIT`), 임산부 300 mg/일, 어린이·청소년 2.5 mg/kg/일. 프로필로 대상군이 확인될 때만 해당 id를 연결하고, 미상 대상군에는 성인 한도를 씌우지 않는다.
- `config/insight_rules.yaml`의 규칙 id는 명세서 8절과 같다. 반복 초과는 확인된 초과일이 2일 이상일 때다. 끼니 분포 규칙의 수치 조건은 명세서에 없으므로 비워 둔다.
- `data/catalog/stimulant_products.json`의 매칭 정책은 `exact_match_only`다.

## 평가셋에서 닫은 항목

OPEN-1은 명세서 4.1로 닫는다. “이번 주/지난주”의 MVP 기본은 rolling 7일이고, 사용자가 달력 주간을 명시한 경우에만 달력 주간(평가셋 R-P10, R-P11)을 쓴다.
