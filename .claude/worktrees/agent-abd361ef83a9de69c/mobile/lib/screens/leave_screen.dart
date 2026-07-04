import 'package:flutter/material.dart';
import '../api/self_api.dart';
import '../models/leave.dart';

const Color brandColor = Color(0xFF5B3FE5);

class LeaveScreen extends StatelessWidget {
  const LeaveScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Leave',
              style:
                  TextStyle(fontWeight: FontWeight.bold, color: brandColor)),
          bottom: TabBar(
            labelColor: brandColor,
            indicatorColor: brandColor,
            tabs: const [
              Tab(text: 'Balances', icon: Icon(Icons.account_balance_wallet_outlined)),
              Tab(text: 'Requests', icon: Icon(Icons.list_alt_outlined)),
            ],
          ),
        ),
        body: const TabBarView(
          children: [
            _BalancesTab(),
            _RequestsTab(),
          ],
        ),
        floatingActionButton: FloatingActionButton(
          backgroundColor: brandColor,
          foregroundColor: Colors.white,
          onPressed: () => showModalBottomSheet(
            context: context,
            isScrollControlled: true,
            shape: const RoundedRectangleBorder(
              borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
            ),
            builder: (_) => const _SubmitLeaveSheet(),
          ),
          child: const Icon(Icons.add),
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Balances tab
// ---------------------------------------------------------------------------

class _BalancesTab extends StatelessWidget {
  const _BalancesTab();

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<List<LeaveBalance>>(
      future: SelfApi.instance.getLeaveBalances(),
      builder: (context, snap) {
        if (snap.connectionState == ConnectionState.waiting) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snap.hasError) {
          return _ErrorView(
            message: 'Failed to load balances',
            onRetry: () => (context as Element).markNeedsBuild(),
          );
        }
        final balances = snap.data!;
        if (balances.isEmpty) {
          return const Center(child: Text('No leave balances found.'));
        }
        return ListView.builder(
          padding: const EdgeInsets.all(12),
          itemCount: balances.length,
          itemBuilder: (context, i) => _BalanceCard(balance: balances[i]),
        );
      },
    );
  }
}

class _BalanceCard extends StatelessWidget {
  const _BalanceCard({required this.balance});

  final LeaveBalance balance;

