import 'package:flutter/material.dart';
import '../api/self_api.dart';
import '../models/timesheet.dart';

const Color brandColor = Color(0xFF5B3FE5);

class TimesheetScreen extends StatelessWidget {
  const TimesheetScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Timesheets',
            style: TextStyle(fontWeight: FontWeight.bold, color: brandColor)),
      ),
      body: FutureBuilder<List<Timesheet>>(
        future: SelfApi.instance.getTimesheets(),
        builder: (context, snap) {
          if (snap.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snap.hasError) {
            return _ErrorView(
              message: 'Failed to load timesheets',
              onRetry: () => (context as Element).markNeedsBuild(),
            );
          }
          final list = snap.data!;
          if (list.isEmpty) {
            return const Center(child: Text('No timesheets found.'));
          }
          return ListView.builder(
            padding: const EdgeInsets.all(12),
            itemCount: list.length,
            itemBuilder: (ctx, i) => _TimesheetCard(ts: list[i]),
          );
        },
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Timesheet list card
// ---------------------------------------------------------------------------

class _TimesheetCard extends StatelessWidget {
  const _TimesheetCard({required this.ts});
  final Timesheet ts;

  Color _statusColor(String s) {
    switch (s.toUpperCase()) {
      case 'APPROVED':
      case 'LOCKED':
        return Colors.green;
      case 'SUBMITTED':
        return Colors.blue;
      case 'REOPENED':
        return Colors.orange;
      default:
        return Colors.grey;
    }
  }

  @override
  Widget build(BuildContext context) {
    final sc = _statusColor(ts.status);
    return Card(
      elevation: 0,
      color: Colors.grey.shade50,
      margin: const EdgeInsets.only(bottom: 10),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: () => Navigator.push(
          context,
          MaterialPageRoute(
            builder: (_) => TimesheetDetailScreen(timesheetId: ts.id, label: ts.periodLabel),
          ),
        ),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    ts.periodLabel,
                    style: const TextStyle(
                        fontWeight: FontWeight.bold, fontSize: 16),
                  ),
                  _StatusChip(label: ts.status, color: sc),
                ],
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  _StatTile(
                    label: 'Worked h',
                    value: ts.totalWorkedHours.toStringAsFixed(1),
                    icon: Icons.schedule_outlined,
                  ),
                  _StatTile(
                    label: 'OT h',
                    value: ts.totalOvertimeHours.toStringAsFixed(1),
                    icon: Icons.more_time_outlined,
                    highlight: ts.totalOvertimeHours > 0,
                  ),
                  _StatTile(
                    label: 'Leave d',
                    value: ts.totalLeaveDays.toStringAsFixed(1),
                    icon: Icons.beach_access_outlined,
                  ),
                  _StatTile(
                    label: 'Absent d',
                    value: ts.totalAbsentDays.toStringAsFixed(1),
                    icon: Icons.cancel_outlined,
                    highlight: ts.totalAbsentDays > 0,
                    highlightColor: Colors.red,
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _StatTile extends StatelessWidget {
  const _StatTile({
    required this.label,
    required this.value,
    required this.icon,
    this.highlight = false,
    this.highlightColor,
  });

  final String label;
  final String value;
  final IconData icon;
  final bool highlight;
  final Color? highlightColor;

  @override
  Widget build(BuildContext context) {
    final color = highlight
        ? (highlightColor ?? brandColor)
        : Colors.grey.shade600;
    return Expanded(
      child: Column(
        children: [
          Icon(icon, size: 18, color: color),
          const SizedBox(height: 4),
          Text(value,
              style: TextStyle(
                  fontWeight: FontWeight.bold,
                  fontSize: 15,
                  color: color)),
          Text(label,
              style:
                  TextStyle(fontSize: 10, color: Colors.grey.shade500)),
        ],
      ),
    );
  }
}

class _StatusChip extends StatelessWidget {
  const _StatusChip({required this.label, required this.color});
  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: color.withValues(alpha: 0.4)),
      ),
      child: Text(
        label,
        style: TextStyle(
            color: color, fontSize: 11, fontWeight: FontWeight.w600),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Detail screen — daily rows
// ---------------------------------------------------------------------------

class TimesheetDetailScreen extends StatefulWidget {
  const TimesheetDetailScreen({
    super.key,
    required this.timesheetId,
    required this.label,
  });

  final String timesheetId;
  final String label;

  @override
  State<TimesheetDetailScreen> createState() => _TimesheetDetailScreenState();
}

class _TimesheetDetailScreenState extends State<TimesheetDetailScreen> {
  late Future<Timesheet> _future;

  @override
  void initState() {
    super.initState();
    _future = SelfApi.instance.getTimesheetDetail(widget.timesheetId);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.label,
            style: const TextStyle(
                fontWeight: FontWeight.bold, color: brandColor)),
      ),
      body: FutureBuilder<Timesheet>(
        future: _future,
        builder: (context, snap) {
          if (snap.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snap.hasError) {
            return _ErrorView(
              message: 'Failed to load detail',
              onRetry: () => setState(
                  () => _future = SelfApi.instance
                      .getTimesheetDetail(widget.timesheetId)),
            );
          }
          final ts = snap.data!;
          return _DetailBody(ts: ts);
        },
      ),
    );
  }
}

