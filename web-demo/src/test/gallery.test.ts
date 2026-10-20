import { describe, expect, test } from 'vitest'
import {
  blankHint, chipTraits, createRefusal, fileDigits, templateChoices, fileLine, fileNumber, galleryNote, headerFacts, nextFileNumber, playedRow, rosterOf, walkStamp,
} from '../app/Gallery'
import * as Premades from '../rules/Premades'
import { BeyonderState } from '../rules/model'
import { SavedSession } from '../app/session/SessionStore'

// GalleryRows.kt, PremadeGallery.kt and SheetHeader.kt, read line by line.
const sheets = Premades.all()
const walk = BeyonderState({ pathway: 'Fool', sequence: 9, digestion: 40 })

describe('the gallery', () => {
  test('files are numbered in list order, the blank sheet unnumbered', () => {
    const blank = sheets.find((it) => it.name === 'LOTM Blank Character Sheet')!
    expect(fileNumber(sheets[0], sheets)).toBe(1)
    expect(fileNumber(blank, sheets)).toBeNull()
    expect(nextFileNumber(sheets)).toBe(sheets.length)
    expect(fileDigits(7)).toBe('07')
    expect(fileLine(7, true)).toBe('File Nº 07 · Beyonder')
    expect(fileLine(null, false)).toBe('Mortal')
  })

  test('a chip is the name before its dash, 1 to 32 characters, two at most', () => {
    expect(chipTraits(['Alertness - 1', 'Fit — +1 HT rolls', 'Luck', 'x'.repeat(33)])).toEqual(['Alertness', 'Fit'])
    expect(chipTraits([' − ', 'Luck'])).toEqual(['Luck'])
  })

  test('the note prints the chips, then the skill count', () => {
    expect(galleryNote({ attributes: { st: 10, dx: 10, iq: 10, ht: 10 }, skills: 13, chips: ['A', 'B'] }, false)).toBe('A, B · 13 sk.')
    expect(galleryNote({ attributes: { st: 10, dx: 10, iq: 10, ht: 10 }, skills: 1, chips: [] }, true)).toBe('1 skills')
  })

  test('the roster splits on a stored walk, never a printed one', () => {
    const saved = new Map([[sheets[0].name, SavedSession({ beyonder: walk })]])
    expect(rosterOf('BEYONDER', sheets, saved).map((it) => it.name)).toEqual([sheets[0].name])
    expect(rosterOf('MORTAL', sheets, saved)).toHaveLength(sheets.length - 1)
    expect(walkStamp(walk)).toBe('Fool 9')
    expect(walkStamp(null)).toBe('Mortal')
  })

  test("a row reads the walk's attributes and the played skills", () => {
    const sheet = sheets[0]
    const row = playedRow(sheet, null)
    expect(row.skills).toBe(sheet.skills.length)
    expect(row.attributes.st).toBe(sheet.attributes.get('ST'))
  })

  test('a new character needs a free name and, from a template, a pick', () => {
    const blank = sheets.find((it) => it.name === 'LOTM Blank Character Sheet')!
    expect(blankHint(blank)).toMatch(/^Blank sheet starts (every attribute at \d+|at ST \d+, DX \d+, IQ \d+, HT \d+)\.$/)
    expect(createRefusal('', new Set(), blank)).toBe('Name the character.')
    expect(createRefusal('Mira', new Set(), null)).toBe('Pick a template.')
    expect(createRefusal('Mira', new Set(), blank)).toBeNull()
    expect(templateChoices(sheets, '  cop ').map((it) => it.name)).toEqual(['Beat Cop'])
    expect(templateChoices(sheets, '').some((it) => it.name === blank.name)).toBe(false)
  })

  test('header facts: none for a mortal, every pill red once lost', () => {
    expect(headerFacts(null, null, 3, false, false)).toEqual([])
    expect(headerFacts(walk, null, 3, false, false)).toEqual([
      { text: 'Fool · Seq 9', ink: 'STAMP' },
      { text: 'Digested 40%', ink: 'INK' },
      { text: 'CoR 3', ink: 'MUTE' },
    ])
    expect(headerFacts(walk, 'Boon', 3, true, false).map((it) => it.ink)).toEqual(['LOST', 'LOST', 'LOST', 'LOST', 'LOST'])
    expect(headerFacts(walk, null, 3, false, true)[0].text).toBe('Lost to corruption')
  })
})
