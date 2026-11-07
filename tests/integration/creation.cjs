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
const config=path.join(run,'config/shirokaicrates/crates/creation_test.json');
function ok(name){results.push({name,status:'passed'});console.log('PASS',name)}
async function wait(fn,timeout=10000){const end=Date.now()+timeout;while(Date.now()<end){const value=fn();if(value)return value;await sleep(50)}throw Error('Timeout: '+fn.toString())}
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
 if(process.argv[2]==='denied'){
  await cmd('crates create denied_test');await sleep(500);assert(!fs.existsSync(path.join(run,'config/shirokaicrates/crates/denied_test.json')));
  ok('non-administrator cannot create crates');bot.quit();return;
 }
 if(process.argv[2]==='verify'){
  await cmd('crates list','creation_test');
  await cmd('crates givekey @s creation_test 1','Itens entregues');
  assert.equal(JSON.parse(fs.readFileSync(config)).rewards[0].item,'minecraft:emerald');
  ok('created crate and edited reward survive server restart');bot.quit();return;
 }
 assert(!fs.existsSync(config),'use a fresh local validation world');
 await cmd('crates create creation_test','criada e carregada');
 const original=fs.readFileSync(config,'utf8'),created=JSON.parse(original);
 assert.equal(created.key.item,'minecraft:tripwire_hook');assert.equal(created.fragment.item,'minecraft:amethyst_shard');
 ok('command creates JSON with key and fragments');
 const other=path.join(run,'config/shirokaicrates/crates/shiny1.json'),otherOriginal=fs.readFileSync(other,'utf8');
 try {
  fs.writeFileSync(other,'{broken');
  await cmd('crates create syntax_failure','JSON criado, mas o carregamento foi rejeitado');
  await cmd('crates list','creation_test');
  ok('invalid JSON preserves the active configuration after creation fails to load');
  const invalid=JSON.parse(otherOriginal);invalid.rewards[0].item='minecraft:missing_item';
  fs.writeFileSync(other,JSON.stringify(invalid));
  await cmd('crates create registry_failure','JSON criado, mas o carregamento foi rejeitado');
  await cmd('crates list','creation_test');
  ok('runtime validation failure preserves the active configuration');
 } finally { fs.writeFileSync(other,otherOriginal); }
 await cmd('crates reload','recarregada');
 fs.unlinkSync(config);
 await cmd('crates create creation_test','já está carregada');
 assert(!fs.existsSync(config));fs.writeFileSync(config,original);
 ok('loaded crate cannot be silently reset if its JSON was removed');

 await cmd('crates create creation_test','nada foi sobrescrito');assert.equal(fs.readFileSync(config,'utf8'),original);ok('existing JSON preserved');
 await cmd('crates create INVALID','ID inválido');ok('invalid ID refused');
 await cmd('clear @s');await cmd('crates givekey @s creation_test 1','Itens entregues');await wait(()=>count('tripwire_hook')===1);ok('physical key available immediately');
 await cmd('crates givefragment @s creation_test 4','Itens entregues');await wait(()=>count('amethyst_shard')===4);ok('physical fragments available immediately');
 created.rewards=[{name:'Created Emerald',type:'item',item:'minecraft:emerald',count:2,rarity:'common',weight:1}];
 fs.writeFileSync(config,JSON.stringify(created));await cmd('crates reload','recarregada');
 await cmd('gamemode creative');await cmd('tp @s 0.5 65 0.5');await cmd('fill -2 63 -2 4 63 5 minecraft:stone');await cmd('setblock 0 64 2 minecraft:chest');
 await bot.lookAt(new Vec3(.5,64.5,2.5),true);await sleep(200);await cmd('crates setblock creation_test','registrada com sucesso');
 await equipKey();await activate();await wait(()=>records().some(t=>t.crateId==='creation_test'&&t.state==='DELIVERED'),15000);
 await wait(()=>count('emerald')===2);assert.equal(count('tripwire_hook'),0);ok('created key opens registered block and delivers edited reward');
 bot.quit();
}
main().then(()=>{fs.writeFileSync(path.join(run,'creation-'+(process.argv[2]||'prepare')+'-results.json'),JSON.stringify({results},null,2));console.log('CREATION PASSED',results.length);process.exit(0)}).catch(e=>{console.error(e);if(bot)bot.quit();process.exit(1)});
