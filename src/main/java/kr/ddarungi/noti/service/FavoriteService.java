package kr.ddarungi.noti.service;

import kr.ddarungi.noti.cache.StationAvailability;
import kr.ddarungi.noti.cache.StationRealtimeService;
import kr.ddarungi.noti.domain.device.UserDeviceRepository;
import kr.ddarungi.noti.domain.favorite.UserFavoriteStation;
import kr.ddarungi.noti.domain.favorite.UserFavoriteStationRepository;
import kr.ddarungi.noti.domain.schedule.UserAlertSchedule;
import kr.ddarungi.noti.domain.schedule.UserAlertScheduleRepository;
import kr.ddarungi.noti.domain.station.StationMaster;
import kr.ddarungi.noti.domain.station.StationMasterRepository;
import kr.ddarungi.noti.web.ApiException;
import kr.ddarungi.noti.web.dto.ApiDtos.FavoriteDto;
import kr.ddarungi.noti.web.dto.ApiDtos.FavoritesResponse;
import kr.ddarungi.noti.web.dto.ApiDtos.ScheduleDto;
import kr.ddarungi.noti.web.dto.ApiDtos.StationDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final UserFavoriteStationRepository favoriteRepository;
    private final UserAlertScheduleRepository scheduleRepository;
    private final StationMasterRepository stationRepository;
    private final UserDeviceRepository deviceRepository;
    private final StationRealtimeService realtime;

    public FavoritesResponse list(Long deviceId) {
        List<UserFavoriteStation> favorites = favoriteRepository.findAllWithStation(deviceId);
        Map<String, StationAvailability> av = realtime.getAvailability(
                favorites.stream().map(f -> f.getStation().getStationId()).toList());
        List<UserAlertSchedule> schedules = scheduleRepository.findAllWithStation(deviceId);

        List<FavoriteDto> items = favorites.stream().map(f -> {
            StationMaster st = f.getStation();
            ScheduleDto tag = schedules.stream()
                    .filter(s -> s.getStation().getStationId().equals(st.getStationId()))
                    .findFirst().map(ScheduleDto::from).orElse(null);
            return new FavoriteDto(f.getFavoriteId(),
                    StationDto.of(st, av.get(st.getStationId()), null), tag);
        }).toList();
        return new FavoritesResponse(items.size(), UserFavoriteStation.MAX_PER_DEVICE, items);
    }

    @Transactional
    public void add(Long deviceId, String stationId) {
        StationMaster station = stationRepository.findById(stationId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "STATION_NOT_FOUND", "Unknown station " + stationId));
        if (favoriteRepository.existsByDeviceDeviceIdAndStationStationId(deviceId, stationId)) return;  // idempotent
        long count = favoriteRepository.countByDeviceDeviceId(deviceId);
        if (count >= UserFavoriteStation.MAX_PER_DEVICE) {
            throw new ApiException(HttpStatus.CONFLICT, "FAVORITE_LIMIT_REACHED",
                    "You can bookmark up to " + UserFavoriteStation.MAX_PER_DEVICE + " stations");
        }
        favoriteRepository.save(UserFavoriteStation.builder()
                .device(deviceRepository.getReferenceById(deviceId)).station(station)
                .sortOrder((int) count).build());
    }

    @Transactional
    public void remove(Long deviceId, String stationId) {
        favoriteRepository.deleteByDeviceDeviceIdAndStationStationId(deviceId, stationId);
    }
}
