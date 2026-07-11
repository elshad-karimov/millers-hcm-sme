import 'package:flutter/material.dart';
import '../api/self_api.dart';
import '../config/offline_cache.dart';
import '../models/policy.dart';
import '../widgets/common.dart';

/// M500 — policy library + acknowledgement (GET /api/self/policies,
/// POST /api/self/policies/{id}/acknowledge).
class PoliciesScreen extends StatefulWidget {
  const PoliciesScreen({super.key});

  @override
  State<PoliciesScreen> createState() => _PoliciesScreenState();
}

class _PoliciesScreenState extends State<PoliciesScreen> {
  late Future<Cached<List<Policy>>> _future;

  @override
  void initState() {
    super.initState();
    _future = SelfApi.instance.getPoliciesCached();
  }

  void _reload() =>
      setState(() => _future = SelfApi.instance.getPoliciesCached());

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Policies',
            style: TextStyle(fontWeight: FontWeight.bold, color: kBrandColor)),
        actions: [
          IconButton(
              icon: const Icon(Icons.refresh_outlined), onPressed: _reload),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async => _reload(),
        color: kBrandColor,
        child: FutureBuilder<Cached<List<Policy>>>(
          future: _future,
          builder: (context, snap) {
            if (snap.connectionState == ConnectionState.waiting) {
              return const Center(child: CircularProgressIndicator());
            }
            if (snap.hasError) {
              return ErrorRetry(
                  message: 'Failed to load policies', onRetry: _reload);
            }
            final cached = snap.data!;
            final items = cached.data;
            final Widget list = items.isEmpty
                ? ListView(children: const [
                    SizedBox(height: 160),
                    EmptyState(
                        icon: Icons.rule_folder_outlined,
                        message: 'No published policies.'),
                  ])
                : ListView.builder(
                    padding: const EdgeInsets.all(12),
                    itemCount: items.length,
                    itemBuilder: (ctx, i) => _PolicyCard(
                      policy: items[i],
                      onChanged: _reload,
                    ),
                  );
            return Column(
              children: [
                if (cached.fromCache) OfflineBanner(cachedAt: cached.cachedAt),
                Expanded(child: list),
              ],
            );
          },
        ),
      ),
    );
  }
}

class _PolicyCard extends StatelessWidget {
  const _PolicyCard({required this.policy, required this.onChanged});
  final Policy policy;
  final VoidCallback onChanged;

  @override
  Widget build(BuildContext context) {
    final needsAck = policy.requiresAck && !policy.acknowledged;
    return Card(
      elevation: 0,
      color: Colors.grey.shade50,
      margin: const EdgeInsets.only(bottom: 10),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: () async {
          final changed = await Navigator.push<bool>(
            context,
            MaterialPageRoute(builder: (_) => _PolicyDetailScreen(policy: policy)),
          );
          if (changed == true) onChanged();
        },
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(policy.title,
                        style: const TextStyle(
                            fontWeight: FontWeight.bold, fontSize: 15)),
                  ),
                  if (policy.acknowledged)
                    const StatusPill(label: 'ACKNOWLEDGED', color: Colors.green)
                  else if (needsAck)
                    const StatusPill(
                        label: 'ACTION NEEDED', color: Colors.orange),
                ],
              ),
              const SizedBox(height: 6),
              Text('${policy.category}  ·  v${policy.version}',
                  style:
                      TextStyle(color: Colors.grey.shade500, fontSize: 12)),
              if (policy.summary != null && policy.summary!.isNotEmpty) ...[
                const SizedBox(height: 8),
                Text(policy.summary!,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style:
                        TextStyle(color: Colors.grey.shade600, fontSize: 13)),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _PolicyDetailScreen extends StatefulWidget {
  const _PolicyDetailScreen({required this.policy});
  final Policy policy;

  @override
  State<_PolicyDetailScreen> createState() => _PolicyDetailScreenState();
}

class _PolicyDetailScreenState extends State<_PolicyDetailScreen> {
  late bool _acknowledged = widget.policy.acknowledged;
  bool _submitting = false;

  Future<void> _acknowledge() async {
    setState(() => _submitting = true);
    try {
      await SelfApi.instance.acknowledgePolicy(widget.policy.id);
      if (!mounted) return;
      setState(() {
        _acknowledged = true;
        _submitting = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: Text('Policy acknowledged'),
        backgroundColor: Colors.green,
      ));
    } catch (e) {
      if (!mounted) return;
      setState(() => _submitting = false);
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('Failed: $e'),
        backgroundColor: Colors.red.shade700,
      ));
    }
  }

  bool get _changed => _acknowledged != widget.policy.acknowledged;

  @override
  Widget build(BuildContext context) {
    final p = widget.policy;
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (didPop) return;
        Navigator.pop(context, _changed);
      },
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Policy',
              style:
                  TextStyle(fontWeight: FontWeight.bold, color: kBrandColor)),
        ),
        body: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            Text(p.title,
                style:
                    const TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                StatusPill(label: p.category, color: Colors.indigo),
                StatusPill(label: 'v${p.version}', color: Colors.blueGrey),
                if (p.effectiveFrom != null)
                  StatusPill(
                      label: 'From ${shortDate(p.effectiveFrom)}',
                      color: Colors.teal),
              ],
            ),
            if (p.summary != null && p.summary!.isNotEmpty) ...[
              const SizedBox(height: 16),
              Text(p.summary!,
                  style: TextStyle(
                      color: Colors.grey.shade700,
                      fontSize: 14,
                      fontStyle: FontStyle.italic)),
            ],
            const Divider(height: 32),
            Text(
              (p.bodyText == null || p.bodyText!.isEmpty)
                  ? 'The full document is available as an attachment. Contact HR if you cannot open it.'
                  : p.bodyText!,
              style: const TextStyle(fontSize: 15, height: 1.5),
            ),
            const SizedBox(height: 32),
            if (_acknowledged)
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: Colors.green.withValues(alpha: 0.08),
                  borderRadius: BorderRadius.circular(12),
                  border:
                      Border.all(color: Colors.green.withValues(alpha: 0.3)),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.check_circle,
                        color: Colors.green, size: 22),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        p.acknowledgedAt != null
                            ? 'Acknowledged on ${shortDate(p.acknowledgedAt)}'
                            : 'You have acknowledged this policy.',
                        style: TextStyle(
                            color: Colors.green.shade800,
                            fontWeight: FontWeight.w600),
                      ),
                    ),
                  ],
                ),
              )
            else if (p.requiresAck)
              SizedBox(
                width: double.infinity,
                height: 50,
                child: FilledButton.icon(
                  onPressed: _submitting ? null : _acknowledge,
                  style: FilledButton.styleFrom(
                      backgroundColor: kBrandColor,
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12))),
                  icon: _submitting
                      ? const SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(
                              strokeWidth: 2.5, color: Colors.white))
                      : const Icon(Icons.verified_outlined),
                  label: Text(_submitting ? 'Submitting…' : 'Acknowledge',
                      style: const TextStyle(
                          fontSize: 16, fontWeight: FontWeight.w600)),
                ),
              )
            else
              Text('This policy does not require acknowledgement.',
                  style: TextStyle(color: Colors.grey.shade500)),
          ],
        ),
      ),
    );
  }
}
