import assert from 'node:assert/strict';
import {after,afterEach,beforeEach,test} from 'node:test';
import React from 'react';
import {create,act} from 'react-test-renderer';
import {MemoryRouter} from 'react-router-dom';
import {createServer} from 'vite';
const server=await createServer({logLevel:'silent',define:{'import.meta.env.VITE_API_BASE_URL':JSON.stringify('https://api.example.invalid/api/v1')},server:{middlewareMode:true,watch:null,hmr:false}});
const {default:Recovery}=await server.ssrLoadModule('/src/pages/PasswordRecoveryPage.jsx');
after(()=>server.close());
const original=globalThis.fetch;
let view,calls;
function text(node){return typeof node==='string'?node:(node?.children??[]).map(text).join('');}
beforeEach(()=>{
  calls=[];globalThis.window=new EventTarget();
  window.location={hash:'#token='+'a'.repeat(64),pathname:'/reset-password'};
  window.history={replaceState:()=>{window.location.hash='';}};
  globalThis.sessionStorage={getItem:()=>null};
  globalThis.fetch=async(url,options)=>{calls.push({url,options});return new Response('{"message":"Generic confirmation"}',{headers:{'content-type':'application/json'}});};
});
afterEach(async()=>{if(view)await act(async()=>view.unmount());view=null;globalThis.fetch=original;delete globalThis.window;delete globalThis.sessionStorage;});
async function mount(reset){await act(async()=>{view=create(React.createElement(MemoryRouter,null,React.createElement(Recovery,{reset})));});}
async function fill(id,value){await act(async()=>view.root.findByProps({id}).props.onChange({target:{value}}));}
async function submit(){await act(async()=>view.root.findByType('form').props.onSubmit({preventDefault(){}}));}
test('recovery submits only identifier and displays generic confirmation',async()=>{
  await mount(false);await fill('recovery-identifier','fixture@example.invalid');await submit();
  assert.deepEqual(JSON.parse(calls[0].options.body),{identifier:'fixture@example.invalid'});
  assert.equal(calls[0].options.headers.get('Authorization'),null);
  assert.match(text(view.root),/If an eligible account exists/);
});
test('reset strips fragment and mismatch sends no request',async()=>{
  await mount(true);assert.equal(window.location.hash,'');
  await fill('new-password','new-fixture-password');await fill('confirm-password','different-password');await submit();
  assert.equal(calls.length,0);assert.match(text(view.root),/Passwords do not match/);
});
test('reset submits token only in POST body and never automatically logs in',async()=>{
  await mount(true);await fill('new-password','new-fixture-password');await fill('confirm-password','new-fixture-password');await submit();
  assert.equal(calls.length,1);assert.ok(calls[0].url.endsWith('/auth/reset-password'));
  assert.equal(JSON.parse(calls[0].options.body).token,'a'.repeat(64));
  assert.match(text(view.root),/Sign in with your new password/);
});
test('invalid reset response offers a fresh link request',async()=>{
  globalThis.fetch=async()=>new Response('{"detail":"This password reset link is invalid or has expired."}',{status:400,headers:{'content-type':'application/problem+json'}});
  await mount(true);await fill('new-password','new-fixture-password');await fill('confirm-password','new-fixture-password');await submit();
  assert.match(text(view.root),/invalid or has expired/);assert.ok(view.root.findAllByType('a').some(x=>x.props.href==='/forgot-password'));
});
test('missing reset token blocks submission',async()=>{
  window.location.hash='';await mount(true);assert.equal(view.root.findAllByType('form').length,0);assert.match(text(view.root),/invalid or has expired/);
});
