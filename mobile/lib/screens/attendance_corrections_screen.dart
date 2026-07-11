import 'package:flutter/material.dart';

import '../api/self_api.dart';
import '../models/attendance.dart';
import '../models/employee.dart';
import '../widgets/common.dart';

/// M498 — attendance corrections + overtime requests (self-service).
class AttendanceCorrectionsScreen extends StatefulWidget {
  const AttendanceCorrectionsScreen({super.key});

  @override
  State<AttendanceCorrectionsScreen> createState() =>
      _AttendanceCorrectionsScreenState();
}

class _AttendanceCorrectionsScreenState
    extends State<AttendanceCorrectionsScreen> {
  late Future<Employee> _profile;
  int _refresh = 0;

  @override
  void initState() {
    super.initState();
    _profile = SelfApi.instance.getProfile();
  }

  void _reload() => setState(() => _refresh++);

  Color _wfColor(String s) {
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
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Attendance Requests',
              style: TextStyle(fontWeight: FontWeight.bold, color: kBrandColor)),
          bottom: const TabBar(
            labelColor: kBrandColor,
            indicatorColor: kBrandColor,
            tabs: [
              Tab(text: 'Corrections', icon: Icon(Icons.edit_calendar_outlined)),
              Tab(text: 'Overtime', icon: Icon(Icons.more_time_outlined)),
            ],
          ),
        ),
        body: FutureBuilder<Employee>(
          future: _profile,
          builder: (context, snap) {
            if (snap.connectionState == ConnectionState.waiting) {
              return const Center(child: CircularProgressIndicator());
            }
            if (snap.hasError) {
              return ErrorRetry(
                  message: 'Failed to load profile',
                  onRetry: () => setState(
                      () => _profile = SelfApi.instance.getProfile()));
            }
            final employeeId = snap.data!.id;
            return TabBarView(
              children: [
                _CorrectionsTab(
                    key: ValueKey('corr-$_refresh'),
                    employeeId: employeeId,
                    wfColor: _wfColor,
                    onChanged: _reload),
                _OvertimeTab(
                    key: ValueKey('ot-$_refresh'),
                    employeeId: employeeId,
                    wfColor: _wfColor,
                    onChanged: _reload),
              ],
            );
          },
        ),
      ),
    );
  }
}

// ───────────────────────────── Corrections tab ─────────────────────────────

class _CorrectionsTab extends StatelessWidget {
  const _CorrectionsTab(
      {super.key,
      required this.employeeId,
      required this.wfColor,
      required this.onChanged});

  final String employeeId;
  final Color Function(String) wfColor;
  final VoidCallback onChanged;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: FutureBuilder<List<AttendanceCorrection>>(
        future: SelfApi.instance.getCorrections(),
        builder: (context, snap) {
          if (snap.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snap.hasError) {
            return ErrorRetry(
                message: 'Failed to load corrections', onRetry: onChanged);
          }
          final list = snap.data!;
          if (list.isEmpty) {
            return const EmptyState(
                icon: Icons.edit_calendar_outlined,
                message: 'No correction requests.');
          }
          return ListView.builder(
            padding: const EdgeInsets.all(12),
            itemCount: list.length,
            itemBuilder: (context, i) {
              final c = list[i];
              return Card(
                margin: const EdgeInsets.only(bottom: 10),
                elevation: 0,
                color: Colors.grey.shade50,
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12)),
                child: ListTile(
                  title: Row(
                    children: [
                      Expanded(
                        child: Text('${c.workDate}  ·  ${c.correctionType ?? ''}',
                            style: const TextStyle(
                                fontWeight: FontWeight.bold, fontSize: 14)),
                      ),
                      StatusPill(
                          label: c.workflowStatus,
                          color: wfColor(c.workflowStatus)),
                    ],
                  ),
                  subtitle: Padding(
                    padding: const EdgeInsets.only(top: 4),
                    child: Text([
                      if (c.requestedClockIn != null)
                        'In ${shortTime(c.requestedClockIn!)}',
                      if (c.requestedClockOut != null)
                        'Out ${shortTime(c.requestedClockOut!)}',
                      if (c.reason != null && c.reason!.isNotEmpty) c.reason!,
                    ].join('  ·  ')),
                  ),
                ),
              );
            },
          );
        },
      ),
      floatingActionButton: FloatingActionButton.extended(
        backgroundColor: kBrandColor,
        foregroundColor: Colors.white,
        icon: const Icon(Icons.add),
        label: const Text('New'),
        onPressed: () => showModalBottomSheet(
          context: context,
          isScrollControlled: true,
          shape: const RoundedRectangleBorder(
              borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
          builder: (_) => _CorrectionSheet(employeeId: employeeId),
        ).then((ok) {
          if (ok == true) onChanged();
        }),
      ),
    );
  }
}

class _CorrectionSheet extends StatefulWidget {
  const _CorrectionSheet({required this.employeeId});
  final String employeeId;

