/// Leave balance from GET /api/self/leave-balances
class LeaveBalance {
  final String id;
  final String leaveTypeCode;
  final String leaveTypeName;
  final double entitlementDays;
  final double usedDays;
  final double pendingDays;
  final int year;

  const LeaveBalance({
    required this.id,
    required this.leaveTypeCode,
    required this.leaveTypeName,
    required this.entitlementDays,
    required this.usedDays,
    required this.pendingDays,
    required this.year,
  });

  double get remainingDays => entitlementDays - usedDays - pendingDays;

  factory LeaveBalance.fromJson(Map<String, dynamic> j) => LeaveBalance(
        id: j['id'] as String,
        leaveTypeCode: j['leaveTypeCode'] as String? ?? '',
        leaveTypeName: j['leaveTypeName'] as String? ?? '',
        entitlementDays: (j['entitlementDays'] as num).toDouble(),
        usedDays: (j['usedDays'] as num? ?? 0).toDouble(),
        pendingDays: (j['pendingDays'] as num? ?? 0).toDouble(),
        year: j['year'] as int? ?? DateTime.now().year,
      );
}

/// Leave request from GET /api/self/leave-requests
class LeaveRequest {
  final String id;
  final String requestNo;
  final String leaveTypeCode;
  final String leaveTypeName;
  final String startDate;
  final String endDate;
  final double totalDays;
  final String status;
  final String? notes;
  final String submittedAt;

  const LeaveRequest({
    required this.id,
    required this.requestNo,
    required this.leaveTypeCode,
    required this.leaveTypeName,
    required this.startDate,
    required this.endDate,
    required this.totalDays,
    required this.status,
    this.notes,
    required this.submittedAt,
  });

  factory LeaveRequest.fromJson(Map<String, dynamic> j) => LeaveRequest(
        id: j['id'] as String,
        requestNo: j['requestNo'] as String? ?? '',
        leaveTypeCode: j['leaveTypeCode'] as String? ?? '',
        leaveTypeName: j['leaveTypeName'] as String? ?? '',
        startDate: j['startDate'] as String? ?? '',
        endDate: j['endDate'] as String? ?? '',
        totalDays: (j['totalDays'] as num? ?? 0).toDouble(),
        status: j['status'] as String? ?? '',
        notes: j['notes'] as String?,
        submittedAt: j['submittedAt'] as String? ?? '',
      );
}

/// Leave type for the submission form dropdown
class LeaveType {
  final String id;
  final String code;
  final String name;

  const LeaveType({required this.id, required this.code, required this.name});

  factory LeaveType.fromJson(Map<String, dynamic> j) => LeaveType(
        id: j['id'] as String,
        code: j['code'] as String? ?? '',
        name: j['name'] as String? ?? '',
      );
}
