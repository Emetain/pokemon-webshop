<script setup>
import { computed, ref } from 'vue'
import { Check, Lock, Monitor, Moon, Sun } from 'lucide-vue-next'
import FormMessage from '../FormMessage.vue'
import { COLOR_MODES, COLOR_THEMES, useAppearance } from '../../stores/appearance'
import { openPremium } from '../../stores/premiumDialog'
import { useSession } from '../../stores/session'
import { t } from '../../utils/i18n'

/**
 * Light, dark or the device's setting, free for everyone, and the colour themes, which are Premium (phase 12). A
 * choice applies at once and is saved on the account, so it follows the customer to other devices.
 */
const MODE_ICONS = { LIGHT: Sun, DARK: Moon, SYSTEM: Monitor }

const session = useSession()
const { chosen } = useAppearance()
const premium = computed(() => session.isPremium.value)

const error = ref('')
const saving = ref(false)

async function save(changes) {
  error.value = ''
  saving.value = true
  try {
    await session.updateProfile(changes)
  } catch (exception) {
    error.value = exception.message
  } finally {
    saving.value = false
  }
}

function chooseTheme(theme) {
  if (theme !== 'POKETOPIA' && !premium.value) {
    openPremium('themes')
    return
  }
  save({ colorTheme: theme })
}
</script>

<template>
  <div class="appearance">
    <FormMessage v-if="error">{{ error }}</FormMessage>

    <fieldset class="group">
      <legend>{{ t('account.appearance.mode') }}</legend>
      <div class="modes">
        <button
          v-for="mode in COLOR_MODES"
          :key="mode"
          class="mode"
          type="button"
          :aria-pressed="chosen.mode === mode"
          :disabled="saving"
          @click="save({ colorMode: mode })"
        >
          <component :is="MODE_ICONS[mode]" :size="18" aria-hidden="true" />
          {{ t(`account.appearance.modes.${mode}`) }}
        </button>
      </div>
      <p class="hint">{{ t('account.appearance.modeHint') }}</p>
    </fieldset>

    <fieldset class="group">
      <legend>{{ t('account.appearance.theme') }}</legend>
      <div class="themes">
        <button
          v-for="theme in COLOR_THEMES"
          :key="theme"
          class="theme"
          type="button"
          :aria-pressed="chosen.theme === theme"
          :aria-haspopup="theme !== 'POKETOPIA' && !premium ? 'dialog' : undefined"
          :disabled="saving"
          @click="chooseTheme(theme)"
        >
          <span class="swatch" :data-swatch="theme.toLowerCase()" aria-hidden="true">
            <Check v-if="chosen.theme === theme" :size="16" />
            <Lock v-else-if="theme !== 'POKETOPIA' && !premium" :size="14" />
          </span>
          {{ t(`account.appearance.themes.${theme}`) }}
        </button>
      </div>
      <p class="hint">{{ premium ? t('account.appearance.themeHint') : t('account.appearance.themeLocked') }}</p>
    </fieldset>
  </div>
</template>

<style scoped>
.appearance {
  display: grid;
  gap: var(--space-5);
}

.group {
  margin: 0;
  padding: 0;
  border: 0;
}

.group legend {
  margin-bottom: var(--space-2);
  font-weight: 650;
}

.modes,
.themes {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.mode,
.theme {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-height: var(--touch-target);
  padding-inline: var(--space-3);
  border: 1.5px solid var(--color-line-strong);
  border-radius: var(--radius-round);
  background: var(--color-surface);
  color: var(--color-ink);
  cursor: pointer;
  font-size: var(--text-sm);
  font-weight: 600;
  transition: border-color 0.15s;
}

.mode[aria-pressed='true'],
.theme[aria-pressed='true'] {
  border-color: var(--color-brand-ink);
  box-shadow: inset 0 0 0 1px var(--color-brand-ink);
}

.swatch {
  display: inline-grid;
  place-items: center;
  width: 1.5rem;
  height: 1.5rem;
  border-radius: var(--radius-round);
  color: var(--color-on-brand);
}

.swatch[data-swatch='poketopia'] {
  background: var(--swatch-poketopia);
}

.swatch[data-swatch='fire'] {
  background: var(--swatch-fire);
}

.swatch[data-swatch='grass'] {
  background: var(--swatch-grass);
}

.swatch[data-swatch='electric'] {
  background: var(--swatch-electric);
  color: var(--color-ink);
}

.swatch[data-swatch='psychic'] {
  background: var(--swatch-psychic);
}

.hint {
  margin-top: var(--space-2);
  color: var(--color-ink-soft);
  font-size: var(--text-sm);
}
</style>
