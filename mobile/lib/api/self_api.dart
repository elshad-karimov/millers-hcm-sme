import 'package:dio/dio.dart';

import '../api/api_client.dart';
import '../config/offline_cache.dart';
import '../models/announcement.dart';
import '../models/attendance.dart';
import '../models/branding.dart';
import '../models/directory.dart';
import '../models/document_item.dart';
import '../models/employee.dart';
import '../models/hr_request.dart';
import '../models/leave.dart';
import '../models/notification_item.dart';
import '../models/performance.dart';
import '../models/personal_info.dart';
import '../models/policy.dart';
import '../models/requests.dart';
import '../models/team.dart';
import '../models/team_calendar.dart';
import '../models/timesheet.dart';
import '../models/training.dart';
import '../models/workflow.dart';

/// Calls the /api/self/* endpoints backed by the existing EmployeeContextService.
class SelfApi {
  SelfApi._();
  static final SelfApi instance = SelfApi._();

  final _dio = ApiClient.instance.dio;

  Future<Employee> getProfile() async {
    final r = await _dio.get('/self/employee');
    return Employee.fromJson(r.data as Map<String, dynamic>);
  }

  Future<List<LeaveBalance>> getLeaveBalances({int? year}) async {
    final r = await _dio.get('/self/leave-balances',
        queryParameters: year != null ? {'year': year} : null);
    return (r.data as List)
        .map((j) => LeaveBalance.fromJson(j as Map<String, dynamic>))
        .toList();
  }

  Future<List<LeaveRequest>> getLeaveRequests() async {
    final r = await _dio.get('/self/leave-requests');
    return (r.data as List)
        .map((j) => LeaveRequest.fromJson(j as Map<String, dynamic>))
        .toList();
  }

  /// Submit a new leave request via POST /api/self/leave/submit.
  /// M511: backend accepts halfDay, replacementEmployeeId and attachmentUrl.
  /// The backend field for free-text notes is `reason` (not `notes`).
  Future<LeaveRequest> submitLeave({
    required String leaveTypeId,
    required String startDate, // yyyy-MM-dd
    required String endDate,
    String? reason,
    bool? halfDay,
    String? replacementEmployeeId,
    String? attachmentUrl,
  }) async {
    final r = await _dio.post('/self/leave/submit', data: {
      'leaveTypeId': leaveTypeId,
      'startDate': startDate,
      'endDate': endDate,
      if (reason != null && reason.isNotEmpty) 'reason': reason,
      if (halfDay != null) 'halfDay': halfDay,
      if (replacementEmployeeId != null)
        'replacementEmployeeId': replacementEmployeeId,
      if (attachmentUrl != null && attachmentUrl.isNotEmpty)
        'attachmentUrl': attachmentUrl,
    });
    return LeaveRequest.fromJson(r.data as Map<String, dynamic>);
  }

  /// M511 — active colleagues that can be picked as a leave replacement.
  Future<List<Peer>> getPeers() async {
    final r = await _dio.get('/self/peers');
    return (r.data as List)
        .map((j) => Peer.fromJson(j as Map<String, dynamic>))
        .toList();
  }

  Future<List<Payslip>> getPayslips() async {
    final r = await _dio.get('/self/payslips');
    return (r.data as List)
        .map((j) => Payslip.fromJson(j as Map<String, dynamic>))
        .toList();
  }

  // ── Timesheets ────────────────────────────────────────────────────────────

  /// GET /api/self/timesheets — list my timesheets (summary, no days).
  Future<List<Timesheet>> getTimesheets() async {
    final r = await _dio.get('/self/timesheets');
    return (r.data as List)
        .map((j) => Timesheet.fromJson(j as Map<String, dynamic>))
        .toList();
  }

  /// GET /api/timesheets/{id} — full timesheet with daily rows.
  Future<Timesheet> getTimesheetDetail(String id) async {
    final r = await _dio.get('/timesheets/$id');
    return Timesheet.fromJson(r.data as Map<String, dynamic>);
  }

  // ── Manager team views (§11.3) ────────────────────────────────────────────