  @override
  State<_CorrectionSheet> createState() => _CorrectionSheetState();
}

class _CorrectionSheetState extends State<_CorrectionSheet> {
  DateTime? _date;
  TimeOfDay? _clockIn;
  TimeOfDay? _clockOut;
  final _reason = TextEditingController();
  bool _submitting = false;

  @override
  void dispose() {
    _reason.dispose();
    super.dispose();
  }

  Future<void> _pickDate() async {
    final now = DateTime.now();
    final d = await showDatePicker(
      context: context,
      initialDate: now,
      firstDate: DateTime(now.year - 1),
      lastDate: now,
    );
    if (d != null) setState(() => _date = d);
  }

  Future<void> _pickTime(bool isIn) async {
    final t = await showTimePicker(
        context: context, initialTime: const TimeOfDay(hour: 9, minute: 0));
    if (t != null) setState(() => isIn ? _clockIn = t : _clockOut = t);
  }

  String? _iso(DateTime date, TimeOfDay? t) {
    if (t == null) return null;
    final dt = DateTime(date.year, date.month, date.day, t.hour, t.minute);
    return dt.toUtc().toIso8601String();
  }

  Future<void> _submit() async {
    if (_date == null) {
      ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Please pick a date')));
      return;
    }
    if (_clockIn == null && _clockOut == null) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('Enter a requested clock-in and/or clock-out time')));
      return;
    }
    if (_reason.text.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Please give a reason')));
      return;
    }
    setState(() => _submitting = true);
    try {
      await SelfApi.instance.submitCorrection(
        employeeId: widget.employeeId,
        workDate:
            '${_date!.year}-${_date!.month.toString().padLeft(2, '0')}-${_date!.day.toString().padLeft(2, '0')}',
        requestedClockIn: _iso(_date!, _clockIn),
        requestedClockOut: _iso(_date!, _clockOut),
        reason: _reason.text.trim(),
      );
      if (!mounted) return;
      Navigator.pop(context, true);
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('Correction submitted'),
          backgroundColor: Colors.green));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Failed: $e'), backgroundColor: Colors.red.shade700));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final vi = MediaQuery.of(context).viewInsets;
    return Padding(
      padding: EdgeInsets.fromLTRB(20, 20, 20, vi.bottom + 20),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Attendance Correction',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          const SizedBox(height: 16),
          OutlinedButton.icon(
            onPressed: _pickDate,
            icon: const Icon(Icons.calendar_today_outlined, size: 16),
            label: Text(_date == null
                ? 'Select work date'
                : '${_date!.year}-${_date!.month.toString().padLeft(2, '0')}-${_date!.day.toString().padLeft(2, '0')}'),
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: () => _pickTime(true),
                  icon: const Icon(Icons.login_rounded, size: 16),
                  label: Text(
                      _clockIn == null ? 'Clock-in' : _clockIn!.format(context)),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: () => _pickTime(false),
                  icon: const Icon(Icons.logout_rounded, size: 16),
                  label: Text(_clockOut == null
                      ? 'Clock-out'
                      : _clockOut!.format(context)),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _reason,
            maxLines: 2,
            decoration: InputDecoration(
              labelText: 'Reason',
              border:
                  OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
            ),
          ),
          const SizedBox(height: 18),
          SizedBox(
            width: double.infinity,
            height: 50,
            child: FilledButton(
              onPressed: _submitting ? null : _submit,
              style: FilledButton.styleFrom(backgroundColor: kBrandColor),
              child: _submitting
                  ? const SizedBox(
                      width: 22,
                      height: 22,
                      child: CircularProgressIndicator(
                          strokeWidth: 2.5, color: Colors.white))
                  : const Text('Submit'),
            ),
          ),
        ],
      ),
    );
  }
}

// ───────────────────────────── Overtime tab ─────────────────────────────────

class _OvertimeTab extends StatelessWidget {
  const _OvertimeTab(
      {super.key,
      required this.employeeId,
      required this.wfColor,
      required this.onChanged});

