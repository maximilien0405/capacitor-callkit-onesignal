import { WebPlugin, PluginListenerHandle } from '@capacitor/core';

import type { CallkitOnesignalPlugin, CallToken } from './definitions';

export class CallkitOnesignalWeb extends WebPlugin implements CallkitOnesignalPlugin {
  async getToken(): Promise<CallToken> {
    throw new Error('getToken() is only available on iOS');
  }

  async getApnsEnvironment(): Promise<{ value: 'development' | 'production' }> {
    throw new Error('getApnsEnvironment() is only available on iOS');
  }

  async replayPendingEvents(): Promise<void> {
    throw new Error('replayPendingEvents() is not available on web platform');
  }

  async wasLaunchedFromVoIP(): Promise<{ value: boolean }> {
    throw new Error('wasLaunchedFromVoIP() is not available on web platform');
  }

  async startOutgoingCall(_options: { callerId: string; username: string; media: any }): Promise<void> {
    throw new Error('startOutgoingCall() is not available on web platform');
  }

  async prepareAudioSessionForCall(): Promise<void> {
    throw new Error('prepareAudioSessionForCall() is not available on web platform');
  }

  async endCall(): Promise<void> {
    throw new Error('endCall() is not available on web platform');
  }

  async updateCallUI(_options: { uuid: string; media: any }): Promise<void> {
    throw new Error('updateCallUI() is not available on web platform');
  }

  async setAudioOutput(_options: { route: string }): Promise<void> {
    throw new Error('setAudioOutput() is not available on web platform');
  }

  async setMuted(_options: { isMuted: boolean }): Promise<void> {
    throw new Error('setMuted() is only available on iOS');
  }

  async isAppInForeground(): Promise<{ value: boolean }> {
    throw new Error('isAppInForeground() is not available on web platform');
  }


  async setAppFullyLoaded(): Promise<void> {
    throw new Error('setAppFullyLoaded() is not available on web platform');
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
