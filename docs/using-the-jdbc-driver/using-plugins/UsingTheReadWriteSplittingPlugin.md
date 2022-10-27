## Read-Write Splitting Plugin

The read-write splitting plugin adds functionality to switch between writer/reader instances via calls to the `Connection#setReadOnly` method. Upon calling `setReadOnly(true)`, the plugin will establish a connection to a random reader instance and direct subsequent queries to this instance. Future calls to `setReadOnly` will switch between the established writer and reader connections according to the boolean argument you supply to the `setReadOnly` method.

### Session State Limitations with the Read-Write Splitting Plugin

There are many session state attributes that can change during a session, and many ways to change them. Consequently, the read-write splitting plugin has limited support for transferring session state between connections. The following attributes will be automatically transferred when switching connections:

- autocommit value
- transaction isolation level

All other session state attributes will be lost when switching connections. There are two scenarios when the plugin may switch a connection:
1. You have loaded the plugin but have kept reader load balancing disabled. In this case, the connection will switch between the writer/reader when calling `setReadOnly`.
2. You have loaded the plugin and have enabled reader load balancing. In this case, the connection will still switch between the writer/reader when calling `setReadOnly`. It will also switch at transaction boundaries. See the section on [reader load balancing](#reader-load-balancing) for more information on what is consided a transaction boundary.

If your SQL workflow depends on session state attributes that are not mentioned above, you will need to re-configure those attributes whenever the connection is switched. If you have loaded the plugin but have kept reader load balancing disabled, you will need to re-configure these attributes after each call to `setReadOnly`. If reader load balancing is enabled, you will also need to re-configure these attributes after each transaction boundary. Since reader load balancing frequently switches the connection, we recommend that you keep it disabled if your workflow depends on session state attributes that are not automatically transferred.

### Loading the Read-Write Splitting Plugin

The read-write splitting plugin is not loaded by default. To load the plugin, set the `connectionPluginFactories` connection parameter:

```
final Properties properties = new Properties();
properties.setProperty("wrapperPlugins", "readWriteSplitting");
```
// code is different//

If you would like to load the read-write splitting plugin alongside the failover and enhanced failure monitoring plugins, the read-write splitting plugin must be the first plugin in the connection chain, otherwise failover exceptions will not be properly processed by the plugin:
// this is how you configure it if using failover plugin. if configuring without fialover plugin you need to include the AuroraHostListConnectionPlugin. need to be before ReadWriteSpltting plugin
Only if using Aurora

```
final Properties properties = new Properties();
properties.setProperty("wrapperPlugins", "readWriteSplitting,failover");
```

If you would like to configure without the failover plugin while using Aurora, you need to include the AuroraHostList plugin before the ReadWriteSplitting plugin:

```
final Properties properties = new Properties();
properties.setProperty("wrapperPlugins", "auroraHostList,readWriteSplitting");
```
### Reader Load Balancing

The plugin can also load balance queries among available reader instances by enabling the `loadBalanceReadOnlyTraffic` connection parameter. This parameter is disabled by default. To enable it, set the following connection parameter:
```
final Properties properties = new Properties();
ReadWriteSplittingPlugin.LOAD_BALANCE_READ_ONLY_TRAFFIC.set(properties, "true");
```
This can also be set by calling setProperties() directly:
```
final Properties properties = new Properties();
properties.setProperty("loadBalanceReadOnlyTraffic", "true");
```

Once this parameter is enabled and `setReadOnly(true)` has been called on the Connection object, the plugin will switch to a new randomly selected reader instance at each transaction boundary. The following scenarios are considered transaction boundaries:
- After calling `commit()` or `rollback()`
- After executing `COMMIT` or `ROLLBACK` as a SQL statement
- After executing any SQL statement while autocommit is on, with the following exceptions:
    - The statement started a transaction via `BEGIN` or `START TRANSACTION`
    - The statement began with `SET` (eg `SET time_zone = "+00:00"`)

### Limitations with Reader Load Balancing

When reader load balancing is enabled, the read-write splitting plugin will analyze methods and statements executed against the Connection object to determine when the connection is at a transaction boundary. This analysis does not support SQL strings containing multiple statements. If your SQL strings contain multiple statements, we recommend that you do not enable reader load balancing as the resulting behavior is not defined. If a SQL string with multiple statements is provided, the plugin will only analyze the first statement.

### Using the Read-Write Splitting Plugin against RDS/Aurora Clusters

When using the read-write splitting plugin against RDS or Aurora clusters, the plugin automatically acquires the cluster topology by querying the cluster. Because of this functionality, you do not have to supply multiple instance URLs in the connection string. Instead, supply just the URL for the initial instance to which you're connecting.

### Using the Read-Write Splitting Plugin against Non-RDS Clusters

If you are using the read-write splitting plugin against a cluster that is not hosted on RDS or Aurora, the plugin will not be able to automatically acquire the cluster topology. Instead, you must supply the topology information in the connection string as a comma-delimited list of multiple instance URLs. The first instance in the list must be the writer instance:

```
String connectionUrl = "jdbc:aws-wrapper:mysql://writer-instance-1.com,reader-instance-1.com,reader-instance-2.com/database-name"
```

### Read Write Splitting Plugin Parameters

| Parameter | Value | Required | Description | Default Value |
| --- | --- | --- | --- | --- |
| `loadBalanceReadOnlyTraffic` | Boolean | No  | Set to `true` to load balance queries among available reader instances. Once enabled, load balancing will automatically be performed for reader instances when the connection has been set to read-only mode via `Connection#setReadOnly` | `false` |

