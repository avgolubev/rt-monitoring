/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package avgolubev.rt_monitoring;

import java.net.InetAddress;
import snmp.SNMPObject;
import snmp.SNMPSequence;
import snmp.SNMPVarBindList;
import snmp.SNMPv1CommunicationInterface;

/**
 *
 * @author avgolubev
 */
public class TaskSNMP extends Task {

//-------------------------------------------------------------------------------------------------------
  public TaskSNMP(int taskId, int paramId, int timeOut, String query,
    String sourcePoolContext, String ip, String port, String login, String password,
    int shedulerId, String condition, int threshold, int repetitions, boolean useResult) {
    super(taskId, paramId, timeOut, query, sourcePoolContext, ip, port, login, password,
      shedulerId, condition, threshold, repetitions, useResult);
  }
//-------------------------------------------------------------------------------------------------------

  @Override
  String execQuery(String queryParam) throws Exception {
    String parameterValue;

    SNMPv1CommunicationInterface comInterface = new SNMPv1CommunicationInterface(0, InetAddress.getByName(ip),
      "public", Integer.parseInt(port));
    SNMPVarBindList newVars = comInterface.getMIBEntry(
      (queryParam == null ? query : query.replaceFirst(INPUT_PARAM_MASK, queryParam))
    );
    SNMPSequence pair = (SNMPSequence) (newVars.getSNMPObjectAt(0));
    SNMPObject snmpValue = pair.getSNMPObjectAt(1);
    if (Thread.interrupted()) {
      throw new InterruptedException("Immediate task stop.");
    }
    String typeString = snmpValue.getClass().getName();
    parameterValue = snmpValue.toString();

    if (typeString.equals("snmp.SNMPOctetString")) {
      int nullLocation = parameterValue.indexOf('\0');
      if (nullLocation >= 0) {
        parameterValue = parameterValue.substring(0, nullLocation);
      }
    }
    comInterface.closeConnection();

    return parameterValue;
  }

}
