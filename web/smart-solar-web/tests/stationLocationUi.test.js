import assert from 'node:assert/strict';
import { test, after } from 'node:test';
import React from 'react';
import { create, act } from 'react-test-renderer';
import { createServer } from 'vite';
const server = await createServer({logLevel:'silent',define:{'import.meta.env.VITE_API_BASE_URL':JSON.stringify('https://api.example.invalid/api/v1')},server:{middlewareMode:true,watch:null,hmr:false}});
const {default: Location, StationMap} = await server.ssrLoadModule('/src/components/StationLocation.jsx');
const {default: ReferencePicker} = await server.ssrLoadModule('/src/components/ReferencePicker.jsx');
after(()=>server.close());
test('existing/manual coordinates and paste helper share controlled fields; invalid paste preserves selection', async()=>{
 let view, point={latitude:6,longitude:79};
 const update=value=>{point=value;};
 const render=()=>React.createElement(Location,{...point,onChange:update});
 await act(async()=>{view=create(render());});
 assert.equal(view.root.findByType(StationMap).props.latitude,6);
 point={latitude:7,longitude:80};
 await act(async()=>{view.update(render());});
 assert.equal(view.root.findByType(StationMap).props.longitude,80);
 const input=()=>view.root.findByType('input');
 await act(async()=>{input().props.onChange({target:{value:'6.9271,79.8612'}});});
 await act(async()=>{view.root.findByType('button').props.onClick();});
 assert.deepEqual(point,{latitude:6.9271,longitude:79.8612});
 await act(async()=>{view.update(render());});
 assert.equal(view.root.findByType(StationMap).props.latitude,6.9271);
 await act(async()=>{input().props.onChange({target:{value:'invalid'}});});
 await act(async()=>{view.root.findByType('button').props.onClick();});
 assert.deepEqual(point,{latitude:6.9271,longitude:79.8612});
 await act(async()=>{view.unmount();});
});
test('reference picker labels are readable while selected values remain real API IDs', async()=>{
 const originalFetch=globalThis.fetch;
 globalThis.window=new EventTarget();globalThis.sessionStorage={getItem:()=>null};
 const id='22222222222222222222222222222222';let selected,view;
 globalThis.fetch=async()=>new Response(JSON.stringify([{stationId:id,name:'Community Solar'}]),{headers:{'Content-Type':'application/json'}});
 try {
  await act(async()=>{view=create(React.createElement(ReferencePicker,{kind:'station',id:'station',value:'',onChange:e=>{selected=e.target.value;}}));});
  await act(async()=>{view.root.findByType('input').props.onFocus();await new Promise(setImmediate);});
  const option=view.root.findAllByType('option').find(node=>node.props.value===id);
  assert.equal(option.children.join(''),'Community Solar · STN-8FHMZDSSO5');
  await act(async()=>{view.root.findByType('select').props.onChange({target:{value:id}});});
  assert.equal(selected,id);
 } finally {if(view) await act(async()=>view.unmount());globalThis.fetch=originalFetch;delete globalThis.window;delete globalThis.sessionStorage;}
});
