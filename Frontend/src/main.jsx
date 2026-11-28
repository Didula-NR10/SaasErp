import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.jsx'
import AccountProtection from './security/AccountProtection.jsx'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    {window.location.pathname === '/account-security' ? <AccountProtection /> : <App />}
  </StrictMode>,
)
