<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Pencil, Trash2 } from 'lucide-vue-next'
import BreadcrumbTrail from '../../components/catalog/BreadcrumbTrail.vue'
import CardImage from '../../components/catalog/CardImage.vue'
import CatalogBand from '../../components/catalog/CatalogBand.vue'
import FormMessage from '../../components/FormMessage.vue'
import NotFoundView from '../NotFoundView.vue'
import QuantityStepper from '../../components/collection/QuantityStepper.vue'
import { extraBinderApi } from '../../services/api'
import { useCurrency } from '../../stores/currency'
import { variantLabel } from '../../utils/catalog'
import { formatMoney } from '../../utils/format'
import { t } from '../../utils/i18n'
import { usePageMeta } from '../../utils/pageMeta'

/** One extra binder with its cards and value (Premium, phase 12). Read only once Premium has ended. */
const props = defineProps({
  id: { type: String, required: true },
})

const router = useRouter()
const { formatPrice } = useCurrency()

const binder = ref(null)
const notFound = ref(false)
const error = ref('')
const busy = ref(false)
const renaming = ref(false)
const newName = ref('')

const premium = computed(() => binder.value?.premium === true)
const copies = computed(() => binder.value?.entries.reduce((sum, entry) => sum + entry.quantity, 0) ?? 0)

async function load() {
  error.value = ''
  notFound.value = false
  try {
    binder.value = await extraBinderApi.get(props.id)
  } catch (exception) {
    if (exception.status === 404 || exception.status === 400) notFound.value = true
    else error.value = exception.message
  }
}

async function run(action) {
  error.value = ''
  busy.value = true
  try {
    await action()
  } catch (exception) {
    error.value = exception.message
  } finally {
    busy.value = false
  }
}

const setQuantity = (entry, quantity) =>
  run(async () => {
    binder.value = await extraBinderApi.setQuantity(binder.value.id, entry.id, quantity)
  })

function startRename() {
  newName.value = binder.value.name
  renaming.value = true
}

const saveName = () =>
  run(async () => {
    binder.value = await extraBinderApi.rename(binder.value.id, newName.value)
    renaming.value = false
  })

const remove = () => {
  if (!window.confirm(t('binders.deleteConfirm'))) return
  run(async () => {
    await extraBinderApi.remove(binder.value.id)
    await router.push({ name: 'binders' })
  })
}

const cardLink = (card) => ({ name: 'card', params: { game: card.game, setCode: card.setCode, cardId: card.id } })
const cardsLabel = (count) => (count === 1 ? t('binders.cardsOne') : t('binders.cards', { count }))

watch(() => props.id, load, { immediate: true })

usePageMeta(() => (binder.value ? { title: binder.value.name, noindex: true } : null))
</script>

