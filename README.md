# Capacitor CallKit OneSignal Plugin

A Capacitor plugin that provides CallKit and PushKit functionality for VoIP calls with OneSignal support on iOS and Android.

## Features

- 📱 **CallKit Integration** - Native iOS call interface
- 🔔 **PushKit VoIP** - Background VoIP push notifications
- 📲 **OneSignal Support** - Seamless integration with OneSignal
- 🎯 **Cross-Platform** - Works on both iOS and Android
- 🔒 **Secure** - Proper permission handling and security

## Installation

```bash
npm install @maximilien0405/capacitor-callkit-onesignal
```

### iOS Setup

1. Add the plugin to your `capacitor.config.ts`:

```typescript
import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.example.app',
  appName: 'My App',
  webDir: 'dist',
  plugins: {
    CallkitOnesignal: {
      // Plugin configuration
    }
  }
};
```

2. Add required permissions to your `ios/App/App/Info.plist`:

```xml
<key>UIBackgroundModes</key>
<array>
    <string>voip</string>
    <string>background-processing</string>
</array>
```

3. Enable CallKit capability in Xcode:
   - Open your project in Xcode
   - Select your app target
   - Go to "Signing & Capabilities"
   - Add "Background Modes" capability
   - Enable "Voice over IP"

### Android Setup

1. **Permissions are automatically added** - The plugin includes all required permissions in its manifest, which Capacitor automatically merges into your app.

2. Add OneSignal configuration to your `android/app/src/main/AndroidManifest.xml`:

```xml
<service
    android:name="com.maximilien0405.callkitonesignal.OneSignalVoipExtenderService"
    android:exported="false"
    android:permission="android.permission.BIND_JOB_SERVICE" />
```

## Usage

### Basic Setup

```typescript
import { CallkitOnesignal } from '@maximilien0405/capacitor-callkit-onesignal';

// Register for VoIP notifications
await CallkitOnesignal.register();

// Listen for incoming calls
CallkitOnesignal.addListener('incoming', (callData) => {
  console.log('Incoming call from:', callData.username);
  console.log('Call type:', callData.media);
  console.log('Call UUID:', callData.uuid);
});

// Listen for call events
CallkitOnesignal.addListener('callAnswered', (callData) => {
  console.log('Call answered:', callData.uuid);
});

CallkitOnesignal.addListener('callEnded', (callData) => {
  console.log('Call ended:', callData.uuid);
});

// Listen for VoIP token registration
CallkitOnesignal.addListener('registration', (token) => {
  console.log('VoIP token:', token.value);
  // Send this token to your server for push notifications
});
```

### API Reference

#### Methods

##### `register()`
Register for VoIP notifications and start listening for incoming calls.

```typescript
await CallkitOnesignal.register();
```

##### `abortCall(options)`
Abort an ongoing call.

```typescript
await CallkitOnesignal.abortCall({ uuid: 'call-uuid-here' });
```

##### `getApnsEnvironment()`
Get the current APNs environment (debug or production).

```typescript
const { environment } = await CallkitOnesignal.getApnsEnvironment();
console.log('APNs environment:', environment); // 'debug' or 'production'
```

#### Events

##### `incoming`
Fired when an incoming call is received.

```typescript
CallkitOnesignal.addListener('incoming', (callData: CallData) => {
  // Handle incoming call
});
```

##### `registration`
Fired when VoIP token is registered.

```typescript
CallkitOnesignal.addListener('registration', (token: CallToken) => {
  // Handle token registration
});
```

##### `callAnswered`
Fired when a call is answered.

```typescript
CallkitOnesignal.addListener('callAnswered', (callData: CallData) => {
  // Handle call answered
});
```

##### `callStarted`
Fired when a call is started.

```typescript
CallkitOnesignal.addListener('callStarted', (callData: CallData) => {
  // Handle call started
});
```

##### `callEnded`
Fired when a call is ended.

```typescript
CallkitOnesignal.addListener('callEnded', (callData: CallData) => {
  // Handle call ended
});
```

#### Types

```typescript
interface CallData {
  connectionId: string;    // Unique connection identifier
  username: string;        // Display name of the caller
  callerId: string;        // Caller identifier
  group: string;           // Group or team identifier
  message: string;         // Call message or description
  organization: string;    // Organization name
  roomname: string;        // Room or meeting identifier
  source: string;          // Source of the call
  title: string;           // Call title
  type: string;            // Call type
  duration: string;        // Call duration in seconds
  media: 'video' | 'audio'; // Media type
  uuid?: string;           // Unique call UUID
}

interface CallToken {
  value: string;           // VoIP Push Token
}
```

## OneSignal Integration

### iOS

1. Configure OneSignal with VoIP capability
2. Send VoIP push notifications with the following payload structure:

```json
{
  "aps": {
    "content-available": 1
  },
  "callerId": "user123",
  "Username": "John Doe",
  "group": "team1",
  "message": "Incoming video call",
  "organization": "My Company",
  "roomname": "meeting-room-1",
  "source": "mobile",
  "title": "Incoming Call",
  "type": "video",
  "duration": "60",
  "media": "video"
}
```

### Android

1. Configure OneSignal with the provided `OneSignalVoipExtenderService`
2. Send data-only push notifications with the same payload structure
3. The service will automatically handle the call UI

## Troubleshooting

### Common Issues

1. **VoIP token not received**
   - Ensure proper iOS capabilities are enabled
   - Check that the app is properly signed
   - Verify OneSignal configuration

2. **Calls not showing on Android**
   - Check notification permissions
   - Verify OneSignal extender service is configured
   - Ensure proper Android permissions

3. **CallKit not working on iOS**
   - Verify CallKit capability is enabled
   - Check that the app is running on a physical device (not simulator)
   - Ensure proper iOS version (iOS 10+)

### Debug Mode

Enable debug logging by checking the APNs environment:

```typescript
const { environment } = await CallkitOnesignal.getApnsEnvironment();
console.log('Running in:', environment, 'mode');
```

## License

MIT

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Support

For support, please open an issue on GitHub or contact the maintainer.