  Future<List<TeamMember>> getTeam() async {
    final r = await _dio.get('/self/team');
    return (r.data as List)
        .map((j) => TeamMember.fromJson(j as Map<String, dynamic>))
        .toList();
  }

  Future<TeamSummary> getTeamSummary() async {
    final r = await _dio.get('/self/team/summary');
    return TeamSummary.fromJson(r.data as Map<String, dynamic>);
  }

  // ── Business trips ────────────────────────────────────────────────────────

  Future<List<BusinessTrip>> getBusinessTrips() async {
    final r = await _dio.get('/self/business-trips');
    return (r.data as List)
        .map((j) => BusinessTrip.fromJson(j as Map<String, dynamic>))
        .toList();
  }

  Future<BusinessTrip> submitBusinessTrip({
    required String destinationCity,
    required String startDate,
    required String endDate,
    String? purpose,
    String tripType = 'DOMESTIC',
  }) async {
    final r = await _dio.post('/self/business-trips/submit', data: {
      'tripType': tripType,
      'destinationCity': destinationCity,
      'startDate': startDate,
      'endDate': endDate,
      if (purpose != null && purpose.isNotEmpty) 'purpose': purpose,
    });
    return BusinessTrip.fromJson(r.data as Map<String, dynamic>);
  }

  // ── Permissions ───────────────────────────────────────────────────────────

  Future<List<PermissionRequest>> getPermissions() async {
    final r = await _dio.get('/self/permission/requests');
    return (r.data as List)
        .map((j) => PermissionRequest.fromJson(j as Map<String, dynamic>))
        .toList();
  }

  Future<PermissionRequest> submitPermission({
    required String permissionTypeId,
    required String permissionDate,
    required String startTime,
    required String endTime,
    required double durationHours,
    String? reason,
  }) async {
    final r = await _dio.post('/self/permission/submit', data: {
      'permissionTypeId': permissionTypeId,
      'permissionDate': permissionDate,
      'startTime': startTime,
      'endTime': endTime,
      'durationHours': durationHours,
      if (reason != null && reason.isNotEmpty) 'reason': reason,
    });
    return PermissionRequest.fromJson(r.data as Map<String, dynamic>);
  }

  // ── Performance ───────────────────────────────────────────────────────────

  Future<List<Goal>> getGoals() async {
    final r = await _dio.get('/self/performance/goals');
    return (r.data as List)
        .map((j) => Goal.fromJson(j as Map<String, dynamic>))
        .toList();
  }

  Future<List<PerformanceReview>> getReviews() async {
    final r = await _dio.get('/self/performance/reviews');
    return (r.data as List)
        .map((j) => PerformanceReview.fromJson(j as Map<String, dynamic>))
        .toList();
  }

  // ── Training / LMS ────────────────────────────────────────────────────────

  Future<List<Enrollment>> getEnrollments() async {
    final r = await _dio.get('/self/learning/enrollments');
    return (r.data as List)
        .map((j) => Enrollment.fromJson(j as Map<String, dynamic>))
        .toList();
  }

  Future<List<Certificate>> getCertificates() async {
    final r = await _dio.get('/self/learning/certificates');
    return (r.data as List)
        .map((j) => Certificate.fromJson(j as Map<String, dynamic>))
        .toList();
  }

  // ── Workflow approvals ────────────────────────────────────────────────────

  /// Pending approvals inbox for the current user (manager / admin).
  Future<List<WorkflowInstance>> getApprovalInbox() async {
    final r = await _dio.get('/workflow/instances',
        queryParameters: {'status': 'PENDING', 'mine': 'true'});
    // The backend returns a page; unwrap content or treat as list.
    final data = r.data;
    if (data is List) {
      return data
          .map((j) => WorkflowInstance.fromJson(j as Map<String, dynamic>))
          .toList();
    }
    final content = (data as Map<String, dynamic>)['content'] as List? ?? [];
    return content
        .map((j) => WorkflowInstance.fromJson(j as Map<String, dynamic>))
        .toList();
  }

