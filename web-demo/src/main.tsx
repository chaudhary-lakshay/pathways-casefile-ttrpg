import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import icon from '@res/drawable/ic_launcher_art.webp'
import 'virtual:case-file.css'
import './ui/theme.css'
import './ui/app.css'
import { App } from './ui/App'

const link = document.createElement('link')
link.rel = 'icon'
link.href = icon
document.head.append(link)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
