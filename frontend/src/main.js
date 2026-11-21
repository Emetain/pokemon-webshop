import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { prefetchPages } from './utils/prefetch'
import '@fontsource-variable/bricolage-grotesque/standard.css'
import '@fontsource-variable/schibsted-grotesk/wght.css'
import './assets/css/main.css'

// Safari on iPhone only shows :active, the pressed state of buttons and links, once the page listens for touches.
document.addEventListener('touchstart', () => {}, { passive: true })

createApp(App).use(router).mount('#app')
prefetchPages(router)
