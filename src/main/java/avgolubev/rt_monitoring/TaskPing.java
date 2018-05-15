/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package avgolubev.rt_monitoring;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 *
 * @author avgolubev
 */
public class TaskPing extends Task{


//-------------------------------------------------------------------------------------------------------
    public TaskPing(int taskId, int paramId, int timeOut, String query,
            String sourcePoolContext, String ip, String port, String login, String password,
            int shedulerId, String condition, int threshold, int repetitions, boolean useResult) {
        super(taskId, paramId, timeOut, query, sourcePoolContext, ip, port, login, password,
                shedulerId, condition, threshold, repetitions, useResult);
    }
//-------------------------------------------------------------------------------------------------------

    @Override
    String execQuery(String queryParam) throws Exception {
        String parameterValue;

        Process process = Runtime.getRuntime().exec("/etc/ping " +
                (queryParam==null ? query:query.replaceFirst(INPUT_PARAM_MASK, queryParam)) + " -n 4");
        process.waitFor();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream(), "CP866"))) {
            StringBuilder sb = new StringBuilder();
            String temp;

            while ((temp = br.readLine()) != null) {
                checkInterrupt();
                sb.append(temp);
            }

            parameterValue = (sb.toString().contains("100% потерь")
                    || sb.toString().contains("100% loss") || sb.toString().contains("100% packet loss"))
                    ? "0" : "1";

        }
        return parameterValue;
    }
//---------------------------------------------------------------------------------------------------------
    private void checkInterrupt() throws InterruptedException{
        if(Thread.interrupted())
                throw new InterruptedException("Immediate task stop.");
    }

}
