package my.gov.dbkl.klcares.configoauth2;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.cloud.config.client.ConfigServicePropertySourceLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

@Order(0)
@Configuration
public class CustomConfigLocator extends ConfigServicePropertySourceLocator {
    public static final Logger LOGGER = LoggerFactory.getLogger(CustomConfigLocator.class);
    private String accessToken;

    @Autowired
    private ConfigClientProperties configClientProperties;

    @Bean
    @ConditionalOnMissingBean
    public CustomConfigClientProperties customConfigClientProperties() {
        return new CustomConfigClientProperties();
    }

    public CustomConfigLocator(ConfigClientProperties defaults) {
        super(defaults);
        setRestTemplate(new RestTemplate());
    }

    @Primary
    @Bean
    public ConfigServicePropertySourceLocator configServicePropertySourceLocator() {
        ConfigServicePropertySourceLocator sourceLocator = new ConfigServicePropertySourceLocator(configClientProperties);
        sourceLocator.setRestTemplate(restTemplate());
        return sourceLocator;
    }

    private RestTemplate restTemplate() {
        final RestTemplate template = new RestTemplate();
        template.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().add("Authorization", "Bearer " + getAccessToken());
            return execution.execute(request, body);
        });

        return template;
    }

    private String getAccessToken() {
        if (null == accessToken) {
            try {
                LOGGER.info("Creating access token.");
                final RestTemplate template = new RestTemplate();
                final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
                form.add("client_id", customConfigClientProperties().getClientId());
                form.add("client_secret", customConfigClientProperties().getClientSecret());
                form.add("scope", customConfigClientProperties().getScope());
                form.add("grant_type", customConfigClientProperties().getGrantType());
                final HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, new HttpHeaders());
                final String jsonResponse = template.postForEntity(customConfigClientProperties().getUrl(), request, String.class).getBody();
                final ObjectMapper mapper = new ObjectMapper();
                accessToken = mapper.readTree(jsonResponse).get("access_token").asText();
            } catch (Exception e) {
                LOGGER.error("Failed to get token.", e);
            }
        }
        return accessToken;
    }
}
