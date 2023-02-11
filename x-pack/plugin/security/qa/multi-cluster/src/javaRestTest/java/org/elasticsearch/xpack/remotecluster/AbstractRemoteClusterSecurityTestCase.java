/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.remotecluster;

import org.apache.http.HttpHost;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.core.IOUtils;
import org.elasticsearch.core.Strings;
import org.elasticsearch.test.cluster.ElasticsearchCluster;
import org.elasticsearch.test.cluster.FeatureFlag;
import org.elasticsearch.test.cluster.local.LocalClusterConfigProvider;
import org.elasticsearch.test.cluster.util.resource.Resource;
import org.elasticsearch.test.rest.ESRestTestCase;
import org.junit.AfterClass;
import org.junit.Before;

import java.io.IOException;
import java.util.Map;

public abstract class AbstractRemoteClusterSecurityTestCase extends ESRestTestCase {

    protected static final String USER = "test_user";
    protected static final SecureString PASS = new SecureString("x-pack-test-password".toCharArray());
    protected static final String REMOTE_SEARCH_USER = "remote_search_user";
    protected static final String REMOTE_SEARCH_ROLE = "remote_search";

    protected static LocalClusterConfigProvider commonClusterConfig = cluster -> cluster.module("analysis-common")
        .feature(FeatureFlag.NEW_RCS_MODE)
        .setting("xpack.license.self_generated.type", "trial")
        .setting("xpack.security.enabled", "true")
        .setting("xpack.security.authc.token.enabled", "true")
        .setting("xpack.security.authc.api_key.enabled", "true")
        .setting("xpack.security.http.ssl.enabled", "false")
        .setting("xpack.security.transport.ssl.enabled", "true")
        .setting("xpack.security.transport.ssl.key", "transport.key")
        .setting("xpack.security.transport.ssl.certificate", "transport.crt")
        .setting("xpack.security.transport.ssl.certificate_authorities", "transport-ca.crt")
        .setting("xpack.security.transport.ssl.verification_mode", "certificate")
        .keystore("bootstrap.password", "x-pack-test-password")
        .keystore("xpack.security.transport.ssl.secure_key_passphrase", "transport-password")
        .configFile("transport.key", Resource.fromClasspath("ssl/transport.key"))
        .configFile("transport.crt", Resource.fromClasspath("ssl/transport.crt"))
        .configFile("transport-ca.crt", Resource.fromClasspath("ssl/transport-ca.crt"))
        .configFile("remote-cluster.key", Resource.fromClasspath("ssl/remote_cluster.key"))
        .configFile("remote-cluster.crt", Resource.fromClasspath("ssl/remote_cluster.crt"))
        .configFile("remote-cluster-ca.crt", Resource.fromClasspath("ssl/remote-cluster-ca.crt"))
        .user(USER, PASS.toString());

    protected static ElasticsearchCluster fulfillingCluster;
    protected static ElasticsearchCluster queryCluster;
    protected static RestClient fulfillingClusterClient;

    @Before
    public void initFulfillingClusterClient() throws IOException {
        if (fulfillingClusterClient == null) {
            assert fulfillingCluster != null;
            fulfillingClusterClient = buildClient(fulfillingCluster.getHttpAddresses());
        }
    }

    @AfterClass
    public static void closeFulFillingClusterClient() throws IOException {
        IOUtils.close(fulfillingClusterClient);
    }

    @Override
    protected String getTestRestCluster() {
        return queryCluster.getHttpAddresses();
    }

    @Override
    protected Settings restClientSettings() {
        final String token = basicAuthHeaderValue(USER, PASS);
        return Settings.builder().put(ThreadContext.PREFIX + ".Authorization", token).build();
    }

    protected void configureRemoteClustersWithApiKey(String indicesPrivilegesJson) throws IOException {
        // Create API key on FC
        final var createApiKeyRequest = new Request("POST", "/_security/api_key");
        createApiKeyRequest.setJsonEntity(Strings.format("""
            {
              "name": "remote_access_key",
              "role_descriptors": {
                "role": {
                  "cluster": ["cluster:monitor/state"],
                  "index": %s
                }
              }
            }""", indicesPrivilegesJson));
        final Response createApiKeyResponse = performRequestAgainstFulfillingCluster(createApiKeyRequest);
        assertOK(createApiKeyResponse);
        final Map<String, Object> apiKeyMap = responseAsMap(createApiKeyResponse);
        final String encodedRemoteAccessApiKey = (String) apiKeyMap.get("encoded");

        // Update remote cluster settings on QC with the API key
        updateClusterSettings(
            Settings.builder()
                .put("cluster.remote.my_remote_cluster.mode", "proxy")
                .put("cluster.remote.my_remote_cluster.proxy_address", fulfillingCluster.getRemoteClusterServerEndpoint(0))
                .put("cluster.remote.my_remote_cluster.authorization", encodedRemoteAccessApiKey)
                .build()
        );
    }

    protected Response performRequestAgainstFulfillingCluster(Request request) throws IOException {
        request.setOptions(RequestOptions.DEFAULT.toBuilder().addHeader("Authorization", basicAuthHeaderValue(USER, PASS)));
        return fulfillingClusterClient.performRequest(request);
    }

    private RestClient buildClient(final String url) throws IOException {
        final int portSeparator = url.lastIndexOf(':');
        final var httpHost = new HttpHost(
            url.substring(0, portSeparator),
            Integer.parseInt(url.substring(portSeparator + 1)),
            getProtocol()
        );
        return buildClient(Settings.EMPTY, new HttpHost[] { httpHost });
    }
}
