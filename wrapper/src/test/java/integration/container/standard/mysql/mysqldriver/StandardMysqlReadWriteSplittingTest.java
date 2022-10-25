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

package integration.container.standard.mysql.mysqldriver;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import com.mysql.cj.conf.PropertyKey;
import com.mysql.cj.exceptions.MysqlErrorNumbers;
import eu.rekawek.toxiproxy.Proxy;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.jdbc.PropertyDefinition;
import software.amazon.jdbc.plugin.efm.HostMonitoringConnectionPlugin;
import software.amazon.jdbc.plugin.readwritesplitting.ReadWriteSplittingPlugin;
import software.amazon.jdbc.util.SqlState;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class StandardMysqlReadWriteSplittingTest extends MysqlStandardMysqlBaseTest {

  private Stream<Arguments> testParameters() {
    return Stream.of(
        Arguments.of(getProps_allPlugins()),
        Arguments.of(getProps_readWritePlugin())
    );
  }

  @ParameterizedTest(name = "test_connectToWriter_setReadOnlyTrueTrueFalseFalseTrue")
  @MethodSource("testParameters")
  public void test_connectToWriter_setReadOnlyTrueTrueFalseFalseTrue(Properties props) throws SQLException {
    try (final Connection conn = connect(props)) {
      String writerConnectionId = queryInstanceId(conn);

      conn.setReadOnly(true);
      String readerConnectionId = queryInstanceId(conn);
      assertNotEquals(writerConnectionId, readerConnectionId);

      conn.setReadOnly(true);
      String currentConnectionId = queryInstanceId(conn);
      assertEquals(readerConnectionId, currentConnectionId);

      conn.setReadOnly(false);
      currentConnectionId = queryInstanceId(conn);
      assertEquals(writerConnectionId, currentConnectionId);

      conn.setReadOnly(false);
      currentConnectionId = queryInstanceId(conn);
      assertEquals(writerConnectionId, currentConnectionId);

      conn.setReadOnly(true);
      currentConnectionId = queryInstanceId(conn);
      assertEquals(readerConnectionId, currentConnectionId);
    }
  }

  @ParameterizedTest(name = "test_setReadOnlyFalseInReadOnlyTransaction")
  @MethodSource("testParameters")
  public void test_setReadOnlyFalseInReadOnlyTransaction(Properties props) throws SQLException{
    try (final Connection conn = connect(props)) {
      String writerConnectionId = queryInstanceId(conn);

      conn.setReadOnly(true);
      String readerConnectionId = queryInstanceId(conn);
      assertNotEquals(writerConnectionId, readerConnectionId);

      final Statement stmt = conn.createStatement();
      stmt.execute("START TRANSACTION READ ONLY");
      stmt.executeQuery("SELECT @@hostname");

      final SQLException exception = assertThrows(SQLException.class, () -> conn.setReadOnly(false));
      String currentConnectionId = queryInstanceId(conn);
      assertEquals(SqlState.ACTIVE_SQL_TRANSACTION.getState(), exception.getSQLState());
      assertEquals(readerConnectionId, currentConnectionId);

      stmt.execute("COMMIT");

      conn.setReadOnly(false);
      currentConnectionId = queryInstanceId(conn);
      assertEquals(writerConnectionId, currentConnectionId);
    }
  }

  @ParameterizedTest(name = "test_setReadOnlyFalseInTransaction_setAutocommitFalse")
  @MethodSource("testParameters")
  public void test_setReadOnlyFalseInTransaction_setAutocommitFalse(Properties props) throws SQLException{
    try (final Connection conn = connect(props)) {
      String writerConnectionId = queryInstanceId(conn);

      conn.setReadOnly(true);
      String readerConnectionId = queryInstanceId(conn);
      assertNotEquals(writerConnectionId, readerConnectionId);

      final Statement stmt = conn.createStatement();
      conn.setAutoCommit(false);
      stmt.executeQuery("SELECT COUNT(*) FROM information_schema.tables");

      final SQLException exception = assertThrows(SQLException.class, () -> conn.setReadOnly(false));
      String currentConnectionId = queryInstanceId(conn);
      assertEquals(SqlState.ACTIVE_SQL_TRANSACTION.getState(), exception.getSQLState());
      assertEquals(readerConnectionId, currentConnectionId);

      stmt.execute("COMMIT");

      conn.setReadOnly(false);
      currentConnectionId = queryInstanceId(conn);
      assertEquals(writerConnectionId, currentConnectionId);
    }
  }

  @ParameterizedTest(name = "test_setReadOnlyFalseInTransaction_setAutocommitZero")
  @MethodSource("testParameters")
  public void test_setReadOnlyFalseInTransaction_setAutocommitZero(Properties props) throws SQLException{
    try (final Connection conn = connect(props)) {
      String writerConnectionId = queryInstanceId(conn);

      conn.setReadOnly(true);
      String readerConnectionId = queryInstanceId(conn);
      assertNotEquals(writerConnectionId, readerConnectionId);

      final Statement stmt = conn.createStatement();
      stmt.execute("SET autocommit = 0");
      stmt.executeQuery("SELECT COUNT(*) FROM information_schema.tables");

      final SQLException exception = assertThrows(SQLException.class, () -> conn.setReadOnly(false));
      String currentConnectionId = queryInstanceId(conn);
      assertEquals(SqlState.ACTIVE_SQL_TRANSACTION.getState(), exception.getSQLState());
      assertEquals(readerConnectionId, currentConnectionId);

      stmt.execute("COMMIT");

      conn.setReadOnly(false);
      currentConnectionId = queryInstanceId(conn);
      assertEquals(writerConnectionId, currentConnectionId);
    }
  }

  @ParameterizedTest(name = "test_setReadOnlyTrueInTransaction")
  @MethodSource("testParameters")
  public void test_setReadOnlyTrueInTransaction(Properties props) throws SQLException{
    try (final Connection conn = connect(props)) {
      String writerConnectionId = queryInstanceId(conn);

      final Statement stmt1 = conn.createStatement();
      stmt1.executeUpdate("DROP TABLE IF EXISTS test_splitting_readonly_transaction");
      stmt1.executeUpdate("CREATE TABLE test_splitting_readonly_transaction (id int not null primary key, text_field varchar(255) not null)");
      stmt1.execute("SET autocommit = 0");

      final Statement stmt2 = conn.createStatement();
      stmt2.executeUpdate("INSERT INTO test_splitting_readonly_transaction VALUES (1, 'test_field value 1')");

      assertDoesNotThrow(() -> conn.setReadOnly(true));
      String currentConnectionId = queryInstanceId(conn);
      assertEquals(writerConnectionId, currentConnectionId);

      stmt2.execute("COMMIT");
      final ResultSet rs = stmt2.executeQuery("SELECT count(*) from test_splitting_readonly_transaction");
      rs.next();
      assertEquals(1, rs.getInt(1));

      conn.setReadOnly(false);
      stmt2.executeUpdate("DROP TABLE IF EXISTS test_splitting_readonly_transaction");
    }
  }

  @ParameterizedTest(name = "test_setReadOnlyTrue_allReadersDown")
  @MethodSource("testParameters")
  public void test_setReadOnlyTrue_allReadersDown(Properties props) throws SQLException, IOException {
    try (Connection conn = connectToProxy(props)) {
      String writerConnectionId = queryInstanceId(conn);

      // Kill all reader instances
      for (int i = 1; i < clusterSize; i++) {
        final String instanceId = instanceIDs[i];
        final Proxy proxyInstance = proxyMap.get(instanceId);
        if (proxyInstance != null) {
          containerHelper.disableConnectivity(proxyInstance);
        } else {
          fail(String.format("%s does not have a proxy setup.", instanceId));
        }
      }

      assertDoesNotThrow(() -> conn.setReadOnly(true));
      String currentConnectionId = assertDoesNotThrow(() -> queryInstanceId(conn));
      assertEquals(writerConnectionId, currentConnectionId);

      assertDoesNotThrow(() -> conn.setReadOnly(false));
      currentConnectionId = assertDoesNotThrow(() -> queryInstanceId(conn));
      assertEquals(writerConnectionId, currentConnectionId);
    }
  }

  @ParameterizedTest(name = "test_setReadOnlyTrue_allInstancesDown")
  @MethodSource("testParameters")
  public void test_setReadOnlyTrue_allInstancesDown(Properties props) throws SQLException, IOException {
    try (Connection conn = connectToProxy(props)) {
      // Kill all instances
      for (int i = 0; i < clusterSize; i++) {
        final String instanceId = instanceIDs[i];
        final Proxy proxyInstance = proxyMap.get(instanceId);
        if (proxyInstance != null) {
          containerHelper.disableConnectivity(proxyInstance);
        } else {
          fail(String.format("%s does not have a proxy setup.", instanceId));
        }
      }

      final SQLException exception = assertThrows(SQLException.class, () -> conn.setReadOnly(true));
      if (pluginChainIncludesFailoverPlugin(props)) {
        assertEquals(SqlState.CONNECTION_UNABLE_TO_CONNECT.getState(), exception.getSQLState());
      } else {
        assertEquals(SqlState.COMMUNICATION_ERROR.getState(), exception.getSQLState());
      }
    }
  }

  @ParameterizedTest(name = "test_setReadOnlyTrue_allInstancesDown_writerClosed")
  @MethodSource("testParameters")
  public void test_setReadOnlyTrue_allInstancesDown_writerClosed(Properties props) throws SQLException, IOException {
    try (Connection conn = connectToProxy(props)) {
      conn.close();

      // Kill all instances
      for (int i = 0; i < clusterSize; i++) {
        final String instanceId = instanceIDs[i];
        final Proxy proxyInstance = proxyMap.get(instanceId);
        if (proxyInstance != null) {
          containerHelper.disableConnectivity(proxyInstance);
        } else {
          fail(String.format("%s does not have a proxy setup.", instanceId));
        }
      }

      final SQLException exception = assertThrows(SQLException.class, () -> conn.setReadOnly(true));
      assertEquals(SqlState.CONNECTION_UNABLE_TO_CONNECT.getState(), exception.getSQLState());
    }
  }

  @ParameterizedTest(name = "test_setReadOnlyFalse_allInstancesDown")
  @MethodSource("testParameters")
  public void test_setReadOnlyFalse_allInstancesDown(Properties props) throws SQLException, IOException {
    try (Connection conn = connectToProxy(props)) {
      String writerConnectionId = queryInstanceId(conn);

      conn.setReadOnly(true);
      String readerConnectionId = queryInstanceId(conn);
      assertNotEquals(writerConnectionId, readerConnectionId);

      // Kill all instances
      for (int i = 0; i < clusterSize; i++) {
        final String instanceId = instanceIDs[i];
        final Proxy proxyInstance = proxyMap.get(instanceId);
        if (proxyInstance != null) {
          containerHelper.disableConnectivity(proxyInstance);
        } else {
          fail(String.format("%s does not have a proxy setup.", instanceId));
        }
      }

      final SQLException exception = assertThrows(SQLException.class, () -> conn.setReadOnly(false));
      assertEquals(SqlState.CONNECTION_UNABLE_TO_CONNECT.getState(), exception.getSQLState());
    }
  }

  @ParameterizedTest(name = "test_readerLoadBalancing_autocommitTrue")
  @MethodSource("testParameters")
  public void test_readerLoadBalancing_autocommitTrue(Properties props) throws SQLException {
    ReadWriteSplittingPlugin.LOAD_BALANCE_READ_ONLY_TRAFFIC.set(props, "true");
    try (final Connection conn = connect(props)) {
      String writerConnectionId = queryInstanceId(conn);

      conn.setReadOnly(true);
      String readerConnectionId = queryInstanceId(conn);
      assertNotEquals(writerConnectionId, readerConnectionId);

      for (int i = 0; i < 10; i++) {
        Statement stmt = conn.createStatement();
        stmt.executeQuery("SELECT " + i);
        readerConnectionId = queryInstanceId(conn);
        assertNotEquals(writerConnectionId, readerConnectionId);

        ResultSet rs = stmt.getResultSet();
        rs.next();
        assertEquals(i, rs.getInt(1));
      }
    }
  }

  @ParameterizedTest(name = "test_readerLoadBalancing_autocommitFalse")
  @MethodSource("testParameters")
  public void test_readerLoadBalancing_autocommitFalse(Properties props) throws SQLException {
    ReadWriteSplittingPlugin.LOAD_BALANCE_READ_ONLY_TRAFFIC.set(props, "true");
    try (final Connection conn = connect(props)) {
      String writerConnectionId = queryInstanceId(conn);

      conn.setReadOnly(true);
      String readerConnectionId = queryInstanceId(conn);
      assertNotEquals(writerConnectionId, readerConnectionId);

      conn.setAutoCommit(false);
      Statement stmt = conn.createStatement();

      for (int i = 0; i < 5; i++) {
        stmt.executeQuery("SELECT " + i);
        conn.commit();
        readerConnectionId = queryInstanceId(conn);
        assertNotEquals(writerConnectionId, readerConnectionId);

        ResultSet rs = stmt.getResultSet();
        rs.next();
        assertEquals(i, rs.getInt(1));

        stmt.executeQuery("SELECT " + i);
        conn.rollback();
        readerConnectionId = queryInstanceId(conn);
        assertNotEquals(writerConnectionId, readerConnectionId);
      }
    }
  }

  @Test
  public void test_transactionResolutionUnknown_readWriteSplittingPluginOnly() throws SQLException, IOException {
    Properties props = getProps_readWritePlugin();
    ReadWriteSplittingPlugin.LOAD_BALANCE_READ_ONLY_TRAFFIC.set(props, "true");
    try (final Connection conn = connectToProxy(props)) {
      String writerConnectionId = queryInstanceId(conn);

      conn.setReadOnly(true);
      conn.setAutoCommit(false);
      String readerId = queryInstanceId(conn);
      assertNotEquals(writerConnectionId, readerId);

      final Statement stmt = conn.createStatement();
      stmt.executeQuery("SELECT 1");
      final Proxy proxyInstance = proxyMap.get(instanceIDs[1]);
      if (proxyInstance != null) {
        containerHelper.disableConnectivity(proxyInstance);
      } else {
        fail(String.format("%s does not have a proxy setup.", readerId));
      }

      SQLException e = assertThrows(SQLException.class, conn::rollback);
      assertEquals(SqlState.CONNECTION_FAILURE_DURING_TRANSACTION.getState(), e.getSQLState());

      try (final Connection newConn = connectToProxy(props)) {
        newConn.setReadOnly(true);
        Statement newStmt = newConn.createStatement();
        ResultSet rs = newStmt.executeQuery("SELECT 1");
        rs.next();
        assertEquals(1, rs.getInt(1));
      }
    }
  }

  private Properties getProps_allPlugins() {
    final Properties props = initDefaultProps();
    setFailureDetectionProps(props);
    addAllTestPlugins(props);
    return props;
  }

  private Properties getProps_readWritePlugin() {
    final Properties props = initDefaultProps();
    setFailureDetectionProps(props);
    addReadWritePlugins(props);
    return props;
  }

  private static void addAllTestPlugins(final Properties props) {
    PropertyDefinition.PLUGINS.set(props, "readWriteSplitting,failover,efm");
  }

  private static void addReadWritePlugins(final Properties props) {
    PropertyDefinition.PLUGINS.set(props, "readWriteSplitting");
  }

  private boolean pluginChainIncludesFailoverPlugin(final Properties props) {
    final String plugins = PropertyDefinition.PLUGINS.getString(props);
    if (plugins == null) {
      return false;
    }

    return plugins.contains("failover");
  }

  private static void setFailureDetectionProps(Properties props) {
    props.setProperty(PropertyKey.socketTimeout.getKeyName(), "500");
    HostMonitoringConnectionPlugin.FAILURE_DETECTION_TIME.set(props, "500");
    HostMonitoringConnectionPlugin.FAILURE_DETECTION_INTERVAL.set(props, "50");
    HostMonitoringConnectionPlugin.FAILURE_DETECTION_COUNT.set(props, "1");
  }
}
