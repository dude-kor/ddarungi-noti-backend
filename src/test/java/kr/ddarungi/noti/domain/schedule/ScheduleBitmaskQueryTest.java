package kr.ddarungi.noti.domain.schedule;

import jakarta.persistence.EntityManagerFactory;
import kr.ddarungi.noti.domain.device.Platform;
import kr.ddarungi.noti.domain.device.UserDevice;
import kr.ddarungi.noti.domain.station.StationMaster;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real V1__init.sql (Flyway) on MySQL 8 and checks that the weekday bitmask is filtered BY THE DATABASE,
 * agrees with {@link UserAlertSchedule#firesOn} for every day, and that device + station arrive without N+1.
 * Skipped automatically when no Docker daemon is available.
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ScheduleBitmaskQueryTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.39")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    private static final LocalTime EIGHT_THIRTY = LocalTime.of(8, 30);

    @Autowired
    UserAlertScheduleRepository repository;
    @Autowired
    TestEntityManager em;
    @Autowired
    EntityManagerFactory emf;

    private StationMaster station;
    private int deviceSeq;

    @BeforeEach
    void seedStation() {
        station = em.persist(StationMaster.fromOpenApi("ST-1", "102. 망원역 1번출구 앞", 15, null, null));
        em.flush();
    }

    private UserAlertSchedule schedule(int days, LocalTime time, boolean scheduleActive, boolean deviceActive) {
        UserDevice device = UserDevice.builder()
                .deviceToken("dev-" + (++deviceSeq)).pushToken("push-" + deviceSeq)
                .platform(Platform.ANDROID).appVersion("1.0").build();
        if (!deviceActive) device.deactivate();
        em.persist(device);

        UserAlertSchedule s = UserAlertSchedule.builder()
                .device(device).slotType(SlotType.COMMUTE_IN).station(station)
                .notifyTime(time).notifyDays(days).thresholdMode(ThresholdMode.ALWAYS).thresholdCount((short) 3)
                .build();
        if (!scheduleActive) s.toggle();
        return em.persistAndFlush(s);
    }

    private Set<Long> dueIds(LocalTime time, DayOfWeek day) {
        return repository.findDue(time, day).stream().map(UserAlertSchedule::getScheduleId)
                .collect(Collectors.toSet());
    }

    // ---- correctness -----------------------------------------------------------------------------------------

    @Test
    void dayBitsFollowTheSpecification() {
        assertThat(UserAlertSchedule.dayBit(DayOfWeek.MONDAY)).isEqualTo(1);
        assertThat(UserAlertSchedule.dayBit(DayOfWeek.TUESDAY)).isEqualTo(2);
        assertThat(UserAlertSchedule.dayBit(DayOfWeek.WEDNESDAY)).isEqualTo(4);
        assertThat(UserAlertSchedule.dayBit(DayOfWeek.THURSDAY)).isEqualTo(8);
        assertThat(UserAlertSchedule.dayBit(DayOfWeek.FRIDAY)).isEqualTo(16);
        assertThat(UserAlertSchedule.dayBit(DayOfWeek.SATURDAY)).isEqualTo(32);
        assertThat(UserAlertSchedule.dayBit(DayOfWeek.SUNDAY)).isEqualTo(64);
        assertThat(UserAlertSchedule.WEEKDAYS).isEqualTo(31);
    }

    @Test
    void databaseFilterMatchesTheJavaPredicateForEveryDayAndEveryMask() {
        // all 127 non-empty masks, one schedule each, same minute
        List<UserAlertSchedule> all = new ArrayList<>();
        for (int mask = 1; mask <= 127; mask++) {
            all.add(schedule(mask, EIGHT_THIRTY, true, true));
        }
        em.clear();

        for (DayOfWeek day : DayOfWeek.values()) {
            Set<Long> expected = all.stream().filter(s -> s.firesOn(day))
                    .map(UserAlertSchedule::getScheduleId).collect(Collectors.toSet());

            assertThat(dueIds(EIGHT_THIRTY, day)).isEqualTo(expected);
            assertThat(expected.size()).isEqualTo(64);              // exactly half of the 127 masks have each bit
        }
    }

    @Test
    void weekdayWeekendAndSingleDaySchedulesAreSeparated() {
        UserAlertSchedule weekdays = schedule(UserAlertSchedule.WEEKDAYS, EIGHT_THIRTY, true, true);
        UserAlertSchedule weekend = schedule(32 | 64, EIGHT_THIRTY, true, true);
        UserAlertSchedule mondayOnly = schedule(1, EIGHT_THIRTY, true, true);
        UserAlertSchedule sundayOnly = schedule(64, EIGHT_THIRTY, true, true);
        em.clear();

        assertThat(dueIds(EIGHT_THIRTY, DayOfWeek.MONDAY))
                .containsExactlyInAnyOrder(weekdays.getScheduleId(), mondayOnly.getScheduleId());
        assertThat(dueIds(EIGHT_THIRTY, DayOfWeek.FRIDAY)).containsExactlyInAnyOrder(weekdays.getScheduleId());
        assertThat(dueIds(EIGHT_THIRTY, DayOfWeek.SATURDAY)).containsExactlyInAnyOrder(weekend.getScheduleId());
        assertThat(dueIds(EIGHT_THIRTY, DayOfWeek.SUNDAY))
                .containsExactlyInAnyOrder(weekend.getScheduleId(), sundayOnly.getScheduleId());
    }

    @Test
    void onlyTheRequestedMinuteActiveSchedulesAndActiveDevicesAreReturned() {
        UserAlertSchedule due = schedule(127, EIGHT_THIRTY, true, true);
        schedule(127, LocalTime.of(8, 35), true, true);             // other minute
        schedule(127, EIGHT_THIRTY, false, true);                   // schedule switched off
        schedule(127, EIGHT_THIRTY, true, false);                   // device deactivated (FCM UNREGISTERED)
        em.clear();

        assertThat(dueIds(EIGHT_THIRTY, DayOfWeek.WEDNESDAY)).containsExactly(due.getScheduleId());
        assertThat(dueIds(LocalTime.of(8, 31), DayOfWeek.WEDNESDAY)).isEmpty();
    }

    // ---- N+1 / fetch join ------------------------------------------------------------------------------------

    @Test
    void deviceAndStationAreLoadedWithoutFurtherQueries() {
        for (int i = 0; i < 20; i++) {
            schedule(127, EIGHT_THIRTY, true, true);
        }
        em.clear();
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        List<UserAlertSchedule> due = repository.findDue(EIGHT_THIRTY, DayOfWeek.TUESDAY);
        long statementsForTheScan = stats.getPrepareStatementCount();

        // touching the associations must not hit the database again
        for (UserAlertSchedule s : due) {
            assertThat(Hibernate.isInitialized(s.getDevice())).isTrue();
            assertThat(Hibernate.isInitialized(s.getStation())).isTrue();
            assertThat(s.getDevice().getPushToken()).isNotNull();
            assertThat(s.getStation().getStationName()).isEqualTo("망원역 1번출구 앞");
        }

        assertThat(due).hasSize(20);
        assertThat(statementsForTheScan).isEqualTo(2L);             // 1 native id query + 1 fetch-join query
        assertThat(stats.getPrepareStatementCount()).isEqualTo(statementsForTheScan);   // no lazy loads afterwards
    }

    @Test
    void emptyResultCostsOneStatement() {
        em.clear();
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        assertThat(repository.findDue(EIGHT_THIRTY, DayOfWeek.MONDAY)).isEmpty();
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1L);
    }
}