  /// POST /api/workflow/instances/{id}/actions
  Future<void> actOnWorkflow(
      String instanceId, String action, String? comment) async {
    await _dio.post('/workflow/instances/$instanceId/actions', data: {
      'action': action,
      if (comment != null && comment.isNotEmpty) 'comment': comment,
    });
  }

  // ── M499: Announcements (GET /api/self/announcements) ─────────────────────

  Future<List<Announcement>> getAnnouncements() async {
    final r = await _dio.get('/self/announcements');
    return (r.data as List)
        .map((j) => Announcement.fromJson(j as Map<String, dynamic>))
        .toList();
  }

  // ── M500: Policies (GET /api/self/policies, POST .../{id}/acknowledge) ─────

  Future<List<Policy>> getPolicies() async {
    final r = await _dio.get('/self/policies');
    return (r.data as List)
        .map((j) => Policy.fromSelfView(j as Map<String, dynamic>))
        .toList();
  }

  Future<void> acknowledgePolicy(String policyId) async {
    await _dio.post('/self/policies/$policyId/acknowledge');
  }

  // ── M501: HR service requests (GET/POST /api/self/hr-requests) ────────────

  Future<List<HrRequest>> getHrRequests() async {
    final r = await _dio.get('/self/hr-requests');
    return (r.data as List)
        .map((j) => HrRequest.fromJson(j as Map<String, dynamic>))
        .toList();
  }

  Future<HrRequest> submitHrRequest({
    required String category,
    required String priority,
    required String subject,
    String? description,
  }) async {
    final r = await _dio.post('/self/hr-requests', data: {
      'category': category,
      'priority': priority,
      'subject': subject,
      if (description != null && description.isNotEmpty)
        'description': description,
    });
    return HrRequest.fromJson(r.data as Map<String, dynamic>);
  }

  Future<List<HrRequestComment>> getHrRequestComments(String requestId) async {
    final r = await _dio.get('/self/hr-requests/$requestId/comments');
    return (r.data as List)
        .map((j) => HrRequestComment.fromJson(j as Map<String, dynamic>))
        .toList();
  }

  Future<HrRequestComment> addHrRequestComment(
      String requestId, String body) async {
    final r = await _dio.post('/self/hr-requests/$requestId/comments',
        data: {'body': body});
    return HrRequestComment.fromJson(r.data as Map<String, dynamic>);
  }

  // ── M502: Documents (GET/POST /api/attachments) ───────────────────────────

  /// List the current employee's uploaded self-service documents.
  Future<List<DocumentItem>> getDocuments(String employeeId) async {
    final r = await _dio.get('/attachments', queryParameters: {
      'ownerModule': 'selfservice',
      'ownerEntity': 'employeedocument',
      'ownerId': employeeId,
    });
    return (r.data as List)
        .map((j) => DocumentItem.fromJson(j as Map<String, dynamic>))
        .toList();
  }

  /// Upload a document as multipart/form-data with upload progress.
  Future<DocumentItem> uploadDocument({
    required String employeeId,
    required List<int> bytes,
    required String filename,
    String? contentType,
    void Function(int sent, int total)? onProgress,
  }) async {
    final form = FormData.fromMap({
      'ownerModule': 'selfservice',
      'ownerEntity': 'employeedocument',
      'ownerId': employeeId,
      'file': MultipartFile.fromBytes(
        bytes,
        filename: filename,
        contentType: contentType != null
            ? DioMediaType.parse(contentType)
            : null,
      ),
    });
    final r = await _dio.post(
      '/attachments',
      data: form,
      onSendProgress: onProgress,
    );
    return DocumentItem.fromJson(r.data as Map<String, dynamic>);
  }

  // ── M503: Personal-info change requests ───────────────────────────────────

  /// GET /api/self/personal-info — my pending / decided change requests.
  Future<List<PersonalInfoChange>> getPersonalInfoChanges() async {
    final r = await _dio.get('/self/personal-info');
    return (r.data as List)
        .map((j) => PersonalInfoChange.fromJson(j as Map<String, dynamic>))
        .toList();
  }