  @override
  Widget build(BuildContext context) {
    final progress = balance.entitlementDays > 0
        ? (balance.usedDays / balance.entitlementDays).clamp(0.0, 1.0)
        : 0.0;

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      shape:
          RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      elevation: 0,
      color: Colors.grey.shade50,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  balance.leaveTypeName,
                  style: const TextStyle(
                      fontWeight: FontWeight.bold, fontSize: 15),
                ),
                Text(
                  '${balance.year}',
                  style: TextStyle(
                      color: Colors.grey.shade500, fontSize: 12),
                ),
              ],
            ),
            const SizedBox(height: 10),
            ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: LinearProgressIndicator(
                value: progress,
                minHeight: 8,
                backgroundColor: Colors.grey.shade200,
                color: brandColor,
              ),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                _DaysTile(
                    label: 'Entitlement',
                    value: balance.entitlementDays),
                _DaysTile(label: 'Used', value: balance.usedDays),
                _DaysTile(
                    label: 'Remaining', value: balance.remainingDays),
              ],
            ),
            if (balance.pendingDays > 0) ...[
              const SizedBox(height: 8),
              Text(
                '${balance.pendingDays.toStringAsFixed(1)} day(s) pending approval',
                style: TextStyle(
                    color: Colors.orange.shade700, fontSize: 12),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _DaysTile extends StatelessWidget {
  const _DaysTile({required this.label, required this.value});

  final String label;
  final double value;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Column(
        children: [
          Text(
            value % 1 == 0
                ? value.toInt().toString()
                : value.toStringAsFixed(1),
            style: const TextStyle(
                fontWeight: FontWeight.bold, fontSize: 20, color: brandColor),
          ),
          Text(
            label,
            style:
                TextStyle(color: Colors.grey.shade500, fontSize: 11),
          ),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Requests tab
// ---------------------------------------------------------------------------

class _RequestsTab extends StatelessWidget {
  const _RequestsTab();

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<List<LeaveRequest>>(
      future: SelfApi.instance.getLeaveRequests(),
      builder: (context, snap) {
        if (snap.connectionState == ConnectionState.waiting) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snap.hasError) {
          return _ErrorView(
            message: 'Failed to load leave requests',
            onRetry: () => (context as Element).markNeedsBuild(),
          );
        }
        final requests = snap.data!;
        if (requests.isEmpty) {
          return const Center(child: Text('No leave requests yet.'));
        }
        return ListView.builder(
          padding: const EdgeInsets.all(12),
          itemCount: requests.length,
          itemBuilder: (context, i) =>
              _RequestTile(request: requests[i]),
        );
      },
    );
  }
}

class _RequestTile extends StatelessWidget {
  const _RequestTile({required this.request});

  final LeaveRequest request;

  Color _statusColor(String status) {
    switch (status.toUpperCase()) {
      case 'APPROVED':
        return Colors.green;
      case 'REJECTED':
        return Colors.red;
      case 'CANCELLED':
        return Colors.grey;
      default:
        return Colors.orange;
    }
  }

  @override
  Widget build(BuildContext context) {
    final statusColor = _statusColor(request.status);
    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      shape:
          RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      elevation: 0,
      color: Colors.grey.shade50,
      child: ListTile(
        contentPadding:
            const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        title: Row(
          children: [
            Expanded(
              child: Text(
                request.leaveTypeName,
                style: const TextStyle(
                    fontWeight: FontWeight.bold, fontSize: 14),
              ),
            ),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: BoxDecoration(
                color: statusColor.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(20),
                border: Border.all(color: statusColor.withValues(alpha: 0.4)),
              ),
              child: Text(
                request.status,
                style: TextStyle(
                    color: statusColor,
                    fontSize: 11,
                    fontWeight: FontWeight.w600),
              ),
            ),
          ],
        ),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const SizedBox(height: 4),
            Text(
              '${request.startDate}  →  ${request.endDate}',
              style: TextStyle(color: Colors.grey.shade600, fontSize: 13),
            ),
            const SizedBox(height: 2),
            Text(
              '${request.totalDays.toStringAsFixed(1)} day(s)  ·  #${request.requestNo}',
              style: TextStyle(color: Colors.grey.shade400, fontSize: 12),
            ),
          ],
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Submit leave bottom sheet
// ---------------------------------------------------------------------------

class _SubmitLeaveSheet extends StatefulWidget {
  const _SubmitLeaveSheet();

  @override
  State<_SubmitLeaveSheet> createState() => _SubmitLeaveSheetState();
}

class _SubmitLeaveSheetState extends State<_SubmitLeaveSheet> {
  final _formKey = GlobalKey<FormState>();
  final _notesController = TextEditingController();

  LeaveType? _selectedType;
  DateTime? _startDate;
  DateTime? _endDate;
  bool _submitting = false;
  List<LeaveType>? _leaveTypes;
  String? _typesError;

  @override
  void initState() {
    super.initState();
    _loadLeaveTypes();
  }

  @override
  void dispose() {
    _notesController.dispose();
    super.dispose();
  }

  Future<void> _loadLeaveTypes() async {
    try {
      final types = await LeaveTypeApi.instance.list();
      if (mounted) setState(() => _leaveTypes = types);
    } catch (e) {
      if (mounted) setState(() => _typesError = e.toString());
    }
  }

  Future<void> _pickDate({required bool isStart}) async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: now,
      firstDate: DateTime(now.year - 1),
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

  String _fmt(DateTime? dt) =>
      dt == null ? 'Select date' : '${dt.year}-${dt.month.toString().padLeft(2, '0')}-${dt.day.toString().padLeft(2, '0')}';

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    if (_selectedType == null) {
      ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Please select a leave type')));
      return;
    }
    if (_startDate == null || _endDate == null) {
      ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Please select start and end dates')));
      return;
    }
    setState(() => _submitting = true);
    try {
      await SelfApi.instance.submitLeave(
        leaveTypeId: _selectedType!.id,
        startDate: _fmt(_startDate),
        endDate: _fmt(_endDate),
        notes: _notesController.text.trim().isEmpty
            ? null
            : _notesController.text.trim(),
      );
      if (!mounted) return;
      Navigator.pop(context);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Leave request submitted successfully'),
          backgroundColor: Colors.green,
        ),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Failed to submit: $e'),
          backgroundColor: Colors.red.shade700,
        ),
      );
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final viewInsets = MediaQuery.of(context).viewInsets;
    return Padding(
      padding: EdgeInsets.only(
        left: 20,
        right: 20,
        top: 20,
        bottom: viewInsets.bottom + 20,
      ),
      child: Form(
        key: _formKey,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Center(
              child: Container(
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: Colors.grey.shade300,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            const SizedBox(height: 16),
            const Text(
              'Submit Leave Request',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 20),
            // Leave type dropdown
            if (_typesError != null)
              Text('Cannot load leave types: $_typesError',
                  style: const TextStyle(color: Colors.red))
            else if (_leaveTypes == null)
              const Center(child: CircularProgressIndicator())
            else
              DropdownButtonFormField<LeaveType>(
                initialValue: _selectedType,
                decoration: InputDecoration(
                  labelText: 'Leave Type',
                  border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(10)),
                  prefixIcon: const Icon(Icons.category_outlined),
                ),
                items: _leaveTypes!
                    .map((t) => DropdownMenuItem(
                        value: t, child: Text(t.name)))
                    .toList(),
                onChanged: (v) => setState(() => _selectedType = v),
                validator: (v) =>
                    v == null ? 'Please select a leave type' : null,
              ),
            const SizedBox(height: 14),
            // Date row
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
                  child: Text('→'),
                ),
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: () => _pickDate(isStart: false),
                    icon: const Icon(Icons.calendar_today_outlined, size: 16),
                    label: Text(_fmt(_endDate)),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: _endDate == null
                          ? Colors.grey.shade600
                          : brandColor,
                      side: BorderSide(color: Colors.grey.shade300),
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(10)),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),
            // Notes
            TextFormField(
              controller: _notesController,
              maxLines: 3,
              decoration: InputDecoration(
                labelText: 'Notes (optional)',
                border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(10)),
                prefixIcon: const Icon(Icons.notes_outlined),
                alignLabelWithHint: true,
              ),
            ),
            const SizedBox(height: 20),
            // Submit button
            SizedBox(
              width: double.infinity,
              height: 50,
              child: FilledButton(
                onPressed: _submitting ? null : _submit,
                style: FilledButton.styleFrom(
                  backgroundColor: brandColor,
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12)),
                ),
                child: _submitting
                    ? const SizedBox(
                        width: 22,
                        height: 22,
                        child: CircularProgressIndicator(
                            strokeWidth: 2.5, color: Colors.white),
                      )
                    : const Text('Submit Request',
                        style: TextStyle(
                            fontSize: 16, fontWeight: FontWeight.w600)),
              ),
            ),
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
