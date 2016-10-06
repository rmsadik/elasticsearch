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

package org.elasticsearch.action.suggest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import com.google.common.base.Joiner;
import org.apache.http.HttpEntity;
import org.apache.http.nio.entity.NStringEntity;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.support.broadcast.BroadcastOperationRequest;
import org.elasticsearch.client.Requests;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.collect.MapBuilder;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.util.UriBuilder;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.search.suggest.SuggestBuilder;

/**
 * A request to get suggestions for corrections of phrases. Best created with
 * {@link org.elasticsearch.client.Requests#suggestRequest(String...)}.
 * <p/>
 * <p>The request requires the suggest query source to be set either using
 * {@link #suggest(org.elasticsearch.common.bytes.BytesReference)} / {@link #suggest(org.elasticsearch.common.bytes.BytesReference, boolean)}
 * or by using {@link #suggest(org.elasticsearch.search.suggest.SuggestBuilder)}
 * (Best created using the {link @org.elasticsearch.search.suggest.SuggestBuilders)}).
 *
 * @see SuggestResponse
 * @see org.elasticsearch.client.Client#suggest(SuggestRequest)
 * @see org.elasticsearch.client.Requests#suggestRequest(String...)
 * @see org.elasticsearch.search.suggest.SuggestBuilders
 */
public final class SuggestRequest extends BroadcastOperationRequest<SuggestRequest> {

    @Nullable
    private String routing;

    @Nullable
    private String preference;

    private BytesReference suggestSource;
    private boolean suggestSourceUnsafe;

    SuggestRequest() {
    }

    /**
     * Constructs a new suggest request against the provided indices. No indices provided means it will
     * run against all indices.
     */
    public SuggestRequest(String... indices) {
        super(indices);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException validationException = super.validate();
        return validationException;
    }

    @Override
    protected void beforeStart() {
        if (suggestSourceUnsafe) {
            suggest(suggestSource.copyBytesArray(), false);
        }
    }

    /**
     * The Phrase to get correction suggestions for 
     */
    BytesReference suggest() {
        return suggestSource;
    }
    
    /**
     * set a new source for the suggest query  
     */
    public SuggestRequest suggest(BytesReference suggestSource) {
        return suggest(suggestSource, false);
    }

    /**
     * set a new source using a {@link org.elasticsearch.search.suggest.SuggestBuilder}
     * for phrase and term suggestion lookup
     */
    public SuggestRequest suggest(SuggestBuilder suggestBuilder) {
        return suggest(suggestBuilder.buildAsBytes(Requests.CONTENT_TYPE));
    }

    /**
     * set a new source using a {@link org.elasticsearch.search.suggest.SuggestBuilder.SuggestionBuilder}
     * for completion suggestion lookup
     */
    public SuggestRequest suggest(SuggestBuilder.SuggestionBuilder suggestionBuilder) {
        return suggest(suggestionBuilder.buildAsBytes(Requests.CONTENT_TYPE));
    }
    
    /**
     * A comma separated list of routing values to control the shards the search will be executed on.
     */
    public String routing() {
        return this.routing;
    }

    /**
     * A comma separated list of routing values to control the shards the search will be executed on.
     */
    public SuggestRequest routing(String routing) {
        this.routing = routing;
        return this;
    }

    /**
     * The routing values to control the shards that the search will be executed on.
     */
    public SuggestRequest routing(String... routings) {
        this.routing = Strings.arrayToCommaDelimitedString(routings);
        return this;
    }

    public SuggestRequest preference(String preference) {
        this.preference = preference;
        return this;
    }

    public String preference() {
        return this.preference;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        routing = in.readOptionalString();
        preference = in.readOptionalString();
        suggest(in.readBytesReference());
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeOptionalString(routing);
        out.writeOptionalString(preference);
        out.writeBytesReference(suggestSource);
    }

    @Override
    public String toString() {
        String sSource = "_na_";
        try {
            sSource = XContentHelper.convertToJson(suggestSource, false);
        } catch (Exception e) {
            // ignore
        }
        return "[" + Arrays.toString(indices) + "]" + ", suggestSource[" + sSource + "]";
    }

    public SuggestRequest suggest(BytesReference suggestSource, boolean contentUnsafe) {
        this.suggestSource = suggestSource;
        this.suggestSourceUnsafe = contentUnsafe;
        return this;
    }

    public SuggestRequest suggest(String source) {
        return suggest(new BytesArray(source));
    }

    @Override
    public String getEndPoint() {
        return UriBuilder.newBuilder().csv(indices()).slash("_suggest").build();
    }

    @Override
    public Map<String, String> getParams() {
        return new MapBuilder<String, String>()
                .putIfNotNull("routing", routing)
                .putIfNotNull("preference", preference).map();
    }

    @Override
    public RestRequest.Method getMethod() {
        return RestRequest.Method.POST;
    }

    @Override
    public HttpEntity getEntity() throws IOException {
        return new NStringEntity(XContentHelper.convertToJson(suggestSource, false), StandardCharsets.UTF_8);
    }
}
