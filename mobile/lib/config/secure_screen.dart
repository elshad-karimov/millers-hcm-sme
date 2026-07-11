import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// M509 — toggles Android FLAG_SECURE to block screenshots / screen recording
/// while a sensitive screen (payslip) is visible.
///
/// iOS: best-effort no-op — there is no equivalent public API to block
/// screenshots, so on iOS these calls silently do nothing.
class SecureScreen {
  SecureScreen._();
  static const _channel = MethodChannel('hcm/secure_screen');

  static Future<void> enable() => _invoke('enable');
  static Future<void> disable() => _invoke('disable');

  static Future<void> _invoke(String method) async {
    if (defaultTargetPlatform != TargetPlatform.android) return; // iOS no-op
    try {
      await _channel.invokeMethod(method);
    } catch (_) {
      // Channel not available (e.g. tests / desktop) — ignore.
    }
  }
}
