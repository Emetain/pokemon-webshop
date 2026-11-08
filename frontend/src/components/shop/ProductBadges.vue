<script setup>
import { computed } from 'vue'
import { t } from '../../utils/i18n'

/** The states of a product as small labels. They sit below the image, never on it. */
const props = defineProps({
  product: { type: Object, required: true },
})

const badges = computed(() => {
  const { product } = props
  const list = []
  if (product.preOrder) list.push({ kind: 'pre-order', text: t('shop.badges.preOrder') })
  if (!product.canBuy) list.push({ kind: 'sold-out', text: t('shop.badges.soldOut') })
  if (product.discountPercent !== null && product.discountPercent !== undefined) {
    list.push({
      kind: 'sale',
      text: product.discountPercent > 0 ? t('shop.discount', { percent: product.discountPercent }) : t('shop.badges.sale'),
      label: t('shop.badges.sale'),
    })
  }
  if (product.lastFew) {
    list.push({
      kind: 'last-few',
      text: product.lastFew === 1 ? t('shop.badges.lastOne') : t('shop.badges.lastFew', { count: product.lastFew }),
    })
  }
  return list
})
</script>

<template>
  <ul v-if="badges.length" class="badges">
    <li v-for="badge in badges" :key="badge.kind" class="badge" :class="badge.kind">
      <span v-if="badge.label" class="visually-hidden">{{ badge.label }}:</span>
      {{ badge.text }}
    </li>
  </ul>
</template>

<style scoped>
.badges {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
  margin: 0;
  padding: 0;
  list-style: none;
}

.badge {
  padding: var(--space-half) var(--space-2);
  border-radius: var(--radius-xs);
  font-size: var(--text-2xs);
  font-weight: 700;
  line-height: 1.5;
}

.pre-order {
  background: var(--color-brand-deep);
  color: var(--color-on-brand);
}

.sold-out {
  background: var(--color-muted-soft);
  color: var(--color-ink-soft);
}

.sale {
  background: var(--color-sale);
  color: var(--color-on-brand);
}

.last-few {
  background: var(--color-warning-soft);
  color: var(--color-warning);
}
</style>
