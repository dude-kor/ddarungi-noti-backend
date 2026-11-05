package kr.ddarungi.noti.notification;

import kr.ddarungi.noti.domain.schedule.SlotType;

/** Payload published to push.exchange; `sendAtEpochMs` is the exact T:00s the worker must wait for. */
public record PushMessage(Long deviceId, String pushToken, Long scheduleId, SlotType slotType,
                          String stationId, String stationName, int bikeCnt, int rackTotCnt,
                          long sendAtEpochMs) {
}