  final String employeeId;
  final Color Function(String) wfColor;
  final VoidCallback onChanged;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: FutureBuilder<List<OvertimeRequestItem>>(
        future: SelfApi.instance.getOvertimeRequests(),
        builder: (context, snap) {
          if (snap.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snap.hasError) {
            return ErrorRetry(
                message: 'Failed to load overtime requests', onRetry: onChanged);
          }
          final list = snap.data!;
          if (list.isEmpty) {
            return const EmptyState(
                icon: Icons.more_time_outlined,
                message: 'No overtime requests.');
          }
          return ListView.builder(
            padding: const EdgeInsets.all(12),
            itemCount: list.length,
            itemBuilder: (context, i) {
              final o = list[i];
              return Card(
                margin: const EdgeInsets.only(bottom: 10),
                elevation: 0,
                color: Colors.grey.shade50,
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12)),
                child: ListTile(
                  title: Row(
                    children: [
                      Expanded(
                        child: Text(
                            '${o.workDate}  ·  ${o.requestedMinutes}m',
                            style: const TextStyle(
                                fontWeight: FontWeight.bold, fontSize: 14)),
                      ),
                      StatusPill(
                          label: o.workflowStatus,
                          color: wfColor(o.workflowStatus)),
                    ],
                  ),
                  subtitle: (o.reason != null && o.reason!.isNotEmpty)
                      ? Padding(
                          padding: const EdgeInsets.only(top: 4),
                          child: Text(o.reason!))
                      : null,
                ),
              );
            },
          );
        },
      ),
      floatingActionButton: FloatingActionButton.extended(
        backgroundColor: kBrandColor,
        foregroundColor: Colors.white,
        icon: const Icon(Icons.add),
        label: const Text('New'),
        onPressed: () => showModalBottomSheet(
          context: context,
          isScrollControlled: true,
          shape: const RoundedRectangleBorder(
              borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
          builder: (_) => _OvertimeSheet(employeeId: employeeId),
        ).then((ok) {
          if (ok == true) onChanged();
        }),
      ),
    );
  }
}

class _OvertimeSheet extends StatefulWidget {
  const _OvertimeSheet({required this.employeeId});
  final String employeeId;

  @override
  State<_OvertimeSheet> createState() => _OvertimeSheetState();
}

class _OvertimeSheetState extends State<_OvertimeSheet> {
  DateTime? _date;
  TimeOfDay? _start;
  TimeOfDay? _end;
  final _reason = TextEditingController();
  bool _submitting = false;

  @override
  void dispose() {
    _reason.dispose();
    super.dispose();
  }

  Future<void> _pickDate() async {
    final now = DateTime.now();
    final d = await showDatePicker(
        context: context,
        initialDate: now,
        firstDate: DateTime(now.year - 1),
        lastDate: now);
    if (d != null) setState(() => _date = d);
  }

  Future<void> _pickTime(bool isStart) async {
    final t = await showTimePicker(
        context: context, initialTime: const TimeOfDay(hour: 18, minute: 0));
    if (t != null) setState(() => isStart ? _start = t : _end = t);
  }

  String _iso(DateTime date, TimeOfDay t) =>
      DateTime(date.year, date.month, date.day, t.hour, t.minute)
          .toUtc()
          .toIso8601String();

  Future<void> _submit() async {
    if (_date == null || _start == null || _end == null) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('Pick a date and start/end times')));
      return;
    }
    setState(() => _submitting = true);
    try {
      await SelfApi.instance.submitOvertime(
        employeeId: widget.employeeId,
        workDate:
            '${_date!.year}-${_date!.month.toString().padLeft(2, '0')}-${_date!.day.toString().padLeft(2, '0')}',
        otStart: _iso(_date!, _start!),
        otEnd: _iso(_date!, _end!),
        reason: _reason.text.trim().isEmpty ? null : _reason.text.trim(),
      );
      if (!mounted) return;
      Navigator.pop(context, true);
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('Overtime request submitted'),
          backgroundColor: Colors.green));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Failed: $e'), backgroundColor: Colors.red.shade700));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final vi = MediaQuery.of(context).viewInsets;
    return Padding(
      padding: EdgeInsets.fromLTRB(20, 20, 20, vi.bottom + 20),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Overtime Request',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          const SizedBox(height: 16),
          OutlinedButton.icon(
            onPressed: _pickDate,
            icon: const Icon(Icons.calendar_today_outlined, size: 16),
            label: Text(_date == null
                ? 'Select work date'
                : '${_date!.year}-${_date!.month.toString().padLeft(2, '0')}-${_date!.day.toString().padLeft(2, '0')}'),
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: () => _pickTime(true),
                  icon: const Icon(Icons.play_arrow_rounded, size: 16),
                  label:
                      Text(_start == null ? 'Start' : _start!.format(context)),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: () => _pickTime(false),
                  icon: const Icon(Icons.stop_rounded, size: 16),
                  label: Text(_end == null ? 'End' : _end!.format(context)),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _reason,
            maxLines: 2,
            decoration: InputDecoration(
              labelText: 'Reason (optional)',
              border:
                  OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
            ),
          ),
          const SizedBox(height: 18),
          SizedBox(
            width: double.infinity,
            height: 50,
            child: FilledButton(
              onPressed: _submitting ? null : _submit,
              style: FilledButton.styleFrom(backgroundColor: kBrandColor),
              child: _submitting
                  ? const SizedBox(
                      width: 22,
                      height: 22,
                      child: CircularProgressIndicator(
                          strokeWidth: 2.5, color: Colors.white))
                  : const Text('Submit'),
            ),
          ),
        ],
      ),
    );
  }
}

/// HH:mm from an ISO datetime string.
String shortTime(String iso) {
  try {
    final dt = DateTime.parse(iso).toLocal();
    return '${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';
  } catch (_) {
    return iso;
  }
}
