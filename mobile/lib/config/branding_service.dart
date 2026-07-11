import 'package:flutter/material.dart';

import '../api/self_api.dart';
import 'app_config.dart';

/// M505b — holds the mobile branding (app name + primary color) fetched from
/// GET /api/config/mobile-branding, applied to the MaterialApp theme.
///
/// Falls back to the built-in Millers theme when the call fails or returns an
/// unparseable colour, so the app always has a valid theme.
class BrandingService {
  BrandingService._();
  static final BrandingService instance = BrandingService._();

  static const Color _fallbackColor = Color(AppConfig.brandColorValue);
  static const String _fallbackName = 'Millers HCM';

  /// Reactive so [MaterialApp] rebuilds when branding resolves after login.
  final ValueNotifier<BrandingState> state = ValueNotifier(
    const BrandingState(color: _fallbackColor, appName: _fallbackName),
  );

  Color get color => state.value.color;
  String get appName => state.value.appName;

  /// Fetches branding and updates [state]. Never throws — best-effort.
  Future<void> load() async {
    try {
      final b = await SelfApi.instance.getMobileBranding();
      state.value = BrandingState(
        color: b.primaryColorValue ?? _fallbackColor,
        appName: b.appName.isEmpty ? _fallbackName : b.appName,
      );
    } catch (_) {
      // Keep the fallback theme.
    }
  }
}

class BrandingState {
  final Color color;
  final String appName;
  const BrandingState({required this.color, required this.appName});
}
