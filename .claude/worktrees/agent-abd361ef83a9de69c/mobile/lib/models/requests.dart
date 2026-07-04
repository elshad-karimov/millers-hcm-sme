/// Business trip and permission models for the mobile app.

class BusinessTrip {
  final String id;
  final String tripNo;
  final String tripType;
  final String? destinationCity;
  final String? purpose;
  final String startDate;
  final String endDate;
  final String status;
  final String submittedAt;

  const BusinessTrip({
    required this.id,
    required this.tripNo,
    required this.tripType,
    this.destinationCity,
    this.purpose,
    required this.startDate,
    required this.endDate,
    required this.status,
    required this.submittedAt,
  });

  factory BusinessTrip.fromJson(Map<String, dynamic> j) => BusinessTrip(
        id: j['id'] as String,
        tripNo: j['tripNo'] as String? ?? '',
        tripType: j['tripType'] as String? ?? '',
        destinationCity: j['destinationCity'] as String?,
        purpose: j['purpose'] as String?,
        startDate: (j['startDate'] as String? ?? '').substring(0, 10),
        endDate: (j['endDate'] as String? ?? '').substring(0, 10),
        status: j['status'] as String? ?? '',
        submittedAt: j['submittedAt'] as String? ?? '',
      );
}

class PermissionType {
  final String id;
  final String code;
  final String name;

  const PermissionType({
    required this.id,
    required this.code,
    required this.name,
  });

  factory PermissionType.fromJson(Map<String, dynamic> j) => PermissionType(
        id: j['id'] as String,
        code: j['code'] as String? ?? '',
        name: j['name'] as String? ?? '',
      );
}

class PermissionRequest {
  final String id;
  final String requestNo;
  final String permissionTypeName;
  final String permissionDate;
  final String? startTime;
  final String? endTime;
  final double durationHours;
  final String status;
  final String? reason;

  const PermissionRequest({
    required this.id,
    required this.requestNo,
    required this.permissionTypeName,
    required this.permissionDate,
    this.startTime,
    this.endTime,
    required this.durationHours,
    required this.status,
    this.reason,
  });

  factory PermissionRequest.fromJson(Map<String, dynamic> j) =>
      PermissionRequest(
        id: j['id'] as String,
        requestNo: j['requestNo'] as String? ?? '',
        permissionTypeName: j['permissionTypeName'] as String? ??
            j['permissionTypeId'] as String? ??
            '',
        permissionDate:
            (j['permissionDate'] as String? ?? '').substring(0, 10),
        startTime: j['startTime'] as String?,
        endTime: j['endTime'] as String?,
        durationHours: (j['durationHours'] as num? ?? 0).toDouble(),
        status: j['status'] as String? ?? '',
        reason: j['reason'] as String?,
      );
}
