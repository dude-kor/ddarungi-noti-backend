package kr.ddarungi.noti.domain.device;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_device",
        uniqueConstraints = @UniqueConstraint(name = "uk_device_token", columnNames = "device_token"),
        indexes = @Index(name = "idx_push_token", columnList = "push_token"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_id")
    private Long deviceId;

    /** Client-generated identifier sent as the X-Device-Token header. */
    @Column(name = "device_token", length = 64, nullable = false)
    private String deviceToken;

    /** FCM registration token. */
    @Column(name = "push_token", length = 255, nullable = false)
    private String pushToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 16)
    private Platform platform;

    @Column(name = "app_version", length = 16)
    private String appVersion;

    @Column(name = "is_active", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 1")
    private boolean active = true;

    @Builder
    private UserDevice(String deviceToken, String pushToken, Platform platform, String appVersion) {
        this.deviceToken = deviceToken;
        this.pushToken = pushToken;
        this.platform = platform;
        this.appVersion = appVersion;
        this.active = true;
    }

    /** Upsert path: refresh token/platform/version and re-activate. */
    public void refresh(String pushToken, Platform platform, String appVersion) {
        this.pushToken = pushToken;
        this.platform = platform;
        this.appVersion = appVersion;
        this.active = true;
    }

    /** Called when FCM answers UNREGISTERED / 410 Gone. */
    public void deactivate() {
        this.active = false;
    }
}
