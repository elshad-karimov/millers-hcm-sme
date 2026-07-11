import 'package:flutter/material.dart';

import '../api/self_api.dart';
import '../models/team_calendar.dart';
import '../widgets/common.dart';

/// M510 — Team calendar (manager view). Confidentiality: the specific leave
/// TYPE is never shown — everyone out is simply rendered as "On Leave".
class TeamCalendarScreen extends StatefulWidget {
  const TeamCalendarScreen({super.key});

  @override
  State<TeamCalendarScreen> createState() => _TeamCalendarScreenState();
}

class _TeamCalendarScreenState extends State<TeamCalendarScreen> {
  late Future<TeamCalendar> _future;

  @override
  void initState() {
    super.initState();
    _future = SelfApi.instance.getTeamCalendar();
  }

  void _reload() =>
      setState(() => _future = SelfApi.instance.getTeamCalendar());

  Color _statusColor(String s) {
    switch (s.toUpperCase()) {
      case 'APPROVED':
        return Colors.green;
      case 'PENDING':
        return Colors.orange;
      case 'CANCELLED':
      case 'REJECTED':
        return Colors.grey;
      default:
        return Colors.blueGrey;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Team Calendar',
            style: TextStyle(fontWeight: FontWeight.bold, color: kBrandColor)),
        actions: [
          IconButton(
              icon: const Icon(Icons.refresh_outlined), onPressed: _reload),
        ],
      ),
      body: FutureBuilder<TeamCalendar>(
        future: _future,
        builder: (context, snap) {
          if (snap.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snap.hasError) {
            return ErrorRetry(
                message: 'Failed to load team calendar', onRetry: _reload);
          }
          final cal = snap.data!;
          if (!cal.isManager) {
            return const EmptyState(
                icon: Icons.groups_outlined,
                message: 'You don\'t manage a team.');
          }
          // Only show entries that are actually time-off (not cancelled/rejected).
          final entries = cal.entries
              .where((e) =>
                  e.status.toUpperCase() != 'CANCELLED' &&
                  e.status.toUpperCase() != 'REJECTED')
              .toList()
            ..sort((a, b) => a.startDate.compareTo(b.startDate));

          return Column(
            children: [
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(14),
                color: kBrandColor.withValues(alpha: 0.06),
                child: Text(
                  '${cal.windowStart ?? ''} → ${cal.windowEnd ?? ''}   ·   '
                  'Team of ${cal.teamSize}   ·   ${entries.length} away',
                  style: TextStyle(color: Colors.grey.shade700, fontSize: 13),
                ),
              ),
              Expanded(
                child: entries.isEmpty
                    ? const EmptyState(
                        icon: Icons.event_available_outlined,
                        message: 'No one is scheduled off this month.')
                    : ListView.builder(
                        padding: const EdgeInsets.all(12),
                        itemCount: entries.length,
                        itemBuilder: (context, i) =>
                            _entryCard(entries[i]),
                      ),
              ),
            ],
          );
        },
      ),
    );
  }

  Widget _entryCard(TeamLeaveEntry e) {
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      elevation: 0,
      color: Colors.grey.shade50,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: kBrandColor.withValues(alpha: 0.12),
          child: const Icon(Icons.beach_access_outlined, color: kBrandColor),
        ),
        title: Text(e.employeeName,
            style: const TextStyle(fontWeight: FontWeight.w600)),
        subtitle: Padding(
          padding: const EdgeInsets.only(top: 4),
          child: Text([
            'On Leave', // confidentiality: leave type intentionally hidden
            '${e.startDate} → ${e.endDate}',
            e.halfDay ? 'half day' : '${e.totalDays.toStringAsFixed(1)} day(s)',
          ].join('  ·  ')),
        ),
        trailing: StatusPill(label: e.status, color: _statusColor(e.status)),
      ),
    );
  }
}
