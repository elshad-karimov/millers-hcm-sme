import 'package:flutter/material.dart';
import '../api/self_api.dart';
import '../models/team.dart';

const Color brandColor = Color(0xFF5B3FE5);

/// Manager team overview — headcount summary + direct reports list (§11.3).
class TeamScreen extends StatefulWidget {
  const TeamScreen({super.key});

  @override
  State<TeamScreen> createState() => _TeamScreenState();
}

class _TeamScreenState extends State<TeamScreen> {
  late Future<_TeamData> _future;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  Future<_TeamData> _load() async {
    final results = await Future.wait([
      SelfApi.instance.getTeam(),
      SelfApi.instance.getTeamSummary(),
    ]);
    return _TeamData(
      members: results[0] as List<TeamMember>,
      summary: results[1] as TeamSummary,
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('My Team',
            style: TextStyle(fontWeight: FontWeight.bold, color: brandColor)),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh_outlined),
            onPressed: () => setState(() => _future = _load()),
          ),
        ],
      ),
      body: FutureBuilder<_TeamData>(
        future: _future,
        builder: (context, snap) {
          if (snap.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snap.hasError) {
            return _ErrorView(
              message: 'Failed to load team',
              onRetry: () => setState(() => _future = _load()),
            );
          }
          final data = snap.data!;
          return _TeamBody(data: data);
        },
      ),
    );
  }
}

class _TeamData {
  final List<TeamMember> members;
  final TeamSummary summary;
  const _TeamData({required this.members, required this.summary});
}

// ---------------------------------------------------------------------------
// Body
// ---------------------------------------------------------------------------

class _TeamBody extends StatelessWidget {
  const _TeamBody({required this.data});
  final _TeamData data;

  @override
  Widget build(BuildContext context) {
    final s = data.summary;
    return Column(
      children: [
        // Summary strip
        Container(
          color: brandColor.withValues(alpha: 0.06),
          padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 16),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              _SummaryTile(
                  icon: Icons.people_alt_outlined,
                  label: 'Headcount',
                  value: '${s.headcount}',
                  color: brandColor),
              _SummaryTile(
                  icon: Icons.beach_access_outlined,
                  label: 'On Leave',
                  value: '${s.onLeaveToday}',
                  color: Colors.orange),
              _SummaryTile(
                  icon: Icons.hourglass_top_outlined,
                  label: 'Probation',
                  value: '${s.onProbation}',
                  color: Colors.teal),
              _SummaryTile(
                  icon: Icons.pending_actions_outlined,
                  label: 'Pending',
                  value: '${s.pendingLeaveRequests}',
                  color: Colors.red.shade400),
            ],
          ),
        ),
        // Members list
        Expanded(
          child: data.members.isEmpty
              ? const Center(child: Text('No direct reports.'))
              : ListView.builder(
                  padding: const EdgeInsets.all(12),
                  itemCount: data.members.length,
                  itemBuilder: (ctx, i) =>
                      _MemberCard(member: data.members[i]),
                ),
        ),
      ],
    );
  }
}

class _SummaryTile extends StatelessWidget {
  const _SummaryTile({
    required this.icon,
    required this.label,
    required this.value,
    required this.color,
  });

  final IconData icon;
  final String label;
  final String value;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Icon(icon, color: color, size: 22),
        const SizedBox(height: 4),
        Text(value,
            style: TextStyle(
                fontWeight: FontWeight.bold,
                fontSize: 18,
                color: color)),
        Text(label,
            style:
                TextStyle(color: Colors.grey.shade600, fontSize: 11)),
      ],
    );
  }
}

class _MemberCard extends StatelessWidget {
  const _MemberCard({required this.member});
  final TeamMember member;

  @override
  Widget build(BuildContext context) {
    final initials =
        '${member.firstName.isNotEmpty ? member.firstName[0] : ''}${member.lastName.isNotEmpty ? member.lastName[0] : ''}';

    return Card(
      elevation: 0,
      color: Colors.grey.shade50,
      margin: const EdgeInsets.only(bottom: 8),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: ListTile(
        contentPadding:
            const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
        leading: CircleAvatar(
          backgroundColor: member.onLeaveToday
              ? Colors.orange.withValues(alpha: 0.2)
              : brandColor.withValues(alpha: 0.12),
          child: Text(
            initials,
            style: TextStyle(
              fontWeight: FontWeight.bold,
              fontSize: 13,
              color: member.onLeaveToday ? Colors.orange.shade700 : brandColor,
            ),
          ),
        ),
        title: Row(
          children: [
            Expanded(
              child: Text(member.fullName,
                  style: const TextStyle(
                      fontWeight: FontWeight.bold, fontSize: 14)),
            ),
            if (member.onLeaveToday)
              Container(
                padding: const EdgeInsets.symmetric(
                    horizontal: 8, vertical: 2),
                decoration: BoxDecoration(
                  color: Colors.orange.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(
                      color: Colors.orange.withValues(alpha: 0.4)),
                ),
                child: Text('On Leave',
                    style: TextStyle(
                        color: Colors.orange.shade700,
                        fontSize: 10,
                        fontWeight: FontWeight.w600)),
              ),
          ],
        ),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (member.positionTitle != null)
              Text(member.positionTitle!,
                  style: TextStyle(
                      color: Colors.grey.shade600, fontSize: 12)),
            if (member.departmentName != null)
              Text(member.departmentName!,
                  style: TextStyle(
                      color: Colors.grey.shade400, fontSize: 11)),
          ],
        ),
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
