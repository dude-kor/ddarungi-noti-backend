package kr.ddarungi.noti.domain.schedule;

import jakarta.persistence.*;
import kr.ddarungi.noti.domain.device.UserDevice;
import kr.ddarungi.noti.domain.station.StationMaster;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Entity
@Table(name = "user_alert_schedule",
        uniqueConstraints = @UniqueConstraint(name = "uk_device_slot", columnNames = {"device_id", "slot_type"}),
        indexes = @Index(name = "idx_scheduler_lookup",
                columnList = "is_active, notify_time, station_id, device_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAlertSchedule {

    /** Bitmask: Mon=1, Tue=2, Wed=4, Thu=8, Fri=16, Sat=32, Sun=64. */
    public static final int WEEKDAYS = 1 | 2 | 4 | 8 | 16;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    private Long scheduleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_schedule_device"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserDevice device;

    @Enumerated(EnumType.STRING)
    @Column(name = "slot_type", nullable = false, length = 16)
    private SlotType slotType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_schedule_station"))
    private StationMaster station;

    /** Always stored at HH:mm:00 (5-minute granularity enforced by the API layer). */
    @Column(name = "notify_time", nullable = false)
    private LocalTime notifyTime;

    @Column(name = "notify_days", nullable = false, columnDefinition = "TINYINT NOT NULL")
    private int notifyDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "threshold_mode", nullable = false, length = 16)
    private ThresholdMode thresholdMode = ThresholdMode.ALWAYS;

    @Column(name = "threshold_count", nullable = false, columnDefinition = "SMALLINT NOT NULL DEFAULT 3")
    private short thresholdCount = 3;

    @Column(name = "is_active", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 1")
    private boolean active = true;

    @Builder
    private UserAlertSchedule(UserDevice device, SlotType slotType, StationMaster station,
                              LocalTime notifyTime, int notifyDays, ThresholdMode thresholdMode,
                              Short thresholdCount) {
        this.device = device;
        this.slotType = slotType;
        this.station = station;
        this.notifyTime = notifyTime.withSecond(0).withNano(0);
        this.notifyDays = notifyDays;
        this.thresholdMode = thresholdMode == null ? ThresholdMode.ALWAYS : thresholdMode;
        this.thresholdCount = thresholdCount == null ? 3 : thresholdCount;
        this.active = true;
    }

    public void update(StationMaster station, LocalTime notifyTime, int notifyDays,
                       ThresholdMode mode, Short thresholdCount) {
        this.station = station;
        this.notifyTime = notifyTime.withSecond(0).withNano(0);
        this.notifyDays = notifyDays;
        this.thresholdMode = mode;
        if (thresholdCount != null) this.thresholdCount = thresholdCount;
    }

    public void toggle() {
        this.active = !this.active;
    }

    public boolean firesOn(DayOfWeek day) {
        return (notifyDays & dayBit(day)) != 0;
    }

    /** Should a push be sent for the live bike count? */
    public boolean matches(int bikeCount) {
        return thresholdMode == ThresholdMode.ALWAYS || bikeCount <= thresholdCount;
    }

    public static int dayBit(DayOfWeek day) {
        return 1 << (day.getValue() - 1);   // MONDAY=1 -> bit 1, SUNDAY=7 -> bit 64
    }
}
