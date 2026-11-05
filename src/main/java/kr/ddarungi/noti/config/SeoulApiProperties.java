package kr.ddarungi.noti.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "seoul-api")
public record SeoulApiProperties(
        String baseUrl,
        String key,
        int chunkSize,
        int maxStations,
        Duration connectTimeout,
        Duration readTimeout) {

    public SeoulApiProperties {
        // The Open API caps a single request at 1,000 rows.
        if (chunkSize <= 0 || chunkSize > 1000) chunkSize = 1000;
        if (maxStations <= 0) maxStations = 3000;
        if (connectTimeout == null) connectTimeout = Duration.ofSeconds(2);
        if (readTimeout == null) readTimeout = Duration.ofSeconds(3);
    }
}
