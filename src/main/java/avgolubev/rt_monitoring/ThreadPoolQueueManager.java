package avgolubev.rt_monitoring;

import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ThreadPoolQueueManager implements Runnable {

//  public ThreadPoolQueueManager() {
//    Logger.getLogger(ThreadPoolQueueManager.class.getName()).log(Level.INFO, "**************new ThreadPoolQueueManager************");
//  }

  @Override
  public void run() {
    Task nextTask;
    Logger.getLogger(ThreadPoolQueueManager.class.getName()).log(Level.INFO, "start ThreadPoolQueueManager");

    for (ConcurrentMap.Entry<String, Task> entry : Manager.TASK_LIST.entrySet()) {
      if (Thread.interrupted()) {
        break;
      }

      nextTask = entry.getValue();

      if (nextTask.isReadyToStart()) {
        nextTask.setExecuteStatus(true);
        Manager.TASK_FUTURE_LIST.put(entry.getKey(),
          Manager.TASK_THREAD_POOL.submit(nextTask) );
      }

    }

    Logger.getLogger(ThreadPoolQueueManager.class.getName()).log(Level.INFO, "stop ThreadPoolQueueManager");
  }
  
}
