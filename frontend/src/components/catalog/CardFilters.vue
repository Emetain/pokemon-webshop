<script setup>
import { computed, ref } from 'vue'
import { SlidersHorizontal } from 'lucide-vue-next'
import FilterSelect from './FilterSelect.vue'
import { categoryLabel, colorFilterLabel, colorLabel, variantLabel } from '../../utils/catalog'
import { t } from '../../utils/i18n'

/**
 * Sorting and filtering for a list of cards. A filter only shows when it has a choice to offer.
 * Anything in the default slot joins the other filters, such as the choice of game in search.
 */
const props = defineProps({
  game: { type: String, default: '' },
  values: { type: Object, required: true },
  options: { type: Object, default: () => ({}) },
  /** The order of the list without a choice: `number`, `best-match` or `price-high`. */
  defaultSort: { type: String, default: 'number' },
  /** Shows the owned and missing filter, for customers with a collection. */
  tracking: { type: Boolean, default: false },
})

const emit = defineEmits(['change', 'clear'])

const open = ref(false)

const toOptions = (values, label = (value) => value) =>
  (values ?? []).map((value) => ({ value, label: label(value) }))

const SORT_LABELS = {
  'best-match': 'catalog.sort.bestMatch',
  number: 'catalog.sort.number',
  name: 'catalog.sort.name',
  'name-desc': 'catalog.sort.nameDesc',
  'price-high': 'catalog.sort.priceHigh',
  'price-low': 'catalog.sort.priceLow',
}

const sortOptions = computed(() =>
  Object.entries(SORT_LABELS)
    .filter(([value]) => value !== 'best-match' && value !== props.defaultSort)
    .map(([value, key]) => ({ value, label: t(key) })),
)

const collectionOptions = [
  { value: 'owned', label: t('catalog.filters.collectionOwned') },
  { value: 'missing', label: t('catalog.filters.collectionMissing') },
]

const categoryOptions = computed(() => toOptions(props.options.categories, categoryLabel))
const colorOptions = computed(() => toOptions(props.options.colors, colorLabel))
const rarityOptions = computed(() => toOptions(props.options.rarities))
const variantOptions = computed(() => toOptions(props.options.variants, variantLabel))

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
const category = filter('category')
const color = filter('color')
const rarity = filter('rarity')
const variant = filter('variant')
const collection = filter('collection')
</script>

<template>
  <div class="card-filters">
    <div class="top">
      <FilterSelect
        v-model="sort"
        :label="t('catalog.sort.label')"
        :all-label="t(SORT_LABELS[defaultSort])"
        :options="sortOptions"
      />
      <button class="toggle" type="button" :aria-expanded="open" @click="open = !open">
        <SlidersHorizontal :size="18" aria-hidden="true" />
        {{ t('catalog.filters.title') }}
        <span v-if="activeCount" class="badge">{{ activeCount }}</span>
      </button>
    </div>

    <div class="panel" :class="{ open }">
      <slot />
      <FilterSelect
        v-if="tracking"
        v-model="collection"
        :label="t('catalog.filters.collection')"
        :all-label="t('catalog.filters.all')"
        :options="collectionOptions"
      />
      <FilterSelect
        v-if="categoryOptions.length > 1"
        v-model="category"
        :label="t('catalog.filters.category')"
        :all-label="t('catalog.filters.all')"
        :options="categoryOptions"
      />
      <FilterSelect
        v-if="colorOptions.length > 1"
        v-model="color"
        :label="colorFilterLabel(game)"
        :all-label="t('catalog.filters.all')"
        :options="colorOptions"
      />
      <FilterSelect
        v-if="rarityOptions.length > 1"
        v-model="rarity"
        :label="t('catalog.filters.rarity')"
        :all-label="t('catalog.filters.all')"
        :options="rarityOptions"
      />
      <FilterSelect
        v-if="variantOptions.length > 1"
        v-model="variant"
        :label="t('catalog.filters.variant')"
        :all-label="t('catalog.filters.all')"
        :options="variantOptions"
      />
      <label class="check">
        <input
          type="checkbox"
          :checked="values.priced"
          @change="emit('change', { priced: $event.target.checked })"
        />
        {{ t('catalog.filters.priced') }}
      </label>
      <button v-if="activeCount" class="clear" type="button" @click="emit('clear')">
        {{ t('catalog.filters.clear') }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.card-filters {
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
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: end;
  gap: var(--space-3);
}

.panel.open {
  display: grid;
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
  min-height: var(--touch-target);
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-link);
  cursor: pointer;
  font-size: var(--text-sm);
  font-weight: 550;
  text-align: left;
}

.clear:hover {
  text-decoration: underline;
}

/* Wide enough to show everything at once: the sort and the filters share one row that can wrap. */
@media (min-width: 768px) {
  .card-filters {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-end;
    gap: var(--space-3);
  }

  .top {
    display: block;
    flex: 0 1 12rem;
  }

  .toggle {
    display: none;
  }

  .panel,
  .panel.open {
    display: contents;
  }

  .panel > :deep(.filter) {
    flex: 0 1 10.5rem;
  }

  .check,
  .clear {
    flex: none;
  }
}
</style>
