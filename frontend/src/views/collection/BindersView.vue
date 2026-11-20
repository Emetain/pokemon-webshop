<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { BookCopy, Lock } from 'lucide-vue-next'
import BreadcrumbTrail from '../../components/catalog/BreadcrumbTrail.vue'
import CatalogBand from '../../components/catalog/CatalogBand.vue'
import FormMessage from '../../components/FormMessage.vue'
import { extraBinderApi } from '../../services/api'
import { openPremium } from '../../stores/premiumDialog'
import { formatMoney } from '../../utils/format'
import { t } from '../../utils/i18n'

/** The extra binders of a customer (Premium, phase 12). A former member still sees them, without changing them. */
const router = useRouter()

const list = ref(null)
const error = ref('')
const name = ref('')
const creating = ref(false)

const premium = computed(() => list.value?.premium === true)
const atLimit = computed(() => list.value && list.value.binders.length >= list.value.max)

async function load() {
  error.value = ''
  try {
    list.value = await extraBinderApi.list()
  } catch (exception) {
    error.value = exception.message
  }
}

async function create() {
  if (!premium.value) {
    openPremium('binders')
    return
  }
  error.value = ''
  creating.value = true
  try {
    const binder = await extraBinderApi.create(name.value)
    await router.push({ name: 'binder', params: { id: binder.id } })
  } catch (exception) {
    error.value = exception.message
  } finally {
    creating.value = false
  }
}

const cardsLabel = (count) => (count === 1 ? t('binders.cardsOne') : t('binders.cards', { count }))

onMounted(load)
</script>

<template>
  <CatalogBand accent="pokemon">
    <BreadcrumbTrail
      :items="[{ label: t('catalog.home'), to: '/' }, { label: t('collection.title'), to: '/collectie' }, { label: t('binders.title') }]"
    />
    <h1 class="title">{{ t('binders.title') }}</h1>
  </CatalogBand>

  <div class="container page">
    <p class="intro">{{ t('binders.intro') }}</p>

    <FormMessage v-if="error">{{ error }}</FormMessage>
    <p v-if="!list && !error" class="note" role="status">{{ t('binders.loading') }}</p>

    <template v-if="list">
      <p v-if="!premium && list.binders.length" class="notice">{{ t('binders.readOnly') }}</p>

      <ul v-if="list.binders.length" class="binders">
        <li v-for="binder in list.binders" :key="binder.id">
          <router-link class="binder" :to="{ name: 'binder', params: { id: binder.id } }">
            <BookCopy :size="24" aria-hidden="true" />
            <span class="name">{{ binder.name }}</span>
            <span class="facts">
              {{ cardsLabel(binder.copies) }}
              <template v-if="binder.unpricedCards"> · {{ t('binders.unpriced', { count: binder.unpricedCards }) }}</template>
            </span>
            <strong class="value">{{ formatMoney(binder.valueCents, binder.currency) }}</strong>
          </router-link>
        </li>
      </ul>
      <p v-else class="note">{{ t('binders.empty') }}</p>

      <form v-if="premium" class="create" @submit.prevent="create">
        <label class="label" for="binder-name">{{ t('binders.name') }}</label>
        <div class="row">
          <input
            id="binder-name"
            v-model="name"
            class="input"
            maxlength="60"
            required
            :placeholder="t('binders.namePlaceholder')"
            :disabled="atLimit"
          />
          <button class="btn btn-primary" type="submit" :disabled="creating || atLimit">
            {{ creating ? t('binders.creating') : t('binders.create') }}
          </button>
        </div>
        <p v-if="atLimit" class="note">{{ t('binders.limit', { max: list.max }) }}</p>
      </form>
      <button v-else class="btn btn-primary unlock" type="button" aria-haspopup="dialog" @click="openPremium('binders')">
        <Lock :size="16" aria-hidden="true" /> {{ t('binders.unlock') }}
      </button>
    </template>
  </div>
</template>

<style scoped>
.title {
  margin-top: var(--space-3);
}

.page {
  padding-block: var(--space-5) var(--space-7);
}

.intro {
  max-width: 65ch;
  color: var(--color-ink-soft);
}

.notice {
  max-width: 65ch;
  margin-top: var(--space-4);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-sm);
  background: var(--color-warning-soft);
  color: var(--color-warning);
  font-size: var(--text-sm);
}

.binders {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 16rem), 1fr));
  gap: var(--space-3);
  margin: var(--space-5) 0 0;
  padding: 0;
  list-style: none;
}

.binder {
  display: grid;
  gap: var(--space-1);
  padding: var(--space-4);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-ink);
  text-decoration: none;
}

.binder svg {
  color: var(--color-brand-ink);
}

@media (hover: hover) {
  .binder:hover {
    border-color: var(--color-brand);
  }
}

.name {
  font-size: var(--text-lg);
  font-weight: 700;
  overflow-wrap: anywhere;
}

.facts {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.value {
  font-size: var(--text-lg);
  font-variant-numeric: tabular-nums;
}

.create {
  max-width: 32rem;
  margin-top: var(--space-6);
}

.label {
  font-size: var(--text-sm);
  font-weight: 600;
}

.row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-1);
}

.input {
  flex: 1 1 12rem;
  height: var(--control-height);
  padding-inline: var(--space-3);
  border: 1.5px solid var(--color-line-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-ink);
  font-size: var(--text-md);
}

.unlock {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-6);
}

.note {
  margin-top: var(--space-3);
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}
</style>
