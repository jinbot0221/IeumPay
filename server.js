import http from 'node:http';
import {readFile,mkdir} from 'node:fs/promises';
import {createStore} from './store.js';
await mkdir('.data',{recursive:true});
const store=createStore(process.env.IEUM_DB || '.data/demo.sqlite');
const server=http.createServer(async(req,res)=>{
 const send=(code,data)=>{res.writeHead(code,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'});res.end(JSON.stringify(data));};
 try{
 const url=new URL(req.url,'http://localhost');
 if(req.method==='GET'&&url.pathname==='/api/state')return send(200,{mode:'demo',...store.state()});
 if(req.method==='GET'&&url.pathname==='/api/health')return send(200,{ok:true,mode:'demo'});
 if(req.method==='POST'&&['/api/pay','/api/cancel'].includes(url.pathname)){
 if(req.headers.origin && req.headers.origin!==`http://${req.headers.host}`)return send(403,{error:'허용되지 않는 요청입니다.'});
 let body='';for await(const chunk of req){body+=chunk;if(body.length>4096)return send(413,{error:'요청이 너무 큽니다.'});}
 const input=JSON.parse(body);return send(200,url.pathname==='/api/pay'?store.pay(input):store.cancel(input.id,input.reason));
 }
 const files={'/':'index.html','/app.js':'app.js','/style.css':'style.css'};const file=files[url.pathname];if(!file||req.method!=='GET')return send(404,{error:'찾을 수 없습니다.'});
 res.writeHead(200,{'Content-Type':file.endsWith('.js')?'text/javascript':file.endsWith('.css')?'text/css':'text/html; charset=utf-8','X-Content-Type-Options':'nosniff'});res.end(await readFile(new URL(`./public/${file}`,import.meta.url)));
 }catch(e){send(400,{error:e.message});}
});
server.listen(Number(process.env.PORT||3000),process.env.HOST||'127.0.0.1',()=>console.log('IEUM PAY demo listening on port '+server.address().port));
