import 'package:flutter/material.dart';
import '../api/self_api.dart';
import '../models/requests.dart';

const Color brandColor = Color(0xFF5B3FE5);

/// Business trip + permission requests screen (§11.2).
class RequestsScreen extends StatelessWidget {
  const RequestsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('My Requests',
              style:
                  TextStyle(fontWeight: FontWeight.bold, color: brandColor)),
          bottom: TabBar(
            labelColor: brandColor,
            indicatorColor: brandColor,
            tabs: const [
              Tab(
                  text: 'Business Trips',
                  icon: Icon(Icons.flight_takeoff_outlined)),
              Tab(
                  text: 'Permissions',
                  icon: Icon(Icons.access_time_outlined)),
            ],
          ),
        ),
        body: const TabBarView(
          children: [
            _TripsTab(),
            _PermissionsTab(),
          ],
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Business trips tab
// ---------------------------------------------------------------------------

class _TripsTab extends StatefulWidget {
  const _TripsTab();

  @override
  State<_TripsTab> createState() => _TripsTabState();
}

class _TripsTabState extends State<_TripsTab> {
  late Future<List<BusinessTrip>> _future;

  @override
  void initState() {
    super.initState();
    _future = SelfApi.instance.getBusinessTrips();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: FutureBuilder<List<BusinessTrip>>(
        future: _future,
        builder: (context, snap) {
          if (snap.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snap.hasError) {
            return _ErrorView(
              message: 'Failed to load trips',
              onRetry: () => setState(
                  () => _future = SelfApi.instance.getBusinessTrips()),
            );
          }
          final trips = snap.data!;
          if (trips.isEmpty) {
            return const Center(child: Text('No business trips yet.'));
          }
          return ListView.builder(
            padding: const EdgeInsets.all(12),
            itemCount: trips.length,
            itemBuilder: (ctx, i) => _TripCard(trip: trips[i]),
          );
        },
      ),
      floatingActionButton: FloatingActionButton(
        backgroundColor: brandColor,
        foregroundColor: Colors.white,
        onPressed: () async {
          final submitted = await showModalBottomSheet<bool>(
            context: context,
            isScrollControlled: true,
            shape: const RoundedRectangleBorder(
              borderRadius:
                  BorderRadius.vertical(top: Radius.circular(20)),
            ),
            builder: (_) => const _SubmitTripSheet(),
          );
          if (submitted == true) {
            setState(
                () => _future = SelfApi.instance.getBusinessTrips());
          }
        },
        child: const Icon(Icons.add),
      ),
    );
  }
}

class _TripCard extends StatelessWidget {
  const _TripCard({required this.trip});
  final BusinessTrip trip;

  Color _statusColor(String s) {
    switch (s.toUpperCase()) {
      case 'APPROVED':
        return Colors.green;
      case 'REJECTED':
        return Colors.red;
      case 'COMPLETED':
        return Colors.teal;
      default:
        return Colors.orange;
    }
  }

  @override
  Widget build(BuildContext context) {
    final sc = _statusColor(trip.status);
    return Card(
      elevation: 0,
      color: Colors.grey.shade50,
      margin: const EdgeInsets.only(bottom: 10),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: ListTile(
        contentPadding:
            const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
        leading: Container(
          width: 44,
          height: 44,
          decoration: BoxDecoration(
            color: brandColor.withValues(alpha: 0.1),
            borderRadius: BorderRadius.circular(10),
          ),
          child: const Icon(Icons.flight_takeoff_rounded,
              color: brandColor, size: 22),
        ),
        title: Text(
          trip.destinationCity ?? trip.tripNo,
          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
        ),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '${trip.startDate}  →  ${trip.endDate}',
              style: TextStyle(color: Colors.grey.shade600, fontSize: 12),
            ),
            if (trip.purpose != null)
              Text(trip.purpose!,
                  style: TextStyle(
                      color: Colors.grey.shade400, fontSize: 11),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis),
          ],
        ),
        trailing: _StatusChip(label: trip.status, color: sc),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Submit trip bottom sheet
// ---------------------------------------------------------------------------

class _SubmitTripSheet extends StatefulWidget {
  const _SubmitTripSheet();

  @override
  State<_SubmitTripSheet> createState() => _SubmitTripSheetState();
}

class _SubmitTripSheetState extends State<_SubmitTripSheet> {
  final _cityController = TextEditingController();
  final _purposeController = TextEditingController();
  String _tripType = 'DOMESTIC';
  DateTime? _startDate;
  DateTime? _endDate;
  bool _submitting = false;

  @override
  void dispose() {
    _cityController.dispose();
    _purposeController.dispose();
    super.dispose();
  }

  String _fmt(DateTime? dt) => dt == null
      ? 'Select date'
      : '${dt.year}-${dt.month.toString().padLeft(2, '0')}-${dt.day.toString().padLeft(2, '0')}';

