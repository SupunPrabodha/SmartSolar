import assert from 'node:assert/strict';
import { test } from 'node:test';
import { displayReference } from '../src/util/displayReference.js';
const fixtures = [
 ['11111111-1111-1111-1111-111111111111','SB2J5ZFT6T'],
 ['22222222222222222222222222222222','8FHMZDSSO5'],
 ['3ef796dd-1234-5678-9abc-12345678d770','FUOFG2HQ7M']
];
test('shared Android fixtures: stable canonical IDs and all presentation prefixes', () => {
 for(const [id,suffix] of fixtures) {
   for(const [kind,prefix] of [['reservation','REF'],['station','STN'],['slot','SLOT']]) {
     assert.equal(displayReference(id,kind), prefix+'-'+suffix);
     assert.equal(displayReference(' '+id.toUpperCase().replaceAll('-','')+' ',kind), prefix+'-'+suffix);
   }
 }
 assert.deepEqual(fixtures.toReversed().map(([id])=>displayReference(id)).toReversed(),fixtures.map(([id])=>displayReference(id)));
 assert.equal(new Set(fixtures.map(([id])=>displayReference(id))).size,fixtures.length);
});
test('invalid or absent references never expose internal text', () => {
 for(const value of [null,undefined,'','invalid','00000000-0000-0000-0000-000000000000','1'.repeat(31),123]) assert.equal(displayReference(value),'Unavailable');
});
