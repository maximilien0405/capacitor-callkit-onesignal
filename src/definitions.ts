import type { PluginListenerHandle } from '@capacitor/core';

export interface CallkitOnesignalPlugin {
  /**
   * Get the VoIP push token (iOS only)
   */
  getToken(): Promise<CallToken>;

  /**
   * Get APNs environment (iOS only): "development" or "production"
   */
  getApnsEnvironment(): Promise<{ value: 'development' | 'production' }>;

  /**
   * Listen for incoming call events (iOS & Android)
   */
  addListener(
    eventName: 'incoming',
    listenerFunc: (callData: CallData) => void
  ): Promise<PluginListenerHandle> & PluginListenerHandle;

  /**
   * Listen for call answered events (iOS & Android)
   */
  addListener(
    eventName: 'callAnswered',
    listenerFunc: (callData: CallData) => void
  ): Promise<PluginListenerHandle> & PluginListenerHandle;

  /**
   * Listen for calls initiated from call history (iOS only)
   * Triggered when user taps a previous call in the Phone app
   */
  addListener(
    eventName: 'callFromHistory',
    listenerFunc: (callData: CallData) => void
  ): Promise<PluginListenerHandle> & PluginListenerHandle;

  /**
   * Listen for call declined events (when user declines an incoming call) (iOS & Android)
   */
  addListener(
    eventName: 'callDeclined',
    listenerFunc: (callData: CallData) => void
  ): Promise<PluginListenerHandle> & PluginListenerHandle;

  /**
   * Listen for call ended events (when user ends an active call) (iOS & Android)
   */
  addListener(
    eventName: 'callEnded',
    listenerFunc: (callData: CallData) => void
  ): Promise<PluginListenerHandle> & PluginListenerHandle;

  /**
   * Check if the app was launched or resumed from a VoIP call.
   * Returns true if the app was launched from a VoIP call, has pending events, or has an ongoing call.
   */
  wasLaunchedFromVoIP(): Promise<{ value: boolean }>;

  /**
   * Re-emits all missed pending events through their event listeners (e.g. incoming calls), as if they just arrived.
   */
  replayPendingEvents(): Promise<void>;

  /**
   * Programmatically show the CallKit VoIP UI (simulate an incoming call).
   * @param options.callerId Unique caller identifier
   * @param options.username Display name of the caller
   * @param options.media 'audio' or 'video'
   */
  startOutgoingCall(options: { callerId: string; username: string; media: CallType }): Promise<void>;

  /**
   * Prepares the audio session for an outgoing call
   */
  prepareAudioSessionForCall(): Promise<void>;

  /**
   * End all current CallKit calls (iOS only). Also deactivates AVAudioSession.
   */
  endCall(): Promise<void>;

  /**
   * Update the UI and CallKit state for an ongoing call (audio <-> video).
   * @param options.uuid The UUID of the call to update
   * @param options.media 'audio' or 'video'
   */
  updateCallUI(options: { uuid: string; media: CallType }): Promise<void>;

  /**
   * Set the audio output route for the current call. (iOS & Android)
   * @param options.route 'speaker' or 'earpiece'
   */
  setAudioOutput(options: { route: AudioOutputRoute }): Promise<void>;

  /**
   * Set the mute state for the current call and update CallKit UI. (iOS only)
   * @param options.isMuted true to mute, false to unmute
   */
  setMuted(options: { isMuted: boolean }): Promise<void>;

  /**
   * Listen for audio route changes (speaker/earpiece/headphones/bluetooth). (iOS & Android)
   */
  addListener(
    eventName: 'audioRouteChanged',
    listenerFunc: (data: AudioRouteData) => void
  ): Promise<PluginListenerHandle> & PluginListenerHandle;

  /**
   * Listen for mute state changes from CallKit UI. (iOS only)
   */
  addListener(
    eventName: 'muteStateChanged',
    listenerFunc: (data: MuteStateData) => void
  ): Promise<PluginListenerHandle> & PluginListenerHandle;

  /**
   * Check if the app is in the foreground (iOS & Android)
   */
  isAppInForeground(): Promise<{ value: boolean }>;

  /**
   * Mark the app as fully loaded and ready to receive events. (iOS & Android)
   * Call this after setting up all event listeners.
   */
  setAppFullyLoaded(): Promise<void>;
}

export type CallType = 'video' | 'audio';

export type AudioOutputRoute = 'speaker' | 'earpiece' | 'bluetooth' | 'headphones';

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
  /** Media type (video/audio) */
  media: CallType;
  /** Unique call UUID */
  uuid?: string;
}

export interface AudioRouteData {
  /** Current audio output route */
  route: AudioOutputRoute;
}

export interface MuteStateData {
  /** Whether the call is currently muted */
  isMuted: boolean;
}