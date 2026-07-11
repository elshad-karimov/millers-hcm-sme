import 'package:flutter/material.dart';
import '../api/self_api.dart';
import '../models/employee.dart';
import '../models/workflow.dart';
import 'announcements_screen.dart';
import 'documents_screen.dart';
import 'hr_requests_screen.dart';
import 'leave_screen.dart';
import 'notifications_screen.dart';
import 'payslip_screen.dart';
import 'approvals_screen.dart';
import 'policies_screen.dart';
import 'profile_screen.dart';
import 'timesheet_screen.dart';
import 'training_screen.dart';
import 'requests_screen.dart';
import 'goals_screen.dart';

const Color brandColor = Color(0xFF5B3FE5);

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int _selectedIndex = 0;
  int _approvalCount = 0;

  // Cache the approval-count future so we don't rebuild it every tab switch.
  late Future<List<WorkflowInstance>> _approvalsFuture;

  @override
  void initState() {
    super.initState();
    _loadApprovalCount();
  }

  void _loadApprovalCount() {
    _approvalsFuture = SelfApi.instance.getApprovalInbox();
    _approvalsFuture.then((list) {
      if (mounted) setState(() => _approvalCount = list.length);
    }).catchError((_) {});
  }

  List<Widget> get _pages => [
        _HomeTab(onReload: _loadApprovalCount),
        const LeaveScreen(),
        const TimesheetScreen(),
        const PayslipScreen(),
        ApprovalsScreen(onCountChanged: (c) {
          if (mounted) setState(() => _approvalCount = c);
        }),
      ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(
        index: _selectedIndex,
        children: _pages,
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _selectedIndex,
        onDestinationSelected: (i) => setState(() => _selectedIndex = i),
        indicatorColor: brandColor.withValues(alpha: 0.15),
        destinations: [
          const NavigationDestination(
            icon: Icon(Icons.home_outlined),
            selectedIcon: Icon(Icons.home_rounded, color: brandColor),
            label: 'Home',
          ),
          const NavigationDestination(
            icon: Icon(Icons.event_available_outlined),
            selectedIcon:
                Icon(Icons.event_available_rounded, color: brandColor),
            label: 'Leave',
          ),
          const NavigationDestination(
            icon: Icon(Icons.calendar_month_outlined),
            selectedIcon: Icon(Icons.calendar_month_rounded, color: brandColor),
            label: 'Timesheet',
          ),
          const NavigationDestination(
            icon: Icon(Icons.receipt_long_outlined),
            selectedIcon: Icon(Icons.receipt_long_rounded, color: brandColor),
            label: 'Payslips',
          ),
          NavigationDestination(
            icon: Badge(
              isLabelVisible: _approvalCount > 0,
              label: Text('$_approvalCount'),
              child: const Icon(Icons.task_alt_outlined),
            ),
            selectedIcon: Badge(
              isLabelVisible: _approvalCount > 0,
              label: Text('$_approvalCount'),
              child: const Icon(Icons.task_alt_rounded, color: brandColor),
            ),
            label: 'Approvals',
          ),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Home tab
// ---------------------------------------------------------------------------

class _HomeTab extends StatelessWidget {
  const _HomeTab({required this.onReload});

  final VoidCallback onReload;

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<Employee>(
      future: SelfApi.instance.getProfile(),
      builder: (context, snap) {
        return Scaffold(
          appBar: AppBar(
            title: const Text(
              'Millers HCM',
              style: TextStyle(
                  fontWeight: FontWeight.bold, color: brandColor, fontSize: 20),
            ),
            actions: [
              const _NotificationBell(),
              IconButton(
                icon: const Icon(Icons.account_circle_outlined),
                onPressed: () => Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const ProfileScreen()),
                ),
              ),
            ],
          ),
          body: () {
            if (snap.connectionState == ConnectionState.waiting) {
              return const Center(child: CircularProgressIndicator());
            }
            if (snap.hasError) {
              return Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.error_outline,
                        size: 48, color: Colors.red),
                    const SizedBox(height: 8),
                    Text('Failed to load profile',
                        style: TextStyle(color: Colors.grey.shade600)),
                    const SizedBox(height: 16),
                    FilledButton.icon(
                      onPressed: onReload,
                      icon: const Icon(Icons.refresh),
                      label: const Text('Retry'),
                    ),
                  ],
                ),
              );
            }
            final emp = snap.data!;
            return _HomeTabContent(employee: emp);
          }(),
        );
      },
    );
  }
}

