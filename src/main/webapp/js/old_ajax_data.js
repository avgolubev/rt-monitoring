
//-------------------------------------------------------------------------------------------
function AJAXInteraction(url, callback) {
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
          if (callback) callback(req);
    }

    this.doGet = function() {
      req.open("GET", url, true);
      req.send(null);
    }
}
//-------------------------------------------------------------------------------------------
function sendQuery() {
    var url = "client?action=1&argument=33";// + encodeURIComponent(target.value);
    var ajax = new AJAXInteraction(url, showParametersByGroup);
    ajax.doGet();
}

//-------------------------------------------------------------------------------------------