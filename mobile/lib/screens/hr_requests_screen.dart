import 'package:flutter/material.dart';
import '../api/self_api.dart';
import '../models/hr_request.dart';
import '../widgets/common.dart';

/// M501 — HR service requests (GET/POST /api/self/hr-requests).
class HrRequestsScreen extends StatefulWidget {
  const HrRequestsScreen({super.key});

  @override
  State<HrRequestsScreen> createState() => _HrRequestsScreenState();
}

class _HrRequestsScreenState extends State<HrRequestsScreen> {
  late Future<List<HrRequest>> _future;

  @override
  void initState() {
    super.initState();
    _future = SelfApi.instance.getHrRequests();
  }

  void _reload() => setState(() => _future = SelfApi.instance.getHrRequests());

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('HR Requests',
            style: TextStyle(fontWeight: FontWeight.bold, color: kBrandColor)),
        actions: [
          IconButton(
              icon: const Icon(Icons.refresh_outlined), onPressed: _reload),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async => _reload(),
        color: kBrandColor,
        child: FutureBuilder<List<HrRequest>>(
          future: _future,
          builder: (context, snap) {
            if (snap.connectionState == ConnectionState.waiting) {
              return const Center(child: CircularProgressIndicator());
            }
            if (snap.hasError) {
              return ErrorRetry(
                  message: 'Failed to load requests', onRetry: _reload);
            }
            final items = snap.data!;
            if (items.isEmpty) {
              return ListView(children: const [
                SizedBox(height: 160),
                EmptyState(
                    icon: Icons.support_agent_outlined,
                    message: 'No HR requests yet.'),
              ]);
            }
            return ListView.builder(
              padding: const EdgeInsets.all(12),
              itemCount: items.length,
              itemBuilder: (ctx, i) => _RequestCard(
                request: items[i],
                onChanged: _reload,
              ),
            );
          },
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        backgroundColor: kBrandColor,
        foregroundColor: Colors.white,
        icon: const Icon(Icons.add),
        label: const Text('New request'),
        onPressed: () async {
          final submitted = await showModalBottomSheet<bool>(
            context: context,
            isScrollControlled: true,
            shape: const RoundedRectangleBorder(
                borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
            builder: (_) => const _SubmitRequestSheet(),
          );
          if (submitted == true) _reload();
        },
      ),
    );
  }
}

Color statusColor(String s) {
  switch (s.toUpperCase()) {
    case 'RESOLVED':
      return Colors.green;
    case 'CLOSED':
      return Colors.blueGrey;
    case 'IN_PROGRESS':
      return Colors.blue;
    default:
      return Colors.orange;
  }
}

Color priorityColor(String s) {
  switch (s.toUpperCase()) {
    case 'HIGH':
      return Colors.red;
    case 'LOW':
      return Colors.grey;
    default:
      return Colors.teal;
  }
}

String prettyEnum(String s) =>
    s.replaceAll('_', ' ').toLowerCase().replaceFirstMapped(
        RegExp(r'^\w'), (m) => m.group(0)!.toUpperCase());

class _RequestCard extends StatelessWidget {
  const _RequestCard({required this.request, required this.onChanged});
  final HrRequest request;
  final VoidCallback onChanged;

