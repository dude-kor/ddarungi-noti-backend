package kr.ddarungi.noti.notification;

import kr.ddarungi.noti.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

/**
 * Consumes push.queue. Each message is handed to its own virtual thread which sleeps until T:00s and then
 * calls FCM, so thousands of pending alerts never occupy listener threads. The listener acks on hand-off:
 * a crash inside the ≤15s wait loses those alerts, which is acceptable for a time-boxed notification
 * (queue TTL is 60s anyway).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushDeliveryWorker {

    private final FcmSender fcmSender;
    private final Semaphore fcmPermits = new Semaphore(256);     // bound concurrent FCM calls

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onMessage(PushMessage message) {
        Thread.ofVirtual().name("push-" + message.scheduleId()).start(() -> deliver(message));
    }

    private void deliver(PushMessage m) {
        try {
            long wait = m.sendAtEpochMs() - System.currentTimeMillis();
            if (wait > 0) Thread.sleep(wait);
            fcmPermits.acquire();
            try {
                fcmSender.send(m);
            } finally {
                fcmPermits.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Push delivery failed for schedule {}", m.scheduleId(), e);
        }
    }
}
