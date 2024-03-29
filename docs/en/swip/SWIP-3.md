# Support RocketMQ Monitoring
## Motivation
RocketMQ is a cloud native messaging and streaming platform, making it simple to build event-driven applications. Now that Skywalking can monitor OpenTelemetry metrics, I want to add RocketMQ monitoring via the OpenTelemetry Collector, which fetches metrics from the RocketMQ Exporter

## Architecture Graph
There is no significant architecture-level change.

## Proposed Changes
```rocketmq-exporter``` collects metrics from RocketMQ and transport the data to OpenTelemetry collector, using SkyWalking openTelemetry receiver to receive these metrics。
Provide cluster, broker, and topic dimensions monitoring.

### RocketMQ Cluster Supported Metrics

| Monitoring Panel                     |Unit        | Metric Name                                                 | Description                                                               | Data Source      |
|--------------------------------------|------------|-------------------------------------------------------------|---------------------------------------------------------------------------|------------------|
| Messages Produced Today              | Count      | meter_rocketmq_cluster_messages_produced_today              | The number of cluster messages produced today.                            | RocketMQ Exporter |
| Messages Consumed Today              | Count      | meter_rocketmq_cluster_messages_consumed_today              | The number of cluster messages consumed today.                            | RocketMQ Exporter |
| Total Producer Tps                   | Msg/sec    | meter_rocketmq_cluster_total_producer_tps                   | The number of messages produced per second.                               | RocketMQ Exporter |
| Total Consume Tps                    | Msg/sec    | meter_rocketmq_cluster_total_consumer_tps                   | The number of messages consumed per second.                               | RocketMQ Exporter |
| Producer Message Size                | Bytes/sec  | meter_rocketmq_cluster_producer_message_size                | The max size of a message produced per second.                            | RocketMQ Exporter |
| Consumer Message Size                | Bytes/sec  | meter_rocketmq_cluster_consumer_message_size                | The max size of the consumed message per second.                          | RocketMQ Exporter |
| Messages Produced Until Yesterday    | Count      | meter_rocketmq_cluster_messages_produced_until_yesterday    | The total number of messages put until 12 o'clock last night.             | RocketMQ Exporter |
| Messages Consumed Until Yesterday    | Count      | meter_rocketmq_cluster_messages_consumed_until_yesterday    | The total number of messages read until 12 o'clock last night.            | RocketMQ Exporter |
| Max Consumer Latency                 | ms         | meter_rocketmq_cluster_max_consumer_latency                 | The max number of consumer latency.                                       | RocketMQ Exporter |
| Max CommitLog Disk Ratio             | %          | meter_rocketmq_cluster_max_commitLog_disk_ratio             | The max utilization ratio of the commit log disk.                         | RocketMQ Exporter |
| CommitLog Disk Ratio                 | %          | meter_rocketmq_cluster_commitLog_disk_ratio                 | The utilization ratio of the commit log disk per broker IP.               | RocketMQ Exporter |
| Pull ThreadPool Queue Head Wait Time | ms         | meter_rocketmq_cluster_pull_threadPool_queue_head_wait_time | The wait time in milliseconds for pulling threadPool queue per broker IP. | RocketMQ Exporter |
| Send ThreadPool Queue Head Wait Time | ms         | meter_rocketmq_cluster_send_threadPool_queue_head_wait_time | The wait time in milliseconds for sending threadPool queue per broker IP. | RocketMQ Exporter |
| Topic Count                          | Count      | meter_rocketmq_cluster_topic_count                          | The number of topics that received messages from the producer.            | RocketMQ Exporter |
| Broker Count                         | Count      | meter_rocketmq_cluster_broker_count                         | The number of brokers that received messages from the producer.           | RocketMQ Exporter |

### RocketMQ Broker Supported Metrics

| Monitoring Panel                           |Unit        | Metric Name                                                             | Description                                        | Data Source       |
|--------------------------------------------|------------|-------------------------------------------------------------------------|----------------------------------------------------|-------------------|
| Produce TPS                                | Msg/sec    | meter_rocketmq_broker_produce_tps                                       | The number of broker produces messages per second. | RocketMQ Exporter |
| Consume QPS                                | Msg/sec    | meter_rocketmq_broker_consume_qps                                       | The number of broker consumes messages per second. | RocketMQ Exporter |
| Producer Message Size                      | Bytes/sec  | meter_rocketmq_broker_producer_message_size                             | The max size of the messages produced per second.  | RocketMQ Exporter |
| Consumer Message Size                      | Bytes/sec  | meter_rocketmq_broker_consumer_message_size                             | The max size of the messages consumed per second.  | RocketMQ Exporter |

### RocketMQ Topic Supported Metrics

| Monitoring Panel          | Unit      | Metric Name                                                      | Description                                                           | Data Source       |
|---------------------------|-----------|------------------------------------------------------------------|-----------------------------------------------------------------------|-------------------|
| Max Producer Message Size | Byte      | meter_rocketmq_topic_max_producer_message_size                   | The maximum number of messages produced.                              | RocketMQ Exporter |
| Max Consumer Message Size | Byte      | meter_rocketmq_topic_max_consumer_message_size                   | The maximum number of messages consumed.                              | RocketMQ Exporter |
| Consumer Latency          | ms        | meter_rocketmq_topic_consumer_latency                            | Consumption delay time of a consumer group.                           | RocketMQ Exporter |
| Producer Tps              | Msg/sec   | meter_rocketmq_topic_producer_tps                                | The number of messages produced per second.                           | RocketMQ Exporter |
| Consumer Group Tps        | Msg/sec   | meter_rocketmq_topic_consumer_group_tps                          | The number of messages consumed per second per consumer group.        | RocketMQ Exporter |
| Producer Offset           | Count     | meter_rocketmq_topic_producer_offset                             | The max progress of a topic's production message.                     | RocketMQ Exporter |
| Consumer Group Offset     | Count     | meter_rocketmq_topic_consumer_group_offset                       | The max progress of a topic's consumption message per consumer group. | RocketMQ Exporter |
| Producer Message Size     | Byte/sec  | meter_rocketmq_topic_producer_message_size                       | The max size of messages produced per second.                         | RocketMQ Exporter |
| Consumer Message Size     | Byte/sec  | meter_rocketmq_topic_consumer_message_size                       | The max size of messages consumed per second.                         | RocketMQ Exporter |
| Consumer Group_Count      | Count     | meter_rocketmq_topic_consumer_group_count                        | The number of consumer groups.                                        | RocketMQ Exporter |
| Broker Count              | Count     | meter_rocketmq_topic_broker_count                                | The number of topics that received messages from the producer.        | RocketMQ Exporter |

## Imported Dependencies libs and their licenses.
No new dependency.

## Compatibility
no breaking changes.

## General usage docs

This feature is out of the box.
