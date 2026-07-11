import 'dart:math';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../api/self_api.dart';
import '../config/location_service.dart';
import '../config/punch_queue.dart';
import '../models/attendance.dart';
import '../widgets/common.dart';
import 'attendance_corrections_screen.dart';

/// M496 — Attendance clock-in/out with GPS capture + geofence distance warning.
/// M508 — offline punch queue with pending-sync count + replay.
class AttendanceScreen extends StatefulWidget {
  const AttendanceScreen({super.key});

  @override
  State<AttendanceScreen> createState() => _AttendanceScreenState();
}

class _AttendanceScreenState extends State<AttendanceScreen> {
  static const _consentKey = 'hcm_location_consent';
  static const _deviceIdKey = 'hcm_device_id';

  Future<List<DailySummary>>? _summariesFuture;
  GeofenceConfig? _geofences;
  bool _punching = false;
  int _pendingSync = 0;

  @override
  void initState() {
    super.initState();
    _reload();
    _loadGeofences();
    _refreshPendingCount();
    // Opportunistically flush any offline punches when the screen opens.
    _replayQueue(silent: true);
  }

  void _reload() {
    final now = DateTime.now();
    final to = _ymd(now);
    final from = _ymd(now.subtract(const Duration(days: 13)));
    setState(() {
      _summariesFuture =
          SelfApi.instance.getAttendanceSummaries(from: from, to: to);
    });
  }

  Future<void> _loadGeofences() async {
    try {
      final gf = await SelfApi.instance.getGeofences();
      if (mounted) setState(() => _geofences = gf);
    } catch (_) {/* geofence warning is best-effort */}
  }

  Future<void> _refreshPendingCount() async {
    final c = await PunchQueue.instance.pendingCount();
    if (mounted) setState(() => _pendingSync = c);
  }

  // ── Consent (PRD S26) ──────────────────────────────────────────────────────

