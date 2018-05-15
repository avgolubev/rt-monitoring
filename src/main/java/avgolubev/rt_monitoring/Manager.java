/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package avgolubev.rt_monitoring;

import com.typesafe.config.Config;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.naming.Context;
import javax.naming.InitialContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Constructor;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.Set;
import javax.naming.NamingException;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.typesafe.config.ConfigFactory;

/**
 *
 * @author avgolubev
 */
@WebServlet(urlPatterns="/manager", loadOnStartup=1)
public class Manager extends HttpServlet {

  static final String CONTENT_TYPE = "text/html; charset=UTF-8";
  static final String CONTENT_TYPE_XML = "text/xml; charset=UTF-8";
  static final String DELIM = "#&";// разделитель в значении параметров
  public static final int POOL_SIZE = 10;

  //SQL_API
  static String SQL_TASKS_LIST;
  static String SQL_SEQUENTIAL_TASKS_LIST;
  static String SQL_TASKS_LIST_BY_EVENTS;
  static String SQL_SCHEDULER_EVENTS_LIST;
  static String SQL_SCHEDULER_TASKS_LIST;
  static String SQL_MONITORED_PARAMETERS_LIST;
  //static final String список_задач_по_отслеживаемым_параметрам =

  public static DataSource dsMonitor;// для обращения к таблицам мониторинга, не меняется в процессе работы или руками
  private java.util.Date startTime;
  private SMSTask smsTask;
  public static ConcurrentHashMap<String, CurrentParameter> currentParamValues
    = new ConcurrentHashMap<>(100, 0.9f, POOL_SIZE);
  //список задач
  public static final ConcurrentMap<String, Task> TASK_LIST = new ConcurrentHashMap<>();
  public static final ExecutorService TASK_THREAD_POOL = Executors.newFixedThreadPool(POOL_SIZE, new TaskThreadFactory());
  public static final ConcurrentMap<String, Future> TASK_FUTURE_LIST = new ConcurrentHashMap<>();
  //----------------------группы последовательных задач----------------------------------------------------
  public static final ConcurrentMap<String, SerialTask> SERIAL_TASK_LIST = new ConcurrentHashMap<>();
  public static final ExecutorService SERIAL_TASK_THREAD_POOL = Executors.newFixedThreadPool(POOL_SIZE, new TaskThreadFactory());
  public static final ConcurrentMap<String, Future> SERIAL_TASK_FUTURE_LIST = new ConcurrentHashMap<>();
//---------------------------------------------------------------------------------------------------------------------
  private static final ScheduledExecutorService SCHEDULED_EXECUTOR = Executors.newScheduledThreadPool(2);
//--------------------------------------------------------------------------------------------------------------------
  //выполняется при  запуске Tomcat

  @Override
  public void init(ServletConfig config) throws ServletException {
    //Locale.setDefault( Locale.US ); // для oracle xe и ojdbc6

    loadConfig();

    try {
      Context initContext = new InitialContext();
      Context envContext = (Context) initContext.lookup("java:comp/env");
      dsMonitor = (DataSource) envContext.lookup("jdbc/monitor_server");
    } catch (NamingException e) {
      Logger.getLogger(Manager.class.getName()).logp(Level.SEVERE,
        "Manager", "init", "Error getting DataSource.", e);
    }
    //connectToDB();//цепляемся к базе
    reloadTaskList("all");//инициализация массива задач
    //reloadSerialTaskList("all");//инициализация массива последовательных задач
    startThreadPoolQueueManager();
    //startSMSTask();//запуск потока рассылки СМС
    startTime = new java.util.Date();
  }
//---------------------------------------------------------------------------------------------------------------------
/*
  private Optinal<Connection> connectToDB() {
    try {
      connection = dsMonitor.getConnection();
    }
    catch (SQLException e) {
      Logger.getLogger(ManageMonitorServ.class.getName()).logp(Level.SEVERE,
                  "ManageMonitorServ","connectToDB","Ошибка при подключении к базе.",e);
    }
  }
   */
//----------------------------------------------------------------------------------------------------------
  private void loadConfig() {
    Config CONF = ConfigFactory.load();
    SQL_TASKS_LIST = CONF.getString("sql.SQL_TASKS_LIST");
    SQL_SEQUENTIAL_TASKS_LIST     = CONF.getString("sql.SQL_SEQUENTIAL_TASKS_LIST");
    SQL_TASKS_LIST_BY_EVENTS      = CONF.getString("sql.SQL_TASKS_LIST_BY_EVENTS");
    SQL_SCHEDULER_EVENTS_LIST     = CONF.getString("sql.SQL_SCHEDULER_EVENTS_LIST");
    SQL_SCHEDULER_TASKS_LIST      = CONF.getString("sql.SQL_SCHEDULER_TASKS_LIST");
    SQL_MONITORED_PARAMETERS_LIST = CONF.getString("sql.SQL_MONITORED_PARAMETERS_LIST");
  }
//----------------------------------------------------------------------------------------------------------
  private String getJMT_Data(String action, String page, String param_id) {
    //return new DecodeStatus().getDecode_string(getQueryThreadStatus(taskList),1);
    return "";
  }
//---------------------------------------------------------------------------------------------------------------------

