import 'package:flutter/material.dart';
import '../api/self_api.dart';
import '../models/workflow.dart';

const Color brandColor = Color(0xFF5B3FE5);

class ApprovalsScreen extends StatefulWidget {
  const ApprovalsScreen({super.key, this.onCountChanged});

  final ValueChanged<int>? onCountChanged;

  @override
  State<ApprovalsScreen> createState() => _ApprovalsScreenState();
}

class _ApprovalsScreenState extends State<ApprovalsScreen> {
  late Future<List<WorkflowInstance>> _future;
  int _refreshKey = 0;

  @override
  void initState() {
    super.initState();
    _future = SelfApi.instance.getApprovalInbox();
  }

  void _reload() {
    setState(() {
      _refreshKey++;
      _future = SelfApi.instance.getApprovalInbox();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Approvals',
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
      body: FutureBuilder<List<WorkflowInstance>>(
        key: ValueKey(_refreshKey),
        future: _future,
        builder: (context, snap) {
          if (snap.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snap.hasError) {
            return _ErrorView(
              message: 'Failed to load approval inbox',
              onRetry: _reload,
            );
          }
          final items = snap.data!;
          widget.onCountChanged?.call(items.length);

          if (items.isEmpty) {
            return const Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.task_alt_rounded, size: 56, color: Colors.grey),
                  SizedBox(height: 12),
                  Text('No pending approvals.',
                      style: TextStyle(color: Colors.grey)),
                ],
              ),
            );
          }

          return ListView.builder(
            padding: const EdgeInsets.all(12),
            itemCount: items.length,
            itemBuilder: (context, i) => _ApprovalCard(
              instance: items[i],
              onActed: _reload,
            ),
          );
        },
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Approval card
// ---------------------------------------------------------------------------

class _ApprovalCard extends StatefulWidget {
  const _ApprovalCard({required this.instance, required this.onActed});

  final WorkflowInstance instance;
  final VoidCallback onActed;

  @override
  State<_ApprovalCard> createState() => _ApprovalCardState();
}

class _ApprovalCardState extends State<_ApprovalCard> {
  bool _actioning = false;

  Future<void> _act(String action) async {
    String? comment;

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) {
        final commentCtrl = TextEditingController();
        return AlertDialog(
          shape:
              RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          title: Text(
            action == 'APPROVE' ? 'Approve Request' : 'Reject Request',
            style: TextStyle(
              color: action == 'APPROVE'
                  ? Colors.green.shade700
                  : Colors.red.shade700,
            ),
          ),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                action == 'APPROVE'
                    ? 'Approving: ${widget.instance.definitionName}'
                    : 'Rejecting: ${widget.instance.definitionName}',
                style: const TextStyle(fontSize: 13),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: commentCtrl,
                maxLines: 3,
                decoration: InputDecoration(
                  labelText: 'Comment (optional)',
                  border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(10)),
                ),
                onChanged: (v) => comment = v.isEmpty ? null : v,
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              style: FilledButton.styleFrom(
                backgroundColor: action == 'APPROVE'
                    ? Colors.green.shade700
                    : Colors.red.shade700,
              ),
              child: Text(action == 'APPROVE' ? 'Approve' : 'Reject'),
            ),
          ],
        );
      },
    );

    if (confirmed != true || !mounted) return;

    setState(() => _actioning = true);
    try {
      await SelfApi.instance
          .actOnWorkflow(widget.instance.id, action, comment);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(action == 'APPROVE'
              ? 'Request approved successfully'
              : 'Request rejected'),
          backgroundColor:
              action == 'APPROVE' ? Colors.green : Colors.red.shade700,
        ),
      );
      widget.onActed();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Action failed: $e'),
          backgroundColor: Colors.red.shade700,
        ),
      );
    } finally {
      if (mounted) setState(() => _actioning = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final instance = widget.instance;

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
            // Header row: definition name + entity badge
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        instance.definitionName,
                        style: const TextStyle(
                            fontWeight: FontWeight.bold, fontSize: 15),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        instance.subjectEntity,
                        style: TextStyle(
                            color: Colors.grey.shade500, fontSize: 12),
                      ),
                    ],
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: Colors.orange.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(
                        color: Colors.orange.withValues(alpha: 0.4)),
                  ),
                  child: const Text(
                    'PENDING',
                    style: TextStyle(
                        color: Colors.orange,
                        fontSize: 11,
                        fontWeight: FontWeight.w600),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
            const Divider(height: 1),
            const SizedBox(height: 10),
            // Meta
            Row(
              children: [
                Icon(Icons.person_outline,
                    size: 14, color: Colors.grey.shade400),
                const SizedBox(width: 4),
                Text(
                  instance.initiatedBy,
                  style: TextStyle(
                      color: Colors.grey.shade600, fontSize: 13),
                ),
                const SizedBox(width: 16),
                Icon(Icons.schedule_outlined,
                    size: 14, color: Colors.grey.shade400),
                const SizedBox(width: 4),
                Expanded(
                  child: Text(
                    _fmtDate(instance.initiatedAt),
                    style: TextStyle(
                        color: Colors.grey.shade600, fontSize: 13),
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ],
            ),
            if (instance.currentStepName.isNotEmpty) ...[
              const SizedBox(height: 6),
              Row(
                children: [
                  Icon(Icons.task_outlined,
                      size: 14, color: Colors.grey.shade400),
                  const SizedBox(width: 4),
                  Text(
                    'Step: ${instance.currentStepName}',
                    style: TextStyle(
                        color: Colors.grey.shade500, fontSize: 12),
                  ),
                ],
              ),
            ],
            const SizedBox(height: 14),
            // Action buttons
            _actioning
                ? const Center(
                    child: SizedBox(
                      width: 24,
                      height: 24,
                      child: CircularProgressIndicator(strokeWidth: 2.5),
                    ),
                  )
                : Row(
                    children: [
                      Expanded(
                        child: OutlinedButton.icon(
                          onPressed: () => _act('REJECT'),
                          icon: const Icon(Icons.close_rounded, size: 16),
                          label: const Text('Reject'),
                          style: OutlinedButton.styleFrom(
                            foregroundColor: Colors.red.shade700,
                            side: BorderSide(
                                color: Colors.red.shade300),
                            shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(8)),
                          ),
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: OutlinedButton.icon(
                          onPressed: () => _act('APPROVE'),
                          icon: const Icon(Icons.check_rounded, size: 16),
                          label: const Text('Approve'),
                          style: OutlinedButton.styleFrom(
                            foregroundColor: Colors.green.shade700,
                            side: BorderSide(
                                color: Colors.green.shade300),
                            shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(8)),
                          ),
                        ),
                      ),
                    ],
                  ),
          ],
        ),
      ),
    );
  }

  String _fmtDate(String iso) {
    if (iso.isEmpty) return '';
    try {
      final dt = DateTime.parse(iso).toLocal();
      return '${dt.year}-${dt.month.toString().padLeft(2, '0')}-${dt.day.toString().padLeft(2, '0')}';
    } catch (_) {
      return iso.length > 10 ? iso.substring(0, 10) : iso;
    }
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
