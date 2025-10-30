import React from 'react'
import { createRoot } from 'react-dom/client'

function App() {
  return (
    <div style={{ fontFamily: 'system-ui, -apple-system, Segoe UI, Roboto, sans-serif', padding: 24 }}>
      <h1>ChefMate Admin</h1>
      <p>Placeholder UI. Steps ahead: Menu/Orders pages.</p>
    </div>
  )
}

const root = createRoot(document.getElementById('root'))
root.render(<App />)


