/*
 * AWS JDBC Proxy Driver
 * Copyright Amazon.com Inc. or affiliates.
 * See the LICENSE file in the project root for more information.
 */

package integration.container.aurora.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.mysql.cj.conf.PropertyKey;
import eu.rekawek.toxiproxy.Proxy;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.aws.rds.jdbc.proxydriver.wrapper.ConnectionWrapper;

public class AuroraPostgresIntegrationTest extends AuroraPostgresBaseTest {
  protected String currWriter;
  protected String currReader;

  protected static String buildConnectionString(
      String connStringPrefix,
      String host,
      String port,
      String databaseName) {
    return connStringPrefix + host + ":" + port + "/" + databaseName;
  }

  private static Stream<Arguments> testParameters() {
    return Stream.of(
        // missing username
        Arguments.of(buildConnectionString(
            DB_CONN_STR_PREFIX,
            POSTGRES_INSTANCE_1_URL,
            String.valueOf(AURORA_POSTGRES_PORT),
            AURORA_POSTGRES_DB),
            "",
            AURORA_POSTGRES_PASSWORD),
        // missing password
        Arguments.of(buildConnectionString(
            DB_CONN_STR_PREFIX,
            POSTGRES_INSTANCE_1_URL,
            String.valueOf(AURORA_POSTGRES_PORT),
            AURORA_POSTGRES_DB),
            AURORA_POSTGRES_USERNAME,
            ""),
        // missing connection prefix
        Arguments.of(buildConnectionString(
            "",
            POSTGRES_INSTANCE_1_URL,
            String.valueOf(AURORA_POSTGRES_PORT),
            AURORA_POSTGRES_DB),
            AURORA_POSTGRES_USERNAME,
            AURORA_POSTGRES_PASSWORD),
        // missing port
        Arguments.of(buildConnectionString(
            DB_CONN_STR_PREFIX,
            POSTGRES_INSTANCE_1_URL,
          "",
            AURORA_POSTGRES_DB),
            AURORA_POSTGRES_USERNAME,
            AURORA_POSTGRES_PASSWORD),
        // incorrect database name
        Arguments.of(buildConnectionString(DB_CONN_STR_PREFIX,
            POSTGRES_INSTANCE_1_URL,
            String.valueOf(AURORA_POSTGRES_PORT),
            "failedDatabaseNameTest"),
            AURORA_POSTGRES_USERNAME,
            AURORA_POSTGRES_PASSWORD)
    );
  }

  private static Stream<Arguments> generateConnectionString() {
    return Stream.of(
            Arguments.of(POSTGRES_INSTANCE_1_URL, AURORA_POSTGRES_PORT),
            Arguments.of(POSTGRES_INSTANCE_1_URL + PROXIED_DOMAIN_NAME_SUFFIX, POSTGRES_PROXY_PORT),
            Arguments.of(POSTGRES_CLUSTER_URL, AURORA_POSTGRES_PORT),
            Arguments.of(POSTGRES_CLUSTER_URL + PROXIED_DOMAIN_NAME_SUFFIX, POSTGRES_PROXY_PORT),
            Arguments.of(POSTGRES_RO_CLUSTER_URL, AURORA_POSTGRES_PORT),
            Arguments.of(POSTGRES_RO_CLUSTER_URL + PROXIED_DOMAIN_NAME_SUFFIX, POSTGRES_PROXY_PORT)
    );
  }

  @ParameterizedTest(name = "test_ConnectionString")
  @MethodSource("generateConnectionString")
  public void test_ConnectionString(String connStr, int port) throws SQLException {
    try (final Connection conn = connectToInstance(connStr, port)) {
      Statement stmt = conn.createStatement();
      ResultSet rs = stmt.executeQuery("SELECT 1");
      rs.next();
      assertEquals(1, rs.getInt(1));
      assertTrue(conn.isValid(3));
    }
  }


  @Test
  public void test_ValidateConnectionWhenNetworkDown() throws SQLException, IOException {
    final Connection conn =
            connectToInstance(POSTGRES_INSTANCE_1_URL + PROXIED_DOMAIN_NAME_SUFFIX, POSTGRES_PROXY_PORT);
    assertTrue(conn.isValid(3));

    containerHelper.disableConnectivity(proxyInstance_1);

    assertFalse(conn.isValid(3));

    containerHelper.enableConnectivity(proxyInstance_1);

    conn.close();
  }

