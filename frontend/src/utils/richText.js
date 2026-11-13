/**
 * The few marks news articles and info pages use, turned into blocks the page renders itself. No HTML from the
 * text ever reaches the page, so an admin can't break it or put a script in it.
 *
 * - A blank line starts a new block. One line break inside a paragraph stays a line break.
 * - `## ` and `### ` start a heading.
 * - Lines starting with `- ` make a list.
 * - `**bold**`, `[text](/path)` and `[text](https://...)` inside a line. A bare https:// address is a link too.
 */

const INLINE = /\*\*(.+?)\*\*|\[([^\]]+)\]\(((?:\/(?!\/)|https:\/\/)[^\s)]*)\)|(https:\/\/[^\s)]+[^\s).,;:!?])/g

/** Splits one line into text, bold and link parts. */
export function parseInline(line) {
  const parts = []
  let last = 0
  for (const match of line.matchAll(INLINE)) {
    if (match.index > last) parts.push({ type: 'text', text: line.slice(last, match.index) })
    if (match[1] !== undefined) parts.push({ type: 'bold', text: match[1] })
    else if (match[2] !== undefined) parts.push({ type: 'link', text: match[2], href: match[3] })
    else parts.push({ type: 'link', text: match[4], href: match[4] })
    last = match.index + match[0].length
  }
  if (last < line.length) parts.push({ type: 'text', text: line.slice(last) })
  return parts
}

export function parseRichText(source) {
  const blocks = []
  const lines = (source ?? '').replace(/\r\n?/g, '\n').split('\n')
  let paragraph = []
  let list = null

  const flush = () => {
    if (paragraph.length) blocks.push({ type: 'paragraph', lines: paragraph.map(parseInline) })
    if (list) blocks.push({ type: 'list', items: list.map(parseInline) })
    paragraph = []
    list = null
  }

  for (const raw of lines) {
    const line = raw.trimEnd()
    if (!line.trim()) {
      flush()
    } else if (line.startsWith('### ') || line.startsWith('## ')) {
      flush()
      const level = line.startsWith('### ') ? 3 : 2
      blocks.push({ type: 'heading', level, parts: parseInline(line.slice(level + 1).trim()) })
    } else if (/^\s*- /.test(line)) {
      if (paragraph.length) {
        blocks.push({ type: 'paragraph', lines: paragraph.map(parseInline) })
        paragraph = []
      }
      list ??= []
      list.push(line.replace(/^\s*- /, ''))
    } else {
      if (list) {
        blocks.push({ type: 'list', items: list.map(parseInline) })
        list = null
      }
      paragraph.push(line.trim())
    }
  }
  flush()
  return blocks
}
