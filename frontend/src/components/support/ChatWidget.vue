<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { MessageCircle, X } from 'lucide-vue-next'
import SupportChat from './SupportChat.vue'
import { useSupportChat } from '../../stores/supportChat'
import { t } from '../../utils/i18n'

/**
 * The chat button at the bottom right of the shop. Opens the assistant, with the same conversation as on the
 * contact page. Not on the contact page itself, which has the assistant on the page, nor during checkout.
 */
const route = useRoute()
const router = useRouter()
const chat = useSupportChat()
const open = ref(false)
const panel = ref(null)
const launcher = ref(null)

const HIDDEN = [/^\/info\/contact/, /^\/afrekenen/, /\/afrekenen$/, /^\/bestelling\//]
const shown = computed(() => chat.state.available && !HIDDEN.some((pattern) => pattern.test(route.path)))

async function toggle() {
  open.value = !open.value
  if (open.value) {
    await nextTick()
    panel.value?.querySelector('textarea')?.focus()
  }
}

/** With Escape or the close button: focus goes back to the button, instead of getting lost on the page. */
async function close() {
  open.value = false
  await nextTick()
  launcher.value?.focus()
}

function toTicket() {
  open.value = false
  router.push({ path: '/info/contact', query: { ticket: '1' } })
}

watch(
  () => route.path,
  () => (open.value = false),
)

onMounted(() => chat.loadStatus())
</script>

<template>
  <div v-if="shown" class="widget">
    <Transition name="panel">
      <section
        v-show="open"
        id="chat-panel"
        ref="panel"
        class="panel"
        role="dialog"
        aria-labelledby="chat-panel-title"
        @keydown.esc="close"
      >
        <header class="head">
          <h2 id="chat-panel-title">{{ t('support.widget.title') }}</h2>
          <button class="close" type="button" :aria-label="t('support.widget.close')" @click="close">
            <X :size="20" aria-hidden="true" />
          </button>
        </header>
        <SupportChat fill @ticket="toTicket" />
        <button class="link-button ticket-link" type="button" @click="toTicket">{{ t('support.widget.toTicket') }}</button>
      </section>
    </Transition>
    <button
      ref="launcher"
      class="launcher"
      type="button"
      :aria-expanded="open"
      aria-controls="chat-panel"
      :aria-label="open ? t('support.widget.close') : t('support.widget.open')"
      @click="toggle"
    >
      <X v-if="open" :size="24" aria-hidden="true" />
      <MessageCircle v-else :size="24" aria-hidden="true" />
      <span v-if="!open" class="launcher-text">{{ t('support.widget.label') }}</span>
    </button>
  </div>
</template>

<style scoped>
.widget {
  position: fixed;
  right: var(--gutter);
  bottom: var(--gutter);
  z-index: calc(var(--header-layer) - 1);
  display: grid;
  justify-items: end;
  gap: var(--space-3);
  /* Above the cookie notice while that is open, and back down along with it when it goes. */
  transform: translateY(calc(-1 * var(--cookie-notice-space, 0px)));
  transition: transform 0.4s var(--ease-spring);
}

.launcher {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-height: 3.25rem;
  padding: 0 var(--space-4);
  border: 0;
  border-radius: var(--radius-round);
  background: var(--color-brand);
  box-shadow: 0 10px 24px var(--color-shadow), inset 0 -3px 0 var(--color-brand-edge);
  color: var(--color-on-brand);
  font: inherit;
  font-weight: 700;
  cursor: pointer;
  transition: transform var(--press-out);
}

@media (hover: hover) {
  .launcher:hover {
    background: var(--color-brand-hover);
  }
}

.launcher:active {
  background: var(--color-brand-hover);
  transform: scale(var(--press-scale));
  transition: transform var(--press-in);
}

/* Fits between the header and the button: only the messages scroll. */
.panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  width: min(24rem, calc(100vw - 2 * var(--gutter)));
  max-height: min(34rem, calc(100dvh - 12.5rem - var(--cookie-notice-space, 0px)));
  padding: var(--space-4);
  overflow: hidden;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-panel);
  /* It grows out of the chat button below it, and shrinks back into it. */
  transform-origin: bottom right;
}

.panel-enter-active {
  transition:
    opacity 0.18s ease-out,
    transform 0.4s var(--ease-spring);
}

.panel-leave-active {
  transition:
    opacity 0.15s ease-in,
    transform 0.2s ease-in;
}

.panel-enter-from,
.panel-leave-to {
  opacity: 0;
  transform: translateY(0.75rem) scale(0.9);
}

@media (prefers-reduced-motion: reduce) {
  .widget {
    transition: none;
  }

  .panel-enter-from,
  .panel-leave-to {
    transform: none;
  }
}

.head,
.ticket-link {
  flex: none;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.head h2 {
  font-size: var(--text-lg);
}

.close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--touch-target);
  height: var(--touch-target);
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-ink);
  cursor: pointer;
}

@media (hover: hover) {
  .close:hover {
    background: var(--color-tint);
  }
}

.close:active {
  background: var(--color-tint);
}

.ticket-link {
  justify-self: start;
  font-size: var(--text-sm);
}

/* The header is taller once the menu bar shows. */
@media (min-width: 768px) {
  .panel {
    max-height: min(34rem, calc(100dvh - 18rem - var(--cookie-notice-space, 0px)));
  }
}

@media (max-width: 479px) {
  .launcher {
    width: 3.25rem;
    padding: 0;
    justify-content: center;
  }

  .launcher-text {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip: rect(0 0 0 0);
  }
}
</style>
