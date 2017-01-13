/*
 * Copyright (c) 2012-2017 The original author or authorsgetRockQuestions()
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
package io.moquette.parser.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.moquette.parser.proto.messages.AbstractMessage;
import io.moquette.parser.proto.messages.SubscribeMessage;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author andrea
 */
public class SubscribeEncoderTest {
    SubscribeEncoder m_encoder = new SubscribeEncoder();
    ChannelHandlerContext m_mockedContext;
    ByteBuf m_out;
         
    @Before
    public void setUp() {
        //mock the ChannelHandlerContext to return an UnpooledAllocator
        m_mockedContext = TestUtils.mockChannelHandler();
        m_out = Unpooled.buffer();
    }
    
    @Test
    public void testEncodeWithMultiTopic() throws Exception {
        SubscribeMessage msg = new SubscribeMessage();
        msg.setQos(AbstractMessage.QOSType.LEAST_ONE);
        msg.setMessageID(0xAABB);
        
        //variable part
        SubscribeMessage.Couple c1 = new SubscribeMessage.Couple((byte)1, "a/b");
        SubscribeMessage.Couple c2 = new SubscribeMessage.Couple((byte)0, "a/b/c");
        msg.addSubscription(c1);
        msg.addSubscription(c2);

        //Exercise
        m_encoder.encode(m_mockedContext, msg, m_out);

        //Verify
        assertEquals((byte)0x82, (byte)m_out.readByte()); //1 byte
        assertEquals(16, m_out.readByte()); //remaining length
        
        //verify MessageID
        assertEquals((byte)0xAA, m_out.readByte());
        assertEquals((byte)0xBB, m_out.readByte());
        
        //Variable part
        TestUtils.verifyString(c1.topicFilter, m_out);
        assertEquals(c1.qos, m_out.readByte());
        TestUtils.verifyString(c2.topicFilter, m_out);
        assertEquals(c2.qos, m_out.readByte());
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testEncode_empty_subscription() throws Exception {
        SubscribeMessage msg = new SubscribeMessage();
        msg.setQos(AbstractMessage.QOSType.LEAST_ONE);
        msg.setMessageID(0xAABB);

        //Exercise
        m_encoder.encode(m_mockedContext, msg, m_out);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testEncode_badQos() throws Exception {
        SubscribeMessage msg = new SubscribeMessage();
        msg.setQos(AbstractMessage.QOSType.EXACTLY_ONCE);
        msg.setMessageID(0xAABB);

        //Exercise
        m_encoder.encode(m_mockedContext, msg, m_out);
    }
}
