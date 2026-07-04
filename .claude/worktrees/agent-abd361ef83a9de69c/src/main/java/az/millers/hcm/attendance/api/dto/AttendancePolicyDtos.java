package az.millers.hcm.attendance.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.attendance.domain.AttendancePolicy;

public class AttendancePolicyDtos {

    public record AttendancePolicyRequest(
            String code,
            String name,
            String description,
            UUID departmentId,
            UUID locationId,
            String employmentType,
            int clockInGraceMinutes,
            int clockOutGraceMinutes,
            boolean lateDeductionEnabled,
            int lateMaxBeforeHalfDayMinutes,
            int lateMonthlyThresholdMinutes,
            boolean earlyLeaveDeductionEnabled,
            String earlyLeaveTreatment,
            int earlyLeaveToleranceMinutes,
            boolean absenceDeductionEnabled,
            int absenceDailyDivisor,
            int hoursPerDay,
            String unauthorizedAbsenceTreatment,
            boolean overtimeRequiresPreapproval,
            int overtimeDeptHeadThresholdMinutes,
            boolean compOffEnabled,
            boolean autoBreakDeductionEnabled,
            int breakMinutes,
            BigDecimal minHoursForBreakDeduction,
            boolean roundingEnabled,
            int roundingMinutes,
            String roundingDirection,
            boolean active
    ) {
        public AttendancePolicy toEntity() {
            AttendancePolicy p = new AttendancePolicy();
            p.setCode(code);
            p.setName(name);
            p.setDescription(description);
            p.setDepartmentId(departmentId);
            p.setLocationId(locationId);
            p.setEmploymentType(employmentType);
            p.setClockInGraceMinutes(clockInGraceMinutes);
            p.setClockOutGraceMinutes(clockOutGraceMinutes);
            p.setLateDeductionEnabled(lateDeductionEnabled);
            p.setLateMaxBeforeHalfDayMinutes(lateMaxBeforeHalfDayMinutes);
            p.setLateMonthlyThresholdMinutes(lateMonthlyThresholdMinutes);
            p.setEarlyLeaveDeductionEnabled(earlyLeaveDeductionEnabled);
            p.setEarlyLeaveTreatment(earlyLeaveTreatment != null ? earlyLeaveTreatment : "SALARY_DEDUCTION");
            p.setEarlyLeaveToleranceMinutes(earlyLeaveToleranceMinutes);
            p.setAbsenceDeductionEnabled(absenceDeductionEnabled);
            p.setAbsenceDailyDivisor(absenceDailyDivisor > 0 ? absenceDailyDivisor : 30);
            p.setHoursPerDay(hoursPerDay > 0 ? hoursPerDay : 8);
            p.setUnauthorizedAbsenceTreatment(unauthorizedAbsenceTreatment != null ? unauthorizedAbsenceTreatment : "UNPAID");
            p.setOvertimeRequiresPreapproval(overtimeRequiresPreapproval);
            p.setOvertimeDeptHeadThresholdMinutes(overtimeDeptHeadThresholdMinutes > 0 ? overtimeDeptHeadThresholdMinutes : 120);
            p.setCompOffEnabled(compOffEnabled);
            p.setAutoBreakDeductionEnabled(autoBreakDeductionEnabled);
            p.setBreakMinutes(breakMinutes > 0 ? breakMinutes : 60);
            p.setMinHoursForBreakDeduction(minHoursForBreakDeduction != null ? minHoursForBreakDeduction : new BigDecimal("6.0"));
            p.setRoundingEnabled(roundingEnabled);
            p.setRoundingMinutes(roundingMinutes > 0 ? roundingMinutes : 15);
            p.setRoundingDirection(roundingDirection != null ? roundingDirection : "NEAREST");
            p.setActive(active);
            return p;
        }
    }

    public record AttendancePolicyResponse(
            UUID id,
            String code,
            String name,
            String description,
            UUID departmentId,
            UUID locationId,
            String employmentType,
            int clockInGraceMinutes,
            int clockOutGraceMinutes,
            boolean lateDeductionEnabled,
            int lateMaxBeforeHalfDayMinutes,
            int lateMonthlyThresholdMinutes,
            boolean earlyLeaveDeductionEnabled,
            String earlyLeaveTreatment,
            int earlyLeaveToleranceMinutes,
            boolean absenceDeductionEnabled,
            int absenceDailyDivisor,
            int hoursPerDay,
            String unauthorizedAbsenceTreatment,
            boolean overtimeRequiresPreapproval,
            int overtimeDeptHeadThresholdMinutes,
            boolean compOffEnabled,
            boolean autoBreakDeductionEnabled,
            int breakMinutes,
            BigDecimal minHoursForBreakDeduction,
            boolean roundingEnabled,
            int roundingMinutes,
            String roundingDirection,
            boolean active,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        public static AttendancePolicyResponse from(AttendancePolicy p) {
            return new AttendancePolicyResponse(
                    p.getId(), p.getCode(), p.getName(), p.getDescription(),
                    p.getDepartmentId(), p.getLocationId(), p.getEmploymentType(),
                    p.getClockInGraceMinutes(), p.getClockOutGraceMinutes(),
                    p.isLateDeductionEnabled(), p.getLateMaxBeforeHalfDayMinutes(), p.getLateMonthlyThresholdMinutes(),
                    p.isEarlyLeaveDeductionEnabled(), p.getEarlyLeaveTreatment(), p.getEarlyLeaveToleranceMinutes(),
                    p.isAbsenceDeductionEnabled(), p.getAbsenceDailyDivisor(), p.getHoursPerDay(),
                    p.getUnauthorizedAbsenceTreatment(),
                    p.isOvertimeRequiresPreapproval(), p.getOvertimeDeptHeadThresholdMinutes(), p.isCompOffEnabled(),
                    p.isAutoBreakDeductionEnabled(), p.getBreakMinutes(), p.getMinHoursForBreakDeduction(),
                    p.isRoundingEnabled(), p.getRoundingMinutes(), p.getRoundingDirection(),
                    p.isActive(), p.getCreatedAt(), p.getUpdatedAt()
            );
        }
    }
}
