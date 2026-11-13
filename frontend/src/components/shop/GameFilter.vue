<script setup>
import { useRoute } from 'vue-router'
import { GAMES } from '../../data/games'
import { t } from '../../utils/i18n'

/** Chips that filter a page on a game through `?game=`, so the view can be shared. */
defineProps({
  current: { type: String, default: null },
  label: { type: String, required: true },
})

const route = useRoute()
const to = (game) => ({ path: route.path, query: game ? { ...route.query, game } : { ...route.query, game: undefined } })
</script>

<template>
  <nav class="filter" :aria-label="label">
    <router-link class="chip" :class="{ active: !current }" :to="to(null)" :aria-current="!current ? 'page' : undefined">
      {{ t('releases.allGames') }}
    </router-link>
    <router-link
      v-for="game in GAMES"
      :key="game.slug"
      class="chip"
      :class="{ active: current === game.slug }"
      :data-game="game.accent"
      :to="to(game.slug)"
      :aria-current="current === game.slug ? 'page' : undefined"
    >
      {{ t(game.labelKey) }}
    </router-link>
  </nav>
</template>

<style scoped>
.filter {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.chip {
  display: inline-flex;
  align-items: center;
  min-height: var(--touch-target);
  padding-inline: var(--space-3);
  border: 1.5px solid var(--color-line);
  border-radius: var(--radius-round);
  color: var(--color-ink);
  font-size: var(--text-sm);
  font-weight: 600;
  text-decoration: none;
}

.chip:hover {
  border-color: var(--accent);
}

.chip.active {
  border-color: var(--accent);
  background: var(--accent);
  color: var(--accent-ink);
}
</style>
