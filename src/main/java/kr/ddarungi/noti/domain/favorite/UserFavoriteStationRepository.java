package kr.ddarungi.noti.domain.favorite;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserFavoriteStationRepository extends JpaRepository<UserFavoriteStation, Long> {

    @Query("select f from UserFavoriteStation f join fetch f.station "
            + "where f.device.deviceId = :deviceId order by f.sortOrder asc")
    List<UserFavoriteStation> findAllWithStation(@Param("deviceId") Long deviceId);

    long countByDeviceDeviceId(Long deviceId);

    boolean existsByDeviceDeviceIdAndStationStationId(Long deviceId, String stationId);

    long deleteByDeviceDeviceIdAndStationStationId(Long deviceId, String stationId);
}
