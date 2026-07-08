import { useState } from 'react'
import Analyzer from './components/Analyzer.jsx'
import BatchAnalyzer from './components/BatchAnalyzer.jsx'
import Dashboard from './components/Dashboard.jsx'

const TABS = [
  { id: 'analyze', label: 'Live Analyzer' },
  { id: 'batch', label: 'Batch Analysis' },
  { id: 'dashboard', label: 'Dashboard' },
]

export default function App() {
  const [tab, setTab] = useState('analyze')

  return (
    <div className="app">
      <header className="header">
        <div>
          <h1>SentiSense</h1>
          <p className="tagline">AI-powered consumer sentiment &amp; state-of-mind analysis</p>
        </div>
        <nav className="tabs">
          {TABS.map(t => (
            <button
              key={t.id}
              className={tab === t.id ? 'tab active' : 'tab'}
              onClick={() => setTab(t.id)}
            >
              {t.label}
            </button>
          ))}
        </nav>
      </header>

      <main className="content">
        {tab === 'analyze' && <Analyzer />}
        {tab === 'batch' && <BatchAnalyzer />}
        {tab === 'dashboard' && <Dashboard />}
      </main>

      <footer className="footer">
        Hybrid engine: VADER-style lexicon + neural classifier trained on 3,000 real consumer reviews
      </footer>
    </div>
  )
}
