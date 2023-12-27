import type { PluginListenerHandle } from '@capacitor/core';

export interface CallKitVoipPlugin {
  register(): Promise<void>;

  show_call_notification(callData: CallData): Promise<void>;

  abortCall(): Promise<void>;

  addListener(
      eventName: 'registration',
      listenerFunc: (token:CallToken)   => void
  ): Promise<PluginListenerHandle> & PluginListenerHandle;

  addListener(
      eventName: 'callAnswered',
      listenerFunc: (callData: CallData)  => void
  ): Promise<PluginListenerHandle> & PluginListenerHandle;

  addListener(
      eventName: 'callStarted',
      listenerFunc: (callData: CallData) => void
  ): Promise<PluginListenerHandle> & PluginListenerHandle;

  addListener(
      eventName: 'callEnded',
      listenerFunc: (callData: CallData) => void
  ): Promise<PluginListenerHandle> & PluginListenerHandle;
}




export type CallType = 'video' | 'audio';

export interface CallToken {
  /**
   * VOIP Token
   */
  value: string;
}

export interface CallData {
  connectionId  :  string
  username      :  string
  callerId      :  string
  group         :  string
  message       :  string
  organization  :  string
  roomname      :  string
  source        :  string
  title         :  string
  type          :  string
  duration      :  string
}