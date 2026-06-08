import 'package:flutter/material.dart';
import '../api/self_api.dart';
import '../models/training.dart';

const Color brandColor = Color(0xFF5B3FE5);

/// Employee training screen — two tabs: My Courses + My Certificates (§11.2 / §8.14.8).
class TrainingScreen extends StatelessWidget {
  const TrainingScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Training',
              style:
                  TextStyle(fontWeight: FontWeight.bold, color: brandColor)),
          bottom: TabBar(
            labelColor: brandColor,
            indicatorColor: brandColor,
            tabs: const [
              Tab(text: 'My Courses', icon: Icon(Icons.school_outlined)),
              Tab(
                  text: 'Certificates',
                  icon: Icon(Icons.workspace_premium_outlined)),
            ],
          ),
        ),
        body: const TabBarView(
          children: [
            _CoursesTab(),
            _CertificatesTab(),
          ],
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Courses tab — join enrollments with course catalogue
// ---------------------------------------------------------------------------

class _CoursesTab extends StatelessWidget {
  const _CoursesTab();

  Future<_CoursesData> _load() async {
    final results = await Future.wait([
      SelfApi.instance.getEnrollments(),
      CourseApi.instance.list(),
    ]);
    final enrollments = results[0] as List<Enrollment>;
    final courses = results[1] as List<Course>;
    final courseMap = {for (final c in courses) c.id: c};
    return _CoursesData(enrollments: enrollments, courseMap: courseMap);
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<_CoursesData>(
      future: _load(),
      builder: (context, snap) {
        if (snap.connectionState == ConnectionState.waiting) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snap.hasError) {
          return _ErrorView(
            message: 'Failed to load courses',
            onRetry: () => (context as Element).markNeedsBuild(),
          );
        }
        final data = snap.data!;
        if (data.enrollments.isEmpty) {
          return const Center(child: Text('No courses assigned yet.'));
        }
        return ListView.builder(
          padding: const EdgeInsets.all(12),
          itemCount: data.enrollments.length,
          itemBuilder: (ctx, i) {
            final e = data.enrollments[i];
            final course = data.courseMap[e.courseId];
            return _EnrollmentCard(enrollment: e, course: course);
          },
        );
      },
    );
  }
}

class _CoursesData {
  final List<Enrollment> enrollments;
  final Map<String, Course> courseMap;
  const _CoursesData({required this.enrollments, required this.courseMap});
}

class _EnrollmentCard extends StatelessWidget {
  const _EnrollmentCard({required this.enrollment, required this.course});

  final Enrollment enrollment;
  final Course? course;

  Color _statusColor(String s) {
    switch (s) {
      case 'PASSED':
        return Colors.green;
      case 'FAILED':
        return Colors.red;
      case 'IN_PROGRESS':
        return Colors.blue;
      case 'EXPIRED':
        return Colors.orange;
      case 'WITHDRAWN':
        return Colors.grey;
      default:
        return Colors.blueGrey;
    }
  }

