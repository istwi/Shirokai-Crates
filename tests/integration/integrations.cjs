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
 bot=mineflayer.createBot({host:'127.0.0.1',port:25565,username:'ShirokaiTest',version:'1.21.1',auth:'offline',hideErrors:true,
  customPackets:{'1.21':{types:require('./model-protocol.cjs'),play:{toClient:{types:{packet_declare_recipes:'restBuffer',packet_entity_metadata:'restBuffer',packet_declare_commands:'restBuffer',packet_advancements:'restBuffer'}}}}}});
 const emit=bot._client.emit;bot._client.emit=function(name,...args){if(name==='entity_metadata'||name==='declare_commands'||name==='advancements')return false;return emit.call(this,name,...args)};
 bot.on('messagestr',m=>console.log('CHAT',m));bot.on('error',e=>console.log('CLIENT',e.message));
 await Promise.race([new Promise(resolve=>bot.once('spawn',resolve)),new Promise((_,reject)=>bot.once('kicked',r=>reject(Error('Kicked '+JSON.stringify(r))))),sleep(20000).then(()=>{throw Error('Login timed out')})]);
 await sleep(400);
}
function count(name){return (bot.currentWindow||bot.inventory).items().filter(i=>i.name===name).reduce((n,i)=>n+i.count,0)}
async function equipKey(){const key=bot.inventory.items().find(i=>i.name==='tripwire_hook');assert(key,'physical key');await bot.equip(key,'hand')}
async function activate(){const block=await wait(()=>bot.blockAt(new Vec3(0,64,2)));await bot.lookAt(new Vec3(.5,64.5,2.5),true);await sleep(200);await bot.activateBlock(block);}
async function main(){
 await connect();await cmd('gamemode creative');await cmd('tp @s 0.5 65 0.5');if(bot.currentWindow)bot.closeWindow(bot.currentWindow);
 const base=JSON.parse(original);
 async function award(reward){
  if(bot.currentWindow)bot.closeWindow(bot.currentWindow);
  await cmd('clear @s');const c={...base,rewards:[reward]};fs.writeFileSync(config,JSON.stringify(c));await cmd('crates reload','recarregada');
  await cmd('crates givekey @s shiny1 1','Itens entregues');await wait(()=>count('tripwire_hook')===1);await equipKey();const previous=records().map(t=>t.id);
  await activate();const tx=await wait(()=>records().find(t=>!previous.includes(t.id)&&['DELIVERED','UNCERTAIN'].includes(t.state)),15000);
  assert.equal(tx.state,'DELIVERED',tx.detail);return tx;
 }
 const transcript=[];bot.on('messagestr',m=>transcript.push(m));
 const item=await award({name:'Cobblemon Balls',type:'item',item:'cobblemon:ultra_ball',count:8,rarity:'common',weight:1});
 if(bot.currentWindow)bot.closeWindow(bot.currentWindow);await cmd('clear @s cobblemon:ultra_ball','Removed 8');ok('deliver registered Cobblemon item through Minecraft inventory');
 const pokemon=await award({name:'Test Shiny Pikachu',type:'pokemon',species:'pikachu',level:10,shiny:true,nature:'adamant',hp_iv:31,attack_iv:31,rarity:'epic',icon:'minecraft:dragon_head',weight:1});
 const snapshot=JSON.parse(pokemon.pokemonJson);assert(pokemon.pokemonUuid);assert(JSON.stringify(snapshot).includes('pikachu'));ok('Cobblemon 1.8.3 API accepts persisted Pokémon snapshot');
 assert(transcript.some(m=>m.includes('ShirokaiTest recebeu Test Shiny Pikachu')));assert(!transcript.some(m=>m.includes('ShirokaiTest recebeu Cobblemon Balls')));ok('global announcements only at epic or above');
 for(const currency of ['impactor:dollars','impactor:tokens']){
  await award({name:'Test '+currency,type:'currency',currency,amount:17,rarity:'common',weight:1,icon:'minecraft:gold_ingot'});ok('Impactor 5.3.5 confirms deposit and save: '+currency);
 }
 await award({name:'Command Reward',type:'command',command:'tell {player} COMMAND_REWARD_OK',rarity:'common',weight:1,icon:'minecraft:paper'});assert(transcript.some(m=>m.includes('COMMAND_REWARD_OK')));ok('configured command reward executes and is journaled');
 if(bot.currentWindow)bot.closeWindow(bot.currentWindow);
 await cmd('lp user ShirokaiTest permission set shirokaicrates.admin false','shirokaicrates.admin');await sleep(1200);await cmd('lp user ShirokaiTest permission set shirokaicrates.use false','shirokaicrates.use');await sleep(1200);await cmd('crates','Você não tem permissão');ok('LuckPerms explicit deny is read without Fabric Permissions API linkage');
 await cmd('lp user ShirokaiTest permission unset shirokaicrates.use','shirokaicrates.use');await sleep(1200);await cmd('lp user ShirokaiTest permission unset shirokaicrates.admin','shirokaicrates.admin');
 bot.quit();await sleep(1000);fs.writeFileSync(path.join(run,'pokemon-test-transaction.json'),JSON.stringify(pokemon,null,2));
}
main().then(()=>{fs.writeFileSync(config,original);fs.writeFileSync(path.join(run,'integration-results.json'),JSON.stringify({results},null,2));console.log('SMOKE PASSED',results.length);process.exit(0)}).catch(e=>{console.error('SMOKE FAILED',e);try{fs.writeFileSync(config,original)}catch{};fs.writeFileSync(path.join(run,'integration-results.json'),JSON.stringify({results,error:e.stack},null,2));if(bot)bot.quit();process.exit(1)});
