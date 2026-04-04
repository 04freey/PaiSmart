<script setup lang="tsx">
import type { UploadFileInfo } from 'naive-ui';
import { NButton, NEllipsis, NModal, NPopconfirm, NProgress, NTag, NUpload } from 'naive-ui';
import { uploadAccept } from '@/constants/common';
import { fakePaginationRequest } from '@/service/request';
import { UploadStatus } from '@/enum';
import SvgIcon from '@/components/custom/svg-icon.vue';
import FilePreview from '@/components/custom/file-preview.vue';
import UploadDialog from './modules/upload-dialog.vue';
import SearchDialog from './modules/search-dialog.vue';

const appStore = useAppStore();

// 文件预览相关状态
const previewVisible = ref(false);
const previewFileName = ref('');
const previewFileMd5 = ref('');

function apiFn() {
  return fakePaginationRequest<Api.KnowledgeBase.List>({ url: '/documents/uploads' });
}

function renderIcon(fileName: string) {
  const ext = getFileExt(fileName);
  if (ext) {
    if (uploadAccept.split(',').includes(`.${ext}`)) return <SvgIcon localIcon={ext} class="mx-4 text-12" />;
    return <SvgIcon localIcon="dflt" class="mx-4 text-12" />;
  }
  return null;
}

// 处理文件预览
function handleFilePreview(fileName: string, fileMd5: string) {
  console.log('[知识库] 点击预览按钮:', {
    fileName,
    fileMd5,
    完整信息: { fileName, fileMd5 }
  });

  previewFileName.value = fileName;
  previewFileMd5.value = fileMd5;
  previewVisible.value = true;
}

// 关闭文件预览
function closeFilePreview() {
  console.log('[知识库] 关闭文件预览');
  previewVisible.value = false;
  previewFileName.value = '';
  previewFileMd5.value = '';
}

const { columns, columnChecks, data, getData, loading } = useTable({
  apiFn,
  immediate: false,
  columns: () => [
    {
      key: 'fileName',
      title: '文件名',
      minWidth: 300,
      render: row => (
        <div class="flex items-center">
          {renderIcon(row.fileName)}
          <NEllipsis lineClamp={2} tooltip>
            <span
              class="cursor-pointer hover:text-primary transition-colors"
              onClick={() => handleFilePreview(row.fileName, row.fileMd5)}
            >
              {row.fileName}
            </span>
          </NEllipsis>
        </div>
      )
    },
    {
      key: 'fileMd5',
      title: 'MD5',
      width: 120,
      render: row => (
        <NEllipsis tooltip>
          <span
            class="cursor-pointer hover:text-primary transition-colors font-mono text-3"
            onClick={() => {
              navigator.clipboard.writeText(row.fileMd5);
              window.$message?.success('MD5已复制');
            }}
            title="点击复制MD5"
          >
            {row.fileMd5.substring(0, 8)}...
          </span>
        </NEllipsis>
      )
    },
    {
      key: 'totalSize',
      title: '文件大小',
      width: 100,
      render: row => fileSize(row.totalSize)
    },
    {
      key: 'status',
      title: '上传状态',
      width: 100,
      render: row => renderStatus(row.status, row.progress)
    },
    {
      key: 'orgTagName',
      title: '组织标签',
      width: 150,
      ellipsis: { tooltip: true, lineClamp: 2 }
    },
    {
      key: 'isPublic',
      title: '是否公开',
      width: 100,
      render: row => (row.public || row.isPublic ? <NTag type="success">公开</NTag> : <NTag type="warning">私有</NTag>)
    },
    {
      key: 'createdAt',
      title: '上传时间',
      width: 100,
      render: row => dayjs(row.createdAt).format('YYYY-MM-DD')
    },
    {
      key: 'operate',
      title: '操作',
      width: 180,
      render: row => (
        <div class="flex gap-4">
          {renderResumeUploadButton(row)}
          <NButton type="primary" ghost size="small" onClick={() => handleFilePreview(row.fileName, row.fileMd5)}>
            预览
          </NButton>
          <NPopconfirm onPositiveClick={() => handleDelete(row.fileMd5)}>
            {{
              default: () => '确认删除当前文件吗？',
              trigger: () => (
                <NButton type="error" ghost size="small">
                  删除
                </NButton>
              )
            }}
          </NPopconfirm>
        </div>
      )
    }
  ]
});

const store = useKnowledgeBaseStore();
const { tasks } = storeToRefs(store);

const summary = computed(() => {
  const total = tasks.value.length;
  const completed = tasks.value.filter(item => item.status === UploadStatus.Completed).length;
  const processing = tasks.value.filter(item => item.status === UploadStatus.Pending).length;
  const interrupted = tasks.value.filter(item => item.status === UploadStatus.Break).length;
  const publicCount = tasks.value.filter(item => item.public || item.isPublic).length;

  return { total, completed, processing, interrupted, publicCount };
});