  Future<bool> _ensureConsent() async {
    final prefs = await SharedPreferences.getInstance();
    if (prefs.getBool(_consentKey) == true) return true;
    if (!mounted) return false;
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: const Text('Location notice'),
        content: const Text(
          'When you clock in or out, this app records your current GPS location '
          'and sends it with your attendance punch so HR can verify you are at '
          'an approved work location.\n\nDo you consent to recording your location?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Not now'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: kBrandColor),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('I consent'),
          ),
        ],
      ),
    );
    if (ok == true) await prefs.setBool(_consentKey, true);
    return ok == true;
  }

  Future<String> _deviceId() async {
    final prefs = await SharedPreferences.getInstance();
    var id = prefs.getString(_deviceIdKey);
    if (id == null) {
      id = 'mob-${DateTime.now().millisecondsSinceEpoch}-${Random().nextInt(99999)}';
      await prefs.setString(_deviceIdKey, id);
    }
    return id;
  }

  // ── Punch ──────────────────────────────────────────────────────────────────

  Future<void> _punch(String type) async {
    if (_punching) return;
    if (!await _ensureConsent()) return;

    setState(() => _punching = true);
    try {
      final loc = await LocationService.instance.getPosition();
      double? lat, lon, acc;
      if (loc.isOk) {
        lat = loc.position!.latitude;
        lon = loc.position!.longitude;
        acc = loc.position!.accuracy;
      }

      // Client-side geofence distance warning (haversine).
      final warning = _geofenceWarning(lat, lon);
      if (warning != null) {
        final proceed = await _confirmOutsideGeofence(type, warning);
        if (proceed != true) {
          if (mounted) setState(() => _punching = false);
          return;
        }
      }

      final queueId =
          'q-${DateTime.now().microsecondsSinceEpoch}-${Random().nextInt(9999)}';
      final deviceId = await _deviceId();
      final ts = DateTime.now().toUtc().toIso8601String();

      try {
        final res = await SelfApi.instance.punch(
          type: type,
          timestamp: ts,
          latitude: lat,
          longitude: lon,
          gpsAccuracy: acc,
          deviceId: deviceId,
          offlineQueueId: queueId,
        );
        if (!mounted) return;
        _showPunchResult(res);
        _reload();
        _replayQueue(silent: true); // flush anything else that was queued
      } catch (e) {
        // Offline / server unreachable — queue it for later replay (M508).
        await PunchQueue.instance.enqueue(QueuedPunch(
          offlineQueueId: queueId,
          type: type,
          timestamp: ts,
          latitude: lat,
          longitude: lon,
          gpsAccuracy: acc,
          deviceId: deviceId,
        ));
        await _refreshPendingCount();
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('No connection — punch saved offline and will sync.'),
            backgroundColor: Colors.orange,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _punching = false);
    }
  }

  String? _geofenceWarning(double? lat, double? lon) {
    final gf = _geofences;
    if (gf == null || !gf.geofencingConfigured || gf.locations.isEmpty) {
      return null;
    }
    if (lat == null || lon == null) return null;
    double? nearest;
    String nearestName = '';
    for (final l in gf.locations) {
      final d = LocationService.distanceMeters(lat, lon, l.latitude, l.longitude);
      if (nearest == null || d < nearest) {
        nearest = d;
        nearestName = l.name;
      }
    }
    if (nearest == null) return null;
    // Warn when outside every geofence radius.
    final inside = gf.locations.any((l) =>
        LocationService.distanceMeters(lat, lon, l.latitude, l.longitude) <=
        l.radiusM);
    if (inside) return null;
    return 'You appear to be ${nearest.round()} m from "$nearestName", '
        'outside the approved work area.';
  }

  Future<bool?> _confirmOutsideGeofence(String type, String warning) {
    return showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: Row(
          children: [
            Icon(Icons.warning_amber_rounded, color: Colors.orange.shade700),
            const SizedBox(width: 8),
            const Text('Outside work area'),
          ],
        ),
        content: Text('$warning\n\nSubmit this ${type == 'IN' ? 'clock-in' : 'clock-out'} anyway?'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Cancel')),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: kBrandColor),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Submit anyway'),
          ),
        ],
      ),
    );
  }

  void _showPunchResult(PunchResult res) {
    final inside = res.geofenceStatus == 'INSIDE';
    final color = res.flagged
        ? Colors.orange
        : (inside ? Colors.green : Colors.blueGrey);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        backgroundColor: color,
        content: Text(
          '${res.type == 'IN' ? 'Clocked in' : 'Clocked out'} at '
          '${_hm(res.recordedAt)}  ·  ${res.geofenceStatus}'
          '${res.flagged ? '  ·  flagged for review' : ''}',
        ),
      ),
    );
  }

  Future<void> _replayQueue({bool silent = false}) async {
    final before = await PunchQueue.instance.pendingCount();
    if (before == 0) {
      if (!silent && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Nothing to sync.')),
        );
      }
      return;
    }
    final synced = await PunchQueue.instance.replay();
    await _refreshPendingCount();
    if (synced > 0) _reload();
    if (!silent && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          backgroundColor: synced > 0 ? Colors.green : Colors.orange,
          content: Text(synced > 0
              ? '$synced offline punch(es) synced.'
              : 'Still offline — punches remain queued.'),
        ),
      );
    }
  }

  // ── Build ────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Attendance',
            style: TextStyle(fontWeight: FontWeight.bold, color: kBrandColor)),
        actions: [
          IconButton(
            tooltip: 'Corrections',
            icon: const Icon(Icons.edit_calendar_outlined),
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(
                  builder: (_) => const AttendanceCorrectionsScreen()),
            ),
          ),
          IconButton(
            tooltip: 'Refresh',
            icon: const Icon(Icons.refresh_outlined),
            onPressed: _reload,
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          _reload();
          await _loadGeofences();
          await _refreshPendingCount();
        },
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            if (_pendingSync > 0) _syncBanner(),
            _clockCard(),
            const SizedBox(height: 20),
            Text('Recent days',
                style: Theme.of(context)
                    .textTheme
                    .titleMedium
                    ?.copyWith(fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            _history(),
          ],
        ),
      ),
    );
  }

  Widget _syncBanner() {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: Colors.orange.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.orange.withValues(alpha: 0.4)),
      ),
      child: Row(
        children: [
          const Icon(Icons.cloud_off_outlined, color: Colors.orange),
          const SizedBox(width: 10),
          Expanded(
            child: Text('$_pendingSync punch(es) waiting to sync',
                style: TextStyle(color: Colors.orange.shade900, fontSize: 13)),
          ),
          TextButton(
            onPressed: () => _replayQueue(),
            child: const Text('Sync now'),
          ),
        ],
      ),
    );
  }

  Widget _clockCard() {
    return Card(
      elevation: 0,
      color: kBrandColor.withValues(alpha: 0.06),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            const Icon(Icons.access_time_filled_rounded,
                size: 40, color: kBrandColor),
            const SizedBox(height: 8),
            FutureBuilder<List<DailySummary>>(
              future: _summariesFuture,
              builder: (context, snap) {
                final today = _todaySummary(snap.data);
                return _todayLine(today, snap.connectionState);
              },
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: FilledButton.icon(
                    onPressed: _punching ? null : () => _punch('IN'),
                    icon: const Icon(Icons.login_rounded),
                    label: const Text('Clock In'),
                    style: FilledButton.styleFrom(
                        backgroundColor: Colors.green.shade600,
                        minimumSize: const Size.fromHeight(52)),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: FilledButton.icon(
                    onPressed: _punching ? null : () => _punch('OUT'),
                    icon: const Icon(Icons.logout_rounded),
                    label: const Text('Clock Out'),
                    style: FilledButton.styleFrom(
                        backgroundColor: Colors.red.shade500,
                        minimumSize: const Size.fromHeight(52)),
                  ),
                ),
              ],
            ),
            if (_punching)
              const Padding(
                padding: EdgeInsets.only(top: 14),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(strokeWidth: 2)),
                    SizedBox(width: 10),
                    Text('Recording location…',
                        style: TextStyle(fontSize: 12, color: Colors.grey)),
                  ],
                ),
              ),
            const SizedBox(height: 8),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(Icons.info_outline,
                    size: 13, color: Colors.grey.shade500),
                const SizedBox(width: 4),
                Flexible(
                  child: Text(
                    'Your GPS location is recorded with each punch.',
                    style:
                        TextStyle(fontSize: 11, color: Colors.grey.shade500),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _todayLine(DailySummary? today, ConnectionState cs) {
    if (cs == ConnectionState.waiting && today == null) {
      return const Text('Loading today…',
          style: TextStyle(color: Colors.grey));
    }
    if (today == null) {
      return const Text('No punch recorded today',
          style: TextStyle(fontWeight: FontWeight.w600));
    }
    final parts = <String>[];
    if (today.entryTime != null) parts.add('In ${_hm(today.entryTime!)}');
    if (today.exitTime != null) parts.add('Out ${_hm(today.exitTime!)}');
    return Column(
      children: [
        Text(parts.isEmpty ? today.status : parts.join('   ·   '),
            style:
                const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
        const SizedBox(height: 2),
        Text('Today  ·  ${today.status}',
            style: TextStyle(fontSize: 12, color: Colors.grey.shade600)),
      ],
    );
  }

  Widget _history() {
    return FutureBuilder<List<DailySummary>>(
      future: _summariesFuture,
      builder: (context, snap) {
        if (snap.connectionState == ConnectionState.waiting) {
          return const Padding(
            padding: EdgeInsets.all(24),
            child: Center(child: CircularProgressIndicator()),
          );
        }
        if (snap.hasError) {
          return ErrorRetry(
              message: 'Failed to load attendance', onRetry: _reload);
        }
        final list = (snap.data ?? [])
            .where((s) => s.status != 'NON_WORKING_DAY')
            .toList();
        if (list.isEmpty) {
          return const EmptyState(
              icon: Icons.event_busy_outlined, message: 'No recent records.');
        }
        return Column(
          children: list.map(_historyTile).toList(),
        );
      },
    );
  }

  Widget _historyTile(DailySummary s) {
    final color = _statusColor(s.status);
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      elevation: 0,
      color: Colors.grey.shade50,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: ListTile(
        title: Text(s.workDate,
            style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14)),
        subtitle: Text([
          if (s.entryTime != null) 'In ${_hm(s.entryTime!)}',
          if (s.exitTime != null) 'Out ${_hm(s.exitTime!)}',
          if (s.workedMinutes > 0) '${_hoursLabel(s.workedMinutes)} worked',
          if (s.lateMinutes > 0) '${s.lateMinutes}m late',
          if (s.overtimeMinutes > 0) '${s.overtimeMinutes}m OT',
        ].join('  ·  ')),
        trailing: StatusPill(label: s.status, color: color),
      ),
    );
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  DailySummary? _todaySummary(List<DailySummary>? list) {
    if (list == null) return null;
    final today = _ymd(DateTime.now());
    for (final s in list) {
      if (s.workDate == today) return s;
    }
    return null;
  }

  Color _statusColor(String s) {
    switch (s.toUpperCase()) {
      case 'PRESENT':
        return Colors.green;
      case 'PARTIAL':
        return Colors.orange;
      case 'ABSENT':
        return Colors.red;
      default:
        return Colors.blueGrey;
    }
  }

  static String _ymd(DateTime d) =>
      '${d.year}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

  String _hm(String iso) {
    try {
      final dt = DateTime.parse(iso).toLocal();
      return '${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';
    } catch (_) {
      return iso;
    }
  }

  String _hoursLabel(int minutes) {
    final h = minutes ~/ 60;
    final m = minutes % 60;
    if (h == 0) return '${m}m';
    return m == 0 ? '${h}h' : '${h}h ${m}m';
  }
}
