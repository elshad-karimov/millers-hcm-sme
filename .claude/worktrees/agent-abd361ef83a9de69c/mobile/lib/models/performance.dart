/// Performance models for the mobile app.

class Goal {
  final String id;
  final String goalNo;
  final String title;
  final String? description;
  final String category;
  final double weightPercent;
  final double progressPercent;
  final String status;
  final String? dueDate;
  final int? rating;

  const Goal({
    required this.id,
    required this.goalNo,
    required this.title,
    this.description,
    required this.category,
    required this.weightPercent,
    required this.progressPercent,
    required this.status,
    this.dueDate,
    this.rating,
  });

  factory Goal.fromJson(Map<String, dynamic> j) => Goal(
        id: j['id'] as String,
        goalNo: j['goalNo'] as String? ?? '',
        title: j['title'] as String? ?? '',
        description: j['description'] as String?,
        category: j['category'] as String? ?? '',
        weightPercent: (j['weightPercent'] as num? ?? 0).toDouble(),
        progressPercent: (j['progressPercent'] as num? ?? 0).toDouble(),
        status: j['status'] as String? ?? '',
        dueDate: (j['dueDate'] as String?)?.substring(0, 10),
        rating: j['rating'] as int?,
      );
}

class PerformanceReview {
  final String id;
  final String reviewNo;
  final String cycleName;
  final String status;
  final double? overallRating;
  final String? completedAt;

  const PerformanceReview({
    required this.id,
    required this.reviewNo,
    required this.cycleName,
    required this.status,
    this.overallRating,
    this.completedAt,
  });

  factory PerformanceReview.fromJson(Map<String, dynamic> j) =>
      PerformanceReview(
        id: j['id'] as String,
        reviewNo: j['reviewNo'] as String? ?? '',
        cycleName: j['cycleName'] as String? ?? j['cycleId'] as String? ?? '',
        status: j['status'] as String? ?? '',
        overallRating: j['overallRating'] == null
            ? null
            : (j['overallRating'] as num).toDouble(),
        completedAt: j['completedAt'] as String?,
      );
}
