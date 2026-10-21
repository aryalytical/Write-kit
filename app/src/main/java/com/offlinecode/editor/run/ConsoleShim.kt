package com.offlinecode.editor.run

/** JS injected before user scripts so console output and errors reach the Output panel. */
object ConsoleShim {
    const val JS = """(function(){
function fmt(a){if(typeof a==='string')return a;if(a instanceof Error)return a.stack||String(a);
if(typeof a==='function'||typeof a==='undefined'||typeof a==='symbol')return String(a);
try{return JSON.stringify(a,null,2)}catch(e){return String(a)}}
['log','info','warn','error','debug'].forEach(function(k){
console[k]=function(){var s=Array.prototype.map.call(arguments,fmt).join(' ');Host.print(k,s);};});
window.addEventListener('error',function(e){Host.print('error',(e.message||'Error')+(e.filename?'\n  at '+e.filename+':'+e.lineno+':'+e.colno:''));});
window.addEventListener('unhandledrejection',function(e){Host.print('error','Unhandled promise rejection: '+fmt(e.reason));});
})();"""
}
