import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';

import '../auth/auth_service.dart';
import '../config/app_config.dart';

/// Millers brand purple — shared across screens.
const Color kBrandColor = Color(0xFF5B3FE5);

/// Circular avatar that loads an attachment thumbnail (with the Bearer token)
/// and falls back to the given [initials] on error / when [attachmentId] is null.
class AttachmentAvatar extends StatelessWidget {
  const AttachmentAvatar({
    super.key,
    required this.attachmentId,
    required this.initials,
    this.radius = 24,
    this.background = kBrandColor,
  });

  final String? attachmentId;
  final String initials;
  final double radius;
  final Color background;

  @override
  Widget build(BuildContext context) {
    final fallback = CircleAvatar(
      radius: radius,
      backgroundColor: background.withValues(alpha: 0.15),
      child: Text(initials,
          style: TextStyle(
              color: background,
              fontWeight: FontWeight.bold,
              fontSize: radius * 0.6)),
    );
    if (attachmentId == null || attachmentId!.isEmpty) return fallback;

    return FutureBuilder<String?>(
      future: AuthService.instance.getAccessToken(),
      builder: (context, snap) {
        final token = snap.data;
        if (token == null) return fallback;
        final url =
            '${AppConfig.apiBaseUrl}/attachments/$attachmentId/thumbnail';
        return CircleAvatar(
          radius: radius,
          backgroundColor: background.withValues(alpha: 0.15),
          child: ClipOval(
            child: CachedNetworkImage(
              imageUrl: url,
              httpHeaders: {'Authorization': 'Bearer $token'},
              width: radius * 2,
              height: radius * 2,
              fit: BoxFit.cover,
              placeholder: (_, __) => fallback,
              errorWidget: (_, __, ___) => fallback,
            ),
          ),
        );
      },
    );
  }
}

/// Standard full-screen error state with a retry button.
class ErrorRetry extends StatelessWidget {
  const ErrorRetry({super.key, required this.message, required this.onRetry});

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
          Text(message, style: TextStyle(color: Colors.grey.shade600)),
          const SizedBox(height: 16),
          FilledButton.icon(
            onPressed: onRetry,
            icon: const Icon(Icons.refresh),
            label: const Text('Retry'),
            style: FilledButton.styleFrom(backgroundColor: kBrandColor),
          ),
        ],
      ),
    );
  }
}

/// Simple centred empty-state message with an icon.
class EmptyState extends StatelessWidget {
  const EmptyState({super.key, required this.icon, required this.message});

  final IconData icon;
  final String message;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 48, color: Colors.grey.shade300),
          const SizedBox(height: 12),
          Text(message, style: TextStyle(color: Colors.grey.shade500)),
        ],
      ),
    );
  }
}

/// Small coloured status pill (rounded, tinted background + border).
class StatusPill extends StatelessWidget {
  const StatusPill({super.key, required this.label, required this.color});

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
        style:
            TextStyle(color: color, fontSize: 10, fontWeight: FontWeight.w600),
      ),
    );
  }
}

/// M507 — banner shown when the network call failed and cached data is served.
class OfflineBanner extends StatelessWidget {
  const OfflineBanner({super.key, this.cachedAt});

  final DateTime? cachedAt;

  @override
  Widget build(BuildContext context) {
    final when = cachedAt == null
        ? ''
        : ' · ${cachedAt!.year}-${cachedAt!.month.toString().padLeft(2, '0')}-${cachedAt!.day.toString().padLeft(2, '0')} '
            '${cachedAt!.hour.toString().padLeft(2, '0')}:${cachedAt!.minute.toString().padLeft(2, '0')}';
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
      color: Colors.orange.withValues(alpha: 0.15),
      child: Row(
        children: [
          const Icon(Icons.cloud_off_outlined, size: 16, color: Colors.orange),
          const SizedBox(width: 8),
          Expanded(
            child: Text('Offline — showing cached data$when',
                style:
                    TextStyle(fontSize: 12, color: Colors.orange.shade900)),
          ),
        ],
      ),
    );
  }
}

/// Turns an ISO-8601 date/datetime string into a short `yyyy-MM-dd` label.
String shortDate(String? iso) {
  if (iso == null || iso.isEmpty) return '';
  final t = iso.indexOf('T');
  return t > 0 ? iso.substring(0, t) : iso;
}
