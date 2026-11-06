<script setup>
import { computed } from 'vue'
import ProgressBar from './ProgressBar.vue'
import CardGrid from '../catalog/CardGrid.vue'
import { GAMES, findGame } from '../../data/games'
import { formatMoney, formatNumber } from '../../utils/format'
import { t } from '../../utils/i18n'

/** The numbers of a collection, for its owner and on the public page. */
const props = defineProps({
  collection: { type: Object, required: true },
})

const gameLabel = (slug) => t(findGame(slug).labelKey)

const games = computed(() => {
  const order = GAMES.map((game) => game.slug)
  return [...props.collection.games].sort((a, b) => order.indexOf(a.game) - order.indexOf(b.game))
})

/** Grouped per game, in the order of the games. */
const setGroups = computed(() =>
  GAMES.map((game) => ({
    game,
    sets: props.collection.sets.filter((set) => set.game === game.slug),
  })).filter((group) => group.sets.length),
)

const unpricedText = computed(() => {
  const count = props.collection.value.unpricedCards
  if (count === 0) return ''
  return count === 1 ? t('collection.unpricedOne') : t('collection.unpriced', { count: formatNumber(count) })
})

const copiesText = computed(() => {
  const count = props.collection.copies
  return count === 1 ? t('collection.copiesOne') : t('collection.copies', { count: formatNumber(count) })
})
</script>

<template>
  <section>
    <p class="value-label">{{ t('collection.value') }}</p>
    <p class="value">{{ formatMoney(collection.value.cents, collection.value.currency) }}</p>

    <!-- The graph of the owner. The public page leaves it out. -->
    <slot name="chart" />

    <div class="total-foot">
      <div>
        <p class="note">{{ copiesText }}</p>
        <p v-if="unpricedText" class="note">{{ unpricedText }}</p>
        <p class="note">{{ t('collection.valueNote') }}</p>
      </div>
      <div>
        <h2 class="overall-title">{{ t('collection.overall') }}</h2>
        <ProgressBar :progress="collection.progress" :name="t('collection.overall')" />
      </div>
    </div>
  </section>

  <section class="block">
    <h2>{{ t('collection.perGame') }}</h2>
    <ul class="games">
      <li v-for="game in games" :key="game.game" class="game" :data-game="findGame(game.game).accent">
        <div class="game-head">
          <router-link class="game-name" :to="{ name: 'sets', params: { game: game.game } }">
            {{ gameLabel(game.game) }}
          </router-link>
          <span class="game-value">{{ formatMoney(game.value.cents, game.value.currency) }}</span>
        </div>
        <ProgressBar :progress="game.progress" :name="gameLabel(game.game)" />
        <p class="note">
          {{
            t('collection.masterHint', {
              owned: formatNumber(game.masterProgress.owned),
              total: formatNumber(game.masterProgress.total),
            })
          }}
        </p>
      </li>
    </ul>
  </section>

  <slot />

  <section v-if="collection.mostValuable.length" class="block">
    <h2 class="block-title">{{ t('collection.mostValuable') }}</h2>
    <CardGrid :cards="collection.mostValuable" show-set />
  </section>

  <section v-if="setGroups.length" class="block">
    <h2 class="block-title">{{ t('collection.sets') }}</h2>
    <div v-for="group in setGroups" :key="group.game.slug" class="set-group" :data-game="group.game.accent">
      <h3 class="set-game">{{ t(group.game.labelKey) }}</h3>
      <ul class="sets">
        <li v-for="set in group.sets" :key="set.code" class="set">
          <router-link :to="{ name: 'set', params: { game: set.game, setCode: set.code } }">{{ set.name }}</router-link>
          <ProgressBar :progress="set.progress" :name="set.name" compact />
        </li>
      </ul>
    </div>
  </section>
</template>

<style scoped>
.total-foot {
  display: grid;
  gap: var(--space-5);
  margin-top: var(--space-5);
}

.value-label {
  font-size: var(--text-sm);
  font-weight: 600;
}

.value {
  font-family: var(--font-display);
  font-size: var(--text-hero);
  font-stretch: 80%;
  font-weight: 800;
  line-height: 1;
  overflow-wrap: anywhere;
}

.note {
  margin-top: var(--space-1);
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.overall-title {
  margin-bottom: var(--space-2);
  font-size: var(--text-lg);
}

.block {
  margin-top: var(--space-7);
}

.block-title {
  margin-bottom: var(--space-4);
}

.games {
  display: grid;
  gap: var(--space-5) var(--space-6);
  margin: var(--space-4) 0 0;
  padding: 0;
  list-style: none;
}

.game-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-2);
}

.game-name {
  color: var(--accent);
  font-family: var(--font-display);
  font-size: var(--text-lg);
  font-stretch: 88%;
  font-weight: 750;
}

.game-value {
  font-weight: 700;
}

.set-group + .set-group {
  margin-top: var(--space-5);
}

.set-game {
  margin-bottom: var(--space-2);
  color: var(--accent);
}

.sets {
  display: grid;
  gap: var(--space-3) var(--space-6);
  margin: 0;
  padding: 0;
  list-style: none;
}

.set {
  display: grid;
  gap: var(--space-1);
  min-width: 0;
  font-size: var(--text-sm);
}

@media (min-width: 768px) {
  .total-foot {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    align-items: end;
    gap: var(--space-7);
  }

  .games,
  .sets {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (min-width: 1100px) {
  .sets {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
