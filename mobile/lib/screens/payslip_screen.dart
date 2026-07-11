import 'package:flutter/material.dart';
import '../api/self_api.dart';
import '../auth/biometric_service.dart';
import '../config/secure_screen.dart';
import '../models/workflow.dart';

const Color brandColor = Color(0xFF5B3FE5);

class PayslipScreen extends StatefulWidget {
  const PayslipScreen({super.key});

  @override
  State<PayslipScreen> createState() => _PayslipScreenState();
}

class _PayslipScreenState extends State<PayslipScreen> {
  late Future<List<Payslip>> _future;

  // M509 — re-auth gate before payslip amounts are shown.
  bool _unlocked = false;
  bool _authInProgress = false;
  String? _authError;

  @override
  void initState() {
    super.initState();
    _future = SelfApi.instance.getPayslips();
    // Block screenshots / screen recording while payslip amounts are on screen.
    SecureScreen.enable();
    _unlocked = ReauthGate.payslip.isValid;
    if (!_unlocked) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _authenticate());
    }
  }

  @override
  void dispose() {
    SecureScreen.disable();
    super.dispose();
  }

  Future<void> _authenticate() async {
    if (_authInProgress) return;
    setState(() {
      _authInProgress = true;
      _authError = null;
    });
    final ok = await BiometricService.instance
        .authenticateSensitive('Verify it\'s you to view payslip amounts');
    if (!mounted) return;
    setState(() {
      _authInProgress = false;
      if (ok) {
        ReauthGate.payslip.markAuthenticated();
        _unlocked = true;
      } else {
        _authError = 'Authentication required to view payslips.';
      }
    });
  }

  void _reload() => setState(() => _future = SelfApi.instance.getPayslips());

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Payslips',
          style: TextStyle(fontWeight: FontWeight.bold, color: brandColor),
        ),
        actions: [
          if (_unlocked)
            IconButton(
              icon: const Icon(Icons.refresh_outlined),
              onPressed: _reload,
              tooltip: 'Refresh',
            ),
        ],
      ),
      body: _unlocked ? _payslipList() : _lockView(),
    );
  }

  Widget _lockView() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.lock_outline, size: 56, color: brandColor),
            const SizedBox(height: 16),
            const Text('Payslips are protected',
                style: TextStyle(fontSize: 17, fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            Text(
              _authError ??
                  'Verify your identity to view your salary details.',
              textAlign: TextAlign.center,
              style: TextStyle(color: Colors.grey.shade600),
            ),
            const SizedBox(height: 24),
            FilledButton.icon(
              onPressed: _authInProgress ? null : _authenticate,
              icon: _authInProgress
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: Colors.white))
                  : const Icon(Icons.fingerprint),
              label: const Text('Unlock'),
              style: FilledButton.styleFrom(backgroundColor: brandColor),
            ),
          ],
        ),
      ),
    );
  }

  Widget _payslipList() {
    return FutureBuilder<List<Payslip>>(
        future: _future,
        builder: (context, snap) {
          if (snap.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snap.hasError) {
            return _ErrorView(
              message: 'Failed to load payslips',
              onRetry: _reload,
            );
          }
          final payslips = snap.data!;
          if (payslips.isEmpty) {
            return const Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.receipt_long_outlined,
                      size: 56, color: Colors.grey),
                  SizedBox(height: 12),
                  Text('No payslips available yet.',
                      style: TextStyle(color: Colors.grey)),
                ],
              ),
            );
          }
          return ListView.builder(
            padding: const EdgeInsets.all(12),
            itemCount: payslips.length,
            itemBuilder: (context, i) =>
                _PayslipExpansionTile(payslip: payslips[i]),
          );
        },
      );
  }
}

class _PayslipExpansionTile extends StatelessWidget {
  const _PayslipExpansionTile({required this.payslip});

  final Payslip payslip;

  Color _statusColor(String s) {
    switch (s.toUpperCase()) {
      case 'FINALISED':
      case 'FINALIZED':
      case 'PAID':
        return Colors.green;
      case 'DRAFT':
        return Colors.orange;
      case 'VOID':
        return Colors.red;
      default:
        return Colors.blueGrey;
    }
  }

  String _fmt(double v) {
    // Format with comma-separated thousands, 2 decimal places
    final parts = v.toStringAsFixed(2).split('.');
    final intPart = parts[0];
    final decPart = parts[1];
    final buf = StringBuffer();
    int count = 0;
    for (int i = intPart.length - 1; i >= 0; i--) {
      if (count > 0 && count % 3 == 0) buf.write(',');
      buf.write(intPart[i]);
      count++;
    }
    return '${buf.toString().split('').reversed.join()}.$decPart';
  }

  @override
  Widget build(BuildContext context) {
    final statusColor = _statusColor(payslip.status);

    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      shape:
          RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      elevation: 0,
      color: Colors.grey.shade50,
      child: ExpansionTile(
        tilePadding:
            const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
        childrenPadding:
            const EdgeInsets.fromLTRB(16, 0, 16, 16),
        shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12)),
        collapsedShape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12)),
        title: Row(
          children: [
            Expanded(
              child: Text(
                payslip.period,
                style: const TextStyle(
                    fontWeight: FontWeight.bold, fontSize: 15),
              ),
            ),
            Container(
              padding:
                  const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: BoxDecoration(
                color: statusColor.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(20),
                border:
                    Border.all(color: statusColor.withValues(alpha: 0.4)),
              ),
              child: Text(
                payslip.status,
                style: TextStyle(
                    color: statusColor,
                    fontSize: 11,
                    fontWeight: FontWeight.w600),
              ),
            ),
          ],
        ),
        subtitle: Padding(
          padding: const EdgeInsets.only(top: 4, bottom: 2),
          child: Text(
            'Gross: ${_fmt(payslip.grossAmount)}  ·  Net: ${_fmt(payslip.netAmount)}',
            style: TextStyle(
                color: Colors.grey.shade600, fontSize: 13),
          ),
        ),
        children: [
          const Divider(height: 1),
          const SizedBox(height: 14),
          Row(
            children: [
              _BreakdownColumn(
                label: 'Gross',
                amount: payslip.grossAmount,
                color: brandColor,
                fmt: _fmt,
              ),
              _BreakdownColumn(
                label: 'Income Tax',
                amount: payslip.incomeTax,
                color: Colors.red.shade600,
                fmt: _fmt,
              ),
              _BreakdownColumn(
                label: 'Net',
                amount: payslip.netAmount,
                color: Colors.green.shade700,
                fmt: _fmt,
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _BreakdownColumn extends StatelessWidget {
  const _BreakdownColumn({
    required this.label,
    required this.amount,
    required this.color,
    required this.fmt,
  });

  final String label;
  final double amount;
  final Color color;
  final String Function(double) fmt;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Column(
        children: [
          Text(
            label,
            style: TextStyle(
                color: Colors.grey.shade500, fontSize: 11),
          ),
          const SizedBox(height: 4),
          Text(
            fmt(amount),
            style: TextStyle(
              color: color,
              fontWeight: FontWeight.bold,
              fontSize: 14,
            ),
          ),
        ],
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
