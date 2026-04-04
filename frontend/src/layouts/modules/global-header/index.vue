<script setup lang="ts">
import { useFullscreen } from '@vueuse/core';
import { useAppStore } from '@/store/modules/app';
import { useThemeStore } from '@/store/modules/theme';
import { $t } from '@/locales';
import GlobalSearch from '../global-search/index.vue';
import ThemeButton from './components/theme-button.vue';
import UserAvatar from './components/user-avatar.vue';

defineOptions({
  name: 'GlobalHeader'
});

interface Props {
  /** Whether to show the logo */
  // showLogo?: App.Global.HeaderProps['showLogo'];
  /** Whether to show the menu toggler */
  showMenuToggler?: App.Global.HeaderProps['showMenuToggler'];
  /** Whether to show the menu */
  // showMenu?: App.Global.HeaderProps['showMenu'];
}

defineProps<Props>();

const appStore = useAppStore();
const themeStore = useThemeStore();
const { isFullscreen, toggle } = useFullscreen();

const isDev = import.meta.env.DEV;
</script>

<template>
  <DarkModeContainer class="ml-12 h-full flex-y-center justify-between gap-12px bg-transparent">
    <div class="flex-y-center gap-12px">
      <RouterLink to="/" class="brand-chip">
        <SystemLogo class="text-32px shrink-0" />
        <div class="brand-copy lt-sm:hidden">
          <strong>{{ $t('system.title') }}</strong>
          <span>AI 工作台</span>
        </div>
      </RouterLink>
      <div id="header-extra" class="header-extra-panel h-full flex-col justify-center rd-full"></div>
    </div>
    <!-- <GlobalLogo v-if="showLogo" class="h-full" :style="{ width: themeStore.sider.width + 'px' }" /> -->
    <MenuToggler
      v-if="showMenuToggler && appStore.isMobile"
      :collapsed="appStore.siderCollapse"
      @click="appStore.toggleSiderCollapse"
    />
    <!--
    <div v-if="showMenu" :id="GLOBAL_HEADER_MENU_ID" class="h-full flex-y-center flex-1-hidden"></div>
    <div v-else class="h-full flex-y-center flex-1-hidden">
      <GlobalBreadcrumb v-if="!appStore.isMobile" class="ml-12px" />
    </div>
-->
    <div class="header-actions h-full flex-y-center justify-end rd-full px-6">
      <GlobalSearch />
      <FullScreen v-if="!appStore.isMobile" :full="isFullscreen" @click="toggle" />
      <LangSwitch
        v-if="themeStore.header.multilingual.visible"
        :lang="appStore.locale"
        :lang-options="appStore.localeOptions"
        @change-lang="appStore.changeLocale"
      />
      <ThemeSchemaSwitch
        :theme-schema="themeStore.themeScheme"
        :is-dark="themeStore.darkMode"
        @switch="themeStore.toggleThemeScheme"
      />
      <ThemeButton v-if="isDev" />
      <UserAvatar />
    </div>
  </DarkModeContainer>
</template>

<style scoped lang="scss">
.brand-chip {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 4px 14px 4px 6px;
  border: 1px solid rgb(15 23 42 / 5%);
  border-radius: 999px;
  background: rgb(255 255 255 / 38%);
  backdrop-filter: blur(18px);
  box-shadow: 0 8px 24px rgb(15 23 42 / 5%);
}

.brand-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.brand-copy strong {
  font-size: 15px;
  line-height: 1.1;
  color: #101828;
}

.brand-copy span {
  font-size: 11px;
  color: #667085;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.header-extra-panel {
  min-width: 160px;
  padding: 0 6px;
  border: 1px solid rgb(15 23 42 / 5%);
  background: rgb(255 255 255 / 32%);
  backdrop-filter: blur(18px);
  box-shadow: 0 8px 24px rgb(15 23 42 / 5%);
}

.header-actions {
  gap: 2px;
  border: 1px solid rgb(15 23 42 / 5%);
  background: rgb(255 255 255 / 32%);
  backdrop-filter: blur(18px);
  box-shadow: 0 8px 24px rgb(15 23 42 / 5%);
}

.header-actions :deep(.n-button),
.header-actions :deep(.n-base-selection),
.header-actions :deep(.n-input) {
  background: transparent !important;
  box-shadow: none !important;
}

.dark {
  .brand-chip,
  .header-extra-panel,
  .header-actions {
    border-color: rgb(255 255 255 / 8%);
    background: rgb(17 24 39 / 30%);
  }

  .brand-copy strong {
    color: #f8fafc;
  }

  .brand-copy span {
    color: #94a3b8;
  }
}

@media (max-width: 768px) {
  .header-extra-panel {
    min-width: 0;
    max-width: 104px;
  }
}
</style>
