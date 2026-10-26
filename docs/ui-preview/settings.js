/* Clickable settings design sample only. No Android persistence or runtime execution. */
const settingSampleSpecs=[
 [0,'theme','界面主题','工作台、菜单与设置使用同一套主题',null,['深色','浅色','跟随系统']],
 [1,'syntax','语法主题','基础词法高亮','显示与配色',['跟随界面','Dark+','Light+']],
 [1,'font','代码字号','使用 sp，随系统字体缩放','显示与配色',Array.from({length:19},(_,i)=>(i+10)+' sp')],
 [1,'lineNumbers','显示行号','与源码逻辑行保持一致','显示与配色'],[1,'wordWrap','自动换行','按编辑区可用宽度折行','显示与配色'],
 [1,'tabWidth','缩进宽度','Tab 和智能缩进共同使用','缩进与输入',['2 列','4 列','8 列']],[1,'useSpaces','使用空格缩进','关闭后插入 Tab 字符','缩进与输入'],[1,'autoIndent','自动缩进','换行后继承适当缩进','缩进与输入'],[1,'autoPairs','补齐括号和引号','保留组合输入与撤销行为','缩进与输入'],
 [2,'complete','基础词法补全','关键词、缓冲区符号与内置候选','候选与参考'],[2,'signatureHelp','内置参数参考','支持的函数参数与说明','候选与参考'],
 [2,'completionTab','Tab 接受补全','关闭时 Tab 执行缩进','补全按键'],[2,'completionEnter','Enter 接受选中候选','方向键明确选中后接受','补全按键'],[2,'completionArrows','候选方向键','Esc 关闭后移动代码光标','补全按键',['主动触发后导航候选','候选可见时导航','始终移动代码光标']],[2,'symbolBar','编程符号栏','输入时显示 Tab 和括号','触屏输入'],
 [3,'autosave','自动保存','失败时保留恢复草稿'],[3,'autoSaveDelay','自动保存延迟','离开页面保留保存保护',null,['700 ms','1500 ms','3000 ms']],
 [4,'terminalFont','终端字号','终端与运行结果共用偏好','终端显示',Array.from({length:15},(_,i)=>(i+10)+' sp')],[4,'terminal','终端配色','切换颜色保留当前会话','终端显示',['跟随界面','CodePocket Dark','Nord','Dracula','Light']],[4,'collapsePanelOnIme','输入代码时临时收起终端','仅在键盘实际占用底部时生效','窗口与键盘'],
 [5,'animations','动画总开关','遵从系统减少动画与省电策略','动画策略'],[5,'motion','平滑移动代码光标','普通输入约60ms，短距离导航约80ms','代码光标'],[5,'blink','柔和闪烁代码光标','输入时清晰显示，停顿后柔和闪烁','代码光标']];