  @Test
  public void test_ConnectWhenNetworkDown() throws SQLException, IOException {
    containerHelper.disableConnectivity(proxyInstance_1);

    assertThrows(Exception.class, () -> {
      // expected to fail since communication is cut
      connectToInstance(POSTGRES_INSTANCE_1_URL + PROXIED_DOMAIN_NAME_SUFFIX, POSTGRES_PROXY_PORT);
    });

    containerHelper.enableConnectivity(proxyInstance_1);

    final Connection conn = connectToInstance(POSTGRES_INSTANCE_1_URL + PROXIED_DOMAIN_NAME_SUFFIX,
            POSTGRES_PROXY_PORT);
    conn.close();
  }

  @Test
  public void test_LostConnectionToWriter() throws SQLException, IOException {

    final String initialWriterId = instanceIDs[0];

    final Properties props = initDefaultProxiedProps();
    props.setProperty("failoverTimeoutMs", "10000");

    // Connect to cluster
    try (final Connection testConnection = connectToInstance(
            initialWriterId + DB_CONN_STR_SUFFIX + PROXIED_DOMAIN_NAME_SUFFIX, POSTGRES_PROXY_PORT, props)) {
      // Get writer
      currWriter = queryInstanceId(testConnection);

      // Put cluster & writer down
      final Proxy proxyInstance = proxyMap.get(currWriter);
      if (proxyInstance != null) {
        containerHelper.disableConnectivity(proxyInstance);
      } else {
        fail(String.format("%s does not have a proxy setup.", currWriter));
      }
      containerHelper.disableConnectivity(proxyCluster);

      assertFirstQueryThrows(testConnection, "08001");

    } finally {
      final Proxy proxyInstance = proxyMap.get(currWriter);
      assertNotNull(proxyInstance, "Proxy isn't found for " + currWriter);
      containerHelper.enableConnectivity(proxyInstance);
      containerHelper.enableConnectivity(proxyCluster);
    }
  }

  @Test
  public void test_LostConnectionToAllReaders() throws SQLException {

    String currentWriterId = instanceIDs[0];
    String anyReaderId = instanceIDs[1];

    // Get Writer
    try (final Connection checkWriterConnection = connectToInstance(
            currentWriterId + DB_CONN_STR_SUFFIX + PROXIED_DOMAIN_NAME_SUFFIX, POSTGRES_PROXY_PORT)) {
      currWriter = queryInstanceId(checkWriterConnection);
    }

    // Connect to cluster
    try (final Connection testConnection = connectToInstance(
            anyReaderId + DB_CONN_STR_SUFFIX + PROXIED_DOMAIN_NAME_SUFFIX, POSTGRES_PROXY_PORT)) {
      // Get reader
      currReader = queryInstanceId(testConnection);
      assertNotEquals(currWriter, currReader);

      // Put all but writer down
      proxyMap.forEach((instance, proxy) -> {
        if (!instance.equalsIgnoreCase(currWriter)) {
          try {
            containerHelper.disableConnectivity(proxy);
          } catch (IOException e) {
            fail("Toxics were already set, should not happen");
          }
        }
      });

      assertFirstQueryThrows(testConnection, "08S02");

      final String newReader = queryInstanceId(testConnection);
      assertEquals(currWriter, newReader);
    } finally {
      proxyMap.forEach((instance, proxy) -> {
        assertNotNull(proxy, "Proxy isn't found for " + instance);
        containerHelper.enableConnectivity(proxy);
      });
    }
  }

