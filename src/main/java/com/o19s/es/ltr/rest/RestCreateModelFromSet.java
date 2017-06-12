/*
 * Copyright [2017] Wikimedia Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.o19s.es.ltr.rest;

import com.o19s.es.ltr.action.CreateModelFromSetAction;
import com.o19s.es.ltr.action.CreateModelFromSetAction.CreateModelFromSetRequestBuilder;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.RestStatusToXContentListener;

import java.io.IOException;

public class RestCreateModelFromSet extends FeatureStoreBaseRestHandler {
    private static final ObjectParser<ParserState, Void> PARSER;
    static {
        PARSER = new ObjectParser<>("model", ParserState::new);
        PARSER.declareString(ParserState::setName, new ParseField("name"));
        PARSER.declareObject(ParserState::setModel,
                StoredLtrModel.LtrModelDefinition::parse,
                new ParseField("model"));
    }

    public RestCreateModelFromSet(Settings settings, RestController controller) {
        super(settings);
        controller.registerHandler(RestRequest.Method.POST, "/_ltr/{store}/_featureset/{name}/_createmodel", this);
        controller.registerHandler(RestRequest.Method.POST, "/_ltr/_featureset/{name}/_createmodel", this);
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        String store = indexName(request);
        Long expectedVersion = null;
        if (request.hasParam("version")) {
            expectedVersion = request.paramAsLong("version", -1);
            if (expectedVersion <= 0) {
                throw new IllegalArgumentException("version must be a strictly positive long value");
            }
        }
        String routing = request.param("routing");
        ParserState state = new ParserState();
        request.withContentOrSourceParamParserOrNull((p) -> ParserState.parse(p, state));
        CreateModelFromSetRequestBuilder builder = CreateModelFromSetAction.INSTANCE.newRequestBuilder(client);
        if (expectedVersion != null) {
            builder.withVersion(store, request.param("name"), expectedVersion, state.name, state.model);
        } else {
            builder.withoutVersion(store, request.param("name"), state.name, state.model);
        }
        builder.routing(routing);
        return (channel) -> builder.execute(new RestStatusToXContentListener<>(channel, (r) -> r.getResponse().getLocation(routing)));
    }

    private static class ParserState {
        String name;
        StoredLtrModel.LtrModelDefinition model;

        public void setName(String name) {
            this.name = name;
        }

        public void setModel(StoredLtrModel.LtrModelDefinition model) {
            this.model = model;
        }

        public static void parse(XContentParser parser, ParserState value) throws IOException {
            PARSER.parse(parser, value, null);
            if (value.name == null) {
                throw new ParsingException(parser.getTokenLocation(), "Missing required value [name]");
            }
        }
    }
}
