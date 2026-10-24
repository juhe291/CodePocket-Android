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
const menuGroups=[['文件','文件操作','files'],['编辑','编辑菜单','pencil'],['视图','视图菜单','maximize-2'],['运行','运行菜单','play'],['终端','终端操作','terminal'],['帮助','帮助菜单','circle-help']];
desktopMenu.innerHTML=menuGroups.map(g=>`<button onclick="scene('${g[1]}')">${g[0]}</button>`).join('');$('.top').prepend(desktopMenu);
const previewSizes={tablet:[1280,800,'平板横屏'],tabletPortrait:[800,1200,'平板竖屏'],phoneLandscape:[840,411,'手机横屏'],phone:[411,891,'手机竖屏']};
$('#device').innerHTML=Object.entries(previewSizes).map(([k,v])=>`<option value="${k}">${v[2]} · ${v[0]} × ${v[1]}</option>`).join('');
function previewLayout(reset=false){
 const size=previewSizes[$('#device').value];previewApp.style.width=size[0]+'px';previewApp.style.height=size[1]+'px';
 const width=previewApp.clientWidth,height=previewApp.clientHeight;const drawer=width<600||height<480;
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
 case '项目菜单':return menu([['新建文件夹','新建文件夹','folder-plus'],'-',['定位当前文件','工作台','crosshair'],['折叠全部目录','折叠全部','list-collapse'],'-',['打开 / 切换工作区','切换项目','folder-open'],['恢复草稿','恢复草稿','refresh-cw']]);
 case '折叠全部':if(rootOpen)toggleRoot();return;
 case '终端操作':return menu([['终端操作','终端动作','terminal'],['会话管理','会话动作','files'],['布局与输入','布局与输入','maximize-2'],['配色与帮助','配色与帮助','palette']]);
 case '终端动作':return menu([['新建终端','终端会话','file-plus-2'],['复制记录','工作台','copy'],['粘贴','工作台','clipboard'],['清空显示','工作台','eraser']]);
 case '会话动作':return menu([['选择会话','终端会话','terminal'],['重命名会话','重命名','pencil'],'-',['关闭当前会话','结束会话','trash-2',true]]);
 case '布局与输入':return menu([['显示键盘','显示键盘','keyboard'],['控制键','控制键','keyboard'],['最大化 / 恢复','最大化 / 恢复','maximize-2'],['收起终端','收起终端','chevron-down']]);
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
const categoryHints=['主题与配色','字号、缩进与显示','候选行为与按键','自动保存与恢复','终端配色与布局','光标与界面过渡','说明与开源组件'];
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
