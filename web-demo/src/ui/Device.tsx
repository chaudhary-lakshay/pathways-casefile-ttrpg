import { useEffect, useState, type ReactNode } from 'react'
import './device.css'

/**
 * A desktop pointer gets the app inside an Android phone: status bar, rounded
 * glass and gesture pill. A touch screen gets the app itself, edge to edge.
 * Decided by the pointer, never by width alone: a narrow desktop window is
 * still a desktop, and a tablet in landscape is still a touch screen.
 */
export const FRAMED = '(hover: hover) and (pointer: fine) and (min-width: 520px) and (min-height: 640px)'

function useClock() {
  const read = () => new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false })
  const [now, setNow] = useState(read)
  useEffect(() => {
    const t = setInterval(() => setNow(read()), 15_000)
    return () => clearInterval(t)
  }, [])
  return now
}

export function Device({ children }: { children: ReactNode }) {
  const clock = useClock()
  return (
    <div className="stage">
      <div className="phone" data-testid="phone">
        <div className="status-bar" aria-hidden="true">
          <span className="clock">{clock}</span>
          <span className="camera" />
          <span className="glyphs">
            <svg viewBox="0 0 24 24"><path d="M2 22h20V2z" /></svg>
            <svg viewBox="0 0 24 24"><path d="M12 21 23.6 6.5C23.1 6.1 18.6 2.5 12 2.5S.9 6.1.4 6.5z" /></svg>
            <svg viewBox="0 0 24 24"><path d="M15.7 4H14V2h-4v2H8.3C7.6 4 7 4.6 7 5.3v15.3c0 .8.6 1.4 1.3 1.4h7.3c.8 0 1.4-.6 1.4-1.3V5.3C17 4.6 16.4 4 15.7 4z" /></svg>
          </span>
        </div>
        <div className="screen">{children}</div>
        <div className="gesture-bar" aria-hidden="true"><span /></div>
      </div>
    </div>
  )
}
