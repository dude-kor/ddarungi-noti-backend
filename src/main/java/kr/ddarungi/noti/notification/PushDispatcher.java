package kr.ddarungi.noti.notification;

import kr.ddarungi.noti.cache.StationAvailability;
import kr.ddarungi.noti.cache.StationRealtimeService;
import kr.ddarungi.noti.config.RabbitConfig;
import kr.ddarungi.noti.domain.schedule.UserAlertSchedule;
import kr.ddarungi.noti.domain.schedule.UserAlertScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pre-fetch dispatcher. Runs at :45 of every minute, resolves target minute T (= now + 15s), matches live
 * counts and enqueues push messages; the worker releases them at exactly T:00.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushDispatcher {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter MINUTE_KEY = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final UserAlertScheduleRepository scheduleRepository;
    private final StationRealtimeService realtime;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redis;

    @Scheduled(cron = "45 * * * * *", zone = "Asia/Seoul")
    public void dispatch() {
        ZonedDateTime target = ZonedDateTime.now(KST).plusSeconds(15).truncatedTo(ChronoUnit.MINUTES);
        dispatchFor(target);
    }

    int dispatchFor(ZonedDateTime target) {
        // With several app instances only one may dispatch a given minute.
        Boolean first = redis.opsForValue()
                .setIfAbsent("dispatch:" + target.format(MINUTE_KEY), "1", Duration.ofMinutes(2));
        if (!Boolean.TRUE.equals(first)) return 0;

        LocalTime time = target.toLocalTime();
        DayOfWeek day = target.getDayOfWeek();
        List<UserAlertSchedule> due = scheduleRepository.findDue(time).stream()
                .filter(s -> s.firesOn(day)).toList();
        if (due.isEmpty()) return 0;

        Set<String> stationIds = due.stream().map(s -> s.getStation().getStationId()).collect(Collectors.toSet());
        Map<String, StationAvailability> live = realtime.getAvailability(stationIds);
        long sendAt = target.toInstant().toEpochMilli();

        int sent = 0;
        for (UserAlertSchedule s : due) {
            StationAvailability av = live.get(s.getStation().getStationId());
            if (av == null || !s.matches(av.bikeCnt())) continue;
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY,
                    new PushMessage(s.getDevice().getDeviceId(), s.getDevice().getPushToken(), s.getScheduleId(),
                            s.getSlotType(), s.getStation().getStationId(), s.getStation().getStationName(),
                            av.bikeCnt(), av.rackTotCnt(), sendAt));
            sent++;
        }
        log.info("Dispatch {}: {} due, {} queued", target.format(MINUTE_KEY), due.size(), sent);
        return sent;
    }
}
