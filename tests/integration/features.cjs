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
const config=path.join(run,'config/shirokaicrates/crates/features_test.json');
function ok(name){results.push({name,status:'passed'});console.log('PASS',name)}
async function wait(fn,timeout=10000){const end=Date.now()+timeout*4;while(Date.now()<end){const value=fn();if(value)return value;await sleep(50)}throw Error('Timeout: '+fn.toString())}
function records(){const d=path.join(run,'world/shirokaicrates/transactions');return fs.existsSync(d)?fs.readdirSync(d).filter(n=>n.endsWith('.json')).map(n=>JSON.parse(fs.readFileSync(path.join(d,n),'utf8'))):[]}
function blocks(){return JSON.parse(fs.readFileSync(path.join(run,'world/shirokaicrates/blocks.json'),'utf8')).blocks}
async function cmd(text,expected){
 const promise=expected?new Promise((resolve,reject)=>{const handler=m=>{if(m.includes(expected)){clearTimeout(timer);bot.removeListener('messagestr',handler);resolve(m)}};bot.on('messagestr',handler);const timer=setTimeout(()=>{bot.removeListener('messagestr',handler);reject(Error(text+' did not produce '+expected))},10000)}):null;
 bot.chat('/'+text);if(promise)return promise;await sleep(150);
}
async function connect(){
 bot=mineflayer.createBot({host:'127.0.0.1',port:25565,username:process.env.MC_TEST_USERNAME||'ShirokaiTest',version:'1.21.1',auth:'offline',hideErrors:true,
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
 await connect();
 const expectedFile=path.join(run,'features-expected.json');
 if(process.argv[2]==='verify'){
  const expected=JSON.parse(fs.readFileSync(expectedFile));
  for(const e of expected){const tx=records().find(t=>t.id===e.id);assert(tx);assert.equal(tx.state,'DELIVERED');assert.equal(tx.pokemonJson,e.pokemonJson);}
  ok('Pokémon UUIDs and IV snapshots persist unchanged after server restart');bot.quit();return;
 }
 const globalFile=path.join(run,'config/shirokaicrates/config.json'),globalOriginal=fs.readFileSync(globalFile,'utf8');
 if(!fs.existsSync(config))await cmd('crates create features_test','criada e carregada');else await cmd('crates reload','recarregada');
 const original=fs.readFileSync(config,'utf8'),base=JSON.parse(original),expected=[];
 const particles=[];
 bot._client.on('world_particles',p=>{if(Math.abs(p.x-.5)<1.5&&Math.abs(p.z-2.5)<1.5&&p.y>64.8&&p.y<65.7)particles.push(p)});
 function writeRewards(rewards){fs.writeFileSync(config,JSON.stringify({...base,rewards}));}
 function ivs(tx){const snap=JSON.parse(tx.pokemonJson);const key=Object.keys(snap).find(k=>k.toLowerCase()==='ivs');assert(key,'IV map present: '+Object.keys(snap));const values=Object.values(snap[key].Base||snap[key]);assert.equal(values.length,6);return values;}
 try {
  await cmd('gamemode creative');await cmd('tp @s 0.5 65 0.5');await cmd('fill -2 63 -2 4 63 5 minecraft:stone');await cmd('setblock 0 64 2 minecraft:chest');
  await bot.lookAt(new Vec3(.5,64.5,2.5),true);await sleep(300);await cmd('crates setblock features_test','registrada com sucesso');
  await wait(()=>particles.length>=6,15000);assert(new Set(particles.map(p=>p.x.toFixed(3)+','+p.z.toFixed(3))).size>2);ok('registered crate emits moving particles around the block');
  const global=JSON.parse(globalOriginal);global.crateEffects={enabled:false};fs.writeFileSync(globalFile,JSON.stringify(global));await cmd('crates reload','recarregada');await sleep(300);let before=particles.length;await sleep(1000);assert.equal(particles.length,before);ok('block effects can be disabled on reload');
  fs.writeFileSync(globalFile,globalOriginal);await cmd('crates reload','recarregada');await wait(()=>particles.length>before);ok('block effects resume after re-enabling');
  writeRewards([{name:'Layout Diamond',type:'item',item:'minecraft:diamond',count:1,chance:100}]);await cmd('crates reload','recarregada');await cmd('clear @s');await cmd('crates givekey @s features_test 1','Itens entregues');await equipKey();const layoutPrevious=new Set(records().map(t=>t.id));await activate();await wait(()=>bot.currentWindow);
  assert.equal(bot.currentWindow.type,'minecraft:generic_9x3');
  assert.equal(bot.currentWindow.slots[4].name,'lime_stained_glass_pane');assert.equal(bot.currentWindow.slots[22].name,'lime_stained_glass_pane');
  for(const slot of [0,1,2,3,5,6,7,8,18,19,20,21,23,24,25,26])assert.equal(bot.currentWindow.slots[slot].name,'gray_stained_glass_pane');
  for(let slot=9;slot<=17;slot++)assert.equal(bot.currentWindow.slots[slot].name,'diamond');ok('roulette uses three rows, gray borders and aligned green center markers');
  const itemCount=count('diamond');await bot.clickWindow(13,0,1);await sleep(100);assert.equal(count('diamond'),itemCount);ok('roulette display items cannot be taken with shift-click');
  await wait(()=>records().some(t=>!layoutPrevious.has(t.id)&&t.reward.name==='Layout Diamond'&&t.state==='DELIVERED'),15000);assert.equal(bot.currentWindow.slots[13].name,'diamond');ok('final reward appears in the center and is delivered');bot.closeWindow(bot.currentWindow);
  async function award(policy,legacy={}){
   await cmd('clear @s');writeRewards([{name:'IV '+(policy?policy.mode:'fixed'),type:'pokemon',species:'pikachu',level:10,shiny:true,icon:'minecraft:dragon_head',chance:100,...legacy,...(policy?{ivs:policy}:{})}]);
   await cmd('crates reload','recarregada');await cmd('crates givekey @s features_test 1','Itens entregues');await equipKey();const previous=new Set(records().map(t=>t.id));await activate();
   const tx=await wait(()=>records().find(t=>!previous.has(t.id)&&t.pokemonJson),15000);
   if(policy?.mode==='perfect') {assert.equal(ivs(tx).filter(v=>v===31).length,policy.perfectCount);writeRewards([{name:'Changed after reservation',type:'item',item:'minecraft:emerald',chance:100}]);await cmd('crates reload','recarregada');}
   await wait(()=>records().find(t=>t.id===tx.id)?.state==='DELIVERED',15000);assert.equal(records().find(t=>t.id===tx.id).pokemonJson,tx.pokemonJson);
   if(bot.currentWindow)bot.closeWindow(bot.currentWindow);expected.push({id:tx.id,pokemonJson:tx.pokemonJson});return tx;
  }
  const perfect=await award({mode:'perfect',perfectCount:3});assert.equal(ivs(perfect).filter(v=>v===31).length,3);ok('Cobblemon receives exactly three perfect IVs, preserved across reload during roulette');
  const random=await award({mode:'random'});assert(ivs(random).every(v=>Number.isInteger(v)&&v>=0&&v<=31));ok('Cobblemon receives six random IVs in the valid range');
  const fixed=await award(null,{hp_iv:31,attack_iv:31});assert(ivs(fixed).filter(v=>v===31).length>=2);ok('legacy fixed IV fields remain compatible');
  fs.writeFileSync(expectedFile,JSON.stringify(expected));
 } finally {fs.writeFileSync(globalFile,globalOriginal);if(fs.existsSync(config))fs.writeFileSync(config,original);}
 bot.quit();
}
main().then(()=>{fs.writeFileSync(path.join(run,'features-'+(process.argv[2]||'prepare')+'-results.json'),JSON.stringify({results},null,2));console.log('FEATURES PASSED',results.length);process.exit(0)}).catch(e=>{console.error(e);if(bot)bot.quit();process.exit(1)});