<template>
  <NotFoundView v-if="notFound" />

  <template v-else>
    <CatalogBand accent="pokemon">
      <BreadcrumbTrail
        :items="[
          { label: t('catalog.home'), to: '/' },
          { label: t('collection.title'), to: '/collectie' },
          { label: t('binders.title'), to: '/collectie/binders' },
          { label: binder?.name ?? '…' },
        ]"
      />
      <h1 class="title">{{ binder?.name ?? t('binders.loading') }}</h1>
    </CatalogBand>

    <div class="container page">
      <FormMessage v-if="error">{{ error }}</FormMessage>

      <template v-if="binder">
        <p v-if="!premium" class="notice">{{ t('binders.readOnly') }}</p>

        <div class="summary">
          <div>
            <p class="value-label">{{ t('collection.value') }}</p>
            <p class="value">{{ formatMoney(binder.valueCents, binder.currency) }}</p>
            <p class="note">
              {{ cardsLabel(copies) }}
              <template v-if="binder.unpricedCards"> · {{ t('binders.unpriced', { count: binder.unpricedCards }) }}</template>
            </p>
          </div>

          <div v-if="premium" class="manage">
            <form v-if="renaming" class="rename" @submit.prevent="saveName">
              <label class="visually-hidden" for="binder-rename">{{ t('binders.name') }}</label>
              <input id="binder-rename" v-model="newName" class="input" maxlength="60" required />
              <button class="btn btn-primary" type="submit" :disabled="busy">{{ t('binders.renameSave') }}</button>
              <button class="btn btn-secondary" type="button" @click="renaming = false">{{ t('binders.cancel') }}</button>
            </form>
            <template v-else>
              <button class="link" type="button" @click="startRename"><Pencil :size="16" aria-hidden="true" /> {{ t('binders.rename') }}</button>
              <button class="link danger" type="button" :disabled="busy" @click="remove">
                <Trash2 :size="16" aria-hidden="true" /> {{ t('binders.delete') }}
              </button>
            </template>
          </div>
        </div>

        <p v-if="!binder.entries.length" class="note empty">{{ t('binders.noCards') }}</p>

        <ul v-else class="cards">
          <li v-for="entry in binder.entries" :key="entry.id" class="card">
            <router-link :to="cardLink(entry.card)" class="image-link" tabindex="-1" aria-hidden="true">
              <CardImage :src="entry.card.imageUrl" :alt="''" />
            </router-link>
            <router-link class="name" :to="cardLink(entry.card)">{{ entry.card.name }}</router-link>
            <p class="meta">{{ entry.card.setName }} · {{ entry.card.number }} · {{ variantLabel(entry.variant) }}</p>
            <p class="price">
              {{ entry.priceCents === null ? t('catalog.noPrice') : formatPrice(entry.priceCents, entry.priceCurrency) }}
            </p>
            <QuantityStepper
              v-if="premium"
              :quantity="entry.quantity"
              :name="`${entry.card.name}, ${variantLabel(entry.variant)}`"
              :disabled="busy"
              @increase="setQuantity(entry, entry.quantity + 1)"
              @decrease="setQuantity(entry, entry.quantity - 1)"
            />
            <p v-else class="meta">{{ entry.quantity }}×</p>
          </li>
        </ul>
      </template>
    </div>
  </template>
</template>

<style scoped>
.title {
  margin-top: var(--space-3);
  overflow-wrap: anywhere;
}

.page {
  padding-block: var(--space-5) var(--space-7);
}

.notice {
  max-width: 65ch;
  margin-bottom: var(--space-4);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-sm);
  background: var(--color-warning-soft);
  color: var(--color-warning);
  font-size: var(--text-sm);
}

.summary {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
}

.value-label {
  font-size: var(--text-sm);
  font-weight: 600;
}

.value {
  font-family: var(--font-display);
  font-size: var(--text-2xl);
  font-weight: 800;
  line-height: 1.1;
}

.manage {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-4);
}

.rename {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.input {
  height: var(--control-height);
  padding-inline: var(--space-3);
  border: 1.5px solid var(--color-line-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-ink);
  font-size: var(--text-md);
}

.link {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  min-height: var(--touch-target);
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-link);
  cursor: pointer;
  font-size: var(--text-sm);
  font-weight: 600;
}

.link.danger {
  color: var(--color-danger);
}

.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 9.5rem), 1fr));
  gap: var(--space-5) var(--space-4);
  margin: var(--space-5) 0 0;
  padding: 0;
  list-style: none;
}

.card {
  display: grid;
  align-content: start;
  gap: var(--space-1);
  min-width: 0;
}

.name {
  margin-top: var(--space-1);
  color: var(--color-ink);
  font-size: var(--text-sm);
  font-weight: 650;
  text-decoration: none;
  overflow-wrap: anywhere;
}

@media (hover: hover) {
  .name:hover {
    color: var(--color-brand-ink);
    text-decoration: underline;
  }
}

.meta {
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.price {
  font-size: var(--text-sm);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.note {
  margin-top: var(--space-1);
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.empty {
  margin-top: var(--space-5);
}
</style>
