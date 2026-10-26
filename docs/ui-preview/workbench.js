/* Presentation sample only. No filesystem, Python, C++, or Shell execution. */
const previewApp=$('#app'), previewBody=$('.body'), previewRail=$('.activity');
const previewWorkspace=document.createElement('div');previewWorkspace.className='workspace';
const previewUpper=document.createElement('div');previewUpper.className='upper-workspace';
const previewExplorer=$('#explorer'),previewEditor=$('.work'),previewTerminal=$('#terminal');
previewUpper.append(previewExplorer,previewEditor);previewWorkspace.append(previewUpper,previewTerminal,$('.symbolbar'),$('.status'));previewBody.append(previewWorkspace);
$('.top>.icon').classList.add('explorer-duplicate');
const previewTools=$('.edittools');previewTools.querySelector('[title="编辑操作"]').remove();
const previewTabs=document.createElement('div');previewTabs.className='tabstrip';previewEditor.prepend(previewTabs);previewTabs.append($('.tabs'),previewTools);
previewExplorer.querySelector('[title="新建文件夹"]').remove();
const railPrimary=document.createElement('div');railPrimary.className='rail-primary';
[...previewRail.querySelectorAll('button:not(.bottom)')].forEach(b=>railPrimary.append(b));
railPrimary.insertAdjacentHTML('beforeend','<button class="icon" title="运行预览" onclick="scene(\'运行状态\')">'+icon('play')+'</button>');previewRail.prepend(railPrimary);railPrimary.firstElementChild.classList.add('selected');
const desktopMenu=document.createElement('nav');desktopMenu.className='desktop-menu';desktopMenu.setAttribute('aria-label','工作台菜单');
const menuGroups=[['文件','文件操作','files'],['编辑','编辑菜单','pencil'],['视图','视图菜单','maximize-2'],['运行','运行菜单','play'],['终端','终端菜单','terminal'],['帮助','帮助菜单','circle-help']];
desktopMenu.innerHTML=menuGroups.map(g=>`<button onclick="scene('${g[1]}')">${g[0]}</button>`).join('');$('.top').prepend(desktopMenu);
const previewSizes={tablet:[1280,800,'平板横屏'],tabletPortrait:[800,1200,'平板竖屏'],phoneLandscape:[840,411,'手机横屏'],phone:[411,891,'手机竖屏']};
$('#device').innerHTML=Object.entries(previewSizes).map(([k,v])=>`<option value="${k}">${v[2]} · ${v[0]} × ${v[1]}</option>`).join('');
let previewHeightOverride=null;
function previewLayout(reset=false){
 if(reset)previewHeightOverride=null;
 const size=previewSizes[$('#device').value];previewApp.style.width=size[0]+'px';previewApp.style.height=(previewHeightOverride??size[1])+'px';
 const width=previewApp.clientWidth,height=previewApp.clientHeight;previewApp.classList.toggle('compact-captions',height<360);const drawer=width<600||height<480;
 isPhone=width<840||height<480;previewApp.classList.toggle('phone',drawer);previewApp.classList.toggle('drawer-mode',drawer);previewApp.classList.toggle('stacked-settings',isPhone);previewApp.classList.toggle('desktop',width>=1000);previewApp.classList.toggle('compact-top',width<600);
 $('.top .title').textContent=width<600?'CodePocket':'Algorithms';
 if(reset){closeLayers();previewExplorer.classList.remove('hidden');previewApp.classList.remove('termmax');previewTerminal.classList.toggle('hidden',drawer);previewTerminal.classList.toggle('open',!drawer);}
}
$('#device').onchange=()=>previewLayout(true);window.addEventListener('resize',()=>previewLayout(false));
toggleExplorer=function(){if(previewApp.classList.contains('drawer-mode')){previewExplorer.classList.toggle('open');previewApp.classList.toggle('drawer',previewExplorer.classList.contains('open'));}else previewExplorer.classList.toggle('hidden');};
const originalRootToggle=toggleRoot;toggleRoot=function(){originalRootToggle();$('#root .folder').innerHTML=icon(rootOpen?'folder-open':'folder');};
const originalScene=scene;
scene=function(name){
 closeLayers();
 switch(name){
 case '紧凑编辑区':previewHeightOverride=240;previewLayout(false);previewApp.classList.remove('termmax');previewTerminal.classList.add('hidden');return;
 case '调整面板尺寸':return menu([['减小面板','工作台','minimize-2'],['增大面板','工作台','maximize-2'],['恢复默认尺寸','工作台','refresh-cw']]);
 case '终端菜单':return menu([['打开终端','打开终端','terminal'],['最大化 / 恢复','最大化 / 恢复','maximize-2'],['收起终端','收起终端','chevron-down'],'-',['清空终端显示','工作台','eraser'],['调整面板尺寸','调整面板尺寸','move']]);
 case '打开工作区':case '工作区载入失败':return previewWorkspaceState(name==='工作区载入失败');
 case '主菜单':return menu(menuGroups.map(g=>[g[0],g[1],g[2]]));
 case '编辑菜单':return menu([['撤销','工作台','undo-2',false,'Ctrl+Z'],['重做','工作台','redo-2',false,'Ctrl+Y / Ctrl+Shift+Z'],'-',
 ['当前文件查找','查找与替换','search',false,'Ctrl+F'],['替换','查找与替换','refresh-cw',false,'Ctrl+H'],['跳转行','跳转行','crosshair',false,'Ctrl+G'],'-',
 ['代码字号','代码字号','case-sensitive',false,'Ctrl++ / Ctrl+-'],'-',
 ['基础词法补全','补全候选','file-code-2',false,'Ctrl+Space'],['内置参数参考','函数参数提示','brackets',false,'Ctrl+Shift+Space'],['内置代码片段','内置代码片段','file-code-2']]);
 case '跳转行':return modal(name,'<label>行号</label><input aria-label="行号" type="number" min="1" value="1">','跳转');
 case '内置代码片段':return menu([['Python for 循环','补全候选','file-code-2'],['C++ for 循环','补全候选','file-code-2']]);
 case '文件操作':return menu([['新建文件','新建文件','file-plus-2',false,'Ctrl+N'],['打开 / 切换工作区','切换项目','folder-open',false,'Ctrl+O'],['保存','工作台','save',false,'Ctrl+S'],'-',['导出副本','恢复草稿','download'],['恢复草稿','恢复草稿','refresh-cw']]);
 case '视图菜单':return menu([['打开终端','打开终端','terminal'],['资源管理器','切换文件树','files',false,'Ctrl+B'],['代码字号','代码字号','case-sensitive'],['切换深浅主题','切换主题','palette'],['设置','设置','settings-2']]);
 case '运行菜单':return menu([['运行预览','运行状态','play',false,'F5'],['问题详情','问题详情','info']]);
 case '帮助菜单':return menu([['快捷键说明','快捷键说明','keyboard'],['帮助与许可','帮助与许可','circle-help']]);
 case '打开终端':openTerminal();return;
 case '切换文件树':toggleExplorer();return;
 case '项目菜单':return menu([['定位当前文件','工作台','crosshair'],['折叠全部目录','折叠全部','list-collapse'],'-',['新建文件夹','新建文件夹','folder-plus'],'-',['打开 / 切换工作区','切换项目','folder-open'],['导入副本','新建文件','upload'],'-',['导出当前内容','工作台','download'],'-',['重命名当前文件','重命名','pencil'],'-',['删除当前文件','确认删除','trash-2',true],'-',['重新载入当前文件','工作台','refresh-cw'],['恢复草稿','恢复草稿','refresh-cw']]);
 case '文件菜单':return menu([['打开','工作台','file-code-2'],'-',['重命名','重命名','pencil'],['移动到…','移动到文件夹','move'],'-',['删除','确认删除','trash-2',true]]);
 case '终端会话':return menu([['Shell 1 · main.py','工作台','terminal'],['Shell 2 · 空闲','工作台','terminal']]);
 case '项目搜索':case '项目搜索无匹配':return previewProjectSearch(name==='项目搜索无匹配');
 case '切换项目':case '工作区长名称':return previewWorkspacePicker(name==='工作区长名称');
 case '恢复草稿':case '恢复草稿长路径':return previewDraftPicker(name==='恢复草稿长路径');
 case '移动到文件夹':case '移动到深层目录':return previewMovePicker(name==='移动到深层目录'?['include','算法练习_'.repeat(4)]:[]);
 case '折叠全部':if(rootOpen)toggleRoot();return;
 case '终端操作':return menu([['终端操作','终端动作','terminal'],['会话管理','会话动作','files'],['布局与输入','布局与输入','maximize-2'],['配色与帮助','配色与帮助','palette']]);
 case '终端动作':return menu([['新建终端','终端会话','file-plus-2'],['复制记录','工作台','copy'],['粘贴','工作台','clipboard'],['清空显示','工作台','eraser']]);
 case '会话动作':return menu([['选择会话','终端会话','terminal'],['重命名会话','重命名','pencil'],'-',['关闭当前会话','结束会话','trash-2',true]]);
 case '布局与输入':return menu([['调整面板尺寸','调整面板尺寸','move'],['显示键盘','显示键盘','keyboard'],['控制键','控制键','keyboard'],['最大化 / 恢复','最大化 / 恢复','maximize-2'],['收起终端','收起终端','chevron-down']]);
 case '配色与帮助':return menu([['终端配色','终端配色','palette'],['快捷键说明','快捷键说明','keyboard'],['帮助与许可','帮助与许可','circle-help']]);
 case '最大化 / 恢复':openTerminal();previewApp.classList.toggle('termmax');return;
 case '收起终端':previewApp.classList.remove('termmax');previewTerminal.classList.add('hidden');return;
 case '显示键盘':openTerminal();$('.inputline').focus();return;
 case '控制键':openTerminal();$('#controlkeys').classList.toggle('hidden');return;
 case '代码字号':return modal(name,'<div class="font-preview" style="padding:20px;background:var(--editor);text-align:center;font:14px monospace">Aa 0123 你好</div><div style="display:flex;justify-content:space-around;align-items:center"><button onclick="previewFont(-1)">A−</button><span id="previewFontValue">14 sp</span><button onclick="previewFont(1)">A+</button></div>','完成');
 case '新建文件':case '新建文件夹':case '重命名':return modal(name,'<div class="location-preview">'+icon('folder')+'<div><small>位置</small>Algorithms /</div></div><label>名称</label><input aria-label="名称" value="'+(name==='重命名'?'main.py':name==='新建文件'?'untitled.py':'')+'">',name==='重命名'?'重命名':'创建');
 }
 return originalScene(name);
};
let previewFontSize=14;
function previewFont(delta){previewFontSize=Math.max(10,Math.min(28,previewFontSize+delta));$('.font-preview').style.fontSize=previewFontSize+'px';$('#previewFontValue').textContent=previewFontSize+' sp';}
const categoryIcons=['palette','file-code-2','keyboard','save','terminal','move','circle-help'];
const categoryHints=['主题与配色','字号、缩进与显示','候选行为与按键','自动保存与恢复','终端显示与运行','光标与界面过渡','说明与开源组件'];
function decorateCategories(){document.querySelectorAll('.settingsnav [data-cat],.settingsdetail [data-cat]').forEach(button=>{if(button.dataset.decorated)return;button.dataset.decorated='true';const i=+button.dataset.cat;button.innerHTML='<span class="categorymark">'+icon(categoryIcons[i])+'</span><span class="categorywords"><strong>'+cats[i]+'</strong>'+(button.closest('.settingsdetail')?'<small>'+categoryHints[i]+'</small>':'')+'</span>';});}
function decorateSectionHeadings(){
 decorateCategories();
 document.querySelectorAll('.settingsdetail h3').forEach(heading=>{
  if(heading.dataset.decorated)return;
  const title=heading.textContent;const index=cats.indexOf(title);
  const glyph=index>=0?categoryIcons[index]:['工作台偏好','偏好设置'].includes(title)?'settings-2':title==='搜索结果'?'search':null;
  if(!glyph)return;
  heading.dataset.decorated='true';
  const mark=document.createElement('span');mark.className='sectionmark';mark.setAttribute('aria-hidden','true');mark.innerHTML=icon(glyph);heading.prepend(mark);
 });
}
new MutationObserver(decorateSectionHeadings).observe($('#layers'),{childList:true,subtree:true});
const endButton=document.createElement('button');endButton.className='icon';endButton.title='关闭当前终端会话';endButton.innerHTML=icon('trash-2');endButton.onclick=()=>scene('结束会话');$('.termhead [title="最大化终端"]').before(endButton);
const sessionName=$('.termname');sessionName.innerHTML=icon('terminal')+'<span class="session-label">终端 · Shell 1</span>'+icon('chevron-down');
const collapseButton=$('.termhead [title="收起终端"]');collapseButton.innerHTML=icon('chevron-down');collapseButton.onclick=()=>scene('收起终端');
const options=$('#scene');['文件操作','视图菜单','运行菜单','帮助菜单','代码字号','打开工作区','工作区载入失败'].forEach(n=>options.add(new Option(n,n)));
previewLayout(true);
const themePreviewToggle=$('#theme').onclick;
$('#theme').onclick=()=>{themePreviewToggle();saved.theme=document.body.classList.contains('light')?'浅色':'深色';};
new MutationObserver(()=>{$('#theme').textContent=document.body.classList.contains('light')?'切换深色':'切换浅色';}).observe(document.body,{attributes:true,attributeFilter:['class']});
const includeRow=previewExplorer.querySelector('.treeitem');const headerRow=[...previewExplorer.querySelectorAll('.treeitem')].find(e=>e.textContent.includes('utils.hpp'));
const includeChildren=document.createElement('div');includeChildren.className='nested-preview hidden';includeRow.after(includeChildren);includeChildren.append(headerRow);includeRow.setAttribute('aria-expanded','false');
function includeVisual(open){includeRow.innerHTML='<span>'+icon(open?'chevron-down':'chevron-right')+'</span><span class="folder">'+icon(open?'folder-open':'folder')+'</span><span class="treename">include</span>';includeRow.setAttribute('aria-expanded',String(open));includeChildren.classList.toggle('hidden',!open);}
includeRow.onclick=()=>includeVisual(includeRow.getAttribute('aria-expanded')!=='true');includeVisual(false);
document.querySelector(".top .projectSearch")?.remove();

