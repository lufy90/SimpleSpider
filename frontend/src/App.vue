<template>
  <el-container>
    <el-header v-if="showHeader" class="header">
      <div class="header-content">
        <div class="logo">SimpleSpider</div>
        <el-menu
          :default-active="activeMenu"
          mode="horizontal"
          router
          class="header-menu"
        >
          <el-menu-item index="/dyauthor">Authors</el-menu-item>
          <el-menu-item index="/dyvideo">Videos</el-menu-item>
          <el-menu-item index="/task">Tasks</el-menu-item>
        </el-menu>
        <div class="header-actions">
          <el-button @click="handleLogout" type="danger" size="small">Logout</el-button>
        </div>
      </div>
    </el-header>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const showHeader = computed(() => {
  return route.path !== '/login'
})

const activeMenu = computed(() => {
  return route.path
})

const handleLogout = () => {
  sessionStorage.removeItem('token')
  router.push('/login')
}
</script>
