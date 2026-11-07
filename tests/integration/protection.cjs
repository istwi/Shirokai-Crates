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
 await connect();await cmd('gamemode creative');await cmd('tp @s 0.5 65 0.5');
 await cmd('setblock 2 64 2 minecraft:diamond_block');await bot.lookAt(new Vec3(2.5,64.5,2.5),true);await sleep(200);await cmd('crates setblock shiny1','registrada com sucesso');
 await cmd('setblock 2 64 3 minecraft:piston[facing=north]');await cmd('setblock 2 64 4 minecraft:redstone_block');await sleep(400);await cmd('execute if block 2 64 2 minecraft:diamond_block run tell @s PISTON_OK','PISTON_OK');ok('registered movable block is not pushed by powered piston');
 await cmd('setblock 2 64 4 minecraft:air');await cmd('setblock 2 64 3 minecraft:air');await bot.lookAt(new Vec3(2.5,64.5,2.5),true);await sleep(200);await cmd('crates removeblock','bloco foi preservado');
 await cmd('summon minecraft:tnt 0.5 64 2.5 {Fuse:1s}');await sleep(4500);await cmd('execute unless entity @e[type=minecraft:tnt] run tell @s TNT_FINISHED','TNT_FINISHED');await cmd('execute if block 0 64 2 minecraft:chest run tell @s EXPLOSION_OK','EXPLOSION_OK');ok('real TNT explosion preserves registered chest');
 await cmd('fill -2 63 -2 4 63 5 minecraft:stone');await cmd('clear @s');
 const primary=bot;await connect('ShirokaiPeer');const peer=bot;bot=primary;
 await cmd('tp ShirokaiPeer 1.5 64 0.5');
 const c=JSON.parse(original);c.rewards=[{name:'Concurrent Diamond',type:'item',item:'minecraft:diamond',count:2,rarity:'common',weight:1}];fs.writeFileSync(config,JSON.stringify(c));await cmd('crates reload','recarregada');
 await cmd('crates givekey ShirokaiPeer shiny1 1','Itens entregues');await cmd('crates givekey ShirokaiTest shiny1 1','Itens entregues');await equipKey();
 await wait(()=>peer.inventory.items().some(i=>i.name==='tripwire_hook'));await peer.equip(peer.inventory.items().find(i=>i.name==='tripwire_hook'),'hand');await peer.lookAt(new Vec3(.5,64.5,2.5),true);await sleep(200);
 const before=records().map(t=>t.id);await Promise.all([activate(),peer.activateBlock(peer.blockAt(new Vec3(0,64,2)))]);
 const done=await wait(()=>{const done=records().filter(t=>!before.includes(t.id)&&t.state==='DELIVERED');return done.length===2&&done},15000);
 assert.equal(new Set(done.map(t=>t.player)).size,2);assert.equal(new Set(done.map(t=>t.id)).size,2);ok('two connected players open the same physical crate concurrently');
 peer.quit();bot.quit();
}
main().then(()=>{fs.writeFileSync(config,original);fs.writeFileSync(path.join(run,'protection-results.json'),JSON.stringify({results},null,2));console.log('SMOKE PASSED',results.length);process.exit(0)}).catch(e=>{console.error('SMOKE FAILED',e);try{fs.writeFileSync(config,original)}catch{};fs.writeFileSync(path.join(run,'protection-results.json'),JSON.stringify({results,error:e.stack},null,2));if(bot)bot.quit();process.exit(1)});