  @Test
  public void test_LostConnectionToReaderInstance() throws SQLException, IOException {

    String currentWriterId = instanceIDs[0];
    String anyReaderId = instanceIDs[1];

    // Get Writer
    try (final Connection checkWriterConnection = connectToInstance(
            currentWriterId + DB_CONN_STR_SUFFIX + PROXIED_DOMAIN_NAME_SUFFIX, POSTGRES_PROXY_PORT)) {
      currWriter = queryInstanceId(checkWriterConnection);
    } catch (SQLException e) {
      fail(e);
    }

    // Connect to instance
    try (final Connection testConnection = connectToInstance(
            anyReaderId + DB_CONN_STR_SUFFIX + PROXIED_DOMAIN_NAME_SUFFIX, POSTGRES_PROXY_PORT)) {
      // Get reader
      currReader = queryInstanceId(testConnection);

      // Put down current reader
      final Proxy proxyInstance = proxyMap.get(currReader);
      if (proxyInstance != null) {
        containerHelper.disableConnectivity(proxyInstance);
      } else {
        fail(String.format("%s does not have a proxy setup.", currReader));
      }

      assertFirstQueryThrows(testConnection, "08S02");

      final String newInstance = queryInstanceId(testConnection);
      assertEquals(currWriter, newInstance);
    } finally {
      final Proxy proxyInstance = proxyMap.get(currReader);
      assertNotNull(proxyInstance, "Proxy isn't found for " + currReader);
      containerHelper.enableConnectivity(proxyInstance);
    }
  }

  @Test
  public void test_LostConnectionReadOnly() throws SQLException, IOException {

    String currentWriterId = instanceIDs[0];
    String anyReaderId = instanceIDs[1];

    // Get Writer
    try (final Connection checkWriterConnection = connectToInstance(
            currentWriterId + DB_CONN_STR_SUFFIX + PROXIED_DOMAIN_NAME_SUFFIX, POSTGRES_PROXY_PORT)) {
      currWriter = queryInstanceId(checkWriterConnection);
    }

    // Connect to instance
    try (final Connection testConnection = connectToInstance(
            anyReaderId + DB_CONN_STR_SUFFIX + PROXIED_DOMAIN_NAME_SUFFIX, POSTGRES_PROXY_PORT)) {
      // Get reader
      currReader = queryInstanceId(testConnection);

      testConnection.setReadOnly(true);

      // Put down current reader
      final Proxy proxyInstance = proxyMap.get(currReader);
      if (proxyInstance != null) {
        containerHelper.disableConnectivity(proxyInstance);
      } else {
        fail(String.format("%s does not have a proxy setup.", currReader));
      }

      assertFirstQueryThrows(testConnection, "08S02");

      final String newInstance = queryInstanceId(testConnection);
      assertNotEquals(currWriter, newInstance);
    } finally {
      final Proxy proxyInstance = proxyMap.get(currReader);
      assertNotNull(proxyInstance, "Proxy isn't found for " + currReader);
      containerHelper.enableConnectivity(proxyInstance);
    }
  }

  @Test
  void test_ValidInvalidValidConnections() throws SQLException {
    final Properties validProp = initDefaultProps();
    validProp.setProperty(PropertyKey.USER.getKeyName(), AURORA_POSTGRES_USERNAME);
    validProp.setProperty(PropertyKey.PASSWORD.getKeyName(), AURORA_POSTGRES_PASSWORD);
    final Connection validConn = connectToInstance(POSTGRES_INSTANCE_1_URL, AURORA_POSTGRES_PORT, validProp);
    validConn.close();

    final Properties invalidProp = initDefaultProps();
    invalidProp.setProperty(PropertyKey.USER.getKeyName(), "INVALID_" + AURORA_POSTGRES_USERNAME);
    invalidProp.setProperty(PropertyKey.PASSWORD.getKeyName(), "INVALID_" + AURORA_POSTGRES_PASSWORD);
    assertThrows(
            SQLException.class,
            () -> connectToInstance(POSTGRES_INSTANCE_1_URL, AURORA_POSTGRES_PORT, invalidProp)
    );

    final Connection validConn2 = connectToInstance(POSTGRES_INSTANCE_1_URL, AURORA_POSTGRES_PORT, validProp);
    validConn2.close();
  }

