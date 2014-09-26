/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.oak.plugins.segment.failover.server;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import org.apache.jackrabbit.oak.plugins.segment.RecordId;
import org.apache.jackrabbit.oak.plugins.segment.Segment;
import org.apache.jackrabbit.oak.plugins.segment.SegmentId;
import org.apache.jackrabbit.oak.plugins.segment.SegmentStore;
import org.apache.jackrabbit.oak.plugins.segment.failover.CommunicationObserver;
import org.apache.jackrabbit.oak.plugins.segment.failover.codec.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Sharable
public class FailoverServerHandler extends SimpleChannelInboundHandler<String> {

    private static final Logger log = LoggerFactory
            .getLogger(FailoverServerHandler.class);

    private final SegmentStore store;
    private final CommunicationObserver observer;
    private final String[] allowedIPRanges;
    public String state;

    public FailoverServerHandler(SegmentStore store, CommunicationObserver observer, String[] allowedIPRanges) {
        this.store = store;
        this.observer = observer;
        this.allowedIPRanges = allowedIPRanges;
    }

    private RecordId headId() {
        if (store != null) {
            return store.getHead().getRecordId();
        }
        return null;
    }

    private static long ipToLong(InetAddress ip) {
        byte[] octets = ip.getAddress();
        long result = 0;
        for (byte octet : octets) {
            result <<= 8;
            result |= octet & 0xff;
        }
        return result;
    }

    private boolean clientAllowed(InetSocketAddress client) {
        if (this.allowedIPRanges != null && this.allowedIPRanges.length > 0) {
            for (String s : this.allowedIPRanges) {
                try {
                    if (ipToLong(InetAddress.getByName(s)) == ipToLong(client.getAddress())) {
                        return true;
                    }
                }
                catch (UnknownHostException ignored) { /* it's an ip range */ }
                int i = s.indexOf('-');
                if (i > 0) {
                    try {
                        long startIPRange = ipToLong(InetAddress.getByName(s.substring(0, i).trim()));
                        long endIPRange = ipToLong(InetAddress.getByName(s.substring(i + 1).trim()));
                        long ipl = ipToLong(client.getAddress());
                        if (startIPRange <= ipl && ipl <= endIPRange) return true;
                    }
                    catch (Exception e) {
                        log.warn("invalid IP-range format: " + s);
                    }
                }
            }
            return false;
        }
        return true;
    }

    @Override
    public void channelRegistered(io.netty.channel.ChannelHandlerContext ctx) throws java.lang.Exception {
        state = "channel registered";
        super.channelRegistered(ctx);
    }

    @Override
    public void channelActive(io.netty.channel.ChannelHandlerContext ctx) throws java.lang.Exception {
        state = "channel active";
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(io.netty.channel.ChannelHandlerContext ctx) throws java.lang.Exception {
        state = "channel inactive";
        super.channelInactive(ctx);
    }

    @Override
    public void channelUnregistered(io.netty.channel.ChannelHandlerContext ctx) throws java.lang.Exception {
        state = "channel unregistered";
        super.channelUnregistered(ctx);
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, String payload)
            throws Exception {
        state = "got message";

        String request = Messages.extractMessageFrom(payload);
        InetSocketAddress client = (InetSocketAddress)ctx.channel().remoteAddress();

        if (!clientAllowed(client)) {
            log.warn("Got request from client " + client + " which is not in the allowed ip ranges! Request will be ignored.");
        }
        else {
            String clientID = Messages.extractClientFrom(payload);
            observer.gotMessageFrom(clientID, request, client);
            if (Messages.GET_HEAD.equalsIgnoreCase(request)) {
                RecordId r = headId();
                if (r != null) {
                    ctx.writeAndFlush(r);
                    log.debug("returning from head request");
                    return;
                }
            } else if (request.startsWith(Messages.GET_SEGMENT)) {
                String sid = request.substring(Messages.GET_SEGMENT.length());
                log.debug("request segment id {}", sid);
                UUID uuid = UUID.fromString(sid);

                Segment s = null;

                for (int i = 0; i < 10; i++) {
                    try {
                        s = store.readSegment(new SegmentId(store.getTracker(),
                                uuid.getMostSignificantBits(), uuid
                                .getLeastSignificantBits()));
                    } catch (IllegalStateException e) {
                        // segment not found
                        log.info("waiting for segment. Got exception: " + e.getMessage());
                        TimeUnit.MILLISECONDS.sleep(1000);
                    }
                    if (s != null) break;
                }

                if (s != null) {
                    log.info("sending segment" + sid + " to " + client);
                    ctx.writeAndFlush(s);
                    observer.didSendSegmentBytes(clientID, s.size());
                    log.debug("master returns from segment request");
                    return;
                }
            } else {
                log.warn("Unknown request {}, ignoring.", request);
            }
        }
        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        state = "exception occurred: " + cause.getMessage();
        log.error(cause.getMessage(), cause);
        ctx.fireExceptionCaught(cause);
    }
}