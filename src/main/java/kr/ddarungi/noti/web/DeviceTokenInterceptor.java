package kr.ddarungi.noti.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.ddarungi.noti.domain.device.UserDevice;
import kr.ddarungi.noti.domain.device.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Every /api/v1 call must carry X-Device-Token. Except for registration and search, the token must
 * belong to a registered device; its id is exposed to controllers as request attribute "deviceId".
 */
@Component
@RequiredArgsConstructor
public class DeviceTokenInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Device-Token";
    public static final String ATTR_DEVICE_ID = "deviceId";

    private final UserDeviceRepository deviceRepository;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        if ("OPTIONS".equals(req.getMethod())) return true;

        String token = req.getHeader(HEADER);
        if (token == null || token.isBlank() || token.length() > 64) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "MISSING_DEVICE_TOKEN",
                    HEADER + " header is required (max 64 chars)");
        }
        String path = req.getRequestURI();
        boolean register = "POST".equals(req.getMethod()) && "/api/v1/devices".equals(path);
        boolean search = "/api/v1/stations/search".equals(path);
        if (register || search) return true;

        UserDevice device = deviceRepository.findByDeviceToken(token).orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "UNKNOWN_DEVICE", "Device is not registered"));
        req.setAttribute(ATTR_DEVICE_ID, device.getDeviceId());
        return true;
    }
}
