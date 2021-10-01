package uk.nhs.prm.deductions.nemseventprocessor.metrics;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;
import uk.nhs.prm.deductions.nemseventprocessor.AppConfig;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
class HealthMetricPublicationTest {

    CloudWatchClient cloudWatchClient = CloudWatchClient.create();

    @Test
    void shouldPutHealthMetricDataIntoCloudWatch() throws InterruptedException {
        AppConfig config = Mockito.mock(AppConfig.class);
        when(config.environment()).thenReturn("ci");

        HealthMetricPublisher publisher = new HealthMetricPublisher(cloudWatchClient, config);

        publisher.publishHealthyStatus();

        List<Metric> metrics = fetchMetricsMatching("PrmDeductions/NemsEventProcessor", "Health");
        assertThat(metrics).isNotEmpty();

        int count = 0;
        MetricDataResult metricData;
        do {
            Thread.sleep(5000);
            metricData = fetchRecentMetricData(2, getMetricWhere(metrics, metricHasDimension("Environment", "ci")));
            if (count++ > 10) {
                break;
            }
        } while (metricData.values().isEmpty());

        System.out.println(metricData.values());
        System.out.println(metricData.timestamps());

        assertThat(metricData.values()).isNotEmpty();
        assertThat(metricData.values().get(0)).isEqualTo(1.0);
    }

    @NotNull
    private Predicate<Metric> metricHasDimension(String name, String value) {
        return metric -> metric.dimensions().stream().anyMatch(dimension ->
                dimension.name().equals(name) && dimension.value().equals(value));
    }

    private <Metric> Predicate<Metric> all(Predicate<Metric>... predicates) {
        return Arrays.stream(predicates).reduce(Predicate::and).orElse(x -> true);
    }

    private Metric getMetricWhere(List<Metric> metrics, Predicate<Metric> metricPredicate) {
        List<Metric> filteredMetrics = metrics.stream().filter(metricPredicate).collect(toList());
        return filteredMetrics.get(0);
    }
    private List<Metric> fetchMetricsMatching(String namespace, String metricName) {
        ListMetricsRequest request = ListMetricsRequest.builder()
                .namespace(namespace)
                .metricName(metricName)
                .recentlyActive(RecentlyActive.PT3_H)
                .build();

        ListMetricsResponse listMetricsResponse = cloudWatchClient.listMetrics(request);
        return listMetricsResponse.metrics();
    }

    private MetricDataResult fetchRecentMetricData(int minutesOfRecency, Metric metric) {
        MetricDataQuery dataQuery = MetricDataQuery.builder()
                .id("health_test_query")
                .metricStat(MetricStat.builder()
                        .metric(metric)
                        .period(1)
                        .stat("Minimum")
                        .build())
                .returnData(true)
                .build();
        GetMetricDataRequest request = GetMetricDataRequest.builder()
                .startTime(Instant.now().minusSeconds(minutesOfRecency * 60).truncatedTo(ChronoUnit.MINUTES))
                .endTime(Instant.now().truncatedTo(ChronoUnit.MINUTES))
                .metricDataQueries(dataQuery)
                .build();

        List<MetricDataResult> metricDataResults = cloudWatchClient.getMetricData(request).metricDataResults();
        System.out.println("metric data results size: " + metricDataResults.size());
        assertThat(metricDataResults.size()).isEqualTo(1);

        MetricDataResult metricDataResult = metricDataResults.get(0);
        assertThat(metricDataResult.statusCode()).isEqualTo(StatusCode.COMPLETE);
        System.out.println("metric data result status: " + metricDataResult.statusCodeAsString());
        System.out.println("metric data result hasValues: " + metricDataResult.hasValues());
        System.out.println("metric data result hasTimestamps: " + metricDataResult.hasTimestamps());
        return metricDataResult;
    }
}