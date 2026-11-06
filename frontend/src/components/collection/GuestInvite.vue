<script setup>
import { useRoute } from 'vue-router'
import { t } from '../../utils/i18n'

defineProps({
  title: { type: String, required: true },
  text: { type: String, required: true },
})

const route = useRoute()
</script>

<template>
  <section class="invite">
    <h2>{{ title }}</h2>
    <p class="text">{{ text }}</p>
    <div class="actions">
      <router-link class="btn btn-primary" to="/registreren">{{ t('collection.guest.register') }}</router-link>
      <router-link class="btn btn-secondary" :to="{ path: '/inloggen', query: { redirect: route.fullPath } }">
        {{ t('collection.guest.login') }}
      </router-link>
    </div>
  </section>
</template>

<style scoped>
.invite {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
  padding: var(--space-5);
  border-radius: var(--radius-md);
  background: var(--color-tint);
}

.text {
  max-width: 60ch;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}
</style>
