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

package software.amazon.jdbc.util;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ConnectionMethodAnalyzer {
  public boolean doesOpenTransaction(Connection currentConn, String methodName, Object[] args) {
    if (!(methodName.contains("execute") && args != null && args.length >= 1)) {
      return false;
    }

    final String statement = getFirstSqlStatement(String.valueOf(args[0]));
    if (isStatementStartingTransaction(statement)) {
      return true;
    }

    boolean autocommit;
    try {
      autocommit = currentConn.getAutoCommit();
    } catch (SQLException e) {
      return false;
    }

    return !autocommit && isStatementDml(statement);
  }

  private String getFirstSqlStatement(String sql) {
    String statement = parseMultiStatementQueries(sql).get(0);
    statement = statement.toUpperCase();
    statement = statement.replaceAll("\\s*/\\*(.*?)\\*/\\s*", " ").trim();
    return statement;
  }

  private List<String> parseMultiStatementQueries(String query) {
    if (query == null || query.isEmpty()) {
      return new ArrayList<>();
    }

    query = query.replaceAll("\\s+", " ");

    // Check to see if string only has blank spaces.
    if (query.trim().isEmpty()) {
      return new ArrayList<>();
    }

    return Arrays.stream(query.split(";")).collect(Collectors.toList());
  }

  public boolean doesCloseTransaction(String methodName, Object[] args) {
    if (methodName.equals("Connection.commit") || methodName.equals("Connection.rollback")) {
      return true;
    }

    if (!(methodName.contains("execute") && args != null && args.length >= 1)) {
      return false;
    }

    String statement = getFirstSqlStatement(String.valueOf(args[0]));
    return isStatementClosingTransaction(statement);
  }

  public boolean isExecuteDml(String methodName, Object[] args) {
    if (!(methodName.contains("execute") && args != null && args.length >= 1)) {
      return false;
    }

    String statement = getFirstSqlStatement(String.valueOf(args[0]));
    return isStatementDml(statement);
  }

  public boolean isStatementDml(String statement) {
       return !isStatementStartingTransaction(statement)
        && !isStatementClosingTransaction(statement)
        && !isStatementSettingState(statement);
  }

  public boolean isStatementSettingState(String statement) {
    return statement.startsWith("SET ");
  }

  public boolean isStatementStartingTransaction(String statement) {
    return statement.startsWith("BEGIN") || statement.startsWith("START TRANSACTION");
  }

  public boolean isStatementClosingTransaction(String statement) {
    return statement.startsWith("COMMIT")
        || statement.startsWith("ROLLBACK")
        || statement.startsWith("END")
        || statement.startsWith("ABORT");
  }

  public boolean isStatementSettingAutoCommit(String methodName, Object[] args) {
    if (!(methodName.contains("execute") && args != null && args.length >= 1)) {
      return false;
    }

    final String statement = getFirstSqlStatement(String.valueOf(args[0]));
    return statement.startsWith("SET AUTOCOMMIT");
  }

  public Boolean getAutoCommitValueFromSqlStatement(Object[] args) {
    String sql = (String) args[0];
    sql = sql.trim();
    sql = sql.toLowerCase();

    int equalsCharacterIndex = sql.indexOf("=");
    if (equalsCharacterIndex == -1) {
      return null;
    }
    sql = sql.substring(equalsCharacterIndex + 1);

    if (sql.contains(";")) {
      sql = sql.substring(0, sql.indexOf(";"));
    }

    sql = sql.trim();
    if ("false".equals(sql) || "0".equals(sql)) {
      return false;
    } else if ("true".equals(sql) || "1".equals(sql)) {
      return true;
    } else {
      return null;
    }
  }
}