// Existing startup states, explicitly shown as a design example rather than running code.
function previewWorkspaceState(failed){
 const symbol=failed?'info':'file-code-2';
 $('#layers').innerHTML='<section class="workspace-state'+(failed?' failed':'')+'"><header class="startup-brand">CodePocket</header><div class="startup-body"><span class="state-symbol" aria-hidden="true">'+icon(symbol)+'</span><h2>'+(failed?'工作区暂时无法打开':'正在打开工作区…')+'</h2>'+(failed?'<p>载入失败不会删除原文件或恢复草稿</p>':'')+'</div></section>';
 if(failed)$('#layers').insertAdjacentHTML('beforeend','<section class="modal"><div class="modalheader"><h2>工作区载入失败</h2></div><div class="modalbody"><p>设计示例：目录授权暂时不可用。请选择原目录重新授权。</p></div><div class="actions"><button onclick="scene(&quot;工作台&quot;)">内部项目</button><button onclick="scene(&quot;切换项目&quot;)">选择目录</button></div></section>');
}

const previewTerminalDock=document.querySelector('.termkeys:not(#controlkeys)');
previewTerminalDock.classList.add('terminal-dock');
previewTerminalDock.innerHTML='<button class="dock-keyboard" title="显示终端虚拟键盘" onclick="scene(&quot;显示键盘&quot;)">'+icon('keyboard')+'<span class="dock-label">键盘</span></button><button class="dock-controls" title="展开或收起终端控制键" onclick="scene(&quot;控制键&quot;)">'+icon('brackets')+'<span class="dock-label">控制键</span></button><span class="dock-spacer"></span><button class="icon" title="复制终端示例记录" onclick="previewCopyTranscript()">'+icon('copy')+'</button><button class="icon" title="滚动到终端底部" onclick="previewTerminalBottom()">'+icon('chevron-down')+'</button>';
function previewTerminalBottom(){const output=document.querySelector('.termcontent');output.scrollTop=output.scrollHeight;}
async function previewCopyTranscript(){
 try{await navigator.clipboard.writeText(document.querySelector('.termcontent').innerText);modal('已复制设计示例记录','<p>复制的是本页示例文字，不是App终端或程序输出。</p>','关闭');}
 catch(error){modal('示例复制不可用','<p>浏览器未允许写入剪贴板，可直接选择示例文字。</p>','关闭');}
}

