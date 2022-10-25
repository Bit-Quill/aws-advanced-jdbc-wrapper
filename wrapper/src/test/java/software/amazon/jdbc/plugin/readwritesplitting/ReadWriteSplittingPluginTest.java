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

package software.amazon.jdbc.plugin.readwritesplitting;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.AnyOf.anyOf;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.jdbc.HostListProviderService;
import software.amazon.jdbc.HostRole;
import software.amazon.jdbc.HostSpec;
import software.amazon.jdbc.JdbcCallable;
import software.amazon.jdbc.PluginService;
import software.amazon.jdbc.plugin.failover.FailoverSuccessSQLException;
import software.amazon.jdbc.util.SqlState;

public class ReadWriteSplittingPluginTest {
  private static final String TEST_PROTOCOL = "jdbc:postgresql:";
  private static final int TEST_PORT = 5432;
  private static final Properties TEST_PROPS = new Properties();

  private final HostSpec writerHostSpec = new HostSpec("instance-0", TEST_PORT);
  private final HostSpec readerHostSpec1 = new HostSpec("instance-1", TEST_PORT, HostRole.READER);
  private final HostSpec readerHostSpec2 = new HostSpec("instance-2", TEST_PORT, HostRole.READER);
  private final HostSpec readerHostSpec3 = new HostSpec("instance-3", TEST_PORT, HostRole.READER);
  private final HostSpec readerHostSpecWithIncorrectRole = new HostSpec("instance-1", TEST_PORT, HostRole.WRITER);
  private final HostSpec instanceUrlHostSpec = new HostSpec(
      "jdbc:aws-wrapper:postgresql://my-instance-name.XYZ.us-east-2.rds.amazonaws.com",
      TEST_PORT);
  private final HostSpec ipUrlHostSpec = new HostSpec("jdbc:aws-wrapper:postgresql://10.10.10.10", TEST_PORT);
  private final HostSpec clusterUrlHostSpec = new HostSpec(
      "jdbc:aws-wrapper:postgresql://my-cluster-name.cluster-XYZ.us-east-2.rds.amazonaws.com",
      TEST_PORT);

  private ReadWriteSplittingPlugin plugin;

  private final List<HostSpec> defaultHosts = Arrays.asList(
      writerHostSpec,
      readerHostSpec1,
      readerHostSpec2,
      readerHostSpec3);
  private final List<HostSpec> singleReaderTopology = Arrays.asList(
      writerHostSpec,
      readerHostSpec1);

  private AutoCloseable closeable;

  @Mock private JdbcCallable<Connection, SQLException> mockConnectFunc;
  @Mock private JdbcCallable<ResultSet, SQLException> mockSqlFunction;
  @Mock private PluginService mockPluginService;
  @Mock private HostListProviderService mockHostListProviderService;
  @Mock private Connection mockWriterConn;
  @Mock private Connection mockNewWriterConn;
  @Mock private Connection mockClosedWriterConn;
  @Mock private Connection mockReaderConn1;
  @Mock private Connection mockReaderConn2;
  @Mock private Connection mockReaderConn3;
  @Mock private Statement mockStatement;
  @Mock private ResultSet mockResultSet;

  @BeforeEach
  public void init() throws SQLException {
    closeable = MockitoAnnotations.openMocks(this);
    mockDefaultBehavior();
  }

  @AfterEach
  void cleanUp() throws Exception {
    closeable.close();
    TEST_PROPS.clear();
  }

  void mockDefaultBehavior() throws SQLException {
    when(this.mockPluginService.getCurrentConnection()).thenReturn(mockWriterConn);
    when(this.mockPluginService.getCurrentHostSpec()).thenReturn(writerHostSpec);
    when(this.mockPluginService.getHosts()).thenReturn(defaultHosts);
    when(this.mockPluginService.connect(eq(writerHostSpec), eq(TEST_PROPS))).thenReturn(mockWriterConn);
    when(this.mockPluginService.connect(eq(readerHostSpec1), eq(TEST_PROPS))).thenReturn(mockReaderConn1);
    when(this.mockPluginService.connect(eq(readerHostSpec2), eq(TEST_PROPS))).thenReturn(mockReaderConn2);
    when(this.mockPluginService.connect(eq(readerHostSpec3), eq(TEST_PROPS))).thenReturn(mockReaderConn3);
    when(this.mockConnectFunc.call()).thenReturn(mockWriterConn);
    when(mockWriterConn.createStatement()).thenReturn(mockStatement);
    when(mockReaderConn1.createStatement()).thenReturn(mockStatement);
    when(mockStatement.executeQuery(any(String.class))).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(true);
    when(mockClosedWriterConn.isClosed()).thenReturn(true);
  }

