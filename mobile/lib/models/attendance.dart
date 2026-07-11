// Attendance models — mobile clock-in (M496), geofences (M497),
// day summaries, corrections (M498), overtime.

/// Response from POST /api/self/attendance/punch
class PunchResult {
  final String eventId;
  final String type; // IN | OUT
  final String recordedAt; // ISO
  final String geofenceStatus; // INSIDE | OUTSIDE | UNKNOWN
  final bool flagged;

  const PunchResult({
    required this.eventId,
    required this.type,
    required this.recordedAt,
    required this.geofenceStatus,
    required this.flagged,
  });

  factory PunchResult.fromJson(Map<String, dynamic> j) => PunchResult(
        eventId: j['eventId']?.toString() ?? '',
        type: j['type'] as String? ?? '',
        recordedAt: j['recordedAt'] as String? ?? '',
        geofenceStatus: j['geofenceStatus'] as String? ?? 'UNKNOWN',
        flagged: j['flagged'] as bool? ?? false,
      );
}

/// GET /api/self/attendance/geofences
class GeofenceConfig {
  final List<GeofenceLocation> locations;
  final bool geofencingConfigured;

  const GeofenceConfig({
    required this.locations,
    required this.geofencingConfigured,
  });

  factory GeofenceConfig.fromJson(Map<String, dynamic> j) => GeofenceConfig(
        locations: ((j['locations'] as List?) ?? [])
            .map((e) => GeofenceLocation.fromJson(e as Map<String, dynamic>))
            .toList(),
        geofencingConfigured: j['geofencingConfigured'] as bool? ?? false,
      );
}

class GeofenceLocation {
  final String locationId;
  final String name;
  final double latitude;
  final double longitude;
  final int radiusM;

  const GeofenceLocation({
    required this.locationId,
    required this.name,
    required this.latitude,
    required this.longitude,
    required this.radiusM,
  });

  factory GeofenceLocation.fromJson(Map<String, dynamic> j) => GeofenceLocation(
        locationId: j['locationId']?.toString() ?? '',
        name: j['name'] as String? ?? '',
        latitude: (j['latitude'] as num?)?.toDouble() ?? 0,
        longitude: (j['longitude'] as num?)?.toDouble() ?? 0,
        radiusM: (j['radiusM'] as num?)?.toInt() ?? 0,
      );
}

/// GET /api/self/attendance/summaries — one employee-day summary.
class DailySummary {
  final String id;
  final String employeeId;
  final String workDate; // yyyy-MM-dd
  final String? entryTime; // ISO
  final String? exitTime; // ISO
  final int workedMinutes;
  final int lateMinutes;
  final int earlyMinutes;
  final int breakMinutes;
  final int overtimeMinutes;
  final String status; // PRESENT | PARTIAL | ABSENT | NON_WORKING_DAY | NO_SCHEDULE

  const DailySummary({
    required this.id,
    required this.employeeId,
    required this.workDate,
    this.entryTime,
    this.exitTime,
    required this.workedMinutes,
    required this.lateMinutes,
    required this.earlyMinutes,
    required this.breakMinutes,
    required this.overtimeMinutes,
    required this.status,
  });

  factory DailySummary.fromJson(Map<String, dynamic> j) => DailySummary(
        id: j['id']?.toString() ?? '',
        employeeId: j['employeeId']?.toString() ?? '',
        workDate: j['workDate'] as String? ?? '',
        entryTime: j['entryTime'] as String?,
        exitTime: j['exitTime'] as String?,
        workedMinutes: (j['workedMinutes'] as num?)?.toInt() ?? 0,
        lateMinutes: (j['lateMinutes'] as num?)?.toInt() ?? 0,
        earlyMinutes: (j['earlyMinutes'] as num?)?.toInt() ?? 0,
        breakMinutes: (j['breakMinutes'] as num?)?.toInt() ?? 0,
        overtimeMinutes: (j['overtimeMinutes'] as num?)?.toInt() ?? 0,
        status: j['status'] as String? ?? '',
      );
}

/// GET /api/self/attendance/corrections — one correction request.
class AttendanceCorrection {
  final String id;
  final String workDate;
  final String? requestedClockIn;
  final String? requestedClockOut;
  final String? requestedStatus;
  final String? reason;
  final String? correctionType;
  final String workflowStatus; // PENDING | APPROVED | REJECTED
  final String? decision;

  const AttendanceCorrection({
    required this.id,
    required this.workDate,
    this.requestedClockIn,
    this.requestedClockOut,
    this.requestedStatus,
    this.reason,
    this.correctionType,
    required this.workflowStatus,
    this.decision,
  });

  factory AttendanceCorrection.fromJson(Map<String, dynamic> j) =>
      AttendanceCorrection(
        id: j['id']?.toString() ?? '',
        workDate: j['workDate'] as String? ?? '',
        requestedClockIn: j['requestedClockIn'] as String?,
        requestedClockOut: j['requestedClockOut'] as String?,
        requestedStatus: j['requestedStatus'] as String?,
        reason: j['reason'] as String?,
        correctionType: j['correctionType'] as String?,
        workflowStatus: j['workflowStatus'] as String? ?? 'PENDING',
        decision: j['decision'] as String?,
      );
}

/// GET /api/self/attendance/overtime-requests — one overtime request.
class OvertimeRequestItem {
  final String id;
  final String workDate;
  final String? otStart;
  final String? otEnd;
  final int requestedMinutes;
  final String? reason;
  final bool preApproved;
  final String workflowStatus;
  final String? decision;

  const OvertimeRequestItem({
    required this.id,
    required this.workDate,
    this.otStart,
    this.otEnd,
    required this.requestedMinutes,
    this.reason,
    required this.preApproved,
    required this.workflowStatus,
    this.decision,
  });

  factory OvertimeRequestItem.fromJson(Map<String, dynamic> j) =>
      OvertimeRequestItem(
        id: j['id']?.toString() ?? '',
        workDate: j['workDate'] as String? ?? '',
        otStart: j['otStart'] as String?,
        otEnd: j['otEnd'] as String?,
        requestedMinutes: (j['requestedMinutes'] as num?)?.toInt() ?? 0,
        reason: j['reason'] as String?,
        preApproved: j['preApproved'] as bool? ?? false,
        workflowStatus: j['workflowStatus'] as String? ?? 'PENDING',
        decision: j['decision'] as String?,
      );
}
