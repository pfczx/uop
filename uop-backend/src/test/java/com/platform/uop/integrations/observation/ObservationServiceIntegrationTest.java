package com.platform.uop.integrations.observation;

import com.platform.uop.monitor.entity.Monitor;
import com.platform.uop.monitor.repository.MonitorRepository;
import com.platform.uop.observation.entity.ObservationBatch;
import com.platform.uop.observation.entity.ObservationEntity;
import com.platform.uop.observation.repository.ObservationBatchRepository;
import com.platform.uop.observation.repository.ObservationRepository;
import com.platform.uop.observation.service.ObservationService;
import com.platform.uop.proto.v1.MetricDescriptor;
import com.platform.uop.proto.v1.MetricType;
import com.platform.uop.proto.v1.MonitorDescriptor;
import com.platform.uop.proto.v1.Observation;
import com.platform.uop.proto.v1.Value;
import com.platform.uop.proto.v1.ValueType;
import com.platform.uop.workspace.entity.Workspace;
import com.platform.uop.workspace.repository.WorkspaceRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.platform.uop.IntegrationTest;

@IntegrationTest
@SpringBootTest
class ObservationServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse("postgres:17"))
        .withDatabaseName("uop")
        .withUsername("postgres")
        .withPassword("postgres");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private ObservationService observationService;

    @Autowired
    private ObservationRepository observationRepository;

    @Autowired
    private ObservationBatchRepository batchRepository;

    @Autowired
    private MonitorRepository monitorRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    private Workspace workspace;
    private Monitor monitor;

    @BeforeEach
    void setUp() {
        observationRepository.deleteAll();
        batchRepository.deleteAll();
        monitorRepository.deleteAll();
        workspaceRepository.deleteAll();

        workspace = workspaceRepository.save(new Workspace("Test Workspace", "Description"));
        
        MonitorDescriptor descriptor = MonitorDescriptor.newBuilder()
            .setId("obs-test-monitor-" + UUID.randomUUID())
            .setName("Observation Test Monitor")
            .setDescription("Monitor for observation tests")
            .setVersion("1.0")
            .setActive(true)
            .addMetrics(MetricDescriptor.newBuilder()
                .setName("cpu.usage")
                .setValueType(ValueType.VALUE_TYPE_DOUBLE)
                .setMetricType(MetricType.METRIC_TYPE_GAUGE)
                .setUnit("percent")
                .build())
            .addMetrics(MetricDescriptor.newBuilder()
                .setName("memory.usage")
                .setValueType(ValueType.VALUE_TYPE_INT64)
                .setMetricType(MetricType.METRIC_TYPE_GAUGE)
                .setUnit("bytes")
                .build())
            .build();
        
        monitor = monitorRepository.save(new Monitor(workspace, descriptor));
    }

    private Observation createObservation(String metric, double value) {
        return Observation.newBuilder()
            .setMetric(metric)
            .setValue(Value.newBuilder().setDoubleValue(value).build())
            .setTimestamp(com.google.protobuf.util.Timestamps.fromMillis(System.currentTimeMillis()))
            .build();
    }

    private Observation createObservation(String metric, long value) {
        return Observation.newBuilder()
            .setMetric(metric)
            .setValue(Value.newBuilder().setIntValue(value).build())
            .setTimestamp(com.google.protobuf.util.Timestamps.fromMillis(System.currentTimeMillis()))
            .build();
    }

    @Nested
    @DisplayName("Batch Save Tests")
    class BatchSaveTests {

        @Test
        @DisplayName("Save valid batch returns saved count")
        void saveBatch_validBatch_returnsSavedCount() {
            String batchId = UUID.randomUUID().toString();
            List<Observation> observations = List.of(
                createObservation("cpu.usage", 42.5),
                createObservation("memory.usage", 1024L)
            );

            ObservationService.BatchSaveResult result = observationService.saveBatch(monitor.getId(), batchId, observations);

            assertThat(result.alreadyProcessed()).isFalse();
            assertThat(result.savedCount()).isEqualTo(2);

            var saved = observationRepository.findAllByMonitor_IdOrderByObservedAtDesc(monitor.getId(), PageRequest.of(0, 10));
            assertThat(saved.getTotalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("Save duplicate batch returns alreadyProcessed=true")
        void saveBatch_duplicateBatch_returnsAlreadyProcessed() {
            String batchId = UUID.randomUUID().toString();
            List<Observation> observations = List.of(createObservation("cpu.usage", 42.5));

            observationService.saveBatch(monitor.getId(), batchId, observations);
            ObservationService.BatchSaveResult result = observationService.saveBatch(monitor.getId(), batchId, observations);

            assertThat(result.alreadyProcessed()).isTrue();
            assertThat(result.savedCount()).isEqualTo(0);

            var saved = observationRepository.findAllByMonitor_IdOrderByObservedAtDesc(monitor.getId(), PageRequest.of(0, 10));
            assertThat(saved.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("Save batch with multiple observations of different types")
        void saveBatch_multipleMetrics_savesAll() {
            String batchId = UUID.randomUUID().toString();
            List<Observation> observations = List.of(
                createObservation("cpu.usage", 42.5),
                createObservation("memory.usage", 2048L),
                createObservation("cpu.usage", 43.0),
                createObservation("memory.usage", 2050L)
            );

            ObservationService.BatchSaveResult result = observationService.saveBatch(monitor.getId(), batchId, observations);

            assertThat(result.savedCount()).isEqualTo(4);

            var saved = observationRepository.findAllByMonitor_IdOrderByObservedAtDesc(monitor.getId(), PageRequest.of(0, 10));
            assertThat(saved.getTotalElements()).isEqualTo(4);
            
            long cpuCount = saved.getContent().stream().filter(o -> "cpu.usage".equals(o.getObservation().getMetric())).count();
            long memCount = saved.getContent().stream().filter(o -> "memory.usage".equals(o.getObservation().getMetric())).count();
            assertThat(cpuCount).isEqualTo(2);
            assertThat(memCount).isEqualTo(2);
        }

        @Test
        @DisplayName("Save batch for non-existent monitor throws NOT_FOUND")
        void saveBatch_monitorNotFound_throwsNotFound() {
            String batchId = UUID.randomUUID().toString();
            List<Observation> observations = List.of(createObservation("cpu.usage", 42.5));

            assertThatThrownBy(() -> observationService.saveBatch(UUID.randomUUID(), batchId, observations))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Empty batch ID throws BAD_REQUEST")
        void saveBatch_emptyBatchId_throwsBadRequest() {
            List<Observation> observations = List.of(createObservation("cpu.usage", 42.5));

            assertThatThrownBy(() -> observationService.saveBatch(monitor.getId(), "", observations))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(BAD_REQUEST));
        }

        @Test
        @DisplayName("Null batch ID throws BAD_REQUEST")
        void saveBatch_nullBatchId_throwsBadRequest() {
            List<Observation> observations = List.of(createObservation("cpu.usage", 42.5));

            assertThatThrownBy(() -> observationService.saveBatch(monitor.getId(), null, observations))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(BAD_REQUEST));
        }

        @Test
        @DisplayName("Batch ID too long throws BAD_REQUEST")
        void saveBatch_batchIdTooLong_throwsBadRequest() {
            String longBatchId = "a".repeat(129);
            List<Observation> observations = List.of(createObservation("cpu.usage", 42.5));

            assertThatThrownBy(() -> observationService.saveBatch(monitor.getId(), longBatchId, observations))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(BAD_REQUEST));
        }

        @Test
        @DisplayName("Batch too large throws BAD_REQUEST")
        void saveBatch_batchTooLarge_throwsBadRequest() {
            String batchId = UUID.randomUUID().toString();
            List<Observation> observations = IntStream.range(0, 1001)
                .mapToObj(i -> createObservation("metric." + i, i))
                .collect(Collectors.toList());

            assertThatThrownBy(() -> observationService.saveBatch(monitor.getId(), batchId, observations))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(BAD_REQUEST));
        }

        @Test
        @DisplayName("Null observation in batch throws BAD_REQUEST")
        void saveBatch_nullObservation_throwsBadRequest() {
            String batchId = UUID.randomUUID().toString();
            List<Observation> observations = new ArrayList<>();
            observations.add(createObservation("cpu.usage", 42.5));
            observations.add(null);

            assertThatThrownBy(() -> observationService.saveBatch(monitor.getId(), batchId, observations))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(BAD_REQUEST));
        }

        @Test
        @DisplayName("Observation without timestamp throws BAD_REQUEST")
        void saveBatch_observationWithoutTimestamp_throwsBadRequest() {
            String batchId = UUID.randomUUID().toString();
            Observation noTimestamp = Observation.newBuilder()
                .setMetric("cpu.usage")
                .setValue(Value.newBuilder().setDoubleValue(42.5).build())
                .build();
            List<Observation> observations = List.of(noTimestamp);

            assertThatThrownBy(() -> observationService.saveBatch(monitor.getId(), batchId, observations))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(BAD_REQUEST));
        }

        @Test
        @DisplayName("Max batch size (1000) works")
        void saveBatch_maxSize_works() {
            String batchId = UUID.randomUUID().toString();
            List<Observation> observations = IntStream.range(0, 1000)
                .mapToObj(i -> createObservation("metric." + i, i))
                .collect(Collectors.toList());

            ObservationService.BatchSaveResult result = observationService.saveBatch(monitor.getId(), batchId, observations);

            assertThat(result.savedCount()).isEqualTo(1000);
        }
    }

    @Nested
    @DisplayName("Query Tests")
    class QueryTests {

        @Test
        @DisplayName("Find by monitor with pagination")
        void findByMonitor_pagination_works() {
            String batchId = UUID.randomUUID().toString();
            List<Observation> observations = IntStream.range(0, 25)
                .mapToObj(i -> createObservation("cpu.usage", i * 1.0))
                .collect(Collectors.toList());
            observationService.saveBatch(monitor.getId(), batchId, observations);

            Page<ObservationEntity> page1 = observationService.findByMonitor(monitor.getId(), PageRequest.of(0, 10));
            Page<ObservationEntity> page2 = observationService.findByMonitor(monitor.getId(), PageRequest.of(1, 10));
            Page<ObservationEntity> page3 = observationService.findByMonitor(monitor.getId(), PageRequest.of(2, 10));

            assertThat(page1.getTotalElements()).isEqualTo(25);
            assertThat(page1.getContent()).hasSize(10);
            assertThat(page2.getContent()).hasSize(10);
            assertThat(page3.getContent()).hasSize(5);
        }

        @Test
        @DisplayName("Find by monitor orders by observedAt desc")
        void findByMonitor_ordersByObservedAtDesc() {
            String batchId = UUID.randomUUID().toString();
            Instant baseTime = Instant.now();
            List<Observation> observations = List.of(
                createObservationWithTimestamp("cpu.usage", 1.0, baseTime.minusSeconds(20)),
                createObservationWithTimestamp("cpu.usage", 2.0, baseTime.minusSeconds(10)),
                createObservationWithTimestamp("cpu.usage", 3.0, baseTime)
            );
            observationService.saveBatch(monitor.getId(), batchId, observations);

            Page<ObservationEntity> page = observationService.findByMonitor(monitor.getId(), PageRequest.of(0, 10));
            
            assertThat(page.getContent().get(0).getObservation().getValue().getDoubleValue()).isGreaterThan(page.getContent().get(1).getObservation().getValue().getDoubleValue());
            assertThat(page.getContent().get(1).getObservation().getValue().getDoubleValue()).isGreaterThan(page.getContent().get(2).getObservation().getValue().getDoubleValue());
        }

    private Observation createObservationWithTimestamp(String metric, double value, Instant timestamp) {
        return Observation.newBuilder()
            .setMetric(metric)
            .setValue(Value.newBuilder().setDoubleValue(value).build())
            .setTimestamp(com.google.protobuf.util.Timestamps.fromMillis(timestamp.toEpochMilli()))
            .build();
    }

        @Test
        @DisplayName("Find by time range works")
        void findByTimeRange_works() {
            Instant now = Instant.now();
            Instant before = now.minusSeconds(10);
            Instant after = now.plusSeconds(10);

            String batchId = UUID.randomUUID().toString();
            List<Observation> observations = List.of(
                createObservation("cpu.usage", 1.0),
                createObservation("cpu.usage", 2.0)
            );
            observationService.saveBatch(monitor.getId(), batchId, observations);

            List<ObservationEntity> inRange = observationService.findByMonitorAndTimeRange(monitor.getId(), before, after);
            List<ObservationEntity> beforeRange = observationService.findByMonitorAndTimeRange(monitor.getId(), 
                before.minusSeconds(20), before.minusSeconds(1));
            List<ObservationEntity> afterRange = observationService.findByMonitorAndTimeRange(monitor.getId(),
                after.plusSeconds(1), after.plusSeconds(20));

            assertThat(inRange).hasSize(2);
            assertThat(beforeRange).isEmpty();
            assertThat(afterRange).isEmpty();
        }

        @Test
        @DisplayName("Find by time range with null bounds throws BAD_REQUEST")
        void findByTimeRange_nullBounds_throwsBadRequest() {
            assertThatThrownBy(() -> observationService.findByMonitorAndTimeRange(monitor.getId(), null, Instant.now()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(BAD_REQUEST));

            assertThatThrownBy(() -> observationService.findByMonitorAndTimeRange(monitor.getId(), Instant.now(), null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(BAD_REQUEST));
        }

        @Test
        @DisplayName("Find by time range with from after to throws BAD_REQUEST")
        void findByTimeRange_fromAfterTo_throwsBadRequest() {
            Instant now = Instant.now();
            assertThatThrownBy(() -> observationService.findByMonitorAndTimeRange(monitor.getId(), now.plusSeconds(10), now))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(BAD_REQUEST));
        }

        @Test
        @DisplayName("Find by non-existent monitor throws NOT_FOUND")
        void findByMonitor_notFound_throwsNotFound() {
            assertThatThrownBy(() -> observationService.findByMonitor(UUID.randomUUID(), PageRequest.of(0, 10)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(NOT_FOUND));

            assertThatThrownBy(() -> observationService.findByMonitorAndTimeRange(UUID.randomUUID(), Instant.now().minusSeconds(10), Instant.now()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Batch with only one observation works")
        void saveBatch_singleObservation_works() {
            String batchId = UUID.randomUUID().toString();
            List<Observation> observations = List.of(createObservation("cpu.usage", 42.5));

            ObservationService.BatchSaveResult result = observationService.saveBatch(monitor.getId(), batchId, observations);

            assertThat(result.savedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Empty observations list (0) is accepted")
        void saveBatch_emptyObservations_accepted() {
            String batchId = UUID.randomUUID().toString();
            List<Observation> observations = List.of();

            ObservationService.BatchSaveResult result = observationService.saveBatch(monitor.getId(), batchId, observations);

            assertThat(result.alreadyProcessed()).isFalse();
            assertThat(result.savedCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("Large number of batches for same monitor")
        void manyBatches_sameMonitor_works() {
            for (int i = 0; i < 50; i++) {
                String batchId = UUID.randomUUID().toString();
                List<Observation> observations = List.of(createObservation("cpu.usage", i * 1.0));
                observationService.saveBatch(monitor.getId(), batchId, observations);
            }

            var page = observationService.findByMonitor(monitor.getId(), PageRequest.of(0, 100));
            assertThat(page.getTotalElements()).isEqualTo(50);
        }

        @Test
        @DisplayName("Concurrent batch saves for same monitor")
        void concurrentBatches_sameMonitor_works() throws InterruptedException {
            int numThreads = 10;
            int batchesPerThread = 5;
            
            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(numThreads);
            List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();

            for (int t = 0; t < numThreads; t++) {
                final int threadNum = t;
                futures.add(executor.submit(() -> {
                    for (int b = 0; b < batchesPerThread; b++) {
                        String batchId = UUID.randomUUID().toString();
                        List<Observation> observations = List.of(
                            createObservation("cpu.usage", threadNum * 100 + b)
                        );
                        observationService.saveBatch(monitor.getId(), batchId, observations);
                    }
                }));
            }

            for (var future : futures) {
                try {
                    future.get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
            executor.shutdown();

            var page = observationService.findByMonitor(monitor.getId(), PageRequest.of(0, 100));
            assertThat(page.getTotalElements()).isEqualTo(numThreads * batchesPerThread);
        }

        @Test
        @DisplayName("Observation with all value types")
        void saveBatch_allValueTypes_works() {
            String batchId = UUID.randomUUID().toString();
            List<Observation> observations = List.of(
                Observation.newBuilder()
                    .setMetric("double.metric")
                    .setValue(Value.newBuilder().setDoubleValue(3.14).build())
                    .setTimestamp(com.google.protobuf.util.Timestamps.fromMillis(System.currentTimeMillis()))
                    .build(),
                Observation.newBuilder()
                    .setMetric("int.metric")
                    .setValue(Value.newBuilder().setIntValue(42L).build())
                    .setTimestamp(com.google.protobuf.util.Timestamps.fromMillis(System.currentTimeMillis()))
                    .build(),
                Observation.newBuilder()
                    .setMetric("uint.metric")
                    .setValue(Value.newBuilder().setUintValue(100L).build())
                    .setTimestamp(com.google.protobuf.util.Timestamps.fromMillis(System.currentTimeMillis()))
                    .build(),
                Observation.newBuilder()
                    .setMetric("string.metric")
                    .setValue(Value.newBuilder().setStringValue("test").build())
                    .setTimestamp(com.google.protobuf.util.Timestamps.fromMillis(System.currentTimeMillis()))
                    .build(),
                Observation.newBuilder()
                    .setMetric("bool.metric")
                    .setValue(Value.newBuilder().setBoolValue(true).build())
                    .setTimestamp(com.google.protobuf.util.Timestamps.fromMillis(System.currentTimeMillis()))
                    .build()
            );

            ObservationService.BatchSaveResult result = observationService.saveBatch(monitor.getId(), batchId, observations);

            assertThat(result.savedCount()).isEqualTo(5);

            var saved = observationRepository.findAllByMonitor_IdOrderByObservedAtDesc(monitor.getId(), PageRequest.of(0, 10));
            assertThat(saved.getTotalElements()).isEqualTo(5);
        }
    }
}