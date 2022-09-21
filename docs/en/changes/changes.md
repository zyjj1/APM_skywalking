## 9.3.0

#### Project

#### OAP Server

* Add component ID(133) for impala JDBC Java agent plugin and component ID(134) for impala server.
* Use prepareStatement in H2SQLExecutor#getByIDs.(No function change).
* Bump up snakeyaml to 1.32 for fixing CVE.
* Fix `DurationUtils.convertToTimeBucket` missed verify date format.
* Enhance LAL to support converting LogData to DatabaseSlowStatement.
* [**Breaking Change**] Change the LAL script format(Add layer property).
* Adapt ElasticSearch 8.1+, migrate from removed APIs to recommended APIs.
* Support monitoring MySQL slow SQLs.
* Support analyzing cache related spans to provide metrics and slow commands for cache services from client side
* Optimize virtual database, fix dynamic config watcher NPE when default value is null 
* Remove physical index existing check and keep template existing check only to avoid meaningless `retry wait`
  in `no-init` mode.
* Make sure instance list ordered in TTL processor to avoid TTL timer never runs.

#### UI

* Fix: tab active incorrectly, when click tab space
* Add impala icon for impala JDBC Java agent plugin.
* (Webapp)Bump up snakeyaml to 1.31 for fixing CVE-2022-25857
* [Breaking Change]: migrate from Spring Web to Armeria, now you should use the environment variable
  name `SW_OAP_ADDRESS`
  to change the OAP backend service addresses, like `SW_OAP_ADDRESS=localhost:12800,localhost:12801`, and use
  environment
  variable `SW_SERVER_PORT` to change the port. Other Spring-related configurations don't take effect anymore.
* Polish the endpoint list graph.
* Fix styles for an adaptive height.
* Fix setting up a new time range after clicking the refresh button.
* Enhance the process topology graph to support dragging nodes.
* UI-template: Fix metrics calculation in `general-service/mesh-service/faas-function` top-list dashboard.
* Update MySQL dashboard to visualize collected slow SQLs.
* Add virtual cache dashboard
* Remove `responseCode` fields of all OAL sources, as well as examples to avoid user's confusion.
* Remove All from the endpoints selector.
* Enhance menu configurations to make it easier to change.

#### Documentation

* Add `metadata-uid` setup doc about Kubernetes coordinator in the cluster management.
* Add a doc for adding menus to booster UI.

All issues and pull requests are [here](https://github.com/apache/skywalking/milestone/149?closed=1)