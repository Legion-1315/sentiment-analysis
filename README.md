# SentiSense — Consumer Sentiment & State-of-Mind Analysis

[![CI](https://github.com/Legion-1315/sentiment-analysis/actions/workflows/ci.yml/badge.svg)](https://github.com/Legion-1315/sentiment-analysis/actions/workflows/ci.yml)

🔗 **Live demo:** https://sentiment-analysis-n0a7.onrender.com · **Source:** https://github.com/Legion-1315/sentiment-analysis

> ⏳ Hosted on Render's free tier, which sleeps after 15 min idle — the first
> request may take ~50 s to wake, then it's fast.

> Full-stack AI app: Spring Boot (Java 21, Gradle) REST API with a hybrid
> lexicon + neural sentiment engine, and a React + Vite dashboard. Deployed as a
> single Docker container where Spring Boot also serves the built React app.

Every online business wants to know how consumers react to its content.
SentiSense analyzes any consumer text — a review, tweet, comment or support
message — and predicts:

- **Sentiment label**: POSITIVE / NEGATIVE / NEUTRAL with a score in [−1, 1]
- **Aspect-based sentiment (ABSA)**: per-topic polarity — *camera = positive,
  battery = negative, support = negative* — from a single review, with the
  evidence clause behind each verdict
- **Consumer mind state**: 🤩 Delighted · 🙂 Satisfied · 😐 Undecided ·
  🙁 Dissatisfied · 😡 Frustrated — each with a recommended business action
- **Purchase intent** (0–100) and a **confidence** estimate
- The exact **words that drove the verdict**, with their valence
- **Batch aspect insights**: across hundreds of reviews, what customers talk
  about and how they feel about each topic ("battery: 45 texts, 78% negative")

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

### Aspect-based sentiment (ABSA)

On top of the whole-text score, a third analyzer answers *what exactly* the
customer liked or disliked. It segments each sentence into clauses, matches
tokens against a curated aspect taxonomy (~130 terms → 19 canonical aspects:
battery, camera, display, price, delivery, service, food…), and scores each
clause with the lexicon rules — **excluding the aspect term's own valence**.

That exclusion fixes a classic lexicon failure: in *"support never replied to
my emails"*, the noun *support* carries positive valence that used to drown
out the negation. At the aspect level the term is neutralized and an
**unmatched-negation prior** kicks in, so `service` correctly reads NEGATIVE
even though the whole-text score stays (wrongly) mildly positive.

```
"Gorgeous screen and great sound, but the battery is terrible
 and support never replied to my emails."

  display  POSITIVE  +0.61   (gorgeous screen)
  audio    POSITIVE  +0.62   (great sound)
  battery  NEGATIVE  −0.48   (battery is terrible)
  service  NEGATIVE  −0.35   (support never replied)
```

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
   intent, per-aspect breakdown and keyword attribution.
2. **Batch Analysis** — paste up to 200 texts (one per line), get a summary
   donut, per-aspect insights table and per-text results.
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
  "keywords": [{"token": "LOVE", "score": 4.226}, {"token": "best", "score": 3.2}],
  "aspects": []
}
```

When the text mentions known aspects ("the camera is amazing but the battery
is terrible"), `aspects` carries one entry per topic:

```json
"aspects": [
  {"aspect": "camera", "label": "POSITIVE", "score": 0.5859,
   "mentions": ["camera"], "evidence": ["The camera is amazing"]},
  {"aspect": "battery", "label": "NEGATIVE", "score": -0.4767,
   "mentions": ["battery"], "evidence": ["the battery is terrible"]}
]
```

## Testing

```bash
cd backend
./gradlew test                          # 47 tests: unit + API integration
./gradlew test --tests "com.sentisense.engine.EvaluationReportTest"   # analyzer comparison
```

### Feature verification (all pass)

| Feature | Example | Result |
|---------|---------|--------|
| Negation | "The camera is **not good** at all" | NEGATIVE ✔ |
| Emoticons | "just got my order **:(**" | NEGATIVE ✔ |
| "but"-clause | "The design is nice **but** the battery is awful" | NEGATIVE ✔ |
| Emphasis | "Absolutely **LOVE** this phone…**!!!**" | score 0.94, DELIGHTED ✔ |
| Aspects | "Gorgeous **screen**, horrible **battery**" | display +, battery − ✔ |
| Negated neutral verb | "**support never replied** to my emails" | aspect `service` NEGATIVE ✔ |
| Validation | blank text / empty batch | HTTP 400 ✔ |

### Honest error analysis

"Customer support never replied to my emails!!!" is misclassified as mildly
positive **at the whole-text level**: *support* is a positive lexicon term and
*replied* carries no valence for the negation to flip. The **aspect-based
analyzer fixes this** — it excludes the aspect term's own valence and applies
an unmatched-negation prior, so the `service` aspect correctly reads NEGATIVE.
The whole-text ensemble is left unchanged to stay faithful to its published
held-out evaluation.

## Deployment (free, one Docker service)

The whole app ships as a single container: a multi-stage [`Dockerfile`](Dockerfile)
builds the React app, bundles it into Spring Boot's static resources, and runs
one JVM that serves both the UI and the `/api` endpoints — one URL, no CORS.

### Deploy to Render (free tier, no credit card)

[![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https://github.com/Legion-1315/sentiment-analysis)

1. Click the button above (or in the Render dashboard: **New + → Blueprint**),
   sign in with GitHub, and authorize access to this repo.
2. Render reads [`render.yaml`](render.yaml) and provisions the `sentisense`
   web service from the [`Dockerfile`](Dockerfile). Click **Apply**.
3. First build takes ~5–8 min; the app is then live at
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
