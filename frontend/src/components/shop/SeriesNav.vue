<script setup>
import { computed, ref } from 'vue'
import { ChevronDown, ChevronLeft } from 'lucide-vue-next'
import { shopPath } from '../../data/shop'
import { formatNumber } from '../../utils/format'
import { t } from '../../utils/i18n'

/**
 * Series and sets of a game, like the big Dutch shops have: a series page lists its sets. Only series with
 * products are listed, so every link leads to something. Folds away on a phone.
 */
const props = defineProps({
  game: { type: String, required: true },
  series: { type: Array, default: () => [] },
  /** The current series, with its sets, on a series or set page. */
  seriesDetail: { type: Object, default: null },
  setCode: { type: String, default: '' },
})

const open = ref(false)

const listed = computed(() => props.series.filter((entry) => entry.productCount > 0))
const sets = computed(() => props.seriesDetail?.sets.filter((set) => set.productCount > 0) ?? [])
</script>

<template>
  <nav v-if="listed.length || seriesDetail" class="series-nav" :aria-label="t('shop.nav.series')">
    <button class="toggle" type="button" :aria-expanded="open" @click="open = !open">
      {{ seriesDetail ? seriesDetail.name : t('shop.nav.series') }}
      <ChevronDown class="chevron" :size="18" aria-hidden="true" />
    </button>

    <div class="body" :class="{ open }">
      <template v-if="seriesDetail">
        <router-link class="back" :to="shopPath(game)">
          <ChevronLeft :size="16" aria-hidden="true" />
          {{ t('shop.nav.allSeries') }}
        </router-link>
        <h2 class="heading">{{ t('shop.nav.sets') }}</h2>
        <ul class="links">
          <li>
            <router-link
              class="link"
              :class="{ current: !setCode }"
              :to="shopPath(game, { series: seriesDetail.slug })"
              :aria-current="!setCode ? 'page' : undefined"
            >
              <span>{{ t('shop.nav.all') }}</span>
            </router-link>
          </li>
          <li v-for="set in sets" :key="set.code">
            <router-link
              class="link"
              :class="{ current: set.code === setCode }"
              :to="shopPath(game, { series: seriesDetail.slug, setCode: set.code })"
              :aria-current="set.code === setCode ? 'page' : undefined"
            >
              <span>{{ set.name }}</span>
              <span class="count">{{ formatNumber(set.productCount) }}</span>
            </router-link>
          </li>
        </ul>
      </template>

      <template v-else>
        <h2 class="heading">{{ t('shop.nav.series') }}</h2>
        <ul class="links">
          <li v-for="entry in listed" :key="entry.slug">
            <router-link class="link" :to="shopPath(game, { series: entry.slug })">
              <span>{{ entry.name }}</span>
              <span class="count">{{ formatNumber(entry.productCount) }}</span>
            </router-link>
          </li>
        </ul>
      </template>
    </div>
  </nav>
</template>

<style scoped>
.series-nav {
  border-top: 1px solid var(--color-line);
  border-bottom: 1px solid var(--color-line);
}

.toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: var(--touch-target);
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-ink);
  cursor: pointer;
  font-size: var(--text-sm);
  font-weight: 650;
  text-align: left;
}

.toggle[aria-expanded='true'] .chevron {
  transform: rotate(180deg);
}

.body {
  display: none;
  padding-bottom: var(--space-3);
}

.body.open {
  display: block;
}

.back {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  min-height: var(--touch-target);
  color: var(--color-link);
  font-size: var(--text-sm);
  font-weight: 600;
  text-decoration: none;
}

.back:hover {
  text-decoration: underline;
}

.heading {
  display: none;
  margin-bottom: var(--space-1);
  font-family: var(--font-body);
  font-size: var(--text-sm);
  font-stretch: 100%;
  font-weight: 700;
}

.links {
  margin: 0;
  padding: 0;
  list-style: none;
}

.link {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2);
  padding-block: var(--space-2);
  color: var(--color-ink);
  font-size: var(--text-sm);
  text-decoration: none;
}

.link:hover {
  color: var(--color-brand);
}

.link.current {
  color: var(--color-brand);
  font-weight: 700;
}

.count {
  flex: none;
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

@media (min-width: 1024px) {
  .series-nav {
    padding-block: var(--space-4) var(--space-3);
    border-bottom: 0;
  }

  .toggle {
    display: none;
  }

  .body,
  .body.open {
    display: block;
    padding: 0;
  }

  .heading {
    display: block;
  }
}
</style>
