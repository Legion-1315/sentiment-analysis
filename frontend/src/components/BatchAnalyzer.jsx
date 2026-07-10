import { useState } from 'react'
import { PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer } from 'recharts'
import { api } from '../api.js'
import { LabelBadge, MindStateBadge } from './SentimentBadge.jsx'

const SAMPLE = `Great phone, amazing battery life!
The screen cracked within a week, very disappointed.
Delivery was on time.
Best purchase I have made this year, highly recommend.
Customer support never replied to my emails!!!
It works, nothing special.`

export default function BatchAnalyzer() {
  const [input, setInput] = useState(SAMPLE)
  const [summary, setSummary] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  const run = async () => {
    const texts = input.split('\n').map(t => t.trim()).filter(Boolean)
    if (!texts.length) return
    setLoading(true)
    setError(null)
    try {
      setSummary(await api.batch(texts, 'batch'))
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  const pieData = summary && [
    { name: 'Positive', value: summary.positive, color: 'var(--pos)' },
    { name: 'Negative', value: summary.negative, color: 'var(--neg)' },
    { name: 'Neutral', value: summary.neutral, color: 'var(--neu)' },
  ].filter(d => d.value > 0)

  return (
    <div className="panel-grid">
      <section className="card">
        <h2>Batch analysis</h2>
        <p className="hint">One text per line (max 200). Paste reviews, comments or survey answers.</p>
        <textarea value={input} onChange={e => setInput(e.target.value)} rows={9} />
        <div className="row">
          <button className="primary" onClick={run} disabled={loading}>
            {loading ? 'Analyzing…' : `Analyze ${input.split('\n').filter(t => t.trim()).length} texts`}
          </button>
        </div>
        {error && <p className="error">{error}</p>}
      </section>

      {summary && (
        <>
          <section className="card">
            <h2>Summary</h2>
            <div className="summary-row">
              <div className="stat-block">
                <div className="stat"><span>{summary.total}</span> texts</div>
                <div className="stat pos"><span>{summary.positive}</span> positive</div>
                <div className="stat neg"><span>{summary.negative}</span> negative</div>
                <div className="stat neu"><span>{summary.neutral}</span> neutral</div>
                <div className="stat"><span>{summary.averageScore.toFixed(3)}</span> avg score</div>
              </div>
              <div style={{ width: 260, height: 220 }}>
                <ResponsiveContainer>
                  <PieChart>
                    <Pie data={pieData} dataKey="value" nameKey="name" innerRadius={45} outerRadius={75}>
                      {pieData.map(d => <Cell key={d.name} fill={d.color} />)}
                    </Pie>
                    <Tooltip />
                    <Legend />
                  </PieChart>
                </ResponsiveContainer>
              </div>
            </div>
          </section>

          {summary.aspects?.length > 0 && (
            <section className="card wide">
              <h2>Aspect insights</h2>
              <p className="hint">What customers talk about — and how they feel about each topic.</p>
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr><th>Aspect</th><th>Texts</th><th>Avg score</th><th>Sentiment split</th></tr>
                  </thead>
                  <tbody>
                    {summary.aspects.map(a => (
                      <tr key={a.aspect}>
                        <td className="aspect-name">{a.aspect}</td>
                        <td>{a.texts}</td>
                        <td className={a.averageScore > 0.1 ? 'pos' : a.averageScore < -0.1 ? 'neg' : ''}>
                          {a.averageScore > 0 ? '+' : ''}{a.averageScore.toFixed(3)}
                        </td>
                        <td>
                          <div className="split-bar" title={`${a.positive} positive · ${a.neutral} neutral · ${a.negative} negative`}>
                            {a.positive > 0 && <div className="split pos-bg" style={{ flex: a.positive }} />}
                            {a.neutral > 0 && <div className="split neu-bg" style={{ flex: a.neutral }} />}
                            {a.negative > 0 && <div className="split neg-bg" style={{ flex: a.negative }} />}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          )}

          <section className="card wide">
            <h2>Results</h2>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr><th>Text</th><th>Label</th><th>Mind state</th><th>Score</th><th>Intent</th></tr>
                </thead>
                <tbody>
                  {summary.results.map((r, i) => (
                    <tr key={i}>
                      <td className="text-cell">{r.text}</td>
                      <td><LabelBadge label={r.analysis.label} /></td>
                      <td><MindStateBadge state={r.analysis.mindState} /></td>
                      <td className={r.analysis.score > 0 ? 'pos' : r.analysis.score < 0 ? 'neg' : ''}>
                        {r.analysis.score.toFixed(3)}
                      </td>
                      <td>{r.analysis.purchaseIntent}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </>
      )}
    </div>
  )
}
