import {test} from 'node:test';
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {mkdtemp,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';

test('HTTP payment survives server restart and recovers without a second debit',async()=>{
 const dir=await mkdtemp(join(tmpdir(),'ieum-api-'));let child;
 async function start(){
  child=spawn(process.execPath,['server.js'],{env:{...process.env,PORT:'0',IEUM_DB:join(dir,'test.sqlite')},stdio:['ignore','pipe','pipe']});
  let line='';const port=await new Promise((resolve,reject)=>{const timeout=setTimeout(()=>reject(Error('Server readiness timeout')),10000);child.once('exit',code=>{clearTimeout(timeout);reject(Error('Server exited '+code));});child.stdout.on('data',d=>{line+=d;const match=line.match(/port (\d+)/);if(match){clearTimeout(timeout);resolve(match[1]);}});});
  return `http://127.0.0.1:${port}`;
 }
 async function stop(){const exited=once(child,'exit');child.kill();await exited;child=null;}
 try{
  let base=await start();
  const call=async(path,body)=>{const r=await fetch(base+path,{method:body?'POST':'GET',headers:{'Content-Type':'application/json'},body:body?JSON.stringify(body):undefined});return {code:r.status,data:await r.json()};};
  const before=(await call('/api/state')).data.wallet;
  const payload={amount:15000,method:'coin',requestKey:'restart-recovery-001'};
  const original=await call('/api/pay',payload);assert.equal(original.code,200);
  await stop();base=await start();
  const recovered=await call('/api/pay',payload);assert.equal(recovered.data.id,original.data.id);
  assert.equal((await call('/api/state')).data.wallet.paid,90000);
  assert.equal((await call('/api/cancel',{id:original.data.id})).code,400);
  assert.equal((await call('/api/cancel',{id:original.data.id,reason:'고객 요청'})).code,200);
  assert.deepEqual((await call('/api/state')).data.wallet,before);
 }finally{if(child)await stop();await rm(dir,{recursive:true,force:true});}
});
