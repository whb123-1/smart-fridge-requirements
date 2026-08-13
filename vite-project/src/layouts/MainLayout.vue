<template>
  <div class="app-shell">
    <router-view />
    <FloatMenu />
  </div>
</template>

<script setup lang="ts">
import FloatMenu from '../components/FloatMenu.vue'
</script>

<style scoped>
.app-shell {
  height: 100vh;
  overflow: auto;
  background: #f5f7fa;
}
</style>
