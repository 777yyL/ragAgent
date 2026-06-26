import { defineStore } from 'pinia'
import { ref } from 'vue'
import { sessionApi, type SessionUser } from '@/api'

export const useSessionStore = defineStore('session', () => {
  const user = ref<SessionUser | null>(null)
  const loading = ref(false)

  async function fetchUser() {
    loading.value = true
    try {
      user.value = await sessionApi.me()
    } catch {
      user.value = null
    } finally {
      loading.value = false
    }
  }

  async function logout() {
    try {
      await sessionApi.logout()
    } finally {
      user.value = null
      window.location.href = '/sso/login'
    }
  }

  function hasRole(code: string): boolean {
    return user.value?.roles?.includes(code) ?? false
  }

  function isAdmin(): boolean {
    return hasRole('ADMIN')
  }

  return { user, loading, fetchUser, logout, hasRole, isAdmin }
})
