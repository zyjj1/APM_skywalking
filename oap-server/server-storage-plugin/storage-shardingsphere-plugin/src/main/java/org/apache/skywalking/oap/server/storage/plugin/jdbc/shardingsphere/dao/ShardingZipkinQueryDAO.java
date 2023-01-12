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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.dao;

import java.io.IOException;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinSpanRecord;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCZipkinQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.DurationWithinTTL;
import zipkin2.Span;
import zipkin2.storage.QueryRequest;

public class ShardingZipkinQueryDAO extends JDBCZipkinQueryDAO {
    public ShardingZipkinQueryDAO(final JDBCHikariCPClient h2Client) {
        super(h2Client);
    }

    @Override
    public List<List<Span>> getTraces(final QueryRequest request, Duration duration) throws IOException {
        return super.getTraces(request,
                               DurationWithinTTL.INSTANCE.getRecordDurationWithinTTL(duration));
    }

    @Override
    protected void buildShardingCondition(StringBuilder sql, List<Object> parameters, long startTimeMillis, long endTimeMillis) {
        sql.append(" and ");
        sql.append(ZipkinSpanRecord.INDEX_NAME).append(".").append(ZipkinSpanRecord.TIME_BUCKET + " >= ?");
        parameters.add(TimeBucket.getRecordTimeBucket(startTimeMillis));

        sql.append(" and ");
        sql.append(ZipkinSpanRecord.INDEX_NAME).append(".").append(ZipkinSpanRecord.TIME_BUCKET + " <= ?");
        parameters.add(TimeBucket.getRecordTimeBucket(endTimeMillis));
    }
}
