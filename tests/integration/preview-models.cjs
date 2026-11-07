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
const config=path.join(run,'config/shirokaicrates/crates/preview_models.json');
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
  customPackets:{'1.21':{types:require('./model-protocol.cjs'),play:{toClient:{types:{packet_declare_recipes:'restBuffer',packet_entity_metadata:'restBuffer',packet_declare_commands:'restBuffer',packet_advancements:'restBuffer'}}}}}});
 const emit=bot._client.emit;bot._client.emit=function(name,...args){if(name==='entity_metadata'||name==='declare_commands'||name==='advancements')return false;return emit.call(this,name,...args)};
 bot.on('messagestr',m=>console.log('CHAT',m));bot.on('error',e=>console.log('CLIENT',e.message));
 await Promise.race([new Promise(resolve=>bot.once('spawn',resolve)),new Promise((_,reject)=>bot.once('kicked',r=>reject(Error('Kicked '+JSON.stringify(r))))),sleep(20000).then(()=>{throw Error('Login timed out')})]);
 await sleep(400);
}
function count(name){return (bot.currentWindow||bot.inventory).items().filter(i=>i.name===name).reduce((n,i)=>n+i.count,0)}
async function equipKey(){const key=bot.inventory.items().find(i=>i.name==='tripwire_hook');assert(key,'physical key');await bot.equip(key,'hand')}
async function activate(){const block=await wait(()=>bot.blockAt(new Vec3(0,64,2)));await bot.lookAt(new Vec3(.5,64.5,2.5),true);await sleep(200);await bot.activateBlock(block);}
function punch(pos=new Vec3(0,64,2)) {bot._client.write('block_dig',{status:0,location:pos,face:1,sequence:1});}
function model(slot) {
 assert(slot,'model item exists');
 const nbt=req('prismarine-nbt');
 const components=slot.components.map(c=>{try{return nbt.simplify(c.data)}catch{return null}}).filter(v=>v&&typeof v==='object');
 const found=components.find(v=>v.species&&v.aspects);assert(found,'Cobblemon model component present: '+JSON.stringify(components));return found;
}
async function main(){
 await connect();
 if(process.argv[2]==='denied'){
  const messages=[];bot.on('messagestr',m=>messages.push(m));punch();await wait(()=>messages.some(m=>m.includes('Você não tem permissão')));
  assert(!bot.currentWindow);ok('preview permission denial is respected');bot.quit();return;
 }
 if(!fs.existsSync(config))await cmd('crates create preview_models','criada e carregada');
 const original=fs.readFileSync(config,'utf8'),base=JSON.parse(original);
 try {
  base.rewards=[{name:'Preview Diamond',type:'item',item:'minecraft:diamond',chance:100}];fs.writeFileSync(config,JSON.stringify(base));await cmd('crates reload','recarregada');
  await cmd('gamemode creative');await cmd('tp @s 0.5 65 0.5');await cmd('fill -2 63 -2 4 63 5 minecraft:stone');await cmd('setblock 0 64 2 minecraft:chest');await bot.lookAt(new Vec3(.5,64.5,2.5),true);await sleep(250);await cmd('crates setblock preview_models','registrada com sucesso');
  await cmd('clear @s');await cmd('gamemode survival');let before=records().length;
  punch();await wait(()=>bot.currentWindow);assert.equal(bot.currentWindow.type,'minecraft:generic_9x6');assert.equal(records().length,before);assert.equal(bot.currentWindow.slots[0].name,'diamond');ok('empty-hand survival punch opens preview without creating a reward');bot.closeWindow(bot.currentWindow);await sleep(700);
  await cmd('gamemode creative');punch();await wait(()=>bot.currentWindow);assert.equal(bot.currentWindow.type,'minecraft:generic_9x6');bot.closeWindow(bot.currentWindow);await cmd('execute if block 0 64 2 minecraft:chest run tell @s PREVIEW_BLOCK_OK','PREVIEW_BLOCK_OK');ok('creative punch opens preview and does not break the crate');await sleep(700);
  await cmd('give @s minecraft:diamond_axe');await sleep(250);await bot.equip(bot.inventory.items().find(i=>i.name==='diamond_axe'),'hand');punch();await sleep(600);assert(!bot.currentWindow);await cmd('execute if block 0 64 2 minecraft:chest run tell @s TOOL_BLOCK_OK','TOOL_BLOCK_OK');ok('tool punch neither opens preview nor breaks the block');
  await cmd('clear @s');await cmd('setblock 2 64 2 minecraft:stone');punch(new Vec3(2,64,2));await sleep(300);bot._client.write('block_dig',{status:1,location:new Vec3(2,64,2),face:1,sequence:2});assert(!bot.currentWindow);ok('ordinary blocks do not open crate previews');
  await cmd('crates givekey @s preview_models 1','Itens entregues');await equipKey();punch();await sleep(600);assert(!bot.currentWindow);assert.equal(count('tripwire_hook'),1);ok('left click with a key does not consume it or open a preview');
  const itemPrevious=new Set(records().map(t=>t.id));await activate();await wait(()=>bot.currentWindow);assert.equal(bot.currentWindow.type,'minecraft:generic_9x3');const window=bot.currentWindow.id;punch();await sleep(200);assert.equal(bot.currentWindow.id,window);assert.equal(bot.currentWindow.type,'minecraft:generic_9x3');ok('right click still starts roulette; an empty-hand attack cannot replace an active opening');
  await wait(()=>records().some(t=>!itemPrevious.has(t.id)&&t.reward.name==='Preview Diamond'&&t.state==='DELIVERED'),15000);bot.closeWindow(bot.currentWindow);
  base.rewards=[{name:'Normal Tyranitar',type:'pokemon',species:'tyranitar',shiny:false,level:60,chance:50,icon:'minecraft:emerald'},{name:'Shiny Tyranitar',type:'pokemon',species:'tyranitar',shiny:true,level:60,chance:50,icon:'minecraft:dragon_head'}];fs.writeFileSync(config,JSON.stringify(base));await cmd('crates reload','recarregada');await cmd('clear @s');await sleep(700);before=records().length;
  punch();await wait(()=>bot.currentWindow);const normal=model(bot.currentWindow.slots[0]),shiny=model(bot.currentWindow.slots[1]);
  assert.equal(normal.species,'cobblemon:tyranitar');assert.equal(shiny.species,'cobblemon:tyranitar');assert(!normal.aspects.includes('shiny'));assert(shiny.aspects.includes('shiny'));assert.equal(records().length,before);ok('preview contains native normal and shiny Tyranitar model components, without generating Pokémon');
  const inventoryBefore=bot.inventory.items().length;await bot.clickWindow(0,0,1);await sleep(200);assert.equal(bot.inventory.items().length,inventoryBefore);ok('Pokémon model display items cannot be extracted');bot.closeWindow(bot.currentWindow);
  base.rewards=[base.rewards[1]];base.rewards[0].chance=100;fs.writeFileSync(config,JSON.stringify(base));await cmd('crates reload','recarregada');await cmd('crates givekey @s preview_models 1','Itens entregues');await equipKey();const previous=new Set(records().map(t=>t.id));await activate();await wait(()=>bot.currentWindow);
  for(let slot=9;slot<=17;slot++){const value=model(bot.currentWindow.slots[slot]);assert.equal(value.species,'cobblemon:tyranitar');assert(value.aspects.includes('shiny'));}ok('all nine roulette slots carry the configured shiny Pokémon model');
  await wait(()=>records().some(t=>!previous.has(t.id)&&t.state==='DELIVERED'),15000);assert(model(bot.currentWindow.slots[13]).aspects.includes('shiny'));assert.equal(records().filter(t=>!previous.has(t.id)).length,1);ok('winning Pokémon model stays centered and exactly one real reward is delivered');
  bot.closeWindow(bot.currentWindow);await cmd('gamerule spawnRadius 0');await cmd('setworldspawn 0 64 0');
 } finally {fs.writeFileSync(config,original);}
 bot.quit();
}
main().then(()=>{fs.writeFileSync(path.join(run,'preview-models-'+(process.argv[2]||'prepare')+'-results.json'),JSON.stringify({results},null,2));console.log('PREVIEW MODELS PASSED',results.length);process.exit(0)}).catch(e=>{console.error(e);if(bot)bot.quit();process.exit(1)});
