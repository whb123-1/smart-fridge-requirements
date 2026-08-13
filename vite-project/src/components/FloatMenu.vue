<template>
  <div class="float-menu">
    <transition name="fade">
      <div v-if="open" class="float-panel">
        <el-tooltip v-for="item in items" :key="item.key" :content="item.label" placement="right">
          <button class="fab" :class="{ primary: item.key === 'home' }" @click="onClick(item)">
            <el-badge v-if="item.key === 'reminders' && unread > 0" :value="unread" :max="99">
              <el-icon :size="18"><component :is="item.icon" /></el-icon>
            </el-badge>
            <el-icon v-else :size="18"><component :is="item.icon" /></el-icon>
          </button>
        </el-tooltip>
      </div>
    </transition>
      <button class="fab main-fab" @click="open = !open">
        <el-icon :size="20"><Refrigerator /></el-icon>
      </button>
  </div>
</template>

<script setup lang="ts">
import { markRaw, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Bell, Food, KnifeFork, Refrigerator, SwitchButton,
} from '@element-plus/icons-vue'
import { unreadCount } from '../api/reminder'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const open = ref(false)
const unread = ref(0)
let timer: number | undefined

const items = [
  { key: 'home', label: '3D 冰箱', path: '/', icon: markRaw(Refrigerator) },
  { key: 'recipes', label: '菜谱与偏好', path: '/recipes', icon: markRaw(Food) },
  { key: 'diet', label: '饮食与统计', path: '/diet', icon: markRaw(KnifeFork) },
  { key: 'reminders', label: '提醒', path: '/reminders', icon: markRaw(Bell) },
  { key: 'logout', label: '退出登录', icon: markRaw(SwitchButton) },
]

const onClick = async (item: { key: string; path?: string }) => {
  open.value = false
  if (item.key === 'logout') {
    await userStore.logout()
    router.push('/login')
  } else if (item.path) {
    router.push(item.path)
  }
}

const loadUnread = async () => {
  try {
    unread.value = await unreadCount()
  } catch {
    unread.value = 0
  }
}

watch(() => route.path, () => {
  open.value = false
})

onMounted(() => {
  loadUnread()
  timer = window.setInterval(loadUnread, 60000)
})

onBeforeUnmount(() => {
  if (timer) {
    window.clearInterval(timer)
  }
})
</script>

<style scoped>
.float-menu {
  position: fixed;
  left: 18px;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  z-index: 200;
}
.float-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 12px 8px;
  border-radius: 26px;
  background: rgba(20, 30, 45, 0.72);
  backdrop-filter: blur(8px);
  box-shadow: 0 8px 24px rgba(10, 20, 40, 0.3);
}
.fab {
  width: 42px;
  height: 42px;
  border: none;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: rgba(255, 255, 255, 0.12);
  cursor: pointer;
  transition: all 0.2s;
}
.fab:hover {
  background: rgba(255, 255, 255, 0.28);
  transform: scale(1.08);
}
.fab.primary {
  background: #409eff;
}
.fab.primary:hover {
  background: #66b1ff;
}
.main-fab {
  width: 50px;
  height: 50px;
  background: linear-gradient(135deg, #1f6feb, #35c0c0);
  box-shadow: 0 6px 20px rgba(31, 111, 235, 0.45);
}
.main-fab:hover {
  transform: scale(1.06);
  background: linear-gradient(135deg, #2a7df5, #45d0d0);
}
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.18s, transform 0.18s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateX(-12px);
}
</style>
