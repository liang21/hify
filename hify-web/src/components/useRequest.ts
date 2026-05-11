import { ref } from 'vue'

export interface UseRequestOptions<T> {
  onSuccess?: (data: T) => void
  onError?: (error: Error) => void
  immediate?: boolean
}

export function useRequest<T = any, P extends any[] = any[]>(
  api: (...args: P) => Promise<T>,
  options: UseRequestOptions<T> = {}
) {
  const { onSuccess, onError, immediate = false } = options

  const data = ref<T | null>(null)
  const loading = ref(false)
  const error = ref<Error | null>(null)

  async function execute(...args: P): Promise<T | null> {
    loading.value = true
    error.value = null

    try {
      const res = await api(...args)
      data.value = res
      onSuccess?.(res)
      return res
    } catch (err: any) {
      error.value = err
      onError?.(err)
      return null
    } finally {
      loading.value = false
    }
  }

  function reset() {
    data.value = null
    loading.value = false
    error.value = null
  }

  return {
    data,
    loading,
    error,
    execute,
    reset
  }
}

export function useRequestLazy<T = any, P extends any[] = any[]>(
  api: (...args: P) => Promise<T>,
  options?: UseRequestOptions<T>
) {
  return useRequest<T, P>(api, { ...options, immediate: false })
}

export function useManualRequest<T = any, P extends any[] = any[]>(
  api: (...args: P) => Promise<T>
) {
  return useRequestLazy<T, P>(api)
}
