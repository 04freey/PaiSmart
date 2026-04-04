<script setup lang="ts">
// eslint-disable-next-line @typescript-eslint/no-unused-vars
import { nextTick } from 'vue';
import { VueMarkdownIt } from 'vue-markdown-shiki';
import { formatDate } from '@/utils/common';
import { $t } from '@/locales';
defineOptions({ name: 'ChatMessage' });

const props = defineProps<{
  msg: Api.Chat.Message,
  sessionId?: string
}>();

const authStore = useAuthStore();

function handleCopy(content: string) {
  navigator.clipboard.writeText(content);
  window.$message?.success('已复制');
}

const chatStore = useChatStore();

// 存储文件名和对应的事件处理
const sourceFiles = ref<Array<{fileName: string, id: string, referenceNumber: number, fileMd5?: string}>>([]);

// 处理来源文件链接的函数
function processSourceLinks(text: string): string {
  // 重置来源文件列表，避免重复
  sourceFiles.value = [];

  // 新格式：匹配 (来源#数字: 文件名 | MD5:xxx) 的正则表达式，兼容全角括号
  // 格式示例：(来源#1: test.txt | MD5:abc123) 或 (来源#1: test.txt|MD5:abc123)
  const newSourcePattern = /([\(（])来源#(\d+):\s*([^|\n\r（）]+?)\s*\|\s*MD5:\s*([a-fA-F0-9]+)([\)）])/g;

  // 先处理新格式（包含MD5）
  let processedText = text.replace(newSourcePattern, (_match, leftParen, sourceNum, fileName, fileMd5, rightParen) => {
    const linkClass = 'source-file-link';
    const trimmedFileName = fileName.trim();
    const trimmedMd5 = fileMd5.trim();
    const fileId = `source-file-${sourceFiles.value.length}`;
    const referenceNumber = parseInt(sourceNum, 10);

    // 存储文件信息（包含文件名和MD5）
    sourceFiles.value.push({
      fileName: trimmedFileName,
      id: fileId,
      referenceNumber,
      fileMd5: trimmedMd5
    });

    const lp = leftParen === '(' ? '(' : '（';
    const rp = rightParen === ')' ? ')' : '）';

    // 显示格式：来源#1: test.txt | MD5:abc...
    return `${lp}来源#${sourceNum}: <span class="${linkClass}" data-file-id="${fileId}">${trimmedFileName} | MD5:${trimmedMd5.substring(0, 8)}...</span>${rp}`;
  });

  // 旧格式：匹配 (来源#数字: 文件名) 的正则表达式，兼容全角括号和无括号格式
  // 用于向后兼容旧的引用格式
  const oldSourcePattern = /([\(（])来源#(\d+):\s*([^\n\r（）]+?)([\)）])/g;

  processedText = processedText.replace(oldSourcePattern, (_match, leftParen, sourceNum, fileName, rightParen) => {
    const linkClass = 'source-file-link';
    const trimmedFileName = fileName.trim();
    const fileId = `source-file-${sourceFiles.value.length}`;
    const referenceNumber = parseInt(sourceNum, 10);

    // 存储文件信息（旧格式，没有MD5）
    sourceFiles.value.push({
      fileName: trimmedFileName,
      id: fileId,
      referenceNumber
    });

    const lp = leftParen || '';
    const rp = rightParen || '';

    return `${lp}来源#${sourceNum}: <span class="${linkClass}" data-file-id="${fileId}">${trimmedFileName}</span>${rp}`;
  });

  return processedText;
}

const content = computed(() => {
  chatStore.scrollToBottom?.();
  const rawContent = props.msg.content ?? '';

  // 只对助手消息处理来源链接
  if (props.msg.role === 'assistant') {
    return processSourceLinks(rawContent);
  }

  return rawContent;
});

// 处理内容点击事件（事件委托）
function handleContentClick(event: MouseEvent) {
  const target = event.target as HTMLElement;

  // 检查点击的是否是文件链接
  if (target.classList.contains('source-file-link')) {
    const fileId = target.getAttribute('data-file-id');
    if (fileId) {
      const file = sourceFiles.value.find(f => f.id === fileId);
      if (file) {
        handleSourceFileClick({
          fileName: file.fileName,
          referenceNumber: file.referenceNumber,
          fileMd5: file.fileMd5
        });
      }
    }
  }
}

// 处理来源文件点击事件
async function handleSourceFileClick(fileInfo: { fileName: string, referenceNumber: number, fileMd5?: string }) {
  const { fileName, referenceNumber, fileMd5: extractedMd5 } = fileInfo;
  console.log('点击了来源文件:', fileName, '引用编号:', referenceNumber, '提取的MD5:', extractedMd5, '会话ID:', props.sessionId);

  try {
    window.$message?.loading(`正在获取文件下载链接: ${fileName}`, {
      duration: 0,
      closable: false
    });

    let targetMd5 = null;

    // 方案1：优先使用从引用中直接提取的MD5
    if (extractedMd5) {
      console.log('使用从引用中提取的MD5:', extractedMd5);
      targetMd5 = extractedMd5;
    }
    // 方案2：如果没有提取到MD5，则通过后端API查询
    else if (props.sessionId) {
      try {
        console.log('步骤1: 通过API查询引用MD5', { sessionId: props.sessionId, referenceNumber });
        const { error: md5Error, data: md5Data } = await request<Api.Document.ReferenceMd5Response>({
          url: 'documents/reference-md5',
          params: {
            sessionId: props.sessionId,
            referenceNumber: referenceNumber.toString()
          },
          baseURL: '/proxy-api'
        });

        console.log('引用MD5查询结果:', { error: md5Error, data: md5Data });

        if (!md5Error && md5Data?.fileMd5) {
          targetMd5 = md5Data.fileMd5;
        }
      } catch (md5Err) {
        console.warn('通过API查询MD5失败:', md5Err);
      }
    }

    // 如果获取到了MD5，使用MD5精确下载
    if (targetMd5) {
      console.log('步骤2: 使用MD5下载文件', targetMd5);
      const { error: downloadError, data: downloadData } = await request<Api.Document.DownloadResponse>({
        url: 'documents/download-by-md5',
        params: {
          fileMd5: targetMd5,
          token: authStore.token
        },
        baseURL: '/proxy-api'
      });

      console.log('文件下载结果:', { error: downloadError, data: downloadData });

      window.$message?.destroyAll();

      if (!downloadError && downloadData?.downloadUrl) {
        window.open(downloadData.downloadUrl, '_blank');
        window.$message?.success(`文件下载链接已打开: ${downloadData.fileName || fileName}`);
        return;
      }
    }

    // 降级方案：使用文件名下载（向后兼容）
    console.log('降级方案: 使用文件名下载', fileName);
    const { error, data } = await request<Api.Document.DownloadResponse>({
      url: 'documents/download',
      params: {
        fileName: fileName,
        token: authStore.token
      },
      baseURL: '/proxy-api'
    });

    window.$message?.destroyAll();

    if (error) {
      window.$message?.error(`文件下载失败: ${error.response?.data?.message || '未知错误'}`);
      return;
    }

    if (data?.downloadUrl) {
      window.open(data.downloadUrl, '_blank');
      window.$message?.success(`文件下载链接已打开: ${data.fileName || fileName}`);
    } else {
      window.$message?.error('未能获取到下载链接');
    }
  } catch (err) {
    window.$message?.destroyAll();
    console.error('文件下载失败:', err);
    window.$message?.error(`文件下载失败: ${fileName}`);
  }
}
</script>

<template>
  <div class="message-row" :class="msg.role === 'user' ? 'is-user' : 'is-assistant'">
    <div class="message-meta">
      <div class="meta-identity">
        <NAvatar v-if="msg.role === 'user'" class="user-avatar">
          <SvgIcon icon="ph:user-circle" class="text-icon-large color-white" />
        </NAvatar>
        <NAvatar v-else class="assistant-avatar">
          <SystemLogo class="text-6 text-white" />
        </NAvatar>
        <div class="meta-copy">
          <NText class="meta-name">{{ msg.role === 'user' ? authStore.userInfo.username : $t('system.title') }}</NText>
          <NText class="meta-time">{{ formatDate(msg.timestamp) }}</NText>
        </div>
      </div>
    </div>

    <div class="message-bubble" :class="msg.role === 'user' ? 'user-bubble' : 'assistant-bubble'">
      <NText v-if="msg.status === 'pending'" class="status-text">
        <icon-eos-icons:three-dots-loading class="text-8" />
      </NText>
      <NText v-else-if="msg.status === 'error'" class="status-text italic">服务器繁忙，请稍后再试</NText>
      <div v-else-if="msg.role === 'assistant'" class="assistant-content" @click="handleContentClick">
        <VueMarkdownIt :content="content" />
      </div>
      <div v-else class="user-content">{{ content }}</div>
    </div>

    <div class="message-actions">
      <NButton quaternary circle class="copy-btn" @click="handleCopy(msg.content)">
        <template #icon>
          <icon-mynaui:copy />
        </template>
      </NButton>
    </div>
  </div>
</template>

<style scoped lang="scss">
.message-row {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 22px;
}

.message-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.meta-identity {
  display: flex;
  align-items: center;
  gap: 12px;
}

.meta-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.meta-name {
  font-size: 14px;
  font-weight: 700;
  color: #101828;
}

.meta-time {
  font-size: 12px;
  color: #98a2b3;
}

.assistant-avatar {
  background: linear-gradient(135deg, rgb(var(--primary-color)) 0%, #7c86ff 100%);
  box-shadow: 0 8px 18px rgb(91 108 255 / 30%);
}

.user-avatar {
  background: linear-gradient(135deg, #16a34a 0%, #22c55e 100%);
  box-shadow: 0 8px 18px rgb(34 197 94 / 26%);
}

.message-bubble {
  position: relative;
  max-width: min(88%, 920px);
  padding: 16px 18px;
  border-radius: 24px;
  backdrop-filter: blur(10px);
}

.assistant-bubble {
  border: 1px solid rgb(15 23 42 / 6%);
  background:
    radial-gradient(circle at top right, rgb(91 108 255 / 10%), transparent 26%),
    linear-gradient(180deg, rgb(255 255 255 / 94%), rgb(249 250 251 / 98%));
  box-shadow: 0 18px 40px rgb(15 23 42 / 5%);
}

.user-bubble {
  align-self: flex-end;
  background: linear-gradient(135deg, rgb(var(--primary-color)) 0%, #7c86ff 100%);
  box-shadow: 0 18px 40px rgb(91 108 255 / 18%);
}

.assistant-content,
.user-content,
.status-text {
  font-size: 15px;
  line-height: 1.85;
}

.user-content {
  color: white;
  white-space: pre-wrap;
  word-break: break-word;
}

.status-text {
  color: #667085;
}

.message-actions {
  display: flex;
  align-items: center;
}

.copy-btn {
  opacity: 0.72;
}

.is-user {
  align-items: flex-end;
}

.is-user .message-meta {
  justify-content: flex-end;
}

.is-user .meta-identity {
  flex-direction: row-reverse;
}

.is-user .meta-copy {
  align-items: flex-end;
}

.is-user .message-actions {
  justify-content: flex-end;
}

:deep(.assistant-content p) {
  margin: 0 0 12px;
}

:deep(.assistant-content p:last-child) {
  margin-bottom: 0;
}

:deep(.assistant-content ul),
:deep(.assistant-content ol) {
  margin: 10px 0 12px 1.2em;
}

:deep(.assistant-content li + li) {
  margin-top: 6px;
}

:deep(.assistant-content pre) {
  overflow-x: auto;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgb(15 23 42 / 4%);
}

:deep(.assistant-content code:not(pre code)) {
  padding: 2px 6px;
  border-radius: 8px;
  background: rgb(15 23 42 / 6%);
  font-size: 0.92em;
}

:deep(.source-file-link) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 999px;
  color: rgb(67 56 202);
  cursor: pointer;
  text-decoration: none;
  background: rgb(91 108 255 / 8%);
  transition: color 0.2s, background-color 0.2s;

  &:hover {
    color: #4338ca;
    background: rgb(91 108 255 / 14%);
  }

  &:active {
    color: #3730a3;
  }
}

.dark {
  .meta-name {
    color: #f8fafc;
  }

  .assistant-bubble {
    border-color: rgb(255 255 255 / 8%);
    background:
      radial-gradient(circle at top right, rgb(91 108 255 / 14%), transparent 26%),
      linear-gradient(180deg, rgb(17 24 39 / 78%), rgb(15 23 42 / 90%));
    box-shadow: 0 18px 40px rgb(0 0 0 / 18%);
  }

  .status-text {
    color: #94a3b8;
  }

  :deep(.assistant-content pre) {
    background: rgb(255 255 255 / 5%);
  }

  :deep(.assistant-content code:not(pre code)) {
    background: rgb(255 255 255 / 7%);
  }

  :deep(.source-file-link) {
    color: #c7d2fe;
    background: rgb(91 108 255 / 18%);
  }
}

@media (max-width: 768px) {
  .message-bubble {
    max-width: 100%;
    padding: 14px 16px;
    border-radius: 20px;
  }
}
</style>
