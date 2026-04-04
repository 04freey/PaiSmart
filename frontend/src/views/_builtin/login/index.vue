<script setup lang="ts">
import { computed } from 'vue';
import type { Component } from 'vue';
import { mixColor } from '@sa/color';
import { loginModuleRecord } from '@/constants/app';
import { useAppStore } from '@/store/modules/app';
import { useThemeStore } from '@/store/modules/theme';
import { $t } from '@/locales';
import PwdLogin from './modules/pwd-login.vue';
import CodeLogin from './modules/code-login.vue';
import Register from './modules/register.vue';
import ResetPwd from './modules/reset-pwd.vue';
import BindWechat from './modules/bind-wechat.vue';

interface Props {
  /** The login module */
  module?: UnionKey.LoginModule;
}

const props = defineProps<Props>();

const appStore = useAppStore();
const themeStore = useThemeStore();

interface LoginModule {
  label: string;
  component: Component;
}

const moduleMap: Record<UnionKey.LoginModule, LoginModule> = {
  'pwd-login': { label: loginModuleRecord['pwd-login'], component: PwdLogin },
  'code-login': { label: loginModuleRecord['code-login'], component: CodeLogin },
  register: { label: loginModuleRecord.register, component: Register },
  'reset-pwd': { label: loginModuleRecord['reset-pwd'], component: ResetPwd },
  'bind-wechat': { label: loginModuleRecord['bind-wechat'], component: BindWechat }
};

const activeModule = computed(() => moduleMap[props.module || 'pwd-login']);

const bgColor = computed(() => {
  const ratio = themeStore.darkMode ? 0.9 : 0;
  return mixColor('#fff', '#000', ratio);
});

const heroHighlights = computed(() => [
  '上传文档后自动解析、切片与向量化',
  '知识库、聊天、权限控制放在同一工作台',
  '更清爽的界面，更直接的 AI 使用体验'
]);
</script>

<template>
  <div class="login-page" :style="{ backgroundColor: bgColor }">
    <div class="login-shell">
      <section class="hero-panel">
        <div class="hero-badge">
          <SystemLogo class="text-28px text-white" />
          <span>AI Knowledge Workspace</span>
        </div>
        <div class="hero-copy">
          <p class="hero-kicker">全新的品牌名，同一套能力内核</p>
          <h1>{{ $t('system.title') }}</h1>
          <p class="hero-desc">
            把知识库、问答、上传解析和权限协同收进一个更清爽的界面里。少一点堆砌，多一点真正可用的工作流。
          </p>
        </div>
        <ul class="hero-points">
          <li v-for="item in heroHighlights" :key="item">
            <span class="point-dot"></span>
            <span>{{ item }}</span>
          </li>
        </ul>
      </section>

      <section class="login-panel">
        <header class="login-panel-header">
          <div>
            <div class="brand-row">
              <SystemLogo class="text-48px text-primary lt-sm:text-40px" />
              <h3 class="brand-title">{{ $t('system.title') }}</h3>
            </div>
            <p class="brand-subtitle">欢迎回来，继续把你的知识交给大聪明。</p>
          </div>
          <div class="panel-actions">
            <ThemeSchemaSwitch
              :theme-schema="themeStore.themeScheme"
              :show-tooltip="false"
              class="text-20px lt-sm:text-18px"
              @switch="themeStore.toggleThemeScheme"
            />
            <LangSwitch
              v-if="themeStore.header.multilingual.visible"
              :lang="appStore.locale"
              :lang-options="appStore.localeOptions"
              :show-tooltip="false"
              @change-lang="appStore.changeLocale"
            />
          </div>
        </header>
        <main class="login-panel-body">
          <div class="module-head">
            <span class="module-kicker">快速进入工作台</span>
            <h3 class="module-title">{{ $t(activeModule.label) }}</h3>
          </div>
          <Transition :name="themeStore.page.animateMode" mode="out-in" appear>
            <component :is="activeModule.component" />
          </Transition>
        </main>
      </section>
    </div>
    <div class="ambient ambient-left"></div>
    <div class="ambient ambient-right"></div>
    <div class="ambient ambient-bottom"></div>
  </div>
