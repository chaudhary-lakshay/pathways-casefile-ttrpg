// A JSON reader that keeps what JSON.parse throws away: a number's own text
// (kotlinx reads `3` as an Int and refuses `3.0`) and the order of every key.

export type Node =
  | { t: 'num'; s: string }
  | { t: 'str'; v: string }
  | { t: 'bool'; v: boolean }
  | { t: 'null' }
  | { t: 'arr'; v: Node[] }
  | { t: 'obj'; v: [string, Node][] }

export class JsonSyntaxError extends Error {
  override name = 'JsonSyntaxError'
}

export function parse(text: string): Node {
  let i = 0
  const fail = (what: string): never => {
    throw new JsonSyntaxError(`${what} at ${i}`)
  }
  const ws = () => {
    while (i < text.length && ' \t\n\r'.includes(text[i])) i++
  }
  const expect = (c: string) => {
    if (text[i] !== c) fail(`expected '${c}'`)
    i++
  }
  const string = (): string => {
    expect('"')
    let out = ''
    for (;;) {
      if (i >= text.length) fail('unterminated string')
      const c = text[i++]
      if (c === '"') return out
      if (c !== '\\') {
        out += c
        continue
      }
      const e = text[i++]
      const simple: Record<string, string> = { '"': '"', '\\': '\\', '/': '/', b: '\b', f: '\f', n: '\n', r: '\r', t: '\t' }
      if (e in simple) out += simple[e]
      else if (e === 'u' && /^[0-9a-fA-F]{4}$/.test(text.slice(i, i + 4))) {
        out += String.fromCharCode(parseInt(text.slice(i, i + 4), 16))
        i += 4
      } else fail('bad escape')
    }
  }
  const value = (): Node => {
    ws()
    const c = text[i]
    if (c === '{') {
      i++
      const v: [string, Node][] = []
      ws()
      if (text[i] === '}') {
        i++
        return { t: 'obj', v }
      }
      for (;;) {
        ws()
        const k = string()
        ws()
        expect(':')
        v.push([k, value()])
        ws()
        if (text[i] === ',') {
          i++
          continue
        }
        expect('}')
        return { t: 'obj', v }
      }
    }
    if (c === '[') {
      i++
      const v: Node[] = []
      ws()
      if (text[i] === ']') {
        i++
        return { t: 'arr', v }
      }
      for (;;) {
        v.push(value())
        ws()
        if (text[i] === ',') {
          i++
          continue
        }
        expect(']')
        return { t: 'arr', v }
      }
    }
    if (c === '"') return { t: 'str', v: string() }
    for (const [word, node] of [['true', { t: 'bool', v: true }], ['false', { t: 'bool', v: false }], ['null', { t: 'null' }]] as const) {
      if (text.startsWith(word, i)) {
        i += word.length
        return node
      }
    }
    const m = /^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?/.exec(text.slice(i))
    if (!m) fail('unexpected character')
    i += m![0].length
    return { t: 'num', s: m![0] }
  }
  const root = value()
  ws()
  if (i !== text.length) fail('trailing text')
  return root
}
