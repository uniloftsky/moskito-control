package org.moskito.control.connectors;

import net.anotheria.moskito.core.accumulation.AccumulatedValue;
import net.anotheria.moskito.core.accumulation.Accumulator;
import net.anotheria.moskito.core.accumulation.AccumulatorRepository;
import net.anotheria.moskito.core.accumulation.Accumulators;
import net.anotheria.moskito.core.config.MoskitoConfigurationHolder;
import net.anotheria.moskito.core.config.dashboards.DashboardConfig;
import net.anotheria.moskito.core.config.dashboards.DashboardsConfig;
import net.anotheria.moskito.core.dynamic.OnDemandStatsProducer;
import net.anotheria.moskito.core.dynamic.OnDemandStatsProducerException;
import net.anotheria.moskito.core.predefined.ServiceStatsFactory;
import net.anotheria.moskito.core.producers.CallExecution;
import net.anotheria.moskito.core.producers.IStatsProducer;
import net.anotheria.moskito.core.registry.ProducerRegistryFactory;
import net.anotheria.moskito.core.threshold.Threshold;
import net.anotheria.moskito.core.threshold.ThresholdConditionGuard;
import net.anotheria.moskito.core.threshold.ThresholdRepository;
import net.anotheria.moskito.core.threshold.ThresholdStatus;
import net.anotheria.moskito.core.threshold.Thresholds;
import net.anotheria.moskito.core.threshold.guard.DoubleBarrierPassGuard;
import net.anotheria.moskito.core.threshold.guard.GuardedDirection;
import net.anotheria.util.StringUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.StatusLine;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.moskito.control.connectors.httputils.HttpHelper;
import org.moskito.control.connectors.parsers.ParserHelper;
import org.moskito.control.connectors.response.ConnectorAccumulatorResponse;
import org.moskito.control.connectors.response.ConnectorAccumulatorsNamesResponse;
import org.moskito.control.connectors.response.ConnectorInformationResponse;
import org.moskito.control.connectors.response.ConnectorStatusResponse;
import org.moskito.control.connectors.response.ConnectorThresholdsResponse;
import org.moskito.control.core.HealthColor;
import org.moskito.control.core.accumulator.AccumulatorDataItem;
import org.moskito.control.core.status.Status;
import org.moskito.control.core.threshold.ThresholdDataItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic Http URL connector.
 *
 * @author dzhmud
 * @since 17.04.2017 1:31 PM
 */
public class HttpURLConnector extends AbstractConnector {

    /**
     * Target applications url.
     */
    private String location;

    /**
     * Component name.
     */
    private String componentName;

    /**
     * Target URL credentials.
     */
    private UsernamePasswordCredentials credentials;

    /**
     * Logger.
     */
    private static Logger log = LoggerFactory.getLogger(HttpURLConnector.class);

    @Override
    public void configure(String componentName, String location, String credentials) {
        this.componentName = componentName;
        this.location = location;
        this.credentials = ParserHelper.getCredentials(credentials);
        IStatsProducer producer = ProducerRegistryFactory.getProducerRegistryInstance().getProducer(componentName + "-Producer");
        if (producer == null) {
            initProducer();
        }
    }

    private void initProducer() {
        ProducerRegistryFactory.getProducerRegistryInstance().registerProducer(new OnDemandStatsProducer(componentName + "-Producer", "frontend", "GET", ServiceStatsFactory.DEFAULT_INSTANCE));
        Accumulators.createAccumulator(componentName + "-AVG 1m", componentName + "-Producer", "GET", "Avg", "1m");
        Accumulators.createAccumulator(componentName + "-AVG 15m", componentName + "-Producer", "GET", "Avg", "15m");
        Accumulators.createAccumulator(componentName + "-AVG 1h", componentName + "-Producer", "GET", "Avg", "1h");
        ThresholdConditionGuard[] guards = new ThresholdConditionGuard[]{
                new DoubleBarrierPassGuard(ThresholdStatus.GREEN, 1000, GuardedDirection.DOWN),
                new DoubleBarrierPassGuard(ThresholdStatus.YELLOW, 1000, GuardedDirection.UP),
                new DoubleBarrierPassGuard(ThresholdStatus.ORANGE, 2000, GuardedDirection.UP),
                new DoubleBarrierPassGuard(ThresholdStatus.RED, 5000, GuardedDirection.UP),
                new DoubleBarrierPassGuard(ThresholdStatus.PURPLE, 20000, GuardedDirection.UP)
        };
        Thresholds.addThreshold(componentName + "-AVG 1m", componentName + "-Producer", "GET", "Avg", "1m", guards);

        DashboardConfig dashboard = new DashboardConfig();
        dashboard.setThresholds(new String[]{componentName + "-AVG 1m"});
        dashboard.setName(componentName + "-Dashboard");

        DashboardsConfig dashboardsConfig = MoskitoConfigurationHolder.getConfiguration().getDashboardsConfig();
        if (dashboardsConfig == null) {
            dashboardsConfig = new DashboardsConfig();
        }
        if (dashboardsConfig.getDashboards() == null) {
            dashboardsConfig.setDashboards(new DashboardConfig[]{dashboard});
        } else {
            dashboardsConfig.setDashboards(ArrayUtils.add(dashboardsConfig.getDashboards(), dashboard));
        }

        MoskitoConfigurationHolder.getConfiguration().setDashboardsConfig(dashboardsConfig);
    }

