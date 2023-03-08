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
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import org.checkerframework.checker.nullness.qual.NonNull;
import software.amazon.jdbc.cleanup.CanReleaseResources;
import software.amazon.jdbc.util.HikariCPSQLException;
import software.amazon.jdbc.util.RdsUrlType;
import software.amazon.jdbc.util.RdsUtils;
import software.amazon.jdbc.util.StringUtils;

public abstract class HikariPooledConnectionProvider implements PooledConnectionProvider,
    CanReleaseResources {

  private static final RdsUtils rdsUtils = new RdsUtils();
  private static final Map<String, HikariDataSource> databasePools = new ConcurrentHashMap<>();
  private final HikariPoolConfigurator poolConfigurator;

  public HikariPooledConnectionProvider(HikariPoolConfigurator hikariPoolConfigurator) {
    poolConfigurator = hikariPoolConfigurator;
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
  public HostSpec getHostSpecByStrategy(@NonNull List<HostSpec> hosts, @NonNull HostRole role, @NonNull String strategy) {
    // This class does not accept any strategy, so the ConnectionProviderManager should prevent us
    // from getting here.
    return null;
  }

  @Override
  public Connection connect(
      @NonNull String protocol, @NonNull HostSpec hostSpec, @NonNull Properties props)
      throws SQLException {
    HikariDataSource ds = databasePools.computeIfAbsent(
        hostSpec.getUrl(), url -> {
          HikariConfig config = poolConfigurator.configurePool(hostSpec, props);
          setConnectionProperties(config, hostSpec, props);
          return new HikariDataSource(config);
        });
    return ds.getConnection();
  }

  abstract String getDataSourceClassName();

  private void setConnectionProperties(
      HikariConfig config, HostSpec hostSpec, Properties props) {
    config.setExceptionOverrideClassName(HikariCPSQLException.class.getName());
    config.setDataSourceClassName(getDataSourceClassName());

    String user = props.getProperty(PropertyDefinition.USER.name);
    String password = props.getProperty(PropertyDefinition.PASSWORD.name);
    if (user != null) {
      config.setUsername(user);
    }
    if (password != null) {
      config.setPassword(password);
    }

    if (HostRole.READER.equals(hostSpec.getRole())) {
      config.setReadOnly(true);
    }

    String serverPropertyName = props.getProperty("serverPropertyName");
    if (!StringUtils.isNullOrEmpty(serverPropertyName)) {
      config.addDataSourceProperty(serverPropertyName, hostSpec.getHost());
    }

    String portPropertyName = props.getProperty("portPropertyName");
    if (!StringUtils.isNullOrEmpty(portPropertyName) && hostSpec.isPortSpecified()) {
      config.addDataSourceProperty("portNumber", hostSpec.getPort());
    }

    String dbPropertyName = props.getProperty("databasePropertyName");
    String db = PropertyDefinition.DATABASE.getString(props);
    if (!StringUtils.isNullOrEmpty(dbPropertyName) && !StringUtils.isNullOrEmpty(db)) {
      config.addDataSourceProperty(dbPropertyName, db);
    }
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
}
