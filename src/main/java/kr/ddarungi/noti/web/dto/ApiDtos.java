package kr.ddarungi.noti.web.dto;

import jakarta.validation.constraints.*;
import kr.ddarungi.noti.cache.StationAvailability;
import kr.ddarungi.noti.domain.device.Platform;
import kr.ddarungi.noti.domain.schedule.SlotType;
import kr.ddarungi.noti.domain.schedule.ThresholdMode;
import kr.ddarungi.noti.domain.schedule.UserAlertSchedule;
import kr.ddarungi.noti.domain.station.StationMaster;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class ApiDtos {
    private ApiDtos() {}

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    // ---- devices ----
    public record DeviceRegisterRequest(@NotBlank @Size(max = 255) String pushToken,
                                        @NotNull Platform platform,
                                        @Size(max = 16) String appVersion) {}

    public record DeviceResponse(Long deviceId) {}

    // ---- stations ----
    public record StationDto(String stationId, String stationNo, String stationName,
                             BigDecimal lat, BigDecimal lng, Short rackTotCnt,
                             StationAvailability availability, Integer distanceM) {
        public static StationDto of(StationMaster s, StationAvailability a, Integer distanceM) {
            return new StationDto(s.getStationId(), s.getStationNo(), s.getStationName(),
                    s.getStationLat(), s.getStationLng(), s.getRackTotCnt(), a, distanceM);
        }
    }

    // ---- schedules ----
    public record ScheduleUpsertRequest(@NotNull SlotType slotType,
                                        @NotBlank @Size(max = 16) String stationId,
                                        @NotNull LocalTime notifyTime,
                                        @NotNull @Min(1) @Max(127) Integer notifyDays,
                                        @NotNull ThresholdMode thresholdMode,
                                        @Min(1) @Max(20) Short thresholdCount) {}

    public record ScheduleDto(Long scheduleId, SlotType slotType, String stationId, String stationNo,
                              String stationName, String notifyTime, int notifyDays,
                              ThresholdMode thresholdMode, short thresholdCount, boolean active) {
        public static ScheduleDto from(UserAlertSchedule s) {
            StationMaster st = s.getStation();
            return new ScheduleDto(s.getScheduleId(), s.getSlotType(), st.getStationId(), st.getStationNo(),
                    st.getStationName(), s.getNotifyTime().format(HHMM), s.getNotifyDays(),
                    s.getThresholdMode(), s.getThresholdCount(), s.isActive());
        }
    }

    // ---- home ----
    public record SlotDto(ScheduleDto schedule, StationAvailability availability) {}

    public record HomeResponse(SlotDto commuteIn, SlotDto commuteOut) {}

    // ---- favorites ----
    public record FavoriteDto(Long favoriteId, StationDto station, ScheduleDto schedule) {}

    public record FavoritesResponse(int count, int max, List<FavoriteDto> items) {}

    // ---- errors ----
    public record ErrorResponse(String code, String message) {}
}
