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

package org.apache.skywalking.oap.server.core.analysis.meter.function.max;

import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@ExtendWith(MockitoExtension.class)
public class MaxLabeledFunctionTest {

    private static final DataTable HTTP_CODE_COUNT_1 = new DataTable("200,2|301,2|404,3|502,4");

    private static final DataTable HTTP_CODE_COUNT_2 = new DataTable("200,1|301,4|404,5|502,1|505,1");

    private static final DataTable HTTP_CODE_COUNT_3 = new DataTable("200,2|301,4|404,5|502,4|505,1");

    private MaxLabeledFunction function;

    @BeforeAll
    public static void setup() {
        MeterEntity.setNamingControl(
                new NamingControl(512, 512, 512, new EndpointNameGrouping()));
    }

    @BeforeEach
    public void before() {
        function = new MaxLabeledFunctionInst();
        function.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
    }

    @AfterAll
    public static void tearDown() {
        MeterEntity.setNamingControl(null);
    }

    @Test
    public void testAccept() {
        function.accept(MeterEntity.newService("service-test", Layer.GENERAL), HTTP_CODE_COUNT_1);
        assertThat(function.getValue()).isEqualTo(HTTP_CODE_COUNT_1);

        function.accept(MeterEntity.newService("service-test", Layer.GENERAL), HTTP_CODE_COUNT_2);
        assertThat(function.getValue()).isEqualTo(HTTP_CODE_COUNT_3);
    }

    @Test
    public void testCalculate() {
        function.accept(MeterEntity.newService("service-test", Layer.GENERAL), HTTP_CODE_COUNT_1);
        function.accept(MeterEntity.newService("service-test", Layer.GENERAL), HTTP_CODE_COUNT_2);
        function.calculate();

        assertThat(function.getValue()).isEqualTo(HTTP_CODE_COUNT_3);
    }

    @Test
    public void testToHour() {
        function.accept(MeterEntity.newService("service-test", Layer.GENERAL), HTTP_CODE_COUNT_1);
        function.accept(MeterEntity.newService("service-test", Layer.GENERAL), HTTP_CODE_COUNT_2);
        function.calculate();

        final MaxLabeledFunction hourFunction = (MaxLabeledFunction) function.toHour();
        hourFunction.calculate();

        assertThat(hourFunction.getValue()).isEqualTo(HTTP_CODE_COUNT_3);
    }

    @Test
    public void testToDay() {
        function.accept(
                MeterEntity.newService("service-test", Layer.GENERAL),
                HTTP_CODE_COUNT_1
        );
        function.accept(
                MeterEntity.newService("service-test", Layer.GENERAL),
                HTTP_CODE_COUNT_2
        );
        function.calculate();

        final MaxLabeledFunction dayFunction = (MaxLabeledFunction) function.toDay();
        dayFunction.calculate();

        assertThat(dayFunction.getValue()).isEqualTo(HTTP_CODE_COUNT_3);
    }

    @Test
    public void testSerialize() {
        function.accept(MeterEntity.newService("service-test", Layer.GENERAL), HTTP_CODE_COUNT_1);

        MaxLabeledFunction function2 = new MaxLabeledFunctionInst();
        function2.deserialize(function.serialize().build());

        assertThat(function2.getEntityId()).isEqualTo(function.getEntityId());
        assertThat(function2.getTimeBucket()).isEqualTo(function.getTimeBucket());
        assertThat(function2.getServiceId()).isEqualTo(function.getServiceId());
        assertThat(function2.getValue()).isEqualTo(function.getValue());
    }

    @Test
    public void testBuilder() throws IllegalAccessException, InstantiationException {
        function.accept(MeterEntity.newService("service-test", Layer.GENERAL), HTTP_CODE_COUNT_1);
        function.calculate();

        StorageBuilder<MaxLabeledFunction> storageBuilder = function.builder().newInstance();

        final HashMapConverter.ToStorage toStorage = new HashMapConverter.ToStorage();
        storageBuilder.entity2Storage(function, toStorage);
        final Map<String, Object> map = toStorage.obtain();
        map.put(MaxLabeledFunction.VALUE, ((DataTable) map.get(MaxLabeledFunction.VALUE)).toStorageData());

        MaxLabeledFunction function2 = storageBuilder.storage2Entity(new HashMapConverter.ToEntity(map));

        assertThat(function2.getValue()).isEqualTo(function.getValue());
    }

    private static class MaxLabeledFunctionInst extends MaxLabeledFunction {
        @Override
        public AcceptableValue<DataTable> createNew() {
            return new MaxLabeledFunctionInst();
        }
    }
}
