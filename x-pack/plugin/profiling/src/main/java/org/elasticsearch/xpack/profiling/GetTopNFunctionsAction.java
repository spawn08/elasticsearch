/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.profiling;

import org.elasticsearch.action.ActionType;

public final class GetTopNFunctionsAction extends ActionType<GetTopNFunctionsResponse> {
    public static final GetTopNFunctionsAction INSTANCE = new GetTopNFunctionsAction();
    public static final String NAME = "indices:data/read/profiling/topn/functions";

    private GetTopNFunctionsAction() {
        super(NAME);
    }
}
