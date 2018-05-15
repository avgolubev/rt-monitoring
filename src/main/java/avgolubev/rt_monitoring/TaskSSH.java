/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package avgolubev.rt_monitoring;

import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.StreamGobbler;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 *
 * @author golubev-av
 */
public class TaskSSH extends Task {

//-------------------------------------------------------------------------------------------------------
  public TaskSSH(int taskId, int paramId, int timeOut, String query,
    String sourcePoolContext, String ip, String port, String login, String password,
    int shedulerId, String condition, int threshold, int repetitions, boolean useResult) {
    super(taskId, paramId, timeOut, query, sourcePoolContext, ip, port, login, password,
      shedulerId, condition, threshold, repetitions, useResult);
  }
//-------------------------------------------------------------------------------------------------------

  @Override
  String execQuery(String queryParam) throws Exception {
    String parameterValue = "";
    ch.ethz.ssh2.Connection conn = null;
    Session session = null;
    BufferedReader br = null;
    String temp;

    try {
      conn = new ch.ethz.ssh2.Connection(ip);
      conn.connect();
      checkInterrupt();

      if (conn.authenticateWithPassword(login, password) == false) {
        throw new IOException("SSH authentication failed.");
      }
      checkInterrupt();

      session = conn.openSession();
      checkInterrupt();
      session.execCommand((queryParam == null ? query : query.replaceFirst(INPUT_PARAM_MASK, queryParam)));
      checkInterrupt();

      br = new BufferedReader(new InputStreamReader(new StreamGobbler(session.getStdout())));
      checkInterrupt();

      while ((temp = br.readLine()) != null) {
        checkInterrupt();
        parameterValue += temp;
      }

    } finally {
      if (br != null) {
        br.close();
      }

      if (session != null) {
        session.close();
      }

      if (conn != null) {
        conn.close();
      }

    }
    return parameterValue;
  }
//---------------------------------------------------------------------------------------------------------

  private void checkInterrupt() throws InterruptedException {
    if (Thread.interrupted()) {
      throw new InterruptedException("Immediate task stop.");
    }
  }

}
