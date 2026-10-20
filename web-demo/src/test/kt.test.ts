import { describe, expect, test } from 'vitest'
import { eqIc, isBlank, trim } from '../rules/kt'
import { newCharacterRefusal } from '../app/session/Characters'

// Each expectation is what Kotlin 2.2 printed for the same call (Codex's
// jshell probe of 2026-10-20 for the first two; Char.isWhitespace for the rest).
describe('Kotlin string semantics on typed text', () => {
  test('equals(ignoreCase) folds per character through upper case', () => {
    expect(eqIc('Intis', 'ıntis')).toBe(true)
    expect(eqIc('straße', 'STRASSE')).toBe(false)
  })

  test('isBlank and trim use Char.isWhitespace, not JS whitespace', () => {
    expect(isBlank('\u001c')).toBe(true)
    expect(isBlank('﻿')).toBe(false)
    expect(trim('\u001f Beat Cop  ')).toBe('Beat Cop')
    expect(trim('﻿A')).toBe('﻿A')
  })

  test('a name taken in another casing is refused as Kotlin refuses it', () => {
    expect(newCharacterRefusal('ıntis', new Set(['Intis']))).toBe('That name is taken.')
    expect(newCharacterRefusal('\u001c', new Set())).toBe('Name the character.')
  })
})
