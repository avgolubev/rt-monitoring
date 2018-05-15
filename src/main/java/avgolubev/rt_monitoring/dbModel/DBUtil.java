/*
 * The MIT License
 *
 * Copyright 2018 1.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package avgolubev.rt_monitoring.dbModel;

import avgolubev.rt_monitoring.Manager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.reflect.FieldMapper;
import java.sql.Timestamp;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 *
 * @author avgolubev
 */
 public class DBUtil {

  private static Timestamp sysdate() {
    return new Timestamp(System.currentTimeMillis());
   }


  public static void saveParamVal (final int p_id, int current_value) {

    // доступность параметра
    int available = current_value == -1 ? 0 : 1;

    Jdbi jdbi = Jdbi.create(Manager.dsMonitor);

    jdbi.useTransaction(h -> {

      h.registerRowMapper(FieldMapper.factory(Param.class));
      h.registerRowMapper(FieldMapper.factory(CurrentParam.class));

      Param rec_param = h.select("select * from params where id = ?", p_id)
        .mapTo(Param.class)
        .findOnly();

      Optional<CurrentParam> temp_result = h.select("select * from current_params where params_id = ?", p_id)
        .mapTo(CurrentParam.class)
        .findFirst();

      Timestamp tmp_date = null;

      // нет пока ещё значения в CURRENT_PARAMS
      if(!temp_result.isPresent()) {

        switch(rec_param.direction) {

          case 1:   //если  больше порога, то - авария
            if (current_value > rec_param.count)
              tmp_date = sysdate();
            break;

          case 0:  //если  равно порогу, то - авария
            if (current_value == rec_param.count)
              tmp_date = sysdate();
            break;

          case -1:  //если  меньше порога, то - авария
            if (current_value < rec_param.count)
              tmp_date = sysdate();
            break;

          case 2: //если не равно порогу, то - авария
            if (current_value != rec_param.count)
              tmp_date = sysdate();
            break;

            default:
              tmp_date = null;
        }

        if(available == 1) //в случае доступности параметра добавляем запись
          h.createUpdate("insert into current_params " +
                         "values (:params_id, :val, :delta, :val_date, :alarm_date, 1)")
            .bind("params_id",  p_id)
            .bind("val",        current_value)
            .bind("delta",      current_value)
            .bind("val_date",   sysdate())
            .bind("alarm_date", tmp_date)
            .execute();
           //если параметр н  доступен в первом запросе, то не будет и значения параметра -
        //повод проверить работоспособность запроса сразу после установки нового параметра
        return;
      }


      // если есть значение в CURRENT_PARAMS
      CurrentParam rec_param_current = temp_result.get();

      //в случае доступности параметра
      if(available == 1)
        //Перенос текущей записи по параметру в историческую таблицу
        h.createUpdate("insert into history_params values (:params_id, :val, :val_date)")
          .bind("params_id", rec_param_current.params_id)
          .bind("val",       rec_param_current.val)
          .bind("val_date",  rec_param_current.val_date)
          .execute();

      switch(rec_param.direction) {

      case 1: //если  больше порога, то - авария
        if (current_value > rec_param.count && rec_param_current.alarm_date == null)
          tmp_date = sysdate();

        if (current_value > rec_param.count && rec_param_current.alarm_date != null)
          tmp_date = rec_param_current.alarm_date;

        break;


      case 0:  //если  равно порогу, то - авария
        if (current_value == rec_param.count && rec_param_current.alarm_date == null)
          tmp_date = sysdate();

        if (current_value == rec_param.count && rec_param_current.alarm_date != null)
          tmp_date = rec_param_current.alarm_date;

        break;

      case -1: //если  меньше порога, то - авария
        if (current_value < rec_param.count && rec_param_current.alarm_date == null)
          tmp_date = sysdate();

        if (current_value < rec_param.count && rec_param_current.alarm_date != null)
          tmp_date = rec_param_current.alarm_date;

        break;

      case 2: //если не равно порогу, то - авария
        if (current_value != rec_param.count && rec_param_current.alarm_date == null)
          tmp_date = sysdate();

        if (current_value != rec_param.count && rec_param_current.alarm_date != null)
          tmp_date = rec_param_current.alarm_date;

      break;

        default:
          tmp_date = null;
      }

      if(available == 1)  //в случае доступности параметра
        h.createUpdate("update current_params " +
                       "set delta = :current_value - val," +
                       "val = :current_value, val_date = :sysdate, " +
                       "alarm_date = :tmp_date, available = 1 where params_id = :p_id")
          .bind("p_id",          p_id)
          .bind("current_value", current_value)
          .bind("sysdate",       sysdate())
          .bind("tmp_date",      tmp_date)
          .execute();
      else
        h.createUpdate("update current_params "
                     + "set available = 0 where params_id = :p_id")
          .bind("p_id", p_id)
          .execute();
      });


  }

  public static void saveShedulerVal(int shedulerId, String value) {

  }

//----------------------------------------------------------------------------------------------------------
  public static void logErrors(int paramId, String method, String shortError, Exception e) {
    Logger.getLogger(DBUtil.class.getName()).logp(Level.SEVERE,
      "Task", method, "Param_id: " + paramId + "; " + shortError, e);

    try (Connection dbConnection = Manager.dsMonitor.getConnection()) {
      PreparedStatement saveLogStatement = dbConnection.prepareStatement("insert into log_tab values(?,?,?,?,?,?)");
      saveLogStatement.setTimestamp(1, new java.sql.Timestamp((new java.util.Date()).getTime()));
      saveLogStatement.setString(2, "QueryThread");
      saveLogStatement.setString(3, method);
      saveLogStatement.setInt(4, paramId);
      saveLogStatement.setString(5, shortError);
      saveLogStatement.setString(6, e.getMessage().substring(0, 250));
      saveLogStatement.executeUpdate();
    } catch (SQLException ex) {
      Logger.getLogger(DBUtil.class.getName()).logp(Level.SEVERE,
        "Task", "logErrors", "Error saving log_tab to DB.", ex);
    }

  }




}
