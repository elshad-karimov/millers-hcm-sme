import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// M506 — persisted app settings: UI language + local notification preference.
///
/// Language is exposed as a [ValueNotifier] so [MaterialApp] can rebuild its
/// `locale` reactively when the user flips the toggle in Settings. Values are
/// stored in the same secure storage the auth tokens use.
class SettingsService {
  SettingsService._();
  static final SettingsService instance = SettingsService._();

  static const _keyLanguage = 'hcm_language';
  static const _keyNotifications = 'hcm_notifications_enabled';

  static const supportedLanguages = ['en', 'az'];

  final _storage = const FlutterSecureStorage();

  /// The active UI locale. Defaults to English until [load] resolves a stored
  /// preference. Wrap [MaterialApp] in a [ValueListenableBuilder] on this.
  final ValueNotifier<Locale> locale = ValueNotifier(const Locale('en'));

  bool _notificationsEnabled = true;
  bool get notificationsEnabled => _notificationsEnabled;

  String get languageCode => locale.value.languageCode;

  /// Loads persisted settings. Call once during app startup.
  Future<void> load() async {
    final lang = await _storage.read(key: _keyLanguage);
    if (lang != null && supportedLanguages.contains(lang)) {
      locale.value = Locale(lang);
    }
    final notif = await _storage.read(key: _keyNotifications);
    _notificationsEnabled = notif != 'false'; // default ON
  }

  Future<void> setLanguage(String code) async {
    if (!supportedLanguages.contains(code)) return;
    locale.value = Locale(code);
    await _storage.write(key: _keyLanguage, value: code);
  }

  Future<void> setNotificationsEnabled(bool enabled) async {
    _notificationsEnabled = enabled;
    await _storage.write(
        key: _keyNotifications, value: enabled ? 'true' : 'false');
  }
}
