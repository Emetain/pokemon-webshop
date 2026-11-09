<script setup>
import { computed, ref } from 'vue'
import { Minus, Plus, Trash2 } from 'lucide-vue-next'
import ProductImage from '../shop/ProductImage.vue'
import { findGame } from '../../data/games'
import { useCart } from '../../stores/cart'
import { formatDate, formatMoney } from '../../utils/format'
import { t } from '../../utils/i18n'

/** One product in the cart, with its quantity. Lowering always works, raising up to what is in stock. */
const props = defineProps({
  item: { type: Object, required: true },
  compact: { type: Boolean, default: false },
})

const emit = defineEmits(['error', 'navigate'])

const cart = useCart()
const busy = ref(false)

const accent = computed(() => (props.item.game ? findGame(props.item.game)?.accent : null))
const link = computed(() => ({ name: 'product', params: { slug: props.item.slug } }))

const problem = computed(() =>
  props.item.problem ? t(`cart.problems.${props.item.problem}`, { available: props.item.available }) : '',
)

async function change(call) {
  busy.value = true
  emit('error', '')
  try {
    await call()
  } catch (exception) {
    emit('error', exception.message)
  } finally {
    busy.value = false
  }
}

const decrease = () => change(() => cart.setQuantity(props.item.productId, props.item.quantity - 1))
const increase = () => change(() => cart.setQuantity(props.item.productId, props.item.quantity + 1))
const remove = () => change(() => cart.remove(props.item.productId))
</script>

<template>
  <li class="line" :class="{ compact, problem: item.problem }">
    <router-link class="image" :to="link" tabindex="-1" aria-hidden="true" @click="emit('navigate')">
      <ProductImage
        :src="item.imageUrl"
        :is-card="item.imageIsCard"
        :set-logo="item.setLogoUrl"
        :type="item.type"
        :game="accent"
      />
    </router-link>

    <div class="body">
      <router-link class="name" :to="link" @click="emit('navigate')">{{ item.name }}</router-link>
      <p v-if="item.unitPriceCents !== null && item.quantity > 1" class="each">
        {{ t('cart.each', { price: formatMoney(item.unitPriceCents, 'EUR') }) }}
      </p>
      <p v-if="item.preOrder" class="pre-order">
        {{ item.shipsFrom ? t('cart.preOrder', { date: formatDate(item.shipsFrom) }) : t('cart.preOrderNoDate') }}
      </p>
      <p v-if="problem" class="warning">{{ problem }}</p>

      <div class="controls">
        <div class="stepper" role="group" :aria-label="t('cart.quantity')">
          <button
            class="step"
            type="button"
            :disabled="busy || item.quantity <= 1"
            :aria-label="t('cart.decrease', { name: item.name })"
            @click="decrease"
          >
            <Minus :size="16" aria-hidden="true" />
          </button>
          <output class="quantity" :aria-label="t('cart.quantity')">{{ item.quantity }}</output>
          <button
            class="step"
            type="button"
            :disabled="busy || item.quantity >= item.maxQuantity"
            :aria-label="t('cart.increase', { name: item.name })"
            @click="increase"
          >
            <Plus :size="16" aria-hidden="true" />
          </button>
        </div>
        <button class="remove" type="button" :disabled="busy" :aria-label="t('cart.removeLabel', { name: item.name })" @click="remove">
          <Trash2 :size="16" aria-hidden="true" />
          <span v-if="!compact">{{ t('cart.remove') }}</span>
        </button>
        <p v-if="item.lineTotalCents !== null && !item.problem" class="line-total">
          <span v-if="item.onSale && item.priceCents" class="old">{{ formatMoney(item.priceCents * item.quantity, 'EUR') }}</span>
          <span :class="{ sale: item.onSale }">{{ formatMoney(item.lineTotalCents, 'EUR') }}</span>
        </p>
      </div>
    </div>
  </li>
</template>

<style scoped>
.line {
  display: grid;
  grid-template-columns: 5.5rem minmax(0, 1fr);
  gap: var(--space-3);
  padding-block: var(--space-4);
  border-bottom: 1px solid var(--color-line);
}

.line.compact {
  grid-template-columns: 4.5rem minmax(0, 1fr);
  padding-block: var(--space-3);
}

.image {
  display: block;
  text-decoration: none;
}

.body {
  display: grid;
  align-content: start;
  gap: var(--space-1);
  min-width: 0;
}

.name {
  color: var(--color-ink);
  font-size: var(--text-sm);
  font-weight: 650;
  line-height: 1.3;
  text-decoration: none;
  overflow-wrap: anywhere;
}

.name:hover {
  color: var(--color-brand);
  text-decoration: underline;
}

.each,
.pre-order {
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.pre-order {
  color: var(--color-brand-deep);
  font-weight: 600;
}

.warning {
  padding: var(--space-1) var(--space-2);
  border-radius: var(--radius-xs);
  background: var(--color-warning-soft);
  color: var(--color-warning);
  font-size: var(--text-xs);
  font-weight: 600;
}

.controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2) var(--space-3);
  margin-top: var(--space-1);
}

.stepper {
  display: inline-flex;
  align-items: center;
  border: 1.5px solid var(--color-line-strong);
  border-radius: var(--radius-sm);
}

.step {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2.25rem;
  height: 2.25rem;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-ink);
  cursor: pointer;
}

.step:hover:not(:disabled) {
  color: var(--color-brand);
}

.step:disabled {
  color: var(--color-line-strong);
  cursor: not-allowed;
}

.quantity {
  min-width: 1.75rem;
  font-size: var(--text-sm);
  font-weight: 700;
  text-align: center;
}

.remove {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  min-height: var(--touch-target);
  padding: 0 var(--space-1);
  border: 0;
  background: transparent;
  color: var(--color-ink-soft);
  cursor: pointer;
  font-size: var(--text-xs);
  font-weight: 600;
}

.remove:hover:not(:disabled) {
  color: var(--color-danger);
}

.line-total {
  display: flex;
  gap: var(--space-2);
  align-items: baseline;
  margin-left: auto;
  font-weight: 750;
  white-space: nowrap;
}

.old {
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
  font-weight: 500;
  text-decoration: line-through;
}

.sale {
  color: var(--color-sale);
}

@media (min-width: 640px) {
  .line:not(.compact) {
    grid-template-columns: 7rem minmax(0, 1fr);
    gap: var(--space-4);
  }
}
</style>
