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

package org.apache.skywalking.oap.server.core.alarm.provider.feishu;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.testing.junit4.server.ServerRule;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmRulesWatcher;
import org.apache.skywalking.oap.server.core.alarm.provider.Rules;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class FeishuHookCallbackTest {
    private final AtomicBoolean isSuccess = new AtomicBoolean();
    private final AtomicBoolean checkSign = new AtomicBoolean();
    private final AtomicInteger count = new AtomicInteger();
    private final String secret = "dummy-secret";

    @Rule
    public final ServerRule server = new ServerRule() {
        @Override
        protected void configure(ServerBuilder sb) {
            sb.service("/feishuhook/receiveAlarm", (ctx, req) -> HttpResponse.from(
                req.aggregate().thenApply(r -> {
                    final String content = r.content().toStringUtf8();
                    final JsonObject jsonObject = new Gson().fromJson(content, JsonObject.class);
                    final String type = jsonObject.get("msg_type").getAsString();
                    if (checkSign.get()) {
                        String timestamp = jsonObject.get("timestamp").getAsString();
                        String sign = jsonObject.get("sign").getAsString();
                        if (StringUtil.isEmpty(timestamp) || StringUtil.isEmpty(sign)) {
                            return HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR);
                        }
                    }
                    if (type.equalsIgnoreCase("text")) {
                        count.incrementAndGet();
                        if (count.get() == 2) {
                            isSuccess.set(true);
                        }
                        return HttpResponse.of(HttpStatus.OK);
                    }
                    return HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR);
                })));
        }
    };

    @Test
    public void testFeishuWebhookWithoutSign() {
        List<FeishuSettings.WebHookUrl> webHooks = new ArrayList<>();
        webHooks.add(new FeishuSettings.WebHookUrl("", "http://127.0.0.1:" + server.httpPort() + "/feishuhook/receiveAlarm?token=dummy_token"));
        Rules rules = new Rules();
        String template = "{\"msg_type\":\"text\",\"content\":{\"text\":\"Skywaling alarm: %s\"}}";
        rules.setFeishus(FeishuSettings.builder().webhooks(webHooks).textTemplate(template).build());

        AlarmRulesWatcher alarmRulesWatcher = new AlarmRulesWatcher(rules, null);
        FeishuHookCallback feishuHookCallback = new FeishuHookCallback(alarmRulesWatcher);
        List<AlarmMessage> alarmMessages = new ArrayList<>(2);
        AlarmMessage alarmMessage = new AlarmMessage();
        alarmMessage.setScopeId(DefaultScopeDefine.SERVICE);
        alarmMessage.setRuleName("service_resp_time_rule");
        alarmMessage.setAlarmMessage("alarmMessage with [DefaultScopeDefine.All]");
        alarmMessages.add(alarmMessage);
        AlarmMessage anotherAlarmMessage = new AlarmMessage();
        anotherAlarmMessage.setRuleName("service_resp_time_rule_2");
        anotherAlarmMessage.setScopeId(DefaultScopeDefine.ENDPOINT);
        anotherAlarmMessage.setAlarmMessage("anotherAlarmMessage with [DefaultScopeDefine.Endpoint]");
        alarmMessages.add(anotherAlarmMessage);
        feishuHookCallback.doAlarm(alarmMessages);
        Assert.assertTrue(isSuccess.get());
    }

    @Test
    public void testFeishuWebhookWithSign() {
        checkSign.set(true);
        List<FeishuSettings.WebHookUrl> webHooks = new ArrayList<>();
        webHooks.add(new FeishuSettings.WebHookUrl(secret, "http://127.0.0.1:" + server.httpPort() + "/feishuhook/receiveAlarm?token=dummy_token"));
        Rules rules = new Rules();
        String template = "{\"msg_type\":\"text\",\"content\":{\"text\":\"Skywaling alarm: %s\"}}";
        rules.setFeishus(FeishuSettings.builder().webhooks(webHooks).textTemplate(template).build());

        AlarmRulesWatcher alarmRulesWatcher = new AlarmRulesWatcher(rules, null);
        FeishuHookCallback feishuHookCallback = new FeishuHookCallback(alarmRulesWatcher);
        List<AlarmMessage> alarmMessages = new ArrayList<>(2);
        AlarmMessage alarmMessage = new AlarmMessage();
        alarmMessage.setScopeId(DefaultScopeDefine.SERVICE);
        alarmMessage.setRuleName("service_resp_time_rule");
        alarmMessage.setAlarmMessage("alarmMessage with [DefaultScopeDefine.All]");
        alarmMessages.add(alarmMessage);
        AlarmMessage anotherAlarmMessage = new AlarmMessage();
        anotherAlarmMessage.setRuleName("service_resp_time_rule_2");
        anotherAlarmMessage.setScopeId(DefaultScopeDefine.ENDPOINT);
        anotherAlarmMessage.setAlarmMessage("anotherAlarmMessage with [DefaultScopeDefine.Endpoint]");
        alarmMessages.add(anotherAlarmMessage);
        feishuHookCallback.doAlarm(alarmMessages);
        Assert.assertTrue(isSuccess.get());
    }

    @Test
    public void testFeishuWebhookWithSignAndAt() {
        checkSign.set(true);
        List<FeishuSettings.WebHookUrl> webHooks = new ArrayList<>();
        webHooks.add(new FeishuSettings.WebHookUrl(secret, "http://127.0.0.1:" + server.httpPort() + "/feishuhook/receiveAlarm?token=dummy_token"));
        Rules rules = new Rules();
        String template = "{\"msg_type\":\"text\",\"content\":{\"text\":\"Skywaling alarm: %s\"},\"ats\":\"123\"}";
        rules.setFeishus(FeishuSettings.builder().webhooks(webHooks).textTemplate(template).build());

        AlarmRulesWatcher alarmRulesWatcher = new AlarmRulesWatcher(rules, null);
        FeishuHookCallback feishuHookCallback = new FeishuHookCallback(alarmRulesWatcher);
        List<AlarmMessage> alarmMessages = new ArrayList<>(2);
        AlarmMessage alarmMessage = new AlarmMessage();
        alarmMessage.setScopeId(DefaultScopeDefine.SERVICE);
        alarmMessage.setRuleName("service_resp_time_rule");
        alarmMessage.setAlarmMessage("alarmMessage with [DefaultScopeDefine.All]");
        alarmMessages.add(alarmMessage);
        AlarmMessage anotherAlarmMessage = new AlarmMessage();
        anotherAlarmMessage.setRuleName("service_resp_time_rule_2");
        anotherAlarmMessage.setScopeId(DefaultScopeDefine.ENDPOINT);
        anotherAlarmMessage.setAlarmMessage("anotherAlarmMessage with [DefaultScopeDefine.Endpoint]");
        alarmMessages.add(anotherAlarmMessage);
        feishuHookCallback.doAlarm(alarmMessages);
        Assert.assertTrue(isSuccess.get());
    }
}
