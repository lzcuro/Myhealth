# Baymax

개인 생체데이터를 기기 안에서 모아 분석하는 온디바이스 헬스케어 AI다. 이 저장소 루트가 프로젝트 루트이며, Python 패키지 이름은 `baymax`다.

수치 기준과 스키마는 `config/`에 두고 Python과 Kotlin이 같이 읽는다. 모델이 말한 건강 수치는 `src/baymax/grounding`에서 도구 출력·DB 근거와 맞춘 뒤에만 사용자에게 나간다.

식단 구현의 기준 문서는 `docs/specs/diet_coaching_agent_spec_v1.0.docx`와 `docs/specs/diet_mvp_eval_set_spec.md`다. `docs/proposal/service_proposal.docx`는 이전 제안서이며, 식단 규칙이 겹치면 `docs/decisions/2026-10-15-spec-over-proposal.md`가 우선한다.

## 디렉토리

```
.
├── README.md
├── AGENTS.md                      # AI 작업 진입점
├── .gitignore
├── pyproject.toml
│
├── docs/
│   ├── proposal/                  # service_proposal.docx (이전 제안서, 보관)
│   ├── specs/                     # diet_coaching_agent_spec_v1.0.docx, diet_mvp_eval_set_spec.md
│   └── decisions/                 # 결정 기록 (파일 1개 = 결정 1개)
│
├── config/                        # 언어 중립 설정 (Python·Kotlin 공용)
│   ├── consumption_limits.yaml    # 카페인 등 섭취 상한, 출처·버전 포함
│   ├── insight_rules.yaml
│   ├── schemas/                   # JSON Schema
│   │   ├── tool_args.schema.json
│   │   ├── analysis_v1.schema.json
│   │   └── coaching_draft.schema.json
│   └── models/                    # 모델 1개 = 파일 1개
│       ├── gemma4-e4b-q4.yaml
│       ├── gemma4-e2b-q4.yaml
│       ├── qwen3.5-4b-q4.yaml
│       ├── exaone4.0-1.2b-q4.yaml
│       └── upper-bound-api.yaml
│
├── data/                          # catalog/ 외에는 gitignore
│   ├── raw/samsung_health/        # 내보내기 원본 (수정 금지)
│   ├── interim/                   # 정규화 중간 산출물
│   ├── db/health.sqlite           # 3계층 Health DB (생성물)
│   └── catalog/
│       └── stimulant_products.json
│
├── models/                        # 가중치 (gitignore)
│
├── src/baymax/
│   ├── etl/                       # 파싱, 귀속일, dedup, coverage
│   ├── db/                        # 스키마, repository
│   ├── skills/
│   │   └── diet/                  # period, normalize, stimulant, analysis, rules, executor
│   ├── agent/
│   │   ├── prompts/               # 모델 무관 규칙 요약·few-shot (버전 파일)
│   │   ├── adapters/              # gemma.py, qwen.py, exaone.py
│   │   ├── backends/              # base, llama_cpp, transformers_hf, litert_lm, api
│   │   └── parse.py               # 출력 JSON 파싱·스키마 검증
│   └── grounding/                 # validate_evidence
│
├── eval/
│   ├── eval_a/                    # data/dev.jsonl, data/test.jsonl, reference_labeler.py, score.py
│   ├── eval_b/                    # fixtures/, scenarios.jsonl, score.py
│   ├── experiments/               # e01_baseline.yaml, e02_fewshot_ablation.yaml
│   ├── runs/                      # 아래 이름 규칙. predictions.jsonl만 gitignore
│   └── reports/leaderboard.md
│
├── device/                        # S25 실측, 삼성 헬스 읽기 요구사항
│   ├── scripts/
│   ├── samsung-health-read-requirements.txt
│   └── results/                   # raw/ 는 gitignore
│
├── tests/
│   ├── unit/                      # TC-01~14
│   └── golden/                    # 입력 → 기대 Analysis JSON
│
├── scripts/                       # run_etl.py, run_eval.py 등 CLI
├── notebooks/
└── android/
    └── samsung-health-data-sdk-1.1.0/
        ├── 1.1.0/                 # Samsung Health Data SDK 1.1.0
        └── HealthDiary/          # 수면·식단을 JSON으로 저장하는 시험 앱
```

빈 디렉터리는 `.gitkeep`으로 자리를 잡아 두었다. `src/baymax` 아래 Python 모듈은 구현할 때 만든다. `data/db/health.sqlite`와 `eval/runs/<...>/`도 실행 결과로 생긴다.

## 영역별 역할

### 문서 — `docs/`

