<script setup>
import { ref, shallowRef, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ChevronLeft, ChevronRight, Heart, ShoppingBag, User, X } from 'lucide-vue-next'
import { mainNav } from '../../data/navigation'
import { useSession } from '../../stores/session'
import { t } from '../../utils/i18n'

const route = useRoute()
const session = useSession()

const dialog = ref(null)
const closing = ref(false)
// The item whose sub-menu is showing. Null shows the main list.
const activeItem = shallowRef(null)

function open() {
  closing.value = false
  activeItem.value = null
  dialog.value.showModal()
}

function close() {
  if (!dialog.value?.open) return
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  if (reducedMotion) {
    dialog.value.close()
  } else {
    closing.value = true
  }
}

function onAnimationEnd() {
  if (closing.value) {
    closing.value = false
    dialog.value.close()
  }
}

function onClick(event) {
  const clickedBackdrop = event.target === dialog.value
  if (clickedBackdrop || event.target.closest('a')) close()
}

watch(() => route.fullPath, close)

defineExpose({ open })
</script>

<template>
  <dialog
    ref="dialog"
    class="mobile-menu"
    :class="{ closing }"
    :aria-label="t('layout.menu')"
    @click="onClick"
    @cancel.prevent="close"
    @animationend.self="onAnimationEnd"
  >
    <div class="panel">
      <div class="panel-head">
        <button v-if="activeItem" class="back-button" type="button" @click="activeItem = null">
          <ChevronLeft :size="20" aria-hidden="true" />
          {{ t('layout.back') }}
        </button>
        <span v-else class="panel-title">{{ t('layout.menu') }}</span>
        <button class="close-button" type="button" :aria-label="t('layout.closeMenu')" @click="close">
          <X :size="24" aria-hidden="true" />
        </button>
      </div>

      <nav class="panel-body" :aria-label="t('layout.mainNavigation')">
        <ul v-if="!activeItem" class="rows">
          <li v-for="item in mainNav" :key="item.key">
            <button v-if="item.columns" class="row" type="button" @click="activeItem = item">
              <span class="tile" :data-game="item.game"><component :is="item.icon" class="tile-icon" /></span>
              <span class="row-label">{{ t(item.labelKey) }}</span>
              <ChevronRight class="row-chevron" :size="18" aria-hidden="true" />
            </button>
            <router-link v-else class="row" :to="item.to">
              <span class="tile"><component :is="item.icon" class="tile-icon" /></span>
              <span class="row-label">{{ t(item.labelKey) }}</span>
            </router-link>
          </li>
        </ul>

        <div v-else :key="activeItem.key" class="sub">
          <div class="sub-head">
            <h2 class="sub-title">{{ t(activeItem.labelKey) }}</h2>
            <router-link class="sub-all" :to="activeItem.to">{{ t('nav.viewAll') }}</router-link>
          </div>
          <section v-for="column in activeItem.columns" :key="column.titleKey" class="sub-group">
            <h3 class="sub-group-title">{{ t(column.titleKey) }}</h3>
            <ul class="sub-links">
              <li v-for="link in column.links" :key="link.to">
                <router-link class="sub-link" :to="link.to">{{ t(link.labelKey) }}</router-link>
              </li>
            </ul>
          </section>
        </div>
      </nav>

      <div class="panel-foot">
        <router-link class="foot-link" to="/account">
          <User :size="18" aria-hidden="true" />
          {{ session.isLoggedIn.value ? t('layout.account') : t('layout.login') }}
        </router-link>
        <router-link class="foot-link" to="/verlanglijst">
          <Heart :size="18" aria-hidden="true" />
          {{ t('layout.wishlist') }}
        </router-link>
        <router-link class="foot-link" to="/winkelwagen">
          <ShoppingBag :size="18" aria-hidden="true" />
          {{ t('layout.cart') }}
        </router-link>
      </div>
    </div>
  </dialog>
</template>

<style scoped>
.mobile-menu {
  position: fixed;
  inset: 0 auto 0 0;
  width: min(26rem, 100vw);
  max-width: none;
  height: 100dvh;
  max-height: none;
  margin: 0;
  padding: 0;
  border: 0;
  background: var(--color-surface);
  color: var(--color-ink);
}

.mobile-menu[open] {
  animation: slide-in 220ms ease-out;
}

.mobile-menu.closing {
  animation: slide-out 160ms ease-in forwards;
}

.mobile-menu::backdrop {
  background: var(--color-brand-deep);
  opacity: 0.5;
}

@keyframes slide-in {
  from {
    transform: translateX(-100%);
  }
}

@keyframes slide-out {
  to {
    transform: translateX(-100%);
  }
}

@media (prefers-reduced-motion: reduce) {
  .mobile-menu[open] {
    animation: none;
  }
}

.panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.panel-head {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: space-between;
  min-height: 3.75rem;
  padding-inline: var(--space-4);
  border-bottom: 1px solid var(--color-line);
}

.panel-title {
  font-family: var(--font-display);
  font-size: var(--text-xl);
  font-stretch: 88%;
  font-weight: 750;
}

.back-button,
.close-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-1);
  min-width: var(--touch-target);
  min-height: var(--touch-target);
  padding: 0;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-ink);
  cursor: pointer;
}

.back-button {
  margin-left: calc(var(--space-1) * -1);
  color: var(--color-brand);
  font-weight: 600;
}

.close-button {
  margin-right: calc(var(--space-2) * -1);
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-2) var(--space-4);
}

.rows {
  margin: 0;
  padding: 0;
  list-style: none;
}

.row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  width: 100%;
  padding: var(--space-2) 0;
  border: 0;
  border-bottom: 1px solid var(--color-line);
  background: transparent;
  color: var(--color-ink);
  cursor: pointer;
  font-size: var(--text-md);
  font-weight: 600;
  text-align: left;
  text-decoration: none;
}

.tile {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 2.5rem;
  height: 2.5rem;
  border-radius: var(--radius-md);
  background: var(--color-tint);
  color: var(--accent);
}

.tile-icon {
  width: 1.375rem;
  height: 1.375rem;
}

.row-label {
  flex: 1;
}

.row-chevron {
  flex: none;
  color: var(--color-ink-soft);
}

.sub {
  animation: sub-in 180ms ease-out;
}

@keyframes sub-in {
  from {
    opacity: 0;
    transform: translateX(1rem);
  }
}

@media (prefers-reduced-motion: reduce) {
  .sub {
    animation: none;
  }
}

.sub-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-4);
  padding-block: var(--space-2);
}

.sub-title {
  font-size: var(--text-xl);
}

.sub-all {
  color: var(--color-brand);
  font-size: var(--text-sm);
  font-weight: 650;
  text-decoration: none;
}

.sub-group {
  margin-top: var(--space-4);
}

.sub-group-title {
  color: var(--color-ink-soft);
  font-family: var(--font-body);
  font-size: var(--text-sm);
  font-stretch: 100%;
  font-weight: 650;
}

.sub-links {
  margin: var(--space-1) 0 0;
  padding: 0;
  list-style: none;
}

.sub-link {
  display: flex;
  align-items: center;
  min-height: var(--touch-target);
  border-bottom: 1px solid var(--color-line);
  color: var(--color-ink);
  text-decoration: none;
}

.panel-foot {
  display: flex;
  flex: none;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 0 var(--space-2);
  padding: var(--space-2) var(--space-4);
  background: var(--color-tint);
  border-top: 1px solid var(--color-line);
}

.foot-link {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  min-height: var(--touch-target);
  color: var(--color-ink);
  font-size: var(--text-xs);
  font-weight: 550;
  text-decoration: none;
}
</style>