  Future<void> _pickDate({required bool isStart}) async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: now,
      firstDate: now,
      lastDate: DateTime(now.year + 2),
      builder: (ctx, child) => Theme(
        data: Theme.of(ctx).copyWith(
          colorScheme:
              Theme.of(ctx).colorScheme.copyWith(primary: brandColor),
        ),
        child: child!,
      ),
    );
    if (picked != null && mounted) {
      setState(() {
        if (isStart) {
          _startDate = picked;
          if (_endDate != null && _endDate!.isBefore(picked)) {
            _endDate = picked;
          }
        } else {
          _endDate = picked;
        }
      });
    }
  }

  Future<void> _submit() async {
    if (_cityController.text.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Enter destination city')));
      return;
    }
    if (_startDate == null || _endDate == null) {
      ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Select travel dates')));
      return;
    }
    setState(() => _submitting = true);
    try {
      await SelfApi.instance.submitBusinessTrip(
        destinationCity: _cityController.text.trim(),
        startDate: _fmt(_startDate),
        endDate: _fmt(_endDate),
        purpose: _purposeController.text.trim().isEmpty
            ? null
            : _purposeController.text.trim(),
        tripType: _tripType,
      );
      if (!mounted) return;
      Navigator.pop(context, true);
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: Text('Business trip submitted'),
        backgroundColor: Colors.green,
      ));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('Failed: $e'),
        backgroundColor: Colors.red.shade700,
      ));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final bottom = MediaQuery.of(context).viewInsets.bottom;
    return Padding(
      padding: EdgeInsets.fromLTRB(20, 20, 20, bottom + 20),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Center(
            child: Container(
              width: 40, height: 4,
              decoration: BoxDecoration(
                  color: Colors.grey.shade300,
                  borderRadius: BorderRadius.circular(2)),
            ),
          ),
          const SizedBox(height: 16),
          const Text('New Business Trip',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          const SizedBox(height: 16),
          DropdownButtonFormField<String>(
            value: _tripType,
            decoration: InputDecoration(
              labelText: 'Trip type',
              border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(10)),
            ),
            items: const [
              DropdownMenuItem(value: 'DOMESTIC', child: Text('Domestic')),
              DropdownMenuItem(
                  value: 'INTERNATIONAL', child: Text('International')),
            ],
            onChanged: (v) => setState(() => _tripType = v!),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _cityController,
            decoration: InputDecoration(
              labelText: 'Destination city',
              border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(10)),
              prefixIcon: const Icon(Icons.location_city_outlined),
            ),
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: () => _pickDate(isStart: true),
                  icon: const Icon(Icons.calendar_today_outlined, size: 16),
                  label: Text(_fmt(_startDate)),
                  style: OutlinedButton.styleFrom(
                    foregroundColor: _startDate == null
                        ? Colors.grey.shade600
                        : brandColor,
                    side: BorderSide(color: Colors.grey.shade300),
                    shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(10)),
                  ),
                ),
              ),
              const Padding(
                  padding: EdgeInsets.symmetric(horizontal: 8),
                  child: Text('→')),
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: () => _pickDate(isStart: false),
                  icon: const Icon(Icons.calendar_today_outlined, size: 16),
                  label: Text(_fmt(_endDate)),
                  style: OutlinedButton.styleFrom(
                    foregroundColor:
                        _endDate == null ? Colors.grey.shade600 : brandColor,
                    side: BorderSide(color: Colors.grey.shade300),
                    shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(10)),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _purposeController,
            maxLines: 2,
            decoration: InputDecoration(
              labelText: 'Purpose (optional)',
              border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(10)),
              prefixIcon: const Icon(Icons.notes_outlined),
              alignLabelWithHint: true,
            ),
          ),
          const SizedBox(height: 20),
          SizedBox(
            width: double.infinity,
            height: 50,
            child: FilledButton(
              onPressed: _submitting ? null : _submit,
              style: FilledButton.styleFrom(
                  backgroundColor: brandColor,
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12))),
              child: _submitting
                  ? const SizedBox(
                      width: 22,
                      height: 22,
                      child: CircularProgressIndicator(
                          strokeWidth: 2.5, color: Colors.white))
                  : const Text('Submit',
                      style: TextStyle(
                          fontSize: 16, fontWeight: FontWeight.w600)),
            ),
          ),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Permissions tab
// ---------------------------------------------------------------------------

class _PermissionsTab extends StatefulWidget {
  const _PermissionsTab();

  @override
  State<_PermissionsTab> createState() => _PermissionsTabState();
}

class _PermissionsTabState extends State<_PermissionsTab> {
  late Future<List<PermissionRequest>> _future;