  @override
  Widget build(BuildContext context) {
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
              builder: (_) => _RequestDetailScreen(request: request)),
        ),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(request.subject,
                        style: const TextStyle(
                            fontWeight: FontWeight.bold, fontSize: 15)),
                  ),
                  StatusPill(
                      label: prettyEnum(request.status),
                      color: statusColor(request.status)),
                ],
              ),
              const SizedBox(height: 6),
              Row(
                children: [
                  Text(request.requestNo,
                      style: TextStyle(
                          color: Colors.grey.shade500, fontSize: 12)),
                  const SizedBox(width: 8),
                  StatusPill(
                      label: prettyEnum(request.category),
                      color: Colors.indigo),
                  const SizedBox(width: 6),
                  StatusPill(
                      label: request.priority,
                      color: priorityColor(request.priority)),
                ],
              ),
              if (request.slaDue != null) ...[
                const SizedBox(height: 8),
                Row(
                  children: [
                    Icon(Icons.timer_outlined,
                        size: 14, color: Colors.grey.shade400),
                    const SizedBox(width: 4),
                    Text('SLA due ${shortDate(request.slaDue)}',
                        style: TextStyle(
                            color: Colors.grey.shade500, fontSize: 12)),
                  ],
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Request detail + comment thread
// ---------------------------------------------------------------------------

class _RequestDetailScreen extends StatefulWidget {
  const _RequestDetailScreen({required this.request});
  final HrRequest request;

  @override
  State<_RequestDetailScreen> createState() => _RequestDetailScreenState();
}

class _RequestDetailScreenState extends State<_RequestDetailScreen> {
  late Future<List<HrRequestComment>> _comments;
  final _commentController = TextEditingController();
  bool _sending = false;

  @override
  void initState() {
    super.initState();
    _comments = SelfApi.instance.getHrRequestComments(widget.request.id);
  }

  @override
  void dispose() {
    _commentController.dispose();
    super.dispose();
  }

  void _reloadComments() => setState(() =>
      _comments = SelfApi.instance.getHrRequestComments(widget.request.id));

  Future<void> _send() async {
    final body = _commentController.text.trim();
    if (body.isEmpty) return;
    setState(() => _sending = true);
    try {
      await SelfApi.instance.addHrRequestComment(widget.request.id, body);
      _commentController.clear();
      if (!mounted) return;
      _reloadComments();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('Failed: $e'),
        backgroundColor: Colors.red.shade700,
      ));
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final r = widget.request;
    return Scaffold(
      appBar: AppBar(
        title: Text(r.requestNo,
            style:
                const TextStyle(fontWeight: FontWeight.bold, color: kBrandColor)),
      ),
      body: Column(
        children: [
          Expanded(
            child: ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Text(r.subject,
                    style: const TextStyle(
                        fontSize: 18, fontWeight: FontWeight.bold)),
                const SizedBox(height: 10),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    StatusPill(
                        label: prettyEnum(r.status),
                        color: statusColor(r.status)),
                    StatusPill(
                        label: prettyEnum(r.category), color: Colors.indigo),
                    StatusPill(
                        label: r.priority, color: priorityColor(r.priority)),
                  ],
                ),
                if (r.description != null && r.description!.isNotEmpty) ...[
                  const SizedBox(height: 16),
                  Text(r.description!,
                      style: const TextStyle(fontSize: 15, height: 1.4)),
                ],
                if (r.resolutionNotes != null &&
                    r.resolutionNotes!.isNotEmpty) ...[
                  const SizedBox(height: 16),
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: Colors.green.withValues(alpha: 0.08),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text('Resolution',
                            style: TextStyle(
                                fontWeight: FontWeight.bold, fontSize: 13)),
                        const SizedBox(height: 4),
                        Text(r.resolutionNotes!,
                            style: const TextStyle(fontSize: 14)),
                      ],
                    ),
                  ),
                ],
                const Divider(height: 32),
                const Text('Comments',
                    style:
                        TextStyle(fontWeight: FontWeight.bold, fontSize: 15)),
                const SizedBox(height: 8),
                FutureBuilder<List<HrRequestComment>>(
                  future: _comments,
                  builder: (context, snap) {
                    if (snap.connectionState == ConnectionState.waiting) {
                      return const Padding(
                        padding: EdgeInsets.all(16),
                        child: Center(child: CircularProgressIndicator()),
                      );
                    }
                    if (snap.hasError) {
                      return Text('Could not load comments.',
                          style: TextStyle(color: Colors.grey.shade500));
                    }
                    final comments = snap.data!;
                    if (comments.isEmpty) {
                      return Text('No comments yet.',
                          style: TextStyle(color: Colors.grey.shade500));
                    }
                    return Column(
                      children: comments
                          .map((c) => _CommentBubble(comment: c))
                          .toList(),
                    );
                  },
                ),
              ],
            ),
          ),
          SafeArea(
            top: false,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(12, 4, 12, 8),
              child: Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _commentController,
                      minLines: 1,
                      maxLines: 4,
                      decoration: InputDecoration(
                        hintText: 'Add a comment…',
                        border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(24)),
                        contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16, vertical: 10),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  IconButton.filled(
                    style: IconButton.styleFrom(backgroundColor: kBrandColor),
                    onPressed: _sending ? null : _send,
                    icon: _sending
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(
                                strokeWidth: 2, color: Colors.white))
                        : const Icon(Icons.send_rounded),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _CommentBubble extends StatelessWidget {
  const _CommentBubble({required this.comment});
  final HrRequestComment comment;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.grey.shade50,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.person_outline, size: 16, color: Colors.grey.shade500),
              const SizedBox(width: 6),
              Text(comment.authorUsername,
                  style: const TextStyle(
                      fontWeight: FontWeight.w600, fontSize: 12)),
              const Spacer(),
              Text(shortDate(comment.createdAt),
                  style: TextStyle(color: Colors.grey.shade400, fontSize: 11)),
            ],
          ),
          const SizedBox(height: 6),
          Text(comment.body, style: const TextStyle(fontSize: 14)),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Submit request bottom sheet
