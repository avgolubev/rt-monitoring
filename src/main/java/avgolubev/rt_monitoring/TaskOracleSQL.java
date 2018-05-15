/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package avgolubev.rt_monitoring;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/**
 *
 * @author golubev-av
 */
public class TaskOracleSQL extends Task {

  private DataSource dsSource;
//-------------------------------------------------------------------------------------------------------

  public TaskOracleSQL(int taskId, int paramId, int timeOut, String query,
    String sourcePoolContext, String ip, String port, String login, String password,
    int shedulerId, String condition, int threshold, int repetitions, boolean useResult) {
    super(taskId, paramId, timeOut, query, sourcePoolContext, ip, port, login, password,
      shedulerId, condition, threshold, repetitions, useResult);

    try {
      Context envContext = (Context) (new InitialContext()).lookup("java:comp/env");
      dsSource = (DataSource) envContext.lookup(this.sourcePoolContext);
    } catch (NamingException e) {
      Logger.getLogger(Task.class.getName()).logp(Level.SEVERE,
        "TaskSQL", "run", "Get monitor DataSource error.", e);
    }
  }
//-------------------------------------------------------------------------------------------------------

  @Override
  String execQuery(String queryParam) throws Exception {
    String parameterValue = null;
    String resultQuery = queryParam == null ? query : query.replaceFirst(INPUT_PARAM_MASK, queryParam);
    if (Pattern.matches(".*into[ \\f\\n\\r\\t\\v]{1,}?{1}.*", resultQuery)) {
      try (Connection connection = dsSource.getConnection();
        CallableStatement call_statement = connection.prepareCall(resultQuery)) {
        call_statement.registerOutParameter(1, java.sql.Types.VARCHAR);
        if (Thread.interrupted())
          throw new InterruptedException("Immediate task stop.");
        call_statement.executeUpdate();
        parameterValue = call_statement.getString(1);
      }
    } else// не возвращает результат
    {
      try (Connection connection = dsSource.getConnection();
        Statement executeStatement = connection.createStatement()) {
        if (Thread.interrupted())
          throw new InterruptedException("Immediate task stop.");
        executeStatement.execute(resultQuery);
      }
    }

    return parameterValue;
  }

}