  @Test
  public void testSetReadOnly_trueFalse() throws SQLException {
    when(this.mockPluginService.getHosts()).thenReturn(singleReaderTopology);
    when(mockPluginService.getCurrentConnection()).thenReturn(mockWriterConn);

    this.plugin = new ReadWriteSplittingPlugin(
        mockPluginService,
        TEST_PROPS,
        mockHostListProviderService,
        mockWriterConn,
        null);
    plugin.switchConnectionIfRequired(true);

    verify(mockPluginService, times(1)).setCurrentConnection(eq(mockReaderConn1), not(eq(writerHostSpec)));
    verify(mockPluginService, times(0)).setCurrentConnection(eq(mockWriterConn), any(HostSpec.class));
    assertEquals(mockReaderConn1, plugin.getReaderConnection());
    assertEquals(mockWriterConn, plugin.getWriterConnection());

    when(mockPluginService.getCurrentConnection()).thenReturn(mockReaderConn1);
    when(mockPluginService.getCurrentHostSpec()).thenReturn(readerHostSpec1);

    plugin.switchConnectionIfRequired(false);

    verify(mockPluginService, times(1)).setCurrentConnection(eq(mockReaderConn1), not(eq(writerHostSpec)));
    verify(mockPluginService, times(1)).setCurrentConnection(eq(mockWriterConn), eq(writerHostSpec));
    assertEquals(mockReaderConn1, plugin.getReaderConnection());
    assertEquals(mockWriterConn, plugin.getWriterConnection());
  }

  @Test
  public void testSetReadOnlyTrue_alreadyOnReader() throws SQLException {
    when(this.mockPluginService.getHosts()).thenReturn(singleReaderTopology);
    when(mockPluginService.getCurrentConnection()).thenReturn(mockReaderConn1);
    when(mockPluginService.getCurrentHostSpec()).thenReturn(readerHostSpec1);

    this.plugin = new ReadWriteSplittingPlugin(
        mockPluginService,
        TEST_PROPS,
        mockHostListProviderService,
        null,
        mockReaderConn1);
    plugin.switchConnectionIfRequired(true);

    verify(mockPluginService, times(0)).setCurrentConnection(any(Connection.class), any(HostSpec.class));
    assertEquals(mockReaderConn1, plugin.getReaderConnection());
    assertNull(plugin.getWriterConnection());
  }

  @Test
  public void testSetReadOnly_falseInTransaction() {
    when(this.mockPluginService.getCurrentConnection()).thenReturn(mockReaderConn1);
    when(this.mockPluginService.getCurrentHostSpec()).thenReturn(readerHostSpec1);
    when(this.mockPluginService.getHosts()).thenReturn(singleReaderTopology);
    when(mockPluginService.isInTransaction()).thenReturn(true);

    this.plugin = new ReadWriteSplittingPlugin(
        mockPluginService,
        TEST_PROPS,
        mockHostListProviderService,
        null,
        mockReaderConn1);

    final SQLException e = assertThrows(SQLException.class, () -> plugin.switchConnectionIfRequired(false));
    assertEquals(SqlState.ACTIVE_SQL_TRANSACTION.getState(), e.getSQLState());
  }

  @Test
  public void testSetReadOnly_true() throws SQLException {
    this.plugin = new ReadWriteSplittingPlugin(mockPluginService, TEST_PROPS);
    plugin.switchConnectionIfRequired(true);

    assertThat(plugin.getReaderConnection(), anyOf(is(mockReaderConn1), is(mockReaderConn2), is(mockReaderConn3)));
  }

  @Test
  public void testSetReadOnly_false() throws SQLException {
    when(this.mockPluginService.getCurrentConnection()).thenReturn(mockReaderConn1);
    when(this.mockPluginService.getCurrentHostSpec()).thenReturn(readerHostSpec1);

    this.plugin = new ReadWriteSplittingPlugin(
        mockPluginService,
        TEST_PROPS,
        mockHostListProviderService,
        null,
        mockReaderConn1);
    plugin.switchConnectionIfRequired(false);

    assertEquals(mockWriterConn, plugin.getWriterConnection());
  }

