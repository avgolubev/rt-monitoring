package avgolubev.rt_monitoring;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;

public class SMSTask implements Runnable {

  private final static long PARAMETERS_CHECKING_PERIOD = 60000;
  private final static long DATABASE_RECONNECTION_PERIOD = 60000;
  private static final SimpleDateFormat DATE_TIME_FORMAT = new SimpleDateFormat("dd.MM.yy HH:mm");
  //список телефоннов рассылки, сlass==0 - не рассылать смс
  private static final String PARAMETERS_VALUES_QUERY
    = "select pc.param_id, p.param_name, pc.param_value, p.param_count, pc.value_date, p.direction "
    + "from param_current pc, param p "
    + "where p.enabled=1 and pc.param_id=p.param_id and "
    + "p.param_id in (select distinct param_id from sms_class where class in "
    + "(select distinct class from sms_phone where class>-1)) order by pc.param_id";
  private static final String SMS_MAILING_LIST
    = "select sp.phone, sp.class from sms_phone sp where sp.class>-1 order by sp.class";
  private static final String CLASSES_PARAMETERS_LIST
    = "select sc.class, sc.param_id from sms_class sc "
    + "where sc.class in (select distinct class from sms_phone where class>-1) and "
    + "sc.param_id in (select param_id from param where enabled=1) order by sc.class";
  private static final String PARAMETERS_LIST
    = "select param_id from param where enabled=1 and param_id in "
    + "(select distinct param_id from sms_class where class in "
    + "(select distinct class from sms_phone where class>-1)) order by param_id";

  private DataSource ds_monitor;//расположение базы мониторинга
  //(может совпадать с расположение БД контролируемых параметров)
  private Statement statement;
  private Connection connection;
  private ResultSet resultSet;
  private boolean runningThread = true;
  private Map<String, SMSTask.ParameterArguments> parameterFlags;
  private static SMSTask.ClassParametersPhones[] classParametesPhones;

//---------------------------------------------------------------------------------------------------------------------
  public SMSTask(String name) {
    try {
      Context initContext = new InitialContext();
      Context envContext = (Context) initContext.lookup("java:comp/env");
      ds_monitor = (DataSource) envContext.lookup("jdbc/monitor_sms");
    } catch (Exception e) {
      Logger.getLogger(SMSTask.class.getName()).logp(Level.SEVERE,
        "SMSMonitorThread", "SMSMonitorThread", "Error getting DataSource.", e);
    }
  }
//------------------------------------------------------------------------------

  private void connectDB() {
    if (connection != null && statement != null) {
      return;
    }
    boolean connectionError = true;
    while (connectionError && getRunStatus()) {
      connectionError = false;
      try {
        connection = ds_monitor.getConnection();
        statement = connection.createStatement();
      } catch (SQLException e) {
        connectionError = true;
        Logger.getLogger(SMSTask.class.getName()).logp(Level.SEVERE,
          "SMSMonitorThread", "connectDB", "DB connection error.", e);
        try {
          Thread.sleep(DATABASE_RECONNECTION_PERIOD);
        } catch (InterruptedException e1) {
          Logger.getLogger(SMSTask.class.getName()).logp(Level.SEVERE,
            "SMSMonitorThread", "connectDB", "Error Thread.sleep.", e1);
        }
      }
    }
  }
//------------------------------------------------------------------------------

  private void disconnectDB() {
    try {
      if (statement != null) {
        statement.close();
        statement = null;
      }
      if (connection != null) {
        connection.close();
        connection = null;
      }
    } catch (SQLException e) {
      Logger.getLogger(SMSTask.class.getName()).logp(Level.SEVERE,
        "SMSMonitorThread", "disconnectDB", "Error DB disconnecting.", e);
    }
  }
//------------------------------------------------------------------------------

  public synchronized void stopThread() {
    runningThread = false;
  }
//------------------------------------------------------------------------------

