/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.application.connector;

import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.io.stream.StreamInput;

import java.io.IOException;
import java.util.Map;

public class ConnectorSearchResult extends ConnectorsAPISearchResult {

    public ConnectorSearchResult(StreamInput in) throws IOException {
        super(in);
    }

    private ConnectorSearchResult(BytesReference resultBytes, Map<String, Object> resultMap, String id) {
        super(resultBytes, resultMap, id);
    }

    public static class Builder {

        private BytesReference resultBytes;
        private Map<String, Object> resultMap;
        private String id;

        public Builder setResultBytes(BytesReference resultBytes) {
            this.resultBytes = resultBytes;
            return this;
        }

        public Builder setResultMap(Map<String, Object> resultMap) {
            this.resultMap = resultMap;
            return this;
        }

        public Builder setId(String id) {
            this.id = id;
            return this;
        }

        public ConnectorSearchResult build() {
            return new ConnectorSearchResult(resultBytes, resultMap, id);
        }
    }
}
