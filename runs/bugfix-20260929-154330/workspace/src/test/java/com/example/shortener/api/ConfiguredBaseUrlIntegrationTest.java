package com.example.shortener.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With shortener.base-url set, short URLs use it instead of the request's host (no host-header injection). */
@SpringBootTest(properties = "shortener.base-url=https://sho.rt")
@AutoConfigureMockMvc
class ConfiguredBaseUrlIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void shortUrlUsesTheConfiguredBaseUrl() throws Exception {
        String body = mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/configured-base\"}")
                        .header("Host", "attacker.example"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(body, "$.code");

        assertThat((String) JsonPath.read(body, "$.shortUrl")).isEqualTo("https://sho.rt/" + code);
    }
}
