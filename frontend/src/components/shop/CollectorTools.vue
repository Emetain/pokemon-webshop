<script setup>
import { computed } from 'vue'
import { ArrowRight } from 'lucide-vue-next'
import { useSession } from '../../stores/session'
import { t } from '../../utils/i18n'

/**
 * The home page's tiles for the collector tools: the value graph, the card scanner and the pre-grade check, so
 * visitors see there's more than the shop. The drawings are the shop's own, never card art.
 */
const session = useSession()

const tools = computed(() => [
  {
    key: 'graph',
    to: session.isLoggedIn.value ? '/collectie' : '/registreren',
    action: session.isLoggedIn.value ? t('home.tools.graph.toCollection') : t('home.tools.graph.register'),
  },
  { key: 'scanner', to: '/scanner', action: t('home.tools.scanner.action') },
  { key: 'pregrade', to: '/pre-grade', action: t('home.tools.pregrade.action') },
])
</script>

<template>
  <section class="tools" aria-labelledby="home-tools">
    <h2 id="home-tools">{{ t('home.tools.title') }}</h2>
    <ul class="tool-tiles">
      <li v-for="tool in tools" :key="tool.key">
        <router-link class="tool" :to="tool.to">
          <span class="art">
            <svg v-if="tool.key === 'graph'" viewBox="0 0 240 140" aria-hidden="true">
              <path class="grid" d="M24 40H216M24 76H216M24 112H216" />
              <path class="area" d="M24 110L58 98L88 103L118 80L148 87L178 58L216 36V124H24Z" />
              <path class="line" d="M24 110L58 98L88 103L118 80L148 87L178 58L216 36" />
              <circle class="dot" cx="216" cy="36" r="6" />
            </svg>
            <svg v-else-if="tool.key === 'scanner'" viewBox="0 0 240 140" aria-hidden="true">
              <path class="corners" d="M70 32V12H90M150 12H170V32M170 108V128H150M90 128H70V108" />
              <rect class="card" x="82" y="18" width="76" height="104" rx="6" />
              <rect class="card-art" x="90" y="30" width="60" height="42" rx="2" />
              <path class="card-text" d="M90 84H140M90 94H128M90 110H106" />
              <path class="beam" d="M58 66H182" />
            </svg>
            <svg v-else viewBox="0 0 240 140" aria-hidden="true">
              <rect class="card" x="82" y="18" width="76" height="104" rx="6" />
              <rect class="frame" x="93" y="28" width="58" height="84" rx="2" />
              <path class="measure" d="M82 70H93M151 70H158" />
              <path class="tick" d="M82 64V76M93 64V76M151 64V76M158 64V76" />
              <text class="ratio" x="66" y="75" text-anchor="end">55</text>
              <text class="ratio" x="174" y="75">45</text>
            </svg>
          </span>
          <span class="body">
            <span class="badge" :class="{ premium: tool.key === 'pregrade' }">{{ t(`home.tools.${tool.key}.badge`) }}</span>
            <h3 class="name">{{ t(`home.tools.${tool.key}.title`) }}</h3>
            <span class="text">{{ t(`home.tools.${tool.key}.text`) }}</span>
            <span class="action">{{ tool.action }} <ArrowRight :size="16" aria-hidden="true" /></span>
          </span>
        </router-link>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.tool-tiles {
  display: grid;
  gap: var(--space-3);
  margin: var(--space-4) 0 0;
  padding: 0;
  list-style: none;
}

.tool {
  display: grid;
  grid-template-rows: auto 1fr;
  height: 100%;
  overflow: hidden;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  color: var(--color-ink);
  text-decoration: none;
}

.tool:focus-visible {
  outline-color: var(--color-focus);
}

.art {
  display: block;
  padding: var(--space-3) var(--space-4) 0;
  background: var(--color-tint);
}

.art svg {
  display: block;
  width: 100%;
  max-width: 18rem;
  height: auto;
  margin-inline: auto;
}

.body {
  display: grid;
  align-content: start;
  gap: var(--space-2);
  padding: var(--space-4);
}

.badge {
  justify-self: start;
  padding: 0.15rem var(--space-2);
  border-radius: var(--radius-round);
  background: var(--color-tint-strong);
  color: var(--color-brand-deep);
  font-size: var(--text-xs, 0.75rem);
  font-weight: 700;
}

.badge.premium {
  background: var(--color-brand-deep);
  color: var(--color-on-brand);
}

.name {
  font-family: var(--font-display);
  font-size: var(--text-lg);
  font-stretch: 88%;
  font-weight: 750;
  line-height: var(--leading-tight);
}

.text {
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}

.action {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  margin-top: var(--space-1);
  color: var(--color-link);
  font-size: var(--text-sm);
  font-weight: 650;
}

@media (hover: hover) {
  .tool:hover {
    border-color: var(--color-line-strong);
  }

  .tool:hover .action {
    text-decoration: underline;
  }
}

/* The drawings, in the theme's colours. */
.grid {
  fill: none;
  stroke: var(--color-line);
  stroke-width: 1.5;
}

.area {
  fill: var(--color-brand);
  opacity: 0.14;
}

.line {
  fill: none;
  stroke: var(--color-brand);
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 4;
}

.dot {
  fill: var(--color-brand);
  stroke: var(--color-tint);
  stroke-width: 3;
}

.card {
  fill: var(--color-surface);
  stroke: var(--color-line-strong);
  stroke-width: 2;
}

.card-art {
  fill: var(--color-tint-strong);
}

.card-text {
  fill: none;
  stroke: var(--color-line);
  stroke-linecap: round;
  stroke-width: 5;
}

.corners {
  fill: none;
  stroke: var(--color-brand);
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 4;
}

.beam {
  stroke: var(--color-brand);
  stroke-linecap: round;
  stroke-width: 3;
  filter: drop-shadow(0 0 4px var(--color-brand));
}

.frame {
  fill: var(--color-tint-strong);
  stroke: var(--color-brand);
  stroke-dasharray: 5 4;
  stroke-width: 2;
}

.measure,
.tick {
  fill: none;
  stroke: var(--color-brand);
  stroke-width: 2;
}

.ratio {
  fill: var(--color-brand-deep);
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 750;
}

@media (min-width: 900px) {
  .tool-tiles {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
