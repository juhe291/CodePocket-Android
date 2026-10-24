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
   if(name==='打开工作区')await page.screenshot({path:`output/playwright/startup-ui-2026-10-24/${device}-${theme}-startup.png`,fullPage:true});
   if(name==='工作台'||name==='设置')await page.screenshot({path:`output/playwright/startup-ui-2026-10-24/${device}-${theme}-${name==='工作台'?'workbench':'settings'}.png`,fullPage:true});
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
 return {cases,checks,paletteChecks,scenes:scenes.length,devices:devices.length,themes:2,errors,failures,androidDeviceVerified:false};
}
