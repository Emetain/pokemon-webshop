<script setup>
import BinderPage from '../BinderPage.vue'

defineProps({
  title: { type: String, required: true },
  intro: { type: String, default: '' },
})
</script>

<template>
  <div class="container auth-shell">
    <section class="auth-form">
      <h1>{{ title }}</h1>
      <p v-if="intro" class="intro">{{ intro }}</p>
      <div class="auth-body">
        <slot />
      </div>
    </section>

    <aside class="auth-binder">
      <BinderPage />
    </aside>
  </div>
</template>

<style scoped>
.auth-shell {
  display: grid;
  padding-block: var(--space-6);
}

.auth-form {
  width: 100%;
  max-width: 26rem;
  margin-inline: auto;
}

.intro {
  margin-top: var(--space-2);
  color: var(--color-ink-soft);
}

.auth-body {
  margin-top: var(--space-5);
}

.auth-binder {
  display: none;
}

@media (min-width: 960px) {
  .auth-shell {
    max-width: 60rem;
    grid-template-columns: minmax(0, 26rem) minmax(0, 24rem);
    align-items: center;
    justify-content: space-between;
    gap: var(--space-7);
    padding-block: var(--space-7);
  }

  .auth-form {
    margin-inline: 0;
  }

  .auth-binder {
    display: block;
    padding: var(--space-5);
    border-radius: var(--radius-lg);
    background: var(--color-binder);
  }
}
</style>
