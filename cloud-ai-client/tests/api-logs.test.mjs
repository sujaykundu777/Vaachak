import test from 'node:test';
import assert from 'node:assert/strict';
import { createLogStore, sanitize } from '../app/lib/api-log-store.ts';
const json = value => Response.json(value);

test('redacts credentials and binary fields before storing, omits text by default', () => {
 const b=sanitize({api_key:'private',token:'private',nested:{authorization:'Bearer private',content:'Hello sk-example123'},images:['base64'],audio_base64:'base64',embeddings:[[1,2]]},true);
 assert.ok(!JSON.stringify(b).includes('private'));assert.ok(!JSON.stringify(b).includes('sk-example123'));assert.equal(b.token,'[redacted]');assert.equal(sanitize({prompt:'context'}).prompt,'[text omitted]');
});
test('captures source, action, journey and leaves original response readable', async () => {
 const store=createLogStore(async()=>json({status:'ok',content:'Hello'}),()=>'/ollama');store.startJourney('Reading test');
 const r=await store.fetch('/api/ollama/generate',{method:'POST',body:JSON.stringify({model:'gemma4',prompt:'Hello'})});assert.equal((await r.json()).content,'Hello');
 const c=store.getSnapshot().calls[0];assert.equal(c.source,'/ollama');assert.equal(c.service,'Ollama');assert.equal(c.upstream,'/api/generate');assert.equal(c.journey,'Reading test');assert.equal(c.state,'success');assert.equal(c.request.prompt,'[text omitted]');
});
test('classifies HTTP errors, provider failures, partials and action requests', async () => {
 for(const [status,body,expected] of [[401,{detail:'missing key'},'error'],[200,{status:'error'},'error'],[200,{status:'FAILED'},'error'],[200,{status:'partial'},'partial'],[200,{status:'REQUIRES_ACTION'},'partial']]){
 const store=createLogStore(async()=>new Response(JSON.stringify(body),{status,headers:{'content-type':'application/json'}}));await store.fetch('/api/backend/backboard/message');assert.equal(store.getSnapshot().calls[0].state,expected);}
});
test('records and rethrows network failures and aborts', async () => {
 for(const [err,expected] of [[new Error('offline'),'error'],[new DOMException('aborted','AbortError'),'cancelled']]){const store=createLogStore(async()=>{throw err;});await assert.rejects(store.fetch('/api/backend/health'));assert.equal(store.getSnapshot().calls[0].state,expected);}
});
test('pause skips calls and text opt-in only changes future captures', async () => {
 const store=createLogStore(async()=>json({content:'answer'}));await store.fetch('/api/backend/health');store.setRecording(false);await store.fetch('/api/backend/health');assert.equal(store.getSnapshot().calls.length,1);store.setRecording(true);store.setCaptureText(true);await store.fetch('/api/backend/health');assert.equal(store.getSnapshot().calls[0].response.content,'[text omitted]');assert.equal(store.getSnapshot().calls[1].response.content,'answer');
});
test('clear during in-flight requests never resurrects cleared entries', async () => {
 let resolve;const waiting=new Promise(r=>{resolve=r;});const store=createLogStore(()=>waiting);const response=store.fetch('/api/backend/health');assert.equal(store.getSnapshot().calls[0].state,'pending');store.clear();resolve(json({status:'ok'}));await response;assert.equal(store.getSnapshot().calls.length,0);
});
test('file bytes and oversized responses are summarized; originals stay readable', async () => {
 const form=new FormData();form.append('file',new Blob(['private file'],{type:'image/png'}),'private.png');form.append('model','gemma4');const store=createLogStore(async()=>json({audio_base64:'A'.repeat(40000)}));const r=await store.fetch('/api/backend/gemini/imageToText',{method:'POST',body:form});assert.equal((await r.json()).audio_base64.length,40000);const c=store.getSnapshot().calls[0];assert.equal(c.request.fields.file.bytes,12);assert.ok(!JSON.stringify(c).includes('private.png'));assert.equal(c.response.byte_limit,32768);
});
test('bounds records and distinguishes journeys', async () => {
 const store=createLogStore(async()=>json({status:'ok'}));store.startJourney('First');await store.fetch('/api/backend/health');const first=store.getSnapshot().calls[0].journeyId;store.startJourney('Second');await store.fetch('/api/database/status');assert.notEqual(store.getSnapshot().calls[1].journeyId,first);for(let i=0;i<201;i++)await store.fetch('/api/backend/health');assert.equal(store.getSnapshot().calls.length,200);
});
test('redacts query credentials and skips unrelated URLs', async () => {
 const store=createLogStore(async()=>json({status:'ok'}));await store.fetch('/api/backend/health?api_key=private&model=gemma4');assert.ok(!store.getSnapshot().calls[0].path.includes('private'));await store.fetch('https://example.com/');assert.equal(store.getSnapshot().calls.length,1);
});
