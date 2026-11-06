<script setup>
import { computed } from 'vue'
import CardImage from './CardImage.vue'
import { useCurrency } from '../../stores/currency'
import { t } from '../../utils/i18n'

const props = defineProps({
  card: { type: Object, required: true },
  showSet: { type: Boolean, default: false },
})

const { formatPrice } = useCurrency()

const destination = computed(() => ({
  name: 'card',
  params: { game: props.card.game, setCode: props.card.setCode, cardId: props.card.id },
}))
</script>

<template>
  <router-link class="tile" :to="destination">
    <CardImage :src="card.imageUrl" />
    <span class="name">{{ card.name }}</span>
    <span v-if="showSet" class="detail">{{ card.setName }}</span>
    <span class="detail details">
      <span>#{{ card.number }}</span>
      <span v-if="card.rarity">{{ card.rarity }}</span>
    </span>
    <span v-if="card.trendCents !== null" class="price">{{ formatPrice(card.trendCents, card.currency) }}</span>
    <span v-else class="no-price">{{ t('catalog.noPrice') }}</span>
  </router-link>
</template>

<style scoped>
.tile {
  display: flex;
  flex-direction: column;
  height: 100%;
  color: var(--color-ink);
  text-decoration: none;
}

.name {
  margin-top: var(--space-2);
  font-size: var(--text-sm);
  font-weight: 650;
  line-height: 1.3;
  overflow-wrap: anywhere;
}

.tile:hover .name {
  color: var(--color-brand);
  text-decoration: underline;
}

.detail {
  margin-top: var(--space-half);
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
  line-height: 1.35;
}

.details {
  display: flex;
  flex-wrap: wrap;
  gap: 0 var(--space-2);
}

/* The price lines up along the bottom of a row, however long the names above it are. */
.price,
.no-price {
  margin-top: auto;
  padding-top: var(--space-1);
}

.price {
  font-size: var(--text-md);
  font-weight: 700;
}

.no-price {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}
</style>
