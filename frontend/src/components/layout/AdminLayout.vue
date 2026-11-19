<script setup>
import {
  ArrowLeft,
  Boxes,
  CalendarDays,
  DatabaseZap,
  FileText,
  FileUp,
  GalleryHorizontal,
  Image,
  Link2,
  LayoutDashboard,
  LifeBuoy,
  Mail,
  Menu,
  Newspaper,
  Package,
  ReceiptText,
  Star,
  TicketPercent,
  Truck,
  X,
  Zap,
} from 'lucide-vue-next'
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppLogo from './AppLogo.vue'
import { t } from '../../utils/i18n'

const route = useRoute()

// The import has its own menu item, so the products item leaves it out.
const onProducts = computed(
  () => route.path.startsWith('/admin/producten') && !route.path.startsWith('/admin/producten/import'),
)

// Below 900px the menu sits behind a button, so a phone shows the page first instead of 19 links.
const menuOpen = ref(false)
const toggle = ref(null)

watch(() => route.fullPath, () => {
  menuOpen.value = false
})

function closeMenu() {
  if (!menuOpen.value) return
  menuOpen.value = false
  toggle.value?.focus()
}
</script>

<template>
  <div class="admin">
    <aside class="sidebar" @keydown.esc="closeMenu">
      <AppLogo class="sidebar-logo" />
      <button
        ref="toggle"
        class="menu-toggle"
        type="button"
        aria-controls="admin-menu"
        :aria-expanded="menuOpen ? 'true' : 'false'"
        @click="menuOpen = !menuOpen"
      >
        <X v-if="menuOpen" :size="20" aria-hidden="true" />
        <Menu v-else :size="20" aria-hidden="true" />
        {{ t('admin.menu') }}
      </button>
      <div id="admin-menu" class="menu" :class="{ open: menuOpen }">
        <nav class="sidebar-nav" :aria-label="t('admin.navigation')">
          <router-link class="sidebar-link" to="/admin" exact-active-class="is-active">
            <LayoutDashboard :size="18" aria-hidden="true" />
            {{ t('admin.overview') }}
          </router-link>
          <router-link class="sidebar-link" to="/admin/bestellingen" active-class="is-active">
            <ReceiptText :size="18" aria-hidden="true" />
            {{ t('admin.orders.nav') }}
          </router-link>
          <router-link
            class="sidebar-link"
            :class="{ 'is-active': onProducts }"
            to="/admin/producten"
            active-class=""
          >
            <Package :size="18" aria-hidden="true" />
            {{ t('admin.products.nav') }}
          </router-link>
          <router-link class="sidebar-link" to="/admin/producten/import" active-class="is-active">
            <FileUp :size="18" aria-hidden="true" />
            {{ t('admin.import.nav') }}
          </router-link>
          <router-link class="sidebar-link" to="/admin/voorraad" active-class="is-active">
            <Boxes :size="18" aria-hidden="true" />
            {{ t('admin.stock.nav') }}
          </router-link>
          <router-link class="sidebar-link" to="/admin/verzending" active-class="is-active">
            <Truck :size="18" aria-hidden="true" />
            {{ t('admin.shipping.nav') }}
          </router-link>
          <router-link class="sidebar-link" to="/admin/drops" active-class="is-active">
            <Zap :size="18" aria-hidden="true" />
            {{ t('admin.drops.nav') }}
          </router-link>
          <router-link class="sidebar-link" to="/admin/kortingscodes" active-class="is-active">
            <TicketPercent :size="18" aria-hidden="true" />
            {{ t('admin.discounts.nav') }}
          </router-link>
          <router-link class="sidebar-link" to="/admin/tickets" active-class="is-active">
            <LifeBuoy :size="18" aria-hidden="true" />
            {{ t('admin.tickets.nav') }}
          </router-link>
          <router-link class="sidebar-link" to="/admin/reviews" active-class="is-active">
            <Star :size="18" aria-hidden="true" />
            {{ t('admin.reviews.nav') }}
          </router-link>
          <router-link class="sidebar-link" to="/admin/hero" active-class="is-active">
            <GalleryHorizontal :size="18" aria-hidden="true" />
            {{ t('admin.slides.nav') }}
          </router-link>
          <router-link class="sidebar-link" to="/admin/releasekalender" active-class="is-active">
            <CalendarDays :size="18" aria-hidden="true" />
            {{ t('admin.releases.nav') }}
          </router-link>
          <router-link class="sidebar-link" to="/admin/nieuws" active-class="is-active">
            <Newspaper :size="18" aria-hidden="true" />
            {{ t('admin.news.nav') }}
          </router-link>
          <router-link class="sidebar-link" to="/admin/paginas" active-class="is-active">
            <FileText :size="18" aria-hidden="true" />
            {{ t('admin.pages.nav') }}
          </router-link>
          <router-link class="sidebar-link" to="/admin/nieuwsbrief" active-class="is-active">
            <Mail :size="18" aria-hidden="true" />
            {{ t('admin.newsletter.nav') }}
          </router-link>
          <router-link class="sidebar-link" to="/admin/sync" active-class="is-active">
            <DatabaseZap :size="18" aria-hidden="true" />
            {{ t('admin.sync.nav') }}
          </router-link>
          <router-link class="sidebar-link" to="/admin/afbeeldingen" active-class="is-active">
            <Image :size="18" aria-hidden="true" />
            {{ t('admin.images.nav') }}
          </router-link>
          <router-link class="sidebar-link" to="/admin/prijskoppelingen" active-class="is-active">
            <Link2 :size="18" aria-hidden="true" />
            {{ t('admin.priceLinks.nav') }}
          </router-link>
        </nav>
        <router-link class="sidebar-link back-link" to="/">
          <ArrowLeft :size="18" aria-hidden="true" />
          {{ t('admin.backToShop') }}
        </router-link>
      </div>
    </aside>

    <main class="admin-main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.admin {
  min-height: 100dvh;
}

