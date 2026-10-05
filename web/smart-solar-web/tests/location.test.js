import assert from 'node:assert/strict';
import { test } from 'node:test';
import { parseLocation, validLocation, googleMapsUrl } from '../src/util/location.js';
import { createStationMap } from '../src/components/stationMap.js';

for (const value of ['6.9271,79.8612','https://www.google.com/maps/@6.9271,79.8612,15z',
  'https://maps.google.com/?q=6.9271,79.8612','https://www.google.com/maps/search/?query=6.9271%2C79.8612',
  'https://www.google.com/maps/place/Test/data=!3d6.9271!4d79.8612']) {
  test('extracts location: ' + value, () => assert.deepEqual(parseLocation(value), {latitude:6.9271,longitude:79.8612}));
}
test('place pin wins over camera center; zero and negative boundary coordinates remain valid', () => {
  assert.deepEqual(parseLocation('https://www.google.com/maps/@1,2,15z/data=!3d6.9271!4d79.8612'), {latitude:6.9271,longitude:79.8612});
  assert.deepEqual(parseLocation('-90,180'), {latitude:-90,longitude:180});
  assert.deepEqual(validLocation('0','0'), {latitude:0,longitude:0});
});
test('rejects invalid ranges, malformed coordinates and unrelated or unsafe URLs', () => {
  for (const value of ['', 'abc', '91,1', '1,-181', 'Infinity,0', '1e2,0', '1,2 extra',
    'https://evil.invalid/?q=1,2', 'https://google.com.evil.invalid/?q=1,2',
    'javascript:alert(1)', 'https://user:password@google.com/?q=1,2', 'https://google.com/maps/%ZZ']) {
    assert.throws(() => parseLocation(value), undefined, value);
  }
  assert.equal(validLocation('', ''), null);
  assert.equal(googleMapsUrl('', ''), null);
  assert.match(googleMapsUrl(6.9271,79.8612), /query=6.9271%2C79.8612$/);
});
test('short links provide safe guidance without network fetching or changing selected coordinates', () => {
  let selected = {latitude:1,longitude:2};
  for (const url of ['https://maps.app.goo.gl/example','https://goo.gl/maps/example']) {
    assert.throws(() => { selected = parseLocation(url); }, /Open the shortened link in Google Maps/);
    assert.deepEqual(selected, {latitude:1,longitude:2});
  }
});
function fakeLeaflet() {
  const events = {}, markerEvents = {};
  const map = {on:(name, callback)=>{events[name]=callback;},fitWorld(){this.world=true;},
    removeLayer(){this.removed=true;},setView(point){this.center=point;},invalidateSize(){},panTo(point){this.center=point;},remove(){this.destroyed=true;}};
  const marker = {point:null,on:(name,callback)=>{markerEvents[name]=callback;},addTo(){return this;},
    setLatLng(point){this.point=point;},getLatLng(){return {lat:this.point[0],lng:this.point[1]};},
    dragging:{enable(){marker.draggable=true;},disable(){marker.draggable=false;}}};
  return {map,marker,events,markerEvents,L:{map:()=>map,divIcon:options=>options,
    tileLayer:(url,options)=>({addTo(){assert.equal(url,'https://tile.openstreetmap.org/{z}/{x}/{y}.png');assert.match(options.attribution,/OpenStreetMap/);return this;},on(){}}),
    marker:(point)=>{marker.point=point;return marker;}}};
}
test('empty map is neutral; existing/manual coordinates initialize and move preview; invalid clears pin', () => {
  const f=fakeLeaflet(), adapter=createStationMap(f.L,{}, {onSelect:()=>{},onTileError:()=>{}});
  assert.equal(f.map.world,true); assert.equal(f.marker.point,null);
  adapter.update(validLocation(6,79)); assert.deepEqual(f.marker.point,[6,79]);assert.deepEqual(f.map.center,[6,79]);
  adapter.update(validLocation('7','80'));assert.deepEqual(f.marker.point,[7,80]);assert.deepEqual(f.map.center,[7,80]);
  adapter.update(null);assert.equal(f.map.removed,true);
  adapter.destroy();assert.equal(f.map.destroyed,true);
});
test('click and drag feed the same coordinate fields; locked/read-only maps cannot edit', () => {
  const f=fakeLeaflet();let fields={};
  const adapter=createStationMap(f.L,{}, {onSelect:value=>{fields=value;adapter.update(value);},onTileError:()=>{}});
  f.events.click({latlng:{lat:6,lng:79}});assert.deepEqual(fields,{latitude:6,longitude:79});
  f.marker.point=[7,80];f.markerEvents.dragend();assert.deepEqual(fields,{latitude:7,longitude:80});assert.deepEqual(f.map.center,[7,80]);
  adapter.update(fields,true);f.events.click({latlng:{lat:8,lng:81}});assert.equal(fields.latitude,7);assert.equal(f.marker.draggable,false);
  const read=fakeLeaflet();createStationMap(read.L,{}, {onTileError:()=>{}}).update(fields);
  assert.equal(read.marker.draggable,false);
});