  @override
  void initState() {
    super.initState();
    _future = SelfApi.instance.getPermissions();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: FutureBuilder<List<PermissionRequest>>(
        future: _future,
        builder: (context, snap) {
          if (snap.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snap.hasError) {
            return _ErrorView(
              message: 'Failed to load permissions',
              onRetry: () => setState(
                  () => _future = SelfApi.instance.getPermissions()),
            );
          }
          final perms = snap.data!;
          if (perms.isEmpty) {
            return const Center(child: Text('No permission requests yet.'));
          }
          return ListView.builder(
            padding: const EdgeInsets.all(12),
            itemCount: perms.length,
            itemBuilder: (ctx, i) => _PermCard(perm: perms[i]),
          );
        },
      ),
      floatingActionButton: FloatingActionButton(
        backgroundColor: brandColor,
        foregroundColor: Colors.white,
        onPressed: () async {
          final submitted = await showModalBottomSheet<bool>(
            context: context,
            isScrollControlled: true,
            shape: const RoundedRectangleBorder(
              borderRadius:
                  BorderRadius.vertical(top: Radius.circular(20)),
            ),
            builder: (_) => const _SubmitPermissionSheet(),
          );
          if (submitted == true) {
            setState(
                () => _future = SelfApi.instance.getPermissions());
          }
        },
        child: const Icon(Icons.add),
      ),
    );
  }
}

class _PermCard extends StatelessWidget {
  const _PermCard({required this.perm});
  final PermissionRequest perm;

  Color _statusColor(String s) {
    switch (s.toUpperCase()) {
      case 'APPROVED':
        return Colors.green;
      case 'REJECTED':
        return Colors.red;
      default:
        return Colors.orange;
    }
  }