class _DetailBody extends StatelessWidget {
  const _DetailBody({required this.ts});
  final Timesheet ts;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        // Summary strip
        Container(
          color: brandColor.withValues(alpha: 0.06),
          padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 16),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              _SummaryCol(
                  label: 'Worked', value: '${ts.totalWorkedHours.toStringAsFixed(1)} h'),
              _SummaryCol(
                  label: 'Overtime', value: '${ts.totalOvertimeHours.toStringAsFixed(1)} h'),
              _SummaryCol(
                  label: 'Leave', value: '${ts.totalLeaveDays.toStringAsFixed(1)} d'),
              _SummaryCol(
                  label: 'Absent', value: '${ts.totalAbsentDays.toStringAsFixed(1)} d'),
            ],
          ),
        ),
        // Daily rows
        Expanded(
          child: ts.days.isEmpty
              ? const Center(child: Text('No daily rows yet.'))
              : ListView.separated(
                  padding: const EdgeInsets.all(12),
                  itemCount: ts.days.length,
                  separatorBuilder: (_, __) => const Divider(height: 1),
                  itemBuilder: (ctx, i) => _DayRow(day: ts.days[i]),
                ),
        ),
      ],
    );
  }
}

class _SummaryCol extends StatelessWidget {
  const _SummaryCol({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Text(value,
            style: const TextStyle(
                fontWeight: FontWeight.bold,
                fontSize: 16,
                color: brandColor)),
        Text(label,
            style: TextStyle(fontSize: 11, color: Colors.grey.shade600)),
      ],
    );
  }
}

class _DayRow extends StatelessWidget {
  const _DayRow({required this.day});
  final TimesheetDay day;

  Color _codeColor(String code) {
    switch (code) {
      case 'W':
        return Colors.blue.shade700;
      case 'L':
        return Colors.green.shade600;
      case 'S':
        return Colors.red.shade400;
      case 'BT':
        return Colors.purple.shade400;
      case 'P':
        return Colors.teal.shade400;
      case 'O':
        return Colors.orange.shade600;
      case 'H':
        return Colors.indigo.shade400;
      default:
        return Colors.grey; // A = absent
    }
  }

  String _codeLabel(String code) {
    const labels = {
      'W': 'Work',
      'L': 'Leave',
      'S': 'Sick',
      'BT': 'Business Trip',
      'P': 'Permission',
      'O': 'Overtime',
      'H': 'Holiday',
      'A': 'Absent',
    };
    return labels[code] ?? code;
  }

  @override
  Widget build(BuildContext context) {
    final color = _codeColor(day.primaryCode);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 4),
      child: Row(
        children: [
          // Code badge
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.12),
              borderRadius: BorderRadius.circular(8),
            ),
            alignment: Alignment.center,
            child: Text(
              day.primaryCode,
              style: TextStyle(
                  color: color,
                  fontWeight: FontWeight.bold,
                  fontSize: 13),
            ),
          ),
          const SizedBox(width: 12),
          // Date + label
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  day.workDate,
                  style: const TextStyle(
                      fontWeight: FontWeight.w600, fontSize: 14),
                ),
                Text(
                  _codeLabel(day.primaryCode),
                  style: TextStyle(
                      color: Colors.grey.shade500, fontSize: 12),
                ),
              ],
            ),
          ),
          // Hours
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                '${day.workedHours.toStringAsFixed(1)} h',
                style: const TextStyle(
                    fontWeight: FontWeight.w600, fontSize: 13),
              ),
              if (day.overtimeHours > 0)
                Text(
                  '+${day.overtimeHours.toStringAsFixed(1)} OT',
                  style: TextStyle(
                      color: Colors.orange.shade700, fontSize: 11),
                ),
              if (day.lateMinutes > 0)
                Text(
                  '${day.lateMinutes} min late',
                  style: TextStyle(
                      color: Colors.red.shade400, fontSize: 11),
                ),
            ],
          ),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Shared error widget
// ---------------------------------------------------------------------------

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
