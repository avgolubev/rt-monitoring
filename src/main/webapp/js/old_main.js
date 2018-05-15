
function change_param(){
paramData.edit_mode.value='save';
paramData.submit();
}
//--------------------------------------------------------------------------------------------------------
function to_edit_mode(str){
webMode.edit_mode.value='edit';
webMode.param_id.value=str;
webMode.submit();
}
//--------------------------------------------------------------------------------------------------------
function cancel_param(){
webMode.edit_mode.value='see';
webMode.submit();
}
//--------------------------------------------------------------------------------------------------------
function choose_Group(str){
mData.group_view.value = str;
mData.submit();
}
//--------------------------------------------------------------------------------------------------------
function on_off_Alarm(str){
mData.alarm_only.value = str;
mData.submit();
}
//--------------------------------------------------------------------------------------------------------
function action(sv,ac){
ServiceData.argument.value = sv;
ServiceData.action.value = ac;
ServiceData.submit();
}
//--------------------------------------------------------------------------------------------------------
function action2(ac){
ServiceData.action.value = ac;
ServiceData.submit();
}
//--------------------------------------------------------------------------------------------------------