const previewControlKeys=document.createElement('div');previewControlKeys.id='controlkeys';previewControlKeys.className='termkeys hidden';
['Ctrl+C','Ctrl+D','Tab','Esc','↑','↓','←','→'].forEach(title=>{
 const key=document.createElement('button');key.textContent=title;key.title=title+' · 设计示例';
 key.onclick=()=>document.querySelector('.inputline')?.focus();previewControlKeys.append(key);
});
previewTerminal.append(previewControlKeys);
const previewControlToggle=previewTerminalDock.querySelector('.dock-controls');
function previewControlsSelection(){const expanded=!previewControlKeys.classList.contains('hidden');previewControlToggle.classList.toggle('selected',expanded);previewControlToggle.setAttribute('aria-expanded',String(expanded));}
new MutationObserver(previewControlsSelection).observe(previewControlKeys,{attributes:true,attributeFilter:['class']});previewControlsSelection();

function decorateRunModePreview(){
 const detail=document.querySelector('.settingsdetail');
 if(!detail||detail.querySelector('h3')?.textContent.trim()!=='终端与运行'||detail.querySelector('.run-modes-preview'))return;
 const section=document.createElement('section');section.className='run-modes-preview';
 section.innerHTML='<h4>运行方式</h4><div class="engine-mode"><span class="filemark py" aria-hidden="true">Py</span><div><strong>Python · 本地</strong><p>在当前终端中交互输入</p></div></div><div class="engine-mode"><span class="filemark" aria-hidden="true">C++</span><div><strong>C++17 · 云端</strong><p>预填输入，完成后返回结果。<br>代码和输入会发送至第三方 Wandbox。</p></div></div>';
 detail.append(section);
}
new MutationObserver(decorateRunModePreview).observe($('#layers'),{childList:true,subtree:true});

