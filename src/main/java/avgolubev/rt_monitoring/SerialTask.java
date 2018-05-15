/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package avgolubev.rt_monitoring;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author avgolubev
 */
public class SerialTask implements Runnable {

  public static final String NOT_AVAILABLE = "-1"; //параметр недоступен
  public final int paramId;
  private final int timeOut;
  //-------------------------------------------
  private volatile boolean taskExecuting = false;//выполняется в настоящее время задача или нет
  private volatile boolean taskEnabled = true;//разрешён запуск задачи
  private final AtomicLong runCount = new AtomicLong(0);
  private volatile long taskExecutionTime = 0;//мсек.
  private volatile long lastExecuteTime = 0;//время, когда закончилось выполнение задачи
  private final Task[] monitorTask;

//------------------------------------------------------------------------------
  public SerialTask(Task[] monitorTask) {
    this.monitorTask = monitorTask;
    this.paramId = monitorTask[0].paramId;
    this.timeOut = monitorTask[monitorTask.length - 1].period;
  }
//------------------------------------------------------------------------------

  @Override
  public void run() {
    Thread.currentThread().setName("paramId_" + paramId);
    String parameterValue[] = {null, "NOT_AVAILABLE"};
    long startTime = System.currentTimeMillis();
    boolean stop = false;
    for (int i = 0; i < monitorTask.length && !stop; i++) {
      parameterValue = monitorTask[i].serialRun(parameterValue[0]);
      switch (parameterValue[1]) {
        case "NOT_AVAILABLE":
          //monitorTask[i].setParameterValue(paramId, NOT_AVAILABLE);
          Logger.getLogger(SerialTask.class.getName()).log(Level.INFO,
            "paramId: {0}; taskId: {1};  value: {2}", new Object[]{paramId, monitorTask[i].taskId, parameterValue[0]});
          stop = true;
          break;

        case "INTERRUPTED":
          stop = true;
          break;

        default:
          if ((i + 1) == monitorTask.length) //monitorTask[i].setParameterValue(paramId, parameterValue[0]);
          {
            Logger.getLogger(SerialTask.class.getName()).log(Level.INFO,
              "paramId: {0}; taskId: {1};  value: {2}", new Object[]{paramId, monitorTask[i].taskId, parameterValue[0]});
          }

      }
    }
    lastExecuteTime = System.currentTimeMillis();
    if (!stop) {//
      setExecuteTime(lastExecuteTime - startTime);
      incrementCountExecute();
    }
    setExecuteStatus(false);

  }
//-----------------------------------------------------------------------------------------------------------

  public boolean isExecuting() {
    return taskExecuting;
  }

  public void setExecuteStatus(boolean status) {
    taskExecuting = status;
  }
//------------------------------------------------------------------------------

  public long getCountExecute() {
    return runCount.get();
  }

  public void incrementCountExecute() {
    runCount.incrementAndGet();
  }
//------------------------------------------------------------------------------

  public long getExecuteDuration() {
    return taskExecutionTime;
  }

  private void setExecuteTime(long time) {
    taskExecutionTime = time;
  }

  //------------------------------------------------------------------------------
  public boolean isReadyToStart() {
    return ((System.currentTimeMillis() - lastExecuteTime) > timeOut * 60 * 1000)
      && !taskExecuting
      && taskEnabled;
  }
//-----------------------------------------------------------------------------------

  public void setEnabled() {
    taskEnabled = true;
  }

  public void setDisabled() {
    taskEnabled = false;
  }

  public boolean isEnabled() {
    return taskEnabled;
  }
  //------------------------------------------------------------------------------
}
