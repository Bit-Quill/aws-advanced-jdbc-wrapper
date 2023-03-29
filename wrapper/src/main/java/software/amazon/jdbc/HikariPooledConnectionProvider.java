/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package software.amazon.jdbc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.checkerframework.checker.nullness.qual.NonNull;
import software.amazon.jdbc.cleanup.CanReleaseResources;
import software.amazon.jdbc.util.HikariCPSQLException;
import software.amazon.jdbc.util.RdsUrlType;
import software.amazon.jdbc.util.RdsUtils;
import software.amazon.jdbc.util.StringUtils;

public class HikariPooledConnectionProvider implements PooledConnectionProvider,
    CanReleaseResources {

  private static final Logger LOGGER = Logger.getLogger(HikariPooledConnectionProvider.class.getName());

  private static final RdsUtils rdsUtils = new RdsUtils();
  private static final Map<String, HikariDataSource> databasePools = new ConcurrentHashMap<>();
  private final HikariPoolConfigurator poolConfigurator;
  private final HikariPoolMapping poolMapping;
  protected int retries = 10;

  public HikariPooledConnectionProvider(HikariPoolConfigurator hikariPoolConfigurator) {
    this(hikariPoolConfigurator, (hostSpec, properties) -> hostSpec.getUrl());
  }

  /**
   * {@link HikariPooledConnectionProvider} constructor.
   *
   * @param hikariPoolConfigurator A lambda that returns a {@link HikariConfig}
   *                               object with specific Hikari configurations.
   * @param mapping A lambda that returns a String that maps to a specific {@link HikariDataSource}
   *                for the internal connection pool.
   */
  public HikariPooledConnectionProvider(
      HikariPoolConfigurator hikariPoolConfigurator,
      HikariPoolMapping mapping) {
    this.poolConfigurator = hikariPoolConfigurator;
    this.poolMapping = mapping;
  }

  @Override
  public boolean acceptsUrl(
      @NonNull String protocol, @NonNull HostSpec hostSpec, @NonNull Properties props) {
    final RdsUrlType urlType = rdsUtils.identifyRdsType(hostSpec.getHost());
    return RdsUrlType.RDS_INSTANCE.equals(urlType);
  }

  @Override
  public boolean acceptsStrategy(@NonNull HostRole role, @NonNull String strategy) {
    return false;
  }

  @Override
  public HostSpec getHostSpecByStrategy(
      @NonNull List<HostSpec> hosts, @NonNull HostRole role, @NonNull String strategy) {
    // This class does not accept any strategy, so the ConnectionProviderManager should prevent us
    // from getting here.
    return null;
  }

  @Override
  public Connection connect(
      @NonNull String protocol, @NonNull HostSpec hostSpec, @NonNull Properties props)
      throws SQLException {
    final HikariDataSource ds = databasePools.computeIfAbsent(
        poolMapping.getKey(hostSpec, props),
        url -> createHikariDataSource(protocol, hostSpec, props)
    );

    Connection conn = ds.getConnection();
    int count = 0;
    while (conn != null && count++ < retries && !conn.isValid(3)) {
      ds.evictConnection(conn);
      conn = ds.getConnection();
    }
    return conn;
  }

  @Override
  public Connection connect(
      @NonNull String url, @NonNull Properties props) throws SQLException {
    // This method is only called by tests/benchmarks
    return null;
  }

  @Override
  public void releaseResources() {
    databasePools.forEach((String url, HikariDataSource ds) -> ds.close());
    databasePools.clear();
  }

  protected HikariConfig getHikariConfig(String protocol, HostSpec hostSpec, Properties connectionProps) {
    Properties hikariProps = new Properties();
    String jdbcUrl = protocol + hostSpec.getUrl();

    String db = PropertyDefinition.DATABASE.getString(connectionProps);
    if (!StringUtils.isNullOrEmpty(db)) {
      jdbcUrl += db;
    }

    HikariConfig config = new HikariConfig(hikariProps);
    config.setJdbcUrl(jdbcUrl);
    config.setExceptionOverrideClassName(HikariCPSQLException.class.getName());

    String user = connectionProps.getProperty(PropertyDefinition.USER.name);
    String password = connectionProps.getProperty(PropertyDefinition.PASSWORD.name);
    if (user != null) {
      config.setUsername(user);
    }
    if (password != null) {
      config.setPassword(password);
    }

    if (HostRole.READER.equals(hostSpec.getRole())) {
      config.setReadOnly(true);
    }

    return config;
  }

  public int getHostCount() {
    return databasePools.size();
  }

  public Set<String> getHosts() {
    return Collections.unmodifiableSet(databasePools.keySet());
  }

  public void logConnections() {
    LOGGER.finest(() -> {
      final StringBuilder builder = new StringBuilder();
      databasePools.forEach((key, dataSource) -> {
        builder.append("\t[ ");
        builder.append(key).append(":");
        builder.append("\n\t {");
        builder.append("\n\t\t").append(dataSource);
        builder.append("\n\t }\n");
        builder.append("\t");
      });
      return String.format("Hikari Pooled Connection: \n[\n%s\n]", builder);
    });
  }

  HikariDataSource createHikariDataSource(String protocol, HostSpec hostSpec, Properties props) {
    HikariConfig config = getHikariConfig(protocol, hostSpec, props);
    poolConfigurator.configurePool(config, hostSpec, props);
    return new HikariDataSource(config);
  }
}
