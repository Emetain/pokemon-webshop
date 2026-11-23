<script setup>
import { ref, watch } from 'vue'
import BallMark from '../components/BallMark.vue'
import BinderPage from '../components/BinderPage.vue'
import FormMessage from '../components/FormMessage.vue'
import CollectorTools from '../components/shop/CollectorTools.vue'
import HomeValueChart from '../components/collection/HomeValueChart.vue'
import HeroCarousel from '../components/shop/HeroCarousel.vue'
import NewsCard from '../components/content/NewsCard.vue'
import ProductRow from '../components/shop/ProductRow.vue'
import { mainNav } from '../data/navigation'
import { contentApi, shopApi, wishlistApi } from '../services/api'
import { useSession } from '../stores/session'
import { t } from '../utils/i18n'

const ROW_SIZE = 12

const session = useSession()

const slides = ref([])
/** Cards on the customer's wishlist that are for sale now (2026-11-23). Only shown when there are any. */
const wanted = ref([])
// null while loading: the row shows skeleton tiles until then (2026-11-21).
const newArrivals = ref(null)
const bestsellers = ref([])
const preOrders = ref([])
const news = ref([])
const error = ref('')

const NEWS_SIZE = 3

/** The games, with the icon and colour they have in the menu. Accessories are no game: they stay in the menu only. */
const gameTiles = mainNav
  .filter((item) => item.game)
  .map((item) => ({
    key: item.key,
    to: item.to,
    label: t(item.labelKey),
    text: t('home.gameCards'),
    icon: item.icon,
    game: item.game,
  }))

// Each part of the page loads on its own, so one that fails leaves the others standing.
shopApi.heroSlides().then((result) => (slides.value = result)).catch(() => {})
shopApi
  .newArrivals(ROW_SIZE)
  .then((result) => (newArrivals.value = result))
  .catch(() => (newArrivals.value = []))
shopApi.bestsellers(ROW_SIZE).then((result) => (bestsellers.value = result)).catch(() => {})
contentApi.news({ size: NEWS_SIZE }).then((result) => (news.value = result.articles)).catch(() => {})
shopApi
  .products({ preOrder: true, sort: 'new', size: ROW_SIZE })
  .then((result) => (preOrders.value = result.results.items))
  .catch(() => {})

// The session can still be loading when the page opens, and logging in or out doesn't reload it.
watch(
  () => session.isLoggedIn.value,
  (loggedIn) => {
    wanted.value = []
    if (!loggedIn) return
    wishlistApi
      .forSale(ROW_SIZE)
      .then((result) => {
        if (session.isLoggedIn.value) wanted.value = result
      })
      .catch(() => {})
  },
  { immediate: true },
)

/** The same product can be in more than one row: the heart changes in all of them. */
function onWished(product, wished) {
  for (const row of [wanted, newArrivals, bestsellers, preOrders]) {
    (row.value ?? []).filter((item) => item.id === product.id).forEach((item) => (item.wished = wished))
  }
}
</script>

<template>
  <HeroCarousel :slides="slides">
    <section class="hero">
      <BallMark class="hero-ball" outline />
      <div class="container hero-grid">
        <div>
          <h1 class="hero-title">{{ t('home.title') }}</h1>
          <p class="hero-intro">{{ t('home.intro') }}</p>
          <div class="hero-actions">
            <router-link v-if="session.isLoggedIn.value" class="btn btn-on-brand" to="/collectie">
              {{ t('home.toCollection') }}
            </router-link>
            <router-link v-else class="btn btn-on-brand" to="/registreren">
              {{ t('home.createAccount') }}
            </router-link>
            <router-link class="btn btn-outline-on-brand" to="/pokemon">{{ t('home.browsePokemon') }}</router-link>
          </div>
        </div>
        <BinderPage class="hero-binder" />
      </div>
    </section>
  </HeroCarousel>

  <div class="container sections">
    <FormMessage v-if="error">{{ error }}</FormMessage>

    <!-- First, above the new stock: cards the customer is looking for that came in, the newest first. -->
    <ProductRow
      v-if="wanted.length"
      :title="t('home.wantedForSale')"
      :products="wanted"
      to="/verlanglijst"
      @wished="onWished"
      @error="error = $event"
    />

    <ProductRow
      v-if="newArrivals === null || newArrivals.length"
      :title="t('home.newArrivals')"
      :products="newArrivals ?? []"
      :loading="newArrivals === null"
      to="/producten?stock=true"
      @wished="onWished"
      @error="error = $event"
    />

    <!-- Right below the new stock: the customer's own value graph, and what the shop offers besides selling. -->
    <HomeValueChart />
    <CollectorTools />

    <section class="games" aria-labelledby="home-games">
      <h2 id="home-games">{{ t('home.games') }}</h2>
      <ul class="game-tiles">
        <li v-for="tile in gameTiles" :key="tile.key">
          <router-link class="game-tile" :to="tile.to" :data-game="tile.game">
            <component :is="tile.icon" class="game-icon" aria-hidden="true" />
            <span class="game-name">{{ tile.label }}</span>
            <span class="game-text">{{ tile.text }}</span>
          </router-link>
        </li>
      </ul>
    </section>

    <ProductRow
      v-if="bestsellers.length"
      :title="t('home.bestsellers')"
      :products="bestsellers"
      to="/producten?sort=popular"
      @wished="onWished"
      @error="error = $event"
    />

    <ProductRow
      v-if="preOrders.length"
      :title="t('home.preOrders')"
      :products="preOrders"
      to="/pre-order"
      @wished="onWished"
      @error="error = $event"
    />

    <section v-if="news.length" class="news" aria-labelledby="home-news">
      <div class="news-head">
        <h2 id="home-news">{{ t('home.news') }}</h2>
        <router-link class="all" to="/nieuws">{{ t('home.allNews') }}</router-link>
      </div>
      <ul class="news-grid">
        <li v-for="article in news" :key="article.slug"><NewsCard :article="article" /></li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.hero {
  position: relative;
  overflow: hidden;
  height: 100%;
  /* Room at the bottom for the buttons of the carousel. */
  padding-block: var(--space-6) var(--space-8);
  background: var(--color-brand);
  color: var(--color-on-brand);
}

