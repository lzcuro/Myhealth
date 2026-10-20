# AGENTS.md

Baymax에서 AI가 작업을 시작할 때 처음 읽는 파일이다. 구현 순서는 이 파일 → `README.md` → `docs/decisions/` → `docs/specs/` → `config/` 다.

## 기준 문서

식단 Skill의 기준은 `docs/specs/diet_coaching_agent_spec_v1.0.docx`와 `docs/specs/diet_mvp_eval_set_spec.md`다. `docs/proposal/service_proposal.docx`는 이전 제안서다. 둘이 겹치면 `docs/decisions/2026-10-15-spec-over-proposal.md`를 따른다. 제안서가 다루는 러닝 코치, 기기 경로, S25 지연·메모리 목표는 식단 명세 밖에 있으므로 그 문장을 구현 범위로 유지한다.

## 문서 위치

| 무엇 | 어디 |
| --- | --- |
| 디렉토리 설명 | `README.md` |
| 이전 제안서 (보관) | `docs/proposal/service_proposal.docx` |
| 식단 코칭 개발자 명세서 v1.0 | `docs/specs/diet_coaching_agent_spec_v1.0.docx` |
| 식단 MVP 평가셋 명세 | `docs/specs/diet_mvp_eval_set_spec.md` |
| 결정 기록 | `docs/decisions/YYYY-MM-DD-짧은-제목.md` |
| 섭취 상한, 인사이트 규칙 | `config/consumption_limits.yaml`, `config/insight_rules.yaml` |
| JSON Schema | `config/schemas/` |
| 모델 카드 | `config/models/<모델id>.yaml` |
| 각성 성분 카탈로그 | `data/catalog/stimulant_products.json` |
| 실험 정의 | `eval/experiments/` |
| 리더보드 | `eval/reports/leaderboard.md` |

필드 계약의 기준은 개발자 명세서 4.3, 7.2, 9.3이다. `config/schemas/`는 그 절을 옮기기 전까지 버전 필드만 가진 초안이며, 검증 게이트로 쓰지 않는다.

## 작업 규칙

1. 섭취 상한의 숫자, 출처, 버전은 `config/consumption_limits.yaml`에만 둔다. 규칙 파일·프롬프트·Kotlin에 같은 숫자를 복사하지 않고 limit id로 참조한다.
2. `config/`는 Python과 Kotlin이 같이 읽는다. 이 디렉터리에 언어별 코드, 주석으로만 존재하는 계약, 비밀값을 넣지 않는다.
3. 프롬프트(`src/baymax/agent/prompts/`)는 모델과 무관하게 쓴다. 채팅 템플릿과 출력 형식 차이는 `src/baymax/agent/adapters/`에만 둔다.
4. 백엔드(`llama_cpp`, `transformers_hf`, `litert_lm`, `api`)는 `src/baymax/agent/backends/base.py`의 공통 인터페이스 뒤에 둔다. 실험 코드가 특정 엔진의 API를 직접 호출하지 않게 한다.
5. 사용자에게 보여주는 건강 수치는 분석 JSON 또는 DB에 있는 값만 쓴다. 초안은 `coaching_draft`, 근거 확인은 `src/baymax/grounding`의 `validate_evidence`를 거친다.
6. 결정이 바뀌면 `docs/decisions/`에 파일을 추가한다. 이전 결정 파일을 고쳐 덮어쓰지 않는다.
7. 평가 run 디렉터리 이름은 `<YYYYMMDD>_<모델>_<백엔드>_<프롬프트>_<fs수>/` 로 하고, `manifest.json`에 모델 해시, 사용한 설정, git commit을 남긴다.
8. `eval/reports/leaderboard.md`는 `eval/runs/*/scores.json`에서 생성한다.
9. 골든 테스트(`tests/golden/`)의 기대 Analysis JSON이 바뀌면 명세와 스키마를 먼저 갱신한다. Kotlin 이식은 이 골든과 같은 출력을 기준으로 한다.
10. `android/`는 6주차부터 구현한다. 그 전에 앱 코드, Gradle, 패키지 구조를 만들지 않는다.

## 금지

- `data/raw/samsung_health/`의 내보내기 원본을 수정·재저장·포맷 변환하지 않는다.
- `data/`에서 `catalog/` 이외의 파일, `models/`의 가중치, `eval/runs/*/predictions.jsonl`, `device/results/raw/`를 커밋하지 않는다.
- Samsung Health 원문, SQLite, 중간 산출물, 평가 원문 출력을 외부 API·클라우드 동기화·이슈 첨부로 보내지 않는다. `upper-bound-api`도 개인 건강 원문을 받는 경로가 아니다. 그 백엔드를 쓰려면 호출 범위와 데이터 최소화를 `docs/decisions/`에 먼저 남긴다.
- 진단, 처방, 복용 중단, 질환 단정을 코칭 문장으로 만들지 않는다. 상한 초과는 `consumption_limits.yaml`에 적힌 출처의 참조 비교로만 말한다.
- 체중이 없을 때 mg/kg 한도를 적용하거나, 성인·임신·청소년 한도를 서로 대체하지 않는다.
- 카탈로그에 브랜드·제품명·용량이 정확히 없는 각성 성분은 `unknown`으로 둔다. 범주 평균, 근사치, 모델 추정으로 mg를 채우지 않는다.
- `weekly_diet_analysis`에 카페인 반감기, 취침 시 잔존량, 수면일 비교를 넣지 않는다. 타우린은 `consumption_limits.yaml`에 승인된 id가 생기기 전에 초과 판정을 만들지 않는다.
- 식단 MVP의 주 지표는 Eval-A full-match다. 제안서의 다수 Skill 라우팅 정확도를 이 평가의 점수로 쓰지 않는다.
- 노트북의 임시 계산을 제품 기준값으로 남기지 않는다. 기준이 되면 `config/`로 옮기고 출처를 적는다.

## 손대지 않는 생성물

- `data/db/health.sqlite`는 ETL·DB 계층이 만든다.
- `eval/eval_a/data/dev.jsonl`, `test.jsonl`은 평가셋 명세가 있은 뒤 채운다.
- `src/baymax/**/*.py`는 이 뼈대에 포함하지 않았다. 패키지를 구현할 때 디렉터리 이름(`etl`, `db`, `skills/diet`, `agent`, `grounding`)을 유지한다.
