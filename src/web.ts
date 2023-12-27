import { WebPlugin } from '@capacitor/core';

import type { CallKitVoipPlugin, CallData } from './definitions';

export class CallKitVoipWeb extends WebPlugin implements CallKitVoipPlugin {
  async register(): Promise<void> {
    console.log('call is register');
    return;
  }

  async show_call_notification(callData: CallData): Promise<void> {
    console.log('call is register', callData);
    return;
  }

  async abortCall(): Promise<void> {
    console.log('call is aborted');
    return;
  }
}