  public synchronized void runThread() {
    runningThread = true;
  }
//------------------------------------------------------------------------------

  public synchronized boolean getRunStatus() {
    return runningThread;
  }
//------------------------------------------------------------------------------

  private void parametersInit() {

    SMSTask.ClassParametersPhones temp_cp = null;
    List<String> phonesList = new ArrayList<>();
    List<SMSTask.ClassParametersPhones> listOfClassParametersPhones = new ArrayList<>();
    Properties indexСlassLinks = new Properties();

    classParametesPhones = null;//освобождаем старый список, если он есть

    try {
      parameterFlags = new HashMap<>();
      resultSet = statement.executeQuery(PARAMETERS_LIST);
      while (resultSet.next()) {
        parameterFlags.put(resultSet.getString("param_id"), new SMSTask.ParameterArguments(0, 0)); //-----инициализация флага параметров
      }
      resultSet.close();

      //инициализация по классам и номерам телефонов
      int i = 0;
      resultSet = statement.executeQuery(SMS_MAILING_LIST);
      while (resultSet.next()) {
        if (temp_cp == null) {
          temp_cp = new SMSTask.ClassParametersPhones(resultSet.getInt("class"));
        }

        if (temp_cp.classId != resultSet.getInt("class")) {//новый класс
          temp_cp.phones = phonesList.toArray(new String[phonesList.size()]);
          listOfClassParametersPhones.add(temp_cp);
          indexСlassLinks.setProperty(Integer.toString(temp_cp.classId), Integer.toString(i));
          temp_cp = new SMSTask.ClassParametersPhones(resultSet.getInt("class"));
          phonesList.clear();
          i++;
        }
        phonesList.add((resultSet.getString("phone") == null ? "" : resultSet.getString("phone")));
      }
      resultSet.close();

      if (temp_cp != null) {//для последнего класса в цикле
        temp_cp.phones = phonesList.toArray(new String[phonesList.size()]);
        listOfClassParametersPhones.add(temp_cp);
        indexСlassLinks.setProperty(Integer.toString(temp_cp.classId), Integer.toString(i));
        classParametesPhones = listOfClassParametersPhones.toArray(new SMSTask.ClassParametersPhones[listOfClassParametersPhones.size()]);

        //--инициализация по номерам параметров
        resultSet = statement.executeQuery(CLASSES_PARAMETERS_LIST);
        while (resultSet.next()) {
          classParametesPhones[Integer.parseInt(indexСlassLinks.getProperty(resultSet.getString("class")))].parameterIds.add(resultSet.getString("param_id"));
        }
        resultSet.close();
      }

    } catch (SQLException e) {
      Logger.getLogger(SMSTask.class.getName()).logp(Level.SEVERE,
        "SMSMonitorThread", "parametersInit", "Error init mailing parameters.", e);
    }
  }
//------------------------------------------------------------------------------

