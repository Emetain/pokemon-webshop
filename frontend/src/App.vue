<script setup>
// Imported here so the region and the colours (phase 12) follow the account on every page, not only where they're set.
import './stores/region'
import './stores/appearance'
import NavigationProgress from './components/layout/NavigationProgress.vue'
</script>

<template>
  <NavigationProgress />
  <router-view />
</template>
