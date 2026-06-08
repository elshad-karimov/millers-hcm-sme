/// Training/LMS models for the mobile app.

class Course {
  final String id;
  final String courseNo;
  final String code;
  final String title;
  final String? description;
  final String category;
  final double durationHours;
  final bool mandatory;
  final double passingScore;
  final int maxAttempts;
  final String status;
  final String? coverUrl;

  const Course({
    required this.id,
    required this.courseNo,
    required this.code,
    required this.title,
    this.description,
    required this.category,
    required this.durationHours,
    required this.mandatory,
    required this.passingScore,
    required this.maxAttempts,
    required this.status,
    this.coverUrl,
  });

  factory Course.fromJson(Map<String, dynamic> j) => Course(
        id: j['id'] as String,
        courseNo: j['courseNo'] as String? ?? '',
        code: j['code'] as String? ?? '',
        title: j['title'] as String? ?? '',
        description: j['description'] as String?,
        category: j['category'] as String? ?? '',
        durationHours: (j['durationHours'] as num? ?? 0).toDouble(),
        mandatory: j['mandatory'] as bool? ?? false,
        passingScore: (j['passingScore'] as num? ?? 0).toDouble(),
        maxAttempts: j['maxAttempts'] as int? ?? 1,
        status: j['status'] as String? ?? '',
        coverUrl: j['coverUrl'] as String?,
      );
}

class Enrollment {
  final String id;
  final String enrollmentNo;
  final String courseId;
  final String status; // ENROLLED | IN_PROGRESS | PASSED | FAILED | WITHDRAWN | EXPIRED
  final String? dueDate;
  final String? completedAt;
  final int attemptsUsed;
  final double? bestScorePercent;

  const Enrollment({
    required this.id,
    required this.enrollmentNo,
    required this.courseId,
    required this.status,
    this.dueDate,
    this.completedAt,
    required this.attemptsUsed,
    this.bestScorePercent,
  });

  bool get isPassed => status == 'PASSED';
  bool get isCompleted => status == 'PASSED' || status == 'FAILED';

  factory Enrollment.fromJson(Map<String, dynamic> j) => Enrollment(
        id: j['id'] as String,
        enrollmentNo: j['enrollmentNo'] as String? ?? '',
        courseId: j['courseId'] as String? ?? '',
        status: j['status'] as String? ?? '',
        dueDate: (j['dueDate'] as String?)?.substring(0, 10),
        completedAt: j['completedAt'] as String?,
        attemptsUsed: j['attemptsUsed'] as int? ?? 0,
        bestScorePercent: j['bestScorePercent'] == null
            ? null
            : (j['bestScorePercent'] as num).toDouble(),
      );
}

class Certificate {
  final String id;
  final String certificateNo;
  final String courseId;
  final String issuedAt;
  final String? validUntil;
  final double? scorePercent;
  final bool revoked;

  const Certificate({
    required this.id,
    required this.certificateNo,
    required this.courseId,
    required this.issuedAt,
    this.validUntil,
    this.scorePercent,
    required this.revoked,
  });

  factory Certificate.fromJson(Map<String, dynamic> j) => Certificate(
        id: j['id'] as String,
        certificateNo: j['certificateNo'] as String? ?? '',
        courseId: j['courseId'] as String? ?? '',
        issuedAt: j['issuedAt'] as String? ?? '',
        validUntil: (j['validUntil'] as String?)?.substring(0, 10),
        scorePercent: j['scorePercent'] == null
            ? null
            : (j['scorePercent'] as num).toDouble(),
        revoked: j['revoked'] as bool? ?? false,
      );
}