  @override
  Widget build(BuildContext context) {
    final sc = _statusColor(enrollment.status);
    final title = course?.title ?? enrollment.courseId;
    final category = course?.category ?? '';

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
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // icon
                Container(
                  width: 44,
                  height: 44,
                  decoration: BoxDecoration(
                    color: brandColor.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: const Icon(Icons.school_rounded,
                      color: brandColor, size: 24),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(title,
                          style: const TextStyle(
                              fontWeight: FontWeight.bold, fontSize: 14),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis),
                      if (category.isNotEmpty)
                        Text(category,
                            style: TextStyle(
                                color: Colors.grey.shade500, fontSize: 12)),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                _StatusChip(label: enrollment.status, color: sc),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                if (enrollment.dueDate != null) ...[
                  Icon(Icons.calendar_today_outlined,
                      size: 13, color: Colors.grey.shade500),
                  const SizedBox(width: 4),
                  Text('Due ${enrollment.dueDate}',
                      style: TextStyle(
                          color: Colors.grey.shade600, fontSize: 12)),
                  const SizedBox(width: 16),
                ],
                if (enrollment.bestScorePercent != null) ...[
                  Icon(Icons.bar_chart_rounded,
                      size: 13, color: Colors.grey.shade500),
                  const SizedBox(width: 4),
                  Text(
                      'Score ${enrollment.bestScorePercent!.toStringAsFixed(1)} %',
                      style: TextStyle(
                          color: Colors.grey.shade600, fontSize: 12)),
                ],
              ],
            ),
            if (course != null && !enrollment.isCompleted) ...[
              const SizedBox(height: 12),
              ClipRRect(
                borderRadius: BorderRadius.circular(4),
                child: LinearProgressIndicator(
                  value: enrollment.status == 'IN_PROGRESS' ? 0.5 : 0.0,
                  minHeight: 6,
                  backgroundColor: Colors.grey.shade200,
                  color: brandColor,
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Certificates tab
// ---------------------------------------------------------------------------

class _CertificatesTab extends StatelessWidget {
  const _CertificatesTab();

  Future<_CertsData> _load() async {
    final results = await Future.wait([
      SelfApi.instance.getCertificates(),
      CourseApi.instance.list(),
    ]);
    final certs = results[0] as List<Certificate>;
    final courses = results[1] as List<Course>;
    final courseMap = {for (final c in courses) c.id: c};
    return _CertsData(certs: certs, courseMap: courseMap);
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<_CertsData>(
      future: _load(),
      builder: (context, snap) {
        if (snap.connectionState == ConnectionState.waiting) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snap.hasError) {
          return _ErrorView(
            message: 'Failed to load certificates',
            onRetry: () => (context as Element).markNeedsBuild(),
          );
        }
        final data = snap.data!;
        final active = data.certs.where((c) => !c.revoked).toList();
        if (active.isEmpty) {
          return const Center(child: Text('No certificates earned yet.'));
        }
        return ListView.builder(
          padding: const EdgeInsets.all(12),
          itemCount: active.length,
          itemBuilder: (ctx, i) {
            final cert = active[i];
            final course = data.courseMap[cert.courseId];
            return _CertCard(cert: cert, courseTitle: course?.title);
          },
        );
      },
    );
  }
}

class _CertsData {
  final List<Certificate> certs;
  final Map<String, Course> courseMap;
  const _CertsData({required this.certs, required this.courseMap});
}

class _CertCard extends StatelessWidget {
  const _CertCard({required this.cert, this.courseTitle});

  final Certificate cert;
  final String? courseTitle;

  bool get _isExpired {
    if (cert.validUntil == null) return false;
    return DateTime.tryParse(cert.validUntil!)?.isBefore(DateTime.now()) ??
        false;
  }

  @override
  Widget build(BuildContext context) {
    final expired = _isExpired;
    return Card(
      elevation: 0,
      color: expired ? Colors.red.shade50 : Colors.green.shade50,
      margin: const EdgeInsets.only(bottom: 10),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Icon(
              Icons.workspace_premium_rounded,
              color: expired ? Colors.red.shade300 : Colors.green.shade600,
              size: 40,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    courseTitle ?? cert.certificateNo,
                    style: const TextStyle(
                        fontWeight: FontWeight.bold, fontSize: 14),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    cert.certificateNo,
                    style: TextStyle(
                        color: Colors.grey.shade600, fontSize: 12),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    'Issued ${cert.issuedAt.substring(0, 10)}'
                    '${cert.validUntil != null ? ' · Valid until ${cert.validUntil}' : ''}',
                    style: TextStyle(
                        color: expired
                            ? Colors.red.shade400
                            : Colors.grey.shade500,
                        fontSize: 11),
                  ),
                  if (cert.scorePercent != null)
                    Text(
                      'Score: ${cert.scorePercent!.toStringAsFixed(1)} %',
                      style: TextStyle(
                          color: Colors.grey.shade500, fontSize: 11),
                    ),
                ],
              ),
            ),
            if (expired)
              const Icon(Icons.warning_amber_rounded,
                  color: Colors.red, size: 20),
          ],
        ),
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
