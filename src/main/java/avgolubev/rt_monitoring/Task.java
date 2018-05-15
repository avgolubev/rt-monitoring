/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package avgolubev.rt_monitoring;

import avgolubev.rt_monitoring.dbModel.DBUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 *
 * @author golubev-av
 */
public abstract class Task implements Runnable {

  public static final String NOT_AVAILABLE = "-1" ; //параметр недоступен
  public static final String INPUT_PARAM_MASK = "#r#" ; //заменяемая в запросе строка для группы последовательных задач
  public final int taskId;
  public final int paramId;
  protected final int period;
  public final String query;
  public final String queryAjax;
  public final String sourcePoolContext;
  public final String ip;
  public final String port;
  public final String login;
  public final String password;

  public final int sheduleId;
  public final String condition;
  public final int threshold;
  public final int repetitions;
  public final boolean useResult;
  //-------------------------------------------
  private volatile boolean enableStart = true;// остановка/запуск задачи
  private volatile boolean executing = false;//выполняется в настоящее время задача или нет
  private AtomicLong runCount = new AtomicLong(0);
  private volatile long taskExecutionTime = 0;//мсек.
  private volatile long lastExecuteTime = 0;//время, когда закончилось выполнение задачи

  private final SimpleDateFormat SDF = new SimpleDateFormat("dd.MM.yy HH:mm");


//------------------------------------------------------------------------------
  public Task(int taskId, int paramId, int period, String query,
          String sourcePoolContext, String ip, String port, String login,
          String password, int sheduleId, String condition, int threshold,
          int repetitions, boolean useResult) {
    this.taskId = taskId;
    this.paramId = paramId;
    this.period = period;
    this.queryAjax = query;
    this.query = query;
    this.sourcePoolContext = sourcePoolContext;
    this.ip = ip;
    this.port = port;
    this.login = login;
    this.password = password;
    //для задач sheduler
    this.sheduleId=sheduleId;
    this.condition = condition.equals("") ? null : condition;
    this.threshold=threshold;
    this.repetitions=repetitions;
    this.useResult=useResult;

  }
//------------------------------------------------------------------------------
    @Override
  public void run() {
    Thread.currentThread().setName("taskId_"+taskId);

    if(condition != null) {
        boolean run_sheduler = false;
        CurrentParameter cur_val = Manager.currentParamValues.get(Integer.toString(paramId));

      if (cur_val == null) {//ещё не получено значение параметра
        setExecuteStatus(false);
        return;
      }

      if (cur_val.available == 0) {//параметр недоступен
        setExecuteStatus(false);
        return;
      }

      switch(condition){
          case "=":
              if(cur_val.value == threshold)
                  run_sheduler = true;
              break;

          case "!=":
              if(cur_val.value != threshold)
                  run_sheduler = true;
              break;

          case ">":
              if(cur_val.value > threshold)
                  run_sheduler = true;
              break;

          case "<":
              if(cur_val.value < threshold)
                  run_sheduler = true;
              break;

          default:
      }

      if(!run_sheduler) {//не выполняется условие запуска
          zeroExecuteCount();
          setExecuteStatus(false);
          return;
      }

      if(run_sheduler && getCountExecute() >= repetitions) {//достигнуто максимальное число запусков
          setExecuteStatus(false);
          return;
      }
    }


    String parameterValue = NOT_AVAILABLE;
    boolean interrupted = false;
    long startTime = System.currentTimeMillis();

    try {
        parameterValue = execQuery(null);
        if(parameterValue == null) parameterValue = "";
    } catch (InterruptedException e) {
        interrupted = true;
        Logger.getLogger(Task.class.getName())
          .log(Level.INFO, "Task; run; immediate task stop: {0}", taskId);
    } catch (Exception e) {
        parameterValue = NOT_AVAILABLE;
        if(condition == null)
            DBUtil.logErrors(paramId, "run", "Parameter is not available.", e);
        else
            DBUtil.logErrors(paramId, "run", "Unable to execute the task scheduler.", e);
    }finally{
        lastExecuteTime = System.currentTimeMillis();
    }

    if (!interrupted) {

      if(condition == null) //контроль параметров
          saveParamVal(paramId, parameterValue);
          //Logger.getLogger(MonitorTask.class.getName()).log(Level.INFO,
          //    "paramId: {0}; value: {1}", new Object[]{paramId, parameterValue});
      else//sheduler
          if(useResult)// сохранить результат выполнения задачи sheduler
              DBUtil.saveShedulerVal(sheduleId, parameterValue);
              //Logger.getLogger(MonitorTask.class.getName()).log(Level.INFO,
              //"shedulerId: {0}; value: {1}", new Object[]{shedulerId, parameterValue});

      incrementCountExecute();
    }

    if(!parameterValue.equals(NOT_AVAILABLE))
        setExecuteTime(lastExecuteTime - startTime);

    setExecuteStatus(false);
  }
//-----------------------------------------------------------------------------------------------------------
    //-----вызов задачи из SerialTask ---------------
  public String[] serialRun(String queryParam) {
    String parameterValue[] ={NOT_AVAILABLE,"NOT_AVAILABLE"};
    long startTime = System.currentTimeMillis();

    try {
        parameterValue[0] = execQuery(queryParam);
        parameterValue[1] = "OK";
    } catch (InterruptedException e) {
        parameterValue[1] = "INTERRUPTED";
        Logger.getLogger(Task.class.getName())
          .log(Level.INFO, "MonitorTask; serialRun; immediate stop task : {0}", paramId);
    } catch (Exception e) {
        parameterValue[0] = NOT_AVAILABLE;
        parameterValue[1] = "NOT_AVAILABLE";
        DBUtil.logErrors(paramId, "serialRun", "Parameter is not available.", e);
    }finally{
        lastExecuteTime = System.currentTimeMillis();
    }

    if (!parameterValue[1].equals("INTERRUPTED")) {
        incrementCountExecute();
    }
    if(!parameterValue[1].equals("NOT_AVAILABLE"))
        setExecuteTime(lastExecuteTime - startTime);
    setExecuteStatus(false);

    return parameterValue;
  }

//-----------------------------------------------------------------------------------------------------------
    public int isExecuting(){
      return (executing ? 1 : 0);
    }
    public void setExecuteStatus(boolean status){
        executing = status;
    }
//------------------------------------------------------------------------------
    public long getCountExecute(){
      return runCount.get();
    }
    public void incrementCountExecute(){
      runCount.incrementAndGet();
    }
    public void  zeroExecuteCount(){
      runCount.set(0);
    }
//------------------------------------------------------------------------------
    public long getExecuteDuration(){
      return taskExecutionTime;
    }
    private void setExecuteTime(long time){
      taskExecutionTime = time;
    }
//------------------------------------------------------------------------------
  public String getLastExecuteTime() {
    return lastExecuteTime == 0 ? "Not yet." : SDF.format(new Date(lastExecuteTime));
  }
//------------------------------------------------------------------------------
  public boolean isReadyToStart() {
    return ((System.currentTimeMillis() - lastExecuteTime) > period * 60 * 1000)
      && !executing
      && enableStart;
  }

