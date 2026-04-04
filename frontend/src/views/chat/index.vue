<script setup lang="ts">
import ChatList from './modules/chat-list.vue';
import InputBox from './modules/input-box.vue';
</script>

<template>
  <div class="chat-workspace">
    <section class="chat-panel">
      <ChatList />
      <InputBox />
    </section>
  </div>
</template>

<style scoped lang="scss">
.chat-workspace {
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 126px);
}

.chat-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 14px;
  min-height: 0;
  padding: 18px;
  border: 1px solid rgb(15 23 42 / 6%);
  border-radius: 28px;
  background: rgb(255 255 255 / 86%);
  box-shadow: 0 24px 50px rgb(15 23 42 / 6%);
}

.dark {
  .chat-panel {
    border-color: rgb(255 255 255 / 8%);
    background: rgb(17 24 39 / 74%);
  }
}

@media (max-width: 640px) {
  .chat-panel {
    padding: 20px 16px;
    border-radius: 22px;
  }
}
</style>
