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

package org.apache.skywalking.library.elasticsearch.bulk;

import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.util.Exceptions;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.library.elasticsearch.ElasticSearch;
import org.apache.skywalking.library.elasticsearch.requests.IndexRequest;
import org.apache.skywalking.library.elasticsearch.requests.UpdateRequest;
import org.apache.skywalking.library.elasticsearch.requests.factory.RequestFactory;

import static java.util.Objects.requireNonNull;

@Slf4j
public final class BulkProcessor {
    private final ArrayBlockingQueue<Object> requests;

    private final AtomicReference<ElasticSearch> es;
    private final int bulkActions;
    private final Semaphore semaphore;

    public static BulkProcessorBuilder builder() {
        return new BulkProcessorBuilder();
    }

    BulkProcessor(
        final AtomicReference<ElasticSearch> es, final int bulkActions,
        final Duration flushInterval, final int concurrentRequests) {
        requireNonNull(flushInterval, "flushInterval");

        this.es = requireNonNull(es, "es");
        this.bulkActions = bulkActions;
        this.semaphore = new Semaphore(concurrentRequests > 0 ? concurrentRequests : 1);
        this.requests = new ArrayBlockingQueue<>(bulkActions + 1);

        final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(
            1, r -> {
            final Thread thread = new Thread(r);
            thread.setName("ElasticSearch BulkProcessor");
            return thread;
        });
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.scheduleWithFixedDelay(
            this::flush, 0, flushInterval.getSeconds(), TimeUnit.SECONDS);
    }

    public BulkProcessor add(IndexRequest request) {
        internalAdd(request);
        return this;
    }

    public BulkProcessor add(UpdateRequest request) {
        internalAdd(request);
        return this;
    }

    @SneakyThrows
    private void internalAdd(Object request) {
        requireNonNull(request, "request");
        requests.put(request);
        flushIfNeeded();
    }

    @SneakyThrows
    private void flushIfNeeded() {
        if (requests.size() >= bulkActions) {
            flush();
        }
    }

    void flush() {
        if (requests.isEmpty()) {
            return;
        }

        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            log.error("Interrupted when trying to get semaphore to execute bulk requests", e);
            return;
        }

        final List<Object> batch = new ArrayList<>(requests.size());
        requests.drainTo(batch);

        final CompletableFuture<Void> flush = doFlush(batch);
        flush.whenComplete((ignored1, ignored2) -> semaphore.release());
        flush.join();
    }

    private CompletableFuture<Void> doFlush(final List<Object> batch) {
        log.debug("Executing bulk with {} requests", batch.size());

        if (batch.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        final CompletableFuture<Void> future = es.get().version().thenCompose(v -> {
            try {
                final RequestFactory rf = v.requestFactory();
                final List<byte[]> bs = new ArrayList<>();
                for (final Object request : batch) {
                    bs.add(v.codec().encode(request));
                    bs.add("\n".getBytes());
                }
                final ByteBuf content = Unpooled.wrappedBuffer(bs.toArray(new byte[0][]));
                return es.get().client().execute(rf.bulk().bulk(content))
                         .aggregate().thenAccept(response -> {
                        final HttpStatus status = response.status();
                        if (status != HttpStatus.OK) {
                            throw new RuntimeException(response.contentUtf8());
                        }
                    });
            } catch (Exception e) {
                return Exceptions.throwUnsafely(e);
            }
        });
        future.whenComplete((ignored, exception) -> {
            if (exception != null) {
                log.error("Failed to execute requests in bulk", exception);
            } else {
                log.debug("Succeeded to execute {} requests in bulk", batch.size());
            }
        });
        return future;
    }
}