    @Override
    public ConnectorStatusResponse getNewStatus() {
        if (StringUtils.isEmpty(location)) {
            log.error("Location is absent!!");
            return new ConnectorStatusResponse(new Status(HealthColor.PURPLE, "Location is missing!"));
        }
        log.debug("URL to Call " + location);
        CallExecution execution = null;
        try {
            OnDemandStatsProducer producer = (OnDemandStatsProducer) ProducerRegistryFactory.getProducerRegistryInstance().getProducer(componentName + "-Producer");
            if (producer != null) {
                execution = producer.getStats("GET").createCallExecution();
                execution.startExecution(componentName + "-AVG");
            }
        } catch (OnDemandStatsProducerException e) {
            log.warn("Couldn't count this call due to producer error", e);
        }
        Status status;
        try {
            CloseableHttpResponse response = HttpHelper.getHttpResponse(location, credentials);
            String content = HttpHelper.getResponseContent(response);
            if (HttpHelper.isScOk(response)) {
                status = getStatus(content);
            } else {
                if (response.getStatusLine() != null) {
                    StatusLine line = response.getStatusLine();
                    String message = "StatusCode:" + line.getStatusCode() + ", reason: " + line.getReasonPhrase();
                    status = new Status(HealthColor.RED, message);
                } else {
                    log.warn("Failed to connect to URL: " + location);
                    status = new Status(HealthColor.PURPLE, content);
                }
            }
        } catch (Throwable e) {
            log.warn("Failed to connect to URL: " + location, e);
            status = new Status(HealthColor.PURPLE, e.getMessage());
        }
        ConnectorStatusResponse newStatus = new ConnectorStatusResponse(status);
        if (execution != null)
            execution.finishExecution();
        return newStatus;
    }


    private Status getStatus(String content) {
        final HealthColor result;
        content = (content == null) ? "" : content.trim();
        switch (content.toLowerCase()) {
            case "down":
            case "red":
            case "failed":
                result = HealthColor.RED;
                break;
            case "yellow":
                result = HealthColor.YELLOW;
                break;
            default:
                result = HealthColor.GREEN;
                content = "";
        }
        return new Status(result, content);
    }

    @Override
    public ConnectorThresholdsResponse getThresholds() {
        ConnectorThresholdsResponse response = new ConnectorThresholdsResponse();
        List<ThresholdDataItem> dataItems = new ArrayList<>();
        for (Threshold threshold : ThresholdRepository.getInstance().getThresholds()) {
            if (threshold.getName().startsWith(componentName + "-AVG")) {
                ThresholdDataItem dataItem = new ThresholdDataItem();
                dataItem.setName(threshold.getName());
                dataItem.setStatus(HealthColor.getHealthColor(threshold.getStatus()));
                dataItem.setLastValue(threshold.getLastValue());
                dataItem.setStatusChangeTimestamp(threshold.getStatusChangeTimestamp());
                dataItems.add(dataItem);
            }
        }
        response.setItems(dataItems);
        return response;
    }

    @Override
    public ConnectorAccumulatorResponse getAccumulators(List<String> accumulatorNames) {
        ConnectorAccumulatorResponse response = new ConnectorAccumulatorResponse();
        for (Accumulator accumulator : AccumulatorRepository.getInstance().getAccumulators()) {
            if (accumulator.getName().startsWith(componentName + "-AVG")) {
                List<AccumulatorDataItem> dataItems = new ArrayList<>();
                for (AccumulatedValue accumulatedValue : accumulator.getValues()) {
                    dataItems.add(new AccumulatorDataItem(accumulatedValue.getTimestamp(), accumulatedValue.getValue()));
                }
                response.addDataLine(accumulator.getName(), dataItems);
            }
        }
        return response;
    }

    @Override
    public ConnectorAccumulatorsNamesResponse getAccumulatorsNames() throws IOException {
        List<String> names = new ArrayList<>();
        for (Accumulator accumulator : AccumulatorRepository.getInstance().getAccumulators()) {
            if (accumulator.getName().startsWith(componentName + "-AVG")) {
                names.add(accumulator.getName());
            }
        }
        return new ConnectorAccumulatorsNamesResponse(names);
    }

    @Override
    public ConnectorInformationResponse getInfo() {
        return null;
    }

    @Override
    public boolean supportsThresholds() {
        return true;
    }

    @Override
    public boolean supportsAccumulators() {
        return true;
    }
}