  private String getSMSMServStatus() {
    if (smsTask == null || !smsTask.getRunStatus()) return "s";

    return "r";
  }
//---------------------------------------------------------------------------------------------------------------------

  protected void processRequest(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {

    response.setContentType(CONTENT_TYPE);
    response.setHeader("Cache-Control", "no-cache");
    String jsonResult;

    String action = request.getParameter("action");
    if(action == null) action = "get_data1";

    String argument = request.getParameter("argument");
    Logger.getLogger(Task.class.getName()).log(Level.INFO,
      "action :{0}; argument: {1}", new Object[]{action, argument});

    switch (action) {

      case "task_start":
        setTaskEnabled(argument);
        jsonResult = "";
        break;

      case "task_stop":
        stopMonitorTask(argument);
        jsonResult = "";
        break;

      case "task_reload":
        stopMonitorTask(argument);
        reloadTaskList(argument);
        jsonResult = "";
        break;
      case "sms_task":
        manageSMSTask(argument);
        jsonResult = "";
        break;
      case "get_data1":
        jsonResult = getParameterTaskList();
        break;
      case "get_data2":
        jsonResult = getParametersByParamId();
        break;
      case "get_data3":
        jsonResult = getParametersByExecuting();
        break;
      case "get_shedule":
        jsonResult = getSchedulerTaskList();
        break;

      default:
        jsonResult = "";
    }

    try (PrintWriter out = response.getWriter()) {
      out.write(jsonResult);
    }
  }

// -----------------------------------------------------------------------------------------------------------------
// {"param": [[], [],... []], "task": [[], [],... []]}
// [] - id, name, condition, threshold, period
// [] - id, query, executing, execute_count, времяВыполненияЗадачи, lastExecuteTime, param_id, start_status
  private String getParameterTaskList() {

    JSONArray json_array;
    JSONArray param_array = new JSONArray();
    JSONArray task_array = new JSONArray();
    JSONObject resultJson = new JSONObject();

    Task monitorTask;

    //Logger.getLogger(MonitorTask.class.getName()).log(Level.INFO,"step1");
    try (Connection connection = dsMonitor.getConnection();
      Statement statement = connection.createStatement();
      ResultSet resultSet = statement.executeQuery(SQL_MONITORED_PARAMETERS_LIST)) {
      while (resultSet.next()) {
        json_array = new JSONArray();
        json_array.add(resultSet.getInt("id"));
        json_array.add(resultSet.getString("name"));
        json_array.add(resultSet.getString("condition"));
        json_array.add(resultSet.getInt("threshold"));
        json_array.add(resultSet.getInt("period"));

        param_array.add(json_array);
      }
    } catch (SQLException e) {
      Logger.getLogger(Manager.class.getName()).logp(Level.SEVERE,
        "MonitorManager", "getParameterTaskList",
        "Error getting the list of monitored parameters.", e);
    }

    return resultJson.toString();
  }
//------------------------------------------------------------------------------

  private String getParametersByParamId() {

    return "";
  }

  private String getParametersByExecuting() {

    return "";
  }
//-------------------------------------------------------------------------------------------------
  // {"schedule": [[], [],... []], "task": [[], [],... []]}
  // [] - id, name, param_id, condition, threshold, repetitions, period, note, enabled, use_result, task_id
  // [] - id, query, class_id, db_id, ip, port, login, pass,
  //      start_status, executing, event_count, времяВыполненияЗадачи, lastExecuteTime

  private String getSchedulerTaskList() {

    JSONArray json_array;
    JSONArray task_array = new JSONArray();
    JSONArray schedule_array = new JSONArray();
    JSONObject resultJson = new JSONObject();
    Task monitor_task;

    try (Connection connection = dsMonitor.getConnection();
      Statement statement = connection.createStatement();
      ResultSet resultSet = statement.executeQuery(SQL_SCHEDULER_EVENTS_LIST)) {
      while (resultSet.next()) {
        json_array = new JSONArray();
        json_array.add(resultSet.getInt("schedule_id"));
        json_array.add(resultSet.getString("schedule_name"));
        json_array.add(resultSet.getInt("param_id"));
        json_array.add(resultSet.getString("condition"));
        json_array.add(resultSet.getInt("threshold"));
        json_array.add(resultSet.getInt("repetitions"));
        json_array.add(resultSet.getInt("period"));
        json_array.add(resultSet.getString("note"));
        json_array.add(resultSet.getInt("enabled"));
        json_array.add(resultSet.getInt("use_result"));
        json_array.add(resultSet.getInt("task_id"));

        schedule_array.add(json_array);
      }
    } catch (SQLException e) {
      Logger.getLogger(Manager.class.getName()).logp(Level.SEVERE,
        "Manager", "getShedulerTaskList",
        "Error getting a list of scheduler events.", e);
    }

    try (Connection connection = dsMonitor.getConnection();
      Statement statement = connection.createStatement();
      ResultSet resultSet = statement.executeQuery(SQL_SCHEDULER_TASKS_LIST)) {
      while (resultSet.next()) {
        json_array = new JSONArray();
        json_array.add(resultSet.getInt(1));
        json_array.add(resultSet.getString(2));
        json_array.add(resultSet.getInt(3));
        json_array.add(resultSet.getInt(4));
        json_array.add(resultSet.getString(5));
        json_array.add(resultSet.getString(6));
        json_array.add(resultSet.getString(7));
        json_array.add(resultSet.getString(8));
        //start_status, executing, event_count, времяВыполненияЗадачи, lastExecuteTime
        monitor_task = TASK_LIST.get(resultSet.getString(1));
        if (monitor_task != null) {
          json_array.add(monitor_task.getStartStatus());
          json_array.add(monitor_task.isExecuting());
          json_array.add(monitor_task.getCountExecute());
          json_array.add(monitor_task.getExecuteDuration());
          json_array.add(monitor_task.getLastExecuteTime());
        }

        task_array.add(json_array);
      }
    } catch (SQLException e) {
      Logger.getLogger(Manager.class.getName()).logp(Level.SEVERE,
        "Manager", "getShedulerTaskList",
        "Error getting a list of scheduler events.", e);
    }

    resultJson.put("schedule", schedule_array);
    resultJson.put("task", task_array);
    return resultJson.toString();
  }

//-----------------------------------------------------------------------------------------------------------------
  private void reloadTaskList(String task_id) {
    String sql_where_conditions = "";

    switch (task_id) {

      case "all": //загрузка всех значений с нуля
        TASK_LIST.clear();
        break;

      case "new": //только новые задачи
        sql_where_conditions = " and t.task_id not in (" + mkString(TASK_LIST.keySet(), ",") + ")";
        break;

      default: // по конкретной задаче
        TASK_LIST.remove(task_id);
        sql_where_conditions = " and t.task_id=" + task_id;
    }

//--------------------------------------------------------------------
    try (Connection connection = dsMonitor.getConnection();
      Statement statement = connection.createStatement();
      ResultSet resultSet = statement.executeQuery(SQL_TASKS_LIST + sql_where_conditions
        + " union all "
        + SQL_TASKS_LIST_BY_EVENTS + sql_where_conditions
      );) {
      while (resultSet.next()) {
        try {
          TASK_LIST.put(resultSet.getString("task_id"), newMonitorTask(
            resultSet.getString("class_name"),
            resultSet.getInt("task_id"),
            resultSet.getInt("id"),
            resultSet.getInt("period"),
            resultSet.getString("query"),
            resultSet.getString("data_source"),
            resultSet.getString("ip"),
            resultSet.getString("port"),
            resultSet.getString("login"),
            resultSet.getString("pass"),
            resultSet.getInt("schedule_id"),
            resultSet.getString("condition"),
            resultSet.getInt("threshold"),
            resultSet.getInt("repetitions"),
            resultSet.getBoolean("use_result"),
            dsMonitor
          ));
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException
          | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
          Logger.getLogger(Manager.class.getName()).logp(Level.SEVERE,
            "Manager", "reloadTaskList", "A task creation error: newMonitorTask.", e);
        }
      }

    } catch (SQLException e) {
      Logger.getLogger(Manager.class.getName()).logp(Level.SEVERE,
        "Manager", "reloadTaskList", "A task creation error.", e);
    }
  }
//------------------------------------------------------------------------------------------------------------------

  private void reloadSerialTaskList(String param_id) {
    String sql_query = SQL_SEQUENTIAL_TASKS_LIST;

    switch (param_id) {
      case "all": //загрузка всех задач
        SERIAL_TASK_LIST.clear();
        break;

      case "new": //только новые
        sql_query = mkString(SERIAL_TASK_LIST.keySet(), ",");

        if (!SERIAL_TASK_LIST.isEmpty()) {
          sql_query = SQL_SEQUENTIAL_TASKS_LIST.replaceFirst("t.param_id in",
            "t.param_id not in (" + sql_query + ") and t.param_id in");
        }

        break;

      default://конкретный параметр
        SERIAL_TASK_LIST.remove(param_id);
        sql_query = SQL_SEQUENTIAL_TASKS_LIST.replaceFirst(
          "t.param_id in", "t.param_id=" + param_id + " and t.param_id in");
    }
//--------------------------------------------------------------------
    ArrayList<Task> tempList = new ArrayList<>();
    String paramId = "";
    try (Connection connection = dsMonitor.getConnection();
      Statement statement = connection.createStatement();
      ResultSet resultSet = statement.executeQuery(sql_query);) {
      while (resultSet.next()) {
        try {
          if (!paramId.equals(resultSet.getString("param_id"))) {
            if (!tempList.isEmpty()) {
              SERIAL_TASK_LIST.put(paramId, new SerialTask(tempList.toArray(new Task[tempList.size()])));
            }
            paramId = resultSet.getString("param_id");
            tempList.clear();
          }

          tempList.add(
            newMonitorTask(
              resultSet.getString("class_name"),
              resultSet.getInt("task_id"),
              resultSet.getInt("param_id"),
              resultSet.getInt("time_out"),
              resultSet.getString("query"),
              resultSet.getString("data_source"),
              resultSet.getString("ip"),
              resultSet.getString("port"),
              resultSet.getString("login"),
              resultSet.getString("pass"),
              0,
              null,
              0,
              0,
              true,
              dsMonitor
            ));

        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException
          | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
          Logger.getLogger(Manager.class.getName()).logp(Level.SEVERE,
            "Manager", "reloadTaskList", "A task creation error: newMonitorTask.", e);
        }
      }
      if (!tempList.isEmpty()) {
        SERIAL_TASK_LIST.put(paramId, new SerialTask(tempList.toArray(new Task[tempList.size()])));
      }
    } catch (SQLException e) {
      Logger.getLogger(Manager.class.getName()).logp(Level.SEVERE,
        "Manager", "reloadTaskList", "A task creation error.", e);
    }
  }
//---------------------------------------------------------------------------------------------------------------------

  private Task newMonitorTask(String className, int taskId, int paramId, int timeOut, String query,
    String sourcePoolContext, String ip, String port, String login, String password,
    int shedulerId, String condition, int threshold, int repetitions, boolean useResult, DataSource dataSource)
    throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException,
    IllegalArgumentException, InvocationTargetException {

    Class<?> taskClass = Class.forName(className);
    Constructor my_constructor = taskClass.getConstructor(int.class, int.class, int.class, String.class,
      String.class, String.class, String.class, String.class, String.class,
      int.class, String.class, int.class, int.class, boolean.class, DataSource.class);

    return (Task) my_constructor.newInstance(taskId, paramId, timeOut, query, sourcePoolContext,
      ip, port, login, password,
      shedulerId, condition, threshold, repetitions, useResult, dataSource);
  }
//---------------------------------------------------------------------------------------------------------------------

  private void setTaskEnabled(String taskId) {//---запуск задач -----------------//
    Task tempMonitorTask;
    switch(taskId) {
      //------------запуск всех задач---------
      case "all":
        for (ConcurrentMap.Entry<String, Task> entry : TASK_LIST.entrySet()) {
          tempMonitorTask = entry.getValue();
          tempMonitorTask.setEnabled();
        }
        break;

      //-------запуск конкретной задачи---------
      default:
        tempMonitorTask = TASK_LIST.get(taskId);
        tempMonitorTask.setEnabled();
    }
  }
//-------------------------------------------------------------------------------------------------------------

//-------------------------------------------------------------------------------------------------------------
  private void startThreadPoolQueueManager() {
    SCHEDULED_EXECUTOR.scheduleAtFixedRate(new ThreadPoolQueueManager(), 10, 60, TimeUnit.SECONDS);
    //SCHEDULED_EXECUTOR.scheduleAtFixedRate(new SerialThreadPoolQueueManager(), 30, 60, TimeUnit.SECONDS);
  }
//---------------------------------------------------------------------------------------------------------------------

  private void stopMonitorTask(String threadId) {
    Future taskFuture;
    //-------останов всех задач-------------
    if (threadId.equals("all")) {
      TASK_LIST.entrySet().forEach((entry) -> {
        entry.getValue().setDisabled();
      });

      for (ConcurrentMap.Entry<String, Future> entry : TASK_FUTURE_LIST.entrySet()) {
        taskFuture = entry.getValue();
        if (!taskFuture.isDone()) {
          taskFuture.cancel(true);
        }
      }
    }

    //-----останов конкретной задачи------------
    else {
      TASK_LIST.get(threadId).setDisabled();
      taskFuture = TASK_FUTURE_LIST.get(threadId);
      if (!taskFuture.isDone()) {
        taskFuture.cancel(true);
      }
    }

  }
//---------------------------------------------------------------------------------------------------------------------

  public void startSMSTask() {
    if (smsTask == null) {
      smsTask = new SMSTask("SMSThread");
    }
    Thread smsWorkThread = new Thread(smsTask, "SMSThread");
    smsWorkThread.setDaemon(true);
    smsWorkThread.start();
  }
//---------------------------------------------------------------------------------------------------------------------

  private void manageSMSTask(String argument) {

    switch (argument) {

      case "start":
        startSMSTask();
        break;

      case "stop":
        stopSMSTask();
        break;

      default:
    }

  }
//--------------------------------------------------------------------------------------------------------------

  public void stopSMSTask() {
    if (smsTask != null) {
      smsTask.stopThread();
      smsTask = null;
    }
  }
//---------------------------------------------------------------------------------------------------------------------


  @Override
  public void destroy() {
    Logger.getLogger(Task.class.getName()).log(Level.INFO, "destroy(): stopping services...");

    SCHEDULED_EXECUTOR.shutdown();
    try {
      if (!SCHEDULED_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS))
        SCHEDULED_EXECUTOR.shutdownNow();
    } catch (InterruptedException ex) {
      SCHEDULED_EXECUTOR.shutdownNow();
    }

    TASK_THREAD_POOL.shutdown();
    stopMonitorTask("all");

    try {
      if (!TASK_THREAD_POOL.awaitTermination(5, TimeUnit.SECONDS))
        TASK_THREAD_POOL.shutdownNow();
    } catch (InterruptedException ex) {
      TASK_THREAD_POOL.shutdownNow();
    }

    stopSMSTask();
    super.destroy();

    Logger.getLogger(Task.class.getName()).log(Level.INFO, "destroy(): services are stopped.");
  }
//---------------------------------------------------------------------------------------------------------------------

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
    processRequest(request, response);
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
    processRequest(request, response);
  }

  @Override
  public String getServletInfo() {
    return "Monitoring management.";
  }

  private String mkString(Set<?> set, String delim) {
    boolean first = true;
    String result = "";
    for (Object obj : set)
      if (first) {
        result = obj.toString();
        first = false;
      } else result += delim + obj.toString();
    return result;
  }

}
