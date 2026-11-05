# REST API (v1)

The contract between this repository and the mobile app (`ddarungi-noti-mobile`). **Change it here first**,
then update the app's models (`lib/features/*/domain`) in the same release.

- Base path: `/api/v1`, JSON, UTF-8, times in KST.
- **Authentication**: every endpoint except `POST /devices` and the station endpoints needs the header
  `X-Device-Token: <client-generated id, max 64 chars>`. The device must have been registered first.

## Errors

All errors are `{ "code": "...", "message": "..." }`.

| HTTP | code | meaning |
|------|------|---------|
| 400 | `VALIDATION_FAILED` / `BAD_REQUEST` | invalid or malformed input |
| 400 | `INVALID_TIME` | `notifyTime` is not on a 5-minute step |
| 401 | `MISSING_DEVICE_TOKEN` | header absent |
| 401 | `UNKNOWN_DEVICE` | token not registered – call `POST /devices`, then retry (the app does this automatically) |
| 404 | `STATION_NOT_FOUND`, `SCHEDULE_NOT_FOUND` | |
| 409 | `FAVORITE_LIMIT_REACHED` | more than 5 favourites |
| 500 | `INTERNAL_ERROR` | |

## Shared shapes

```jsonc
// StationAvailability – served from Redis (60 s TTL); stale=true means the Seoul API is down
{ "bikeCnt": 7, "rackTotCnt": 15, "shared": 47, "status": "NORMAL", "updatedAt": 1762300000000, "stale": false }

// Station
{ "stationId": "ST-4", "stationNo": "102", "stationName": "망원역 1번출구 앞",
  "lat": 37.5558578, "lng": 126.910463, "rackTotCnt": 15,
  "availability": { /* StationAvailability, may be null */ }, "distanceM": 120 /* nearby only */ }

// Schedule  (notifyDays bitmask: Mon=1 Tue=2 Wed=4 Thu=8 Fri=16 Sat=32 Sun=64, weekdays=31)
{ "scheduleId": 3, "slotType": "COMMUTE_IN", "stationId": "ST-4", "stationNo": "102",
  "stationName": "망원역 1번출구 앞", "notifyTime": "08:30", "notifyDays": 31,
  "thresholdMode": "BELOW_N", "thresholdCount": 3, "active": true }
```

## Endpoints

| Method & path | Body / query | Response |
|---|---|---|
| `POST /devices` | `{ "pushToken": "<FCM token>", "platform": "ANDROID"\|"IOS", "appVersion": "0.1.0" }` + `X-Device-Token` | `{ "deviceId": 1 }` – upsert |
| `GET /stations/search` | `q` – name, station number or chosung (`ㅁㅇㅇ`) | `Station[]` (max 20) |
| `GET /stations/nearby` | `lat`, `lng`, `radius` (m, 100–5000, default 1000), `limit` (≤300, default 100) | `Station[]`, nearest first, with availability |
| `GET /home` | – | `{ "commuteIn": Slot\|null, "commuteOut": Slot\|null }`, `Slot = { "schedule": Schedule, "availability": StationAvailability\|null }` |
| `POST /schedules` | `{ slotType, stationId, notifyTime "HH:mm", notifyDays 1–127, thresholdMode "ALWAYS"\|"BELOW_N", thresholdCount 1–20 }` | `Schedule` – one per (device, slotType); posting again replaces it |
| `PATCH /schedules/{id}/toggle` | – | `Schedule` with `active` flipped |
| `GET /favorites` | – | `{ "count": 3, "max": 5, "items": [ { "favoriteId", "station": Station, "schedule": Schedule\|null } ] }` |
| `POST /favorites/{stationId}` | – | `204`, idempotent |
| `DELETE /favorites/{stationId}` | – | `204` |

## Push payload

Sent through FCM HTTP v1 at `notifyTime:00` (high priority, time-sensitive) for schedules whose day bit and threshold
match the live count. Tokens answering `UNREGISTERED` / `410` deactivate the device.