  /// POST /api/self/personal-info/submit — one change request per field.
  Future<PersonalInfoChange> submitPersonalInfoChange({
    required String fieldKey,
    required String newValue,
    String? reason,
  }) async {
    final r = await _dio.post('/self/personal-info/submit', data: {
      'fieldKey': fieldKey,
      'newValue': newValue,
      if (reason != null && reason.isNotEmpty) 'reason': reason,
    });
    return PersonalInfoChange.fromJson(r.data as Map<String, dynamic>);
  }

  // ── M505: Notifications (GET /api/notifications) ──────────────────────────

  Future<List<NotificationItem>> getNotifications({int page = 0, int size = 30}) async {
    final r = await _dio.get('/notifications',
        queryParameters: {'page': page, 'size': size});
    final data = r.data;
    final items = data is List
        ? data
        : ((data as Map<String, dynamic>)['content'] as List? ?? []);
    return items
        .map((j) => NotificationItem.fromJson(j as Map<String, dynamic>))
        .toList();
  }

  Future<int> getUnreadNotificationCount() async {
    final r = await _dio.get('/notifications/unread-count');
    return ((r.data as Map<String, dynamic>)['count'] as num?)?.toInt() ?? 0;
  }

  Future<void> markNotificationRead(String id) async {
    await _dio.patch('/notifications/$id/read');
  }

  Future<void> markAllNotificationsRead() async {
    await _dio.post('/notifications/read-all');
  }

  // ── M496 / M497 / M508: Attendance clock-in + geofences ───────────────────

  /// POST /api/self/attendance/punch — record an IN/OUT punch.
  /// [offlineQueueId] makes replay idempotent (M508).
  Future<PunchResult> punch({
    required String type, // 'IN' | 'OUT'
    required String timestamp, // ISO-8601 with offset
    double? latitude,
    double? longitude,
    double? gpsAccuracy,
    String? deviceId,
    String? offlineQueueId,
  }) async {
    final r = await _dio.post('/self/attendance/punch', data: {
      'type': type,
      'timestamp': timestamp,
      if (latitude != null) 'latitude': latitude,
      if (longitude != null) 'longitude': longitude,
      if (gpsAccuracy != null) 'gpsAccuracy': gpsAccuracy,
      if (deviceId != null) 'deviceId': deviceId,
      if (offlineQueueId != null) 'offlineQueueId': offlineQueueId,
    });
    return PunchResult.fromJson(r.data as Map<String, dynamic>);
  }

  /// GET /api/self/attendance/geofences
  Future<GeofenceConfig> getGeofences() async {
    final r = await _dio.get('/self/attendance/geofences');
    return GeofenceConfig.fromJson(r.data as Map<String, dynamic>);
  }

  /// GET /api/self/attendance/summaries?from=&to=
  Future<List<DailySummary>> getAttendanceSummaries({
    required String from, // yyyy-MM-dd
    required String to,
  }) async {
    final r = await _dio.get('/self/attendance/summaries',
        queryParameters: {'from': from, 'to': to});
    return (r.data as List)
        .map((j) => DailySummary.fromJson(j as Map<String, dynamic>))
        .toList();
  }

  // ── M498: Attendance corrections + overtime requests ──────────────────────

  Future<List<AttendanceCorrection>> getCorrections() async {
    final r = await _dio.get('/self/attendance/corrections');
    return (r.data as List)
        .map((j) => AttendanceCorrection.fromJson(j as Map<String, dynamic>))
        .toList();
  }

  Future<AttendanceCorrection> submitCorrection({
    required String employeeId,
    required String workDate, // yyyy-MM-dd
    String? requestedClockIn, // ISO
    String? requestedClockOut, // ISO
    String? requestedStatus,
    String correctionType = 'CLOCK_TIME',
    String? reason,
  }) async {
    final r = await _dio.post('/self/attendance/corrections', data: {
      'employeeId': employeeId,
      'workDate': workDate,
      if (requestedClockIn != null) 'requestedClockIn': requestedClockIn,
      if (requestedClockOut != null) 'requestedClockOut': requestedClockOut,
      if (requestedStatus != null) 'requestedStatus': requestedStatus,
      'correctionType': correctionType,
      if (reason != null && reason.isNotEmpty) 'reason': reason,
      'absenceStatusChanged': false,
      'overtimeDeltaMinutes': 0,
    });
    return AttendanceCorrection.fromJson(r.data as Map<String, dynamic>);
  }

