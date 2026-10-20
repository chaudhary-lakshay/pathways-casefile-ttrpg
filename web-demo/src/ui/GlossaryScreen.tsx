// GlossaryScreen.kt: the book's glossary (pp. 167-168), searchable, one
// term open at a time, a letter strip that jumps and marks the group in view.
import { useMemo, useRef, useState, type UIEvent } from 'react'
import { glossaryGroups } from '../app/Glossary'
import * as ContentLoader from '../rules/content/ContentLoader'
import { Icon } from './icons'
import { BoxedIconButton } from './kit'
import './glossary.css'

export function GlossaryScreen({ onBack }: { onBack: () => void }) {
  const entries = useMemo(() => ContentLoader.glossary(), [])
  const [query, setQuery] = useState('')
  const [openTerm, setOpenTerm] = useState<string | null>(null)
  const [inView, setInView] = useState(0)
  const list = useRef<HTMLDivElement>(null)
  const groups = glossaryGroups(entries, query)
  // firstVisibleItemIndex: the last group whose top has scrolled to the list's top.
  const onScroll = (e: UIEvent<HTMLDivElement>) => {
    const el = e.currentTarget
    const blocks = [...el.querySelectorAll<HTMLElement>('.glossary-group')]
    let index = 0
    blocks.forEach((b, i) => { if (b.offsetTop - el.offsetTop <= el.scrollTop + 1) index = i })
    if (index !== inView) setInView(index)
  }
  const jump = (index: number) => {
    const el = list.current
    const block = el?.querySelectorAll<HTMLElement>('.glossary-group')[index]
    if (el && block) el.scrollTo({ top: block.offsetTop - el.offsetTop, behavior: 'smooth' })
  }
  return (
    <div className="scaffold glossary">
      <div className="row" style={{ paddingTop: 12 }}>
        <BoxedIconButton icon="arrowBack" label="Back" onClick={onBack} />
        <span className="labelSmall caps muted grow" style={{ textAlign: 'right' }} aria-hidden="true">PP. 167–168 · {entries.length} ENTRIES</span>
      </div>
      <h1 className="displaySmall" style={{ paddingTop: 8 }}>Glossary</h1>
      <label className="search-line">
        <Icon name="search" size={22} />
        <input className="bodyLarge mono" placeholder="look up a term" aria-label="Look up a term" value={query} onChange={(e) => setQuery(e.target.value)} />
      </label>
      <div className="letters">
        {groups.map((g, i) => (
          <button key={`${i}${g.letter}`} className={`letter${i === inView ? ' current' : ''}`} onClick={() => jump(i)} aria-label={`Jump to ${g.letter}`}>
            <span className="labelMedium">{g.letter}</span>
          </button>
        ))}
      </div>
      <hr className="rule thick" />
      <div className="scroll" ref={list} onScroll={onScroll}>
        {groups.length === 0 && <p className="bodyMedium" style={{ padding: '16px 0' }}>No term matches.</p>}
        {groups.map((g, i) => (
          <div key={`${i}${g.letter}`} className="glossary-group">
            <div className="row" style={{ alignItems: 'flex-start', padding: '8px 0' }}>
              <span className="displaySmall group-letter" aria-hidden="true" style={{ color: g.entries.some((e) => e.term === openTerm) ? 'var(--on-surface)' : 'var(--on-surface-variant)' }}>
                {g.letter}
              </span>
              <div className="grow">
                {g.entries.map((entry) =>
                  entry.term === openTerm ? (
                    <div key={entry.term} className="open-term">
                      <button className="term-row" onClick={() => setOpenTerm(null)} aria-expanded="true">
                        <span className="titleLarge serif grow">{entry.term}</span>
                        <Icon name="keyboardArrowUp" size={20} />
                      </button>
                      <p className="bodyMedium" style={{ paddingBottom: 8 }}>{entry.definition}</p>
                    </div>
                  ) : (
                    <button key={entry.term} className="term-row" onClick={() => setOpenTerm(entry.term)} aria-expanded="false">
                      <span className="bodyLarge">{entry.term}</span>
                      <span className="dots" />
                      <Icon name="keyboardArrowDown" size={20} />
                    </button>
                  ),
                )}
              </div>
            </div>
            <hr className="rule" />
          </div>
        ))}
        <div style={{ height: '85%' }} />
      </div>
    </div>
  )
}
