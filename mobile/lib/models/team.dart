/// Team/manager models for the mobile app (§11.3).

class TeamMember {
  final String id;
  final String employeeNo;
  final String firstName;
  final String lastName;
  final String? positionTitle;
  final String? departmentName;
  final String employmentStatus;
  final String? hireDate;
  final bool onLeaveToday;

  const TeamMember({
    required this.id,
    required this.employeeNo,
    required this.firstName,
    required this.lastName,
    this.positionTitle,
    this.departmentName,
    required this.employmentStatus,
    this.hireDate,
    required this.onLeaveToday,
  });

  String get fullName => '$firstName $lastName';

  factory TeamMember.fromJson(Map<String, dynamic> j) => TeamMember(
        id: j['id'] as String,
        employeeNo: j['employeeNo'] as String? ?? '',
        firstName: j['firstName'] as String? ?? '',
        lastName: j['lastName'] as String? ?? '',
        positionTitle: j['positionTitle'] as String?,
        departmentName: j['departmentName'] as String?,
        employmentStatus: j['employmentStatus'] as String? ?? '',
        hireDate: j['hireDate'] as String?,
        onLeaveToday: j['onLeaveToday'] as bool? ?? false,
      );
}

class TeamSummary {
  final int headcount;
  final int onLeaveToday;
  final int onProbation;
  final int pendingLeaveRequests;
  final int contractsEndingSoon;

  const TeamSummary({
    required this.headcount,
    required this.onLeaveToday,
    required this.onProbation,
    required this.pendingLeaveRequests,
    required this.contractsEndingSoon,
  });

  factory TeamSummary.fromJson(Map<String, dynamic> j) => TeamSummary(
        headcount: j['headcount'] as int? ?? 0,
        onLeaveToday: (j['onLeaveToday'] as int?) ?? 0,
        onProbation: (j['onProbation'] as int?) ?? 0,
        pendingLeaveRequests: (j['pendingLeaveRequests'] as int?) ?? 0,
        contractsEndingSoon: (j['contractsEndingSoon'] as int?) ?? 0,
      );
}
