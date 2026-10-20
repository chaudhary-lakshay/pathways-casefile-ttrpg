import { describe, expect, test } from 'vitest'
import { controlKeyLine, untrainedShown } from '../app/SheetText'
import { Buyable, SkillKey } from '../rules/model'

// SelfControlDialog.kt title; UntrainedDialog.kt filter.
describe('the dialog text', () => {
  test('a keyed self-control roll names its attribute and offset', () => {
    expect(controlKeyLine({ attribute: 'Will', offset: 0, target: null, source: 'p' })).toBe('keyed on Will, after any potion')
    expect(controlKeyLine({ attribute: 'Will', offset: 2, target: null, source: 'p' })).toBe('keyed on Will+2, after any potion')
    expect(controlKeyLine({ attribute: 'IQ', offset: -1, target: null, source: 'p' })).toBe('keyed on IQ-1, after any potion')
  })

  test('a flat self-control roll says no attribute moves it', () => {
    expect(controlKeyLine({ attribute: null, offset: 0, target: 12, source: 'p' })).toBe('a flat number the book prints — no attribute moves it')
  })

  test('the untrained list filters on the trimmed query, ignoring case', () => {
    const rows = [
      { key: SkillKey('Climbing', null, Buyable('DX'), 'AVERAGE'), level: 9, source: 'DX-5' },
      { key: SkillKey('Driving', 'Automobile', Buyable('DX'), 'AVERAGE'), level: 8, source: 'DX-5' },
    ]
    expect(untrainedShown(rows, '  climb ').map((it) => it.level)).toEqual([9])
    expect(untrainedShown(rows, 'AUTOMOBILE').map((it) => it.level)).toEqual([8])
    expect(untrainedShown(rows, '')).toHaveLength(2)
  })
})
