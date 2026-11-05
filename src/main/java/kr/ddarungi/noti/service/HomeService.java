package kr.ddarungi.noti.service;

import kr.ddarungi.noti.cache.StationAvailability;
import kr.ddarungi.noti.cache.StationRealtimeService;
import kr.ddarungi.noti.domain.schedule.SlotType;
import kr.ddarungi.noti.domain.schedule.UserAlertSchedule;
import kr.ddarungi.noti.domain.schedule.UserAlertScheduleRepository;
import kr.ddarungi.noti.web.dto.ApiDtos.HomeResponse;
import kr.ddarungi.noti.web.dto.ApiDtos.ScheduleDto;
import kr.ddarungi.noti.web.dto.ApiDtos.SlotDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HomeService {

    private final UserAlertScheduleRepository scheduleRepository;
    private final StationRealtimeService realtime;

    public HomeResponse home(Long deviceId) {
        List<UserAlertSchedule> schedules = scheduleRepository.findAllWithStation(deviceId);
        Map<String, StationAvailability> av = realtime.getAvailability(
                schedules.stream().map(s -> s.getStation().getStationId()).toList());
        return new HomeResponse(slot(schedules, SlotType.COMMUTE_IN, av), slot(schedules, SlotType.COMMUTE_OUT, av));
    }

    private static SlotDto slot(List<UserAlertSchedule> all, SlotType type, Map<String, StationAvailability> av) {
        return all.stream().filter(s -> s.getSlotType() == type).findFirst()
                .map(s -> new SlotDto(ScheduleDto.from(s), av.get(s.getStation().getStationId())))
                .orElse(null);
    }
}
