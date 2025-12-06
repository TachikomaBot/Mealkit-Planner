import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App.tsx'
import './index.css'
import { initializeNative } from './native'
import { seedPantryStaples } from './db'

// Initialize native features (notifications, status bar, etc.)
initializeNative()

// Seed pantry with staples if empty
seedPantryStaples()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
)
