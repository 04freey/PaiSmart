<script setup lang="ts">
defineOptions({
  name: 'SearchDialog'
});

const loading = ref(false);
const visible = defineModel<boolean>('visible', { default: false });

const { formRef, restoreValidation } = useNaiveForm();

const store = useAuthStore();
const model = ref<Api.KnowledgeBase.SearchParams>(createDefaultModel());

function createDefaultModel(): Api.KnowledgeBase.SearchParams {
  return {
    userId: `${store.userInfo.id}`,
    query: '',
    topK: 10
  };
}

const list = ref<Api.KnowledgeBase.SearchResult[]>([]);

const patterns = ref<string[]>([]);
function highlight(text: string) {
  if (!model.value.query) return false;
  if (text.includes(model.value.query)) return true;
  return false;
}

async function search() {
  loading.value = true;
  const { error, data } = await request<Api.KnowledgeBase.SearchResult[]>({
    url: '/search/hybrid',
    params: model.value,
    baseURL: '/proxy-api'
  });
  if (!error) {
    list.value = data;
    patterns.value = [model.value.query];
  }
  loading.value = false;
}

function reset() {
  model.value = createDefaultModel();
  patterns.value = [];
  list.value = [];
  restoreValidation();
}
watch(visible, () => {
  if (visible.value) {
    reset();
  }
});
</script>

<template>
  <NModal
    v-model:show="visible"
    preset="dialog"
    title="知识库检索"
    :show-icon="false"
    :mask-closable="false"
    class="w-1000px!"
  >
    <div class="search-hero">
      <div>
        <p class="search-eyebrow">大聪明 · Retrieval</p>
        <h3 class="search-title">用关键词快速验证召回结果</h3>
      </div>
      <p class="search-desc">适合检查某段文本是否被切片收录、召回分数是否合理，以及来源文件是否正确。</p>
    </div>
    <NForm
      ref="formRef"
      :model="model"
      label-placement="left"
      :label-width="60"
      inline
      class="pb-2"
      :show-feedback="false"
    >
      <NGrid>
        <NFormItemGi label="topK" path="topK" class="pr-24px" span="6">
          <NInputNumber
            v-model:value="model.topK"
            placeholder="请输入topK"
            clearable
            :min="1"
            :precision="0"
            :step="10"
          />
        </NFormItemGi>
        <NFormItemGi label="关键字" path="query" class="pr-24px" span="12">
          <NInput v-model:value="model.query" placeholder="请输入关键字" clearable />
        </NFormItemGi>
        <NFormItemGi span="6">
          <NSpace class="w-full" justify="end">
            <NButton @click="reset">
              <template #icon>
                <icon-ic-round-refresh class="text-icon" />
              </template>
              重置
            </NButton>
            <NButton type="primary" ghost @click="search">
              <template #icon>
                <icon-ic-round-search class="text-icon" />
              </template>
              搜索
            </NButton>
          </NSpace>
        </NFormItemGi>
      </NGrid>
    </NForm>
    <NSpin :show="loading">
      <NEmpty v-if="list.length === 0" description="暂无数据" class="py-100px" />
      <NScrollbar v-else class="max-h-500px">
        <NCard
          v-for="(item, index) in list"
          :key="index"
          class="result-card my-8"
          embedded
          :segmented="{
            content: true,
            footer: 'soft'
          }"
        >
          <div class="relative">
            <NHighlight
              v-if="highlight(item.textContent)"
              highlight-class="bg-[rgb(var(--primary-400-color))] color-white px-2 mx-1 rd-sm"
              :text="item.textContent"
              :patterns="patterns"
            />
            <span v-else>{{ item.textContent }}</span>
            <NTag
              :bordered="false"
              draggable
              class="absolute right-0 top-0 bg-[rgb(var(--primary-color)/.9)] color-white hover:bg-transparent hover:color-transparent"
            >
              Score: {{ item.score }}
            </NTag>
          </div>
          <template #footer>
            <span>来源：{{ item.fileName }}</span>
          </template>
        </NCard>
      </NScrollbar>
    </NSpin>
  </NModal>
</template>

<style scoped lang="scss">
.search-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 16px;
  padding: 18px 20px;
  border: 1px solid rgb(91 108 255 / 10%);
  border-radius: 22px;
  background:
    radial-gradient(circle at top right, rgb(91 108 255 / 14%), transparent 30%),
    linear-gradient(135deg, rgb(255 255 255 / 98%), rgb(245 247 255 / 98%));
}

.search-eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
  color: rgb(var(--primary-color));
  text-transform: uppercase;
}

.search-title {
  margin: 0;
  font-size: 24px;
  color: #101828;
}

.search-desc {
  max-width: 420px;
  margin: 0;
  font-size: 13px;
  line-height: 1.8;
  color: #667085;
}

.result-card {
  border-radius: 20px;
  box-shadow: 0 12px 30px rgb(15 23 42 / 5%);
}

@media (max-width: 768px) {
  .search-hero {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
