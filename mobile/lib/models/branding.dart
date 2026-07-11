import 'package:flutter/material.dart';

/// M505b — Mobile branding from GET /api/config/mobile-branding.
class MobileBranding {
  final String? logoUrl;
  final String primaryColor; // hex string like "#1976D2"
  final String appName;

  const MobileBranding({
    this.logoUrl,
    required this.primaryColor,
    required this.appName,
  });

  factory MobileBranding.fromJson(Map<String, dynamic> j) => MobileBranding(
        logoUrl: j['logoUrl'] as String?,
        primaryColor: j['primaryColor'] as String? ?? '',
        appName: j['appName'] as String? ?? 'Millers HCM',
      );

  /// Parses the `#RRGGBB` / `#AARRGGBB` hex color, returning null when invalid
  /// so callers can fall back to the built-in Millers theme.
  Color? get primaryColorValue {
    var hex = primaryColor.trim();
    if (hex.isEmpty) return null;
    if (hex.startsWith('#')) hex = hex.substring(1);
    if (hex.length == 6) hex = 'FF$hex';
    if (hex.length != 8) return null;
    final v = int.tryParse(hex, radix: 16);
    return v == null ? null : Color(v);
  }
}
