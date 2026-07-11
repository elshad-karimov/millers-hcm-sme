import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

/// M507 — lightweight offline read cache backed by shared_preferences.
///
/// Confidentiality: this cache is PLAINTEXT and is used ONLY for non-sensitive
/// data (profile, leave balances, announcements, policies). Salary / payslip
/// data is never written here — see [SelfApi] which has no payslip cache.
class OfflineCache {
  OfflineCache._();
  static final OfflineCache instance = OfflineCache._();

  static const _prefix = 'hcm_cache_';

  Future<void> saveJson(String key, Object data) async {
    final prefs = await SharedPreferences.getInstance();
    final envelope = jsonEncode({
      'cachedAt': DateTime.now().toIso8601String(),
      'data': data,
    });
    await prefs.setString('$_prefix$key', envelope);
  }

  /// Returns the cached envelope, or null when nothing is cached.
  Future<CacheEnvelope?> readJson(String key) async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString('$_prefix$key');
    if (raw == null) return null;
    try {
      final map = jsonDecode(raw) as Map<String, dynamic>;
      return CacheEnvelope(
        data: map['data'],
        cachedAt: DateTime.tryParse(map['cachedAt'] as String? ?? ''),
      );
    } catch (_) {
      return null;
    }
  }
}

class CacheEnvelope {
  final Object? data;
  final DateTime? cachedAt;
  const CacheEnvelope({required this.data, required this.cachedAt});
}

/// Wraps a value with provenance so the UI can show an "offline" banner.
class Cached<T> {
  final T data;
  final bool fromCache;
  final DateTime? cachedAt;

  const Cached(this.data, {this.fromCache = false, this.cachedAt});
}
