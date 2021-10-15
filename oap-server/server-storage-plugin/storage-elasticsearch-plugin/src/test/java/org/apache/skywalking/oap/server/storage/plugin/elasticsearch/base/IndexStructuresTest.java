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
 *
 */

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base;

import java.util.HashMap;
import org.apache.skywalking.library.elasticsearch.response.Mappings;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.junit.Assert;
import org.junit.Test;

public class IndexStructuresTest {

    @Test
    public void getMapping() {
        IndexStructures structures = new IndexStructures();
        HashMap<String, Object> properties = new HashMap<>();
        properties.put("a", "b");
        properties.put("c", "d");
        structures.putStructure(
            "test", Mappings.builder()
                            .type(ElasticSearchClient.TYPE)
                            .properties(properties)
                            .build());
        Mappings mapping = structures.getMapping("test");
        Assert.assertEquals(mapping.getProperties(), properties);

        structures.putStructure(
            "test2", Mappings.builder()
                             .type(ElasticSearchClient.TYPE)
                             .properties(new HashMap<>())
                             .build());
        mapping = structures.getMapping("test2");

        Assert.assertTrue(mapping.getProperties().isEmpty());
    }

    @Test
    public void resolveStructure() {
        IndexStructures structures = new IndexStructures();
        HashMap<String, Object> properties = new HashMap<>();
        properties.put("a", "b");
        properties.put("c", "d");
        structures.putStructure(
            "test", Mappings.builder()
                            .type(ElasticSearchClient.TYPE)
                            .properties(properties)
                            .build());
        Mappings mapping = structures.getMapping("test");
        Assert.assertEquals(properties, mapping.getProperties());
        HashMap<String, Object> properties2 = new HashMap<>();
        properties2.put("a", "b");
        properties2.put("f", "g");
        structures.putStructure(
            "test", Mappings.builder()
                            .type(ElasticSearchClient.TYPE)
                            .properties(properties2)
                            .build());
        mapping = structures.getMapping("test");
        HashMap<String, Object> res = new HashMap<>();
        res.put("a", "b");
        res.put("c", "d");
        res.put("f", "g");
        Assert.assertEquals(res, mapping.getProperties());
    }

    @Test
    public void diffStructure() {
        IndexStructures structures = new IndexStructures();
        HashMap<String, Object> properties = new HashMap<>();
        properties.put("a", "b");
        properties.put("c", "d");
        properties.put("f", "g");
        structures.putStructure(
            "test", Mappings.builder()
                            .type(ElasticSearchClient.TYPE)
                            .properties(properties)
                            .build());
        HashMap<String, Object> properties2 = new HashMap<>();
        properties2.put("a", "b");
        Mappings diffMappings = structures.diffStructure(
            "test", Mappings.builder()
                            .type(ElasticSearchClient.TYPE)
                            .properties(properties2)
                            .build());
        HashMap<String, Object> res = new HashMap<>();
        res.put("c", "d");
        res.put("f", "g");
        Assert.assertEquals(res, diffMappings.getProperties());
        diffMappings = structures.diffStructure(
            "test",
            Mappings.builder()
                    .type(ElasticSearchClient.TYPE)
                    .properties(properties)
                    .build()
        );
        Assert.assertEquals(new HashMap<>(), diffMappings.getProperties());
    }

    @Test
    public void containsStructure() {
        IndexStructures structures = new IndexStructures();
        HashMap<String, Object> properties = new HashMap<>();
        properties.put("a", "b");
        properties.put("c", "d");
        properties.put("f", "g");
        structures.putStructure("test", Mappings.builder()
                                                .type(ElasticSearchClient.TYPE)
                                                .properties(properties)
                                                .build());

        HashMap<String, Object> properties2 = new HashMap<>();
        properties2.put("a", "b");
        properties2.put("c", "d");
        Assert.assertTrue(structures.containsStructure(
            "test",
            Mappings.builder()
                    .type(ElasticSearchClient.TYPE)
                    .properties(properties2)
                    .build()
        ));

        HashMap<String, Object> properties3 = new HashMap<>();
        properties3.put("a", "b");
        properties3.put("q", "d");
        Assert.assertFalse(structures.containsStructure(
            "test",
            Mappings.builder()
                    .type(ElasticSearchClient.TYPE)
                    .properties(properties3)
                    .build()
        ));
    }
}
