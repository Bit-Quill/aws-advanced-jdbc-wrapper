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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
import java.util.concurrent.Callable;
import javax.xml.transform.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import software.amazon.jdbc.HostRole;
import software.amazon.jdbc.HostSpec;
import software.amazon.jdbc.JdbcCallable;
import software.amazon.jdbc.PluginService;
import software.amazon.jdbc.PropertyDefinition;
import software.amazon.jdbc.plugin.AwsSecretsManagerConnectionPlugin;
import software.amazon.jdbc.plugin.failover.FailoverSuccessSQLException;
import software.amazon.jdbc.util.SqlState;

public class ReadWriteSplittingPluginTest {
//133
  HostSpec instanceUrlHostSpec = new HostSpec("jdbc:aws-wrapper:postgresql://my-instance-name.XYZ.us-east-2.rds.amazonaws.com", TEST_PORT);
  //141
  HostSpec ipUrlHostSpec = new HostSpec("jdbc:aws-wrapper:postgresql://10.10.10.10", TEST_PORT);
 //143
  HostSpec clusterUrlHostSpec = new HostSpec("jdbc:aws-wrapper:postgresql://my-cluster-name.cluster-XYZ.us-east-2.rds.amazonaws.com", TEST_PORT);
  HostSpec updatedCurrentHostSpecNull = new HostSpec("none", TEST_PORT);

  private static final String TEST_PROTOCOL = "jdbc:postgresql:";
  private static final String TEST_SQL_ERROR = "SQL exception error message";
  private static final String UNHANDLED_ERROR_CODE = "HY000";
  private static final int WRITER_INDEX = 0;
  private static final int TEST_PORT = 5432;
  @Spy private final HostSpec writerHostSpec = new HostSpec("instance-0",TEST_PORT);
  private static final HostSpec readerHostSpec1 = new HostSpec("instance-1", TEST_PORT, HostRole.READER);
  private static final HostSpec readerHostSpec2 = new HostSpec("instance-2", TEST_PORT, HostRole.READER);
  private static final HostSpec readerHostSpec3 = new HostSpec("instance-3", TEST_PORT, HostRole.READER);

  private static final Properties TEST_PROPS = new Properties();
  private ReadWriteSplittingPlugin plugin;
  private final List<HostSpec> defaultHosts = Arrays.asList(
      writerHostSpec,
      readerHostSpec1,
      readerHostSpec2,
      readerHostSpec3);

  private final List<HostSpec> oneReaderTopology = Arrays.asList(
      writerHostSpec,
      readerHostSpec1);

  private AutoCloseable closeable;

  @Mock
  JdbcCallable<Connection, SQLException> connectFunc;

  @Mock
  PluginService mockPluginService;

  @Mock private Connection mockWriterConn;
  @Mock private Connection mockReaderConn1;
  @Mock private Connection mockReaderConn2;
  @Mock private Connection mockReaderConn3;
  @Mock private Connection mockClosedWriterConn;
  @Mock private Connection mockNewWriterConn;

  @Mock JdbcCallable<ResultSet, SQLException> mockSqlFunction;




  @BeforeEach
  public void init() throws SQLException {
    closeable = MockitoAnnotations.openMocks(this);
    this.plugin = new ReadWriteSplittingPlugin(mockPluginService, TEST_PROPS);
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

    when(this.connectFunc.call()).thenReturn(mockWriterConn);
    mockClosedConnectionBehavior(mockClosedWriterConn);
  }

  void mockClosedConnectionBehavior(Connection mockConn) throws SQLException {
    when(mockConn.isClosed()).thenReturn(true);
  }

