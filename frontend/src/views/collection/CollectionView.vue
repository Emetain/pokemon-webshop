<script setup>
import { computed, onMounted, ref } from 'vue'
import BreadcrumbTrail from '../../components/catalog/BreadcrumbTrail.vue'
import CatalogBand from '../../components/catalog/CatalogBand.vue'
import CatalogNotice from '../../components/catalog/CatalogNotice.vue'
import CollectionSummary from '../../components/collection/CollectionSummary.vue'
import GuestInvite from '../../components/collection/GuestInvite.vue'
import ProgressBar from '../../components/collection/ProgressBar.vue'
import ValueChart from '../../components/collection/ValueChart.vue'
import { collectionApi, pokedexApi } from '../../services/api'
import { useSession } from '../../stores/session'
import { t } from '../../utils/i18n'

const session = useSession()

const collection = ref(null)
const history = ref(null)
const pokedex = ref(null)
const loading = ref(true)
const error = ref('')

const loggedIn = computed(() => session.isLoggedIn.value)
const empty = computed(() => collection.value?.copies === 0)

async function load() {
  if (!loggedIn.value) return
  loading.value = true
  error.value = ''
  try {
    const [overview, dex, valueHistory] = await Promise.all([
      collectionApi.overview(),
      pokedexApi.all(),
      // The page is still complete without the graph.
      collectionApi.history().catch(() => null),
    ])
    collection.value = overview
    history.value = valueHistory
    pokedex.value = dex.progress.total > 0 ? dex.progress : null
  } catch (exception) {
    error.value = exception.message
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <CatalogBand accent="pokemon">
    <BreadcrumbTrail :items="[{ label: t('catalog.home'), to: '/' }, { label: t('collection.title') }]" />
    <h1 class="title">{{ t('collection.title') }}</h1>
  </CatalogBand>

  <div class="container page">
    <GuestInvite v-if="!loggedIn" :title="t('collection.guest.title')" :text="t('collection.guest.text')" />

    <CatalogNotice v-else-if="error" :title="error">
      <button class="btn btn-secondary" type="button" @click="load">{{ t('catalog.retry') }}</button>
    </CatalogNotice>

    <p v-else-if="loading" class="loading" role="status">{{ t('catalog.loading') }}</p>

    <CatalogNotice v-else-if="empty" :title="t('collection.empty.title')" :text="t('collection.empty.text')">
      <router-link class="btn btn-primary" :to="{ name: 'sets', params: { game: 'pokemon' } }">
        {{ t('collection.empty.action') }}
      </router-link>
    </CatalogNotice>

    <CollectionSummary v-else :collection="collection">
      <template #chart>
        <ValueChart v-if="history" :points="history.points" :currency="history.currency" />
      </template>

      <section v-if="pokedex" class="pokedex" data-game="pokemon">
        <div class="pokedex-head">
          <h2>{{ t('pokedex.title') }}</h2>
          <router-link to="/collectie/pokedex">{{ t('collection.toPokedex') }}</router-link>
        </div>
        <ProgressBar :progress="pokedex" :name="t('pokedex.title')" />
      </section>
    </CollectionSummary>
  </div>
</template>

<style scoped>
.title {
  margin-top: var(--space-3);
}

.page {
  padding-block: var(--space-5) 0;
}

.loading {
  color: var(--color-ink-soft);
}

.pokedex {
  margin-top: var(--space-7);
}

.pokedex-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-1) var(--space-3);
  margin-bottom: var(--space-3);
  font-size: var(--text-sm);
}
</style>
