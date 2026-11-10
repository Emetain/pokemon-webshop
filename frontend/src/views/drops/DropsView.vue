<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { Zap } from 'lucide-vue-next'
import DropCard from '../../components/drops/DropCard.vue'
import FormMessage from '../../components/FormMessage.vue'
import { dropApi } from '../../services/api'
import { t } from '../../utils/i18n'

/** Live drops first, then what's coming, then the last drops that ended. */
const drops = ref(null)
const error = ref('')

const REFRESH_MS = 30_000
let timer = null

async function load() {
  try {
    drops.value = await dropApi.list()
    error.value = ''
  } catch (exception) {
    error.value = exception.message
  }
}

onMounted(() => {
  load()
  timer = setInterval(load, REFRESH_MS)
})

onBeforeUnmount(() => clearInterval(timer))
</script>

<template>
  <div class="container page">
    <header class="head">
      <h1><Zap class="title-icon" :size="28" aria-hidden="true" />{{ t('drops.title') }}</h1>
      <p class="intro">{{ t('drops.intro') }}</p>
    </header>

    <FormMessage v-if="error">{{ error }}</FormMessage>
    <p v-else-if="!drops" class="muted" aria-busy="true">{{ t('drops.loading') }}</p>

    <template v-else>
      <section v-if="drops.live.length" class="section" aria-labelledby="drops-live">
        <h2 id="drops-live" class="section-title live-title">{{ t('drops.live') }}</h2>
        <ul class="grid">
          <li v-for="drop in drops.live" :key="drop.slug">
            <DropCard :drop="drop" :server-time="drops.serverTime" />
          </li>
        </ul>
      </section>

      <section class="section" aria-labelledby="drops-upcoming">
        <h2 id="drops-upcoming" class="section-title">{{ t('drops.upcoming') }}</h2>
        <ul v-if="drops.upcoming.length" class="grid">
          <li v-for="drop in drops.upcoming" :key="drop.slug">
            <DropCard :drop="drop" :server-time="drops.serverTime" @started="load" />
          </li>
        </ul>
        <p v-else-if="!drops.live.length" class="empty">{{ t('drops.empty') }}</p>
      </section>

      <section v-if="drops.past.length" class="section" aria-labelledby="drops-past">
        <h2 id="drops-past" class="section-title">{{ t('drops.past') }}</h2>
        <ul class="grid past">
          <li v-for="drop in drops.past" :key="drop.slug">
            <DropCard :drop="drop" />
          </li>
        </ul>
      </section>
    </template>
  </div>
</template>

<style scoped>
.page {
  padding-block: var(--space-5) 0;
}

.head {
  max-width: 44rem;
}

h1 {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.title-icon {
  color: var(--color-brand);
}

.intro {
  margin-top: var(--space-2);
  color: var(--color-ink-soft);
  font-size: var(--text-lg);
}

.muted {
  margin-top: var(--space-4);
  color: var(--color-ink-soft);
}

.section {
  margin-top: var(--space-6);
}

.section-title {
  font-size: var(--text-xl);
}

.live-title {
  color: var(--color-sale);
}

.grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--space-4);
  margin: var(--space-4) 0 0;
  padding: 0;
  list-style: none;
}

.empty {
  margin-top: var(--space-3);
  padding: var(--space-5);
  border: 1px dashed var(--color-line-strong);
  border-radius: var(--radius-lg);
  color: var(--color-ink-soft);
}

@media (min-width: 640px) {
  .grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (min-width: 1024px) {
  .grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .grid.past {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
</style>
