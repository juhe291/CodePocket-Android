async (page) => {
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.setViewportSize({width:1440,height:1000});await page.reload();
 const failures=[];let cases=0;let checks=0;let paletteChecks=0;
 const devices=['tablet','tabletPortrait','phoneLandscape','phone'];
 const scenes=await page.locator('#scene option').evaluateAll(xs=>xs.map(x=>x.value));
 for(const device of devices)for(const theme of ['dark','light']){
  await page.locator('#device').selectOption(device);
  const isLight=await page.locator('body').evaluate(e=>e.classList.contains('light'));
  if(isLight!==(theme==='light'))await page.locator('#theme').click();
  if(theme==='light'){
   const colors=await page.evaluate(()=>Object.fromEntries(['kw','type','str','fn','comment'].map(c=>[c,getComputedStyle(document.querySelector('#code .'+c)).color])));
   const expected={kw:'rgb(126, 78, 148)',type:'rgb(40, 107, 125)',str:'rgb(142, 70, 48)',fn:'rgb(121, 94, 38)',comment:'rgb(97, 118, 103)'};
   for(const [role,color] of Object.entries(expected)){paletteChecks++;if(colors[role]!==color)failures.push({device,theme,role,color:colors[role],expected:color});}
  }
  for(const name of scenes){
   await page.locator('#scene').selectOption(name);await page.waitForTimeout(12);cases++;
   const result=await page.evaluate(()=>{
    const app=document.querySelector('#app'),a=app.getBoundingClientRect();const bad=[];
    if(document.documentElement.scrollWidth>innerWidth+1)bad.push('document horizontal overflow');
    if(app.scrollWidth>app.clientWidth+1)bad.push('workbench horizontal overflow');
    const vis=e=>getComputedStyle(e).display!=='none'&&e.getBoundingClientRect().width>0;
    for(const el of document.querySelectorAll('.popup,.modal,.settings,.top>button,.termhead button')){
     if(!vis(el))continue;const r=el.getBoundingClientRect();
     if(r.left<a.left-1||r.right>a.right+1)bad.push('outside: '+(el.title||el.className));
    }
    const rail=document.querySelector('.activity').getBoundingClientRect(),footer=document.querySelector('.status').getBoundingClientRect();
    if(Math.abs(rail.bottom-footer.bottom)>1)bad.push('activity rail height');
    const terminal=document.querySelector('#terminal'),w=document.querySelector('.workspace').getBoundingClientRect();
    if(vis(terminal)){const t=terminal.getBoundingClientRect();if(Math.abs(t.left-w.left)>1||Math.abs(t.right-w.right)>1)bad.push('terminal width');}
    if(document.querySelector('.edittools [title="编辑操作"]'))bad.push('duplicate editor more');
    if(document.querySelector('.top .projectSearch'))bad.push('duplicate project search');
    return bad;
   });
   result.forEach(x=>failures.push({device,theme,scene:name,issue:x}));
   if(name==='保存失败状态')await page.screenshot({path:`output/playwright/path-status-2026-10-24/${device}-${theme}-save-failure.png`,fullPage:true});
   if(name==='未保存标签')await page.screenshot({path:`output/playwright/path-status-2026-10-24/${device}-${theme}-dirty-tabs.png`,fullPage:true});
   if(name==='编辑菜单')await page.screenshot({path:`output/playwright/path-status-2026-10-24/${device}-${theme}-edit-menu.png`,fullPage:true});
   if(name==='打开工作区')await page.screenshot({path:`output/playwright/path-status-2026-10-24/${device}-${theme}-startup.png`,fullPage:true});
   if(name==='工作台'||name==='设置')await page.screenshot({path:`output/playwright/path-status-2026-10-24/${device}-${theme}-${name==='工作台'?'workbench':'settings'}.png`,fullPage:true});
  }
 }
 const check=(ok,name)=>{checks++;if(!ok)failures.push({behavior:name});};
 await page.locator('#device').selectOption('phone');await page.locator('#scene').selectOption('工作区载入失败');
 await page.locator('.modal .actions button').filter({hasText:'内部项目'}).click();
 check(await page.locator('.workspace-state').count()===0,'failure preview internal project returns to workbench example');
 await page.locator('#scene').selectOption('工作区载入失败');
 await page.locator('.modal .actions button').filter({hasText:'选择目录'}).click();
 check(await page.locator('.modal').isVisible()&&await page.locator('.workspace-state').count()===0,'failure preview reauthorize opens workspace selector example');
 await page.locator('#device').selectOption('phone');await page.locator('#scene').selectOption('工作台');
 await page.locator('.activity [title="项目文件"]').click();
 const expanded=await page.locator('#root').getAttribute('aria-expanded');await page.locator('#root').click();
 check(await page.locator('#root').getAttribute('aria-expanded')!==expanded,'root toggles');await page.locator('#root').click();
 const include=page.locator('.treeitem').filter({hasText:'include'});await include.click();
 check(await page.locator('.nested-preview').isVisible(),'nested directory expands');await include.click();
 check(!(await page.locator('.nested-preview').isVisible()),'nested directory collapses');
 await page.locator('.treeitem').filter({hasText:'solution.cpp'}).click();
 check((await page.locator('#code').innerText()).includes('std::cout'),'real preview C++ sample selection');
 check(!(await page.locator('#explorer').evaluate(e=>e.classList.contains('open'))),'file selection closes phone drawer');
 await page.locator('.top [title="打开终端"]').click();const terminal=await page.locator('#terminal').elementHandle();
 await page.locator('.top [title="打开终端"]').click();
 check(await terminal.evaluate(e=>e===document.querySelector('#terminal')),'terminal entry keeps same DOM');
 check(!(await page.locator('.inputline').evaluate(e=>e===document.activeElement)),'terminal entry does not steal input focus');
 await page.locator('.termhead [title="关闭当前终端会话"]').click();
 check(await page.locator('.modalheader').innerText()==='结束会话','close session shortcut confirmation');
 await page.locator('.modal .actions button').first().click();
 check(await page.locator('#terminal').isVisible(),'cancel keeps terminal preview');
 const lightBefore=await page.locator('body').evaluate(e=>e.classList.contains('light'));
 await page.locator('#scene').selectOption('设置');await page.locator('.settingsdetail [data-cat="0"]').click();
 await page.locator('.settings select[data-key="theme"]').selectOption(lightBefore?'深色':'浅色');await page.locator('#settingscancel').click();
 check(await page.locator('body').evaluate(e=>e.classList.contains('light'))===lightBefore,'settings cancel retains current theme');
 await page.locator('#scene').selectOption('设置');await page.locator('.settingsdetail [data-cat="0"]').click();
 await page.locator('.settings select[data-key="theme"]').selectOption('浅色');await page.locator('#settingsapply').click();
 check(await page.locator('body').evaluate(e=>e.classList.contains('light')),'settings apply changes theme');
 await page.emulateMedia({reducedMotion:'reduce'});
 check(await page.locator('.caret').first().evaluate(e=>getComputedStyle(e).animationName)==='none','reduced motion preview');
 await page.emulateMedia({reducedMotion:'no-preference'});
 await page.setViewportSize({width:360,height:800});await page.locator('#device').selectOption('phone');await page.locator('#scene').selectOption('工作台');
 check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'360px browser width');
 await page.setViewportSize({width:320,height:900});await page.locator('#device').selectOption('phone');await page.locator('#scene').selectOption('编辑菜单');
 const redo=page.locator('.menurow').filter({hasText:'重做'});
 const titleBox=await redo.locator('.label').boundingBox(),shortcutBox=await redo.locator('small').boundingBox();
 check(shortcutBox.y>=titleBox.y+titleBox.height,'narrow edit preview stacks shortcut below full command');
 check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'320px browser menu width');
 await page.setViewportSize({width:360,height:900});await page.locator('#device').selectOption('phone');await page.locator('#scene').selectOption('工作台');await page.locator('.top [title="打开终端"]').click();
 const dock=page.locator('.terminal-dock');
 check(await dock.locator('button').count()===4,'terminal dock keeps four existing actions');
 const labelHidden=await dock.locator('.dock-label').first().evaluate(e=>getComputedStyle(e).display==='none');
 check(labelHidden,'narrow terminal preview uses icon controls');
 const keyToggle=dock.locator('[title="展开或收起终端控制键"]');await keyToggle.click();
 check(await page.locator('#controlkeys').isVisible(),'terminal control row opens');await keyToggle.click();
 check(!(await page.locator('#controlkeys').isVisible()),'terminal control row closes');
 await page.setViewportSize({width:1440,height:1000});await page.locator('#device').selectOption('tablet');await page.locator('#scene').selectOption('设置');await page.locator('.settingsnav [data-cat="4"]').click();
 await page.locator('.run-modes-preview').waitFor();
 check((await page.locator('.engine-mode').count())===2,'run settings separate existing Python and C++ modes');
 check((await page.locator('.run-modes-preview').innerText()).includes('代码和输入会发送至第三方 Wandbox'),'run settings retain code and stdin disclosure');
 check(!(await page.locator('.settingsdetail').innerText()).includes('优先左右分屏'),'run settings do not promise unsupported side docking');
 await page.screenshot({path:'output/playwright/path-status-2026-10-24/tablet-run-modes.png',fullPage:true});
 await page.locator('#scene').selectOption('工作台');
 const cleanTabs=await page.locator('.tabs .tab').evaluateAll(xs=>xs.map(x=>{const r=x.getBoundingClientRect();return {left:r.left,width:r.width,text:x.textContent}}));
 await page.locator('#scene').selectOption('未保存标签');
 const dirtyTabs=await page.locator('.tabs .tab').evaluateAll(xs=>xs.map(x=>{const r=x.getBoundingClientRect();return {left:r.left,width:r.width,text:x.textContent}}));
 check(JSON.stringify(cleanTabs)===JSON.stringify(dirtyTabs),'dirty sample retains tab title text and geometry');
 check(await page.locator('.tab.dirty .tab-dirty-indicator').isVisible()&&(await page.locator('.status .state').innerText())==='未保存','dirty sample shows marker and truthful example status');
 await page.locator('#scene').selectOption('工作台');
 check(await page.locator('.tab.dirty').count()===0&&(await page.locator('.status .state').innerText())==='已保存','leaving dirty sample clears only example state');
 await page.setViewportSize({width:1440,height:1000});await page.locator('#device').selectOption('phone');await page.locator('#scene').selectOption('工作台');
 await page.evaluate(()=>{for(let i=1;i<=8;i++)selectFile('practice_'+i+'.py');});
 const activeBounds=await page.locator('.tabs .tab.active').boundingBox(),viewportBounds=await page.locator('.tabs').boundingBox();
 check(activeBounds.x>=viewportBounds.x-1&&activeBounds.x+activeBounds.width<=viewportBounds.x+viewportBounds.width+1,'opening a later example file reveals its complete tab');
 await page.screenshot({path:'output/playwright/path-status-2026-10-24/phone-overflow-tabs.png',fullPage:true});
 await page.locator('.tabs').evaluate(e=>e.scrollLeft=0);await page.evaluate(()=>{decoratePreviewTabMarkers();previewTabState();});
 check(await page.locator('.tabs').evaluate(e=>e.scrollLeft)===0,'decoration-only sample refresh preserves manual tab scrolling');
 await page.evaluate(()=>selectFile('practice_8.py'));
 check(await page.locator('.tabs').evaluate(e=>e.scrollLeft)>0,'explicitly reopening the current example reveals its tab');
 await page.locator('#device').selectOption('tablet');await page.evaluate(()=>selectFile('very_long_example_filename_'.repeat(12)+'.py'));
 const closeBounds=await page.locator('.tabs .tab.active [data-preview-close]').boundingBox(),wideBounds=await page.locator('.tabs').boundingBox();
 const longTabBounds=await page.locator('.tabs .tab.active').boundingBox();
 check(longTabBounds.x>=wideBounds.x-1&&longTabBounds.x+longTabBounds.width<=wideBounds.x+wideBounds.width+1&&closeBounds.x+closeBounds.width<=wideBounds.x+wideBounds.width+1,'long example name keeps its type icon, filename and close glyph visible');
 await page.screenshot({path:'output/playwright/path-status-2026-10-24/tablet-overflow-long-name.png',fullPage:true});
 await page.locator('#device').selectOption('phone');
 await page.waitForFunction(()=>{const a=document.querySelector('.tabs .tab.active').getBoundingClientRect(),v=document.querySelector('.tabs').getBoundingClientRect();return a.left>=v.left-1&&a.right<=v.right+1;});
 check(await page.locator('.tabs').evaluate(e=>e.scrollLeft)>0,'resizing the design viewport reveals its existing current tab');
 await page.locator('#scene').selectOption('保存失败状态');
 check((await page.locator('.status .state').innerText())==='保存失败'&&await page.locator('.tab.dirty').count()===1,'save-failure design prioritizes failure while keeping the dirty example');
 const dirtyMarker=await page.locator('.tabs .tab.active .tab-dirty-indicator').boundingBox(),separateClose=await page.locator('.tabs .tab.active [data-preview-close]').boundingBox();
 check(dirtyMarker.x+dirtyMarker.width<=separateClose.x,'new example tabs keep their dirty marker before the separate close glyph');
 check((await page.locator('.status .state').getAttribute('title')).includes('未保存')&&await page.locator('.status .state').evaluate(e=>getComputedStyle(e).color!==getComputedStyle(e.parentElement).color),'save-failure example retains full explanation and distinct muted error tone');
 await page.locator('#scene').selectOption('工作台');
 check(await page.locator('.status .state.failure').count()===0&&(await page.locator('.status .state').innerText())!=='保存失败','leaving failure example clears its presentation state');
 const currentFile=await page.locator('.tabs .tab.active').getAttribute('data-file');
 check((await page.locator('.crumb').getAttribute('title')).includes(currentFile),'truncated path retains its full example explanation');
 await page.locator('#scene').selectOption('保存失败状态');await page.evaluate(()=>{document.querySelector('.crumb').style.fontSize='22px';document.querySelector('.status').style.fontSize='22px';});
 check(await page.evaluate(()=>['.crumb','.status'].every(s=>{const e=document.querySelector(s);return e.scrollHeight<=e.clientHeight+1&&e.getBoundingClientRect().height>=38;})),'large example text grows path and status heights without vertical clipping');
 await page.screenshot({path:'output/playwright/path-status-2026-10-24/phone-large-status-design.png',fullPage:true});
 return {cases,checks,paletteChecks,scenes:scenes.length,devices:devices.length,themes:2,errors,failures,androidDeviceVerified:false};
}
