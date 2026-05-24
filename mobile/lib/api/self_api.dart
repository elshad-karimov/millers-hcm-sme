import '../api/api_client.dart';
import '../models/employee.dart';
import '../models/leave.dart';
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
