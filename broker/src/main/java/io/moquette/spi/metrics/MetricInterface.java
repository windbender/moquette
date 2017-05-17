package io.moquette.spi.metrics;

import io.netty.handler.codec.mqtt.MqttMessageType;

/**
 * Created by lumigrow on 5/17/17.
 */
public interface MetricInterface {
    MetricContext startProcess(MqttMessageType messageType);
}
