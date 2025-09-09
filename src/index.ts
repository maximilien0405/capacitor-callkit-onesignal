import { registerPlugin } from '@capacitor/core';

import type { CallkitOnesignalPlugin } from './definitions';

const CallkitOnesignal = registerPlugin<CallkitOnesignalPlugin>('CallkitOnesignal', {
  web: () => import('./web').then(m => new m.CallkitOnesignalWeb()),
});

export * from './definitions';
export { CallkitOnesignal };
