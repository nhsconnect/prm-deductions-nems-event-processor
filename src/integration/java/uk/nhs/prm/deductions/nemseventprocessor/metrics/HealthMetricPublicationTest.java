package uk.nhs.prm.deductions.nemseventprocessor.metrics;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.ListMetricsRequest;
import software.amazon.awssdk.services.cloudwatch.model.ListMetricsResponse;
import software.amazon.awssdk.services.cloudwatch.model.Metric;
import software.amazon.awssdk.services.cloudwatch.model.MetricDataQuery;
import software.amazon.awssdk.services.cloudwatch.model.MetricDataResult;
import software.amazon.awssdk.services.cloudwatch.model.MetricStat;
import software.amazon.awssdk.services.cloudwatch.model.RecentlyActive;
import software.amazon.awssdk.services.cloudwatch.model.StatusCode;
import uk.nhs.prm.deductions.nemseventprocessor.config.ScheduledConfig;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(ScheduledConfig.class)
@TestPropertySource(properties = {"environment = ci", "metric.health.value = 1.0"})
@ExtendWith(MockitoExtension.class)
class HealthMetricPublicationTest {

    CloudWatchClient cloudWatchClient = CloudWatchClient.create();


    @Test
    void shouldPutHealthMetricDataIntoCloudWatch() throws InterruptedException {

        AppConfig config = new AppConfig("ci", 1.0);

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