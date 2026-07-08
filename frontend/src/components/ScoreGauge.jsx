// Semicircular gauge rendering a sentiment score in [-1, 1]
export default function ScoreGauge({ score }) {
  const clamped = Math.max(-1, Math.min(1, score))
  const angle = (clamped + 1) * 90 // -1 → 0°, +1 → 180°
  const rad = (Math.PI * (180 - angle)) / 180
  const needleX = 100 + 72 * Math.cos(rad)
  const needleY = 95 - 72 * Math.sin(rad)
  const color = clamped > 0.1 ? 'var(--pos)' : clamped < -0.1 ? 'var(--neg)' : 'var(--neu)'

  return (
    <div className="gauge">
      <svg viewBox="0 0 200 110" width="220">
        <defs>
          <linearGradient id="gaugeGrad" x1="0" y1="0" x2="1" y2="0">
            <stop offset="0%" stopColor="#e5484d" />
            <stop offset="50%" stopColor="#8b8d98" />
            <stop offset="100%" stopColor="#30a46c" />
          </linearGradient>
        </defs>
        <path
          d="M 15 95 A 85 85 0 0 1 185 95"
          fill="none"
          stroke="url(#gaugeGrad)"
          strokeWidth="14"
          strokeLinecap="round"
          opacity="0.85"
        />
        <line x1="100" y1="95" x2={needleX} y2={needleY} stroke={color} strokeWidth="3.5" strokeLinecap="round" />
        <circle cx="100" cy="95" r="6" fill={color} />
        <text x="15" y="108" fontSize="9" fill="var(--muted)">-1</text>
        <text x="178" y="108" fontSize="9" fill="var(--muted)">+1</text>
      </svg>
      <div className="gauge-value" style={{ color }}>{clamped.toFixed(3)}</div>
    </div>
  )
}