  private void parametersControl() {
    int currentThresholdExceeded;
    int param_threshold, param_value, param_condition, previousThresholdExceeded;
    String param_id = "-1", param_name, param_value_date;
    String msg, direction;
    List<String[]> messagesList = new ArrayList<>();

    try {
      resultSet = statement.executeQuery(PARAMETERS_VALUES_QUERY);

      while (resultSet.next()) {//цикл по параметрам-----------------------------------------------
        param_threshold = resultSet.getInt("param_count");
        param_value = resultSet.getInt("param_value");
        param_id = resultSet.getString("param_id");
        param_name = resultSet.getString("param_name");
        param_value_date = DATE_TIME_FORMAT.format(resultSet.getTimestamp("value_date"));
        param_condition = resultSet.getInt("direction");

        if (parameterFlags.containsKey(param_id)) {
          previousThresholdExceeded = parameterFlags.get(param_id).exceededThreshold;
        } else {
          previousThresholdExceeded = 0;
          parameterFlags.put(param_id, new SMSTask.ParameterArguments(0, 0));
        }

        switch (param_condition) {//***********************************************************
          case -1:  //меньше порога
            currentThresholdExceeded = thresholdExceede(param_condition, param_value, param_threshold);
            if (param_threshold == 0) {  //порог равен нулю, неверно заданы параметры
              Logger.getLogger(SMSTask.class.getName()).logp(Level.WARNING,
                "SMSMonitorThread", "parametersControl", "Threshold is zero - parameters are set incorrectly. param_id: " + param_id, (Throwable) null);
              break;
            }

            //если превышение порога более 3 раз и не было СМС по с 1 по 3 (parameterFlags.get(param_id)==0),
            //то выставляем текущий порог на 3 для отправки СМС
            if (currentThresholdExceeded > 3 && previousThresholdExceeded == 0) {
              currentThresholdExceeded = 3;
            }

            // после 3 порога не отсылаем смс и если состояние не изменилось с прошлой отправки
            if (currentThresholdExceeded != previousThresholdExceeded && currentThresholdExceeded <= 3) {
              if (currentThresholdExceeded < previousThresholdExceeded) {   //уменьшение аварийности
                direction = "+";
              } else {//увеличение аварийности
                direction = "-";
              }
              if (!isVibration(previousThresholdExceeded, param_threshold, param_value, parameterFlags.get(param_id).paramVal)) {
                previousThresholdExceeded = currentThresholdExceeded;
                msg = param_name + ": " + direction + param_value + "/" + param_threshold + "; " + param_value_date;
                messagesList.add(new String[]{param_id, msg});
              }
            }
            break;

          case 0:  //равно порогу
            //авария, но сообщение не отправлено
            if (param_threshold == param_value && previousThresholdExceeded == 0) {
              previousThresholdExceeded++;
              msg = param_name + ": авария; " + param_value + "/" + param_threshold + "; " + param_value_date;
              messagesList.add(new String[]{param_id, msg});
              break;
            }
            //авария устранена, но сообщение не отправлено
            if (param_threshold != param_value && previousThresholdExceeded == 1) {
              previousThresholdExceeded--;
              msg = param_name + ": норма; " + param_value + "/" + param_threshold + "; " + param_value_date;
              messagesList.add(new String[]{param_id, msg});
            }
            break;

          case 2:  //не равно порогу
            //авария, но сообщение не отправлено
            if (param_threshold != param_value && previousThresholdExceeded == 0) {
              previousThresholdExceeded++;
              msg = param_name + ": alarm; " + param_value + "/" + param_threshold + "; " + param_value_date;
              messagesList.add(new String[]{param_id, msg});
              break;
            }
            //авария устранена, но сообщение не отправлено
            if (param_threshold == param_value && previousThresholdExceeded == 1) {
              previousThresholdExceeded--;
              msg = param_name + ": norm; " + param_value + "/" + param_threshold + "; " + param_value_date;
              messagesList.add(new String[]{param_id, msg});
            }
            break;

          case 1:  //больше порога----------------------------------------
            currentThresholdExceeded = thresholdExceede(param_condition, param_value, param_threshold);
            //если превышение порога более 3 раз и не было СМС по с 1 по 3 пороги (parameterFlags.get(param_id)==0),
            //то выставляем порог на три для отправки СМС
            if (currentThresholdExceeded > 3 && previousThresholdExceeded == 0) {
              currentThresholdExceeded = 3;
            }

            //после 3 порога не отсылаем смс
            if (currentThresholdExceeded != previousThresholdExceeded && currentThresholdExceeded <= 3) {
              if (currentThresholdExceeded < previousThresholdExceeded) {   //уменьшение аварийности
                direction = "-";
              } else { //увеличение аварийности
                direction = "+";
              }
              if (!isVibration(previousThresholdExceeded, param_threshold, param_value, parameterFlags.get(param_id).paramVal)) {
                previousThresholdExceeded = currentThresholdExceeded;
                msg = param_name + ": " + direction + param_value + "/" + param_threshold + "; " + param_value_date;
                messagesList.add(new String[]{param_id, msg});
              }
            }
            break;//------------------------------------------------------

        }//switch**********************************************************************
        parameterFlags.get(param_id).exceededThreshold = previousThresholdExceeded; //обновляем значение

      }//----------------цикл по параметрам
      resultSet.close();

      //отправка всех смс
      messagesList.forEach((message) -> {
        sendSMS(message[0], message[1]);
      });

    } catch (SQLException e) {
      Logger.getLogger(SMSTask.class.getName()).logp(Level.SEVERE,
        "SMSMonitorThread", "parametersControl", "Parameters control error. param_id: " + param_id, e);
    }
  }
//----------------------------------------------------------------------------------------------------------------