- `proposal/service_proposal.docx`는 이전 제안서다. 식단 명세와 겹치는 문장은 결정 기록이 대체한다. 러닝 코치, 기기 경로, S25 성능 목표는 이 파일에 남아 있다.
- `specs/diet_coaching_agent_spec_v1.0.docx`는 식단 코칭 개발자 명세서 v1.0이다. 예전 파일명은 `diet_mvp_eval_set_spec.docx`였고, 본문에 맞게 바꿨다.
- `specs/diet_mvp_eval_set_spec.md`는 Eval-A·Eval-B의 라벨·채점 기준이다.
- `decisions/`는 결정 하나당 파일 하나다. 파일명은 `YYYY-MM-DD-짧은-제목.md`로 한다.

### 설정 — `config/`

언어 중립이다. Python과 Kotlin이 같은 YAML·JSON을 읽으므로 이 디렉터리에 코드나 언어별 타입을 넣지 않는다.

- `consumption_limits.yaml`은 명세서 8.2의 식약처 카페인 한도다. 성인 400 mg/일, 임산부 300 mg/일, 어린이·청소년 2.5 mg/kg/일. 대상군이 확인될 때만 해당 id를 연결한다.
- `insight_rules.yaml`은 명세서 8의 규칙 id다. 한도 숫자는 limits 파일의 id로 참조한다. 끼니 분포 규칙의 수치 조건은 명세서에 없어 비어 있다.
- `schemas/`는 도구 인자(`tool_args`), 식단 분석(`analysis_v1`), 코칭 초안(`coaching_draft`)의 JSON Schema다. 필드 계약은 명세서 4.3, 7.2, 9.3이고, 스키마 파일은 그 절을 옮기기 전까지 검증 게이트로 쓰지 않는다.
- `models/`는 모델 카드다. 가중치 경로는 `models/`(저장소 루트)에 있고, 카드에는 id, 계열, 양자화, 백엔드만 적는다.

### 데이터 — `data/`

| 경로 | 내용 | git |
| --- | --- | --- |
| `raw/samsung_health/` | Samsung Health 내보내기 원본 | 무시 |
| `interim/` | 정규화 중간 산출물 | 무시 |
| `db/health.sqlite` | 3계층 Health DB | 무시 |
| `catalog/stimulant_products.json` | 각성 성분 카탈로그. 정확 일치만 `verified` | 커밋 |

원본 내보내기는 수정하지 않는다. 고칠 값이 있으면 `interim/`이나 DB로 옮긴 뒤 고친다.

SDK로 시험 삼아 읽은 최근 7일 파일은 `data/raw/samsung-health-week.json`이다. 내보내기 원본 폴더와 따로 두며, 역시 Git에는 올리지 않는다.

### 가중치 — `models/`

GGUF 등 가중치 파일 전용이다. 커밋하지 않는다. 어떤 파일인지, 해시는 무엇인지는 `config/models/*.yaml`과 평가 run의 `manifest.json`에 적는다.

### 소스 — `src/baymax/`

- `etl/`: Samsung Health 파싱, 귀속일, 중복 제거, 커버리지.
- `db/`: SQLite 스키마와 repository.
- `skills/diet/`: 기간 자르기, 정규화, 각성 성분, 분석 JSON, 규칙, 실행기.
- `agent/prompts/`: 모델과 무관한 규칙 요약과 few-shot. 버전마다 파일을 나눈다.
- `agent/adapters/`: Gemma, Qwen, EXAONE의 템플릿과 출력 형식 차이만 둔다.
- `agent/backends/`: 공통 인터페이스와 실행 엔진. PC 양자화는 `llama_cpp`, PC 비양자화는 `transformers_hf`, S25는 `litert_lm`, 품질 상한은 `api`.
- `agent/parse.py`: 모델 출력에서 JSON을 파싱하고 스키마를 검증한다.
- `grounding/`: `validate_evidence`. 분석 JSON에 없는 수치를 코칭 문장에 남기지 않기 위한 계층이다.

### 평가 — `eval/`

- `eval_a/`: 라벨이 있는 평가. `data/dev.jsonl`, `data/test.jsonl`, `reference_labeler.py`, `score.py`.
- `eval_b/`: 합성 Analysis JSON(`fixtures/`)과 `scenarios.jsonl`로 코칭을 점수화한다.
- `experiments/`: 모델 × 조건 조합. `e01_baseline.yaml`은 few-shot 0인 기준선, `e02_fewshot_ablation.yaml`은 예시 개수만 바꾼다.
- `runs/`: 실험 결과. 디렉터리 이름은 `<YYYYMMDD>_<모델>_<백엔드>_<프롬프트>_<fs수>/` 이다.
  - `manifest.json`: 모델 해시, 설정, git commit
  - `predictions.jsonl`: 원문 출력, 파싱 결과, 지연시간 (gitignore)
  - `scores.json`: 집계 점수 (커밋 가능)
- `reports/leaderboard.md`: `runs/`의 `scores.json`으로 다시 만든다. 순위를 손으로 고치지 않는다.

### 기기 — `device/`

