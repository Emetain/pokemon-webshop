<script setup>
import { ref, shallowRef, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ChevronLeft, ChevronRight, Heart, ShoppingBag, User, X } from 'lucide-vue-next'
import LiveBadge from '../drops/LiveBadge.vue'
import { mainNav, menuColumns } from '../../data/navigation'
import { useShopNavigation } from '../../stores/shopNavigation'
import { useSession } from '../../stores/session'
import { useDrawer } from '../../utils/drawer'
import { t } from '../../utils/i18n'

const { seriesByGame, loadSeries } = useShopNavigation()
loadSeries('pokemon')

const columnsOf = (item) => menuColumns(item, seriesByGame.value)

const route = useRoute()
const session = useSession()

const dialog = ref(null)
const panel = ref(null)
const scrim = ref(null)
const body = ref(null)
// The item whose sub-menu is showing. Null shows the main list.
const activeItem = shallowRef(null)
// A sub-menu comes in from the right and goes back out to the right, like pages pushed onto a stack.
const direction = ref('forward')

const drawer = useDrawer({ dialog, panel, scrim, side: 'left', onDismiss: () => drawer.close() })

function open() {
  direction.value = 'forward'
  activeItem.value = null
  drawer.open()
}

function showSub(item) {
  direction.value = 'forward'
  activeItem.value = item
  body.value.scrollTop = 0
}

function showMain() {
  direction.value = 'back'
  activeItem.value = null
  body.value.scrollTop = 0
}

function onClick(event) {
  if (event.target.closest('a')) drawer.close()
}

watch(() => route.fullPath, () => drawer.close())

defineExpose({ open })
</script>

<template>
  <dialog
    ref="dialog"
    class="mobile-menu"
    :aria-label="t('layout.menu')"
    @click="onClick"
    @cancel.prevent="drawer.close()"
  >
    <div ref="scrim" class="scrim" @click="drawer.close()"></div>
    <div ref="panel" class="panel">
      <div class="panel-head">
        <button v-if="activeItem" class="back-button" type="button" @click="showMain">
          <ChevronLeft :size="20" aria-hidden="true" />
          {{ t('layout.back') }}
        </button>
        <span v-else class="panel-title">{{ t('layout.menu') }}</span>
        <button class="close-button" type="button" :aria-label="t('layout.closeMenu')" @click="drawer.close()">
          <X :size="24" aria-hidden="true" />
        </button>
      </div>

      <nav ref="body" class="panel-body" :aria-label="t('layout.mainNavigation')">
        <div class="pages">
          <Transition :name="direction">
            <ul v-if="!activeItem" class="rows">
              <li v-for="item in mainNav" :key="item.key">
                <button v-if="item.columns" class="row" type="button" @click="showSub(item)">
                  <span class="tile" :data-game="item.game"><component :is="item.icon" class="tile-icon" /></span>
                  <span class="row-label">{{ t(item.labelKey) }}</span>
                  <ChevronRight class="row-chevron" :size="18" aria-hidden="true" />
                </button>
                <router-link v-else class="row" :to="item.to">
                  <span class="tile"><component :is="item.icon" class="tile-icon" /></span>
                  <span class="row-label">{{ t(item.labelKey) }}</span>
                  <LiveBadge v-if="item.key === 'drops'" />
                </router-link>
              </li>
            </ul>

            <div v-else :key="activeItem.key" class="sub">
              <div class="sub-head">
                <h2 class="sub-title">{{ t(activeItem.labelKey) }}</h2>
                <router-link class="sub-all" :to="activeItem.to">{{ t('nav.viewAll') }}</router-link>
              </div>
              <section v-for="column in columnsOf(activeItem)" :key="column.titleKey" class="sub-group">
                <h3 class="sub-group-title">{{ t(column.titleKey) }}</h3>
                <ul class="sub-links">
                  <li v-for="link in column.links" :key="link.to">
                    <router-link class="sub-link" :to="link.to">{{ link.label ?? t(link.labelKey) }}</router-link>
                  </li>
                </ul>
              </section>
            </div>
          </Transition>
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
/* The dialog covers the screen. The scrim and the panel inside it are moved by useDrawer. */
.mobile-menu {
  position: fixed;
  inset: 0;
  width: 100%;
  max-width: none;
  height: 100dvh;
  max-height: none;
  margin: 0;
  padding: 0;
  /* Clip, not hidden: a hidden overflow can still scroll, and the browser would scroll the dialog sideways to the
     close button it focuses while the panel is still off screen. */
  overflow: clip;
  border: 0;
  background: transparent;
  color: var(--color-ink);
}

.mobile-menu::backdrop {
  background: transparent;
}

.scrim {
  position: absolute;
  inset: 0;
  background: var(--color-brand-deep);
  opacity: 0;
}

.panel {
  position: absolute;
  inset: 0 auto 0 0;
  display: flex;
  flex-direction: column;
  width: min(26rem, 100vw);
  background: var(--color-surface);
  transform: translate3d(-100%, 0, 0);
  /* Up and down scroll the menu and two fingers zoom; sideways is the swipe that closes it. */
  touch-action: pan-y pinch-zoom;
}

/* Pulled further in than open, the panel shows no gap at the edge of the screen. */
.panel::after {
  content: '';
  position: absolute;
  inset: 0 100% 0 auto;
  width: 4rem;
  background: inherit;
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
  overflow-x: hidden;
  overflow-y: auto;
  /* A scrolling box decides on touches itself: it has to leave sideways swipes to the panel as well. */
  touch-action: pan-y pinch-zoom;
  padding: var(--space-2) var(--space-4);
}

/* The page going and the page coming share one cell while they pass each other. */
.pages {
  display: grid;
}

.pages > * {
  grid-area: 1 / 1;
}

/*
 * Forward, the sub-menu comes in from the right while the list makes way to the left. Back is the same path in
 * reverse, so the sub-menu leaves the way it came. Without motion, the two just cross-fade.
 */
.forward-enter-active,
.forward-leave-active,
.back-enter-active,
.back-leave-active {
  transition:
    transform 0.32s var(--ease-spring),
    opacity 0.2s ease-out;
}

.forward-enter-from,
.back-leave-to {
  opacity: 0;
  transform: translateX(40%);
}

.forward-leave-to,
.back-enter-from {
  opacity: 0;
  transform: translateX(-25%);
}

.forward-leave-active,
.back-leave-active {
  pointer-events: none;
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

@media (prefers-reduced-motion: reduce) {
  .forward-enter-from,
  .forward-leave-to,
  .back-enter-from,
  .back-leave-to {
    transform: none;
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

/* A row lights up under the finger, the way a list on a phone does. */
.row,
.sub-link,
.foot-link {
  -webkit-touch-callout: none;
  transition: background-color 0.25s;
}

.row:active,
.sub-link:active,
.foot-link:active {
  background: var(--color-tint);
  transition: none;
}

.back-button,
.close-button {
  transition: transform var(--press-out);
}

.back-button:active,
.close-button:active {
  transform: scale(0.92);
  transition: transform var(--press-in);
}
</style>
