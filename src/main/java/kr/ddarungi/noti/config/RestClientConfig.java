package kr.ddarungi.noti.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /** Spring 6 RestClient for the Seoul Open API: connect 2s / read 3s (spec 2.3). */
    @Bean
    RestClient seoulRestClient(RestClient.Builder builder, SeoulApiProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) props.connectTimeout().toMillis());
        factory.setReadTimeout((int) props.readTimeout().toMillis());
        return builder.baseUrl(props.baseUrl()).requestFactory(factory).build();
    }
}
