import { createApp } from 'vue'
import { createPinia } from 'pinia'

import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import './styles/index.scss'

/**
 * 应用入口。
 *
 * Element Plus 采用全量引入 + 全量图标注册：
 * 好处是省掉 unplugin-auto-import / unplugin-vue-components 的版本适配成本，
 * 对内部管理系统的体积影响可以接受（已通过 vite 的 manualChunks 单独拆包）。
 * 若后续需要极致体积，再换成按需引入即可。
 */
const app = createApp(App)

// 注册全部 Element Plus 图标，模板里直接写 <el-icon><Van /></el-icon>
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn, size: 'default' })

app.mount('#app')
