
//-------------------------------------------------------------------------------------------
function AJAXInteraction2(url, divId, callback) {
    var req = init();
    req.onreadystatechange = processRequest;

    function init() {
      if (window.XMLHttpRequest) return new XMLHttpRequest();
      else if (window.ActiveXObject) return new ActiveXObject("Microsoft.XMLHTTP");
    }

    function processRequest () {
      // readyState of 4 signifies request is complete
      if (req.readyState == 4)
        // status of 200 signifies sucessful HTTP call
        if (req.status == 200)
          if (callback) callback(req,divId);
    }

    this.doGet = function() {
      req.open("GET", url, true);
      req.send(null);
    }
}
//-------------------------------------------------------------------------------------------
//список параметров по номеру группы
var parametersByGroupURL= 'client?action=1&argument=';

//-------------------------------------------------------------------------------------------
function getParametersByGroup(group,divId,ajaxId) {
    var url = parametersByGroupURL+group;
	if(!paramGroups[ajaxId][2])
		paramGroups[ajaxId][2] = new AJAXInteraction2(url, divId, showParametersByGroup);
    paramGroups[ajaxId][2].doGet();
}
//-------------------------------------------------------------------------------------------
function showParametersByGroup(req,divId){
    var myJsonObj = jQuery.parseJSON(req.responseText);
    
    var result='<table border="0" cellpadding="0" cellspacing="0" width="100%">'+
    '<tbody><tr><td bgcolor="gray">'+
    '<table class="text1" align="center" border="0" cellpadding="1" cellspacing="1" width="100%">'+
    '<tbody><tr align="center" bgcolor="#550097">'+
    '<td colspan="8" class="menulink">'+myJsonObj.group+
    '</td></tr>'+
    '<tr bgcolor="#dcdcdc">'+
    '<th class="menulink2"></th>'+
    '<th class="menulink2">№</th>'+
    '<th class="menulink2">Параметр</th>'+
    '<th class="menulink2">Значение</th>'+
    '<th class="menulink2">Изменение</th>'+
    '<th class="menulink2">Порог</th>'+
    '<th class="menulink2">Время<br>обновления</th>'+
    '<th class="menulink2">Время<br>возникновения<br>аварии</th>'+
    '</tr>';

    var num = 0;
    var trColor;		
    var checked;
     
    //---------цикл по параметрам в группе--------------------------
	for (var i in myJsonObj.params) 
	{
        if(myJsonObj.params[i].av==1)
		{//параметр доступен
            if(myJsonObj.params[i].expired==1)//данные просрочены
                trColor = '#99ccff';
            else{
                trColor=(myJsonObj.params[i].alarm=='' ? '#f0fff0':'#ff6347');
				if(alarmView && myJsonObj.params[i].alarm=='') continue;
			}	
        }    
        else// параметр недоступен
            trColor='#feff9c';
			
			
		
		num++;//количество отображаемых параметров в группе			
			
		//--восстановление отмеченных параметров (графики) после обновления
		checked='';
        for(var ch = 0; ch < check_array.length; ++ch) 
		{
            if(check_array[ch][0] == myJsonObj.params[i].id){
                checked= check_array[ch][1]==1 ? 'checked':'';
                break;
            }
        }//-----------------------------------------------------
			
	
        result += '<tr bgcolor="'+trColor+'">\n<td>'+
        '<input type="checkbox" onclick="setCheckStatus('+myJsonObj.params[i].id+');" '+checked+'></td>\n'+			
        '<td>'+num+'</td>\n<td>'+myJsonObj.params[i].name+'</td>\n<td>'+
        myJsonObj.params[i].value+'</td>\n<td>'+myJsonObj.params[i].delta+'</td>\n<td>'+
        +myJsonObj.params[i].limit+'</td>\n<td>'+
        myJsonObj.params[i].date+'</td>\n<td>'+myJsonObj.params[i].alarm+'</td>\n</tr>';
    }//-----------------------------------
  
    if(num>0)
		result+='</tbody></table> </td></tr></tbody></table>';
	else
		result = '';
  
    var div_handle = document.getElementById(divId); 
    if(div_handle)     
        div_handle.innerHTML=result;     
}
//-------------------------------------------------------------------------------------------