.hero-ball {
  position: absolute;
  top: -9rem;
  right: -19rem;
  width: 30rem;
  height: 30rem;
  color: var(--color-on-brand);
  opacity: 0.16;
  transform: rotate(-18deg);
}

.hero-grid {
  position: relative;
  display: grid;
  gap: var(--space-6);
}

.hero-title {
  font-size: var(--text-hero);
  font-stretch: 80%;
  font-weight: 800;
  line-height: 0.98;
}

.hero-intro {
  max-width: 34rem;
  margin-top: var(--space-4);
  color: var(--color-on-brand-soft);
  font-size: var(--text-lg);
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-top: var(--space-5);
}

.hero-binder {
  max-width: 24rem;
}

.sections {
  display: grid;
  gap: var(--space-7);
  padding-block: var(--space-6) 0;
}

.game-tiles {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
  margin: var(--space-4) 0 0;
  padding: 0;
  list-style: none;
}

/* The last tile spans the row when the count is odd, so the grid never ends with a hole. */
.game-tiles > li:last-child:nth-child(odd) {
  grid-column: 1 / -1;
}

.game-tile {
  display: grid;
  align-content: start;
  gap: var(--space-1);
  height: 100%;
  padding: var(--space-4);
  border-radius: var(--radius-lg);
  background: var(--accent);
  color: var(--accent-ink);
  text-decoration: none;
}

.game-tile:not([data-game]) {
  background: var(--color-brand-deep);
  color: var(--color-on-brand);
}

.game-tile:focus-visible {
  outline-color: var(--color-focus);
}

.game-icon {
  width: 1.75rem;
  height: 1.75rem;
  margin-bottom: var(--space-2);
}

.game-name {
  font-family: var(--font-display);
  font-size: var(--text-xl);
  font-stretch: 88%;
  font-weight: 750;
  line-height: var(--leading-tight);
}

@media (hover: hover) {
  .game-tile:hover .game-name {
    text-decoration: underline;
  }
}

.game-text {
  font-size: var(--text-sm);
}

/* Wide enough to open the binder: two pages side by side. */
@media (min-width: 640px) {
  .hero-binder {
    max-width: 46rem;
  }
}

@media (min-width: 900px) {
  .game-tiles {
    grid-template-columns: repeat(5, minmax(0, 1fr));
  }

  .game-tiles > li:last-child:nth-child(odd) {
    grid-column: auto;
  }
}

@media (min-width: 960px) {
  .hero {
    padding-block: var(--space-7) var(--space-8);
  }

  .hero-ball {
    top: 50%;
    right: -9rem;
    width: 50rem;
    height: 50rem;
    transform: translateY(-50%) rotate(-18deg);
  }
}

/* Room for the text and an open binder next to each other. */
@media (min-width: 1200px) {
  .hero-grid {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1.45fr);
    align-items: center;
    gap: var(--space-7);
  }

  .hero-title {
    font-size: clamp(2.75rem, 4.6vw, 4.25rem);
  }

  .hero-binder {
    justify-self: end;
    width: 100%;
  }
}

.news-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
}

.news-head .all {
  font-size: var(--text-sm);
  font-weight: 600;
}

.news-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 17rem), 1fr));
  gap: var(--space-5) var(--space-4);
  margin: var(--space-3) 0 0;
  padding: 0;
  list-style: none;
}
</style>