</template>

<style scoped lang="scss">
.login-page {
  position: relative;
  display: flex;
  min-height: 100%;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: 32px;
}

.login-shell {
  position: relative;
  z-index: 2;
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(380px, 440px);
  width: min(1180px, 100%);
  overflow: hidden;
  border: 1px solid rgb(255 255 255 / 14%);
  border-radius: 32px;
  background: rgb(255 255 255 / 76%);
  backdrop-filter: blur(24px);
  box-shadow: 0 30px 80px rgb(15 23 42 / 12%);
}

.hero-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 720px;
  padding: 48px;
  color: white;
  background:
    radial-gradient(circle at 20% 20%, rgb(255 255 255 / 18%), transparent 28%),
    radial-gradient(circle at 80% 10%, rgb(255 255 255 / 12%), transparent 22%),
    linear-gradient(135deg, #3d4bff 0%, #6573ff 45%, #111827 100%);
}

.hero-badge {
  display: inline-flex;
  width: fit-content;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border: 1px solid rgb(255 255 255 / 18%);
  border-radius: 999px;
  background: rgb(255 255 255 / 10%);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-copy h1 {
  margin: 12px 0 0;
  font-size: clamp(52px, 8vw, 86px);
  line-height: 0.95;
  letter-spacing: -0.04em;
}

.hero-kicker {
  margin: 0;
  font-size: 13px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: rgb(255 255 255 / 72%);
}

.hero-desc {
  max-width: 520px;
  margin: 20px 0 0;
  font-size: 16px;
  line-height: 1.9;
  color: rgb(255 255 255 / 84%);
}

.hero-points {
  display: grid;
  gap: 14px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.hero-points li {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 15px;
  color: rgb(255 255 255 / 88%);
}

.point-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: white;
  box-shadow: 0 0 0 6px rgb(255 255 255 / 12%);
}

.login-panel {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 34px 34px 36px;
}

.login-panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.brand-row {
  display: flex;
  align-items: center;
  gap: 14px;
}

.brand-title {
  margin: 0;
  font-size: 34px;
  line-height: 1;
  color: #101828;
}

.brand-subtitle {
  margin: 12px 0 0;
  font-size: 14px;
  line-height: 1.8;
  color: #667085;
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.login-panel-body {
  margin-top: 36px;
}

.module-head {
  margin-bottom: 24px;
}

.module-kicker {
  display: inline-block;
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: rgb(var(--primary-color));
  text-transform: uppercase;
}

.module-title {
  margin: 0;
  font-size: 24px;
  color: #101828;
}

.ambient {
  position: absolute;
  border-radius: 999px;
  filter: blur(100px);
  opacity: 0.5;
}

.ambient-left {
  top: 10%;
  left: -8%;
  width: 260px;
  height: 260px;
  background: rgb(91 108 255 / 28%);
}

.ambient-right {
  top: 12%;
  right: -6%;
  width: 320px;
  height: 320px;
  background: rgb(14 165 233 / 20%);
}

.ambient-bottom {
  bottom: -14%;
  left: 30%;
  width: 340px;
  height: 340px;
  background: rgb(59 130 246 / 18%);
}

.dark {
  .login-shell {
    border-color: rgb(255 255 255 / 10%);
    background: rgb(17 24 39 / 72%);
    box-shadow: 0 30px 90px rgb(0 0 0 / 28%);
  }

  .login-panel {
    background: linear-gradient(180deg, rgb(17 24 39 / 82%), rgb(15 23 42 / 88%));
  }

  .brand-title,
  .module-title {
    color: #f8fafc;
  }

  .brand-subtitle {
    color: #94a3b8;
  }
}

@media (max-width: 960px) {
  .login-page {
    padding: 18px;
  }

  .login-shell {
    grid-template-columns: 1fr;
  }

  .hero-panel {
    min-height: auto;
    padding: 28px 24px;
    gap: 32px;
  }

  .login-panel {
    padding: 28px 20px 24px;
  }
}

@media (max-width: 640px) {
  .hero-copy h1 {
    font-size: 48px;
  }

  .brand-title {
    font-size: 28px;
  }
}
</style>
