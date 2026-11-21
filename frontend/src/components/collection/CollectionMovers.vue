<script setup>
import { computed, ref, watch } from 'vue'
import { Lock, TrendingDown, TrendingUp } from 'lucide-vue-next'
import CardImage from '../catalog/CardImage.vue'
import { collectionApi } from '../../services/api'
import { openPremium } from '../../stores/premiumDialog'
import { useSession } from '../../stores/session'
import { variantLabel } from '../../utils/catalog'
import { formatMoney, formatPercent, formatShortDate } from '../../utils/format'
import { t } from '../../utils/i18n'
import PageSkeleton from '../skeleton/PageSkeleton.vue'

/** The owned cards that rose and fell most in a day, a week or a month (Premium, phase 12). */
const PERIODS = ['1D', '1W', '1M']

const session = useSession()
const premium = computed(() => session.isPremium.value)

const period = ref('1W')
const result = ref(null)
const loading = ref(false)
const error = ref('')
let latest = 0

async function load() {
  if (!premium.value) return
  const request = ++latest
  loading.value = true
  error.value = ''
  try {
    const answer = await collectionApi.movers(period.value)
    if (request === latest) result.value = answer
  } catch (exception) {
    if (request === latest) error.value = exception.message
  } finally {
    if (request === latest) loading.value = false
  }
}

watch([period, premium], load, { immediate: true })

const change = (mover) => {
  const sign = mover.changeCents > 0 ? '+' : '−'
  const money = `${sign}${formatMoney(Math.abs(mover.changeCents), result.value.currency)}`
  return mover.percent === null ? money : `${money} (${sign}${formatPercent(Math.abs(mover.percent))})`
}

const cardLink = (card) => ({ name: 'card', params: { game: card.game, setCode: card.setCode, cardId: card.id } })
</script>

<template>
  <section class="movers">
    <div class="head">
      <h2>{{ t('movers.title') }}</h2>
      <div v-if="premium" class="periods" role="group" :aria-label="t('movers.periodsLabel')">
        <button
          v-for="item in PERIODS"
          :key="item"
          class="period"
          type="button"
          :aria-pressed="item === period"
          @click="period = item"
        >
          {{ t(`movers.periods.${item}`) }}
        </button>
      </div>
    </div>

    <div v-if="!premium" class="locked">
      <p><Lock :size="16" aria-hidden="true" /> {{ t('movers.locked') }}</p>
      <button class="btn btn-secondary" type="button" aria-haspopup="dialog" @click="openPremium('movers')">
        {{ t('movers.unlock') }}
      </button>
    </div>

    <p v-else-if="error" class="error" role="alert">{{ error }}</p>
    <PageSkeleton v-else-if="!result" kind="list" :page="false" :heading="false" />
    <p v-else-if="!result.since" class="note">{{ t('movers.noHistory') }}</p>

    <template v-else>
      <p class="note">{{ t('movers.intro') }} {{ t('movers.since', { date: formatShortDate(result.since) }) }}</p>
      <div class="columns" :aria-busy="loading">
        <div v-for="side in ['risers', 'fallers']" :key="side" class="column" :class="side">
          <h3>
            <component :is="side === 'risers' ? TrendingUp : TrendingDown" :size="18" aria-hidden="true" />
            {{ t(`movers.${side}`) }}
          </h3>
          <p v-if="!result[side].length" class="note">{{ t(side === 'risers' ? 'movers.noneUp' : 'movers.noneDown') }}</p>
          <ol v-else class="list">
            <li v-for="mover in result[side]" :key="`${mover.card.id}-${mover.variant}`" class="mover">
              <router-link class="thumb" :to="cardLink(mover.card)" tabindex="-1" aria-hidden="true">
                <CardImage :src="mover.card.imageUrl" :alt="''" />
              </router-link>
              <div class="text">
                <router-link class="name" :to="cardLink(mover.card)">{{ mover.card.name }}</router-link>
                <p class="meta">{{ mover.card.setName }} · {{ variantLabel(mover.variant) }}</p>
                <p class="numbers">
                  <strong class="amount">{{ change(mover) }}</strong>
                  <span class="now">{{ formatMoney(mover.nowCents, result.currency) }}</span>
                  <span class="then">{{ t('movers.from', { from: formatMoney(mover.thenCents, result.currency) }) }}</span>
                </p>
              </div>
            </li>
          </ol>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.movers {
  margin-top: var(--space-6);
}

.head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2) var(--space-4);
}

.head h2 {
  font-size: var(--text-lg);
}

.periods {
  display: flex;
  gap: var(--space-1);
}

.period {
  min-height: 2.25rem;
  padding-inline: var(--space-3);
  border: 1.5px solid var(--color-line-strong);
  border-radius: var(--radius-round);
  background: var(--color-surface);
  color: var(--color-ink);
  cursor: pointer;
  font-size: var(--text-sm);
  font-weight: 600;
}

.period[aria-pressed='true'] {
  border-color: var(--color-brand);
  background: var(--color-brand);
  color: var(--color-on-brand);
}

.locked {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-top: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  background: var(--color-tint);
  font-size: var(--text-sm);
}

.locked p {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-weight: 600;
}

.columns {
  display: grid;
  gap: var(--space-5);
  margin-top: var(--space-3);
}

.column h3 {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-md);
}

/* Up or down is never colour alone: the heading, the arrow and the sign say it too. */
.risers h3,
.risers .amount {
  color: var(--color-success);
}

.fallers h3,
.fallers .amount {
  color: var(--color-danger);
}

.list {
  margin: var(--space-2) 0 0;
  padding: 0;
  list-style: none;
}

.mover {
  display: flex;
  gap: var(--space-3);
  align-items: center;
  padding-block: var(--space-2);
  border-bottom: 1px solid var(--color-line);
}

.thumb {
  flex: none;
  width: 2.75rem;
}

.text {
  min-width: 0;
}

.name {
  color: var(--color-ink);
  font-size: var(--text-sm);
  font-weight: 650;
  text-decoration: none;
}

@media (hover: hover) {
  .name:hover {
    color: var(--color-brand-ink);
    text-decoration: underline;
  }
}

.meta {
  overflow: hidden;
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.numbers {
  display: flex;
  flex-wrap: wrap;
  gap: 0 var(--space-2);
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
}

.then {
  color: var(--color-ink-soft);
}

.note {
  margin-top: var(--space-2);
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.error {
  margin-top: var(--space-2);
  color: var(--color-danger);
  font-size: var(--text-sm);
}

@media (min-width: 768px) {
  .columns {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
