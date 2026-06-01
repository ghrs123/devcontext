import { defineConfig } from 'vite';
import { resolve } from 'node:path';

export default defineConfig({
  build: {
    lib: {
      entry: resolve(__dirname, 'src/main.js'),
      name: 'FitVision',
      formats: ['iife'],
      fileName: () => 'fitvision-widget.min.js'
    },
    target: 'es2017',
    minify: true,
    cssCodeSplit: false,
    rollupOptions: {
      external: [],
      output: {
        manualChunks: undefined
      }
    }
  }
});