const summaryItems = computed(() => [
  { label: '文件总数', value: summary.value.total, hint: '当前知识库中的文件规模' },
  { label: '已完成', value: summary.value.completed, hint: '已解析并建立索引的文档' },
  { label: '处理中', value: summary.value.processing, hint: '正在上传、解析或向量化' },
  { label: '公开文档', value: summary.value.publicCount, hint: '团队可直接检索的内容' }
]);

onMounted(async () => {
  await getList();
});

/** 异步获取列表函数 该函数主要用于更新或初始化上传任务列表 它首先调用getData函数获取数据，然后根据获取到的数据状态更新任务列表 */
async function getList() {
  console.log('[知识库] 开始获取文件列表');

  // 等待获取最新数据
  await getData();

  console.log('[知识库] 获取到原始数据，数量:', data.value.length);
  data.value.forEach((item, index) => {
    console.log(`[知识库] 原始数据[${index}]:`, {
      fileName: item.fileName,
      fileMd5: item.fileMd5,
      status: item.status
    });
  });

  if (data.value.length === 0) {
    tasks.value = [];
    return;
  }

  // 遍历获取到的数据，以处理每个项目
  data.value.forEach((item, dataIndex) => {
    // 检查项目状态是否为已完成
    if (item.status === UploadStatus.Completed) {
      // 查找任务列表中是否有匹配的文件MD5
      const index = tasks.value.findIndex(task => task.fileMd5 === item.fileMd5);
      // 如果找到匹配项，则更新其状态
      if (index !== -1) {
        tasks.value[index].status = UploadStatus.Completed;
        console.log(`[知识库] 更新现有任务[${index}]:`, {
          fileName: item.fileName,
          fileMd5: item.fileMd5
        });
      } else {
        // 如果没有找到匹配项，则将该项目添加到任务列表中
        tasks.value.push(item);
        console.log(`[知识库] 添加新任务[${tasks.value.length - 1}]:`, {
          fileName: item.fileName,
          fileMd5: item.fileMd5
        });
      }
    } else if (!tasks.value.some(task => task.fileMd5 === item.fileMd5)) {
      // 如果项目状态不是已完成，并且任务列表中没有相同的文件MD5，则将该项目的状态设置为中断，并添加到任务列表中
      item.status = UploadStatus.Break;
      tasks.value.push(item);
      console.log(`[知识库] 添加中断任务[${tasks.value.length - 1}]:`, {
        fileName: item.fileName,
        fileMd5: item.fileMd5
      });
    }
  });

  console.log('[知识库] 任务列表处理完成，总数:', tasks.value.length);
  tasks.value.forEach((task, index) => {
    console.log(`[知识库] 最终任务[${index}]:`, {
      fileName: task.fileName,
      fileMd5: task.fileMd5,
      status: task.status
    });
  });
}

async function handleDelete(fileMd5: string) {
  const index = tasks.value.findIndex(task => task.fileMd5 === fileMd5);

  if (index !== -1) {
    tasks.value[index].requestIds?.forEach(requestId => {
      request.cancelRequest(requestId);
    });
  }

  // 如果文件一个分片也没有上传完成，则直接删除
  if (tasks.value[index].uploadedChunks && tasks.value[index].uploadedChunks.length === 0) {
    tasks.value.splice(index, 1);
    return;
  }

  const { error } = await request({ url: `/documents/${fileMd5}`, method: 'DELETE' });
  if (!error) {
    tasks.value.splice(index, 1);
    window.$message?.success('删除成功');
    await getData();
  }
}

// #region 文件上传
const uploadVisible = ref(false);
function handleUpload() {
  uploadVisible.value = true;
}
// #endregion

// #region 检索知识库
const searchVisible = ref(false);
function handleSearch() {
  searchVisible.value = true;
}
// #endregion

// 渲染上传状态
function renderStatus(status: UploadStatus, percentage: number) {
  if (status === UploadStatus.Completed) return <NTag type="success">已完成</NTag>;
  else if (status === UploadStatus.Break) return <NTag type="error">上传中断</NTag>;
  return <NProgress percentage={percentage} processing />;
}

// #region 文件续传
function renderResumeUploadButton(row: Api.KnowledgeBase.UploadTask) {
  if (row.status === UploadStatus.Break) {
    if (row.file)
      return (
        <NButton type="primary" size="small" ghost onClick={() => resumeUpload(row)}>
          续传
        </NButton>
      );
    return (
      <NUpload
        show-file-list={false}
        default-upload={false}
        accept={uploadAccept}
        onBeforeUpload={options => onBeforeUpload(options, row)}
        class="w-fit"
      >
        <NButton type="primary" size="small" ghost>
          续传
        </NButton>
      </NUpload>
    );
  }
  return null;
}

// 任务列表存在文件，直接续传
function resumeUpload(row: Api.KnowledgeBase.UploadTask) {
  row.status = UploadStatus.Pending;
  store.startUpload();
}

