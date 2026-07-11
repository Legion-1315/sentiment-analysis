# SentiSense — Consumer Sentiment Analysis

AI-powered sentiment predictor that identifies a consumer's state of mind from
free text (reviews, tweets, support messages), inspired by the classic
RNN-for-sentiment exercise but engineered as a production-style full-stack app.

## Architecture

```
sentiment-analysis/
├── backend/    Spring Boot 3.5 (Java 21+, Gradle) REST API, port 8080
└── frontend/   React 18 + Vite dashboard, port 5173 (proxies /api → 8080)
```

### Sentiment engine (backend/src/main/java/com/sentisense/engine/)

Hybrid ensemble of two analyzers, combined in `SentimentEngine`:

1. **`LexiconAnalyzer`** — rule-based, VADER-style. Uses the MIT-licensed VADER
   lexicon (`resources/data/vader_lexicon.txt`, 7.5k terms rated −4..+4).
   Implements negation flipping (×−0.74), booster/dampener intensifiers with
   distance decay, ALL-CAPS emphasis, exclamation amplification, and
   contrastive-"but" clause reweighting. Compound score normalized to [−1, 1]
   via `x/√(x²+15)`.
2. **`MlClassifier`** — single-layer neural classifier (logistic regression,
   SGD, 40 epochs, seed 42) over unigram+bigram features with `_NEG` negation
   scope marking. Trained **at startup (~700 ms)** on the bundled UCI
   "Sentiment Labelled Sentences" corpus (3,000 Amazon/IMDb/Yelp review
   sentences in `resources/data/*_labelled.txt`). Stratified 90/10 split;
   held-out metrics exposed at `/api/sentiment/model-info`.

