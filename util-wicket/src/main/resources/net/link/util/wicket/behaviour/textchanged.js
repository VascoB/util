 /*
  * Script to trigger Wicket if the content of a text field is changing
  * @author Stein Desmet
  */

 ;
 
 if (typeof(Wicket) == "undefined")
 	Wicket = { };

 Wicket.TextChanged=function(elementId, callbackUrl, enableTimer){
      var txtField = $('#' + elementId);
      txtField.data('oldValue', txtField.val());
      txtField.bind("propertychange keyup input paste cut", watchTextBox );
    
      if(enableTimer==true) {
          var timer = setInterval(watchTextBox, 100);
      }

      function watchTextBox(){
          // If value has changed...
          if (txtField.data('oldValue') != txtField.val()) {
               // Updated stored value
               //txtField.data('oldValue', txtField.val());
               txtField.unbind("propertychange keyup input paste cut", watchTextBox );
               if(enableTimer==true) {
                    clearInterval(timer);
                }
               // Callback to wicket
               var wcall = wicketAjaxGet(callbackUrl+ (callbackUrl.indexOf("?")>-1 ? "&" : "?") + "tval="+txtField.val(), function() { }, function() { });
         }
      }
 };

