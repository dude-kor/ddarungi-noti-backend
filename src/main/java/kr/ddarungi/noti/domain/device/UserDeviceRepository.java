package kr.ddarungi.noti.domain.device;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {
    Optional<UserDevice> findByDeviceToken(String deviceToken);
}
