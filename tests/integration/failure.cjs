// Test-only vanilla protocol client. Skip Cobblemon's custom recipes/entity metadata packets.
// Gameplay interactions, inventory windows and server-side transactions are not mocked.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { createRequire } = require('node:module');
const req = process.env.MC_TEST_NODE_MODULES ? createRequire(path.join(process.env.MC_TEST_NODE_MODULES,'package.json')) : require;
const mineflayer = req('mineflayer');
const { Vec3 } = req('vec3');
const run = path.resolve(process.env.MC_TEST_RUN || 'run');
const sleep = ms=>new Promise(r=>setTimeout(r,ms));
const results=[];
let bot;
const config=path.join(run,'config/shirokaicrates/crates/shiny1.json');
const original=fs.readFileSync(config,'utf8');
function ok(name){results.push({name,status:'passed'});console.log('PASS',name)}
async function wait(fn,timeout=10000){const end=Date.now()+timeout;while(Date.now()<end){const value=fn();if(value)return value;await sleep(50)}throw Error('Timeout: '+fn.toString())}
function records(){const d=path.join(run,'world/shirokaicrates/transactions');return fs.existsSync(d)?fs.readdirSync(d).filter(n=>n.endsWith('.json')).map(n=>JSON.parse(fs.readFileSync(path.join(d,n),'utf8'))):[]}
function blocks(){return JSON.parse(fs.readFileSync(path.join(run,'world/shirokaicrates/blocks.json'),'utf8')).blocks}
async function cmd(text,expected){
 const promise=expected?new Promise((resolve,reject)=>{const handler=m=>{if(m.includes(expected)){clearTimeout(timer);bot.removeListener('messagestr',handler);resolve(m)}};bot.on('messagestr',handler);const timer=setTimeout(()=>{bot.removeListener('messagestr',handler);reject(Error(text+' did not produce '+expected))},10000)}):null;
 bot.chat('/'+text);if(promise)return promise;await sleep(150);
}
async function connect(name='ShirokaiTest'){
 bot=mineflayer.createBot({host:'127.0.0.1',port:25565,username:name,version:'1.21.1',auth:'offline',hideErrors:true,
  customPackets:{'1.21':{play:{toClient:{types:{packet_declare_recipes:'restBuffer',packet_entity_metadata:'restBuffer',packet_declare_commands:'restBuffer',packet_advancements:'restBuffer'}}}}}});
 const emit=bot._client.emit;bot._client.emit=function(name,...args){if(name==='entity_metadata'||name==='declare_commands'||name==='advancements')return false;return emit.call(this,name,...args)};
 bot.on('messagestr',m=>console.log('CHAT',m));bot.on('error',e=>console.log('CLIENT',e.message));
 await Promise.race([new Promise(resolve=>bot.once('spawn',resolve)),new Promise((_,reject)=>bot.once('kicked',r=>reject(Error('Kicked '+JSON.stringify(r))))),sleep(20000).then(()=>{throw Error('Login timed out')})]);
 await sleep(400);
}
function count(name){return (bot.currentWindow||bot.inventory).items().filter(i=>i.name===name).reduce((n,i)=>n+i.count,0)}
async function equipKey(){const key=bot.inventory.items().find(i=>i.name==='tripwire_hook');assert(key,'physical key');await bot.equip(key,'hand')}
async function activate(){const block=await wait(()=>bot.blockAt(new Vec3(0,64,2)));await bot.lookAt(new Vec3(.5,64.5,2.5),true);await sleep(200);await bot.activateBlock(block);}
async function main(){
 await connect();await cmd('gamemode creative');await cmd('tp @s 0.5 65 0.5');await cmd('clear @s');await cmd('crates givekey @s shiny1 2','Itens entregues');await wait(()=>count('tripwire_hook')===2);await equipKey();
 const dir=path.join(run,'world/shirokaicrates/transactions'),mode=fs.statSync(dir).mode&0o777, before=records().length;
 const transcript=[];bot.on('messagestr',m=>transcript.push(m));
 try {
  fs.chmodSync(dir,0o500);await activate();await wait(()=>transcript.some(m=>m.includes('falha de persistência')));
  assert.equal(records().length,before);assert.equal(count('tripwire_hook'),2);ok('failed ledger write blocks opening without consuming a key');
 }finally{fs.chmodSync(dir,mode)}
 bot.quit();await sleep(300);await connect();assert.equal(count('tripwire_hook'),2);ok('player can log in after a critical persistence error');bot.quit();
}
main().then(()=>{fs.writeFileSync(config,original);fs.writeFileSync(path.join(run,'failure-results.json'),JSON.stringify({results},null,2));console.log('SMOKE PASSED',results.length);process.exit(0)}).catch(e=>{console.error('SMOKE FAILED',e);try{fs.writeFileSync(config,original)}catch{};fs.writeFileSync(path.join(run,'failure-results.json'),JSON.stringify({results,error:e.stack},null,2));if(bot)bot.quit();process.exit(1)});
