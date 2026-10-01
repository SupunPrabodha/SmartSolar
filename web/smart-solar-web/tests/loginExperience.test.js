import test from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { build } from 'esbuild';
import React from 'react';
import { act, create } from 'react-test-renderer';
import { MemoryRouter, useLocation } from 'react-router-dom';

const bundle=await build({
 stdin:{contents:`export {default as Login} from './src/pages/LoginPage'; export {FeedbackHost} from './src/components/Feedback'; export {clearFeedback} from './src/util/feedback';`,resolveDir:process.cwd(),loader:'jsx'},
 bundle:true,write:false,platform:'node',format:'cjs',packages:'external',jsx:'automatic',
 plugins:[{name:'login-session',setup(b){
  b.onResolve({filter:/auth\/AuthContext$/},()=>({path:'auth',namespace:'fixture'}));
  b.onLoad({filter:/.*/,namespace:'fixture'},()=>({contents:'export const useAuth=()=>globalThis.loginFixture;'}));
 }}]
});
const compiled={exports:{}};
new Function('module','exports','require',bundle.outputFiles[0].text)(compiled,compiled.exports,createRequire(import.meta.url));
const {Login,FeedbackHost,clearFeedback}=compiled.exports;
function Location(){return React.createElement('output',{id:'location'},useLocation().pathname);}
async function mount(login){
 globalThis.loginFixture={user:null,loading:false,sessionError:'',login};
 globalThis.requestAnimationFrame=fn=>{fn();return 0;};
 let view;
 await act(async()=>{view=create(React.createElement(MemoryRouter,{initialEntries:['/login']},
  React.createElement(Login),React.createElement(FeedbackHost),React.createElement(Location)));});
 return view;
}
const json=view=>JSON.stringify(view.toJSON());
async function fill(view){
 await act(async()=>{view.root.findByProps({id:'nic'}).props.onChange({target:{value:'fixture'}});
 view.root.findByProps({id:'password'}).props.onChange({target:{value:'fixture-password'}});});
}
async function submit(view){await act(async()=>{await view.root.findByType('form').props.onSubmit({preventDefault(){}});});}
async function dispose(view){await act(async()=>{view.unmount();clearFeedback();});delete globalThis.requestAnimationFrame;}
for(const status of [400,401,403])test('login '+status+' shows one generic feedback alert without diagnostics',async()=>{
 const view=await mount(async()=>{throw Object.assign(new Error('Unknown NIC. Reference: secret-diagnostic'),{status,traceId:'hidden-trace'});});
 try{await fill(view);await submit(view);
  assert.equal(view.root.findAllByProps({role:'alert'}).length,1);
  assert.match(json(view),/Sign-in failed/);assert.match(json(view),/Check your NIC and password and try again/);
  assert.doesNotMatch(json(view),/secret-diagnostic|Reference:|hidden-trace|Unknown NIC/);
  assert.equal(view.root.findByProps({id:'password'}).props.value,'');
 }finally{await dispose(view);}
});
for(const error of [{status:503},{unavailable:true},{status:429}])test('operational login failure '+JSON.stringify(error)+' stays distinct',async()=>{
 const view=await mount(async()=>{throw Object.assign(new Error('raw implementation detail'),error);});
 try{await fill(view);await submit(view);
  assert.match(json(view),error.status===429?/Too many sign-in attempts/:/couldn't reach Smart Solar/);
  assert.doesNotMatch(json(view),/Check your NIC and password|raw implementation detail/);
 }finally{await dispose(view);}
});
test('password reveal, persistent labels, semantic heading and recovery link are retained',async()=>{
 const view=await mount(async()=>{});
 try{
  assert.equal(view.root.findByType('h1').children.join(''),'Sign in to your workspace');
  assert.equal(view.root.findByProps({htmlFor:'nic'}).children.join(''),'NIC');
  assert.equal(view.root.findByProps({id:'password'}).props.autoComplete,'current-password');
  await act(async()=>view.root.findByProps({'aria-label':'Show password'}).props.onClick());
  assert.equal(view.root.findByProps({id:'password'}).props.type,'text');
  await act(async()=>view.root.findByProps({'aria-label':'Hide password'}).props.onClick());
  assert.equal(view.root.findByProps({id:'password'}).props.type,'password');
  assert.equal(view.root.findAllByProps({href:'/forgot-password'}).length,1);
  assert.match(json(view),/login-story/);assert.match(json(view),/Prosumers use the Android app/);
  const motif=view.root.findByProps({className:'login-grid-motif'});
  assert.equal(motif.props['aria-hidden'],'true');
  assert.equal(motif.props.focusable,'false');
 }finally{await dispose(view);}
});
test('pending login disables controls, blocks duplicate submit and keeps successful workspace redirect',async()=>{
 let resolve,calls=0;const view=await mount(()=>{calls++;return new Promise(done=>{resolve=done;});});
 try{
  await fill(view);let pending;
  await act(async()=>{pending=view.root.findByType('form').props.onSubmit({preventDefault(){}});});
  assert.equal(view.root.findByProps({id:'nic'}).props.disabled,true);
  assert.match(json(view),/Signing in/);
  await submit(view);assert.equal(calls,1);
  await act(async()=>{resolve({role:'GridOperator'});await pending;});
  assert.equal(view.root.findByProps({id:'location'}).children.join(''),'/');
  assert.equal(view.root.findByProps({id:'nic'}).props.disabled,false);
 }finally{await dispose(view);}
});