  @Test
  public void testSetReadOnly_true_oneHost() throws SQLException {
    when(this.mockPluginService.getHosts()).thenReturn(Arrays.asList(writerHostSpec));

    this.plugin = new ReadWriteSplittingPlugin(
        mockPluginService,
        TEST_PROPS,
        mockHostListProviderService,
        mockWriterConn,
        null);
    plugin.switchConnectionIfRequired(true);

    verify(mockPluginService, times(0)).setCurrentConnection(any(Connection.class), any(HostSpec.class));
    assertEquals(mockWriterConn, plugin.getWriterConnection());
    assertEquals(mockWriterConn, plugin.getReaderConnection());
  }

  @Test
  public void testSetReadOnly_true_oneHost_writerClosed() throws SQLException {
    when(this.mockPluginService.getHosts()).thenReturn(Arrays.asList(writerHostSpec));
    when(this.mockPluginService.getCurrentConnection()).thenReturn(mockClosedWriterConn);

    this.plugin =
        new ReadWriteSplittingPlugin(mockPluginService, TEST_PROPS, mockHostListProviderService, mockClosedWriterConn,
            null);
    plugin.switchConnectionIfRequired(true);

    verify(mockPluginService, times(1)).setCurrentConnection(eq(mockWriterConn), eq(writerHostSpec));
    verify(mockPluginService, times(0)).setCurrentConnection(not(eq(mockWriterConn)), eq(writerHostSpec));
    assertEquals(mockWriterConn, plugin.getWriterConnection());
    assertEquals(mockWriterConn, plugin.getReaderConnection());
  }

  @Test
  public void testSetReadOnly_false_writerConnectionFails() throws SQLException {
    when(mockPluginService.connect(eq(writerHostSpec), eq(TEST_PROPS))).thenThrow(SQLException.class);
    when(this.mockPluginService.getHosts()).thenReturn(singleReaderTopology);
    when(mockPluginService.getCurrentConnection()).thenReturn(mockReaderConn1);
    when(mockPluginService.getCurrentHostSpec()).thenReturn(readerHostSpec1);

    this.plugin =
        new ReadWriteSplittingPlugin(mockPluginService, TEST_PROPS, mockHostListProviderService, null, mockReaderConn1);

    final SQLException e = assertThrows(SQLException.class, () -> plugin.switchConnectionIfRequired(false));
    assertEquals(SqlState.CONNECTION_UNABLE_TO_CONNECT.getState(), e.getSQLState());
    verify(mockPluginService, times(0)).setCurrentConnection(any(Connection.class), any(HostSpec.class));
  }

  @Test
  public void testSetReadOnly_true_readerConnectionFailed() throws SQLException {
    when(this.mockPluginService.connect(eq(readerHostSpec1), eq(TEST_PROPS))).thenThrow(SQLException.class);
    when(this.mockPluginService.connect(eq(readerHostSpec2), eq(TEST_PROPS))).thenThrow(SQLException.class);
    when(this.mockPluginService.connect(eq(readerHostSpec3), eq(TEST_PROPS))).thenThrow(SQLException.class);

    this.plugin = new ReadWriteSplittingPlugin(
        mockPluginService,
        TEST_PROPS,
        mockHostListProviderService,
        mockWriterConn,
        null);
    plugin.switchConnectionIfRequired(true);

    verify(mockPluginService, times(0)).setCurrentConnection(any(Connection.class), any(HostSpec.class));
    assertEquals(mockWriterConn, plugin.getReaderConnection());
  }

  @Test
  public void testSetReadOnly_true_readerConnectionFails_writerClosed() throws SQLException {
    when(mockPluginService.connect(eq(readerHostSpec1), eq(TEST_PROPS))).thenThrow(SQLException.class);
    when(mockPluginService.connect(eq(readerHostSpec2), eq(TEST_PROPS))).thenThrow(SQLException.class);
    when(mockPluginService.connect(eq(readerHostSpec3), eq(TEST_PROPS))).thenThrow(SQLException.class);
    when(mockPluginService.getCurrentConnection()).thenReturn(mockClosedWriterConn);

    this.plugin = new ReadWriteSplittingPlugin(
        mockPluginService,
        TEST_PROPS,
        mockHostListProviderService,
        mockClosedWriterConn,
        null);

    final SQLException e = assertThrows(SQLException.class, () -> plugin.switchConnectionIfRequired(true));
    assertEquals(SqlState.CONNECTION_UNABLE_TO_CONNECT.getState(), e.getSQLState());
    verify(mockPluginService, times(0)).setCurrentConnection(any(Connection.class), any(HostSpec.class));
    assertNull(plugin.getReaderConnection());
  }

