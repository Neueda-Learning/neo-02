package com.neobank.module.service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.neobank.module.model.PolicyConfig;
import com.neobank.module.repository.PolicyConfigRepository;

/** Exercises UC07's real transactional write path against the H2 test profile. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PolicyConfigWriterTest {

    @Autowired
    private PolicyConfigWriter writer;

    @Autowired
    private PolicyConfigRepository configs;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void replayingTheSamePostDoesNotCreateASecondVersion() throws Exception {
        long countBefore = configs.count();
        String request = """
                {
                  "supportedResidencies": ["GB", "JP"],
                  "excludedResidencies": ["US"],
                  "restrictionList": [],
                  "sampleEvery": 23
                }
                """;

        MvcResult first = mvc.perform(post("/config")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andReturn();
        MvcResult replay = mvc.perform(post("/config")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andReturn();

        int firstVersion = objectMapper.readTree(first.getResponse().getContentAsString())
                .get("version").asInt();
        int replayVersion = objectMapper.readTree(replay.getResponse().getContentAsString())
                .get("version").asInt();
        assertThat(replayVersion).isEqualTo(firstVersion);
        assertThat(configs.count()).isEqualTo(countBefore + 1);
    }

    @Test
    void publishAllocatesANewVersionAndAnIdenticalReplayIsANoOp() {
        PolicyConfig before = current();
        long countBefore = configs.count();
        List<String> supported = List.of("GB", "CA");
        List<String> excluded = List.of("US");
        List<PolicyConfig.RestrictionEntry> restrictions = List.of(
                new PolicyConfig.RestrictionEntry("Replay Test", "1991-02-03", "test"));

        PolicyConfig first = writer.publish(supported, excluded, restrictions, 13);
        PolicyConfig replay = writer.publish(supported, excluded, restrictions, 13);

        assertThat(first.getVersion()).isEqualTo(before.getVersion() + 1);
        assertThat(replay.getVersion()).isEqualTo(first.getVersion());
        assertThat(configs.count()).isEqualTo(countBefore + 1);
    }

    @Test
    void twoConcurrentPublishersAllocateDistinctConsecutiveVersions() throws Exception {
        int versionBefore = current().getVersion();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<PolicyConfig> first = pool.submit(() -> publishAfterBarrier(
                    ready, start, List.of("GB", "IE"), 17));
            Future<PolicyConfig> second = pool.submit(() -> publishAfterBarrier(
                    ready, start, List.of("GB", "DE"), 19));

            ready.await();
            start.countDown();

            assertThat(Set.of(first.get().getVersion(), second.get().getVersion()))
                    .containsExactlyInAnyOrder(versionBefore + 1, versionBefore + 2);
        }

        assertThat(current().getVersion()).isEqualTo(versionBefore + 2);
    }

    private PolicyConfig publishAfterBarrier(CountDownLatch ready, CountDownLatch start,
                                             List<String> supported, int sampleEvery) throws Exception {
        ready.countDown();
        start.await();
        return writer.publish(supported, List.of("US"), List.of(), sampleEvery);
    }

    private PolicyConfig current() {
        return configs.findFirstByOrderByVersionDesc().orElseThrow();
    }
}
