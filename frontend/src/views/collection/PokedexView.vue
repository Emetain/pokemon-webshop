<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import BreadcrumbTrail from '../../components/catalog/BreadcrumbTrail.vue'
import CardImage from '../../components/catalog/CardImage.vue'
import CatalogBand from '../../components/catalog/CatalogBand.vue'
import CatalogNotice from '../../components/catalog/CatalogNotice.vue'
import GuestInvite from '../../components/collection/GuestInvite.vue'
import ProgressBar from '../../components/collection/ProgressBar.vue'
import { pokedexApi } from '../../services/api'
import { useSession } from '../../stores/session'
import { dexLabel } from '../../utils/catalog'
import { t } from '../../utils/i18n'

const SKELETON_SLOTS = 24

const props = defineProps({
  generatie: { type: String, default: '' },
})

const router = useRouter()
const session = useSession()

const pokedex = ref(null)
const loading = ref(true)
const error = ref('')

const generation = computed(() => Number.parseInt(props.generatie, 10) || null)

const slots = computed(() => {
  const all = pokedex.value?.slots ?? []
  return generation.value ? all.filter((slot) => slot.generation === generation.value) : all
})

const shownProgress = computed(() => {
  if (!pokedex.value) return null
  const chosen = pokedex.value.generations.find((item) => item.generation === generation.value)
  return chosen?.progress ?? pokedex.value.progress
})

const progressName = computed(() =>
  generation.value ? t('pokedex.generation', { number: generation.value }) : t('pokedex.title'),
)

function choose(number) {
  router.push({ query: number ? { generatie: String(number) } : {} })
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    pokedex.value = await pokedexApi.all()
  } catch (exception) {
    error.value = exception.message
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div data-game="pokemon">
    <CatalogBand accent="pokemon">
      <BreadcrumbTrail
        :items="[
          { label: t('catalog.home'), to: '/' },
          { label: t('collection.title'), to: '/collectie' },
          { label: t('pokedex.title') },
        ]"
      />
      <h1 class="title">{{ t('pokedex.title') }}</h1>
      <p class="intro">{{ t('pokedex.intro') }}</p>
    </CatalogBand>

    <div class="container page">
      <GuestInvite
        v-if="!session.isLoggedIn.value"
        class="guest"
        :title="t('pokedex.guestTitle')"
        :text="t('pokedex.guestText')"
      />

      <CatalogNotice v-if="error" :title="error">
        <button class="btn btn-secondary" type="button" @click="load">{{ t('catalog.retry') }}</button>
      </CatalogNotice>

      <ul v-else-if="loading" class="slots" :aria-label="t('pokedex.loading')" aria-busy="true">
        <li v-for="slot in SKELETON_SLOTS" :key="slot" class="pocket skeleton"></li>
      </ul>

      <CatalogNotice v-else-if="!pokedex.slots.length" :title="t('pokedex.emptyTitle')" :text="t('pokedex.emptyText')" />

      <template v-else>
        <ProgressBar v-if="session.isLoggedIn.value" class="total" :progress="shownProgress" :name="progressName" />

        <nav class="generations" :aria-label="t('pokedex.generations')">
          <button class="generation" type="button" :aria-pressed="!generation" @click="choose(null)">
            {{ t('pokedex.all') }}
          </button>
          <button
            v-for="item in pokedex.generations"
            :key="item.generation"
            class="generation"
            type="button"
            :aria-pressed="generation === item.generation"
            @click="choose(item.generation)"
          >
            {{ t('pokedex.generation', { number: item.generation }) }}
          </button>
        </nav>

        <ul v-if="session.isLoggedIn.value" class="generation-bars">
          <li v-for="item in pokedex.generations" :key="item.generation">
            <p class="generation-name">{{ t('pokedex.generation', { number: item.generation }) }}</p>
            <ProgressBar
              :progress="item.progress"
              :name="t('pokedex.generation', { number: item.generation })"
              compact
            />
          </li>
        </ul>

        <ul class="slots">
          <li v-for="slot in slots" :key="slot.dexNumber">
            <router-link
              class="slot"
              :to="{ name: 'pokedex-entry', params: { dexNumber: slot.dexNumber } }"
              :aria-label="t('pokedex.open', { name: slot.name })"
            >
              <span v-if="slot.filled" class="pocket filled">
                <CardImage :src="slot.imageUrl" :alt="t('pokedex.filled', { name: slot.name })" />
              </span>
              <span v-else class="pocket"></span>
              <span class="number">{{ dexLabel(slot.dexNumber) }}</span>
              <span class="name">{{ slot.name }}</span>
            </router-link>
          </li>
        </ul>
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

.guest {
  margin-bottom: var(--space-5);
}

.total {
  margin-bottom: var(--space-4);
}

/* One line that scrolls on a phone, like the series on the list of sets. */
.generations {
  display: flex;
  gap: var(--space-2);
  margin-inline: calc(var(--gutter) * -1);
  padding: var(--space-half) var(--gutter) var(--space-2);
  overflow-x: auto;
}

.generation {
  flex: none;
  min-height: var(--touch-target);
  padding-inline: var(--space-3);
  border: 0;
  border-radius: var(--radius-round);
  background: var(--color-tint);
  color: var(--color-ink);
  cursor: pointer;
  font-size: var(--text-sm);
  font-weight: 550;
}

.generation:hover {
  background: var(--color-tint-strong);
}

.generation[aria-pressed='true'] {
  background: var(--accent);
  color: var(--accent-ink);
}

.generation-bars {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3) var(--space-4);
  margin: var(--space-3) 0 var(--space-5);
  padding: 0;
  list-style: none;
}

.generation-name {
  margin-bottom: var(--space-1);
  font-size: var(--text-xs);
  font-weight: 600;
}

.slots {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-4) var(--space-3);
  margin: var(--space-4) 0 0;
  padding: 0;
  list-style: none;
}

.slot {
  display: block;
  color: var(--color-ink);
  text-decoration: none;
}

/* The same empty pocket as in the binder on the home page. */
.pocket {
  display: block;
  aspect-ratio: 63 / 88;
  border: 1.5px dashed var(--color-line-strong);
  border-radius: var(--radius-card);
  background: var(--color-tint);
}

.slot:hover .pocket:not(.filled) {
  border-color: var(--color-brand);
}

.pocket.filled {
  border: 0;
  background: none;
}

.pocket.skeleton {
  border-style: solid;
  border-color: var(--color-tint);
}

.number {
  display: block;
  margin-top: var(--space-1);
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
}

.name {
  display: block;
  font-size: var(--text-sm);
  font-weight: 600;
  line-height: 1.25;
  overflow-wrap: anywhere;
}

.slot:hover .name {
  color: var(--color-brand);
  text-decoration: underline;
}

@media (min-width: 560px) {
  .slots {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .generation-bars {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (min-width: 768px) {
  .generations {
    flex-wrap: wrap;
    margin-inline: 0;
    padding-inline: 0;
    overflow-x: visible;
  }

  .slots {
    grid-template-columns: repeat(6, minmax(0, 1fr));
  }

  .generation-bars {
    grid-template-columns: repeat(5, minmax(0, 1fr));
  }
}

@media (min-width: 1100px) {
  .slots {
    grid-template-columns: repeat(8, minmax(0, 1fr));
  }
}
</style>
