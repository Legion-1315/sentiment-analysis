# SentiSense — Consumer Sentiment & State-of-Mind Analysis

[![CI](https://github.com/Legion-1315/sentiment-analysis/actions/workflows/ci.yml/badge.svg)](https://github.com/Legion-1315/sentiment-analysis/actions/workflows/ci.yml)

🔗 **Live demo:** _add your Render URL here after deploying_ · **Source:** https://github.com/Legion-1315/sentiment-analysis

> Full-stack AI app: Spring Boot (Java 21, Gradle) REST API with a hybrid
> lexicon + neural sentiment engine, and a React + Vite dashboard. Deployed as a
> single Docker container where Spring Boot also serves the built React app.

Every online business wants to know how consumers react to its content.
SentiSense analyzes any consumer text — a review, tweet, comment or support
message — and predicts:

- **Sentiment label**: POSITIVE / NEGATIVE / NEUTRAL with a score in [−1, 1]
- **Consumer mind state**: 🤩 Delighted · 🙂 Satisfied · 😐 Undecided ·
  🙁 Dissatisfied · 😡 Frustrated — each with a recommended business action
- **Purchase intent** (0–100) and a **confidence** estimate
- The exact **words that drove the verdict**, with their valence

## How the AI works

Two analyzers vote in an ensemble:

| Analyzer | Approach | Strength |
|----------|----------|----------|
| Lexicon (VADER-style rules) | 7,520-term valence lexicon + rules for negation, intensifiers ("extremely"), ALL-CAPS, "!!!" and "but"-clauses | Intensity, emoji, no training needed |
| Neural classifier | Single-layer network (logistic regression) over unigrams+bigrams with negation marking, trained at startup on 3,000 real Amazon/IMDb/Yelp review sentences (UCI dataset) | Domain phrasing learned from real consumer data |

The ML vote is weighted by how much of the text's vocabulary it saw during
training, so unfamiliar text gracefully falls back to the lexicon.

**Measured on 300 held-out review sentences:** lexicon alone 76%, ML alone 81%,
**ensemble 85% accuracy**.

## Quick start

Prerequisites: Java 21+ and Node 18+ (Gradle comes bundled via the wrapper).

```bash
# Terminal 1 — backend API on http://localhost:8080
cd backend
./gradlew bootRun            # Windows: .\gradlew.bat bootRun

# Terminal 2 — web app on http://localhost:5173
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 — three tabs:

1. **Live Analyzer** — analyze one text, see the gauge, mind state, purchase
   intent and keyword attribution.
2. **Batch Analysis** — paste up to 200 texts (one per line), get a summary
   donut + per-text results table.
3. **Dashboard** — aggregate stats, sentiment distribution, mind-state chart,
   model metrics and analysis history (persisted in H2).

## API example

```bash
curl -X POST http://localhost:8080/api/sentiment/analyze \
  -H "Content-Type: application/json" \
  -d '{"text": "Absolutely LOVE this phone, best purchase ever!!!"}'
```

```json
{
  "label": "POSITIVE",
  "mindState": "DELIGHTED",
  "mindStateMeaning": "Enthusiastic advocate — likely to buy and recommend",
  "score": 0.9366,
  "confidence": 0.9434,
  "purchaseIntent": 97,
  "lexiconScore": 0.9062,
  "mlProbability": 0.9971,
  "keywords": [{"token": "LOVE", "score": 4.226}, {"token": "best", "score": 3.2}]
}
```

## Testing

```bash
cd backend
./gradlew test                          # 38 tests: unit + API integration
./gradlew test --tests "com.sentisense.engine.EvaluationReportTest"   # analyzer comparison
```

### Feature verification (all pass)

| Feature | Example | Result |
|---------|---------|--------|
| Negation | "The camera is **not good** at all" | NEGATIVE ✔ |
| Emoticons | "just got my order **:(**" | NEGATIVE ✔ |
| "but"-clause | "The design is nice **but** the battery is awful" | NEGATIVE ✔ |
| Emphasis | "Absolutely **LOVE** this phone…**!!!**" | score 0.94, DELIGHTED ✔ |
| Validation | blank text / empty batch | HTTP 400 ✔ |

### Honest error analysis

"Customer support never replied to my emails!!!" is misclassified as mildly
positive: *support* is a positive lexicon term and *replied* carries no
valence for the negation to flip. Negated-neutral-verb phrases are the
engine's main known weakness — a future aspect-based model would address it.

## Deployment (free, one Docker service)

The whole app ships as a single container: a multi-stage [`Dockerfile`](Dockerfile)
builds the React app, bundles it into Spring Boot's static resources, and runs
one JVM that serves both the UI and the `/api` endpoints — one URL, no CORS.

### Deploy to Render (free tier, no credit card)

1. Push this repo to GitHub (see below).
2. Sign in to [render.com](https://render.com) with GitHub.
3. **New + → Blueprint**, pick the `sentiment-analysis` repo. Render reads
   [`render.yaml`](render.yaml) and provisions the `sentisense` web service.
4. Click **Apply**. First build takes ~5–8 min; the app is then live at
   `https://sentisense-XXXX.onrender.com`. Put that URL at the top of this README.

Notes for the free plan:
- The service **sleeps after 15 min idle**; the next visit cold-starts in
  ~50 s. To keep it warm for a demo, add a free cron ping (e.g.
  [cron-job.org](https://cron-job.org)) hitting `/api/sentiment/model-info`
  every 10 min.
- The H2 database is on ephemeral disk, so analysis **history resets on
  redeploy** — expected for a demo.

### Run the container locally

```bash
docker build -t sentisense .
docker run -p 8080:8080 sentisense   # open http://localhost:8080
```

## Credits

- VADER lexicon — Hutto & Gilbert (2014), MIT license
- UCI "Sentiment Labelled Sentences" dataset — Kotzias et al. (2015)
- Inspired by the GeeksforGeeks RNN sentiment-analysis tutorial
