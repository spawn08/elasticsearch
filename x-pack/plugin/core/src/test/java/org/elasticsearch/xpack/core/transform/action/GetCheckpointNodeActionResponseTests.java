/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.core.transform.action;

import org.elasticsearch.common.io.stream.Writeable.Reader;
import org.elasticsearch.test.AbstractWireSerializingTestCase;
import org.elasticsearch.xpack.core.transform.action.GetCheckpointNodeAction.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GetCheckpointNodeActionResponseTests extends AbstractWireSerializingTestCase<Response> {

    public static Response randomNodeCheckpointResponse() {
        Map<String, long[]> checkpointsByIndex = new HashMap<>();
        int indices = randomIntBetween(1, 10);
        for (int i = 0; i < indices; ++i) {
            List<Long> checkpoints = new ArrayList<>();
            int shards = randomIntBetween(1, 20);
            for (int j = 0; j < shards; ++j) {
                checkpoints.add(randomLongBetween(0, 1_000_000));
            }
            checkpointsByIndex.put(randomAlphaOfLengthBetween(1, 10), checkpoints.stream().mapToLong(l -> l).toArray());
        }
        return new Response(checkpointsByIndex);
    }

    @Override
    protected Reader<Response> instanceReader() {
        return Response::new;
    }

    @Override
    protected Response createTestInstance() {
        return randomNodeCheckpointResponse();
    }
}
