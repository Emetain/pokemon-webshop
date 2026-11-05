import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import '@fontsource-variable/bricolage-grotesque/standard.css'
import '@fontsource-variable/schibsted-grotesk/wght.css'
import './assets/css/main.css'

createApp(App).use(router).mount('#app')
