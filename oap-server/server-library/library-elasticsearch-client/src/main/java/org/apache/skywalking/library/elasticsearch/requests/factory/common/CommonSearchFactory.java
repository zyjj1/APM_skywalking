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

package org.apache.skywalking.library.elasticsearch.requests.factory.common;

import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpRequestBuilder;
import com.linecorp.armeria.common.MediaType;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.library.elasticsearch.ElasticSearchVersion;
import org.apache.skywalking.library.elasticsearch.requests.factory.SearchFactory;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;

@Slf4j
@RequiredArgsConstructor
public final class CommonSearchFactory implements SearchFactory {
    private final ElasticSearchVersion version;

    @SneakyThrows
    @Override
    public HttpRequest search(Search search,
                              Map<String, ?> queryParams,
                              String... indices) {
        final HttpRequestBuilder builder = HttpRequest.builder();

        if (indices == null || indices.length == 0) {
            builder.get("/_search");
        } else {
            builder.get("/{indices}/_search")
                   .pathParam("indices", String.join(",", indices));
        }

        if (queryParams != null && !queryParams.isEmpty()) {
            queryParams.forEach(builder::queryParam);
        }

        final byte[] content = version.codec().encode(search);

        if (log.isDebugEnabled()) {
            log.debug("Search request: {}", new String(content));
        }

        return builder.content(MediaType.JSON, content)
                      .build();
    }
}