  @Test
  public void testSetReadOnly_trueFalse() throws SQLException {
    // passed
    when(this.mockPluginService.getHosts()).thenReturn(oneReaderTopology);
    when(mockPluginService.getCurrentConnection()).thenReturn(mockClosedWriterConn);

    plugin.setWriterConnection(mockWriterConn);
    plugin.explicitlyReadOnly = true;
    plugin.switchConnectionIfRequired();

    verify(mockPluginService, times(1)).setCurrentConnection(eq(mockReaderConn1), not(eq(writerHostSpec)));
    verify(mockPluginService, times(0)).setCurrentConnection(eq(mockWriterConn), any(HostSpec.class));
    assertEquals(mockReaderConn1, plugin.getReaderConnection());
    assertEquals(mockWriterConn, plugin.getWriterConnection());

    when(mockPluginService.getCurrentConnection()).thenReturn(mockReaderConn1);
    when(mockPluginService.getCurrentHostSpec()).thenReturn(readerHostSpec1);


    plugin.explicitlyReadOnly = false;
    plugin.switchConnectionIfRequired();

    verify(mockPluginService, times(1)).setCurrentConnection(eq(mockReaderConn1), not(eq(writerHostSpec)));
    //verify(mockPluginService, times(1)).setCurrentConnection(eq(mockWriterConn), eq(writerHostSpec));
    assertEquals(mockReaderConn1, plugin.getReaderConnection());
    assertEquals(mockWriterConn, plugin.getWriterConnection());

  }

  @Test
  public void testSetReadOnly_trueTrue() throws SQLException {
    // passed
    when(this.mockPluginService.getHosts()).thenReturn(oneReaderTopology);
    when(mockPluginService.getCurrentConnection()).thenReturn(mockClosedWriterConn);

    plugin.setWriterConnection(mockWriterConn);
    plugin.explicitlyReadOnly = true;
    plugin.switchConnectionIfRequired();

    verify(mockPluginService, times(1)).setCurrentConnection(eq(mockReaderConn1), not(eq(writerHostSpec)));
    verify(mockPluginService, times(0)).setCurrentConnection(eq(mockWriterConn), any(HostSpec.class));
    assertEquals(mockReaderConn1, plugin.getReaderConnection());
    assertEquals(mockWriterConn, plugin.getWriterConnection());

    when(mockPluginService.getCurrentConnection()).thenReturn(mockReaderConn1);
    when(mockPluginService.getCurrentHostSpec()).thenReturn(readerHostSpec1);

    plugin.explicitlyReadOnly = true;
    plugin.switchConnectionIfRequired();
    verify(mockPluginService, times(1)).setCurrentConnection(eq(mockReaderConn1), not(eq(writerHostSpec)));
    verify(mockPluginService, times(0)).setCurrentConnection(eq(mockWriterConn), any(HostSpec.class));
    assertEquals(mockReaderConn1, plugin.getReaderConnection());
    assertEquals(mockWriterConn, plugin.getWriterConnection());

  }

  @Test
  public void testSetReadOnly_falseInTransaction() throws Exception {
    when(this.mockPluginService.getCurrentConnection()).thenReturn(mockReaderConn1);
    when(this.mockPluginService.getCurrentHostSpec()).thenReturn(readerHostSpec1);

    plugin.switchConnectionIfRequired();

    verify(mockPluginService, times(1)).setCurrentConnection(eq(mockReaderConn1), not(eq(writerHostSpec)));
    verify(mockPluginService, times(0)).setCurrentConnection(eq(mockWriterConn), any(HostSpec.class));
    assertEquals(mockReaderConn1, plugin.getReaderConnection());
    assertEquals(mockWriterConn, plugin.getWriterConnection());
//
//    plugin.execute(ResultSet.class, SQLException.class, Connection.class, "Connection.executeQuery", mockSqlFunction, new Object[] { "begin" });
//
//    final SQLException e = assertThrows(SQLException.class, () -> plugin.switchConnectionIfRequired());
//    assertEquals("25001", e.getSQLState());
//    verify(mockPluginService, times(1)).setCurrentConnection(eq(mockReaderConn1), not(eq(writerHostSpec)));
//    verify(mockPluginService, times(0)).setCurrentConnection(eq(mockWriterConn), any(HostSpec.class));
//    assertEquals(mockReaderConn1, plugin.getReaderConnection());
//    assertEquals(mockWriterConn, plugin.getWriterConnection());
  }
//
//      plugin.execute(
//  ResultSet .class,
//  SQLException.class,
//  MONITOR_METHOD_INVOKE_ON,
//      "close",
//  mockSqlFunction,
//  EMPTY_ARGS);
//  verify(mockSqlFunction).call();
//  verify(mockHostListProvider, never()).getRdsUrlType();

