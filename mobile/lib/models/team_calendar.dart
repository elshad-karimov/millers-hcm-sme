/// M510 — Team calendar (manager view) from GET /api/self/team-calendar.
///
/// Confidentiality: the specific leave TYPE is intentionally NOT surfaced to the
/// manager here — entries are rendered simply as "On Leave" / "Away".
class TeamCalendar {
  final bool isManager;
  final String? windowStart;
  final String? windowEnd;
  final int teamSize;
  final List<TeamLeaveEntry> entries;

  const TeamCalendar({
    required this.isManager,
    this.windowStart,
    this.windowEnd,
    required this.teamSize,
    required this.entries,
  });

  factory TeamCalendar.fromJson(Map<String, dynamic> j) {
    final isManager = j['manager'] as bool? ?? false;
    if (!isManager) {
      return const TeamCalendar(
          isManager: false, teamSize: 0, entries: []);
    }
    final cal = (j['calendar'] as Map<String, dynamic>?) ?? {};
    return TeamCalendar(
      isManager: true,
      windowStart: cal['windowStart'] as String?,
      windowEnd: cal['windowEnd'] as String?,
      teamSize: (cal['teamSize'] as num?)?.toInt() ?? 0,
      entries: ((cal['entries'] as List?) ?? [])
          .map((e) => TeamLeaveEntry.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}

class TeamLeaveEntry {
  final String employeeName;
  final String employeeNo;
  final String startDate;
  final String endDate;
  final double totalDays;
  final bool halfDay;
  final String status;

  const TeamLeaveEntry({
    required this.employeeName,
    required this.employeeNo,
    required this.startDate,
    required this.endDate,
    required this.totalDays,
    required this.halfDay,
    required this.status,
  });

  factory TeamLeaveEntry.fromJson(Map<String, dynamic> j) => TeamLeaveEntry(
        employeeName: j['employeeName'] as String? ?? '',
        employeeNo: j['employeeNo'] as String? ?? '',
        startDate: j['startDate'] as String? ?? '',
        endDate: j['endDate'] as String? ?? '',
        totalDays: (j['totalDays'] as num?)?.toDouble() ?? 0,
        halfDay: j['halfDay'] as bool? ?? false,
        status: j['status'] as String? ?? '',
      );
}
