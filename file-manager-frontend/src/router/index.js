import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue')
  },
  {
    path: '/',
    component: () => import('../layout/MainLayout.vue'),
    redirect: '/files',
    children: [
      { path: 'files', name: 'Files', component: () => import('../views/Files.vue') },
      { path: 'files/recycle', name: 'RecycleBin', component: () => import('../views/RecycleBin.vue') },
      { path: 'users', name: 'Users', component: () => import('../views/Users.vue') },
      { path: 'roles', name: 'Roles', component: () => import('../views/Roles.vue') },
      { path: 'permissions', name: 'Permissions', component: () => import('../views/Permissions.vue') },
      { path: 'nodes', name: 'Nodes', component: () => import('../views/Nodes.vue') }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.path !== '/login' && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
