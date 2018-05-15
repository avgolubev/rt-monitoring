/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package avgolubev.rt_monitoring;

import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author avgolubev
 */
public class SerialThreadPoolQueueManager implements Runnable {

  @Override
  public void run() {

    SerialTask nextSerialTask;
    Logger.getLogger(SerialThreadPoolQueueManager.class.getName()).log(Level.INFO, "start SerialThreadPoolQueueManager");
    for (ConcurrentMap.Entry<String, SerialTask> entry : Manager.SERIAL_TASK_LIST.entrySet()) {
      if (Thread.interrupted()) {
        break;
      }

      nextSerialTask = entry.getValue();

      if (nextSerialTask.isReadyToStart()) {
        nextSerialTask.setExecuteStatus(true);
        Manager.SERIAL_TASK_FUTURE_LIST.put(entry.getKey(),
          Manager.SERIAL_TASK_THREAD_POOL.submit(nextSerialTask)
        );
      }
    }
    Logger.getLogger(SerialThreadPoolQueueManager.class.getName()).log(Level.INFO, "stop SerialThreadPoolQueueManager");
  }

}