async function onBeforeUpload(
  options: { file: UploadFileInfo; fileList: UploadFileInfo[] },
  row: Api.KnowledgeBase.UploadTask
) {
  const md5 = await calculateMD5(options.file.file!);
  if (md5 !== row.fileMd5) {
    window.$message?.error('两次上传的文件不一致');
    return false;
  }
  loading.value = true;
  const { error, data: progress } = await request<Api.KnowledgeBase.Progress>({
    url: '/upload/status',
    params: { file_md5: row.fileMd5 }
  });
  if (!error) {
    row.file = options.file.file!;
    row.status = UploadStatus.Pending;
    row.progress = progress.progress;
    row.uploadedChunks = progress.uploaded;
    store.startUpload();
    loading.value = false;
    return true;
  }
  loading.value = false;
  return false;
}
</script>

<template>
  <div class="min-h-500px flex-col-stretch gap-16px overflow-hidden lt-sm:overflow-auto">
    <section class="knowledge-overview">
      <div class="overview-main">
        <p class="overview-eyebrow">大聪明 · 知识底座</p>
        <h1 class="overview-title">让上传、检索和预览落在同一个工作台里</h1>
        <p class="overview-desc">
          这里不是“文件堆放处”，而是你给大聪明持续喂知识、管理权限、验证召回效果的控制面板。
        </p>
      </div>
      <div class="overview-actions">
        <NButton type="primary" size="large" @click="handleUpload">
          <template #icon>
            <icon-mdi:upload />
          </template>
          上传文件
        </NButton>
        <NButton size="large" ghost type="primary" @click="handleSearch">
          <template #icon>
            <icon-ic-round-search class="text-icon" />
          </template>
          检索知识库
        </NButton>
      </div>
    </section>

    <section class="summary-grid">
      <article v-for="item in summaryItems" :key="item.label" class="summary-item">
        <span class="summary-label">{{ item.label }}</span>
        <strong class="summary-value">{{ item.value }}</strong>
        <span class="summary-hint">{{ item.hint }}</span>
      </article>
    </section>

    <NCard title="文件列表" :bordered="false" size="small" class="sm:flex-1-hidden card-wrapper table-panel">
      <template #header-extra>
        <TableHeaderOperation v-model:columns="columnChecks" :loading="loading" @add="handleUpload" @refresh="getList" />
      </template>
      <NDataTable
        striped
        :columns="columns"
        :data="tasks"
        size="small"
        :flex-height="!appStore.isMobile"
        :scroll-x="962"
        :loading="loading"
        remote
        :row-key="row => row.id"
        :pagination="false"
        class="sm:h-full"
      />
    </NCard>
    <UploadDialog v-model:visible="uploadVisible" />
    <SearchDialog v-model:visible="searchVisible" />

    <NModal v-model:show="previewVisible" preset="card" title="文件预览" style="width: 80%; max-width: 1000px;">
      <FilePreview
        :file-name="previewFileName"
        :file-md5="previewFileMd5"
        :visible="previewVisible"
        @close="closeFilePreview"
      />
    </NModal>
  </div>
</template>

<style scoped lang="scss">
.knowledge-overview {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding: 28px 30px;
  border-radius: 28px;
  background:
    radial-gradient(circle at top right, rgb(91 108 255 / 18%), transparent 28%),
    linear-gradient(135deg, rgb(255 255 255 / 96%), rgb(244 247 255 / 96%));
  border: 1px solid rgb(91 108 255 / 10%);
  box-shadow: 0 24px 50px rgb(15 23 42 / 7%);
}

.overview-main {
  max-width: 720px;
}

.overview-eyebrow {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.18em;
  color: rgb(var(--primary-color));
  text-transform: uppercase;
}

.overview-title {
  margin: 0;
  font-size: clamp(28px, 3vw, 40px);
  line-height: 1.15;
  color: #101828;
}

.overview-desc {
  margin: 14px 0 0;
  max-width: 640px;
  font-size: 15px;
  line-height: 1.8;
  color: #475467;
}

.overview-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.summary-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 20px 22px;
  border-radius: 22px;
  background: rgb(255 255 255 / 88%);
  border: 1px solid rgb(15 23 42 / 6%);
}

.summary-label {
  font-size: 13px;
  color: #667085;
}

.summary-value {
  font-size: 30px;
  line-height: 1;
  color: #101828;
}

.summary-hint {
  font-size: 12px;
  color: #98a2b3;
}

.table-panel {
  border-radius: 28px;
  box-shadow: 0 24px 50px rgb(15 23 42 / 6%);
}

.file-list-container {
  transition: width 0.3s ease;
}

:deep() {
  .n-progress-icon.n-progress-icon--as-text {
    white-space: nowrap;
  }
}

@media (max-width: 960px) {
  .knowledge-overview {
    flex-direction: column;
    align-items: flex-start;
  }

  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .knowledge-overview {
    padding: 22px 18px;
    border-radius: 22px;
  }

  .overview-title {
    font-size: 26px;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
