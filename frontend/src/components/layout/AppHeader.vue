<script setup>
import { computed, nextTick, ref } from 'vue'
import { Heart, Menu, Search, ShoppingBag, Truck, User } from 'lucide-vue-next'
import AppLogo from './AppLogo.vue'
import MegaMenu from './MegaMenu.vue'
import MobileMenu from './MobileMenu.vue'
import { useSession } from '../../stores/session'
import { t } from '../../utils/i18n'

const session = useSession()

const mobileMenu = ref(null)
const searchInput = ref(null)
const searchOpen = ref(false)
const cartCount = 0

const accountLabel = computed(() =>
  session.isLoggedIn.value ? t('layout.account') : t('layout.login'),
)

async function toggleSearch() {
  searchOpen.value = !searchOpen.value
  if (searchOpen.value) {
    await nextTick()
    searchInput.value.focus()
  }
}
</script>

<template>
  <p class="top-bar">
    <Truck :size="16" aria-hidden="true" />
    {{ t('layout.shippingNotice') }}
  </p>

  <header class="site-header">
    <div class="container bar">
      <button
        class="action menu-button"
        type="button"
        :aria-label="t('layout.openMenu')"
        @click="mobileMenu.open()"
      >
        <Menu :size="24" aria-hidden="true" />
      </button>

      <AppLogo class="bar-logo" />

      <form class="search" :class="{ open: searchOpen }" role="search" @submit.prevent>
        <label class="visually-hidden" for="site-search">{{ t('layout.search') }}</label>
        <input
          id="site-search"
          ref="searchInput"
          class="search-input"
          type="search"
          :placeholder="t('layout.searchPlaceholder')"
        />
        <Search class="search-icon" :size="20" aria-hidden="true" />
      </form>

      <div class="actions">
        <button
          class="action search-toggle"
          type="button"
          :aria-label="t('layout.search')"
          :aria-expanded="searchOpen"
          @click="toggleSearch"
        >
          <Search :size="22" aria-hidden="true" />
        </button>
        <router-link class="action" to="/account" :aria-label="accountLabel">
          <User :size="22" aria-hidden="true" />
          <span class="action-label">{{ accountLabel }}</span>
        </router-link>
        <router-link class="action wishlist" to="/verlanglijst" :aria-label="t('layout.wishlist')">
          <Heart :size="22" aria-hidden="true" />
        </router-link>
        <router-link
          class="action"
          to="/winkelwagen"
          :aria-label="t('layout.cartWithCount', { count: cartCount })"
        >
          <ShoppingBag :size="22" aria-hidden="true" />
          <span class="action-label">{{ t('layout.cart') }}</span>
          <span class="cart-count" aria-hidden="true">{{ cartCount }}</span>
        </router-link>
      </div>
    </div>

    <MegaMenu />
    <MobileMenu ref="mobileMenu" />
  </header>
</template>

<style scoped>
.top-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-1) var(--gutter);
  background: var(--color-brand-deep);
  color: var(--color-on-brand-soft);
  font-size: var(--text-xs);
  text-align: center;
}

.site-header {
  position: sticky;
  top: 0;
  z-index: var(--header-layer);
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-line);
}

.bar {
  display: grid;
  grid-template-areas:
    'menu logo actions'
    'search search search';
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  column-gap: var(--space-1);
  padding-block: var(--space-2);
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

.action {
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
  color: var(--color-ink);
  cursor: pointer;
  text-decoration: none;
  transition: color 0.2s;
}

.action:hover {
  color: var(--color-brand);
}

.action-label {
  display: none;
  font-size: var(--text-sm);
  font-weight: 600;
}

.wishlist {
  display: none;
}

.cart-count {
  position: absolute;
  top: var(--space-half);
  right: var(--space-half);
  min-width: 1.125rem;
  padding-inline: var(--space-1);
  border-radius: var(--radius-round);
  background: var(--color-brand);
  color: var(--color-on-brand);
  font-size: var(--text-2xs);
  font-weight: 700;
  line-height: 1.125rem;
  text-align: center;
}

.search {
  grid-area: search;
  position: relative;
  display: none;
  margin-top: var(--space-2);
}

.search.open {
  display: block;
}

.search-input {
  width: 100%;
  height: var(--control-height);
  padding: 0 var(--space-7) 0 var(--space-4);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
  background: var(--color-tint);
  color: var(--color-ink);
}

.search-input::placeholder {
  color: var(--color-ink-soft);
  opacity: 1;
}

.search-input:hover {
  border-color: var(--color-line-strong);
}

.search-icon {
  position: absolute;
  top: 50%;
  right: var(--space-4);
  color: var(--color-ink);
  pointer-events: none;
  transform: translateY(-50%);
}

@media (min-width: 480px) {
  .wishlist {
    display: inline-flex;
  }
}

@media (min-width: 1024px) {
  .bar {
    grid-template-areas: 'logo search actions';
    column-gap: var(--space-6);
    padding-block: var(--space-3);
  }

  .menu-button,
  .search-toggle {
    display: none;
  }

  .search,
  .search.open {
    display: block;
    margin-top: 0;
  }

  .actions {
    gap: var(--space-2);
  }

  .action-label {
    display: inline;
  }

  .cart-count {
    position: static;
    min-width: 1.5rem;
    background: var(--color-tint-strong);
    color: var(--color-brand);
    line-height: 1.5rem;
  }
}
</style>
