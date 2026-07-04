package az.millers.hcm.attendance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.attendance.domain.ShiftPatternDay;

public interface ShiftPatternDayRepository extends JpaRepository<ShiftPatternDay, UUID> {

    List<ShiftPatternDay> findByPatternIdOrderByDayIndexAsc(UUID patternId);

    @Modifying
    @Query("delete from ShiftPatternDay d where d.patternId = :patternId")
    void deleteAllByPatternId(UUID patternId);

    long countByShiftId(UUID shiftId);
}
