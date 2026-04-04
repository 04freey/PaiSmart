<script setup lang="ts">
import { NEmpty, NScrollbar } from 'naive-ui';
import { VueMarkdownItProvider } from 'vue-markdown-shiki';
import ChatMessage from './chat-message.vue';

defineOptions({
  name: 'ChatList'
});

const chatStore = useChatStore();
const { list, sessionId } = storeToRefs(chatStore);

const loading = ref(false);
const scrollbarRef = ref<InstanceType<typeof NScrollbar>>();

watch(() => [...list.value], scrollToBottom);

function scrollToBottom() {
  setTimeout(() => {
    scrollbarRef.value?.scrollBy({
      top: 999999999999999,
      behavior: 'auto'
    });
  }, 100);
}

const range = ref<[number, number]>([dayjs().subtract(7, 'day').valueOf(), dayjs().add(1, 'day').valueOf()]);

const params = computed(() => {
  return {
    start_date: dayjs(range.value[0]).format('YYYY-MM-DD'),
    end_date: dayjs(range.value[1]).format('YYYY-MM-DD')
  };
});

watchEffect(() => {
  getList();
});

async function getList() {
  loading.value = true;
  const { error, data } = await request<Api.Chat.Message[]>({
    url: 'users/conversation',
    params: params.value
  });
  if (!error) {
    list.value = data;
  }
  loading.value = false;
}

onMounted(() => {
  chatStore.scrollToBottom = scrollToBottom;
});
</script>

<template>
  <Suspense>
    <NScrollbar ref="scrollbarRef" class="chat-scroll h-0 flex-auto">
      <Teleport defer to="#header-extra">
        <div class="chat-filter-bar">
          <NForm :model="params" label-placement="left" :show-feedback="false" inline>
            <NFormItem label="时间">
              <NDatePicker v-model:value="range" type="daterange" />
            </NFormItem>
          </NForm>
        </div>
      </Teleport>
      <NSpin :show="loading">
        <div class="chat-list-shell">
          <div v-if="!loading && list.length === 0" class="chat-empty-state">
            <NEmpty description="还没有对话记录">
              <template #extra>
                <span class="text-13px color-#98a2b3">从一个具体问题开始，比如：这个文档的核心结论是什么？</span>
              </template>
            </NEmpty>
          </div>
          <VueMarkdownItProvider v-else>
            <ChatMessage v-for="(item, index) in list" :key="index" :msg="item" :session-id="sessionId" />
          </VueMarkdownItProvider>
        </div>
      </NSpin>
    </NScrollbar>
  </Suspense>
</template>

<style scoped lang="scss">
.chat-filter-bar {
  display: flex;
  align-items: center;
  min-height: 40px;
  padding: 0 8px;
}

.chat-list-shell {
  min-height: 100%;
  padding: 8px 6px 0;
}

.chat-filter-bar :deep(.n-form-item) {
  margin-right: 0 !important;
}

.chat-filter-bar :deep(.n-form-item-label),
.chat-filter-bar :deep(.n-input),
.chat-filter-bar :deep(.n-input__input-el),
.chat-filter-bar :deep(.n-base-selection),
.chat-filter-bar :deep(.n-base-selection-label),
.chat-filter-bar :deep(.n-date-picker .n-input) {
  background: transparent !important;
  box-shadow: none !important;
}

.chat-filter-bar :deep(.n-form-item-label) {
  min-width: auto !important;
  padding-right: 8px !important;
  font-size: 12px;
  color: #667085;
}

.chat-filter-bar :deep(.n-input),
.chat-filter-bar :deep(.n-base-selection) {
  border-radius: 999px;
}

.chat-empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 340px;
  border: 1px dashed rgb(91 108 255 / 16%);
  border-radius: 24px;
  background:
    radial-gradient(circle at top, rgb(91 108 255 / 10%), transparent 40%),
    linear-gradient(180deg, rgb(255 255 255 / 78%), rgb(248 250 252 / 92%));
}

.dark .chat-empty-state {
  border-color: rgb(255 255 255 / 10%);
  background:
    radial-gradient(circle at top, rgb(91 108 255 / 14%), transparent 40%),
    linear-gradient(180deg, rgb(17 24 39 / 70%), rgb(15 23 42 / 88%));
}
</style>
