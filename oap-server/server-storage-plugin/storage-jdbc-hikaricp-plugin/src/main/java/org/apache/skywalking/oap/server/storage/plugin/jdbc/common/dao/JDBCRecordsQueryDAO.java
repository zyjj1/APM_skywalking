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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.RecordCondition;
import org.apache.skywalking.oap.server.core.query.type.Record;
import org.apache.skywalking.oap.server.core.storage.query.IRecordsQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class JDBCRecordsQueryDAO implements IRecordsQueryDAO {
    private final JDBCHikariCPClient jdbcClient;

    @Override
    public List<Record> readRecords(final RecordCondition condition,
                                           final String valueColumnName,
                                           final Duration duration) throws IOException {
        StringBuilder sql = new StringBuilder("select * from " + condition.getName() + " where ");
        List<Object> parameters = new ArrayList<>(10);

        if (condition.getParentEntity() != null && condition.getParentEntity().buildId() != null) {
            sql.append(" ").append(TopN.ENTITY_ID).append(" = ? and");
            parameters.add(condition.getParentEntity().buildId());
        }

        sql.append(" ").append(TopN.TIME_BUCKET).append(" >= ?");
        parameters.add(duration.getStartTimeBucketInSec());
        sql.append(" and ").append(TopN.TIME_BUCKET).append(" <= ?");
        parameters.add(duration.getEndTimeBucketInSec());

        sql.append(" order by ").append(valueColumnName);
        if (condition.getOrder().equals(Order.DES)) {
            sql.append(" desc ");
        } else {
            sql.append(" asc ");
        }
        sql.append(" limit ").append(condition.getTopN());

        List<Record> results = new ArrayList<>();
        try (Connection connection = jdbcClient.getConnection()) {
            try (ResultSet resultSet = jdbcClient.executeQuery(
                connection, sql.toString(), parameters.toArray(new Object[0]))) {
                while (resultSet.next()) {
                    Record record = new Record();
                    record.setName(resultSet.getString(TopN.STATEMENT));
                    final String refId = resultSet.getString(TopN.TRACE_ID);
                    record.setRefId(StringUtil.isEmpty(refId) ? "" : refId);
                    record.setId(record.getRefId());
                    record.setValue(resultSet.getString(valueColumnName));
                    results.add(record);
                }
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }

        return results;
    }

}
