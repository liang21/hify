import { ElMessageBox, ElMessage } from 'element-plus'

interface ConfirmOptions {
  message?: string
  title?: string
  confirmButtonText?: string
  cancelButtonText?: string
  type?: 'warning' | 'info' | 'success' | 'error'
}

export function useConfirm() {
  async function confirm<T = any>(
    api: () => Promise<T>,
    options: ConfirmOptions = {}
  ): Promise<T | null> {
    const {
      message = '确定要删除吗？删除后无法恢复。',
      title = '确认删除',
      confirmButtonText = '确定',
      cancelButtonText = '取消',
      type = 'warning'
    } = options

    try {
      await ElMessageBox.confirm(message, title, {
        confirmButtonText,
        cancelButtonText,
        type,
        confirmButtonClass: 'el-button--danger'
      })

      const result = await api()
      ElMessage.success('操作成功')
      return result
    } catch (error: any) {
      if (error === 'cancel') {
        return null
      }
      ElMessage.error(error.message || '操作失败')
      throw error
    }
  }

  async function confirmDelete<T = any>(
    api: () => Promise<T>,
    targetName?: string
  ): Promise<T | null> {
    const message = targetName ? `确定要删除 "${targetName}" 吗？` : '确定要删除吗？'
    return confirm(api, { message, type: 'warning' })
  }

  return {
    confirm,
    confirmDelete
  }
}