// Visual example only: dirty markers occupy the same space in clean/modified tabs.
function decoratePreviewTabMarkers(){
 document.querySelectorAll('.tabs .tab').forEach(tab=>{
  if(tab.querySelector('.tab-dirty-indicator'))return;
  const marker=document.createElement('span');marker.className='tab-dirty-indicator';marker.setAttribute('aria-hidden','true');
  const close=tab.lastElementChild;
  if(close&&(close.matches('svg')||close.dataset.icon==='x'||close.hasAttribute('data-preview-close')))tab.insertBefore(marker,close);else tab.append(marker);
 });
}
function previewTabState(){const active=document.querySelector('.tabs .tab.active');document.querySelector('.status .state').textContent=active?.classList.contains('dirty')?'未保存':'已保存';decoratePreviewTreeState();}
const selectFileBeforeMarkers=selectFile;
selectFile=function(name){selectFileBeforeMarkers(name);decoratePreviewTabMarkers();previewTabState();};
const sceneBeforeMarkers=scene;
scene=function(name){
 document.querySelectorAll('.tabs .tab').forEach(tab=>tab.classList.remove('dirty'));
 if(name==='未保存标签'){
  sceneBeforeMarkers('工作台');decoratePreviewTabMarkers();document.querySelector('.tabs .tab.active')?.classList.add('dirty');previewTabState();return;
 }
 const result=sceneBeforeMarkers(name);decoratePreviewTabMarkers();previewTabState();return result;
};
decoratePreviewTabMarkers();previewTabState();$('#scene').add(new Option('未保存标签','未保存标签'));

