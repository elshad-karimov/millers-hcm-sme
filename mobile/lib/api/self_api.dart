import '../api/api_client.dart';
import '../models/employee.dart';
import '../models/leave.dart';
import '../models/performance.dart';
import '../models/requests.dart';
import '../models/team.dart';
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

  /// Submit a new leave request via POST /api/self/leave/submit
  Future<LeaveRequest> submitLeave({
    required String leaveTypeId,
    required String startDate, // yyyy-MM-dd
    required String endDate,
    String? notes,
  }) async {
    final r = await _dio.post('/self/leave/submit', data: {
      'leaveTypeId': leaveTypeId,
      'startDate': startDate,
      'endDate': endDate,
      if (notes != null) 'notes': notes,
    });
    return LeaveRequest.fromJson(r.data as Map<String, dynamic>);
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
