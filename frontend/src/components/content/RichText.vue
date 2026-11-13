<script>
import { h } from 'vue'
import { RouterLink } from 'vue-router'
import { parseRichText } from '../../utils/richText'

/** Text with the marks of `utils/richText.js`, rendered as elements. Headings start at `level` + the mark. */
export default {
  name: 'RichText',
  props: {
    text: { type: String, default: '' },
    /** The heading level `## ` becomes, for pages that already have their own h1 and h2 above the text. */
    headingStart: { type: Number, default: 2 },
  },
  setup(props) {
    const inline = (parts) =>
      parts.map((part) => {
        if (part.type === 'bold') return h('strong', part.text)
        if (part.type === 'link' && part.href.startsWith('/')) return h(RouterLink, { to: part.href }, () => part.text)
        if (part.type === 'link') return h('a', { href: part.href, rel: 'noopener noreferrer', target: '_blank' }, part.text)
        return part.text
      })

    return () =>
      h(
        'div',
        { class: 'rich-text' },
        parseRichText(props.text).map((block) => {
          if (block.type === 'heading') {
            const level = Math.min(6, props.headingStart + block.level - 2)
            return h(`h${level}`, inline(block.parts))
          }
          if (block.type === 'list') return h('ul', block.items.map((item) => h('li', inline(item))))
          return h(
            'p',
            block.lines.flatMap((line, index) => (index === 0 ? inline(line) : [h('br'), ...inline(line)])),
          )
        }),
      )
  },
}
</script>

<style scoped>
.rich-text {
  max-width: 68ch;
  line-height: var(--leading-body);
}

.rich-text > * + * {
  margin-top: var(--space-3);
}

.rich-text :deep(h2) {
  margin-top: var(--space-5);
  font-size: var(--text-xl);
}

.rich-text :deep(h3) {
  margin-top: var(--space-4);
  font-size: var(--text-lg);
}

.rich-text :deep(ul) {
  display: grid;
  gap: var(--space-1);
  padding-left: var(--space-5);
}

.rich-text :deep(li)::marker {
  color: var(--accent);
}
</style>
