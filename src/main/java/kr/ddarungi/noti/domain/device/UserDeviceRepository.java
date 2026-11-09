package kr.ddarungi.noti.domain.device;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Optional;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {
    Optional<UserDevice> findByDeviceToken(String deviceToken);

    /**
     * Bulk deactivation after a mass FCM send (UNREGISTERED / 410 results).
     *
     * <p>FIX (deadlock/gap-lock safety): the predicate is the primary key only, so InnoDB takes plain record locks
     * on existing rows - no next-key or gap locks as a range scan over idx_push_token would. Callers MUST pass ids
     * in ascending order and in chunks (see {@code DeviceService.deactivateAll}), so two concurrent batches always
     * lock rows in the same order and cannot form a cycle. The update does not touch any FK column, so no child
     * rows of user_alert_schedule / user_favorite_station are locked.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update UserDevice d set d.active = false where d.deviceId in :ids and d.active = true")
    int deactivateByIds(@Param("ids") Collection<Long> ids);
}
