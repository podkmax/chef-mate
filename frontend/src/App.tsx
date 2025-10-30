import { useEffect, useState } from 'react'

export function App() {
  const [health, setHealth] = useState<string>('...')

  useEffect(() => {
    fetch('/api/health')
      .then(r => r.json())
      .then(j => setHealth(j.status ?? 'unknown'))
      .catch(() => setHealth('unreachable'))
  }, [])

  return (
    <div style={{ fontFamily: 'system-ui, sans-serif', padding: 24 }}>
      <h1>ChefMate Admin</h1>
      <p>Placeholder UI.</p>
      <p>Backend health: <strong>{health}</strong></p>
    </div>
  )
}


