package kr.ddarungi.noti.domain.schedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface UserAlertScheduleRepository extends JpaRepository<UserAlertSchedule, Long> {

    Optional<UserAlertSchedule> findByDeviceDeviceIdAndSlotType(Long deviceId, SlotType slotType);

    @Query("select s from UserAlertSchedule s join fetch s.station where s.device.deviceId = :deviceId")
    List<UserAlertSchedule> findAllWithStation(@Param("deviceId") Long deviceId);

    /** Dispatcher scan: hits idx_scheduler_lookup (is_active, notify_time, station_id, device_id). */
    @Query("""
           select s from UserAlertSchedule s
             join fetch s.device d
             join fetch s.station
            where s.active = true
              and s.notifyTime = :time
              and d.active = true
           """)
    List<UserAlertSchedule> findDue(@Param("time") LocalTime time);
}