// ---------------------------------------------------------------------------

class _SubmitRequestSheet extends StatefulWidget {
  const _SubmitRequestSheet();

  @override
  State<_SubmitRequestSheet> createState() => _SubmitRequestSheetState();
}

class _SubmitRequestSheetState extends State<_SubmitRequestSheet> {
  static const _categories = <String>[
    'SALARY_CERT',
    'EMPLOYMENT_LETTER',
    'PAYROLL_INQUIRY',
    'POLICY_QUESTION',
    'GRIEVANCE',
    'DOCUMENT_RENEWAL',
    'OTHER',
  ];
  static const _priorities = <String>['LOW', 'NORMAL', 'HIGH'];

  String _category = 'SALARY_CERT';
  String _priority = 'NORMAL';
  final _subjectController = TextEditingController();
  final _descController = TextEditingController();
  bool _submitting = false;

  @override
  void dispose() {
    _subjectController.dispose();
    _descController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_subjectController.text.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Enter a subject')));
      return;
    }
    setState(() => _submitting = true);
    try {
      await SelfApi.instance.submitHrRequest(
        category: _category,
        priority: _priority,
        subject: _subjectController.text.trim(),
        description: _descController.text.trim(),
      );
      if (!mounted) return;
      Navigator.pop(context, true);
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: Text('HR request submitted'),
        backgroundColor: Colors.green,
      ));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('Failed: $e'),
        backgroundColor: Colors.red.shade700,
      ));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final bottom = MediaQuery.of(context).viewInsets.bottom;
    return Padding(
      padding: EdgeInsets.fromLTRB(20, 20, 20, bottom + 20),
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
                  borderRadius: BorderRadius.circular(2)),
            ),
          ),
          const SizedBox(height: 16),
          const Text('New HR Request',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          const SizedBox(height: 16),
          DropdownButtonFormField<String>(
            initialValue: _category,
            decoration: InputDecoration(
              labelText: 'Category',
              border:
                  OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
            ),
            items: _categories
                .map((c) =>
                    DropdownMenuItem(value: c, child: Text(prettyEnum(c))))
                .toList(),
            onChanged: (v) => setState(() => _category = v!),
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<String>(
            initialValue: _priority,
            decoration: InputDecoration(
              labelText: 'Priority',
              border:
                  OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
            ),
            items: _priorities
                .map((p) =>
                    DropdownMenuItem(value: p, child: Text(prettyEnum(p))))
                .toList(),
            onChanged: (v) => setState(() => _priority = v!),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _subjectController,
            decoration: InputDecoration(
              labelText: 'Subject',
              border:
                  OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
              prefixIcon: const Icon(Icons.subject_outlined),
            ),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _descController,
            maxLines: 3,
            decoration: InputDecoration(
              labelText: 'Description (optional)',
              border:
                  OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
              alignLabelWithHint: true,
            ),
          ),
          const SizedBox(height: 20),
          SizedBox(
            width: double.infinity,
            height: 50,
            child: FilledButton(
              onPressed: _submitting ? null : _submit,
              style: FilledButton.styleFrom(
                  backgroundColor: kBrandColor,
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12))),
              child: _submitting
                  ? const SizedBox(
                      width: 22,
                      height: 22,
                      child: CircularProgressIndicator(
                          strokeWidth: 2.5, color: Colors.white))
                  : const Text('Submit',
                      style: TextStyle(
                          fontSize: 16, fontWeight: FontWeight.w600)),
            ),
          ),
        ],
      ),
    );
  }
}
