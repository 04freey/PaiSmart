/** Default theme settings */
export const themeSettings: App.Theme.ThemeSetting = {
  themeScheme: 'auto',
  grayscale: false,
  colourWeakness: false,
  recommendColor: true,
  themeColor: '#5b6cff',
  otherColor: { info: '#2080f0', success: '#52c41a', warning: '#faad14', error: '#f5222d' },
  isInfoFollowPrimary: true,
  resetCacheStrategy: 'close',
  layout: { mode: 'vertical', scrollMode: 'content', reverseHorizontalMix: false },
  page: { animate: true, animateMode: 'fade-slide' },
  header: { height: 60, breadcrumb: { visible: false, showIcon: true }, multilingual: { visible: false } },
  tab: { visible: false, cache: true, height: 44, mode: 'chrome' },
  fixedHeaderAndTab: true,
  sider: {
    inverted: false,
    width: 196,
    collapsedWidth: 64,
    mixWidth: 90,
    mixCollapsedWidth: 64,
    mixChildMenuWidth: 200
  },
  footer: { visible: false, fixed: false, height: 48, right: true },
  watermark: { visible: false, text: '大聪明 DaCongMing' },
  tokens: {
    light: {
      colors: {
        container: 'rgb(255, 255, 255)',
        layout: 'rgb(245, 247, 255)',
        inverted: 'rgb(0, 20, 40)',
        'base-text': 'rgb(22, 28, 45)'
      },
      boxShadow: {
        header: '0 10px 30px rgb(91, 108, 255, 0.08)',
        sider: '8px 0 30px 0 rgb(91, 108, 255, 0.05)',
        tab: '0 10px 24px rgb(0, 21, 41, 0.06)'
      }
    },
    dark: { colors: { container: 'rgb(28, 28, 28)', layout: 'rgb(18, 18, 18)', 'base-text': 'rgb(224, 224, 224)' } }
  }
};

/**
 * Override theme settings
 *
 * If publish new version, use `overrideThemeSettings` to override certain theme settings
 */
export const overrideThemeSettings: Partial<App.Theme.ThemeSetting> = {};
