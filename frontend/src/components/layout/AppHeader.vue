<script setup>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Heart, Menu, Search, ShoppingCart, Truck, User } from 'lucide-vue-next'
import AppLogo from './AppLogo.vue'
import MobileMenu from './MobileMenu.vue'
import { gameLinks, pageLinks } from '../../data/navigation'
import { useSession } from '../../stores/session'
import { t } from '../../utils/i18n'

const route = useRoute()
const session = useSession()

const mobileMenu = ref(null)
const cartCount = 0

const accountLabel = computed(() =>
  session.isLoggedIn.value ? t('layout.account') : t('layout.login'),
)
</script>

<template>
  <header class="site-header" :data-game="route.meta.game">
    <p class="top-bar">
      <Truck :size="16" aria-hidden="true" />
      {{ t('layout.shippingNotice') }}
    </p>

    <div class="container bar">
      <button
        class="icon-button menu-button"
        type="button"
        :aria-label="t('layout.openMenu')"
        @click="mobileMenu.open()"
      >
        <Menu :size="24" aria-hidden="true" />
      </button>

      <AppLogo class="bar-logo" />

      <form class="search" role="search" @submit.prevent>
        <label class="visually-hidden" for="site-search">{{ t('layout.search') }}</label>
        <Search class="search-icon" :size="18" aria-hidden="true" />
        <input
          id="site-search"
          class="search-input"
          type="search"
          :placeholder="t('layout.searchPlaceholder')"
        />
      </form>

      <div class="actions">
        <router-link class="icon-button" to="/account" :aria-label="accountLabel">
          <User :size="22" aria-hidden="true" />
          <span class="action-label">{{ accountLabel }}</span>
        </router-link>
        <router-link class="icon-button" to="/verlanglijst" :aria-label="t('layout.wishlist')">
          <Heart :size="22" aria-hidden="true" />
        </router-link>
        <router-link
          class="icon-button"
          to="/winkelwagen"
          :aria-label="t('layout.cartWithCount', { count: cartCount })"
        >
          <ShoppingCart :size="22" aria-hidden="true" />
          <span class="cart-count" aria-hidden="true">{{ cartCount }}</span>
        </router-link>
      </div>
    </div>

    <nav class="container tabs" :aria-label="t('layout.mainNavigation')">
      <ul class="tab-list">
        <li v-for="link in gameLinks" :key="link.to">
          <router-link class="tab" :to="link.to" :data-game="link.game">{{ t(link.labelKey) }}</router-link>
        </li>
      </ul>
      <ul class="page-list">
        <li v-for="link in pageLinks" :key="link.to">
          <router-link class="page-link" :to="link.to">{{ t(link.labelKey) }}</router-link>
        </li>
      </ul>
    </nav>

    <MobileMenu ref="mobileMenu" />
  </header>
</template>

<style scoped>
.site-header {
  --spine: var(--color-binder-raised);
  background: var(--color-binder);
  border-bottom: var(--spine-width) solid var(--spine);
  color: var(--color-on-binder);
}

.site-header[data-game] {
  --spine: var(--accent);
}

.site-header :focus-visible {
  outline-color: var(--color-focus-on-binder);
}

.top-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-1) var(--gutter);
  background: var(--color-binder-deep);
  color: var(--color-on-binder-soft);
  font-size: var(--text-xs);
  text-align: center;
}

.bar {
  display: grid;
  grid-template-areas:
    'menu logo actions'
    'search search search';
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  column-gap: var(--space-1);
  row-gap: var(--space-2);
  padding-block: var(--space-2) var(--space-3);
}

.menu-button {
  grid-area: menu;
  margin-left: calc(var(--space-2) * -1);
}

.bar-logo {
  grid-area: logo;
  justify-self: start;
}

.actions {
  grid-area: actions;
  display: flex;
  align-items: center;
  margin-right: calc(var(--space-2) * -1);
}

.icon-button {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  min-width: var(--touch-target);
  min-height: var(--touch-target);
  padding: var(--space-1) var(--space-2);
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-on-binder);
  cursor: pointer;
  text-decoration: none;
}

.icon-button:hover {
  background: var(--color-binder-raised);
}

.action-label {
  display: none;
  font-size: var(--text-sm);
  font-weight: 550;
}

.cart-count {
  position: absolute;
  top: var(--space-half);
  right: var(--space-half);
  min-width: 1.125rem;
  padding-inline: var(--space-1);
  border-radius: var(--radius-round);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-size: var(--text-2xs);
  font-weight: 700;
  line-height: 1.125rem;
  text-align: center;
}

.search {
  grid-area: search;
  position: relative;
}

.search-icon {
  position: absolute;
  top: 50%;
  left: var(--space-3);
  color: var(--color-ink-soft);
  transform: translateY(-50%);
}

.search-input {
  width: 100%;
  height: var(--touch-target);
  padding: 0 var(--space-3) 0 2.5rem;
  border: 0;
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-ink);
}

.search-input::placeholder {
  color: var(--color-ink-soft);
  opacity: 1;
}

.tabs {
  display: none;
}

@media (min-width: 1024px) {
  .bar {
    grid-template-areas: 'logo search actions';
    grid-template-columns: auto minmax(0, 1fr) auto;
    column-gap: var(--space-6);
    padding-block: var(--space-3);
  }

  .menu-button {
    display: none;
  }

  .search {
    max-width: 38rem;
  }

  .action-label {
    display: inline;
  }

  .tabs {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    gap: var(--space-5);
    padding-top: var(--space-1);
  }

  .tab-list,
  .page-list {
    display: flex;
    margin: 0;
    padding: 0;
    list-style: none;
  }

  .tab-list {
    gap: var(--space-1);
  }

  .page-list {
    gap: var(--space-5);
  }

  /* Index tabs of a binder. The active tab runs into the spine under the header. */
  .tab {
    display: block;
    padding: var(--space-2) var(--space-5);
    border-radius: var(--radius-md) var(--radius-md) 0 0;
    background: var(--color-binder-raised);
    box-shadow: inset 0 3px 0 var(--accent);
    color: var(--color-on-binder);
    font-family: var(--font-display);
    font-size: var(--text-md);
    font-stretch: 88%;
    font-weight: 700;
    text-decoration: none;
  }

  .tab:hover,
  .tab.router-link-active {
    background: var(--accent);
    box-shadow: none;
    color: var(--accent-ink);
  }

  .page-link {
    display: block;
    padding-block: var(--space-2);
    color: var(--color-on-binder-soft);
    font-size: var(--text-sm);
    font-weight: 550;
    text-decoration: none;
  }

  .page-link:hover,
  .page-link.router-link-active {
    color: var(--color-on-binder);
    text-decoration: underline;
    text-decoration-color: var(--color-primary);
    text-decoration-thickness: 2px;
  }
}
</style>
