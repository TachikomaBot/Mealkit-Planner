import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

// Set to true to enable PWA caching (disable during development for faster iteration)
const ENABLE_PWA = false;

export default defineConfig({
  server: {
    host: true, // Allow access from network (for mobile testing)
    proxy: {
      '/api/anthropic': {
        target: 'https://api.anthropic.com',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/anthropic/, ''),
        headers: {
          'anthropic-dangerous-direct-browser-access': 'true',
        },
      },
    },
  },
  plugins: [
    react(),
    ENABLE_PWA && VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['vite.svg', 'apple-touch-icon.png'],
      manifest: {
        name: 'Meal Planner',
        short_name: 'Meals',
        description: 'Smart meal planning with pantry management',
        theme_color: '#fe4a49',
        background_color: '#f4f4f8',
        display: 'standalone',
        orientation: 'portrait',
        scope: '/',
        start_url: '/',
        icons: [
          {
            src: 'pwa-192x192.png',
            sizes: '192x192',
            type: 'image/png'
          },
          {
            src: 'pwa-512x512.png',
            sizes: '512x512',
            type: 'image/png'
          },
          {
            src: 'pwa-512x512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'any maskable'
          }
        ]
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,ico,png,svg,jpg,jpeg}'],
        // Skip waiting and claim clients immediately for faster updates
        skipWaiting: true,
        clientsClaim: true,
        // Don't cache API calls
        navigateFallbackDenylist: [/^\/api/],
        // Allow larger images (up to 5MB)
        maximumFileSizeToCacheInBytes: 5 * 1024 * 1024,
      }
    }),
  ].filter(Boolean),
})
