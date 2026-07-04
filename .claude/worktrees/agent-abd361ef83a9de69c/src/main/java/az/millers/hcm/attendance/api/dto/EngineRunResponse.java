package az.millers.hcm.attendance.api.dto;

public record EngineRunResponse(
        int employeesProcessed,
        int summariesWritten) {
}
