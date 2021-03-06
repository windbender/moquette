/*
 * Copyright (c) 2012-2017 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.moquette.server.netty;

import io.moquette.spi.impl.ProtocolProcessor;
import io.moquette.spi.metrics.MetricContext;
import io.moquette.spi.metrics.MetricInterface;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.mqtt.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;

@Sharable
public class NettyMQTTHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(NettyMQTTHandler.class);
    private final ProtocolProcessor m_processor;
    private final MetricInterface m_metric;
    public NettyMQTTHandler(ProtocolProcessor processor, MetricInterface metric ) {
        m_processor = processor;
        m_metric = metric;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object message) {
        String clientID = NettyUtils.clientID(ctx.channel());
        MqttMessage msg = (MqttMessage) message;
        if(msg.decoderResult().isFailure()) {
            LOG.error("there is a problem decoding message for clientID={} because {}",clientID,msg.decoderResult().toString() );
            return;
        }
        if(msg.fixedHeader() == null) {
            LOG.error("this message has a null fixed header");
        }
        MqttMessageType messageType = msg.fixedHeader().messageType();
        SocketAddress rmAddr = ctx.channel().remoteAddress();
        MetricContext mc = null;
        if(m_metric != null) mc =m_metric.startProcess(messageType);
        LOG.debug("Processing MQTT message, type={} from {} at {}", messageType);
        try {
            switch (messageType) {
                case CONNECT:
                    m_processor.processConnect(ctx.channel(), (MqttConnectMessage) msg);
                    break;
                case SUBSCRIBE:
                    m_processor.processSubscribe(ctx.channel(), (MqttSubscribeMessage) msg);
                    break;
                case UNSUBSCRIBE:
                    m_processor.processUnsubscribe(ctx.channel(), (MqttUnsubscribeMessage) msg);
                    break;
                case PUBLISH:
                    m_processor.processPublish(ctx.channel(), (MqttPublishMessage) msg);
                    break;
                case PUBREC:
                    m_processor.processPubRec(ctx.channel(), msg);
                    break;
                case PUBCOMP:
                    m_processor.processPubComp(ctx.channel(), msg);
                    break;
                case PUBREL:
                    m_processor.processPubRel(ctx.channel(), msg);
                    break;
                case DISCONNECT:
                    m_processor.processDisconnect(ctx.channel());
                    break;
                case PUBACK:
                    m_processor.processPubAck(ctx.channel(), (MqttPubAckMessage) msg);
                    break;
                case PINGREQ:
                    m_processor.processPingReq(ctx,  msg);
                    break;
                default:
                    LOG.error("Unkonwn MessageType:{}", messageType);
                    break;
            }
        } catch (Throwable ex) {
            LOG.error("Exception was caught while processing MQTT message, " + ex.getCause(), ex);
            ctx.fireExceptionCaught(ex);
        } finally {
            if(mc != null) mc.processStopped();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String clientID = NettyUtils.clientID(ctx.channel());
        if (clientID != null && !clientID.isEmpty()) {
            LOG.info("Notifying connection lost event. MqttClientId = {}.", clientID);
            m_processor.processConnectionLost(clientID, ctx.channel());
        }
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOG.error(
                "An unexpected exception was caught while processing MQTT message. "
                + "Closing Netty channel. MqttClientId = {}, cause = {}, errorMessage = {}. is {}",
                NettyUtils.clientID(ctx.channel()),
                cause.getCause(),
                cause.getMessage(),cause.toString());
        ctx.close();
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isWritable()) {
            m_processor.notifyChannelWritable(ctx.channel());
        }
        ctx.fireChannelWritabilityChanged();
    }

}