  @Test
  public void testExecute_failoverToNewWriter() throws SQLException {
    when(mockSqlFunction.call()).thenThrow(FailoverSuccessSQLException.class);
    when(mockPluginService.getCurrentConnection()).thenReturn(mockNewWriterConn);

    this.plugin = new ReadWriteSplittingPlugin(
        mockPluginService,
        TEST_PROPS,
        mockHostListProviderService,
        mockWriterConn,
        null);

    assertThrows(
        SQLException.class,
        () -> plugin.execute(
            ResultSet.class,
            SQLException.class,
            Statement.class,
            "Statement.executeQuery",
            mockSqlFunction,
            new Object[] {
                "begin"}));
    verify(mockWriterConn, times(1)).close();
  }

  @Test
  public void testConnectNonInitialConnection() throws SQLException {
    this.plugin = new ReadWriteSplittingPlugin(
        mockPluginService,
        TEST_PROPS,
        mockHostListProviderService,
        mockWriterConn,
        null);

    Connection connection = plugin.connect(TEST_PROTOCOL, writerHostSpec, TEST_PROPS, false, this.mockConnectFunc);

    assertEquals(mockWriterConn, connection);
    verify(mockConnectFunc).call();
    verify(mockHostListProviderService, times(0)).setInitialConnectionHostSpec(any(HostSpec.class));
  }

  @Test
  public void testConnectRdsInstanceUrl() throws SQLException {
    when(this.mockPluginService.getCurrentConnection()).thenReturn(null);
    when(this.mockPluginService.getCurrentHostSpec()).thenReturn(readerHostSpecWithIncorrectRole);
    when(this.mockConnectFunc.call()).thenReturn(mockReaderConn1);

    this.plugin = new ReadWriteSplittingPlugin(
        mockPluginService,
        TEST_PROPS,
        mockHostListProviderService,
        null,
        null);
    Connection connection = plugin.connect(TEST_PROTOCOL, instanceUrlHostSpec, TEST_PROPS, true, this.mockConnectFunc);

    assertEquals(mockReaderConn1, connection);
    verify(mockConnectFunc).call();
    verify(mockHostListProviderService, times(1)).setInitialConnectionHostSpec(eq(readerHostSpec1));
  }

  @Test
  public void testConnectIpUrl() throws SQLException {
    when(this.mockPluginService.getCurrentConnection()).thenReturn(null);
    when(this.mockPluginService.getCurrentHostSpec()).thenReturn(readerHostSpecWithIncorrectRole);
    when(this.mockConnectFunc.call()).thenReturn(mockReaderConn1);
    when(mockResultSet.getString(any(String.class))).thenReturn("instance-1");

    this.plugin = new ReadWriteSplittingPlugin(
        mockPluginService,
        TEST_PROPS,
        mockHostListProviderService,
        null,
        null);
    Connection connection = plugin.connect(TEST_PROTOCOL, ipUrlHostSpec, TEST_PROPS, true, this.mockConnectFunc);

    assertEquals(mockReaderConn1, connection);
    verify(mockConnectFunc).call();
    verify(mockHostListProviderService, times(1)).setInitialConnectionHostSpec(eq(readerHostSpec1));
  }


  @Test
  public void testConnectClusterUrl() throws SQLException {
    when(this.mockPluginService.getCurrentConnection()).thenReturn(null);

    this.plugin = new ReadWriteSplittingPlugin(
        mockPluginService,
        TEST_PROPS,
        mockHostListProviderService,
        null,
        null);
    Connection connection = plugin.connect(TEST_PROTOCOL, clusterUrlHostSpec, TEST_PROPS, true, this.mockConnectFunc);

    assertEquals(mockWriterConn, connection);
    verify(mockConnectFunc).call();
    verify(mockHostListProviderService, times(0)).setInitialConnectionHostSpec(any(HostSpec.class));
  }

  @Test
  public void testConnect_errorUpdatingHostSpec() {
    when(this.mockPluginService.getCurrentConnection()).thenReturn(null);

    this.plugin = new ReadWriteSplittingPlugin(
        mockPluginService,
        TEST_PROPS,
        mockHostListProviderService,
        null,
        null);

    assertThrows(
        SQLException.class,
        () -> plugin.connect(
            TEST_PROTOCOL,
            ipUrlHostSpec,
            TEST_PROPS,
            true,
            this.mockConnectFunc));
    verify(mockHostListProviderService, times(0)).setInitialConnectionHostSpec(any(HostSpec.class));
  }
}
