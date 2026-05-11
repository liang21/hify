import { ElMessage, ElNotification, ElMessageBox } from 'element-plus'

type MessageOptions = {
  message?: string
  duration?: number
  showClose?: boolean
}

type NotificationOptions = MessageOptions & {
  title?: string
  position?: 'top-right' | 'top-left' | 'bottom-right' | 'bottom-left'
}

class Notify {
  success(message: string, options: MessageOptions = {}) {
    return ElMessage.success({
      message,
      duration: 3000,
      showClose: true,
      ...options
    })
  }

  error(message: string, options: MessageOptions = {}) {
    return ElMessage.error({
      message,
      duration: 5000,
      showClose: true,
      ...options
    })
  }

  warning(message: string, options: MessageOptions = {}) {
    return ElMessage.warning({
      message,
      duration: 3000,
      showClose: true,
      ...options
    })
  }

  info(message: string, options: MessageOptions = {}) {
    return ElMessage.info({
      message,
      duration: 3000,
      showClose: true,
      ...options
    })
  }

  successNotification(title: string, message?: string, options: NotificationOptions = {}) {
    return ElNotification.success({
      title,
      message,
      duration: 3000,
      ...options
    })
  }

  errorNotification(title: string, message?: string, options: NotificationOptions = {}) {
    return ElNotification.error({
      title,
      message,
      duration: 5000,
      ...options
    })
  }

  warningNotification(title: string, message?: string, options: NotificationOptions = {}) {
    return ElNotification.warning({
      title,
      message,
      duration: 3000,
      ...options
    })
  }

  infoNotification(title: string, message?: string, options: NotificationOptions = {}) {
    return ElNotification.info({
      title,
      message,
      duration: 3000,
      ...options
    })
  }

  confirm(message: string, title = '确认') {
    return ElMessageBox.confirm(message, title, {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  }

  async confirmAsync(message: string, title = '确认') {
    try {
      await this.confirm(message, title)
      return true
    } catch {
      return false
    }
  }
}

export const notify = new Notify()

export const notifySuccess = notify.success.bind(notify)
export const notifyError = notify.error.bind(notify)
export const notifyWarning = notify.warning.bind(notify)
export const notifyInfo = notify.info.bind(notify)
