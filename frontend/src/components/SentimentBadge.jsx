const LABEL_COLORS = {
  POSITIVE: 'var(--pos)',
  NEGATIVE: 'var(--neg)',
  NEUTRAL: 'var(--neu)',
}

const STATE_EMOJI = {
  DELIGHTED: '🤩',
  SATISFIED: '🙂',
  UNDECIDED: '😐',
  DISSATISFIED: '🙁',
  FRUSTRATED: '😡',
}

export function LabelBadge({ label }) {
  return (
    <span className="badge" style={{ background: LABEL_COLORS[label] ?? 'var(--neu)' }}>
      {label}
    </span>
  )
}

export function MindStateBadge({ state }) {
  return (
    <span className="mindstate">
      {STATE_EMOJI[state] ?? ''} {state}
    </span>
  )
}
