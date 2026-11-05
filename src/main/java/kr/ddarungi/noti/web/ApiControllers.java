package kr.ddarungi.noti.web;

import jakarta.validation.Valid;
import kr.ddarungi.noti.domain.device.UserDevice;
import kr.ddarungi.noti.service.*;
import kr.ddarungi.noti.web.dto.ApiDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static kr.ddarungi.noti.web.DeviceTokenInterceptor.ATTR_DEVICE_ID;
import static kr.ddarungi.noti.web.DeviceTokenInterceptor.HEADER;

/** All REST endpoints under /api/v1 (kept in one file for compactness; split per resource if it grows). */
public final class ApiControllers {
    private ApiControllers() {}

    @RestController
    @RequestMapping("/api/v1/devices")
    @RequiredArgsConstructor
    public static class DeviceController {
        private final DeviceService deviceService;

        /** Register / upsert device and FCM push token. */
        @PostMapping
        public DeviceResponse register(@RequestHeader(HEADER) String deviceToken,
                                       @Valid @RequestBody DeviceRegisterRequest request) {
            UserDevice device = deviceService.register(deviceToken, request);
            return new DeviceResponse(device.getDeviceId());
        }
    }

    @RestController
    @RequestMapping("/api/v1/stations")
    @RequiredArgsConstructor
    public static class StationController {
        private final StationService stationService;

        @GetMapping("/search")
        public List<StationDto> search(@RequestParam("q") String q) {
            return stationService.search(q, 20);
        }

        /** Markers for the Kakao map viewport (addition to the spec: SCR-03 needs it). */
        @GetMapping("/nearby")
        public List<StationDto> nearby(@RequestParam double lat, @RequestParam double lng,
                                       @RequestParam(defaultValue = "1000") int radius,
                                       @RequestParam(defaultValue = "100") int limit) {
            return stationService.nearby(lat, lng, Math.min(Math.max(radius, 100), 5000), Math.min(limit, 300));
        }
    }

    @RestController
    @RequestMapping("/api/v1/home")
    @RequiredArgsConstructor
    public static class HomeController {
        private final HomeService homeService;

        @GetMapping
        public HomeResponse home(@RequestAttribute(ATTR_DEVICE_ID) Long deviceId) {
            return homeService.home(deviceId);
        }
    }

    @RestController
    @RequestMapping("/api/v1/schedules")
    @RequiredArgsConstructor
    public static class ScheduleController {
        private final ScheduleService scheduleService;

        @PostMapping
        public ScheduleDto upsert(@RequestAttribute(ATTR_DEVICE_ID) Long deviceId,
                                  @Valid @RequestBody ScheduleUpsertRequest request) {
            return scheduleService.upsert(deviceId, request);
        }

        @PatchMapping("/{id}/toggle")
        public ScheduleDto toggle(@RequestAttribute(ATTR_DEVICE_ID) Long deviceId, @PathVariable Long id) {
            return scheduleService.toggle(deviceId, id);
        }
    }

    @RestController
    @RequestMapping("/api/v1/favorites")
    @RequiredArgsConstructor
    public static class FavoriteController {
        private final FavoriteService favoriteService;

        @GetMapping
        public FavoritesResponse list(@RequestAttribute(ATTR_DEVICE_ID) Long deviceId) {
            return favoriteService.list(deviceId);
        }

        /** Addition to the spec: needed to populate/remove bookmarks (max 5 per device). */
        @PostMapping("/{stationId}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public void add(@RequestAttribute(ATTR_DEVICE_ID) Long deviceId, @PathVariable String stationId) {
            favoriteService.add(deviceId, stationId);
        }

        @DeleteMapping("/{stationId}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public void remove(@RequestAttribute(ATTR_DEVICE_ID) Long deviceId, @PathVariable String stationId) {
            favoriteService.remove(deviceId, stationId);
        }
    }
}
