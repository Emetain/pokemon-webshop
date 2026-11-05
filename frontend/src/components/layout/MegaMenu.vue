<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ChevronDown, X } from 'lucide-vue-next'
import BallMark from '../BallMark.vue'
import { mainNav } from '../../data/navigation'
import { t } from '../../utils/i18n'

// Short pauses, so passing over the menu on the way to something else doesn't open it,
// and a small slip of the mouse doesn't close it.
const OPEN_DELAY_MS = 120
const CLOSE_DELAY_MS = 220

const route = useRoute()

const root = ref(null)
const openKey = ref(null)
// A menu opened by a click stays open until the visitor closes it. One opened by hover follows the mouse.
const pinned = ref(false)
// Going from one open menu to the next swaps at once, without the opening animation.
const switching = ref(false)

let openTimer = null
let closeTimer = null

const canHover = () => window.matchMedia('(hover: hover) and (pointer: fine)').matches

function clearTimers() {
  clearTimeout(openTimer)
  clearTimeout(closeTimer)
}

function open(key) {
  switching.value = openKey.value !== null
  openKey.value = key
}

function close() {
  clearTimers()
  openKey.value = null
  pinned.value = false
}

function onItemHover(key) {
  if (!canHover()) return
  clearTimers()
  if (openKey.value === key) return
  if (openKey.value) {
    pinned.value = false
    open(key)
  } else {
    openTimer = setTimeout(() => open(key), OPEN_DELAY_MS)
  }
}

function onPlainLinkHover() {
  if (canHover() && !pinned.value) close()
}

function onNavEnter() {
  clearTimeout(closeTimer)
}

function onNavLeave() {
  clearTimeout(openTimer)
  if (canHover() && !pinned.value) {
    closeTimer = setTimeout(close, CLOSE_DELAY_MS)
  }
}

function onItemClick(key) {
  clearTimers()
  if (openKey.value === key && pinned.value) {
    close()
  } else {
    open(key)
    pinned.value = true
  }
}

function onPanelClick(event) {
  if (event.target.closest('a')) close()
}

function onEscape() {
  const key = openKey.value
  if (!key) return
  close()
  root.value.querySelector(`[aria-controls="mega-${key}"]`)?.focus()
}

function onFocusOut(event) {
  const openItem = root.value.querySelector(`#mega-${openKey.value}`)?.closest('li')
  const movedOutside = event.relatedTarget && !openItem?.contains(event.relatedTarget)
  if (openItem && movedOutside) close()
}

function onDocumentPointerDown(event) {
  if (openKey.value && !root.value.contains(event.target)) close()
}

function isCurrent(item) {
  return route.path === item.to || route.path.startsWith(`${item.to}/`)
}

watch(() => route.fullPath, close)

onMounted(() => document.addEventListener('pointerdown', onDocumentPointerDown))

onBeforeUnmount(() => {
  clearTimers()
  document.removeEventListener('pointerdown', onDocumentPointerDown)
})
</script>

<template>
  <nav
    ref="root"
    class="mega"
    :aria-label="t('layout.mainNavigation')"
    @mouseenter="onNavEnter"
    @mouseleave="onNavLeave"
    @focusout="onFocusOut"
    @keydown.esc="onEscape"
  >
    <ul class="container mega-list">
      <li v-for="item in mainNav" :key="item.key">
        <template v-if="item.columns">
          <button
            class="mega-top"
            :class="{ 'is-open': openKey === item.key, 'is-current': isCurrent(item) }"
            type="button"
            :data-game="item.game"
            :aria-expanded="openKey === item.key"
            :aria-controls="`mega-${item.key}`"
            @mouseenter="onItemHover(item.key)"
            @click="onItemClick(item.key)"
          >
            {{ t(item.labelKey) }}
            <ChevronDown class="mega-chevron" :size="16" aria-hidden="true" />
          </button>

          <Transition name="mega" :css="!switching">
            <div
              v-if="openKey === item.key"
              :id="`mega-${item.key}`"
              class="mega-panel"
              :data-game="item.game"
              @click="onPanelClick"
            >
              <div class="container mega-body">
                <div class="mega-head">
                  <h2 class="mega-title">{{ t(item.labelKey) }}</h2>
                  <router-link class="mega-all" :to="item.to">{{ t('nav.viewAll') }}</router-link>
                </div>

                <div class="mega-grid">
                  <section v-for="column in item.columns" :key="column.titleKey">
                    <h3 class="mega-group">{{ t(column.titleKey) }}</h3>
                    <ul class="mega-links">
                      <li v-for="link in column.links" :key="link.to">
                        <router-link class="mega-link" :to="link.to">{{ t(link.labelKey) }}</router-link>
                      </li>
                    </ul>
                  </section>

                  <aside class="mega-feature">
                    <BallMark class="mega-feature-ball" outline />
                    <h3 class="mega-feature-title">{{ t(item.feature.titleKey) }}</h3>
                    <p class="mega-feature-text">{{ t(item.feature.textKey) }}</p>
                    <router-link class="btn btn-on-brand mega-feature-action" :to="item.feature.to">
                      {{ t(item.feature.actionKey) }}
                    </router-link>
                  </aside>
                </div>
              </div>

              <div class="mega-foot">
                <div class="container">
                  <button class="mega-close" type="button" @click="close">
                    {{ t('layout.closeMenu') }}
                    <X :size="16" aria-hidden="true" />
                  </button>
                </div>
              </div>
            </div>
          </Transition>
        </template>

        <router-link
          v-else
          class="mega-top"
          :class="{ 'is-current': isCurrent(item) }"
          :to="item.to"
          @mouseenter="onPlainLinkHover"
        >
          {{ t(item.labelKey) }}
        </router-link>
      </li>
    </ul>
  </nav>
