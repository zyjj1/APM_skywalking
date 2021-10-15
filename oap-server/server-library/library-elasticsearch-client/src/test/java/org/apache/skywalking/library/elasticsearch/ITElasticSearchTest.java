/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.library.elasticsearch;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.library.elasticsearch.client.TemplateClient;
import org.apache.skywalking.library.elasticsearch.requests.IndexRequest;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.aggregation.Aggregation;
import org.apache.skywalking.library.elasticsearch.response.IndexTemplate;
import org.apache.skywalking.library.elasticsearch.response.Mappings;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.awaitility.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RequiredArgsConstructor
@RunWith(Parameterized.class)
public class ITElasticSearchTest {

    @Parameterized.Parameters(name = "version: {0}")
    public static Collection<Object[]> es() {
        return Arrays.asList(new Object[][] {
            {
                "ElasticSearch 6.3.2",
                new ElasticsearchContainer(
                    DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch-oss")
                                   .withTag("6.3.2"))
            },
            {
                "ElasticSearch 7.4.2",
                new ElasticsearchContainer(
                    DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch-oss")
                                   .withTag("7.4.2"))
            },
            {
                "ElasticSearch 7.8.0",
                new ElasticsearchContainer(
                    DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch-oss")
                                   .withTag("7.8.0"))
            },
            {
                "ElasticSearch 7.15.0",
                new ElasticsearchContainer(
                    DockerImageName.parse("elastic/elasticsearch")
                                   .withTag("7.15.0")
                                   .asCompatibleSubstituteFor(
                                       "docker.elastic.co/elasticsearch/elasticsearch-oss"))
            },
            {
                "OpenSearch 1.0.0",
                new ElasticsearchContainer(
                    DockerImageName.parse("opensearchproject/opensearch")
                                   .withTag("1.0.0")
                                   .asCompatibleSubstituteFor(
                                       "docker.elastic.co/elasticsearch/elasticsearch-oss"))
                    .withEnv("plugins.security.disabled", "true")
            }
        });
    }

    @Parameterized.Parameter
    public String ignored;
    @Parameterized.Parameter(1)
    public ElasticsearchContainer server;
    private ElasticSearch client;

    @Before
    public void setup() {
        server.start();

        client = ElasticSearch.builder()
                              .endpoints(server.getHttpHostAddress())
                              .build();
        client.connect();
    }

    @After
    public void tearDown() {
        server.stop();
    }

    @Test
    public void testTemplate() {
        final String name = "test-template";
        final TemplateClient templateClient = client.templates();

        final ImmutableMap<String, Object> properties = ImmutableMap.of(
            "metric_table", ImmutableMap.of("type", "keyword"),
            "service_id", ImmutableMap.of("type", "keyword")
        );
        final Mappings mappings = Mappings.builder()
                                          .type("_doc")
                                          .properties(properties)
                                          .build();

        assertThat(templateClient.createOrUpdate(name, ImmutableMap.of(), mappings, 0))
            .isTrue();

        assertThat(templateClient.exists(name)).isTrue();

        assertThat(templateClient.get(name))
            .isPresent()
            .map(IndexTemplate::getMappings)
            .map(Mappings::getProperties)
            .hasValue(mappings.getProperties());
    }

    @Test
    public void testIndex() {
        final String index = "test-index";
        assertFalse(client.index().exists(index));
        assertFalse(client.index().get(index).isPresent());
        assertTrue(client.index().create(index, null, null));
        assertTrue(client.index().exists(index));
        assertNotNull(client.index().get(index));
        assertTrue(client.index().delete(index));
        assertFalse(client.index().get(index).isPresent());
    }

    @Test
    public void testDoc() {
        final String index = "test-index";
        assertTrue(client.index().create(index, null, null));

        final ImmutableMap<String, Object> doc = ImmutableMap.of("key", "val");
        final String idWithSpace = "an id"; // UI management templates' IDs contains spaces
        final String type = "type";

        client.documents().index(
            IndexRequest.builder()
                        .index(index)
                        .type(type)
                        .id(idWithSpace)
                        .doc(doc)
                        .build(), null);

        assertTrue(client.documents().get(index, type, idWithSpace).isPresent());
        assertEquals(client.documents().get(index, type, idWithSpace).get().getId(), idWithSpace);
        assertEquals(client.documents().get(index, type, idWithSpace).get().getSource(), doc);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSearch() {
        final String index = "test-index";
        assertTrue(
            client.index().create(
                index,
                Mappings.builder()
                        .type("type")
                        .properties(ImmutableMap.of("key1", ImmutableMap.of("type", "keyword")))
                        .properties(ImmutableMap.of("key2", ImmutableMap.of("type", "keyword")))
                        .build(),
                null
            )
        );

        final String type = "type";

        for (int i = 0; i < 10; i++) {
            client.documents().index(
                IndexRequest.builder()
                            .index(index)
                            .type(type)
                            .id("id" + i)
                            .doc(ImmutableMap.of("key1", "val" + i, "key2", "val" + (i + 1)
                            ))
                            .build(), null);
        }

        await().atMost(Duration.ONE_MINUTE).untilAsserted(() -> {
            SearchResponse response = client.search(
                Search.builder().query(Query.bool().must(Query.term("key1", "val1"))).build()
            );
            assertEquals(1, response.getHits().getTotal());
            assertEquals("val1", response.getHits().iterator().next().getSource().get("key1"));
        });

        await().atMost(Duration.ONE_MINUTE)
               .pollInterval(Duration.FIVE_SECONDS)
               .untilAsserted(() -> {
                   SearchResponse response = client.search(
                       Search.builder()
                             .query(
                                 Query.bool()
                                      .must(
                                          Query.bool()
                                               .should(Query.bool()
                                                            .must(Query.term("key1", "val1"))
                                                            .must(Query.term("key2", "val2"))
                                                            .build())
                                               .should(Query.bool()
                                                            .must(Query.term("key1", "val3"))
                                                            .must(Query.term("key2", "val4"))
                                                            .build()
                                               )))
                             .aggregation(
                                 Aggregation
                                     .terms("key1").field("key1.keyword")
                                     .subAggregation(
                                         Aggregation.terms("key2").field("key2.keyword"))
                                     .size(1000)
                                     .build())
                             .build());
                   assertEquals(2, response.getHits().getTotal());
                   assertEquals(1, response.getAggregations().size());
                   assertEquals(
                       2,
                       (
                           (List<?>)
                               ((Map<String, ?>) response.getAggregations().get("key1"))
                                   .get("buckets")
                       ).size()
                   );
               });
    }
}