  public void setEnabled(){
      enableStart = true;
  }
  public void setDisabled(){
      enableStart = false;
  }
  public int getStartStatus(){
      return (enableStart ? 1 : 0);
  }
//-----------------------------------------------------------------------------------------------------------
  private CurrentParameter makeCurrentParameter(int paramId, String value) {

    int val_int;
    try {
      val_int = Integer.parseInt(value);
    } catch (NumberFormatException e) {
      DBUtil.logErrors(paramId, "makeCurrentParameter", "The value of the parameter is not int: " + value, e);
      return null;
    }

    CurrentParameter cur_val = Manager.currentParamValues.get(Integer.toString(paramId));

    if (cur_val == null) {
      return value.equals(NOT_AVAILABLE)
        ? null
        : new CurrentParameter(paramId, val_int, 0, new Date(), null, 1);
    }

    return value.equals(NOT_AVAILABLE)
      ? new CurrentParameter(paramId, cur_val.value, cur_val.delta, cur_val.value_date, cur_val.alarm_date, 0)
      : new CurrentParameter(paramId, val_int, val_int - cur_val.value, new Date(), null, 1);
  }
//-----------------------------------------------------------------------------------------------------------
  private void saveParamVal(int paramId, String value) {

    Manager.currentParamValues.put(Integer.toString(paramId), makeCurrentParameter(paramId, value));

    try {
      DBUtil.saveParamVal(paramId, Integer.parseInt(value));
    } catch (Exception e) {
      DBUtil.logErrors(paramId, "setParameterValue", "Error saving parameter value.", e);
    }
  }
//----------------------------------------------------------------------------------------------------------
  private void saveShedulerVal(int shedulerId, String value, DataSource dataSource) {

    try (Connection dbConnection = dataSource.getConnection();
      PreparedStatement statement = dbConnection.prepareStatement(
        "insert into scheduler_log (schedule_id, note, log_date) values (?, ?, ?)")) {
      statement.setInt(1, shedulerId);
      statement.setString(2, value);
      statement.setDate(3, new java.sql.Date(System.currentTimeMillis()));
      statement.executeUpdate();
    } catch (SQLException e) {
      DBUtil.logErrors(paramId, "setShedulerValue", "Error saving task response.", e);
    }
  }
//-----------------------------------------------------------------------------------------
    abstract String execQuery(String queryParam) throws Exception;
    //throw new IllegalArgumentException("timeout value is negative");
}