Galaxy S25에서 지연시간, 피크 메모리, 배터리를 재는 스크립트와 결과다. `results/raw/`는 커밋하지 않는다. 노트북에서 삼성 헬스 읽기를 다시 맞출 때 필요한 PC·휴대폰 조건은 `samsung-health-read-requirements.txt`에 있다.

### 테스트 — `tests/`

- `unit/`: TC-01부터 TC-14.
- `golden/`: 같은 입력에 대해 기대하는 Analysis JSON. Kotlin으로 옮길 때 이 골든과 출력이 같아야 한다.

### 나머지

- `scripts/`: `run_etl.py`, `run_eval.py` 같은 CLI 진입점.
- `notebooks/`: 탐색용. 여기 있는 계산을 제품 경로의 기준으로 쓰지 않고, 확정되면 `src/`와 `config/`로 옮긴다.
- `android/`: 온디바이스 제품 앱은 6주차부터 구현한다. 그 전에 들어 있는 `HealthDiary`는 삼성 샘플을 고친 읽기 시험용이다.

## 삼성 헬스에서 최근 7일 수면·식단 읽기

삼성 헬스 데이터 SDK는 휴대폰 안의 앱이 삼성 헬스 저장소를 읽는 라이브러리다. PC에 USB로 연결되었다고 해서 PC가 헬스 DB를 직접 조회하지는 못한다. DataViewer(`1.1.0/tool/DataViewer_1.1.0.apk`)와 Health Diary의 원래 화면은 기록을 휴대폰에 보여 주기만 한다. PC 파일로 남기는 경로는 `HealthDiary`에 추가한 `ExportWeekActivity`다.

노트북에서 같은 순서를 밟을 때의 설치 목록과 명령은 `device/samsung-health-read-requirements.txt`에 모아 두었다. 아래는 그 절차가 하는 일이다.

1. 휴대폰에서 USB 디버깅을 켜고, PC의 RSA 키를 허용한다. `adb devices`에 일련번호와 `device`가 보여야 한다. `unauthorized`이면 아직 허용 전이다.
2. 삼성 헬스 6.30.2 이상에서 개발자 모드를 연다. 설정 > 삼성 헬스 정보에서 버전 줄을 10번 이상 누르면 Developer mode (Samsung Health Data SDK)가 생긴다. 읽기 시험에는 Developer mode for Data Read를 켠다.
3. PC에는 JDK 17, Android SDK 34(platform `android-34`, build-tools `34.0.0`), platform-tools의 `adb`가 필요하다. `JAVA_HOME`은 JDK 17을 가리켜야 한다. SDK 경로는 `HealthDiary/local.properties`의 `sdk.dir`에만 적고 커밋하지 않는다. Windows용 `adb`를 받아 둔 `device/platform-tools/`도 커밋하지 않는다.
4. `android/samsung-health-data-sdk-1.1.0/HealthDiary`에서 `gradlew.bat assembleDebug`로 디버그 APK를 만든다. 결과 파일 이름은 `Health Diary-Debug1.1.0.apk`다.
5. `adb install -r`로 설치하고 `ExportWeekActivity`를 실행한다. 패키지 이름은 `com.samsung.android.health.sdk.sample.healthdiary`다. 수면과 식단 읽기 동의는 이 패키지에 대해 따로 받는다. DataViewer에서 이미 허용했어도 이 앱에는 다시 뜰 수 있다.
6. 액티비티는 실행 시각 기준 최근 7일(`now.minusDays(7)`부터 `now`까지)을 읽는다. 수면은 `DataTypes.SLEEP`, 식단은 `DataTypes.NUTRITION`이다. 수면 기록에는 세션과 수면 단계를 포함하고, 식단 기록에는 끼니, 음식명, 칼로리, 영양소, 출처 앱·기기를 포함한다.
7. 앱은 결과를 자신의 내부 저장소 `files/samsung-health-week.json`에 쓴다. 디버그 빌드라 `adb exec-out run-as <패키지> cat files/samsung-health-week.json`으로 PC의 `data/raw/samsung-health-week.json`에 저장한다. `files/status.txt`가 `OK sleep=<건수> nutrition=<건수>`이면 읽기가 끝난 것이다. `NO_PERMISSION`이면 휴대폰에서 동의가 끝나지 않은 것이다.

이 PC에서 한 시험 읽기의 구간은 2026-10-19 13:29부터 2026-10-26 13:29까지였고, 수면 7건, 식단 37건이었다. 그 JSON은 `data/raw/`에만 둔다.

## 로컬에서 무시되는 것

`.gitignore`는 `data/*`를 무시하고 `data/catalog/`만 다시 포함한다. `models/`, `eval/runs/*/predictions.jsonl`, `device/results/raw/`, `device/platform-tools/`도 무시한다. `*.sqlite`, `*.gguf`는 경로가 바뀌어도 커밋되지 않는다. Android의 `.gradle/`과 `local.properties`도 커밋하지 않는다.
