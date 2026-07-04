/// Workflow instance returned by GET /api/workflow/instances
class WorkflowInstance {
  final String id;
  final String definitionName;
  final String subjectEntity;
  final String subjectId;
  final String status;
  final String currentStepName;
  final String currentStepRole;
  final String initiatedBy;
  final String initiatedAt;
  final String? completedAt;

  const WorkflowInstance({
    required this.id,
    required this.definitionName,
    required this.subjectEntity,
    required this.subjectId,
    required this.status,
    required this.currentStepName,
    required this.currentStepRole,
    required this.initiatedBy,
    required this.initiatedAt,
    this.completedAt,
  });

  bool get isPending => status == 'PENDING';

  factory WorkflowInstance.fromJson(Map<String, dynamic> j) =>
      WorkflowInstance(
        id: j['id'] as String,
        definitionName: j['definitionName'] as String? ?? '',
        subjectEntity: j['subjectEntity'] as String? ?? '',
        subjectId: j['subjectId'] as String? ?? '',
        status: j['status'] as String? ?? '',
        currentStepName: j['currentStepName'] as String? ?? '',
        currentStepRole: j['currentStepRole'] as String? ?? '',
        initiatedBy: j['initiatedBy'] as String? ?? '',
        initiatedAt: j['initiatedAt'] as String? ?? '',
        completedAt: j['completedAt'] as String?,
      );
}

/// Payslip summary from GET /api/self/payslips
class Payslip {
  final String id;
  final int periodYear;
  final int periodMonth;
  final double grossAmount;
  final double netAmount;
  final double incomeTax;
  final String status;

  const Payslip({
    required this.id,
    required this.periodYear,
    required this.periodMonth,
    required this.grossAmount,
    required this.netAmount,
    required this.incomeTax,
    required this.status,
  });

  String get period =>
      '$periodYear-${periodMonth.toString().padLeft(2, '0')}';

  factory Payslip.fromJson(Map<String, dynamic> j) => Payslip(
        id: j['id'] as String,
        periodYear: j['periodYear'] as int? ?? 0,
        periodMonth: j['periodMonth'] as int? ?? 0,
        grossAmount: (j['grossAmount'] as num? ?? 0).toDouble(),
        netAmount: (j['netAmount'] as num? ?? 0).toDouble(),
        incomeTax: (j['incomeTax'] as num? ?? 0).toDouble(),
        status: j['status'] as String? ?? '',
      );
}
