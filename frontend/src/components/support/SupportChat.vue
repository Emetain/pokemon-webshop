<script setup>
import { nextTick, ref, useId, watch } from 'vue'
import { Bot, RotateCcw, Send } from 'lucide-vue-next'
import FormMessage from '../FormMessage.vue'
import { useSupportChat } from '../../stores/supportChat'
import { t } from '../../utils/i18n'

/**
 * The chatbot: questions and answers, and a way on to a ticket when it can't help. Used on the contact page and in
 * the chat button. `ticket` asks the page to show the ticket form.
 */
defineProps({
  /** Taller message list, for the contact page. */
  roomy: { type: Boolean, default: false },
  /** Fill a panel of limited height: only the messages scroll. For the chat button. */
  fill: { type: Boolean, default: false },
})

const emit = defineEmits(['ticket'])

const chat = useSupportChat()
const id = useId()
const question = ref('')
const list = ref(null)

/** Links in an answer are shop paths such as /info/verzending: shown as links, everything else as text. */
const PATH = /((?<=^|[\s(])\/[a-z][a-z0-9/-]*[a-z0-9])/g

function parts(text) {
  return text.split(PATH).map((piece, index) => ({ link: index % 2 === 1, text: piece }))
}

async function send() {
  const text = question.value
  question.value = ''
  await chat.ask(text)
}

watch(
  () => [chat.state.messages.length, chat.state.busy],
  async () => {
    await nextTick()
    if (list.value) list.value.scrollTop = list.value.scrollHeight
  },
)
</script>

<template>
  <div class="chat" :class="{ fill }">
    <ol ref="list" class="messages" :class="{ roomy }" aria-live="polite" :aria-label="t('support.chat.conversation')">
      <li class="message assistant">
        <Bot class="avatar" :size="18" aria-hidden="true" />
        <p>{{ t('support.chat.welcome') }}</p>
      </li>
      <li v-for="(message, index) in chat.state.messages" :key="index" class="message" :class="message.role">
        <Bot v-if="message.role === 'assistant'" class="avatar" :size="18" aria-hidden="true" />
        <p>
          <span class="visually-hidden">{{ message.role === 'user' ? t('support.chat.you') : t('support.chat.assistant') }}: </span>
          <template v-for="(part, partIndex) in parts(message.text)" :key="partIndex">
            <router-link v-if="part.link && message.role === 'assistant'" :to="part.text">{{ part.text }}</router-link>
            <template v-else>{{ part.text }}</template>
          </template>
        </p>
      </li>
      <li v-if="chat.state.busy" class="message assistant typing" aria-busy="true">
        <Bot class="avatar" :size="18" aria-hidden="true" />
        <p>{{ t('support.chat.thinking') }}</p>
      </li>
    </ol>

    <FormMessage v-if="chat.state.error">{{ chat.state.error }}</FormMessage>

    <div v-if="chat.state.suggestTicket" class="suggest">
      <p>{{ t('support.chat.suggestTicket') }}</p>
      <button class="btn btn-primary btn-small" type="button" @click="emit('ticket')">{{ t('support.chat.makeTicket') }}</button>
    </div>

    <form class="ask" @submit.prevent="send">
      <label class="visually-hidden" :for="`${id}-question`">{{ t('support.chat.question') }}</label>
      <textarea
        :id="`${id}-question`"
        v-model="question"
        class="input"
        rows="2"
        maxlength="1000"
        :placeholder="t('support.chat.placeholder')"
        :disabled="chat.state.available === false"
        @keydown.enter.exact.prevent="send"
      ></textarea>
      <button
        class="btn btn-primary send"
        type="submit"
        :disabled="chat.state.busy || !question.trim() || chat.state.available === false"
        :aria-label="t('support.chat.send')"
      >
        <Send :size="18" aria-hidden="true" />
      </button>
    </form>

    <div class="foot">
      <p class="note">{{ t('support.chat.note') }}</p>
      <button v-if="chat.state.messages.length" class="link-button restart" type="button" @click="chat.reset()">
        <RotateCcw :size="14" aria-hidden="true" />
        {{ t('support.chat.restart') }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.chat {
  display: grid;
  gap: var(--space-3);
}

.messages {
  display: grid;
  align-content: start;
  gap: var(--space-2);
  max-height: 22rem;
  margin: 0;
  padding: var(--space-1);
  overflow-y: auto;
  list-style: none;
  overscroll-behavior: contain;
}

.messages.roomy {
  max-height: 28rem;
}

.chat.fill {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
}

.fill .messages {
  flex: 1 1 auto;
  min-height: 5rem;
  max-height: none;
}

.fill > :not(.messages) {
  flex: none;
}

.message {
  display: flex;
  gap: var(--space-2);
  align-items: flex-start;
  max-width: 88%;
}

.message p {
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  white-space: pre-line;
  overflow-wrap: anywhere;
}

.assistant p {
  border-top-left-radius: var(--radius-sm);
  background: var(--color-tint);
}

.user {
  justify-self: end;
}

.user p {
  border-top-right-radius: var(--radius-sm);
  background: var(--color-brand);
  color: var(--color-on-brand);
}

.avatar {
  flex: none;
  margin-top: var(--space-2);
  color: var(--color-brand);
}

.typing p {
  color: var(--color-ink-soft);
  font-style: italic;
}

.suggest {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-3);
  align-items: center;
  justify-content: space-between;
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-success-soft);
  font-size: var(--text-sm);
}

.ask {
  display: flex;
  gap: var(--space-2);
  align-items: stretch;
}

.input {
  flex: 1;
  min-width: 0;
  padding: var(--space-2) var(--space-3);
  border: 1.5px solid var(--color-line-strong);
  border-radius: var(--radius-sm);
  font: inherit;
  resize: none;
}

.send {
  flex: none;
  min-width: var(--touch-target);
  padding-inline: var(--space-3);
}

.foot {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1) var(--space-3);
  align-items: baseline;
  justify-content: space-between;
}

.note {
  color: var(--color-ink-soft);
  font-size: var(--text-xs);
}

.restart {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--text-xs);
}
</style>
