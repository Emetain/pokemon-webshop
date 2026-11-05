<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { X } from 'lucide-vue-next'
import { gameLinks, pageLinks } from '../../data/navigation'
import { useSession } from '../../stores/session'
import { t } from '../../utils/i18n'

const route = useRoute()
const session = useSession()

const dialog = ref(null)
const closing = ref(false)

function open() {
  closing.value = false
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
        <span class="panel-title">{{ t('layout.menu') }}</span>
        <button class="close-button" type="button" :aria-label="t('layout.closeMenu')" @click="close">
          <X :size="24" aria-hidden="true" />
        </button>
      </div>

      <nav :aria-label="t('layout.mainNavigation')">
        <ul class="game-list">
          <li v-for="link in gameLinks" :key="link.to">
            <router-link class="game-link" :to="link.to" :data-game="link.game">{{ t(link.labelKey) }}</router-link>
          </li>
        </ul>
        <ul class="page-list">
          <li v-for="link in pageLinks" :key="link.to">
            <router-link class="page-link" :to="link.to">{{ t(link.labelKey) }}</router-link>
          </li>
          <li>
            <router-link class="page-link" to="/account">
              {{ session.isLoggedIn.value ? t('layout.account') : t('layout.login') }}
            </router-link>
          </li>
        </ul>
      </nav>
    </div>
  </dialog>
</template>

<style scoped>
.mobile-menu {
  position: fixed;
  inset: 0 auto 0 0;
  width: min(20rem, 86vw);
  max-width: none;
  height: 100dvh;
  max-height: none;
  margin: 0;
  padding: 0;
  border: 0;
  background: var(--color-binder);
  color: var(--color-on-binder);
}

.mobile-menu[open] {
  animation: slide-in 220ms ease-out;
}

.mobile-menu.closing {
  animation: slide-out 160ms ease-in forwards;
}

.mobile-menu::backdrop {
  background: var(--color-binder-deep);
  opacity: 0.6;
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
  height: 100%;
  overflow-y: auto;
  padding: var(--space-3) var(--space-4) var(--space-6);
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-3);
}

.panel-title {
  font-family: var(--font-display);
  font-size: var(--text-lg);
  font-stretch: 88%;
  font-weight: 750;
}

.close-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--touch-target);
  height: var(--touch-target);
  margin-right: calc(var(--space-2) * -1);
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-on-binder);
  cursor: pointer;
}

.mobile-menu :focus-visible {
  outline-color: var(--color-focus-on-binder);
}

.game-list,
.page-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.game-list {
  display: grid;
  gap: var(--space-1);
  margin-bottom: var(--space-4);
}

/* The binder tabs, turned on their side. */
.game-link {
  display: block;
  padding: var(--space-3) var(--space-4);
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
  background: var(--color-binder-raised);
  box-shadow: inset 5px 0 0 var(--accent);
  color: var(--color-on-binder);
  font-family: var(--font-display);
  font-size: var(--text-lg);
  font-stretch: 88%;
  font-weight: 700;
  text-decoration: none;
}

.game-link.router-link-active {
  background: var(--accent);
  box-shadow: none;
  color: var(--accent-ink);
}

.page-link {
  display: block;
  padding: var(--space-3) var(--space-1);
  border-bottom: 1px solid var(--color-binder-raised);
  color: var(--color-on-binder);
  text-decoration: none;
}
</style>
