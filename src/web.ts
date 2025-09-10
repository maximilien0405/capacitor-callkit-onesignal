import { WebPlugin, PluginListenerHandle } from '@capacitor/core';

import type { CallkitOnesignalPlugin, CallToken } from './definitions';

export class CallkitOnesignalWeb extends WebPlugin implements CallkitOnesignalPlugin {
  async getToken(): Promise<CallToken> {
    throw new Error('getToken() is only available on iOS');
  }

  async abortCall(_options: { uuid: string }): Promise<void> {
    return;
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
}
