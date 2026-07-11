import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../api/self_api.dart';

/// M508 — offline attendance punch queue.
///
/// When a punch fails (offline), it is queued locally with a generated
/// [offlineQueueId] + the device timestamp. On reconnect the queue is replayed
/// to POST /api/self/attendance/punch, which is idempotent on offlineQueueId,
/// so a double-send is safe.
class PunchQueue {
  PunchQueue._();
  static final PunchQueue instance = PunchQueue._();

  static const _key = 'hcm_punch_queue';

  Future<List<QueuedPunch>> all() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_key);
    if (raw == null) return [];
    try {
      final list = jsonDecode(raw) as List;
      return list
          .map((e) => QueuedPunch.fromJson(e as Map<String, dynamic>))
          .toList();
    } catch (_) {
      return [];
    }
  }

  Future<int> pendingCount() async => (await all()).length;

  Future<void> enqueue(QueuedPunch punch) async {
    final items = await all()..add(punch);
    await _write(items);
  }

  Future<void> _write(List<QueuedPunch> items) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
        _key, jsonEncode(items.map((e) => e.toJson()).toList()));
  }

  /// Replays all queued punches. Successfully-sent items are removed from the
  /// queue; items that fail again stay queued for the next attempt.
  /// Returns how many were synced.
  Future<int> replay() async {
    final items = await all();
    if (items.isEmpty) return 0;
    final remaining = <QueuedPunch>[];
    int synced = 0;
    for (final p in items) {
      try {
        await SelfApi.instance.punch(
          type: p.type,
          timestamp: p.timestamp,
          latitude: p.latitude,
          longitude: p.longitude,
          gpsAccuracy: p.gpsAccuracy,
          deviceId: p.deviceId,
          offlineQueueId: p.offlineQueueId,
        );
        synced++;
      } catch (_) {
        remaining.add(p); // still offline / still failing — keep it
      }
    }
    await _write(remaining);
    return synced;
  }
}

class QueuedPunch {
  final String offlineQueueId;
  final String type; // IN | OUT
  final String timestamp; // ISO-8601 device time
  final double? latitude;
  final double? longitude;
  final double? gpsAccuracy;
  final String? deviceId;

  const QueuedPunch({
    required this.offlineQueueId,
    required this.type,
    required this.timestamp,
    this.latitude,
    this.longitude,
    this.gpsAccuracy,
    this.deviceId,
  });

  Map<String, dynamic> toJson() => {
        'offlineQueueId': offlineQueueId,
        'type': type,
        'timestamp': timestamp,
        if (latitude != null) 'latitude': latitude,
        if (longitude != null) 'longitude': longitude,
        if (gpsAccuracy != null) 'gpsAccuracy': gpsAccuracy,
        if (deviceId != null) 'deviceId': deviceId,
      };

  factory QueuedPunch.fromJson(Map<String, dynamic> j) => QueuedPunch(
        offlineQueueId: j['offlineQueueId'] as String? ?? '',
        type: j['type'] as String? ?? 'IN',
        timestamp: j['timestamp'] as String? ?? '',
        latitude: (j['latitude'] as num?)?.toDouble(),
        longitude: (j['longitude'] as num?)?.toDouble(),
        gpsAccuracy: (j['gpsAccuracy'] as num?)?.toDouble(),
        deviceId: j['deviceId'] as String?,
      );
}
