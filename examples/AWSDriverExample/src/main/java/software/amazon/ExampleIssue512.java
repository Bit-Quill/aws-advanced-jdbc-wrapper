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

package software.amazon;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import software.amazon.jdbc.ConnectionProviderManager;
import software.amazon.jdbc.HikariPooledConnectionProvider;
import software.amazon.jdbc.PropertyDefinition;
import software.amazon.jdbc.ds.AwsWrapperDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class ExampleIssue512 {

  private static final String USER = "pgadmin";
  private static final String PASSWORD = "my_password_2020";
  private static final String DATABASE_NAME = "postgres";
  private static final String RW_ENDPOINT = "atlas-postgres.cluster-czygpppufgy4.us-east-2.rds.amazonaws.com";
  private static final String RO_ENDPOINT = "atlas-postgres.cluster-ro-czygpppufgy4.us-east-2.rds.amazonaws.com";

  private static final String PGSQL_LISTDB = "SELECT datname FROM pg_database;";
  private static final String PGSQL_SELECT1 = "SELECT 1;";
  private static final String PGSQL_VERSION = "SELECT version();";

  public static void main(String[] args) throws SQLException, InterruptedException {
    HikariDataSource ro_datasource = new HikariDataSource();
    ro_datasource.setUsername(USER);
    ro_datasource.setPassword(PASSWORD);
    ro_datasource.setDataSourceClassName(AwsWrapperDataSource.class.getName());

    // Configure AwsWrapperDataSource:
    ro_datasource.addDataSourceProperty("jdbcProtocol", "jdbc:aws-wrapper:postgresql:");
    ro_datasource.addDataSourceProperty("database", DATABASE_NAME);
    ro_datasource.addDataSourceProperty("serverPort", "5432");
    ro_datasource.addDataSourceProperty("serverName", RO_ENDPOINT);

    ro_datasource.addDataSourceProperty("targetDataSourceClassName", "org.postgresql.ds.PGSimpleDataSource");

    // Configuring PGSimpleDataSource (optional):
    Properties ro_targetDataSourceProps = new Properties();
    ro_targetDataSourceProps.setProperty(PropertyDefinition.PLUGINS.name, "failover,efm,auroraConnectionTracker,driverMetaData");
    ro_datasource.addDataSourceProperty("targetDataSourceProperties", ro_targetDataSourceProps);

    HikariDataSource rw_datasource = new HikariDataSource();
    rw_datasource.setUsername(USER);
    rw_datasource.setPassword(PASSWORD);
    rw_datasource.setDataSourceClassName(AwsWrapperDataSource.class.getName());

    // Configure AwsWrapperDataSource:
    rw_datasource.addDataSourceProperty("jdbcProtocol", "jdbc:aws-wrapper:postgresql:");
    rw_datasource.addDataSourceProperty("database", DATABASE_NAME);
    rw_datasource.addDataSourceProperty("serverPort", "5432");
    rw_datasource.addDataSourceProperty("serverName", RW_ENDPOINT);

    rw_datasource.addDataSourceProperty("targetDataSourceClassName", "org.postgresql.ds.PGSimpleDataSource");

    // Configuring PGSimpleDataSource (optional):
    Properties rw_targetDataSourceProps = new Properties();
    rw_targetDataSourceProps.setProperty(PropertyDefinition.PLUGINS.name, "failover,efm,auroraConnectionTracker,driverMetaData");
    rw_datasource.addDataSourceProperty("targetDataSourceProperties", rw_targetDataSourceProps);

    final Connection ro_conn = ro_datasource.getConnection();
    final Connection rw_conn = rw_datasource.getConnection();

    for (int i=0; i<60; i++) {
      Statement ro_statement = ro_conn.createStatement();
      Statement rw_statement = rw_conn.createStatement();

      ResultSet ro_rs = ro_statement.executeQuery(PGSQL_VERSION);
      while (ro_rs.next()) {
        System.out.println(ro_rs.getString(1));
      }
      ro_rs.close();
      ro_statement.close();

      Thread.sleep(30000);

      ResultSet rw_rs = rw_statement.executeQuery(PGSQL_SELECT1);
      while (rw_rs.next()) {
        System.out.println(rw_rs.getString(1));
      }
      rw_rs.close();
      rw_statement.close();

      Thread.sleep(30000);

    }
  }

}