  /**
   * Current writer dies, no available reader instance, connection fails.
   */
  @Test
  public void test_writerConnectionFailsDueToNoReader() throws SQLException, IOException {

    final String currentWriterId = instanceIDs[0];

    Properties props = initDefaultProxiedProps();
    props.setProperty("failoverTimeoutMs", "10000");
    try (Connection conn = connectToInstance(
            currentWriterId + DB_CONN_STR_SUFFIX + PROXIED_DOMAIN_NAME_SUFFIX,
            POSTGRES_PROXY_PORT,
            props)) {
      // Put all but writer down first
      proxyMap.forEach((instance, proxy) -> {
        if (!instance.equalsIgnoreCase(currentWriterId)) {
          try {
            containerHelper.disableConnectivity(proxy);
          } catch (IOException e) {
            fail("Toxics were already set, should not happen");
          }
        }
      });

      // Crash the writer now
      final Proxy proxyInstance = proxyMap.get(currentWriterId);
      if (proxyInstance != null) {
        containerHelper.disableConnectivity(proxyInstance);
      } else {
        fail(String.format("%s does not have a proxy setup.", currentWriterId));
      }

      // All instances should be down, assert exception thrown with SQLState code 08001
      // (SQL_STATE_UNABLE_TO_CONNECT_TO_DATASOURCE)
      assertFirstQueryThrows(conn, "08001");
    } finally {
      proxyMap.forEach((instance, proxy) -> {
        assertNotNull(proxy, "Proxy isn't found for " + instance);
        containerHelper.enableConnectivity(proxy);
      });
    }
  }

  /**
   * Current reader dies, after failing to connect to several reader instances, failover to another reader.
   */
  @Test
  public void test_failFromReaderToReaderWithSomeReadersAreDown()
          throws SQLException, IOException {
    assertTrue(clusterSize >= 3, "Minimal cluster configuration: 1 writer + 2 readers");
    final String readerNode = instanceIDs[1];

    Properties props = initDefaultProxiedProps();
    try (Connection conn = connectToInstance(readerNode + DB_CONN_STR_SUFFIX + PROXIED_DOMAIN_NAME_SUFFIX,
            POSTGRES_PROXY_PORT, props)) {
      // First kill all reader instances except one
      for (int i = 1; i < clusterSize - 1; i++) {
        final String instanceId = instanceIDs[i];
        final Proxy proxyInstance = proxyMap.get(instanceId);
        if (proxyInstance != null) {
          containerHelper.disableConnectivity(proxyInstance);
        } else {
          fail(String.format("%s does not have a proxy setup.", instanceId));
        }
      }

      assertFirstQueryThrows(conn, "08S02");

      // Assert that we failed over to the only remaining reader instance (Instance5) OR Writer
      // instance (Instance1).
      final String currentConnectionId = queryInstanceId(conn);
      assertTrue(
              currentConnectionId.equals(instanceIDs[clusterSize - 1]) // Last reader
                      || currentConnectionId.equals(instanceIDs[0])); // Writer
    }
  }

