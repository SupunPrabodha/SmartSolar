import test from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { build } from 'esbuild';
import React from 'react';
import { act, create } from 'react-test-renderer';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';

const bundle = await build({
  stdin: { contents: `export * from './src/components/LoadingExperience';
    export { default as Guard } from './src/routes/ProtectedRoute';
    export { ExperienceProvider, InboxContents, RecentActivity } from './src/components/Experience';`,
    resolveDir: process.cwd(), loader:'jsx' },
  bundle:true, write:false, platform:'node', format:'cjs', packages:'external', jsx:'automatic',
  plugins:[{name:'presentation-fixtures',setup(b) {
    b.onResolve({filter:/auth\/AuthContext$/},()=>({path:'session',namespace:'fixture'}));
    b.onResolve({filter:/api\/apiClient$/},()=>({path:'api',namespace:'fixture'}));
    b.onLoad({filter:/.*/,namespace:'fixture'},args=>({contents:args.path==='session'
      ? 'export const useAuth=()=>globalThis.loadingSession;'
      : 'export const apiFetch=(...args)=>globalThis.loadingRequest(...args);'}));
  }}]
});
const compiled={exports:{}};
new Function('module','exports','require',bundle.outputFiles[0].text)(compiled,compiled.exports,createRequire(import.meta.url));
const {Guard,BootScreen,MetricLoadingGrid,SkeletonRegion,ActionLabel,ExperienceProvider,InboxContents,RecentActivity}=compiled.exports;
const render=(C,props)=>renderToStaticMarkup(React.createElement(C,props));
test('session restoration uses branded boot and subsequently releases protected content',()=>{
  globalThis.loadingSession={loading:true,user:null};
  const children=React.createElement('p',null,'Verified content');
  const boot=renderToStaticMarkup(Guard({children}));
  assert.match(boot,/SMART SOLAR/);assert.match(boot,/Restoring your workspace/);
  assert.match(boot,/aria-busy="true"/);assert.doesNotMatch(boot,/Loading session|Verified content/);
  globalThis.loadingSession={loading:false,user:{role:'GridOperator'}};
  assert.equal(Guard({children}),children);
  globalThis.loadingSession={loading:false,user:null};
  assert.equal(Guard({children}).props.to,'/login');
});
test('metric skeletons preserve known labels without a fake numeric value',()=>{
  const html=render(MetricLoadingGrid,{labels:['Pending Reservations','Approved Future Reservations']});
  assert.match(html,/Pending Reservations/);assert.match(html,/Approved Future Reservations/);
  assert.match(html,/Loading dashboard/);assert.match(html,/aria-hidden="true"/);
  assert.doesNotMatch(html,/•••|metric-value/);
});
test('data skeleton exposes one meaningful status and hides decorative geometry',()=>{
  const html=render(SkeletonRegion,{label:'Loading stations',rows:3});
  assert.equal((html.match(/role="status"/g)||[]).length,1);
  assert.equal((html.match(/class="skeleton-row"/g)||[]).length,3);
  assert.match(html,/aria-busy="true"/);assert.match(html,/aria-hidden="true"/);
});
test('action progress stays inside the button label',()=>{
  assert.match(render(ActionLabel,{busy:true,pending:'Saving…'}),/Saving…/);
  assert.doesNotMatch(render(ActionLabel,{busy:false,children:'Save profile'}),/action-progress/);
});
for(const outcome of ['populated','empty','error'])test('notification skeleton transitions to '+outcome+' without changing the request',async()=>{
  const originalWindow=globalThis.window;globalThis.window=new EventTarget();
  globalThis.loadingSession={user:{nic:'fixture',fullName:'Fixture',role:'GridOperator'},sessionRevision:0};
  let settle,fail,calls=0,view;
  globalThis.loadingRequest=(url)=>{assert.equal(url,'/notifications');calls++;return new Promise((resolve,reject)=>{settle=resolve;fail=reject;});};
  try{
    await act(async()=>{view=create(React.createElement(MemoryRouter,null,React.createElement(ExperienceProvider,null,
      React.createElement(InboxContents),React.createElement(RecentActivity))));});
    assert.equal(view.root.findAllByType(SkeletonRegion).length,2);
    await act(async()=>{
      if(outcome==='error')fail(new Error('Connection unavailable'));
      else settle({unreadCount:outcome==='populated'?1:0,items:outcome==='populated'?[{id:'fixture',priority:'Low',category:'Security',message:'Fixture update',atUtc:'2026-01-01T00:00:00Z'}]:[]});
    });
    assert.equal(calls,1);assert.equal(view.root.findAllByType(SkeletonRegion).length,0);
    const html=JSON.stringify(view.toJSON());
    assert.match(html,outcome==='error'?/Connection unavailable/:outcome==='empty'?/caught up/:/Fixture update/);
    if(outcome==='error')assert.match(html,/Retry/);
  }finally{if(view)await act(async()=>view.unmount());globalThis.window=originalWindow;}
});