  @Test
  public void testSetReadOnly_true() throws SQLException {
    // passed
    plugin.explicitlyReadOnly = true;
    plugin.switchConnectionIfRequired();

    assertThat(plugin.getReaderConnection(), anyOf(is(mockReaderConn1), is(mockReaderConn2), is(mockReaderConn3)));
  }

  @Test
  public void testSetReadOnly_false() throws SQLException {
    // passed
    when(this.mockPluginService.getCurrentConnection()).thenReturn(mockReaderConn1);
    when(this.mockPluginService.getCurrentHostSpec()).thenReturn(readerHostSpec1);

    plugin.switchConnectionIfRequired();
    // verify we established a new writer connection
    // verify that connection plugin is what we expect it to be
    assertEquals(mockWriterConn, plugin.getWriterConnection());
  }

  @Test
  public void testSetReadOnly_true_oneHost() throws SQLException {
    // passed
    when(this.mockPluginService.getHosts()).thenReturn(Arrays.asList(writerHostSpec));

    plugin.explicitlyReadOnly = true;
    plugin.switchConnectionIfRequired();

    verify(mockPluginService, times(0)).setCurrentConnection(any(Connection.class), any(HostSpec.class));
    assertEquals(mockWriterConn, plugin.getWriterConnection());
    assertEquals(mockWriterConn, plugin.getReaderConnection());
  }

  @Test
  public void testSetReadOnly_true_oneHost_writerClosed() throws SQLException {
    // passed
    when(this.mockPluginService.getHosts()).thenReturn(Arrays.asList(writerHostSpec));
    when(this.mockPluginService.getCurrentConnection()).thenReturn(mockClosedWriterConn);

    plugin.explicitlyReadOnly = true;
    plugin.switchConnectionIfRequired();

    verify(mockPluginService, times(1)).setCurrentConnection(eq(mockWriterConn), eq(writerHostSpec));
    verify(mockPluginService, times(0)).setCurrentConnection(not(eq(mockWriterConn)), eq(writerHostSpec));
    assertEquals(mockWriterConn, plugin.getWriterConnection());
    assertEquals(mockWriterConn, plugin.getReaderConnection());
  }

  @Test
  public void testSetReadOnly_true_noReaderHostMatch() throws SQLException {
    // passed
    when(this.mockPluginService.getHosts()).thenReturn(oneReaderTopology);
    when(mockPluginService.getCurrentConnection()).thenReturn(mockWriterConn);

    plugin.setWriterConnection(mockWriterConn);
    final List<HostSpec> hosts = plugin.getHosts();
    plugin.explicitlyReadOnly = true;
    plugin.switchConnectionIfRequired();

    when(mockPluginService.getCurrentConnection()).thenReturn(mockReaderConn1);
    when(mockPluginService.getCurrentHostSpec()).thenReturn(readerHostSpec1);

    plugin.explicitlyReadOnly = false;
    plugin.switchConnectionIfRequired();
    hosts.remove(WRITER_INDEX + 1);

    verify(mockPluginService, times(1)).setCurrentConnection(eq(mockReaderConn1), eq(readerHostSpec1));
    verify(mockPluginService, times(1)).setCurrentConnection(eq(mockWriterConn), eq(writerHostSpec));
    assertEquals(mockReaderConn1, plugin.getReaderConnection());

    when(mockPluginService.getCurrentConnection()).thenReturn(mockWriterConn);

    plugin.explicitlyReadOnly = true;
    plugin.switchConnectionIfRequired();

    verify(mockPluginService, times(1)).setCurrentConnection(eq(mockReaderConn1), eq(readerHostSpec1));
    verify(mockPluginService, times(1)).setCurrentConnection(eq(mockWriterConn), eq(writerHostSpec));
    assertEquals(mockReaderConn1, plugin.getReaderConnection());
  }

