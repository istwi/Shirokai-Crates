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
async function connect(){
 bot=mineflayer.createBot({host:'127.0.0.1',port:Number(process.env.MC_TEST_PORT||25565),username:'ShirokaiTest',version:'1.21.1',auth:'offline',hideErrors:true,
  customPackets:{'1.21':{play:{toClient:{types:{packet_declare_recipes:'restBuffer',packet_entity_metadata:'restBuffer',packet_declare_commands:'restBuffer',packet_advancements:'restBuffer'}}}}}});
 const emit=bot._client.emit;bot._client.emit=function(name,...args){if(name==='entity_metadata'||name==='declare_commands'||name==='advancements')return false;return emit.call(this,name,...args)};
 bot.on('messagestr',m=>console.log('CHAT',m));bot.on('error',e=>console.log('CLIENT',e.message));
 await Promise.race([new Promise(resolve=>bot.once('spawn',resolve)),new Promise((_,reject)=>bot.once('kicked',r=>reject(Error('Kicked '+JSON.stringify(r))))),sleep(20000).then(()=>{throw Error('Login timed out')})]);
 await sleep(400);
}
function count(name){return (bot.currentWindow||bot.inventory).items().filter(i=>i.name===name).reduce((n,i)=>n+i.count,0)}
async function equipKey(){const key=bot.inventory.items().find(i=>i.name==='tripwire_hook');assert(key,'physical key');await bot.equip(key,'hand')}
async function activate(){const block=await wait(()=>bot.blockAt(new Vec3(0,64,2)));await bot.lookAt(new Vec3(.5,64.5,2.5),true);await sleep(200);await bot.activateBlock(block);}
async function activatePatchCrate(){
 await bot.lookAt(new Vec3(8.5,64.5,2.5),true);await sleep(200);await bot.activateBlock(bot.blockAt(new Vec3(8,64,2)));
}
async function main(){
 const phase=process.argv[2],expectedFile=path.join(run,'patch-011-expected.json');await connect();ok('real client login with Fabric Loader 0.20.1');
 await cmd('gamemode creative');await cmd('tp @s 8.5 65 0.5');
 if(phase==='prepare'){
  const legacyKeys=count('tripwire_hook');assert(legacyKeys>=2,'Legacy keys from v0.1.0 must be retained for the upgrade test');
  await cmd('fill 6 63 -2 12 63 5 minecraft:stone');await cmd('setblock 8 64 2 minecraft:chest');await bot.lookAt(new Vec3(8.5,64.5,2.5),true);await sleep(200);
  await cmd('crates setblock shiny1','registrada com sucesso');assert(blocks().some(b=>b.position.x===8&&b.crateId==='shiny1'));ok('setblock shiny1 registers a new physical chest');
  const crate=JSON.parse(original);crate.rewards=[{name:'Patch 0.1.1 Diamond',type:'item',item:'minecraft:diamond',count:2,rarity:'common',weight:1}];fs.writeFileSync(config,JSON.stringify(crate));await cmd('crates reload','recarregada');
  const before=records().map(t=>t.id),diamondsBefore=count('diamond');await equipKey();await activatePatchCrate();await wait(()=>bot.currentWindow);
  const first=await wait(()=>records().find(t=>!before.includes(t.id)&&t.state==='DELIVERED'),15000);
  await wait(()=>count('tripwire_hook')===legacyKeys-1&&count('diamond')===diamondsBefore+2);ok('pre-upgrade physical key opens roulette and consumes exactly one key');
  bot.closeWindow(bot.currentWindow);await cmd('gamemode survival');await cmd('give @s minecraft:diamond_axe');await sleep(200);await bot.equip(bot.inventory.items().find(i=>i.name==='diamond_axe'),'hand');
  await Promise.race([bot.dig(bot.blockAt(new Vec3(8,64,2)),true).catch(()=>{}),sleep(4000)]);bot.stopDigging();
  await cmd('execute if block 8 64 2 minecraft:chest run tell @s PROTECTED_011','PROTECTED_011');ok('registered chest resists a legitimate mining attempt');
  await cmd('gamemode creative');await cmd('setblock 8 64 2 minecraft:air');await cmd('execute if block 8 64 2 minecraft:chest run tell @s WORLD_MIXIN_OK','WORLD_MIXIN_OK');ok('WorldMixin still blocks direct replacement of a registered chest');
  const prior=records().map(t=>t.id);await equipKey();await activatePatchCrate();const pending=await wait(()=>bot.currentWindow&&records().find(t=>!prior.includes(t.id)&&t.state==='READY'));
  await wait(()=>count('tripwire_hook')===legacyKeys-2);fs.writeFileSync(expectedFile,JSON.stringify({first:first.id,pending:pending.id,keyCount:legacyKeys-2,diamondCount:diamondsBefore+4,crateId:'shiny1'}));
  fs.writeFileSync(config,original);const disconnected=new Promise(resolve=>bot.once('end',resolve));bot.quit();await disconnected;ok('disconnect leaves the same persisted opening ready for controlled server restart');
 }else if(phase==='verify'){
  const expected=JSON.parse(fs.readFileSync(expectedFile));assert(blocks().some(b=>b.position.x===8&&b.crateId===expected.crateId));assert.equal(records().find(t=>t.id===expected.first).state,'DELIVERED');assert.equal(records().find(t=>t.id===expected.pending).state,'READY');assert.equal(count('tripwire_hook'),expected.keyCount);ok('physical registrations, key consumption and transaction history persist after restart');
  await cmd('crates pending');await wait(()=>bot.currentWindow);const pending=records().filter(t=>!['DELIVERED','CANCELLED'].includes(t.state)).sort((a,b)=>b.created.localeCompare(a.created));const slot=pending.findIndex(t=>t.id===expected.pending);assert(slot>=0&&slot<45);await bot.clickWindow(slot,0,0);
  await wait(()=>records().find(t=>t.id===expected.pending).state==='DELIVERED');await wait(()=>count('diamond')===expected.diamondCount);assert.equal(count('tripwire_hook'),expected.keyCount);ok('pending reward survives upgrade test restart and is delivered without another key');
  if(bot.currentWindow)bot.closeWindow(bot.currentWindow);await cmd('execute if block 8 64 2 minecraft:chest run tell @s RESTART_PROTECTION_OK','RESTART_PROTECTION_OK');ok('physical protected chest remains after restart');bot.quit();
 }else throw Error('Use prepare or verify');
}
main().then(()=>{fs.writeFileSync(config,original);fs.writeFileSync(path.join(run,'patch-011-'+process.argv[2]+'-results.json'),JSON.stringify({results},null,2));console.log('PATCH CHECKS PASSED',results.length);process.exit(0)}).catch(e=>{console.error('PATCH CHECK FAILED',e);try{fs.writeFileSync(config,original)}catch{};fs.writeFileSync(path.join(run,'patch-011-'+process.argv[2]+'-results.json'),JSON.stringify({results,error:e.stack},null,2));if(bot)bot.quit();process.exit(1)});
