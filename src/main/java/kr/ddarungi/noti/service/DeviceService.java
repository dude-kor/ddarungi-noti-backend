package kr.ddarungi.noti.service;

import kr.ddarungi.noti.domain.device.UserDevice;
import kr.ddarungi.noti.domain.device.UserDeviceRepository;
import kr.ddarungi.noti.web.dto.ApiDtos.DeviceRegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final UserDeviceRepository repository;

    @Transactional
    public UserDevice register(String deviceToken, DeviceRegisterRequest r) {
        return repository.findByDeviceToken(deviceToken)
                .map(d -> { d.refresh(r.pushToken(), r.platform(), r.appVersion()); return d; })
                .orElseGet(() -> repository.save(UserDevice.builder()
                        .deviceToken(deviceToken).pushToken(r.pushToken())
                        .platform(r.platform()).appVersion(r.appVersion()).build()));
    }

    /** FCM said the token is dead (UNREGISTERED / 410). */
    @Transactional
    public void deactivate(Long deviceId) {
        repository.findById(deviceId).ifPresent(UserDevice::deactivate);
    }
}