openSettings=function(initialCategory=null){
 let draft={...saved},category=initialCategory??(isPhone?-1:0),query='';
 $('#layers').innerHTML='<section class="settings"><div class="settingshead"><button class="icon" id="settingsback" title="返回分类">'+icon('arrow-left')+'</button><h2>设置</h2><span aria-hidden="true">'+icon('settings-2')+'</span></div><div class="settingssearch"><input type="search" placeholder="搜索设置" aria-label="搜索设置"></div><div class="settingsbody"><nav class="settingsnav"></nav><div class="settingsdetail"></div></div><div class="actions"><button id="settingscancel">取消</button><button class="primary" id="settingsapply">应用</button></div></section>';
 const groupTitle=s=>query?cats[s[0]]+(s[4]?' · '+s[4]:''):s[4];
 function picker(s){
  const scope=cats[s[0]]+(s[4]?' · '+s[4]:'');const layer=document.createElement('section');layer.className='modal settings-choice';layer.setAttribute('aria-label',s[2]);
  layer.innerHTML='<div class="modalheader"><h2>'+s[2]+'</h2></div><div class="picker-scope">'+scope+'</div><div class="modalbody">'+s[5].map(v=>'<button class="menurow choice-option '+(v===(draft[s[1]]??s[5][0])?'selected':'')+'" data-value="'+v+'">'+icon(s[1].includes('Font')||s[1]==='font'?'case-sensitive':'palette')+'<span class="label">'+v+'</span>'+(v===(draft[s[1]]??s[5][0])?icon('check'):'')+'</button>').join('')+'</div><div class="actions"><button>取消</button></div>';
  $('#layers').append(layer);layer.querySelector('.actions button').onclick=()=>layer.remove();layer.querySelectorAll('[data-value]').forEach(b=>b.onclick=()=>{draft[s[1]]=b.dataset.value;layer.remove();render()});
 }
 function render(){
  $('.settingsnav').innerHTML=cats.map((s,i)=>'<button class="'+(category===i?'active':'')+'" data-cat="'+i+'">'+s+'</button>').join('');const detail=$('.settingsdetail');
  if(category<0&&!query)detail.innerHTML='<h3>工作台偏好</h3>'+cats.map((s,i)=>'<button class="menurow" data-cat="'+i+'">'+s+'</button>').join('');
  else {
   const matches=settingSampleSpecs.filter(s=>query?(s[2]+s[3]+cats[s[0]]).includes(query):s[0]===category);let prior;
   detail.innerHTML='<h3>'+(query?'搜索结果':cats[category])+'</h3>'+matches.map(s=>{const group=groupTitle(s);const title=group&&group!==prior?'<h4 class="settings-group">'+group+'</h4>':'';prior=group;return title+'<div class="setting"><div class="words"><strong>'+s[2]+'</strong><p>'+s[3]+'</p>'+(s[5]?'<button class="setting-choice" data-key="'+s[1]+'">'+(draft[s[1]]??s[5][0])+icon('chevron-right')+'</button>':'')+'</div>'+(s[5]?'':'<input type="checkbox" data-key="'+s[1]+'" aria-label="'+s[2]+'" '+(draft[s[1]]!==false?'checked':'')+'>')+'</div>';}).join('')+(!matches.length&&category!==6?'<p>没有匹配的设置</p>':'');
   if(category===2&&!query)detail.insertAdjacentHTML('beforeend','<button class="menurow" id="sample-key-help">'+icon('keyboard')+'补全按键说明</button>');
   if(category===6&&!query)detail.insertAdjacentHTML('beforeend','<p>快捷键说明 / 运行与权限 / 开源许可证</p>');
   detail.querySelectorAll('.setting-choice').forEach(b=>b.onclick=()=>picker(settingSampleSpecs.find(s=>s[1]===b.dataset.key)));detail.querySelectorAll('input[data-key]').forEach(b=>b.onchange=()=>draft[b.dataset.key]=b.checked);
   if($('#sample-key-help'))$('#sample-key-help').onclick=()=>{const layer=document.createElement('section');layer.className='modal settings-choice';layer.innerHTML='<div class="modalheader"><h2>补全按键说明</h2></div><div class="modalbody"><p>Tab 接受候选或缩进；Shift+Tab 反缩进。方向键按设置导航，Esc 关闭。Ctrl+Space 主动请求基础词法补全。</p><p>设计预览；实际完整说明以 App 为准。</p></div><div class="actions"><button>关闭</button></div>';$('#layers').append(layer);layer.querySelector('button').onclick=()=>layer.remove();};
  }
  document.querySelectorAll('[data-cat]').forEach(b=>b.onclick=()=>{category=+b.dataset.cat;query='';$('.settings input[type=search]').value='';render()});$('#settingsback').style.visibility=category>=0&&isPhone?'visible':'hidden';
 }
 $('.settings input[type=search]').oninput=e=>{query=e.target.value;render()};$('#settingsback').onclick=()=>{category=-1;query='';render()};$('#settingscancel').onclick=closeLayers;$('#settingsapply').onclick=()=>{saved=draft;document.body.classList.toggle('light',draft.theme==='浅色'||(draft.theme==='跟随系统'&&matchMedia('(prefers-color-scheme:light)').matches));applyTerminalPreview(draft.terminal);closeLayers()};render();
};
const settingsParentScene=scene;
scene=function(name){if(name==='设置选项'){closeLayers();openSettings(1);$('.setting-choice[data-key="font"]').click();return;}if(name==='补全按键说明'){closeLayers();openSettings(2);$('#sample-key-help').click();return;}return settingsParentScene(name);};
['设置选项','补全按键说明'].forEach(n=>$('#scene').add(new Option(n,n)));
