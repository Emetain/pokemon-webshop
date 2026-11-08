<script setup>
import { computed, ref, useId, watch } from 'vue'
import { SlidersHorizontal } from 'lucide-vue-next'
import FilterSelect from '../catalog/FilterSelect.vue'
import { languageLabel } from '../../data/shop'
import { t } from '../../utils/i18n'

/**
 * Sorting and filtering for a list of products. The values live in the URL. Prices are typed in euros and only
 * applied when the field is left or Enter is pressed, so typing doesn't reload the list at every digit.
 */
const props = defineProps({
  values: { type: Object, required: true },
  languages: { type: Array, default: () => [] },
  defaultSort: { type: String, default: '' },
  /** Leaves out the pre-order filter on the pre-order page. */
  preOrderPage: { type: Boolean, default: false },
})

const emit = defineEmits(['change', 'clear'])

const open = ref(false)
const ids = { min: useId(), max: useId() }

const SORTS = [
  ['', 'shop.sort.featured'],
  ['new', 'shop.sort.new'],
  ['price-low', 'shop.sort.priceLow'],
  ['price-high', 'shop.sort.priceHigh'],
  ['name', 'shop.sort.name'],
  ['popular', 'shop.sort.popular'],
  ['release', 'shop.sort.release'],
]

const defaultLabel = computed(() => t(SORTS.find(([value]) => value === props.defaultSort)?.[1] ?? 'shop.sort.featured'))
const sortOptions = computed(() =>
  SORTS.filter(([value]) => value && value !== props.defaultSort).map(([value, key]) => ({ value, label: t(key) })),
)
const languageOptions = computed(() => props.languages.map((code) => ({ value: code, label: languageLabel(code) })))

const activeCount = computed(
  () => Object.entries(props.values).filter(([name, value]) => name !== 'sort' && value).length,
)

function filter(name) {
  return computed({
    get: () => props.values[name] ?? '',
    set: (value) => emit('change', { [name]: value }),
  })
}

const sort = filter('sort')
const language = filter('language')

const minText = ref(props.values.min ?? '')
const maxText = ref(props.values.max ?? '')
watch(() => props.values.min, (value) => (minText.value = value ?? ''))
watch(() => props.values.max, (value) => (maxText.value = value ?? ''))

function applyPrice(name, text) {
  const cleaned = String(text).trim().replace(',', '.')
  const value = cleaned && Number.isFinite(Number(cleaned)) && Number(cleaned) >= 0 ? cleaned : ''
  if (value !== (props.values[name] ?? '')) emit('change', { [name]: value })
}

function toggle(name, checked) {
  emit('change', { [name]: checked })
}
</script>

<template>
  <div class="product-filters">
    <div class="top">
      <FilterSelect v-model="sort" :label="t('shop.sort.label')" :all-label="defaultLabel" :options="sortOptions" />
      <button class="toggle" type="button" :aria-expanded="open" @click="open = !open">
        <SlidersHorizontal :size="18" aria-hidden="true" />
        {{ t('shop.filters.title') }}
        <span v-if="activeCount" class="badge">{{ activeCount }}</span>
      </button>
    </div>

    <div class="panel" :class="{ open }">
      <slot />
      <FilterSelect
        v-if="languageOptions.length > 1"
        v-model="language"
        :label="t('shop.filters.language')"
        :all-label="t('shop.filters.allLanguages')"
        :options="languageOptions"
      />
      <fieldset class="price">
        <legend class="legend">{{ t('shop.filters.price') }}</legend>
        <label class="visually-hidden" :for="ids.min">{{ t('shop.filters.minPrice') }}</label>
        <input
          :id="ids.min"
          v-model="minText"
          class="price-input"
          inputmode="decimal"
          :placeholder="t('shop.filters.minPrice')"
          @change="applyPrice('min', minText)"
        />
        <label class="visually-hidden" :for="ids.max">{{ t('shop.filters.maxPrice') }}</label>
        <input
          :id="ids.max"
          v-model="maxText"
          class="price-input"
          inputmode="decimal"
          :placeholder="t('shop.filters.maxPrice')"
          @change="applyPrice('max', maxText)"
        />
      </fieldset>
      <label class="check">
        <input type="checkbox" :checked="values.stock" @change="toggle('stock', $event.target.checked)" />
        {{ t('shop.filters.inStock') }}
      </label>
      <label class="check">
        <input type="checkbox" :checked="values.sale" @change="toggle('sale', $event.target.checked)" />
        {{ t('shop.filters.sale') }}
      </label>
      <label v-if="!preOrderPage" class="check">
        <input type="checkbox" :checked="values.preorder" @change="toggle('preorder', $event.target.checked)" />
        {{ t('shop.filters.preOrder') }}
      </label>
      <button v-if="activeCount" class="clear" type="button" @click="emit('clear')">
        {{ t('shop.filters.clear') }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.product-filters {
  display: grid;
  gap: var(--space-3);
}

.top {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: var(--space-3);
}

.toggle {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  height: var(--touch-target);
  padding-inline: var(--space-3);
  border: 1.5px solid var(--color-line-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-ink);
  cursor: pointer;
  font-size: var(--text-sm);
  font-weight: 600;
}

.badge {
  min-width: 1.25rem;
  padding-inline: var(--space-1);
  border-radius: var(--radius-round);
  background: var(--color-brand);
  color: var(--color-on-brand);
  font-size: var(--text-2xs);
  font-weight: 700;
  line-height: 1.25rem;
  text-align: center;
}

.panel {
  display: none;
  gap: var(--space-3);
}

.panel.open {
  display: grid;
}

.price {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-2);
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
}

.legend {
  margin-bottom: var(--space-1);
  padding: 0;
  font-size: var(--text-xs);
  font-weight: 600;
}

.price-input {
  width: 100%;
  min-width: 0;
  height: var(--touch-target);
  padding-inline: var(--space-2);
  border: 1.5px solid var(--color-line-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  font-size: var(--text-sm);
}

.check {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-height: var(--touch-target);
  cursor: pointer;
  font-size: var(--text-sm);
  font-weight: 550;
}

.check input {
  width: 1.125rem;
  height: 1.125rem;
  margin: 0;
  accent-color: var(--color-brand);
}

.clear {
  justify-self: start;
  min-height: var(--touch-target);
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-link);
  cursor: pointer;
  font-size: var(--text-sm);
  font-weight: 550;
}

.clear:hover {
  text-decoration: underline;
}

/* In the sidebar on wide screens, everything shows at once and stacks. */
@media (min-width: 1024px) {
  .top {
    display: block;
  }

  .toggle {
    display: none;
  }

  .panel,
  .panel.open {
    display: grid;
  }
}
</style>
