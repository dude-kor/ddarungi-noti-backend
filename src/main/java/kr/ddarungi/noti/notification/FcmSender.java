package kr.ddarungi.noti.notification;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import jakarta.annotation.PostConstruct;
import kr.ddarungi.noti.config.FcmProperties;
import kr.ddarungi.noti.service.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;

/** FCM HTTP v1 sender (Firebase Admin SDK). With fcm.enabled=false it only logs (local dev). */
@Slf4j
@Component
@RequiredArgsConstructor
public class FcmSender {

    private final FcmProperties props;
    private final DeviceService deviceService;
    private FirebaseMessaging messaging;

    @PostConstruct
    void init() throws IOException {
        if (!props.enabled()) {
            log.info("FCM disabled: push messages will only be logged");
            return;
        }
        if (FirebaseApp.getApps().isEmpty()) {
            try (FileInputStream in = new FileInputStream(props.credentialsPath())) {
                FirebaseApp.initializeApp(FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(in)).build());
            }
        }
        messaging = FirebaseMessaging.getInstance();
    }

    public void send(PushMessage m) {
        String title = "따릉이 알리미";
        String body = m.bikeCnt() == 0
                ? m.stationName() + "에 남은 따릉이가 없어요"
                : m.stationName() + "에 따릉이 " + m.bikeCnt() + "대 있어요";

        if (messaging == null) {
            log.info("[FCM dry-run] device={} '{}' - {}", m.deviceId(), title, body);
            return;
        }
        Message message = Message.builder()
                .setToken(m.pushToken())
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putData("stationId", m.stationId())
                .putData("scheduleId", String.valueOf(m.scheduleId()))
                .putData("slotType", m.slotType().name())
                .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
                .setApnsConfig(ApnsConfig.builder()
                        .putHeader("apns-priority", "10")
                        .setAps(Aps.builder().putCustomData("interruption-level", "time-sensitive").build())
                        .build())
                .build();
        try {
            messaging.send(message);
        } catch (FirebaseMessagingException e) {
            boolean gone = e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                    || (e.getHttpResponse() != null && e.getHttpResponse().getStatusCode() == 410);
            if (gone) {
                log.info("Token for device {} is dead, deactivating", m.deviceId());
                deviceService.deactivate(m.deviceId());
            } else {
                log.warn("FCM send failed for device {}: {}", m.deviceId(), e.getMessage());
            }
        }
    }
}