  //zzz
  @Test
  public void testSetReadOnly_true_noReaderHostMatch_writerClosed() throws SQLException {
    when(this.mockPluginService.getHosts()).thenReturn(oneReaderTopology);
    when(this.mockPluginService.getCurrentConnection()).thenReturn(mockClosedWriterConn);

//
//    when(mockPluginService.getCurrentConnection())
//        .thenReturn(mockClosedWriterConn, mockClosedWriterConn, mockClosedWriterConn, mockReaderConn1, mockClosedWriterConn);

    final List<HostSpec> hosts = plugin.getHosts();
    plugin.explicitlyReadOnly = true;
    plugin.switchConnectionIfRequired();

    when(mockPluginService.getCurrentConnection()).thenReturn(mockReaderConn1);
    when(mockPluginService.getCurrentHostSpec()).thenReturn(readerHostSpec1);

    plugin.explicitlyReadOnly = false;
    plugin.switchConnectionIfRequired();
    hosts.remove(WRITER_INDEX + 1);

    when(this.mockPluginService.getHosts()).thenReturn(Arrays.asList(writerHostSpec));


    verify(mockPluginService, times(1)).setCurrentConnection(eq(mockReaderConn1), eq(readerHostSpec1));
    verify(mockPluginService, times(1)).setCurrentConnection(eq(mockWriterConn), eq(writerHostSpec));
    assertEquals(mockReaderConn1, plugin.getReaderConnection());

    when(this.mockPluginService.getCurrentConnection()).thenReturn(mockClosedWriterConn);
    when(mockPluginService.connect(eq(writerHostSpec), eq(TEST_PROPS))).thenThrow(SQLException.class);
    when(mockPluginService.connect(eq(readerHostSpec1), eq(TEST_PROPS))).thenThrow(SQLException.class);

    plugin.explicitlyReadOnly = true;
    final SQLException e = assertThrows(SQLException.class, () -> plugin.switchConnectionIfRequired());
    assertEquals("08001", e.getSQLState());
    verify(mockPluginService, times(1)).setCurrentConnection(eq(mockReaderConn1), eq(readerHostSpec1));
    verify(mockPluginService, times(1)).setCurrentConnection(eq(mockWriterConn), eq(writerHostSpec));
    assertEquals(mockReaderConn1, plugin.getReaderConnection());

  }

  @Test
  public void testSetReadOnly_false_writerConnectionFails() throws SQLException {
    // passed
    when(mockPluginService.connect(eq(writerHostSpec), eq(TEST_PROPS))).thenThrow(SQLException.class);
    when(this.mockPluginService.getHosts()).thenReturn(oneReaderTopology);
    when(mockPluginService.getCurrentConnection()).thenReturn(mockClosedWriterConn);

    plugin.explicitlyReadOnly = true;
    plugin.switchConnectionIfRequired();

    verify(mockPluginService, times(1)).setCurrentConnection(eq(mockReaderConn1), eq(readerHostSpec1));
    verify(mockPluginService, times(0)).setCurrentConnection(eq(mockClosedWriterConn), any(HostSpec.class));
    assertEquals(mockReaderConn1, plugin.getReaderConnection());

    when(mockPluginService.getCurrentConnection()).thenReturn(mockReaderConn1);
    when(mockPluginService.getCurrentHostSpec()).thenReturn(readerHostSpec1);

    plugin.explicitlyReadOnly = false;
    final SQLException e = assertThrows(SQLException.class, () -> plugin.switchConnectionIfRequired());
    assertEquals("08001", e.getSQLState());
    verify(mockPluginService, times(1)).setCurrentConnection(eq(mockReaderConn1), eq(readerHostSpec1));
    verify(mockPluginService, times(0)).setCurrentConnection(eq(mockClosedWriterConn), any(HostSpec.class));
    assertEquals(mockReaderConn1, plugin.getReaderConnection());
  }

  @Test
  public void testSetReadOnly_true_readerConnectionFailed() throws SQLException {
    // passed
    when(this.mockPluginService.connect(eq(readerHostSpec1), eq(TEST_PROPS))).thenThrow(SQLException.class);
    when(this.mockPluginService.connect(eq(readerHostSpec2), eq(TEST_PROPS))).thenThrow(SQLException.class);
    when(this.mockPluginService.connect(eq(readerHostSpec3), eq(TEST_PROPS))).thenThrow(SQLException.class);

    plugin.explicitlyReadOnly = true;
    plugin.switchConnectionIfRequired();

    verify(mockPluginService, times(0)).setCurrentConnection(any(Connection.class), any(HostSpec.class));
    assertEquals(mockWriterConn, plugin.getReaderConnection());
  }