class _HomeTabContent extends StatelessWidget {
  const _HomeTabContent({required this.employee});

  final Employee employee;

  @override
  Widget build(BuildContext context) {
    final initials =
        '${employee.firstName.isNotEmpty ? employee.firstName[0] : ''}${employee.lastName.isNotEmpty ? employee.lastName[0] : ''}';

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Welcome card
          Card(
            elevation: 0,
            color: brandColor,
            shape:
                RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Row(
                children: [
                  CircleAvatar(
                    radius: 28,
                    backgroundColor: Colors.white.withValues(alpha: 0.25),
                    child: Text(
                      initials,
                      style: const TextStyle(
                          color: Colors.white,
                          fontWeight: FontWeight.bold,
                          fontSize: 20),
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Hello, ${employee.firstName}',
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 20,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          employee.employeeNo,
                          style: TextStyle(
                              color: Colors.white.withValues(alpha: 0.8),
                              fontSize: 13),
                        ),
                        const SizedBox(height: 8),
                        _StatusChip(status: employee.employmentStatus),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 24),
          Text(
            'Quick actions',
            style: Theme.of(context)
                .textTheme
                .titleMedium
                ?.copyWith(fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: _QuickActionCard(
                  icon: Icons.beach_access_rounded,
                  label: 'Submit Leave',
                  color: Colors.orange.shade700,
                  onTap: () => Navigator.push(
                    context,
                    MaterialPageRoute(builder: (_) => const LeaveScreen()),
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _QuickActionCard(
                  icon: Icons.calendar_month_rounded,
                  label: 'Timesheet',
                  color: Colors.indigo.shade600,
                  onTap: () => Navigator.push(
                    context,
                    MaterialPageRoute(builder: (_) => const TimesheetScreen()),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: _QuickActionCard(
                  icon: Icons.receipt_long_rounded,
                  label: 'My Payslips',
                  color: Colors.teal.shade700,
                  onTap: () => Navigator.push(
                    context,
                    MaterialPageRoute(builder: (_) => const PayslipScreen()),
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _QuickActionCard(
                  icon: Icons.school_rounded,
                  label: 'Training',
                  color: Colors.purple.shade600,
                  onTap: () => Navigator.push(
                    context,
                    MaterialPageRoute(
                        builder: (_) => const TrainingScreen()),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: _QuickActionCard(
                  icon: Icons.flight_takeoff_rounded,
                  label: 'Requests',
                  color: Colors.cyan.shade700,
                  onTap: () => Navigator.push(
                    context,
                    MaterialPageRoute(
                        builder: (_) => const RequestsScreen()),
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _QuickActionCard(
                  icon: Icons.track_changes_rounded,
                  label: 'Goals',
                  color: Colors.green.shade700,
                  onTap: () => Navigator.push(
                    context,
                    MaterialPageRoute(builder: (_) => const GoalsScreen()),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: _QuickActionCard(
                  icon: Icons.campaign_rounded,
                  label: 'Announcements',
                  color: Colors.deepOrange.shade600,
                  onTap: () => Navigator.push(
                    context,
                    MaterialPageRoute(
                        builder: (_) => const AnnouncementsScreen()),
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _QuickActionCard(
                  icon: Icons.rule_folder_rounded,
                  label: 'Policies',
                  color: Colors.blue.shade700,
                  onTap: () => Navigator.push(
                    context,
                    MaterialPageRoute(builder: (_) => const PoliciesScreen()),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: _QuickActionCard(
                  icon: Icons.support_agent_rounded,
                  label: 'HR Requests',
                  color: Colors.pink.shade600,
                  onTap: () => Navigator.push(
                    context,
                    MaterialPageRoute(builder: (_) => const HrRequestsScreen()),
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _QuickActionCard(
                  icon: Icons.folder_shared_rounded,
                  label: 'Documents',
                  color: Colors.brown.shade600,
                  onTap: () => Navigator.push(
                    context,
                    MaterialPageRoute(builder: (_) => const DocumentsScreen()),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          if (employee.department != null || employee.position != null)
            Card(
              elevation: 0,
              color: Colors.grey.shade50,
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12)),
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  children: [
                    if (employee.department != null)
                      _InfoRow(
                          icon: Icons.business_outlined,
                          label: 'Department',
                          value: employee.department!),
                    if (employee.position != null) ...[
                      if (employee.department != null)
                        const Divider(height: 16),
                      _InfoRow(
                          icon: Icons.work_outline_rounded,
                          label: 'Position',
                          value: employee.position!),
                    ],
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _StatusChip extends StatelessWidget {
  const _StatusChip({required this.status});

  final String status;

  @override
  Widget build(BuildContext context) {
    final isActive =
        status.toUpperCase() == 'ACTIVE' || status.toUpperCase() == 'EMPLOYED';
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
      decoration: BoxDecoration(
        color: (isActive ? Colors.green : Colors.orange).withValues(alpha: 0.2),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(
        status,
        style: TextStyle(
          color: isActive ? Colors.green.shade200 : Colors.orange.shade200,
          fontSize: 11,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}

class _QuickActionCard extends StatelessWidget {
  const _QuickActionCard({
    required this.icon,
    required this.label,
    required this.color,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final Color color;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Card(
      elevation: 0,
      shape:
          RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      color: color.withValues(alpha: 0.08),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 20),
          child: Column(
            children: [
              Icon(icon, color: color, size: 32),
              const SizedBox(height: 8),
              Text(
                label,
                style: TextStyle(
                    color: color,
                    fontWeight: FontWeight.w600,
                    fontSize: 13),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// App-bar notification bell with an unread-count badge (M505).
class _NotificationBell extends StatefulWidget {
  const _NotificationBell();

  @override
  State<_NotificationBell> createState() => _NotificationBellState();
}

class _NotificationBellState extends State<_NotificationBell> {
  int _unread = 0;

  @override
  void initState() {
    super.initState();
    _loadCount();
  }

  void _loadCount() {
    SelfApi.instance.getUnreadNotificationCount().then((c) {
      if (mounted) setState(() => _unread = c);
    }).catchError((_) {});
  }

  @override
  Widget build(BuildContext context) {
    return IconButton(
      icon: Badge(
        isLabelVisible: _unread > 0,
        label: Text('$_unread'),
        child: const Icon(Icons.notifications_outlined),
      ),
      tooltip: 'Notifications',
      onPressed: () async {
        await Navigator.push(
          context,
          MaterialPageRoute(builder: (_) => const NotificationsScreen()),
        );
        _loadCount();
      },
    );
  }
}

class _InfoRow extends StatelessWidget {
  const _InfoRow(
      {required this.icon, required this.label, required this.value});

  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(icon, size: 18, color: Colors.grey.shade500),
        const SizedBox(width: 8),
        Text('$label: ',
            style: TextStyle(color: Colors.grey.shade500, fontSize: 13)),
        Expanded(
          child: Text(value,
              style: const TextStyle(
                  fontWeight: FontWeight.w600, fontSize: 13),
              overflow: TextOverflow.ellipsis),
        ),
      ],
    );
  }
}
