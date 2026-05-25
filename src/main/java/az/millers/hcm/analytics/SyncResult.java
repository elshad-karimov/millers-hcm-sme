package az.millers.hcm.analytics;

public record SyncResult(
    boolean success,
    String message,
    int employeesLoaded,
    int attendanceLoaded,
    int payrollLoaded,
    int leaveLoaded
) {}