.sidebar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2) var(--space-4);
  padding: var(--space-3) var(--gutter);
  background: var(--color-brand-deep);
  color: var(--color-on-brand);
}

.sidebar :focus-visible {
  outline-color: var(--color-focus-on-brand);
}

.menu-toggle {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-height: var(--touch-target);
  padding: var(--space-2) var(--space-3);
  border: 1.5px solid var(--color-on-brand-soft);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-on-brand);
  font-size: var(--text-sm);
  font-weight: 600;
  cursor: pointer;
}

.menu-toggle:hover {
  background: var(--color-brand-raised);
}

.menu {
  display: none;
  flex-basis: 100%;
  padding-bottom: var(--space-2);
}

.menu.open {
  display: block;
}

.sidebar-link {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-height: var(--touch-target);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-sm);
  color: var(--color-on-brand-soft);
  font-size: var(--text-sm);
  font-weight: 550;
  text-decoration: none;
}

.sidebar-link:hover {
  background: var(--color-brand-raised);
  color: var(--color-on-brand);
}

.sidebar-link.is-active {
  background: var(--color-brand-raised);
  box-shadow: inset 3px 0 0 var(--color-on-brand);
  color: var(--color-on-brand);
}

.admin-main {
  padding: var(--space-5) var(--gutter);
}

@media (min-width: 900px) {
  .admin {
    display: grid;
    grid-template-columns: 15rem minmax(0, 1fr);
  }

  .sidebar {
    position: sticky;
    top: 0;
    flex-direction: column;
    flex-wrap: nowrap;
    align-items: stretch;
    justify-content: flex-start;
    height: 100dvh;
    /* In a low window the links scroll inside the blue sidebar instead of running out onto the white page. */
    overflow-y: auto;
    padding: var(--space-5) var(--space-3);
  }

  .menu-toggle {
    display: none;
  }

  .menu,
  .menu.open {
    display: flex;
    flex: 1;
    flex-direction: column;
    padding-bottom: 0;
  }

  /* The round badge sits in the middle of the sidebar, as big as in the shop header. */
  .sidebar-logo {
    --logo-height: 6rem;
    --logo-height-wide: 6rem;

    align-self: center;
  }

  .sidebar-nav {
    flex: 1;
    margin-top: var(--space-5);
  }

  .admin-main {
    padding: var(--space-6);
  }
}
</style>
