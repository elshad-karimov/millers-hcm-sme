import 'package:flutter/material.dart';
import '../api/self_api.dart';
import '../models/performance.dart';

const Color brandColor = Color(0xFF5B3FE5);

/// Performance goals screen — goals + review history (§11.2).
class GoalsScreen extends StatelessWidget {
  const GoalsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Performance',
              style:
                  TextStyle(fontWeight: FontWeight.bold, color: brandColor)),
          bottom: TabBar(
            labelColor: brandColor,
            indicatorColor: brandColor,
            tabs: const [
              Tab(text: 'My Goals', icon: Icon(Icons.track_changes_outlined)),
              Tab(
                  text: 'Reviews',
                  icon: Icon(Icons.rate_review_outlined)),
            ],
          ),
        ),
        body: const TabBarView(
          children: [
            _GoalsTab(),
            _ReviewsTab(),
          ],
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Goals tab
// ---------------------------------------------------------------------------

class _GoalsTab extends StatelessWidget {
  const _GoalsTab();

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<List<Goal>>(
      future: SelfApi.instance.getGoals(),
      builder: (context, snap) {
        if (snap.connectionState == ConnectionState.waiting) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snap.hasError) {
          return _ErrorView(
            message: 'Failed to load goals',
            onRetry: () => (context as Element).markNeedsBuild(),
          );
        }
        final goals = snap.data!;
        if (goals.isEmpty) {
          return const Center(child: Text('No goals assigned yet.'));
        }
        return ListView.builder(
          padding: const EdgeInsets.all(12),
          itemCount: goals.length,
          itemBuilder: (ctx, i) => _GoalCard(goal: goals[i]),
        );
      },
    );
  }
}

class _GoalCard extends StatelessWidget {
  const _GoalCard({required this.goal});
  final Goal goal;

  Color _statusColor(String s) {
    switch (s) {
      case 'ACHIEVED':
        return Colors.green;
      case 'NOT_ACHIEVED':
        return Colors.red;
      case 'IN_PROGRESS':
        return Colors.blue;
      case 'OVERDUE':
        return Colors.orange;
      default:
        return Colors.grey;
    }
  }

  @override
  Widget build(BuildContext context) {
    final sc = _statusColor(goal.status);
    final progress = (goal.progressPercent / 100).clamp(0.0, 1.0);

    return Card(
      elevation: 0,
      color: Colors.grey.shade50,
      margin: const EdgeInsets.only(bottom: 10),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(goal.title,
                      style: const TextStyle(
                          fontWeight: FontWeight.bold, fontSize: 14),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis),
                ),
                const SizedBox(width: 8),
                _StatusChip(label: goal.status, color: sc),
              ],
            ),
            const SizedBox(height: 4),
            Row(
              children: [
                Text(goal.category,
                    style: TextStyle(
                        color: Colors.grey.shade500, fontSize: 12)),
                if (goal.dueDate != null) ...[
                  const SizedBox(width: 12),
                  Icon(Icons.schedule_outlined,
                      size: 12, color: Colors.grey.shade500),
                  const SizedBox(width: 2),
                  Text(goal.dueDate!,
                      style: TextStyle(
                          color: Colors.grey.shade500, fontSize: 12)),
                ],
              ],
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '${goal.progressPercent.toStringAsFixed(0)} %',
                        style: TextStyle(
                            fontWeight: FontWeight.bold,
                            fontSize: 13,
                            color: progress >= 1.0
                                ? Colors.green
                                : brandColor),
                      ),
                      const SizedBox(height: 4),
                      ClipRRect(
                        borderRadius: BorderRadius.circular(4),
                        child: LinearProgressIndicator(
                          value: progress,
                          minHeight: 6,
                          backgroundColor: Colors.grey.shade200,
                          color: progress >= 1.0
                              ? Colors.green
                              : brandColor,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 16),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Text('Weight',
                        style: TextStyle(
                            color: Colors.grey.shade500, fontSize: 11)),
                    Text('${goal.weightPercent.toStringAsFixed(0)} %',
                        style: const TextStyle(
                            fontWeight: FontWeight.w600, fontSize: 13)),
                  ],
                ),
              ],
            ),
            if (goal.rating != null) ...[
              const SizedBox(height: 8),
              Row(
                children: [
                  ...List.generate(
                    5,
                    (i) => Icon(
                      i < goal.rating! ? Icons.star_rounded : Icons.star_outline_rounded,
                      size: 18,
                      color: Colors.amber,
                    ),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Reviews tab
// ---------------------------------------------------------------------------

class _ReviewsTab extends StatelessWidget {
  const _ReviewsTab();

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<List<PerformanceReview>>(
      future: SelfApi.instance.getReviews(),
      builder: (context, snap) {
        if (snap.connectionState == ConnectionState.waiting) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snap.hasError) {
          return _ErrorView(
            message: 'Failed to load reviews',
            onRetry: () => (context as Element).markNeedsBuild(),
          );
        }
        final reviews = snap.data!;
        if (reviews.isEmpty) {
          return const Center(child: Text('No performance reviews yet.'));
        }
        return ListView.builder(
          padding: const EdgeInsets.all(12),
          itemCount: reviews.length,
          itemBuilder: (ctx, i) => _ReviewCard(review: reviews[i]),
        );
      },
    );
  }
}

class _ReviewCard extends StatelessWidget {
  const _ReviewCard({required this.review});
  final PerformanceReview review;

  Color _statusColor(String s) {
    switch (s.toUpperCase()) {
      case 'COMPLETED':
        return Colors.green;
      case 'IN_PROGRESS':
        return Colors.blue;
      case 'PENDING':
        return Colors.orange;
      default:
        return Colors.grey;
    }
  }

  @override
  Widget build(BuildContext context) {
    final sc = _statusColor(review.status);
    return Card(
      elevation: 0,
      color: Colors.grey.shade50,
      margin: const EdgeInsets.only(bottom: 10),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: ListTile(
        contentPadding:
            const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
        leading: const CircleAvatar(
          backgroundColor: Color(0x1A5B3FE5),
          child: Icon(Icons.rate_review_rounded, color: brandColor, size: 22),
        ),
        title: Text(review.cycleName,
            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(review.reviewNo,
                style: TextStyle(
                    color: Colors.grey.shade500, fontSize: 12)),
            if (review.overallRating != null)
              Row(
                children: [
                  ...List.generate(
                    5,
                    (i) => Icon(
                      i < (review.overallRating ?? 0).round()
                          ? Icons.star_rounded
                          : Icons.star_outline_rounded,
                      size: 14,
                      color: Colors.amber,
                    ),
                  ),
                  const SizedBox(width: 4),
                  Text(
                    review.overallRating!.toStringAsFixed(1),
                    style: const TextStyle(fontSize: 12),
                  ),
                ],
              ),
          ],
        ),
        trailing: _StatusChip(label: review.status, color: sc),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Shared widgets
// ---------------------------------------------------------------------------

class _StatusChip extends StatelessWidget {
  const _StatusChip({required this.label, required this.color});
  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: color.withValues(alpha: 0.4)),
      ),
      child: Text(
        label,
        style: TextStyle(
            color: color, fontSize: 10, fontWeight: FontWeight.w600),
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
