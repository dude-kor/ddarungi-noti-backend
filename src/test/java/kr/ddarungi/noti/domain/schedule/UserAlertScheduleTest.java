package kr.ddarungi.noti.domain.schedule;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserAlertScheduleTest {

    private static UserAlertSchedule schedule(int days, ThresholdMode mode, Short count) {
        return UserAlertSchedule.builder()
                .slotType(SlotType.COMMUTE_IN)
                .notifyTime(LocalTime.of(8, 30))
                .notifyDays(days)
                .thresholdMode(mode)
                .thresholdCount(count)
                .build();
    }

    @Test
    void dayBitsFollowTheMonToSunBitmask() {
        assertThat(UserAlertSchedule.dayBit(DayOfWeek.MONDAY)).isEqualTo(1);
        assertThat(UserAlertSchedule.dayBit(DayOfWeek.WEDNESDAY)).isEqualTo(4);
        assertThat(UserAlertSchedule.dayBit(DayOfWeek.FRIDAY)).isEqualTo(16);
        assertThat(UserAlertSchedule.dayBit(DayOfWeek.SATURDAY)).isEqualTo(32);
        assertThat(UserAlertSchedule.dayBit(DayOfWeek.SUNDAY)).isEqualTo(64);
        assertThat(UserAlertSchedule.WEEKDAYS).isEqualTo(31);
    }

    @Test
    void firesOnlyOnSelectedDays() {
        UserAlertSchedule weekdays = schedule(UserAlertSchedule.WEEKDAYS, ThresholdMode.ALWAYS, null);
        assertThat(weekdays.firesOn(DayOfWeek.MONDAY)).isTrue();
        assertThat(weekdays.firesOn(DayOfWeek.FRIDAY)).isTrue();
        assertThat(weekdays.firesOn(DayOfWeek.SATURDAY)).isFalse();
        assertThat(weekdays.firesOn(DayOfWeek.SUNDAY)).isFalse();
    }

    @Test
    void alwaysModeMatchesAnyBikeCount() {
        UserAlertSchedule s = schedule(127, ThresholdMode.ALWAYS, null);
        assertThat(s.matches(0)).isTrue();
        assertThat(s.matches(25)).isTrue();
    }

    @Test
    void belowNModeMatchesUpToAndIncludingTheThreshold() {
        UserAlertSchedule s = schedule(127, ThresholdMode.BELOW_N, (short) 3);
        assertThat(s.matches(0)).isTrue();
        assertThat(s.matches(3)).isTrue();
        assertThat(s.matches(4)).isFalse();
    }

    @Test
    void defaultsAndTimeNormalisation() {
        UserAlertSchedule s = UserAlertSchedule.builder()
                .slotType(SlotType.COMMUTE_OUT)
                .notifyTime(LocalTime.of(18, 5, 42, 123))
                .notifyDays(1)
                .build();

        assertThat(s.getNotifyTime()).isEqualTo(LocalTime.of(18, 5));      // stored at HH:mm:00
        assertThat(s.getThresholdMode()).isEqualTo(ThresholdMode.ALWAYS);
        assertThat(s.getThresholdCount()).isEqualTo((short) 3);
        assertThat(s.isActive()).isTrue();
    }

    @Test
    void toggleFlipsActive() {
        UserAlertSchedule s = schedule(1, ThresholdMode.ALWAYS, null);
        s.toggle();
        assertThat(s.isActive()).isFalse();
        s.toggle();
        assertThat(s.isActive()).isTrue();
    }
}