</template>

<style scoped>
.mega {
  position: relative;
  display: none;
  border-top: 1px solid var(--color-line);
}

@media (min-width: 1024px) {
  .mega {
    display: block;
  }
}

.mega-list {
  display: flex;
  gap: var(--space-6);
  margin-block: 0;
  list-style: none;
}

.mega-top {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  height: 2.625rem;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-ink);
  cursor: pointer;
  font-size: var(--text-sm);
  font-weight: 650;
  text-decoration: none;
  transition: color 0.2s;
}

.mega-top:hover,
.mega-top.is-open {
  color: var(--color-brand);
}

/* The bar under the open or current item, in the colour of its game. */
.mega-top::after {
  content: '';
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 3px;
  border-radius: var(--radius-xs) var(--radius-xs) 0 0;
  background: var(--accent);
  transform: scaleX(0);
  transition: transform 0.2s;
}

.mega-top.is-open::after,
.mega-top.is-current::after {
  transform: scaleX(1);
}

.mega-chevron {
  transition: transform 0.2s;
}

.mega-top.is-open .mega-chevron {
  transform: rotate(180deg);
}

.mega-panel {
  position: absolute;
  top: 100%;
  right: 0;
  left: 0;
  max-height: calc(100dvh - 8rem);
  overflow-y: auto;
  background: var(--color-surface);
  border-top: 1px solid var(--color-line);
  box-shadow: var(--shadow-panel);
}

.mega-enter-active {
  transition:
    opacity 0.16s ease-out,
    transform 0.16s ease-out;
}

.mega-enter-from {
  opacity: 0;
  transform: translateY(-0.5rem);
}

.mega-body {
  padding-block: var(--space-5);
}

.mega-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-4);
}

.mega-title {
  font-size: var(--text-xl);
}

.mega-all {
  color: var(--color-brand);
  font-size: var(--text-sm);
  font-weight: 650;
  text-decoration: none;
}

.mega-all:hover {
  text-decoration: underline;
}

.mega-grid {
  display: flex;
  gap: var(--space-6);
}

.mega-grid > section {
  flex: 1;
  min-width: 0;
}

.mega-group {
  margin-bottom: var(--space-2);
  color: var(--color-ink-soft);
  font-family: var(--font-body);
  font-size: var(--text-sm);
  font-stretch: 100%;
  font-weight: 650;
}

.mega-links {
  margin: 0;
  padding: 0;
  list-style: none;
}

.mega-link {
  display: block;
  padding-block: var(--space-1);
  color: var(--color-ink);
  font-size: var(--text-sm);
  text-decoration: none;
  transition: color 0.2s;
}

.mega-link:hover {
  color: var(--color-brand);
}

.mega-feature {
  position: relative;
  display: flex;
  flex: 0 0 18rem;
  flex-direction: column;
  align-items: flex-start;
  min-height: 15rem;
  overflow: hidden;
  padding: var(--space-5);
  border-radius: var(--radius-lg);
  background: var(--color-brand);
  color: var(--color-on-brand);
}

.mega-feature :focus-visible {
  outline-color: var(--color-focus-on-brand);
}

.mega-feature-ball {
  position: absolute;
  right: -3.5rem;
  bottom: -3.5rem;
  width: 13rem;
  height: 13rem;
  color: var(--color-on-brand);
  opacity: 0.28;
  transform: rotate(-18deg);
}

.mega-feature-title {
  position: relative;
  font-size: var(--text-xl);
}

.mega-feature-text {
  position: relative;
  margin-block: var(--space-2) var(--space-4);
  color: var(--color-on-brand-soft);
  font-size: var(--text-sm);
}

.mega-feature-action {
  position: relative;
  margin-top: auto;
  min-height: var(--touch-target);
  padding-inline: var(--space-4);
  font-size: var(--text-sm);
}

.mega-foot {
  background: var(--color-tint);
  border-top: 1px solid var(--color-line);
}

.mega-foot .container {
  display: flex;
  justify-content: flex-end;
}

.mega-close {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  min-height: var(--touch-target);
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-ink-soft);
  cursor: pointer;
  font-size: var(--text-sm);
  transition: color 0.2s;
}

.mega-close:hover {
  color: var(--color-brand);
}

@media (prefers-reduced-motion: reduce) {
  .mega-enter-active,
  .mega-top::after,
  .mega-chevron {
    transition: none;
  }
}
</style>
