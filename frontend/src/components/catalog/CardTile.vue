<script setup>
import { computed } from 'vue'
import { Check } from 'lucide-vue-next'
import CardImage from './CardImage.vue'
import CollectionControls from '../collection/CollectionControls.vue'
import { useCurrency } from '../../stores/currency'
import { t } from '../../utils/i18n'

/** `card.owned` is null for a visitor who isn't logged in: the tile then has nothing about a collection. */
const props = defineProps({
  card: { type: Object, required: true },
  showSet: { type: Boolean, default: false },
  master: { type: Boolean, default: false },
})

defineEmits(['change'])

const { formatPrice } = useCurrency()

const destination = computed(() => ({
  name: 'card',
  params: { game: props.card.game, setCode: props.card.setCode, cardId: props.card.id },
}))

const tracking = computed(() => Boolean(props.card.owned))
const owned = computed(() => tracking.value && props.card.owned.length > 0)
</script>

<template>
  <article class="tile" :class="{ tracking, owned }">
    <router-link class="tile-link" :to="destination">
      <!-- The frame sits around the image with a gap, so nothing touches or covers the card itself. -->
      <span class="frame">
        <CardImage :src="card.imageUrl" />
        <span v-if="owned" class="visually-hidden">{{ t('collection.owned') }}</span>
      </span>
      <span class="name">{{ card.name }}</span>
      <span v-if="showSet" class="detail">{{ card.setName }}</span>
      <span class="detail details">
        <span>#{{ card.number }}</span>
        <span v-if="card.rarity">{{ card.rarity }}</span>
      </span>
      <span v-if="card.trendCents !== null" class="price">{{ formatPrice(card.trendCents, card.currency) }}</span>
      <span v-else class="no-price">{{ t('catalog.noPrice') }}</span>
    </router-link>

    <template v-if="tracking">
      <p v-if="owned && !master" class="status owned-status">
        <Check :size="14" aria-hidden="true" />
        {{ t('collection.owned') }}
      </p>
      <p v-else-if="!master" class="status">{{ t('collection.missing') }}</p>
      <CollectionControls :card="card" :master="master" @change="$emit('change', $event)" />
    </template>
    <slot />
  </article>
</template>

<style scoped>
.tile {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.tile-link {
  display: flex;
  flex: 1;
  flex-direction: column;
  color: var(--color-ink);
  text-decoration: none;
}

.frame {
  display: block;
}

/* Every tile keeps room for the frame, so owned and missing cards line up in a row. */
.tracking .frame {
  padding: var(--frame-gap);
  border: var(--frame-width) solid transparent;
  border-radius: var(--radius-sm);
}

.owned .frame {
  border-color: var(--color-brand);
}

.name {
  margin-top: var(--space-2);
  font-size: var(--text-sm);
  font-weight: 650;
  line-height: 1.3;
  overflow-wrap: anywhere;
}

.tile-link:hover .name {
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

.status {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  margin-top: var(--space-1);
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
  font-weight: 600;
}

.owned-status {
  color: var(--color-brand);
}
</style>
