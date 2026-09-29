package com.example.shortener.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:blocklist;DB_CLOSE_DELAY=-1",
        "shortener.blocklist.domains=blocked.test"})
@AutoConfigureMockMvc
class BlocklistIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void blocklistedDestinationIsRejectedWithInvalidUrl() throws Exception {
        mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://www.blocked.test/promo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_URL"))
                .andExpect(jsonPath("$.error.message").value("URL host is blocklisted"));
    }

    @Test
    void otherDestinationsAreUnaffected() throws Exception {
        mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"http://example.com/fine\"}"))
                .andExpect(status().isCreated());
    }
}