  private int thresholdExceede(int direction, int param_value, int param_count) {
    int exceeded;
    switch (direction) {
      case 1:
        if (param_count > 0)  //порог больше нуля
          exceeded = param_value > 0 ? (param_value - 1) / param_count : 0;
        else //порог равен нулю
          exceeded = param_value;
        break;

      case -1:
        exceeded = param_value > 0 ? (param_count - 1) / param_value : param_count;
        break;

      default:
        exceeded = 0;

    }
    return exceeded;
  }
//----------------------------------------------------------------------------------------------------------------

  private boolean isVibration(int sentSmsNumber, int threshold, int currentVal, int previousVal) {
    if (sentSmsNumber == 0) return false;

    if (threshold == 0 && currentVal == 0) return false;

    int delta = Math.abs(currentVal - previousVal);
    if (threshold == 0)
      threshold = 1;

    return 100 * delta / threshold < 10;
  }
//----------------------------------------------------------------------------------------------------------------

  private void sendSMS(String param_id, String msg) {
    if (classParametesPhones == null) return;

    for (SMSTask.ClassParametersPhones currentClass : classParametesPhones) {
      if (currentClass.parameterIds.contains(param_id)) {
        for (String phone : currentClass.phones) {

          try {
            statement.executeUpdate("begin send_bts_sms('".
              concat(phone).
              concat("', '").
              concat(msg).
              concat("', 000445, sysdate); end;"));
          } catch (SQLException e) {
            Logger.getLogger(SMSTask.class.getName()).logp(Level.SEVERE,
              "SMSMonitorThread", "sendSMS",
              "Error insertion message into table mailing. param_id: " + param_id + "; msg: " + msg, e);
          }

        }
      }
    }

  }

  //------------------------------------------------------------------------------
  @Override
  public void run() {
    runThread();
    connectDB();
    parametersInit();

    while (runningThread) {
      connectDB();
      parametersControl();
      disconnectDB();
      try {
        Thread.sleep(PARAMETERS_CHECKING_PERIOD);
      } catch (InterruptedException e) {
        Logger.getLogger(SMSTask.class.getName()).logp(Level.SEVERE,
          "SMSMonitorThread", "run", "Error sleep.", e);
      }
    }
    disconnectDB();
  }
//----------------------------------------------------------------------------------------------------------------------------

  private class ClassParametersPhones {

    private int classId = 0;
    private List<String> parameterIds;
    private String[] phones;

    private ClassParametersPhones(int classId) {
      this.classId = classId;
      parameterIds = new ArrayList<>();
    }
  }
//------------------------------------------------------------------------------------------------------------------------

  private class ParameterArguments {

    private int exceededThreshold;
    private int paramVal;

    private ParameterArguments(int exceededThreshold, int paramVal) {
      this.exceededThreshold = exceededThreshold;
      this.paramVal = paramVal;
    }
  }
//----------------------------------------------------------------------------------------------------------------------------

//----------------------------------------------------------------------------------------------------------------------------
}
