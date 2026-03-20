import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/dyauthor',
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
    },
    {
      path: '/dyauthor',
      name: 'dyauthor',
      component: () => import('@/views/DyauthorView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/dyauthor/:id',
      name: 'dyauthor-detail',
      component: () => import('@/views/DyauthorDetailView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/dyvideo',
      name: 'dyvideo',
      component: () => import('@/views/DyvideoView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/task',
      name: 'task',
      component: () => import('@/views/TaskView.vue'),
      meta: { requiresAuth: true },
    },
  ],
})

// Navigation guard
router.beforeEach((to, from, next) => {
  const token = sessionStorage.getItem('token')
  
  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next('/dyauthor')
  } else {
    next()
  }
})

export default router

