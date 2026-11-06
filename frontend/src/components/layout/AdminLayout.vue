<script setup>
import { ArrowLeft, DatabaseZap, Image, LayoutDashboard } from 'lucide-vue-next'
import AppLogo from './AppLogo.vue'
import { t } from '../../utils/i18n'
</script>

<template>
  <div class="admin">
    <aside class="sidebar">
      <AppLogo class="sidebar-logo" inverse />
      <nav class="sidebar-nav" :aria-label="t('admin.navigation')">
        <router-link class="sidebar-link" to="/admin" exact-active-class="is-active">
          <LayoutDashboard :size="18" aria-hidden="true" />
          {{ t('admin.overview') }}
        </router-link>
        <router-link class="sidebar-link" to="/admin/sync" active-class="is-active">
          <DatabaseZap :size="18" aria-hidden="true" />
          {{ t('admin.sync.nav') }}
        </router-link>
        <router-link class="sidebar-link" to="/admin/afbeeldingen" active-class="is-active">
          <Image :size="18" aria-hidden="true" />
          {{ t('admin.images.nav') }}
        </router-link>
      </nav>
      <router-link class="sidebar-link back-link" to="/">
        <ArrowLeft :size="18" aria-hidden="true" />
        {{ t('admin.backToShop') }}
      </router-link>
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

.sidebar-nav {
  order: 3;
  flex-basis: 100%;
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
    padding: var(--space-5) var(--space-3);
  }

  .sidebar-logo {
    padding-inline: var(--space-3);
  }

  .sidebar-nav {
    order: 0;
    flex: 1;
    flex-basis: auto;
    margin-top: var(--space-5);
  }

  .admin-main {
    padding: var(--space-6);
  }
}
</style>
