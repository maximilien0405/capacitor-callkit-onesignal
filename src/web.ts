import { WebPlugin, PluginListenerHandle } from '@capacitor/core';

import type { CallkitOnesignalPlugin } from './definitions';

export class CallkitOnesignalWeb extends WebPlugin implements CallkitOnesignalPlugin {
  async register(): Promise<void> {
    return;
  }

  async abortCall(_options: { uuid: string }): Promise<void> {
    return;
  }

  async getApnsEnvironment(): Promise<{ environment: 'debug' | 'production' }> {
    return { environment: 'debug' };
  }

  addListener(_eventName: string, _callback: (data: any) => void): Promise<PluginListenerHandle> & PluginListenerHandle {
    const handle: PluginListenerHandle = {
      remove: async () => {
        return;
      },
    };

    const promise = new Promise<PluginListenerHandle>((resolve) => {
      setTimeout(() => {
        resolve(handle);
      }, 0);
    });

    return Object.assign(promise, handle);
  }

  async removeAllListeners(): Promise<void> {
    return;
  }
}