  @Test
  public void testSetReadOnly_true_readerConnectionFails_writerClosed() throws SQLException {
    // passed
    when(mockPluginService.connect(eq(readerHostSpec1),eq(TEST_PROPS))).thenThrow(SQLException.class);
    when(mockPluginService.connect(eq(readerHostSpec2),eq(TEST_PROPS))).thenThrow(SQLException.class);
    when(mockPluginService.connect(eq(readerHostSpec3),eq(TEST_PROPS))).thenThrow(SQLException.class);
    when(mockPluginService.getCurrentConnection()).thenReturn(mockClosedWriterConn);

    plugin.explicitlyReadOnly = true;
    final SQLException e = assertThrows(SQLException.class, () -> plugin.switchConnectionIfRequired());
    assertEquals("08001", e.getSQLState());
    verify(mockPluginService, times(0)).setCurrentConnection(any(Connection.class), any(HostSpec.class));
    assertNull(plugin.getReaderConnection());
  }

  @Test
  public void testExecute_failoverToNewWriter() throws SQLException {
   // passed
    when(mockSqlFunction.call()).thenThrow(FailoverSuccessSQLException.class);
    when(mockPluginService.getCurrentConnection()).thenReturn(mockNewWriterConn);
    plugin.setWriterConnection(mockWriterConn);
    assertThrows(SQLException.class, () -> plugin.execute(ResultSet.class, SQLException.class, Statement.class, "Statement.executeQuery", mockSqlFunction, new Object[] { "begin" }));
    assertNull(plugin.getReaderConnection());
    verify(mockWriterConn, times(1)).close();
  }

  @Test
  public void testConnectWithCurrentConnectionDoesNotEqualNull() throws SQLException {
    // passed
    Connection connection = plugin.connect(TEST_PROTOCOL, writerHostSpec, TEST_PROPS, true, this.connectFunc);

    assertEquals(mockWriterConn, connection);
    verify(connectFunc).call();
    verify(writerHostSpec, times(0)).getHost();
  }

  @Test
  public void testConnectRdsInstanceUrl() throws SQLException {
    //131
    when(this.mockPluginService.getCurrentConnection()).thenReturn(null, mockWriterConn);
    Connection connection = plugin.connect(TEST_PROTOCOL, instanceUrlHostSpec, TEST_PROPS, true, this.connectFunc);
    assertEquals(mockWriterConn, connection);
    verify(connectFunc).call();
    verify(writerHostSpec, times(0)).getHost();
  }

  @Test
  public void testConnectIpUrl() throws SQLException {
    // 141
    when(this.mockPluginService.getCurrentConnection()).thenReturn(null, mockWriterConn);
    Connection connection = plugin.connect(TEST_PROTOCOL, ipUrlHostSpec, TEST_PROPS, true, this.connectFunc);
    assertEquals(mockWriterConn, connection);
    //verify(connectFunc).call();
  //  verify(writerHostSpec, times(0)).getHost();

  }

  @Test
  public void testConnectClusterUrl() throws SQLException {
    // line 143
     // passed
    when(this.mockPluginService.getCurrentConnection()).thenReturn(null, mockWriterConn);
    Connection connection = plugin.connect(TEST_PROTOCOL, clusterUrlHostSpec, TEST_PROPS, true, this.connectFunc);
    assertEquals(mockWriterConn, connection);
    verify(connectFunc).call();
    verify(writerHostSpec, times(0)).getHost();
  }

  @Test
  public void testConnectUpdatedCurrentHost_Null() throws SQLException {
    when(this.mockPluginService.getCurrentConnection()).thenReturn(null, mockWriterConn);
    assertThrows(SQLException.class, () -> plugin.connect(TEST_PROTOCOL, clusterUrlHostSpec, TEST_PROPS, true, this.connectFunc));
  }



}