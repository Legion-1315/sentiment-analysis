import { useCallback, useEffect, useState } from 'react'
import {
  PieChart, Pie, Cell, BarChart, Bar, XAxis, YAxis, Tooltip, Legend, ResponsiveContainer,
} from 'recharts'
import { api } from '../api.js'
import { LabelBadge, MindStateBadge } from './SentimentBadge.jsx'

const LABEL_COLORS = { POSITIVE: '#30a46c', NEGATIVE: '#e5484d', NEUTRAL: '#8b8d98' }
const STATE_COLORS = {
  DELIGHTED: '#30a46c', SATISFIED: '#7fbf7f', UNDECIDED: '#8b8d98',
  DISSATISFIED: '#e2a336', FRUSTRATED: '#e5484d',
}

export default function Dashboard() {
  const [stats, setStats] = useState(null)
  const [history, setHistory] = useState(null)
  const [model, setModel] = useState(null)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    try {
      const [s, h, m] = await Promise.all([api.stats(), api.history(0, 15), api.modelInfo()])
      setStats(s); setHistory(h); setModel(m); setError(null)
    } catch (e) {
      setError(e.message)
    }
  }, [])

  useEffect(() => { load() }, [load])

  const clear = async () => {
    if (!window.confirm('Delete all analysis history?')) return
    await api.clearHistory()
    load()
  }

  if (error) return <p className="error">Backend unreachable: {error}</p>
  if (!stats) return <p className="hint">Loading…</p>

  const labelData = Object.entries(stats.byLabel)
    .map(([name, value]) => ({ name, value }))
    .filter(d => d.value > 0)
  const stateData = Object.entries(stats.byMindState).map(([name, value]) => ({ name, value }))

  return (
    <div className="panel-grid">
      <section className="card">
        <h2>Overview</h2>
        <div className="stat-block">
          <div className="stat"><span>{stats.totalAnalyses}</span> total analyses</div>
          <div className="stat"><span>{stats.averageScore.toFixed(3)}</span> avg sentiment</div>
          <div className="stat"><span>{stats.averagePurchaseIntent.toFixed(0)}</span> avg purchase intent</div>
        </div>
        {model && (
          <div className="model-info">
            <h3>Model</h3>
            <p>
              Trained on <b>{model.trainSize}</b> consumer reviews, evaluated on <b>{model.testSize}</b> held-out —
              accuracy <b>{(model.accuracy * 100).toFixed(1)}%</b>, F1 <b>{model.f1.toFixed(3)}</b>,
              vocabulary <b>{model.vocabularySize.toLocaleString()}</b> features
            </p>
          </div>
        )}
        <button className="danger" onClick={clear}>Clear history</button>
      </section>

      {stats.totalAnalyses > 0 && (
        <section className="card">
          <h2>Sentiment distribution</h2>
          <div style={{ width: '100%', height: 240 }}>
            <ResponsiveContainer>
              <PieChart>
                <Pie data={labelData} dataKey="value" nameKey="name" innerRadius={50} outerRadius={80}>
                  {labelData.map(d => <Cell key={d.name} fill={LABEL_COLORS[d.name]} />)}
                </Pie>
                <Tooltip />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          </div>
        </section>
      )}

      {stats.totalAnalyses > 0 && (
        <section className="card">
          <h2>Consumer mind states</h2>
          <div style={{ width: '100%', height: 240 }}>
            <ResponsiveContainer>
              <BarChart data={stateData} margin={{ left: -20 }}>
                <XAxis dataKey="name" tick={{ fontSize: 10, fill: 'var(--muted)' }} />
                <YAxis allowDecimals={false} tick={{ fill: 'var(--muted)' }} />
                <Tooltip cursor={{ fill: 'rgba(255,255,255,0.05)' }} />
                <Bar dataKey="value" radius={[6, 6, 0, 0]}>
                  {stateData.map(d => <Cell key={d.name} fill={STATE_COLORS[d.name]} />)}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </section>
      )}

      {history?.content?.length > 0 && (
        <section className="card wide">
          <h2>Recent analyses</h2>
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th>Text</th><th>Label</th><th>Mind state</th><th>Score</th><th>When</th></tr>
              </thead>
              <tbody>
                {history.content.map(r => (
                  <tr key={r.id}>
                    <td className="text-cell">{r.text}</td>
                    <td><LabelBadge label={r.label} /></td>
                    <td><MindStateBadge state={r.mindState} /></td>
                    <td className={r.score > 0 ? 'pos' : r.score < 0 ? 'neg' : ''}>{r.score.toFixed(3)}</td>
                    <td className="hint">{new Date(r.analyzedAt).toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  )
}
