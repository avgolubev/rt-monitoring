/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package avgolubev.rt_monitoring;

import java.util.concurrent.ThreadFactory;

/**
 *
 * @author avgolubev
 */
public class TaskThreadFactory implements ThreadFactory {

  @Override
  public Thread newThread(Runnable r) {
    Thread thread = new Thread(r);
    thread.setDaemon(true);
    return thread;
  }

}
