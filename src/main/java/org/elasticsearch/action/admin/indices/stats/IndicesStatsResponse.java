/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.action.admin.indices.stats;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.elasticsearch.action.ShardOperationFailedException;
import org.elasticsearch.action.support.broadcast.BroadcastOperationResponse;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.*;

import java.io.IOException;
import java.util.*;

/**
 */
public class IndicesStatsResponse extends BroadcastOperationResponse implements ToXContent {

    private ShardStats[] shards;

    private ImmutableMap<ShardRouting, CommonStats> shardStatsMap;

    IndicesStatsResponse() {

    }

    IndicesStatsResponse(ShardStats[] shards, ClusterState clusterState, int totalShards, int successfulShards, int failedShards, List<ShardOperationFailedException> shardFailures) {
        super(totalShards, successfulShards, failedShards, shardFailures);
        this.shards = shards;
    }

    public ImmutableMap<ShardRouting, CommonStats> asMap() {
        if (shardStatsMap == null) {
            ImmutableMap.Builder<ShardRouting, CommonStats> mb = ImmutableMap.builder();
            for (ShardStats ss : shards) {
                mb.put(ss.getShardRouting(), ss.getStats());
            }

            shardStatsMap = mb.build();
        }
        return shardStatsMap;
    }

    public ShardStats[] getShards() {
        return this.shards;
    }

    public ShardStats getAt(int position) {
        return shards[position];
    }

    public IndexStats getIndex(String index) {
        return getIndices().get(index);
    }

    private Map<String, IndexStats> indicesStats;

    public Map<String, IndexStats> getIndices() {
        if (indicesStats != null) {
            return indicesStats;
        }
        Map<String, IndexStats> indicesStats = Maps.newHashMap();

        Set<String> indices = Sets.newHashSet();
        for (ShardStats shard : shards) {
            indices.add(shard.getIndex());
        }

        for (String index : indices) {
            List<ShardStats> shards = Lists.newArrayList();
            for (ShardStats shard : this.shards) {
                if (shard.getShardRouting().index().equals(index)) {
                    shards.add(shard);
                }
            }
            indicesStats.put(index, new IndexStats(index, shards.toArray(new ShardStats[shards.size()])));
        }
        this.indicesStats = indicesStats;
        return indicesStats;
    }

    private CommonStats total = null;

    public CommonStats getTotal() {
        if (total != null) {
            return total;
        }
        CommonStats stats = new CommonStats();
        for (ShardStats shard : shards) {
            stats.add(shard.getStats());
        }
        total = stats;
        return stats;
    }

    private CommonStats primary = null;

    public CommonStats getPrimaries() {
        if (primary != null) {
            return primary;
        }
        CommonStats stats = new CommonStats();
        for (ShardStats shard : shards) {
            if (shard.getShardRouting().primary()) {
                stats.add(shard.getStats());
            }
        }
        primary = stats;
        return stats;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        shards = new ShardStats[in.readVInt()];
        for (int i = 0; i < shards.length; i++) {
            shards[i] = ShardStats.readShardStats(in);
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeVInt(shards.length);
        for (ShardStats shard : shards) {
            shard.writeTo(out);
        }
    }

    @Override
    public void readFrom(XContentObject in) throws IOException {
        super.readFrom(in);
        XContentHelper.populate(in, JsonField.values(), this);
        if (this.indicesStats != null) {
            List<ShardStats> shardStatss = new ArrayList<>();
            for (Map.Entry<String, IndexStats> entry : indicesStats.entrySet()) {
                ShardStats[] shards = entry.getValue().getShards();
                if (shards != null) {
                    shardStatss.addAll(Arrays.asList(shards));
                }
            }
            this.shards = shardStatss.toArray(new ShardStats[shardStatss.size()]);
        }
    }


    enum JsonField implements XContentObjectParseable<IndicesStatsResponse> {

        _all {
            @Override
            public void apply(XContentObject in, IndicesStatsResponse response) throws IOException {
                XContentObject all = in.getAsXContentObject(this);
                response.primary = new CommonStats();
                response.primary.readFrom(all.getAsXContentObject("primaries"));

                response.total = new CommonStats();
                response.total.readFrom(all.getAsXContentObject("total"));
            }
        },
        indices {
            @Override
            public void apply(XContentObject in, IndicesStatsResponse response) throws IOException {
                XContentObject indicesObject = in.getAsXContentObject(this);
                for (String index : indicesObject.keySet()) {
                    XContentObject indexDetails = indicesObject.getAsXContentObject(index);
                    IndexStats indexShard = new IndexStats(index, indexDetails);
                    if (response.indicesStats == null) {
                        response.indicesStats = new LinkedHashMap<>();
                    }
                    response.indicesStats.put(index, indexShard);
                }
            }
        }

    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        String level = params.param("level", "indices");
        boolean isLevelValid = "indices".equalsIgnoreCase(level) || "shards".equalsIgnoreCase(level) || "cluster".equalsIgnoreCase(level);
        if (!isLevelValid) {
            return builder;
        }

        builder.startObject("_all");

        builder.startObject("primaries");
        getPrimaries().toXContent(builder, params);
        builder.endObject();

        builder.startObject("total");
        getTotal().toXContent(builder, params);
        builder.endObject();

        builder.endObject();

        if ("indices".equalsIgnoreCase(level) || "shards".equalsIgnoreCase(level)) {
            builder.startObject(Fields.INDICES);
            for (IndexStats indexStats : getIndices().values()) {
                builder.startObject(indexStats.getIndex(), XContentBuilder.FieldCaseConversion.NONE);

                builder.startObject("primaries");
                indexStats.getPrimaries().toXContent(builder, params);
                builder.endObject();

                builder.startObject("total");
                indexStats.getTotal().toXContent(builder, params);
                builder.endObject();

                if ("shards".equalsIgnoreCase(level)) {
                    builder.startObject(Fields.SHARDS);
                    for (IndexShardStats indexShardStats : indexStats) {
                        builder.startArray(Integer.toString(indexShardStats.getShardId().id()));
                        for (ShardStats shardStats : indexShardStats) {
                            builder.startObject();
                            shardStats.toXContent(builder, params);
                            builder.endObject();
                        }
                        builder.endArray();
                    }
                    builder.endObject();
                }
                builder.endObject();
            }
            builder.endObject();
        }

        return builder;
    }

    static final class Fields {
        static final XContentBuilderString INDICES = new XContentBuilderString("indices");
        static final XContentBuilderString SHARDS = new XContentBuilderString("shards");
    }

    @Override
    public String toString() {
        try {
            XContentBuilder builder = XContentFactory.jsonBuilder().prettyPrint();
            builder.startObject();
            toXContent(builder, EMPTY_PARAMS);
            builder.endObject();
            return builder.string();
        } catch (IOException e) {
            return "{ \"error\" : \"" + e.getMessage() + "\"}";
        }
    }
}
