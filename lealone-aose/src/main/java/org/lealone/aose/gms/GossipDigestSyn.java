/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lealone.aose.gms;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.List;

import org.lealone.aose.net.IVersionedSerializer;
import org.lealone.aose.util.TypeSizes;

/**
 * This is the first message that gets sent out as a start of the Gossip protocol in a
 * round.
 */
public class GossipDigestSyn {
    public static final IVersionedSerializer<GossipDigestSyn> serializer = new GossipDigestSynSerializer();

    final String clusterId;
    final List<GossipDigest> gDigests;

    public GossipDigestSyn(String clusterId, List<GossipDigest> gDigests) {
        this.clusterId = clusterId;
        this.gDigests = gDigests;
    }

    List<GossipDigest> getGossipDigests() {
        return gDigests;
    }

    private static class GossipDigestSynSerializer implements IVersionedSerializer<GossipDigestSyn> {
        @Override
        public void serialize(GossipDigestSyn gDigestSynMessage, DataOutput out, int version) throws IOException {
            out.writeUTF(gDigestSynMessage.clusterId);
            GossipDigestSerializationHelper.serialize(gDigestSynMessage.gDigests, out, version);
        }

        @Override
        public GossipDigestSyn deserialize(DataInput in, int version) throws IOException {
            String clusterId = in.readUTF();
            List<GossipDigest> gDigests = GossipDigestSerializationHelper.deserialize(in, version);
            return new GossipDigestSyn(clusterId, gDigests);
        }

        @Override
        public long serializedSize(GossipDigestSyn syn, int version) {
            long size = TypeSizes.NATIVE.sizeof(syn.clusterId);
            size += GossipDigestSerializationHelper.serializedSize(syn.gDigests, version);
            return size;
        }
    }
}
