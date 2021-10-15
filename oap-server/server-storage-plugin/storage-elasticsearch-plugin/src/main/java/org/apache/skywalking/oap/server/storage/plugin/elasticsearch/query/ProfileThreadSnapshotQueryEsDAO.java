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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query;

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Sort;
import org.apache.skywalking.library.elasticsearch.requests.search.aggregation.Aggregation;
import org.apache.skywalking.library.elasticsearch.requests.search.aggregation.AggregationBuilder;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.profile.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;

public class ProfileThreadSnapshotQueryEsDAO extends EsDAO
    implements IProfileThreadSnapshotQueryDAO {

    private final int querySegmentMaxSize;

    protected final ProfileThreadSnapshotRecord.Builder builder =
        new ProfileThreadSnapshotRecord.Builder();

    public ProfileThreadSnapshotQueryEsDAO(ElasticSearchClient client,
                                           int profileTaskQueryMaxSize) {
        super(client);
        this.querySegmentMaxSize = profileTaskQueryMaxSize;
    }

    @Override
    public List<BasicTrace> queryProfiledSegments(String taskId) {
        final BoolQueryBuilder segmentIdQuery =
            Query.bool()
                 .must(Query.term(ProfileThreadSnapshotRecord.TASK_ID, taskId))
                 .must(Query.term(ProfileThreadSnapshotRecord.SEQUENCE, 0));

        final SearchBuilder search =
            Search.builder().query(segmentIdQuery)
                  .size(querySegmentMaxSize)
                  .sort(
                      ProfileThreadSnapshotRecord.DUMP_TIME,
                      Sort.Order.DESC
                  );

        SearchResponse response =
            getClient().search(
                IndexController.LogicIndicesRegister.getPhysicalTableName(
                    ProfileThreadSnapshotRecord.INDEX_NAME),
                search.build()
            );

        final List<String> segmentIds = new LinkedList<>();
        for (SearchHit searchHit : response.getHits().getHits()) {
            segmentIds.add(
                (String) searchHit.getSource().get(ProfileThreadSnapshotRecord.SEGMENT_ID));
        }

        if (CollectionUtils.isEmpty(segmentIds)) {
            return Collections.emptyList();
        }

        final BoolQueryBuilder traceQuery = Query.bool();
        for (String segmentId : segmentIds) {
            traceQuery.should(Query.term(SegmentRecord.SEGMENT_ID, segmentId));
        }
        final SearchBuilder traceSearch =
            Search.builder().query(traceQuery)
                  .size(segmentIds.size())
                  .sort(SegmentRecord.START_TIME, Sort.Order.DESC);

        response = getClient().search(SegmentRecord.INDEX_NAME, traceSearch.build());

        List<BasicTrace> result = new ArrayList<>();
        for (SearchHit searchHit : response.getHits().getHits()) {
            BasicTrace basicTrace = new BasicTrace();

            basicTrace.setSegmentId((String) searchHit.getSource().get(SegmentRecord.SEGMENT_ID));
            basicTrace.setStart(
                String.valueOf(searchHit.getSource().get(SegmentRecord.START_TIME)));
            basicTrace.getEndpointNames().add(
                IDManager.EndpointID.analysisId(
                    (String) searchHit.getSource().get(SegmentRecord.ENDPOINT_ID)
                ).getEndpointName());
            basicTrace.setDuration(
                ((Number) searchHit.getSource().get(SegmentRecord.LATENCY)).intValue());
            basicTrace.setError(BooleanUtils.valueToBoolean(
                ((Number) searchHit.getSource().get(SegmentRecord.IS_ERROR)).intValue()));
            basicTrace.getTraceIds()
                      .add((String) searchHit.getSource().get(SegmentRecord.TRACE_ID));

            result.add(basicTrace);
        }

        return result;
    }

    @Override
    public int queryMinSequence(String segmentId, long start, long end) {
        return querySequenceWithAgg(
            Aggregation.min(ProfileThreadSnapshotRecord.SEQUENCE)
                       .field(ProfileThreadSnapshotRecord.SEQUENCE),
            segmentId, start, end
        );
    }

    @Override
    public int queryMaxSequence(String segmentId, long start, long end) {
        return querySequenceWithAgg(
            Aggregation.max(ProfileThreadSnapshotRecord.SEQUENCE)
                       .field(ProfileThreadSnapshotRecord.SEQUENCE),
            segmentId, start, end
        );
    }

    @Override
    public List<ProfileThreadSnapshotRecord> queryRecords(String segmentId,
                                                          int minSequence,
                                                          int maxSequence) {
        final String index = IndexController.LogicIndicesRegister.getPhysicalTableName(
            ProfileThreadSnapshotRecord.INDEX_NAME);

        final BoolQueryBuilder query =
            Query.bool()
                 .must(Query.term(ProfileThreadSnapshotRecord.SEGMENT_ID, segmentId))
                 .must(Query.range(ProfileThreadSnapshotRecord.SEQUENCE)
                            .gte(minSequence)
                            .lt(maxSequence));

        final SearchBuilder search =
            Search.builder().query(query)
                  .size(maxSequence - minSequence);
        final SearchResponse response = getClient().search(index, search.build());

        List<ProfileThreadSnapshotRecord> result = new ArrayList<>(maxSequence - minSequence);
        for (SearchHit searchHit : response.getHits().getHits()) {
            ProfileThreadSnapshotRecord record = builder.storage2Entity(searchHit.getSource());

            result.add(record);
        }
        return result;
    }

    @Override
    public SegmentRecord getProfiledSegment(String segmentId) {
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(SegmentRecord.INDEX_NAME);
        final SearchBuilder search =
            Search.builder()
                  .query(Query.term(SegmentRecord.SEGMENT_ID, segmentId))
                  .size(1);

        final SearchResponse response = getClient().search(index, search.build());

        if (response.getHits().getHits().isEmpty()) {
            return null;
        }
        final SearchHit searchHit = response.getHits().iterator().next();
        final SegmentRecord segmentRecord = new SegmentRecord();
        segmentRecord.setSegmentId((String) searchHit.getSource().get(SegmentRecord.SEGMENT_ID));
        segmentRecord.setTraceId((String) searchHit.getSource().get(SegmentRecord.TRACE_ID));
        segmentRecord.setServiceId((String) searchHit.getSource().get(SegmentRecord.SERVICE_ID));
        segmentRecord.setStartTime(
            ((Number) searchHit.getSource().get(SegmentRecord.START_TIME)).longValue());
        segmentRecord.setLatency(
            ((Number) searchHit.getSource().get(SegmentRecord.LATENCY)).intValue());
        segmentRecord.setIsError(
            ((Number) searchHit.getSource().get(SegmentRecord.IS_ERROR)).intValue());
        String dataBinaryBase64 = (String) searchHit.getSource().get(SegmentRecord.DATA_BINARY);
        if (!Strings.isNullOrEmpty(dataBinaryBase64)) {
            segmentRecord.setDataBinary(Base64.getDecoder().decode(dataBinaryBase64));
        }
        return segmentRecord;
    }

    protected int querySequenceWithAgg(AggregationBuilder aggregationBuilder,
                                       String segmentId, long start, long end) {
        final BoolQueryBuilder query =
            Query.bool()
                 .must(Query.term(ProfileThreadSnapshotRecord.SEGMENT_ID, segmentId))
                 .must(Query.range(ProfileThreadSnapshotRecord.DUMP_TIME).gte(start).lte(end));

        final SearchBuilder search = 
            Search.builder()
                  .query(query).size(0)
                  .aggregation(aggregationBuilder);
        final String index = IndexController.LogicIndicesRegister.getPhysicalTableName(
            ProfileThreadSnapshotRecord.INDEX_NAME);
        final SearchResponse response = getClient().search(index, search.build());
        final Map<String, Object> agg =
            (Map<String, Object>) response.getAggregations()
                                          .get(ProfileThreadSnapshotRecord.SEQUENCE);

        return ((Number) agg.get("value")).intValue();
    }
}
