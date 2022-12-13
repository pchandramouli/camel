/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.jms.issues;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.TextMessage;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.jms.core.JmsTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(15)
public class JmsCustomJMSReplyToIssueTest extends AbstractJMSTest {

    private JmsComponent amq;

    @Test
    public void testCustomJMSReplyTo() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");

        // start a inOnly route
        template.sendBody("direct:start", "Hello World");

        JmsTemplate jms = new JmsTemplate(amq.getConfiguration().getConnectionFactory());
        TextMessage msg = (TextMessage) jms.receive("JmsCustomJMSReplyToIssueTest.in");
        assertEquals("Hello World", msg.getText());

        // there should be a JMSReplyTo so we know where to send the reply
        Destination replyTo = msg.getJMSReplyTo();
        assertEquals("queue://JmsCustomJMSReplyToIssueTest.reply", replyTo.toString());

        // send reply
        template.sendBody("activemq:" + replyTo, "Bye World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected JmsComponent buildComponent(ConnectionFactory connectionFactory) {
        amq = super.buildComponent(connectionFactory);

        return amq;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // must preserve QoS so Camel will send JMSReplyTo even if message is inOnly
                from("direct:start").process(exchange -> {
                    exchange.getMessage().setBody("Hello World");
                    // set the JMSReplyTo to force sending the reply here
                    exchange.getMessage().setHeader("JMSReplyTo", "JmsCustomJMSReplyToIssueTest.reply");
                }).to("activemq:queue:JmsCustomJMSReplyToIssueTest.in?preserveMessageQos=true");

                from("activemq:queue:JmsCustomJMSReplyToIssueTest.reply").to("mock:result");
            }
        };
    }

}
