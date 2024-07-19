// export default {
//   input: 'dist/esm/index.js',
//   output: [
//     {
//       file: 'dist/plugin.js',
//       format: 'iife',
//       name: 'capacitorCallKitVoip',
//       globals: {
//         '@capacitor/core': 'capacitorExports',
//       },
//       sourcemap: true,
//       inlineDynamicImports: true,
//     },
//     {
//       file: 'dist/plugin.cjs.js',
//       format: 'cjs',
//       sourcemap: true,
//       inlineDynamicImports: true,
//     },
//   ],
//   external: ['@capacitor/core'],
// };


// rollup.config.mjs
import { defineConfig } from 'rollup';
import json from '@rollup/plugin-json';

export default defineConfig({
  input: 'dist/esm/index.js',
  output: [
    {
      file: 'dist/plugin.js',
      format: 'iife',
      name: 'capacitorCallKitVoip',
      globals: {
        '@capacitor/core': 'capacitorExports',
      },
      sourcemap: true,
      inlineDynamicImports: true,
    },
    {
      file: 'dist/plugin.cjs.js',
      format: 'cjs',
      sourcemap: true,
      inlineDynamicImports: true,
    },
  ],
  external: ['@capacitor/core'],
  plugins: [json()]
});