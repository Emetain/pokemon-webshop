<script setup>
import { computed, ref, watch } from 'vue'
import BreadcrumbTrail from '../../components/catalog/BreadcrumbTrail.vue'
import CatalogBand from '../../components/catalog/CatalogBand.vue'
import CatalogNotice from '../../components/catalog/CatalogNotice.vue'
import SetTile from '../../components/catalog/SetTile.vue'
import { findGame } from '../../data/games'
import { catalogApi, collectionApi } from '../../services/api'
import { useSession } from '../../stores/session'
import { seriesLabel } from '../../utils/catalog'
import { t } from '../../utils/i18n'

const props = defineProps({
  game: { type: String, required: true },
})

const session = useSession()

const sets = ref([])
const loading = ref(true)
const error = ref('')
// Per set code: how far the customer is. Only sets they have cards of.
const progress = ref({})

const game = computed(() => findGame(props.game))
const gameLabel = computed(() => (game.value ? t(game.value.labelKey) : ''))

/** Sets arrive newest first, so a series sits where its newest set is. */
const groups = computed(() => {
  const bySeries = new Map()
  for (const set of sets.value) {
    if (!bySeries.has(set.series)) bySeries.set(set.series, [])
    bySeries.get(set.series).push(set)
  }
  return [...bySeries].map(([series, seriesSets], index) => ({
    id: `serie-${index + 1}`,
    label: seriesLabel(series),
    sets: seriesSets,
  }))
})

const setCountLabel = (count) => (count === 1 ? t('catalog.oneSet') : t('catalog.setCount', { count }))

let latestRequest = 0

async function load() {
  if (!game.value) return
  const request = ++latestRequest
  loading.value = true
  error.value = ''
  try {
    const result = await catalogApi.sets(props.game)
    if (request === latestRequest) sets.value = result
  } catch (exception) {
    if (request === latestRequest) error.value = exception.message
  } finally {
    if (request === latestRequest) loading.value = false
  }
}

async function loadProgress() {
  progress.value = {}
  if (!game.value || !session.isLoggedIn.value) return
  try {
    const started = await collectionApi.startedSets(props.game)
    progress.value = Object.fromEntries(started.map((set) => [set.code, set.progress]))
  } catch {
    // The list of sets still works without the bars.
  }
}

watch(() => props.game, load, { immediate: true })
watch(() => props.game, loadProgress, { immediate: true })
</script>

<template>
  <div v-if="!game" class="container">
    <CatalogNotice :title="t('catalog.notFound.gameTitle')" :text="t('catalog.notFound.text')">
      <router-link class="btn btn-primary" :to="{ name: 'sets', params: { game: 'pokemon' } }">
        {{ t('catalog.notFound.toPokemon') }}
      </router-link>
    </CatalogNotice>
  </div>

  <div v-else :data-game="game.accent">
    <CatalogBand :accent="game.accent">
      <BreadcrumbTrail :items="[{ label: t('catalog.home'), to: '/' }, { label: gameLabel }]" />
      <h1 class="title">{{ t('catalog.gameTitle', { game: gameLabel }) }}</h1>
      <p class="intro">{{ t('catalog.gameIntro', { game: gameLabel }) }}</p>
    </CatalogBand>

    <div class="container page">
      <CatalogNotice v-if="error" :title="error">
        <button class="btn btn-secondary" type="button" @click="load">{{ t('catalog.retry') }}</button>
      </CatalogNotice>

      <ul v-else-if="loading" class="sets" :aria-label="t('catalog.loadingSets')" aria-busy="true">
        <li v-for="tile in 10" :key="tile" class="skeleton"></li>
      </ul>

      <CatalogNotice
        v-else-if="!sets.length"
        :title="t('catalog.empty.gameTitle')"
        :text="t('catalog.empty.gameText')"
      />

      <template v-else>
        <nav v-if="groups.length > 1" class="series-nav" :aria-label="t('catalog.seriesNavigation')">
          <router-link v-for="group in groups" :key="group.id" class="series-link" :to="{ hash: `#${group.id}` }">
            {{ group.label }}
          </router-link>
        </nav>

        <section v-for="group in groups" :id="group.id" :key="group.id" class="series">
          <div class="series-head">
            <h2>{{ group.label }}</h2>
            <span class="series-count">{{ setCountLabel(group.sets.length) }}</span>
          </div>
          <ul class="sets">
            <li v-for="set in group.sets" :key="set.code">
              <SetTile :set="set" :progress="progress[set.code]" />
            </li>
          </ul>
        </section>
      </template>
    </div>
  </div>
</template>

<style scoped>
.title {
  margin-top: var(--space-3);
}

.intro {
  margin-top: var(--space-1);
}

.page {
  padding-block: var(--space-5) 0;
}

/* One line that scrolls on a phone, because Magic alone has more than twenty series. */
.series-nav {
  display: flex;
  gap: var(--space-2);
  margin-inline: calc(var(--gutter) * -1);
  padding: var(--space-half) var(--gutter) var(--space-2);
  overflow-x: auto;
}

.series-link {
  flex: none;
  padding: var(--space-1) var(--space-3);
  border-radius: var(--radius-round);
  background: var(--color-tint);
  color: var(--color-ink);
  font-size: var(--text-sm);
  font-weight: 550;
  text-decoration: none;
}

.series-link:hover {
  background: var(--color-tint-strong);
}

.series {
  margin-top: var(--space-5);
}

.series-head {
  display: flex;
  align-items: baseline;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.series-count {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.sets {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.skeleton {
  aspect-ratio: 4 / 3;
  border-radius: var(--radius-md);
  background: var(--color-tint);
}

@media (min-width: 640px) {
  .sets {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (min-width: 768px) {
  .series-nav {
    flex-wrap: wrap;
    margin-inline: 0;
    padding-inline: 0;
    overflow-x: visible;
  }

  .sets {
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: var(--space-4);
  }
}

@media (min-width: 1200px) {
  .sets {
    grid-template-columns: repeat(5, minmax(0, 1fr));
  }
}
</style>
