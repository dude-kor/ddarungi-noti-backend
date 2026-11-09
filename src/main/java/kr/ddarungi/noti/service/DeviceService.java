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

    /**
     * Deactivates many devices after a mass send.
     * FIX: ids are de-duplicated, sorted ascending and updated in chunks of 500, each chunk in its own short
     * transaction. Every writer therefore takes the same record locks in the same order (no lock cycle), and no
     * transaction holds hundreds of row locks while FCM is being called.
     */
    public int deactivateAll(java.util.Collection<Long> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) return 0;
        java.util.List<Long> sorted = deviceIds.stream().filter(java.util.Objects::nonNull)
                .distinct().sorted().toList();
        int changed = 0;
        for (int from = 0; from < sorted.size(); from += 500) {
            // each call is its own transaction: deactivateByIds is @Transactional on the repository interface
            changed += repository.deactivateByIds(sorted.subList(from, Math.min(from + 500, sorted.size())));
        }
        return changed;
    }

    /** FCM said the token is dead (UNREGISTERED / 410). */
    @Transactional
    public void deactivate(Long deviceId) {
        repository.findById(deviceId).ifPresent(UserDevice::deactivate);
    }
}
