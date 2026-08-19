import { createApp } from 'vue'
import './fonts.css'
import App from './App.vue'
import { router } from './router'
import './styles.css'
import './recipe-selector.css'

createApp(App).use(router).mount('#app')
