/// Timesheet models — mirrors TimesheetResponse / TimesheetDay from the web API.

class TimesheetDay {
  final String id;
  final String workDate;
  final String primaryCode; // W | L | S | BT | P | O | H | A
  final double workedHours;
  final double overtimeHours;
  final int lateMinutes;
  final int earlyMinutes;
  final String? anomalies;

  const TimesheetDay({
    required this.id,
    required this.workDate,
    required this.primaryCode,
    required this.workedHours,
    required this.overtimeHours,
    required this.lateMinutes,
    required this.earlyMinutes,
    this.anomalies,
  });

  factory TimesheetDay.fromJson(Map<String, dynamic> j) => TimesheetDay(
        id: j['id'] as String,
        workDate: j['workDate'] as String? ?? '',
        primaryCode: j['primaryCode'] as String? ?? 'A',
        workedHours: (j['workedHours'] as num? ?? 0).toDouble(),
        overtimeHours: (j['overtimeHours'] as num? ?? 0).toDouble(),
        lateMinutes: j['lateMinutes'] as int? ?? 0,
        earlyMinutes: j['earlyMinutes'] as int? ?? 0,
        anomalies: j['anomalies'] as String?,
      );
}

class Timesheet {
  final String id;
  final int periodYear;
  final int periodMonth;
  final String status;
  final double totalWorkedHours;
  final double totalOvertimeHours;
  final double totalLeaveDays;
  final double totalAbsentDays;
  final List<TimesheetDay> days;

  const Timesheet({
    required this.id,
    required this.periodYear,
    required this.periodMonth,
    required this.status,
    required this.totalWorkedHours,
    required this.totalOvertimeHours,
    required this.totalLeaveDays,
    required this.totalAbsentDays,
    required this.days,
  });

  String get periodLabel {
    const months = [
      '', 'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
      'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'
    ];
    return '${months[periodMonth]} $periodYear';
  }

  factory Timesheet.fromJson(Map<String, dynamic> j) => Timesheet(
        id: j['id'] as String,
        periodYear: j['periodYear'] as int? ?? 0,
        periodMonth: j['periodMonth'] as int? ?? 0,
        status: j['status'] as String? ?? '',
        totalWorkedHours: (j['totalWorkedHours'] as num? ?? 0).toDouble(),
        totalOvertimeHours: (j['totalOvertimeHours'] as num? ?? 0).toDouble(),
        totalLeaveDays: (j['totalLeaveDays'] as num? ?? 0).toDouble(),
        totalAbsentDays: (j['totalAbsentDays'] as num? ?? 0).toDouble(),
        days: (j['days'] as List? ?? [])
            .map((d) => TimesheetDay.fromJson(d as Map<String, dynamic>))
            .toList(),
      );
}
