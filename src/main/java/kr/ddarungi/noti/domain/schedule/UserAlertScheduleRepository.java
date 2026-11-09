package kr.ddarungi.noti.domain.schedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserAlertScheduleRepository extends JpaRepository<UserAlertSchedule, Long> {

    /** Upper bound of ids per IN (...) list; keeps statements small and plan-cache friendly. */
    int IN_CHUNK = 1000;

    Optional<UserAlertSchedule> findByDeviceDeviceIdAndSlotType(Long deviceId, SlotType slotType);

    @Query("select s from UserAlertSchedule s join fetch s.station where s.device.deviceId = :deviceId")
    List<UserAlertSchedule> findAllWithStation(@Param("deviceId") Long deviceId);

    // ------------------------------------------------------------------------------------------------------------
    // Dispatcher scan (runs every minute at :45).
    //
    // FIX: the old findDue() loaded every active schedule of the minute and filtered the weekday bitmask in Java
    //      (s.firesOn(day)): all rows of the wrong weekdays crossed the wire only to be thrown away.
    //
    // FIX: two statements instead of one, on purpose:
    //  1) a NATIVE id query evaluates the bitmask inside MySQL with the real `&` operator. The suggested
    //     FUNCTION('BITAND', ...) is passed through by Hibernate as BITAND(a, b) unless the dialect maps it, and
    //     MySQL has no BITAND() function (that is Oracle/H2 syntax) - it would fail at runtime on production.
    //  2) a JPQL JOIN FETCH loads schedule + device + station for exactly those ids in ONE round trip (no N+1).
    //
    // Locking: both are plain SELECTs = non-locking consistent reads under InnoDB REPEATABLE READ. They take no
    // record or gap locks, so the scan can never block (or be deadlocked by) device deactivation or schedule edits.
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Ids of schedules due at {@code time} on a day whose bit is set in {@code dayMask}
     * (Mon=1 ... Sun=64, see {@link UserAlertSchedule#dayBit}). Served by idx_scheduler_lookup
     * (is_active, notify_time, notify_days, ...): the equality prefix narrows to the minute, the bit test is
     * evaluated on the index entry itself.
     */
    @Query(value = """
            SELECT s.schedule_id
              FROM user_alert_schedule s
              JOIN user_device d ON d.device_id = s.device_id
             WHERE s.is_active = 1
               AND s.notify_time = :time
               AND (s.notify_days & :dayMask) <> 0
               AND d.is_active = 1
             ORDER BY s.schedule_id
            """, nativeQuery = true)
    List<Long> findDueIds(@Param("time") LocalTime time, @Param("dayMask") int dayMask);

    /** Fetch-joins device and station: callers can read both without touching the database again. */
    @Query("""
           select s from UserAlertSchedule s
             join fetch s.device
             join fetch s.station
            where s.scheduleId in :ids
            order by s.scheduleId
           """)
    List<UserAlertSchedule> findAllWithAssociationsByIdIn(@Param("ids") Collection<Long> ids);

    /** Everything the dispatcher needs for {@code time} on {@code day}, device and station already loaded. */
    default List<UserAlertSchedule> findDue(LocalTime time, DayOfWeek day) {
        List<Long> ids = findDueIds(time, UserAlertSchedule.dayBit(day));
        if (ids.isEmpty()) {
            return List.of();
        }
        List<UserAlertSchedule> due = new ArrayList<>(ids.size());
        for (int from = 0; from < ids.size(); from += IN_CHUNK) {
            due.addAll(findAllWithAssociationsByIdIn(ids.subList(from, Math.min(from + IN_CHUNK, ids.size()))));
        }
        return due;
    }
}
