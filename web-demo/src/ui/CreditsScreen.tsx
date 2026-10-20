// CreditsScreen.kt, plus the web demo's own notice: a fan project, and the
// licences of the fonts and icons it ships.
import { BoxedIconButton, Caps } from './kit'

const CREDITS: [string, string][] = [
  ['Supplement designer and author', 'Earvin Salonoy'],
  ['Rulebook scribes', "Mr.Worm's LOM TTRPG Discord Server"],
  ['Source material', 'Lord of the Mysteries, by Cuttlefish That Loves Diving'],
  ['Game system', 'GURPS 4th Edition, by Steve Jackson Games'],
  ['App', 'chaudhary-lakshay'],
]

const WEB: [string, string][] = [
  ['Fonts', 'Fraunces and IBM Plex Sans / Mono, SIL Open Font License 1.1'],
  ['Icons', 'Material Icons, by Google, Apache License 2.0'],
]

export function CreditsScreen({ onBack }: { onBack: () => void }) {
  return (
    <div className="scaffold">
      <div className="scroll" style={{ padding: '0 20px' }}>
        <div className="row" style={{ paddingTop: 12 }}>
          <BoxedIconButton icon="arrowBack" label="Back" onClick={onBack} />
        </div>
        <h1 className="displaySmall" style={{ paddingTop: 8 }}>Credits</h1>
        <p className="bodyLarge" style={{ padding: '12px 0 16px' }}>
          The game is Powered by GURPS: Pathways — Lord of the Mysteries. Its rules, pathways, potions and characters are the
          work of the people below. This app is only a way to play their game.
        </p>
        <hr className="rule thick" />
        {[...CREDITS, ...WEB].map(([role, name]) => (
          <div key={role}>
            <div style={{ padding: '14px 0' }}>
              <Caps text={role} />
              <p className="titleLarge" style={{ paddingTop: 4 }}>{name}</p>
            </div>
            <hr className="rule" />
          </div>
        ))}
        <p className="bodyMedium muted" style={{ padding: '20px 0 0' }}>Personal, non-commercial use only, as the rulebook asks.</p>
        <p className="bodyMedium muted" style={{ padding: '12px 0 20px' }}>
          An unofficial fan project. It is not affiliated with or endorsed by Steve Jackson Games, Cuttlefish That Loves Diving
          or their publishers. GURPS is a trademark of Steve Jackson Games; Lord of the Mysteries belongs to its author and
          publishers. This page runs in your browser only: no account, no server, no tracking.
        </p>
      </div>
    </div>
  )
}
