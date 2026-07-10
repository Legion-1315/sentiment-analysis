import { useState } from 'react'
import { api } from '../api.js'
import ScoreGauge from './ScoreGauge.jsx'
import { LabelBadge, MindStateBadge } from './SentimentBadge.jsx'

const EXAMPLES = [
  'Absolutely love this phone, the camera is incredible!',
  'Gorgeous screen and great sound, but the battery is terrible and support never replied to my emails.',
  'Worst customer service ever. Never buying from them again!!!',
  'The package arrived on Tuesday afternoon.',
]

export default function Analyzer() {
  const [text, setText] = useState('')
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  const analyze = async () => {
    if (!text.trim()) return
    setLoading(true)
    setError(null)
    try {
      setResult(await api.analyze(text, 'web'))
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="panel-grid">
      <section className="card">
        <h2>Analyze consumer text</h2>
        <textarea
          value={text}
          onChange={e => setText(e.target.value)}
          onKeyDown={e => { if (e.key === 'Enter' && e.ctrlKey) analyze() }}
          placeholder="Paste a review, tweet, comment or support message…"
          rows={5}
          maxLength={4000}
        />
        <div className="row">
          <button className="primary" onClick={analyze} disabled={loading || !text.trim()}>
            {loading ? 'Analyzing…' : 'Analyze sentiment'}
          </button>
          <span className="hint">Ctrl+Enter</span>
        </div>
        <div className="examples">
          {EXAMPLES.map(ex => (
            <button key={ex} className="example" onClick={() => setText(ex)}>
              {ex.length > 42 ? ex.slice(0, 42) + '…' : ex}
            </button>
          ))}
        </div>
        {error && <p className="error">{error}</p>}
      </section>

      {result && (
        <section className="card result">
          <div className="result-head">
            <LabelBadge label={result.label} />
            <MindStateBadge state={result.mindState} />
          </div>
          <p className="meaning">{result.mindStateMeaning}</p>

          <div className="result-body">
            <ScoreGauge score={result.score} />
            <div className="metrics">
              <Metric name="Confidence" value={`${(result.confidence * 100).toFixed(0)}%`} />
              <Metric name="Purchase intent" value={`${result.purchaseIntent}/100`} bar={result.purchaseIntent} />
              <Metric name="Lexicon score" value={result.lexiconScore.toFixed(3)} />
              <Metric name="ML P(positive)" value={result.mlProbability.toFixed(3)} />
            </div>
          </div>

          {result.aspects?.length > 0 && (
            <div className="keywords">
              <h3>Aspect breakdown</h3>
              <div className="aspect-list">
                {result.aspects.map((a, i) => (
                  <div key={i} className="aspect-row" title={a.evidence?.join(' • ')}>
                    <span className="aspect-name">{a.aspect}</span>
                    <span
                      className="chip"
                      style={{
                        borderColor: a.label === 'POSITIVE' ? 'var(--pos)'
                          : a.label === 'NEGATIVE' ? 'var(--neg)' : 'var(--neu)',
                      }}
                    >
                      {a.label.toLowerCase()} <small>{a.score > 0 ? '+' : ''}{a.score.toFixed(2)}</small>
                    </span>
                    <span className="aspect-mentions">{a.mentions.join(', ')}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {result.keywords?.length > 0 && (
            <div className="keywords">
              <h3>Sentiment-bearing words</h3>
              <div className="chips">
                {result.keywords.map((k, i) => (
                  <span
                    key={i}
                    className="chip"
                    style={{ borderColor: k.score > 0 ? 'var(--pos)' : 'var(--neg)' }}
                    title={`valence ${k.score}`}
                  >
                    {k.token} <small>{k.score > 0 ? '+' : ''}{k.score.toFixed(1)}</small>
                  </span>
                ))}
              </div>
            </div>
          )}
        </section>
      )}
    </div>
  )
}

function Metric({ name, value, bar }) {
  return (
    <div className="metric">
      <span className="metric-name">{name}</span>
      <span className="metric-value">{value}</span>
      {bar !== undefined && (
        <div className="meter"><div className="meter-fill" style={{ width: `${bar}%` }} /></div>
      )}
    </div>
  )
}