  Future<List<OvertimeRequestItem>> getOvertimeRequests() async {
    final r = await _dio.get('/self/attendance/overtime-requests');
    return (r.data as List)
        .map((j) => OvertimeRequestItem.fromJson(j as Map<String, dynamic>))
        .toList();
  }

  Future<OvertimeRequestItem> submitOvertime({
    required String employeeId,
    required String workDate,
    required String otStart, // ISO
    required String otEnd, // ISO
    String? reason,
  }) async {
    final r = await _dio.post('/self/attendance/overtime-requests', data: {
      'employeeId': employeeId,
      'workDate': workDate,
      'otStart': otStart,
      'otEnd': otEnd,
      if (reason != null && reason.isNotEmpty) 'reason': reason,
      'preApproved': false,
    });
    return OvertimeRequestItem.fromJson(r.data as Map<String, dynamic>);
  }

  // ── M504: Employee directory ──────────────────────────────────────────────

  Future<List<DirectoryEntry>> searchDirectory(String query) async {
    final r = await _dio.get('/self/directory',
        queryParameters: {'q': query});
    return (r.data as List)
        .map((j) => DirectoryEntry.fromJson(j as Map<String, dynamic>))
        .toList();
  }

  // ── M505b: Mobile branding ────────────────────────────────────────────────

  Future<MobileBranding> getMobileBranding() async {
    final r = await _dio.get('/config/mobile-branding');
    return MobileBranding.fromJson(r.data as Map<String, dynamic>);
  }

  // ── M510: Team calendar (manager) ─────────────────────────────────────────

  Future<TeamCalendar> getTeamCalendar({String? windowStart, String? windowEnd}) async {
    final r = await _dio.get('/self/team-calendar', queryParameters: {
      if (windowStart != null) 'windowStart': windowStart,
      if (windowEnd != null) 'windowEnd': windowEnd,
    });
    return TeamCalendar.fromJson(r.data as Map<String, dynamic>);
  }

  // ── M512: Bulk workflow approvals ─────────────────────────────────────────

  /// POST /api/workflow/bulk-act. Returns a per-item result: {id, ok, error?}.
  Future<List<BulkActionResult>> bulkAct({
    required List<String> instanceIds,
    required String action, // APPROVE | REJECT
    String? comment,
  }) async {
    final r = await _dio.post('/workflow/bulk-act', data: {
      'instanceIds': instanceIds,
      'action': action,
      if (comment != null && comment.isNotEmpty) 'comment': comment,
    });
    return (r.data as List)
        .map((j) => BulkActionResult.fromJson(j as Map<String, dynamic>))
        .toList();
  }

  // ── M507: Offline read cache (non-sensitive data only) ────────────────────
  //
  // Each of these tries the network first and writes-through to the local
  // cache on success; on a network failure it serves the last cached copy and
  // marks the result `fromCache` so the UI can show an "offline" banner.
  // Payslip / salary data is deliberately NOT cached here.

  Future<Cached<Employee>> getProfileCached() async {
    try {
      final r = await _dio.get('/self/employee');
      final map = r.data as Map<String, dynamic>;
      await OfflineCache.instance.saveJson('profile', map);
      return Cached(Employee.fromJson(map));
    } catch (e) {
      final env = await OfflineCache.instance.readJson('profile');
      if (env?.data is Map) {
        return Cached(
          Employee.fromJson(Map<String, dynamic>.from(env!.data as Map)),
          fromCache: true,
          cachedAt: env.cachedAt,
        );
      }
      rethrow;
    }
  }

