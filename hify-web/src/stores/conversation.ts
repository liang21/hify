import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Message } from '@/types/conversation'

export const useConversationStore = defineStore('conversation', () => {
  const currentConversationId = ref<number | null>(null)
  const messages = ref<Message[]>([])

  function setCurrentConversation(id: number | null) {
    currentConversationId.value = id
  }

  function addMessage(message: Message) {
    messages.value.push(message)
  }

  function clearMessages() {
    messages.value = []
  }

  return {
    currentConversationId,
    messages,
    setCurrentConversation,
    addMessage,
    clearMessages
  }
})
