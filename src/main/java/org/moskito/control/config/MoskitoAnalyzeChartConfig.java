package org.moskito.control.config;

import com.google.gson.annotations.SerializedName;
import org.configureme.annotations.Configure;
import org.configureme.annotations.ConfigureMe;

/**
 * Represents chart properties used in request to MoSKito-Analyze.
 * @author strel
 */
@ConfigureMe
public class MoskitoAnalyzeChartConfig {

    /**
     * Chart name.
     */
    @Configure
    @SerializedName("name")
    private String name;

    /**
     * Chart caption.
     */
    @Configure
    @SerializedName("caption")
    private String caption;

    /**
     * Interval name / type.
     */
    @Configure
    @SerializedName("interval")
    private String interval;

    /**
     * Chart type, i.e. what chart should actually show
     * ( total, average value and so on).
     */
    @Configure
    @SerializedName("type")
    private String type;

    @Configure
    @SerializedName("@hosts")
    private String[] hosts;

    /**
     * Producer name.
     */
    @Configure
    @SerializedName("producer")
    private String producer;

    /**
     * Stat name.
     */
    @Configure
    @SerializedName("stat")
    private String stat;

    /**
     * Value name.
     */
    @Configure
    @SerializedName("value")
    private String value;


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public String getInterval() {
        return interval;
    }

    public void setInterval(String interval) {
        this.interval = interval;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String[] getHosts() {
        return hosts;
    }

    public void setHosts(String[] hosts) {
        this.hosts = hosts;
    }

    public String getProducer() {
        return producer;
    }

    public void setProducer(String producer) {
        this.producer = producer;
    }

    public String getStat() {
        return stat;
    }

    public void setStat(String stat) {
        this.stat = stat;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
