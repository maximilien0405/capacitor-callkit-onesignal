import type { PluginListenerHandle } from '@capacitor/core';

export interface CallkitOnesignalPlugin {
  /**
   * Register for VoIP notifications and start listening for incoming calls
   */
  register(): Promise<void>;

  /**
   * Abort an ongoing call
   * @param options.uuid The UUID of the call to abort
   */
  abortCall(options: { uuid: string }): Promise<void>;

  /**
   * Get the current APNs environment (debug or production)
   */
  getApnsEnvironment(): Promise<{ environment: 'debug' | 'production' }>;

  /**
   * Listen for incoming call events
   */
  addListener(
    eventName: 'incoming',
    listenerFunc: (callData: CallData) => void
  ): Promise<PluginListenerHandle> & PluginListenerHandle;

  /**
   * Listen for VoIP token registration events
   */
  addListener(
    eventName: 'registration',
    listenerFunc: (token: CallToken) => void
  ): Promise<PluginListenerHandle> & PluginListenerHandle;

  /**
   * Listen for call answered events
   */
  addListener(
    eventName: 'callAnswered',
    listenerFunc: (callData: CallData) => void
  ): Promise<PluginListenerHandle> & PluginListenerHandle;

  /**
   * Listen for call started events
   */
  addListener(
    eventName: 'callStarted',
    listenerFunc: (callData: CallData) => void
  ): Promise<PluginListenerHandle> & PluginListenerHandle;

  /**
   * Listen for call ended events
   */
  addListener(
    eventName: 'callEnded',
    listenerFunc: (callData: CallData) => void
  ): Promise<PluginListenerHandle> & PluginListenerHandle;

  /**
   * Remove all listeners for this plugin
   */
  removeAllListeners(): Promise<void>;
}

export type CallType = 'video' | 'audio';

export interface CallToken {
  /**
   * VoIP Push Token for receiving incoming calls
   */
  value: string;
}

export interface CallData {
  /** Unique connection identifier */
  connectionId: string;
  /** Display name of the caller */
  username: string;
  /** Caller identifier */
  callerId: string;
  /** Group or team identifier */
  group: string;
  /** Call message or description */
  message: string;
  /** Organization name */
  organization: string;
  /** Room or meeting identifier */
  roomname: string;
  /** Source of the call */
  source: string;
  /** Call title */
  title: string;
  /** Call type */
  type: string;
  /** Call duration in seconds */
  duration: string;
  /** Media type (video/audio) */
  media: CallType;
  /** Unique call UUID */
  uuid?: string;
}