  @override
  Widget build(BuildContext context) {
    final sc = _statusColor(perm.status);
    return Card(
      elevation: 0,
      color: Colors.grey.shade50,
      margin: const EdgeInsets.only(bottom: 10),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: ListTile(
        contentPadding:
            const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        leading: Container(
          width: 44,
          height: 44,
          decoration: BoxDecoration(
            color: Colors.teal.withValues(alpha: 0.1),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Icon(Icons.access_time_rounded,
              color: Colors.teal.shade600, size: 22),
        ),
        title: Text(perm.permissionTypeName,
            style:
                const TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(perm.permissionDate,
                style: TextStyle(
                    color: Colors.grey.shade600, fontSize: 12)),
            if (perm.startTime != null && perm.endTime != null)
              Text('${perm.startTime} – ${perm.endTime}  ·  '
                  '${perm.durationHours.toStringAsFixed(1)} h',
                  style: TextStyle(
                      color: Colors.grey.shade500, fontSize: 11)),
          ],
        ),
        trailing: _StatusChip(label: perm.status, color: sc),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Submit permission bottom sheet
// ---------------------------------------------------------------------------

class _SubmitPermissionSheet extends StatefulWidget {
  const _SubmitPermissionSheet();

  @override
  State<_SubmitPermissionSheet> createState() =>
      _SubmitPermissionSheetState();
}

class _SubmitPermissionSheetState extends State<_SubmitPermissionSheet> {
  final _reasonController = TextEditingController();
  PermissionType? _selectedType;
  DateTime? _permDate;
  TimeOfDay _startTime = const TimeOfDay(hour: 9, minute: 0);
  TimeOfDay _endTime = const TimeOfDay(hour: 10, minute: 0);
  bool _submitting = false;
  List<PermissionType>? _types;

  @override
  void initState() {
    super.initState();
    PermissionTypeApi.instance
        .list()
        .then((ts) => mounted ? setState(() => _types = ts) : null);
  }

  @override
  void dispose() {
    _reasonController.dispose();
    super.dispose();
  }

  String _fmtDate(DateTime? dt) => dt == null
      ? 'Select date'
      : '${dt.year}-${dt.month.toString().padLeft(2, '0')}-${dt.day.toString().padLeft(2, '0')}';

  String _fmtTime(TimeOfDay t) =>
      '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}:00';

  double get _durationHours {
    final start = _startTime.hour * 60 + _startTime.minute;
    final end = _endTime.hour * 60 + _endTime.minute;
    return (end - start).clamp(0, 480) / 60.0;
  }

  Future<void> _submit() async {
    if (_selectedType == null || _permDate == null) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('Select type and date')));
      return;
    }
    setState(() => _submitting = true);
    try {
      await SelfApi.instance.submitPermission(
        permissionTypeId: _selectedType!.id,
        permissionDate: _fmtDate(_permDate),
        startTime: _fmtTime(_startTime),
        endTime: _fmtTime(_endTime),
        durationHours: _durationHours,
        reason: _reasonController.text.trim().isEmpty
            ? null
            : _reasonController.text.trim(),
      );
      if (!mounted) return;
      Navigator.pop(context, true);
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: Text('Permission request submitted'),
        backgroundColor: Colors.green,
      ));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('Failed: $e'),
        backgroundColor: Colors.red.shade700,
      ));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final bottom = MediaQuery.of(context).viewInsets.bottom;
    return Padding(
      padding: EdgeInsets.fromLTRB(20, 20, 20, bottom + 20),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Center(
            child: Container(
              width: 40, height: 4,
              decoration: BoxDecoration(
                  color: Colors.grey.shade300,
                  borderRadius: BorderRadius.circular(2)),
            ),
          ),
          const SizedBox(height: 16),
          const Text('New Permission Request',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          const SizedBox(height: 16),
          if (_types == null)
            const Center(child: CircularProgressIndicator())
          else
            DropdownButtonFormField<PermissionType>(
              value: _selectedType,
              decoration: InputDecoration(
                labelText: 'Permission type',
                border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(10)),
              ),
              items: _types!
                  .map((t) =>
                      DropdownMenuItem(value: t, child: Text(t.name)))
                  .toList(),
              onChanged: (v) => setState(() => _selectedType = v),
            ),
          const SizedBox(height: 12),
          OutlinedButton.icon(
            onPressed: () async {
              final now = DateTime.now();
              final picked = await showDatePicker(
                context: context,
                initialDate: now,
                firstDate: DateTime(now.year - 1),
                lastDate: DateTime(now.year + 1),
                builder: (ctx, child) => Theme(
                  data: Theme.of(ctx).copyWith(
                    colorScheme: Theme.of(ctx)
                        .colorScheme
                        .copyWith(primary: brandColor),
                  ),
                  child: child!,
                ),
              );
              if (picked != null && mounted) {
                setState(() => _permDate = picked);
              }
            },
            icon: const Icon(Icons.calendar_today_outlined, size: 16),
            label: Text(_fmtDate(_permDate)),
            style: OutlinedButton.styleFrom(
              minimumSize: const Size.fromHeight(50),
              foregroundColor:
                  _permDate == null ? Colors.grey.shade600 : brandColor,
              side: BorderSide(color: Colors.grey.shade300),
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(10)),
            ),
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Start', style: TextStyle(fontSize: 13)),
                  subtitle: Text(_startTime.format(context),
                      style: const TextStyle(
                          fontWeight: FontWeight.bold, fontSize: 15)),
                  onTap: () async {
                    final t = await showTimePicker(
                        context: context, initialTime: _startTime);
                    if (t != null && mounted) {
                      setState(() => _startTime = t);
                    }
                  },
                ),
              ),
              Expanded(
                child: ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('End', style: TextStyle(fontSize: 13)),
                  subtitle: Text(_endTime.format(context),
                      style: const TextStyle(
                          fontWeight: FontWeight.bold, fontSize: 15)),
                  onTap: () async {
                    final t = await showTimePicker(
                        context: context, initialTime: _endTime);
                    if (t != null && mounted) {
                      setState(() => _endTime = t);
                    }
                  },
                ),
              ),
              Text(
                '${_durationHours.toStringAsFixed(1)} h',
                style: TextStyle(
                    color: Colors.grey.shade600, fontSize: 13),
              ),
            ],
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _reasonController,
            maxLines: 2,
            decoration: InputDecoration(
              labelText: 'Reason (optional)',
              border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(10)),
              alignLabelWithHint: true,
            ),
          ),
          const SizedBox(height: 20),
          SizedBox(
            width: double.infinity,
            height: 50,
            child: FilledButton(
              onPressed: _submitting ? null : _submit,
              style: FilledButton.styleFrom(
                  backgroundColor: brandColor,
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12))),
              child: _submitting
                  ? const SizedBox(
                      width: 22,
                      height: 22,
                      child: CircularProgressIndicator(
                          strokeWidth: 2.5, color: Colors.white))
                  : const Text('Submit',
                      style: TextStyle(
                          fontSize: 16, fontWeight: FontWeight.w600)),
            ),
          ),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Shared widgets
// ---------------------------------------------------------------------------

class _StatusChip extends StatelessWidget {
  const _StatusChip({required this.label, required this.color});
  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: color.withValues(alpha: 0.4)),
      ),
      child: Text(
        label,
        style: TextStyle(
            color: color, fontSize: 10, fontWeight: FontWeight.w600),
      ),
    );
  }
}

class _ErrorView extends StatelessWidget {
  const _ErrorView({required this.message, required this.onRetry});
  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.error_outline, size: 48, color: Colors.red),
          const SizedBox(height: 8),
          Text(message,
              style: TextStyle(color: Colors.grey.shade600)),
          const SizedBox(height: 16),
          FilledButton.icon(
            onPressed: onRetry,
            icon: const Icon(Icons.refresh),
            label: const Text('Retry'),
            style: FilledButton.styleFrom(backgroundColor: brandColor),
          ),
        ],
      ),
    );
  }
}