  Future<Cached<List<LeaveBalance>>> getLeaveBalancesCached({int? year}) async {
    final key = 'leave_balances_${year ?? 'current'}';
    try {
      final r = await _dio.get('/self/leave-balances',
          queryParameters: year != null ? {'year': year} : null);
      final list = r.data as List;
      await OfflineCache.instance.saveJson(key, list);
      return Cached(list
          .map((j) => LeaveBalance.fromJson(j as Map<String, dynamic>))
          .toList());
    } catch (e) {
      final env = await OfflineCache.instance.readJson(key);
      if (env?.data is List) {
        return Cached(
          (env!.data as List)
              .map((j) =>
                  LeaveBalance.fromJson(Map<String, dynamic>.from(j as Map)))
              .toList(),
          fromCache: true,
          cachedAt: env.cachedAt,
        );
      }
      rethrow;
    }
  }

  Future<Cached<List<Announcement>>> getAnnouncementsCached() async {
    try {
      final r = await _dio.get('/self/announcements');
      final list = r.data as List;
      await OfflineCache.instance.saveJson('announcements', list);
      return Cached(list
          .map((j) => Announcement.fromJson(j as Map<String, dynamic>))
          .toList());
    } catch (e) {
      final env = await OfflineCache.instance.readJson('announcements');
      if (env?.data is List) {
        return Cached(
          (env!.data as List)
              .map((j) =>
                  Announcement.fromJson(Map<String, dynamic>.from(j as Map)))
              .toList(),
          fromCache: true,
          cachedAt: env.cachedAt,
        );
      }
      rethrow;
    }
  }

  Future<Cached<List<Policy>>> getPoliciesCached() async {
    try {
      final r = await _dio.get('/self/policies');
      final list = r.data as List;
      await OfflineCache.instance.saveJson('policies', list);
      return Cached(list
          .map((j) => Policy.fromSelfView(j as Map<String, dynamic>))
          .toList());
    } catch (e) {
      final env = await OfflineCache.instance.readJson('policies');
      if (env?.data is List) {
        return Cached(
          (env!.data as List)
              .map((j) =>
                  Policy.fromSelfView(Map<String, dynamic>.from(j as Map)))
              .toList(),
          fromCache: true,
          cachedAt: env.cachedAt,
        );
      }
      rethrow;
    }
  }
}

/// M512 — one line of a bulk-action result.
class BulkActionResult {
  final String id;
  final bool ok;
  final String? error;

  const BulkActionResult({required this.id, required this.ok, this.error});

  factory BulkActionResult.fromJson(Map<String, dynamic> j) => BulkActionResult(
        id: j['id']?.toString() ?? '',
        ok: j['ok'] as bool? ?? false,
        error: j['error'] as String?,
      );
}

/// Permission type lookup.
class PermissionTypeApi {
  PermissionTypeApi._();
  static final PermissionTypeApi instance = PermissionTypeApi._();

  final _dio = ApiClient.instance.dio;

  Future<List<PermissionType>> list() async {
    final r = await _dio.get('/permissions/types');
    final data = r.data;
    final items = data is List
        ? data
        : ((data as Map<String, dynamic>)['content'] as List? ?? []);
    return items
        .map((j) => PermissionType.fromJson(j as Map<String, dynamic>))
        .toList();
  }
}

/// Course lookup (used by TrainingScreen to resolve courseId → title).
class CourseApi {
  CourseApi._();
  static final CourseApi instance = CourseApi._();

  final _dio = ApiClient.instance.dio;

  Future<List<Course>> list() async {
    final r = await _dio.get('/learning/courses',
        queryParameters: {'size': 500, 'status': 'PUBLISHED'});
    final data = r.data;
    final items = data is List
        ? data
        : ((data as Map<String, dynamic>)['content'] as List? ?? []);
    return items
        .map((j) => Course.fromJson(j as Map<String, dynamic>))
        .toList();
  }
}

/// Leave types for the submission form.
class LeaveTypeApi {
  LeaveTypeApi._();
  static final LeaveTypeApi instance = LeaveTypeApi._();

  final _dio = ApiClient.instance.dio;

  Future<List<LeaveType>> list() async {
    final r = await _dio.get('/leave/types');
    return (r.data as List)
        .map((j) => LeaveType.fromJson(j as Map<String, dynamic>))
        .toList();
  }
}