// Navigation in the design sample reveals the selected tab without requesting keyboard focus.
const selectFileBeforeReveal=selectFile;
selectFile=function(name){
 selectFileBeforeReveal(name);
 const selected=document.querySelector('.tabs .tab.active');
 if(!selected)return;
 if(!selected.querySelector('.filemark')){
  const extension=name.split('.').pop().toLowerCase();const kind=extension==='py'?'py':['h','hpp'].includes(extension)?'h':'';
  const label=extension==='py'?'Py':['h','hpp'].includes(extension)?'H':extension==='cpp'?'C++':'·';
  selected.innerHTML='<span class="filemark '+kind+'">'+label+'</span><span class="tab-filename">'+esc(name)+'</span><span data-preview-close aria-hidden="true">'+icon('x')+'</span>';
  decoratePreviewTabMarkers();
 }
 selected.title=name;
 revealPreviewTabSelection();
};

// Save-failure presentation is a labelled design example, never an execution result.
const previewStateBeforeFailure=previewTabState;
previewTabState=function(){previewStateBeforeFailure();const state=document.querySelector('.status .state');state.classList.remove('failure');state.removeAttribute('title');};
const sceneBeforeFailure=scene;
scene=function(name){
 const failure=name==='保存失败状态';const result=sceneBeforeFailure(failure?'未保存标签':name);
 const state=document.querySelector('.status .state');state.classList.toggle('failure',failure);
 if(failure){state.textContent='保存失败';state.title='未保存 · 保存失败：设计示例，当前编辑仍保留；这不是实际文件操作结果。';}
 return result;
};
const selectFileBeforePathHelp=selectFile;
selectFile=function(name){selectFileBeforePathHelp(name);document.querySelector('.crumb').title='Algorithms / '+name;};
document.querySelector('.crumb').title='Algorithms / '+document.querySelector('.tabs .tab.active').dataset.file;
$('#scene').add(new Option('保存失败状态','保存失败状态'));

