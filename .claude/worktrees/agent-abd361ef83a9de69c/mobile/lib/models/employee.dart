/// Employee profile returned by GET /api/self/employee
class Employee {
  final String id;
  final String employeeNo;
  final String firstName;
  final String? middleName;
  final String lastName;
  final String? email;
  final String? phone;
  final String? position;
  final String? department;
  final String employmentStatus;
  final String? hireDate;

  const Employee({
    required this.id,
    required this.employeeNo,
    required this.firstName,
    this.middleName,
    required this.lastName,
    this.email,
    this.phone,
    this.position,
    this.department,
    required this.employmentStatus,
    this.hireDate,
  });

  String get fullName =>
      [firstName, middleName, lastName].where((s) => s != null && s.isNotEmpty).join(' ');

  factory Employee.fromJson(Map<String, dynamic> j) => Employee(
        id: j['id'] as String,
        employeeNo: j['employeeNo'] as String? ?? '',
        firstName: j['firstName'] as String? ?? '',
        middleName: j['middleName'] as String?,
        lastName: j['lastName'] as String? ?? '',
        email: j['workEmail'] as String?,
        phone: j['phone'] as String?,
        position: j['positionTitle'] as String?,
        department: j['departmentName'] as String?,
        employmentStatus: j['employmentStatus'] as String? ?? '',
        hireDate: j['hireDate'] as String?,
      );
}
