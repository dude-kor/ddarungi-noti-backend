package kr.ddarungi.noti.service;

import kr.ddarungi.noti.domain.device.UserDeviceRepository;
import kr.ddarungi.noti.domain.schedule.ThresholdMode;
import kr.ddarungi.noti.domain.schedule.UserAlertSchedule;
import kr.ddarungi.noti.domain.schedule.UserAlertScheduleRepository;
import kr.ddarungi.noti.domain.station.StationMaster;
import kr.ddarungi.noti.domain.station.StationMasterRepository;
import kr.ddarungi.noti.web.ApiException;
import kr.ddarungi.noti.web.dto.ApiDtos.ScheduleDto;
import kr.ddarungi.noti.web.dto.ApiDtos.ScheduleUpsertRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final UserAlertScheduleRepository scheduleRepository;
    private final StationMasterRepository stationRepository;
    private final UserDeviceRepository deviceRepository;

    @Transactional
    public ScheduleDto upsert(Long deviceId, ScheduleUpsertRequest r) {
        if (r.notifyTime().getMinute() % 5 != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TIME", "notifyTime must be on a 5-minute step");
        }
        StationMaster station = stationRepository.findById(r.stationId()).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "STATION_NOT_FOUND", "Unknown station " + r.stationId()));
        Short count = r.thresholdMode() == ThresholdMode.BELOW_N
                ? (r.thresholdCount() == null ? Short.valueOf((short) 3) : r.thresholdCount())
                : null;

        UserAlertSchedule schedule = scheduleRepository
                .findByDeviceDeviceIdAndSlotType(deviceId, r.slotType()).orElse(null);
        if (schedule == null) {
            schedule = scheduleRepository.save(UserAlertSchedule.builder()
                    .device(deviceRepository.getReferenceById(deviceId))
                    .slotType(r.slotType()).station(station)
                    .notifyTime(r.notifyTime()).notifyDays(r.notifyDays())
                    .thresholdMode(r.thresholdMode()).thresholdCount(count).build());
        } else {
            schedule.update(station, r.notifyTime(), r.notifyDays(), r.thresholdMode(), count);
        }
        return ScheduleDto.from(schedule);
    }

    @Transactional
    public ScheduleDto toggle(Long deviceId, Long scheduleId) {
        UserAlertSchedule schedule = scheduleRepository.findById(scheduleId)
                .filter(s -> s.getDevice().getDeviceId().equals(deviceId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Schedule not found"));
        schedule.toggle();
        return ScheduleDto.from(schedule);
    }
}