function revealPreviewTabSelection(){
 const viewport=document.querySelector('.tabs'),selected=viewport.querySelector('.tab.active');
 if(!selected)return;
 const tab=selected.getBoundingClientRect(),box=viewport.getBoundingClientRect();
 const left=box.left+viewport.clientLeft,right=left+viewport.clientWidth;
 const delta=tab.width>viewport.clientWidth?tab.right-right:tab.left<left?tab.left-left:tab.right>right?tab.right-right:0;
 if(delta)viewport.scrollTo({left:viewport.scrollLeft+delta,behavior:'auto'});
}
let previewTabViewportWidth=document.querySelector('.tabs').clientWidth;
new ResizeObserver(entries=>{
 const width=entries[0].target.clientWidth;
 if(width!==previewTabViewportWidth){previewTabViewportWidth=width;revealPreviewTabSelection();}
}).observe(document.querySelector('.tabs'));

// Measured short-window example only; does not simulate an Android keyboard.
$('#scene').add(new Option('紧凑编辑区','紧凑编辑区'));
const sceneSelectionBeforeCompact=$('#scene').onchange;
$('#scene').onchange=event=>{
 if(event.target.value!=='紧凑编辑区'){previewHeightOverride=null;previewLayout(false);}
 sceneSelectionBeforeCompact(event);
};

// Reflect the labelled dirty-document example without adding a second tree action.
function decoratePreviewTreeState(){
 const current=document.querySelector('.tabs .tab.active');
 const dirtyFile=current?.classList.contains('dirty')?current.dataset.file:null;
 document.querySelectorAll('.treeitem').forEach(row=>{
  const title=row.querySelector('.treename');
  if(!title||!row.querySelector('.filemark'))return;
  const path=title.textContent;row.dataset.previewFile=path;
  if(!row.querySelector('.tree-dirty-indicator')){
   const marker=document.createElement('span');marker.className='tree-dirty-indicator';marker.setAttribute('aria-hidden','true');
   title.after(marker);
  }
  const modified=path===dirtyFile;row.classList.toggle('dirty',modified);
  row.title='Algorithms / '+path+(modified?' · 未保存':'');
  row.setAttribute('aria-label',row.title);
 });
}
decoratePreviewTreeState();

