import 'package:flutter/material.dart';
import '../api/self_api.dart';
import '../auth/auth_service.dart';
import '../models/employee.dart';

const Color brandColor = Color(0xFF5B3FE5);

class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key});

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  late Future<Employee> _future;
  bool _signingOut = false;

  @override
  void initState() {
    super.initState();
    _future = SelfApi.instance.getProfile();
  }

  void _reload() => setState(() => _future = SelfApi.instance.getProfile());

  Future<void> _signOut() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        shape:
            RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: const Text('Sign Out'),
        content: const Text('Are you sure you want to sign out?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: FilledButton.styleFrom(backgroundColor: Colors.red.shade700),
            child: const Text('Sign Out'),
          ),
        ],
      ),
    );

    if (confirmed != true || !mounted) return;

    setState(() => _signingOut = true);
    try {
      await AuthService.instance.logout();
      if (!mounted) return;
      // Pop back to login — remove all routes and push login.
      Navigator.of(context).pushNamedAndRemoveUntil('/login', (_) => false);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Sign-out failed: $e'),
          backgroundColor: Colors.red.shade700,
        ),
      );
    } finally {
      if (mounted) setState(() => _signingOut = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'My Profile',
          style: TextStyle(fontWeight: FontWeight.bold, color: brandColor),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh_outlined),
            onPressed: _reload,
            tooltip: 'Refresh',
          ),
        ],
      ),
      body: FutureBuilder<Employee>(
        future: _future,
        builder: (context, snap) {
          if (snap.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snap.hasError) {
            return _ErrorView(
              message: 'Failed to load profile',
              onRetry: _reload,
            );
          }
          final emp = snap.data!;
          return _ProfileBody(
            employee: emp,
            onSignOut: _signOut,
            signingOut: _signingOut,
          );
        },
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Profile body
// ---------------------------------------------------------------------------

class _ProfileBody extends StatelessWidget {
  const _ProfileBody({
    required this.employee,
    required this.onSignOut,
    required this.signingOut,
  });

  final Employee employee;
  final VoidCallback onSignOut;
  final bool signingOut;

  String get _initials {
    final f = employee.firstName.isNotEmpty ? employee.firstName[0] : '';
    final l = employee.lastName.isNotEmpty ? employee.lastName[0] : '';
    return '$f$l'.toUpperCase();
  }

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(20),
      child: Column(
        children: [
          const SizedBox(height: 12),
          // Avatar
          CircleAvatar(
            radius: 48,
            backgroundColor: brandColor,
            child: Text(
              _initials,
              style: const TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.bold,
                fontSize: 30,
              ),
            ),
          ),
          const SizedBox(height: 16),
          // Name
          Text(
            employee.fullName,
            style: const TextStyle(
              fontSize: 20,
              fontWeight: FontWeight.bold,
            ),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 4),
          Text(
            employee.employeeNo,
            style: TextStyle(
              fontSize: 14,
              color: Colors.grey.shade500,
            ),
          ),
          const SizedBox(height: 8),
          _StatusChip(status: employee.employmentStatus),
          const SizedBox(height: 28),
          // Details card
          Card(
            elevation: 0,
            color: Colors.grey.shade50,
            shape:
                RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            child: Column(
              children: [
                if (employee.email != null)
                  _ProfileTile(
                    icon: Icons.email_outlined,
                    label: 'Email',
                    value: employee.email!,
                  ),
                if (employee.phone != null) ...[
                  if (employee.email != null) const Divider(height: 1, indent: 56),
                  _ProfileTile(
                    icon: Icons.phone_outlined,
                    label: 'Phone',
                    value: employee.phone!,
                  ),
                ],
                if (employee.department != null) ...[
                  const Divider(height: 1, indent: 56),
                  _ProfileTile(
                    icon: Icons.business_outlined,
                    label: 'Department',
                    value: employee.department!,
                  ),
                ],
                if (employee.position != null) ...[
                  const Divider(height: 1, indent: 56),
                  _ProfileTile(
                    icon: Icons.work_outline_rounded,
                    label: 'Position',
                    value: employee.position!,
                  ),
                ],
                const Divider(height: 1, indent: 56),
                _ProfileTile(
                  icon: Icons.badge_outlined,
                  label: 'Status',
                  value: employee.employmentStatus,
                ),
                if (employee.hireDate != null) ...[
                  const Divider(height: 1, indent: 56),
                  _ProfileTile(
                    icon: Icons.calendar_today_outlined,
                    label: 'Hire Date',
                    value: employee.hireDate!,
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(height: 32),
          // Sign out button
          SizedBox(
            width: double.infinity,
            height: 50,
            child: OutlinedButton.icon(
              onPressed: signingOut ? null : onSignOut,
              icon: signingOut
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(
                          strokeWidth: 2.5, color: Colors.red),
                    )
                  : const Icon(Icons.logout_rounded, color: Colors.red),
              label: Text(
                signingOut ? 'Signing out…' : 'Sign Out',
                style: TextStyle(
                    color: Colors.red.shade700,
                    fontSize: 16,
                    fontWeight: FontWeight.w600),
              ),
              style: OutlinedButton.styleFrom(
                side: BorderSide(color: Colors.red.shade300),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12)),
              ),
            ),
          ),
          const SizedBox(height: 12),
        ],
      ),
    );
  }
}

class _ProfileTile extends StatelessWidget {
  const _ProfileTile({
    required this.icon,
    required this.label,
    required this.value,
  });

  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: Icon(icon, color: brandColor, size: 22),
      title: Text(
        label,
        style: TextStyle(color: Colors.grey.shade500, fontSize: 12),
      ),
      subtitle: Text(
        value,
        style: const TextStyle(
            fontWeight: FontWeight.w600, fontSize: 14, color: Colors.black87),
      ),
      dense: true,
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
    final color = isActive ? Colors.green.shade700 : Colors.orange.shade700;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: color.withValues(alpha: 0.4)),
      ),
      child: Text(
        status,
        style: TextStyle(
          color: color,
          fontSize: 12,
          fontWeight: FontWeight.w600,
        ),
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
