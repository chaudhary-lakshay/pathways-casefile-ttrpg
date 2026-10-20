import { describe, expect, test } from 'vitest'
import { contractLines } from '../app/DialogLines'

// ContractLinesTest.kt's inputs and expected lines.
describe('ContractLinesTest', () => {
  const none = { total: 0, line: null }

  test('the corruption penalty is printed', () => {
    expect(contractLines(16, 0, 0, none, 4, 6, 0, 0)).toContain('-1 for 4 CoR')
  })

  test('hurt, shock and declared states are printed', () => {
    const lines = contractLines(16, -1, -4, { total: -2, line: '-2 declared' }, 0, 6, 0, 0)
    expect(lines).toContain('-1 hurt or tired')
    expect(lines).toContain('-4 shock')
    expect(lines).toContain('-2 declared')
  })

  test('a term worth nothing is not printed', () => {
    const lines = contractLines(16, 0, 0, none, 2, 6, 0, 0)
    expect(lines.some((it) => it.includes('CoR') || it.includes('shock') || it.includes('hurt'))).toBe(false)
  })
})
