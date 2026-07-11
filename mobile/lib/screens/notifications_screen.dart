import 'package:flutter/material.dart';
import '../api/self_api.dart';
import '../models/notification_item.dart';
import '../widgets/common.dart';
import 'announcements_screen.dart';
import 'approvals_screen.dart';
import 'hr_requests_screen.dart';
import 'leave_screen.dart';
import 'payslip_screen.dart';
import 'policies_screen.dart';
import 'timesheet_screen.dart';
import 'training_screen.dart';

/// M505 — in-app notification centre (GET /api/notifications).
class NotificationsScreen extends StatefulWidget {
  const NotificationsScreen({super.key});

  @override
  State<NotificationsScreen> createState() => _NotificationsScreenState();
}

class _NotificationsScreenState extends State<NotificationsScreen> {
  late Future<List<NotificationItem>> _future;

  @override
  void initState() {
    super.initState();
    _future = SelfApi.instance.getNotifications();
  }

  void _reload() =>
      setState(() => _future = SelfApi.instance.getNotifications());

  Future<void> _markAllRead() async {
    try {
      await SelfApi.instance.markAllNotificationsRead();
      if (!mounted) return;
      _reload();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('Failed: $e'),
        backgroundColor: Colors.red.shade700,
      ));
    }
  }

  /// Best-effort deep link to the relevant screen based on the module.
  Widget? _targetFor(NotificationItem n) {
    final m = (n.module ?? '').toLowerCase();
    final e = (n.entityType ?? '').toLowerCase();
    final key = '$m $e';
    if (key.contains('leave')) return const LeaveScreen();
    if (key.contains('approval') || key.contains('workflow')) {
      return const ApprovalsScreen();
    }
    if (key.contains('payroll') || key.contains('payslip')) {
      return const PayslipScreen();
    }
    if (key.contains('timesheet') || key.contains('attendance')) {
      return const TimesheetScreen();
    }
    if (key.contains('training') || key.contains('learning') ||
        key.contains('course')) {
      return const TrainingScreen();
    }
    if (key.contains('announcement')) return const AnnouncementsScreen();
    if (key.contains('policy')) return const PoliciesScreen();
    if (key.contains('request') || key.contains('service')) {
      return const HrRequestsScreen();
    }
    return null;
  }

  Future<void> _open(NotificationItem n) async {
    if (!n.isRead) {
      try {
        await SelfApi.instance.markNotificationRead(n.id);
      } catch (_) {/* non-fatal */}
    }
    final target = _targetFor(n);
    if (!mounted) return;
    if (target != null) {
      await Navigator.push(
          context, MaterialPageRoute(builder: (_) => target));
    }
    if (mounted) _reload();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Notifications',
            style: TextStyle(fontWeight: FontWeight.bold, color: kBrandColor)),
        actions: [
          IconButton(
            icon: const Icon(Icons.done_all),
            tooltip: 'Mark all read',
            onPressed: _markAllRead,
          ),
          IconButton(
              icon: const Icon(Icons.refresh_outlined), onPressed: _reload),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async => _reload(),
        color: kBrandColor,
        child: FutureBuilder<List<NotificationItem>>(
          future: _future,
          builder: (context, snap) {
            if (snap.connectionState == ConnectionState.waiting) {
              return const Center(child: CircularProgressIndicator());
            }
            if (snap.hasError) {
              return ErrorRetry(
                  message: 'Failed to load notifications', onRetry: _reload);
            }
            final items = snap.data!;
            if (items.isEmpty) {
              return ListView(children: const [
                SizedBox(height: 160),
                EmptyState(
                    icon: Icons.notifications_none,
                    message: 'You are all caught up.'),
              ]);
            }
            return ListView.separated(
              padding: const EdgeInsets.all(12),
              itemCount: items.length,
              separatorBuilder: (_, __) => const SizedBox(height: 6),
              itemBuilder: (ctx, i) =>
                  _NotificationTile(item: items[i], onTap: () => _open(items[i])),
            );
          },
        ),
      ),
    );
  }
}

class _NotificationTile extends StatelessWidget {
  const _NotificationTile({required this.item, required this.onTap});
  final NotificationItem item;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final unread = !item.isRead;
    return Card(
      elevation: 0,
      color: unread ? kBrandColor.withValues(alpha: 0.06) : Colors.grey.shade50,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: ListTile(
        onTap: onTap,
        contentPadding:
            const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        leading: Stack(
          children: [
            Container(
              width: 42,
              height: 42,
              decoration: BoxDecoration(
                color: kBrandColor.withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(10),
              ),
              child: const Icon(Icons.notifications_outlined,
                  color: kBrandColor, size: 20),
            ),
            if (unread)
              Positioned(
                right: 0,
                top: 0,
                child: Container(
                  width: 10,
                  height: 10,
                  decoration: const BoxDecoration(
                      color: Colors.redAccent, shape: BoxShape.circle),
                ),
              ),
          ],
        ),
        title: Text(item.title,
            style: TextStyle(
                fontWeight: unread ? FontWeight.bold : FontWeight.w500,
                fontSize: 14)),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (item.body != null && item.body!.isNotEmpty)
              Text(item.body!,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(color: Colors.grey.shade600, fontSize: 12)),
            const SizedBox(height: 2),
            Text(shortDate(item.createdAt),
                style: TextStyle(color: Colors.grey.shade400, fontSize: 11)),
          ],
        ),
        trailing: const Icon(Icons.chevron_right, color: Colors.grey),
      ),
    );
  }
}