Ensemble: ML vote is weighted by its **feature coverage** (fraction of the
text's n-grams seen in training), so out-of-domain text falls back to the
lexicon. Final score maps to label (±0.10 thresholds) and to a consumer
**mind state**: DELIGHTED / SATISFIED / UNDECIDED / DISSATISFIED / FRUSTRATED,
plus a 0–100 purchase-intent heuristic.

3. **`AspectSentimentAnalyzer`** — aspect-based sentiment (ABSA): per-aspect
   polarity ("camera = positive, battery = negative") instead of one score per
   text. Pipeline: clause segmentation (hard `.;!?\n` boundaries; soft
   comma/conjunction splits with aspect-less segments merged into the
   neighbouring aspect clause) → aspect detection against a curated taxonomy
   (`resources/data/aspect_lexicon.txt`, ~130 terms → 19 canonical aspects,
   naive plural stripping) → clause scoring via `LexiconAnalyzer` **with the
   aspect terms excluded from valence** → mild negative prior (−0.35) for
   clauses whose only signal is an unmatched negation. Returned in `aspects`
   on every analysis; `/batch` aggregates them into per-aspect rows
   (texts, avg score, pos/neg/neu split — top 12).

### Persistence

H2 file database at `backend/data/` (gitignored). Every analysis is saved as
an `AnalysisRecord`; `/stats` aggregates by label and mind state.

## API (base: http://localhost:8080/api/sentiment)

| Method | Path          | Purpose                                        |
|--------|---------------|------------------------------------------------|
| POST   | `/analyze`    | `{text, source?}` → full analysis (persisted)  |
| POST   | `/batch`      | `{texts: [...], source?}` → summary + per-item |
| GET    | `/history`    | Paged history (`page`, `size`)                 |
| DELETE | `/history`    | Clear history                                  |
| GET    | `/stats`      | Aggregates: counts, avg score, avg intent      |
| GET    | `/model-info` | ML training/eval metrics                       |

Validation: text ≤ 4000 chars, batch ≤ 200 items; errors return
`{error, details[]}` with HTTP 400 (`ApiExceptionHandler`).

## Commands

```powershell
# Backend (from backend/) — Gradle wrapper, no local Gradle install needed
.\gradlew.bat bootRun        # start API on :8080
.\gradlew.bat test           # all 38 tests
.\gradlew.bat test --tests "com.sentisense.engine.EvaluationReportTest"  # analyzer comparison
.\gradlew.bat bootJar        # executable jar in build/libs/

# Frontend (from frontend/)
npm install
npm run dev                  # dev server on :5173
npm run build                # production build check
```

Build: Gradle 9.2 via wrapper, plugins `org.springframework.boot` 3.5.7 +
`io.spring.dependency-management` 1.1.7; compiles with `--release 21` on any
newer installed JDK (machine has JDK 25). Migrated from Maven 2026-07-08.

## Test & evaluation status (2026-07-10)

- 47 JUnit tests, all green: tokenizer, lexicon rules, classifier, ensemble,
  aspect analyzer (`AspectSentimentAnalyzerTest`), MockMvc API integration
  (`SentimentApiTest`), evaluation report.
- Held-out accuracy (300 unseen review sentences): lexicon-only **76%**,
  ML-only **81%** (F1 0.805), **ensemble 85%** — the hybrid measurably beats
  both components.

## Known limitations

- Negation of *neutral* verbs isn't caught by the lexicon at the **whole-text**
  level: "support never replied to my emails" scores positive ("support" is a
  positive lexicon term; "replied" carries no valence to flip). The
  **aspect-level** result is correct — `AspectSentimentAnalyzer` excludes the
  aspect term's own valence and applies the unmatched-negation prior, so the
  `service` aspect reads NEGATIVE. Documented in README error analysis.
- English only; sarcasm and implicit sentiment remain hard.
- Model retrains on every backend start (cheap, deterministic via seed 42) —
  no weight persistence.

## Design decisions

- Pure-Java ML instead of a Python/TensorFlow sidecar: zero extra runtime
  deps, sub-second training, fully testable in JUnit. The RNN of the original
  article is approximated by negation-scope-marked n-grams, which capture the
  main sequential signal at this corpus size.
- Deterministic seed (42) everywhere so tests and metrics are reproducible.
- Vite dev proxy handles CORS in dev; `CorsConfig` allows :5173/:3000 anyway.

## Deployment

Single-container design: multi-stage [`Dockerfile`](Dockerfile) builds React →
copies `dist/` into Spring Boot `resources/static/` → `bootJar` (`-x test`) →
runs on `eclipse-temurin:21-jre`. Spring serves the SPA at `/` and the API at
`/api/**` on the **same origin**, so production needs no CORS and the frontend's
relative `/api` calls work unchanged. `server.port=${PORT:8080}` honors the
host's injected port. Verified locally (bundled static served correctly)
2026-07-08; Docker/Render not built on this machine (no Docker installed).

- **Host:** Render free tier via [`render.yaml`](render.yaml) Blueprint
  (`runtime: docker`, `plan: free`, health check `/api/sentiment/model-info`).
  Prod overrides `SPRING_DATASOURCE_URL` to drop H2 `AUTO_SERVER`.
  **Live:** https://sentiment-analysis-n0a7.onrender.com (deployed & verified
  2026-07-08 — SPA, model-info, and analyze all return 200).
- **Repo:** github.com/Legion-1315/sentiment-analysis (public).
- **Free-tier caveats:** sleeps after 15 min idle (~50 s cold start). Mitigated
  by [`keep-warm.yml`](.github/workflows/keep-warm.yml) — a scheduled GitHub
  Actions ping every ~10 min, 2–17 UTC only (≈ 7:30–23:20 IST) so the two
  Render apps stay inside the workspace's 750 free instance-hours/month.
  GitHub pauses cron workflows after 60 days without commits. H2 on ephemeral
  disk so history resets on redeploy.
- **`.dockerignore`** excludes build outputs, `node_modules`, `.git`, `data/`.
- The bundled `resources/static/` dir is created only inside the Docker build —
  it is **not** committed to the repo.

## Possible enhancements

- Emotion categories (joy/anger/fear); CSV file upload for batch; export
  reports; weight persistence; multilingual lexicons; transformer upgrade via
  DJL (note: ~250 MB ONNX weights won't fit Render's 512 MB free tier —
  needs a paid tier or external inference).
- Aspect-based sentiment **shipped 2026-07-10** (see engine section above).
