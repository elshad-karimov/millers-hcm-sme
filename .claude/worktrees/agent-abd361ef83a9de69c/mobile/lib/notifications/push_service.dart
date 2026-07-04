import 'package:firebase_messaging/firebase_messaging.dart';
import '../api/api_client.dart';

/// FCM push notification service — PRD §17.5 / M154.
///
/// Call [init] once after Firebase is initialised (in main.dart).
/// After a successful login, call [registerToken] to send the FCM token to the
/// HCM backend so the server can push notifications to this device.
class PushService {
  PushService._();
  static final PushService instance = PushService._();

  final _messaging = FirebaseMessaging.instance;

  /// Request permission and register foreground/background handlers.
  ///
  /// Must be called after [Firebase.initializeApp()].
  Future<void> init() async {
    // Request notification permission (iOS prompts the user; Android 13+).
    await _messaging.requestPermission(
      alert: true,
      badge: true,
      sound: true,
    );

    // Foreground messages — show an in-app banner.
    FirebaseMessaging.onMessage.listen(_handleForeground);

    // Background tap — navigate when the user taps a notification that
    // opened the app from background.
    FirebaseMessaging.onMessageOpenedApp.listen(_handleTap);
  }

  /// Register (or refresh) the FCM token with the HCM backend.
  ///
  /// Call this once after a successful login so the server can push
  /// workflow approvals, leave status changes, and other alerts to the
  /// device. The backend upserts the token (idempotent).
  Future<void> registerToken() async {
    try {
      final token = await _messaging.getToken();
      if (token == null) return;
      await ApiClient.instance.dio.post('/notifications/device-token', data: {
        'fcmToken': token,
        'platform': 'FLUTTER',
      });

      // Refresh handler — backend re-registers automatically.
      _messaging.onTokenRefresh.listen((newToken) {
        ApiClient.instance.dio.post('/notifications/device-token', data: {
          'fcmToken': newToken,
          'platform': 'FLUTTER',
        }).catchError((_) {});
      });
    } catch (_) {
      // Non-critical — if FCM registration fails, in-app notifications still work.
    }
  }

  /// Deregister the token on logout so push stops to this device.
  Future<void> deregisterToken() async {
    try {
      final token = await _messaging.getToken();
      if (token == null) return;
      await ApiClient.instance.dio
          .delete('/notifications/device-token', data: {'fcmToken': token});
      await _messaging.deleteToken();
    } catch (_) {}
  }

  void _handleForeground(RemoteMessage message) {
    // TODO: surface as an in-app snackbar using a global key or
    // an overlay. For now the notification lands in the device tray.
  }

  void _handleTap(RemoteMessage message) {
    // TODO: parse message.data['screen'] and navigate accordingly.
  }
}
