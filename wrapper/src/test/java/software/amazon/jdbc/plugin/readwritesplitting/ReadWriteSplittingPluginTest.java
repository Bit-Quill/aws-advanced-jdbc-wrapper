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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.ec2.model.Host;
import software.amazon.jdbc.HostRole;
import software.amazon.jdbc.HostSpec;
import software.amazon.jdbc.JdbcCallable;
import software.amazon.jdbc.PluginService;

public class ReadWriteSplittingPluginTest {

  private static final String TEST_HOST = "test-domain";
  private static final String TEST_SQL_ERROR = "SQL exception error message";
  private static final int TEST_PORT = 5432;
  private static final HostSpec
      writerHostSpec = new HostSpec(TEST_HOST, TEST_PORT);
  private static final HostSpec readerHostSpec1 = new HostSpec(TEST_HOST, TEST_PORT, HostRole.READER);
  private static final HostSpec readerHostSpec2 = new HostSpec(TEST_HOST, TEST_PORT, HostRole.READER);
  private static final HostSpec readerHostSpec3 = new HostSpec(TEST_HOST, TEST_PORT, HostRole.READER);

  private static final Properties TEST_PROPS = new Properties();
  private ReadWriteSplittingPlugin plugin;
  private final List<HostSpec> defaultHosts = Arrays.asList(
      writerHostSpec,
      readerHostSpec1,
      readerHostSpec2,
      readerHostSpec3);

  private AutoCloseable closeable;

  @Mock
  JdbcCallable<Connection, SQLException> connectFunc;

  @Mock
  PluginService mockPluginService;

  @Mock private Connection mockWriterConn;
  @Mock private Connection mockReaderConn1;
  @Mock private Connection mockReaderConn2;
  @Mock private Connection mockReaderConn3;

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

  @Test
  public void testSetReadOnly_false() throws SQLException {
    when(this.mockPluginService.getCurrentConnection()).thenReturn(mockReaderConn1);
    when(this.mockPluginService.getCurrentHostSpec()).thenReturn(readerHostSpec1);

    plugin.switchConnectionIfRequired();
    // verify we established a new writer connection
    // verify that connection plugin is what we expect it to be
    assertEquals(mockWriterConn, plugin.getWriterConnection());
  }

  @Test
  public void testSetReadOnly_true() throws SQLException {
    plugin.explicitlyReadOnly = true;
    plugin.switchConnectionIfRequired();
    assertThat(plugin.getReaderConnection(), anyOf(is(mockReaderConn1), is(mockReaderConn2), is(mockReaderConn3)));
  }

  @Test
  public void testSetReadOnly_true_oneHost() throws SQLException {
    plugin.explicitlyReadOnly = true;
    when(this.mockPluginService.getHosts()).thenReturn(Arrays.asList(writerHostSpec));
    plugin.switchConnectionIfRequired();
    verify(mockPluginService, times(0)).setCurrentConnection(any(Connection.class), any(HostSpec.class));
    assertEquals(mockWriterConn, plugin.getWriterConnection());
    assertEquals(mockWriterConn, plugin.getReaderConnection());
  }

  @Test
  public void testSetReadOnly_true_oneHost_writerClosed() throws SQLException {

  }

  void mockDefaultBehavior() throws SQLException {
    when(this.mockPluginService.getCurrentConnection()).thenReturn(mockWriterConn);
    when(this.mockPluginService.getCurrentHostSpec()).thenReturn(writerHostSpec);
    when(this.mockPluginService.getHosts()).thenReturn(defaultHosts);
    when(this.mockPluginService.connect(eq(writerHostSpec), eq(TEST_PROPS))).thenReturn(mockWriterConn);
    when(this.mockPluginService.connect(eq(readerHostSpec1), eq(TEST_PROPS))).thenReturn(mockReaderConn1);
    when(this.mockPluginService.connect(eq(readerHostSpec2), eq(TEST_PROPS))).thenReturn(mockReaderConn2);
    when(this.mockPluginService.connect(eq(readerHostSpec3), eq(TEST_PROPS))).thenReturn(mockReaderConn3);
  }
}