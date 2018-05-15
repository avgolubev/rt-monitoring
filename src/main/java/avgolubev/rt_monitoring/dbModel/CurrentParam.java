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

/**
 *
 * @author avgolubev
 */
public class CurrentParam {

  public int params_id;
  public int val;
  public int delta;
  public java.sql.Timestamp val_date;
  public java.sql.Timestamp alarm_date;
  public int available;

//  public CurrentParam(int params_id, int val, int delta, java.sql.Timestamp val_date,
//                       java.sql.Timestamp alarm_date, int available) {
//    this.params_id = params_id;
//    this.val = val;
//    this.delta = delta;
//    this.val_date = val_date;
//    this.alarm_date = alarm_date;
//    this.available = available;
//  }

}
