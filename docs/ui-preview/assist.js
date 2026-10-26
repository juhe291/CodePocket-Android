/* Design samples only. Native Main/Sora tests own real insertion, selection and undo evidence. */
const sceneBeforeAssistPreview=scene;
scene=function(name){
 const result=sceneBeforeAssistPreview(name);
 if(['补全候选','大字体补全','函数参数提示','多形式参数参考','内置代码片段','片段占位符导航'].includes(name)){
  closeLayers();
  if(name==='补全候选'||name==='大字体补全')return previewCandidates(name==='大字体补全');
  if(name==='函数参数提示'||name==='多形式参数参考')return previewReference(name==='多形式参数参考');
  return previewSnippetChoices(name==='片段占位符导航');
 }
 return result;
};
function assistWindow(html,extra='',preferred=380){
 $('#layers').innerHTML='<section class="popup has-menu-heading assist-preview '+extra+'">'+html+'</section>';
 const popup=$('#layers .popup'),app=previewApp.getBoundingClientRect(),code=$('#code').getBoundingClientRect();
 const anchor=$('#code .caret')?.getBoundingClientRect()||{left:code.left+50,top:code.top+80,bottom:code.top+100};
 const left=Math.max(app.left+8,code.left),right=Math.min(app.right-8,code.right),width=Math.min(preferred,right-left);
 popup.style.width=width+'px';popup.style.right='auto';popup.style.left=Math.max(left-app.left,Math.min(anchor.left-app.left,right-app.left-width))+'px';
 const below=app.bottom-anchor.bottom-12,above=anchor.top-app.top-12,room=Math.max(below,above);
 popup.style.maxHeight=Math.max(48,room)+'px';const height=popup.getBoundingClientRect().height;
 popup.style.top=(below>=height?anchor.bottom-app.top+4:Math.max(8,anchor.top-app.top-height-4))+'px';
 popup.querySelector('[data-close-assist]').onclick=closeLayers;return popup;
}
function assistHeading(title,controls=''){return '<div class="menu-heading"><strong class="menu-heading-title" title="'+esc(title)+'">'+esc(title)+'</strong>'+controls+'<button class="icon" data-close-assist title="关闭参考">'+icon('x')+'</button></div>';}
const candidateExamples=[
 ['pass','关键字','case-sensitive','语言关键字参考；当前为基础词法补全。'],
 ['print','内置/常用名称','brackets','Python 内置名称参考，未验证局部同名对象或当前作用域。'],
 ['project_value','文件中出现的名称','file-text','当前文件出现过此名称，未分析作用域或类型。'],
 ['print · 输出','内置代码片段','file-code-2','内置模板；Tab/Shift+Tab 跳转占位符。']
];
function previewCandidates(large){
 const popup=assistWindow(assistHeading('基础词法补全')+'<div class="candidate-list">'+candidateExamples.map((item,i)=>'<button class="candidate-row" data-candidate="'+i+'" title="'+esc(item[0]+' · '+item[1])+'">'+icon(item[2])+'<code>'+esc(item[0])+'</code><small>'+esc(item[1])+'</small></button>').join('')+'</div><div class="candidate-detail"></div>',large?'assist-large':'');
 popup.dataset.selected='0';const update=()=>{const selected=+popup.dataset.selected;popup.querySelectorAll('[data-candidate]').forEach((row,i)=>{row.classList.toggle('selected',i===selected);row.setAttribute('aria-selected',String(i===selected));});popup.querySelector('.candidate-detail').textContent=candidateExamples[selected][1]+' · '+candidateExamples[selected][3];};
 popup.querySelectorAll('[data-candidate]').forEach(row=>{const kind=row.querySelector('small');if(kind.scrollWidth>popup.clientWidth/3)kind.hidden=true;row.onclick=closeLayers;});popup.updateExample=update;update();
}
function previewReference(multiple){
 const forms=multiple?['range(stop, /)','range(start, stop, step=1, /)']:['print(*objects, sep=" ", end="\\n", file=None, flush=False)'];
 let current=0;const controls=multiple?'<button class="icon" data-reference-prev title="上一参考形式">'+icon('arrow-left')+'</button><button class="icon" data-reference-next title="下一参考形式">'+icon('arrow-right')+'</button>':'';
 const popup=assistWindow(assistHeading('内置签名参考 1/'+forms.length,controls)+'<div class="reference-code"></div><p class="reference-detail">内置文档参考，不解析局部同名绑定或调用类型。</p><div class="reference-notice" title="Python 3.13 文档参考；同名绑定未验证">Python 3.13 文档参考；同名绑定未验证</div>','',460);
 const render=()=>{popup.querySelector('.menu-heading-title').textContent='内置签名参考 '+(current+1)+'/'+forms.length;popup.querySelector('.reference-code').textContent=forms[current];};
 if(multiple){popup.querySelector('[data-reference-prev]').onclick=()=>{current=(current+forms.length-1)%forms.length;render();};popup.querySelector('[data-reference-next]').onclick=()=>{current=(current+1)%forms.length;render();};}render();
}
function previewSnippetChoices(navigation){
 const cpp=document.querySelector('.tab.active')?.dataset.file?.endsWith('.cpp')||false;
 const examples=navigation?[['previous','上一个占位符','','arrow-left'],['next','下一个占位符','','arrow-right'],['finish','结束占位符导航','','check']]:[['for','for',cpp?'范围遍历':'遍历循环','file-code-2'],['if','if','条件分支','file-code-2'],['while','while','循环','file-code-2'],['function',cpp?'func':'def','函数定义','file-code-2'],['output',cpp?'cout':'print','输出','file-code-2'],['main','main',cpp?'主函数':'主模块入口','file-code-2']];
 const form=previewPicker('内置代码片段',(cpp?'C++17':'Python')+' · 内置模板',examples.map(item=>previewChoice(...item)));form.classList.add('snippet-picker');form.querySelector('.picker-scope svg').outerHTML=icon('brackets');form.querySelectorAll('[data-choice]').forEach(row=>row.onclick=closeLayers);
}
document.addEventListener('keydown',event=>{
 const popup=document.querySelector('.assist-preview[data-selected]');if(!popup||event.ctrlKey||event.altKey||event.metaKey)return;
 if(event.key==='ArrowDown'||event.key==='ArrowUp'){event.preventDefault();popup.dataset.selected=String(Math.max(0,Math.min(candidateExamples.length-1,+popup.dataset.selected+(event.key==='ArrowDown'?1:-1))));popup.updateExample();}
 if(event.key==='Tab'&&!event.shiftKey){event.preventDefault();closeLayers();}
});
for(const name of ['大字体补全','多形式参数参考','片段占位符导航'])$('#scene').add(new Option(name,name));

if(![...$('#scene').options].some(option=>option.value==='内置代码片段'))$('#scene').add(new Option('内置代码片段','内置代码片段'));