// Context belongs to menu navigation, not the document or terminal session.
let previewMenuParent=null;
const previewMenuHeadings={'文件操作':'文件','编辑菜单':'编辑','视图菜单':'视图','运行菜单':'运行','终端菜单':'终端','帮助菜单':'帮助','终端动作':'终端操作','会话动作':'会话管理','布局与输入':'布局与输入','配色与帮助':'配色与帮助','调整面板尺寸':'面板尺寸','文件菜单':'src/main.py','项目菜单':'资源管理器','终端会话':'终端会话','终端配色':'终端配色'};
const sceneBeforeMenuContext=scene;
scene=function(name){
 const parent=previewMenuParent;previewMenuParent=null;
 const result=sceneBeforeMenuContext(name);const popup=document.querySelector('#layers .popup');
 if(!popup)return result;
 popup.dataset.menuScene=name;
 if(name==='主菜单'||name==='终端操作'||name==='布局与输入'||name==='配色与帮助')popup.querySelectorAll('.menurow').forEach(row=>row.addEventListener('click',()=>{previewMenuParent=name;},{capture:true}));
 const title=previewMenuHeadings[name];if(!title)return result;
 const body=document.createElement('div');body.className='menu-content';body.append(...popup.childNodes);
 const header=document.createElement('div');header.className='menu-heading';
 if(parent==='主菜单'||parent==='终端操作'||parent==='布局与输入'||parent==='配色与帮助'){
  popup.dataset.menuBack=parent;const back=document.createElement('button');back.className='menu-back';back.title=parent==='主菜单'?'返回工作台菜单':parent==='布局与输入'?'返回布局与输入':parent==='配色与帮助'?'返回配色与帮助':'返回终端菜单';back.innerHTML=icon('arrow-left');back.onclick=()=>scene(parent);header.append(back);
 }
 const label=document.createElement('strong');label.className='menu-heading-title';label.textContent=title;label.title=title;header.append(label);
 if(name==='终端会话'||name==='终端配色'){
  const selected=name==='终端会话'?0:['跟随界面','CodePocket Dark','Nord','Dracula','Light'].indexOf(saved.terminal);
  body.querySelectorAll('.menurow').forEach((row,i)=>{row.setAttribute('aria-checked',String(i===selected));if(i===selected)row.querySelector('svg').outerHTML=icon('check');});
 }
 popup.classList.add('has-menu-heading');popup.append(header,body);return result;
};
$('#scene').add(new Option('终端菜单','终端菜单'));$('#scene').add(new Option('调整面板尺寸','调整面板尺寸'));
document.addEventListener('keydown',event=>{
 const popup=document.querySelector('#layers .popup');
 if(event.key==='ArrowLeft'&&popup?.dataset.menuBack){event.preventDefault();scene(popup.dataset.menuBack);}
});

// Design examples only; the Android search continues to use its real workspace worker.
function previewProjectSearch(empty){
 const result=empty?'no_such_token':'greet';
 modal('项目搜索','<div class="search-scope">'+icon('search')+'<span>Algorithms</span></div><div class="search-controls"><input aria-label="项目查找文本" value="'+result+'"><button type="button">搜索</button></div><label class="search-case"><input type="checkbox">区分大小写</label><p class="search-summary">'+(empty?'找到 0 处匹配':'找到 2 处匹配')+'</p><div class="search-results" '+(empty?'hidden':'')+'>'+[2,6].map(line=>'<button class="search-result" onclick="closeLayers()"><span>'+icon('file-code-2')+'main.py:'+line+'</span><code>'+ (line===2?'def greet(name):':'print(greet(name))')+'</code></button>').join('')+'</div>','关闭');
 const form=$('.modal');form.classList.add('project-search');form.querySelector('.actions').firstElementChild.remove();
 form.querySelector('.search-controls button').onclick=()=>scene('项目搜索无匹配');
}
$('#scene').add(new Option('项目搜索无匹配','项目搜索无匹配'));

