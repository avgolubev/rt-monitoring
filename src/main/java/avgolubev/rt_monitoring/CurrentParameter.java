/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package avgolubev.rt_monitoring;

import java.util.Date;

/**
 *
 * @author avgolubev
 */
public class CurrentParameter {

  public int id;
  public int value;
  public int delta;
  public Date value_date;
  public Date alarm_date;
  public int available;

  public CurrentParameter(int id, int value, int delta, Date value_date, Date alarm_date, int available) {
    this.id = id;
    this.value = value;
    this.delta = delta;
    this.value_date = value_date;
    this.alarm_date = alarm_date;
    this.available = available;
  }

}