  /**
   * Current reader dies, failover to another reader repeat to loop through instances in the cluster testing ability to
   * revive previously down reader instance.
   */
  @Test
  @Disabled
  public void test_failoverBackToThePreviouslyDownReader() throws Exception {

    assertTrue(clusterSize >= 5, "Minimal cluster configuration: 1 writer + 4 readers");

    final String writerInstanceId = instanceIDs[0];
    final String firstReaderInstanceId = instanceIDs[1];

    // Connect to reader (Instance2).
    Properties props = initDefaultProxiedProps();
    try (Connection conn = connectToInstance(firstReaderInstanceId + DB_CONN_STR_SUFFIX + PROXIED_DOMAIN_NAME_SUFFIX,
            POSTGRES_PROXY_PORT, props)) {
      conn.setReadOnly(true);

      // Start crashing reader (Instance2).
      Proxy proxyInstance = proxyMap.get(firstReaderInstanceId);
      containerHelper.disableConnectivity(proxyInstance);

      assertFirstQueryThrows(conn, "08S02");

      // Assert that we are connected to another reader instance.
      final String secondReaderInstanceId = queryInstanceId(conn);
      assertTrue(isDBInstanceReader(secondReaderInstanceId));
      assertNotEquals(firstReaderInstanceId, secondReaderInstanceId);

      // Crash the second reader instance.
      proxyInstance = proxyMap.get(secondReaderInstanceId);
      containerHelper.disableConnectivity(proxyInstance);

      assertFirstQueryThrows(conn, "08S02");

      // Assert that we are connected to the third reader instance.
      final String thirdReaderInstanceId = queryInstanceId(conn);
      assertTrue(isDBInstanceReader(thirdReaderInstanceId));
      assertNotEquals(firstReaderInstanceId, thirdReaderInstanceId);
      assertNotEquals(secondReaderInstanceId, thirdReaderInstanceId);

      // Grab the id of the fourth reader instance.
      final HashSet<String> readerInstanceIds = new HashSet<>(Arrays.asList(instanceIDs));
      readerInstanceIds.remove(writerInstanceId); // Writer
      readerInstanceIds.remove(firstReaderInstanceId);
      readerInstanceIds.remove(secondReaderInstanceId);
      readerInstanceIds.remove(thirdReaderInstanceId);

      final String fourthInstanceId = readerInstanceIds.stream().findFirst()
              .orElseThrow(() -> new Exception("Empty instance Id"));

      // Crash the fourth reader instance.
      proxyInstance = proxyMap.get(fourthInstanceId);
      containerHelper.disableConnectivity(proxyInstance);

      // Stop crashing the first and second.
      proxyInstance = proxyMap.get(firstReaderInstanceId);
      containerHelper.enableConnectivity(proxyInstance);

      proxyInstance = proxyMap.get(secondReaderInstanceId);
      containerHelper.enableConnectivity(proxyInstance);

      final String currentInstanceId = queryInstanceId(conn);
      assertEquals(thirdReaderInstanceId, currentInstanceId);

      // Start crashing the third instance.
      proxyInstance = proxyMap.get(thirdReaderInstanceId);
      containerHelper.disableConnectivity(proxyInstance);

      assertFirstQueryThrows(conn, "08S02");

      final String lastInstanceId = queryInstanceId(conn);

      assertTrue(
              firstReaderInstanceId.equals(lastInstanceId)
                      || secondReaderInstanceId.equals(lastInstanceId));
    }
  }

  @Test
  public void testSuccessOpenConnection() throws SQLException {

    final String url = buildConnectionString(
        DB_CONN_STR_PREFIX,
        POSTGRES_INSTANCE_1_URL,
        String.valueOf(AURORA_POSTGRES_PORT),
        AURORA_POSTGRES_DB);

    Properties props = new Properties();
    props.setProperty("user", AURORA_POSTGRES_USERNAME);
    props.setProperty("password", AURORA_POSTGRES_PASSWORD);

    Connection conn = connectToInstanceCustomUrl(url, props);

    assertTrue(conn instanceof ConnectionWrapper);
    assertTrue(conn.isWrapperFor(org.postgresql.PGConnection.class));

    assertTrue(conn.isValid(10));
    conn.close();
  }

  @Test
  public void testSuccessOpenConnectionNoPort() throws SQLException {

    final String url = DB_CONN_STR_PREFIX + POSTGRES_INSTANCE_1_URL  + "/" + AURORA_POSTGRES_DB;

    Properties props = new Properties();
    props.setProperty("user", AURORA_POSTGRES_USERNAME);
    props.setProperty("password", AURORA_POSTGRES_PASSWORD);

    Connection conn = connectToInstanceCustomUrl(url, props);

    assertTrue(conn instanceof ConnectionWrapper);
    assertTrue(conn.isWrapperFor(org.postgresql.PGConnection.class));

    assertTrue(conn.isValid(10));
    conn.close();
  }

  @ParameterizedTest
  @MethodSource("testParameters")
  public void testFailedConnection(String url, String user, String password) {

    Properties props = new Properties();
    props.setProperty("user", user);
    props.setProperty("password", password);

    assertThrows(SQLException.class, () -> connectToInstanceCustomUrl(url, props));
  }

  @Test
  public void testFailedHost() {

    Properties props = new Properties();
    props.setProperty("user", AURORA_POSTGRES_USERNAME);
    props.setProperty("password", AURORA_POSTGRES_PASSWORD);
    String url = buildConnectionString(
        DB_CONN_STR_PREFIX,
        "",
        String.valueOf(AURORA_POSTGRES_PORT),
        AURORA_POSTGRES_DB);
    assertThrows(RuntimeException.class, () -> connectToInstanceCustomUrl(
        url, props));
  }
}
