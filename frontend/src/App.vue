<script setup>
// Imported here so the region and the colours (phase 12) follow the account on every page, not only where they're set.
import './stores/region'
import './stores/appearance'
</script>

<template>
  <router-view />
</template>