// Storage selectors remain design-only examples; native Android owns all real transactions.
function previewChoice(key,title,detail,glyph,checked=false,group=false){return '<button class="choice-row '+(group?'choice-group':'')+'" data-choice="'+esc(key)+'" aria-selected="'+checked+'" title="'+esc(title+' · '+detail)+'">'+icon(glyph)+'<span class="choice-words"><strong>'+esc(title)+'</strong><small>'+esc(detail)+'</small></span><span class="choice-check">'+(checked?icon('check'):'')+'</span></button>';}
function previewPicker(title,scope,rows){
 modal(title,'<div class="picker-scope">'+icon('folder-open')+'<span>'+esc(scope)+'</span></div><div class="choice-list">'+rows.join('')+'</div>','取消');
 const form=$('.modal');form.classList.add('storage-picker');const actions=form.querySelector('.actions');actions.firstElementChild.remove();actions.lastElementChild.classList.remove('primary');return form;
}
function previewWorkspacePicker(long){
 const form=previewPicker('工作区','Algorithms',[
  previewChoice('select','选择手机目录…','通过 Android 系统选择项目文件夹','folder-open'),
  previewChoice('internal','内部项目','保存在应用内，卸载会清除','folder'),
  previewChoice('reload','重新载入当前目录','','refresh-cw'),
  previewChoice('current',long?'算法练习_长目录_'.repeat(4):'Algorithms','最近的系统目录','folder-open',true,true),
  previewChoice('python','Python课程','最近的系统目录','folder-open')]);
 form.querySelectorAll('[data-choice]').forEach(row=>row.onclick=()=>{
  if(row.dataset.choice==='select')modal('系统目录权限说明','<p>保存会写回所选目录，授权只限该目录。外部文件由系统提供方保留。</p><p>设计预览，不会启动系统文件选择器。</p>','知道了');else closeLayers();
 });
}
function previewDraftPicker(long){
 const paths=long?Array.from({length:10},(_,i)=>'课程目录_'.repeat(3)+'/章节'+i):['chapter1','chapter2'];
 const form=previewPicker('导出恢复草稿',paths.length+' 个草稿 · 选择后导出',paths.map((path,i)=>previewChoice(String(i),long?'solution.cpp':'solution.py',path,'file-code-2')));
 form.querySelectorAll('[data-choice]').forEach(row=>row.onclick=()=>modal('导出草稿预览','<p>实际 App 将打开 Android 系统保存界面。此样稿只展示操作层级，草稿与当前编辑内容分别保留。</p>','关闭'));
}
function previewMovePicker(parts=[]){
 modal('移动到文件夹','<div class="location-preview">'+icon('file-code-2')+'<div><small>来源</small>Algorithms / main.py</div></div><p class="move-target">目标：'+esc(parts.length?parts.join('/'):'项目根目录')+'</p><div class="move-directories"></div>','移动到此处');
 const form=$('.modal');form.classList.add('move-picker');form.querySelector('.modalheader h2').insertAdjacentHTML('afterbegin','<span class="sectionmark">'+icon('move')+'</span>');const list=form.querySelector('.move-directories');
 if(parts.length){const back=document.createElement('button');back.className='menurow';back.innerHTML=icon('arrow-left')+'<span>返回上一级</span>';back.onclick=()=>previewMovePicker(parts.slice(0,-1));list.append(back);}
 const folders=parts.length===0?['include','examples']:parts.length===1?['算法练习_'.repeat(4)]:[];
 for(const name of folders){const row=document.createElement('button');row.className='menurow';row.innerHTML=icon('folder')+'<span>'+esc(name)+'/</span>';row.onclick=()=>previewMovePicker([...parts,name]);list.append(row);}
 if(!folders.length){const empty=document.createElement('p');empty.className='picker-empty';empty.textContent='此目录没有可选子文件夹';list.append(empty);}
}
for(const name of ['工作区长名称','恢复草稿长路径','移动到深层目录'])$('#scene').add(new Option(name,name));
