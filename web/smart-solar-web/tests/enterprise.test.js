import assert from 'node:assert/strict';
import {test} from 'node:test';
import {notify,dismiss,clearFeedback,subscribeFeedback} from '../src/util/feedback.js';

test('feedback queue caps at three and updates loading in place',()=>{
  clearFeedback();let items;const unsubscribe=subscribeFeedback(x=>{items=x;});
  notify('Preparing','loading','export');notify('Downloaded','success','export');
  assert.equal(items.length,1);assert.equal(items[0].type,'success');
  notify('Two');notify('Three');notify('Four');assert.equal(items.length,3);
  const before=items;notify('Four');assert.equal(items,before);
  dismiss('Four');assert.equal(items.length,2);unsubscribe();clearFeedback();
});
test('session cleanup removes transient feedback',()=>{
  let items;const unsubscribe=subscribeFeedback(x=>items=x);notify('Private result');clearFeedback();
  assert.deepEqual(items,[]);unsubscribe();
});
