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
 await connect();ok('login with Cobblemon + Impactor + LuckPerms');
 await cmd('gamemode creative');await cmd('tp @s 0.5 65 0.5');await cmd('fill -2 63 -2 4 63 5 minecraft:stone');
 await cmd('crates list','shiny1');
 const suggestions=await bot.tabComplete('/crates setblock ',true,false);assert(suggestions.some(s=>(s.match||s)==='shiny1'));assert(!suggestions.some(s=>(s.match||s)==='missing'));ok('autocomplete uses loaded JSON IDs');
 await cmd('setblock 0 64 2 minecraft:chest');await sleep(250);await bot.lookAt(new Vec3(.5,64.5,2.5),true);await sleep(200);
 await cmd('crates setblock missing','ID inexistente');ok('reject missing crate ID');
 await cmd('crates setblock shiny1','registrada com sucesso');await wait(()=>blocks().some(b=>b.crateId==='shiny1'));ok('register looked-at physical block');
 await cmd('setblock 2 64 2 minecraft:barrel');await bot.lookAt(new Vec3(2.5,64.5,2.5),true);await sleep(200);await cmd('crates setblock shiny1','registrada com sucesso');
 assert.equal(blocks().filter(b=>b.crateId==='shiny1').length,2);ok('several blocks share the filename ID');
 const changed=JSON.parse(original);changed.name='Smoke Changed';changed.rewards=[{name:'Smoke Diamond',type:'item',item:'minecraft:diamond',count:3,rarity:'common',weight:1}];
 fs.writeFileSync(config,JSON.stringify(changed));await cmd('crates reload','recarregada');assert.equal(blocks().filter(b=>b.crateId==='shiny1').length,2);ok('reload preserves physical registrations');
 fs.writeFileSync(config,'{bad');await cmd('crates reload','Reload rejeitado');assert.equal(blocks().length,2);ok('invalid reload preserves registrations and active snapshot');fs.writeFileSync(config,JSON.stringify(changed));
 await bot.lookAt(new Vec3(2.5,64.5,2.5),true);await sleep(200);await cmd('crates setblock shiny2','registrada com sucesso');assert.equal(blocks().filter(b=>b.crateId==='shiny1').length,1);ok('replace one registration only');
 await cmd('crates removeblock','bloco foi preservado');await cmd('execute if block 2 64 2 minecraft:barrel run tell @s BARREL_OK','BARREL_OK');ok('remove registration without destroying block');
 await cmd('clear @s');await activate();await sleep(300);assert(!bot.currentWindow);ok('no key does not open vanilla chest or roulette');
 await cmd('crates givekey @s shiny2 1','Itens entregues');await equipKey();const before=records().length;await activate();await sleep(300);assert.equal(records().length,before);ok('wrong physical key rejected');
 await cmd('clear @s');await cmd(`give @s minecraft:tripwire_hook[minecraft:custom_name='{"text":"✦ Chave Shiny I"}'] 1`);await equipKey();await activate();await sleep(200);assert.equal(records().length,before);ok('renamed unsigned item cannot forge a key');
 await cmd('clear @s');await cmd('crates givekey @s shiny1 3','Itens entregues');await wait(()=>count('tripwire_hook')===3);await equipKey();
 const diamondBefore=count('diamond');await activate();await wait(()=>bot.currentWindow);assert.equal(bot.currentWindow.type,'minecraft:generic_9x6');ok('correct main-hand key starts horizontal roulette');
 await activate();bot._client.write('block_place',{hand:1,location:{x:0,y:64,z:2},direction:1,cursorX:.5,cursorY:.5,cursorZ:.5,insideBlock:false,sequence:77});
 await wait(()=>records().some(t=>t.reward.name==='Smoke Diamond'&&t.state==='DELIVERED'),15000);
 await wait(()=>count('diamond')===diamondBefore+3);assert.equal(count('tripwire_hook'),2);assert.equal(records().length,before+1);ok('one key, one result: repeated and offhand interaction');
 bot.closeWindow(bot.currentWindow);await cmd('crates preview shiny1');await wait(()=>bot.currentWindow);const itemsBefore=count('diamond');await bot.clickWindow(0,0,0);await sleep(200);assert.equal(count('diamond'),itemsBefore);ok('preview is read-only');bot.closeWindow(bot.currentWindow);
 await cmd('crates givefragment @s shiny1 4','Itens entregues');await wait(()=>count('amethyst_shard')===4);await cmd('crates fragments');await wait(()=>bot.currentWindow);await bot.clickWindow(3,0,0);await wait(()=>count('amethyst_shard')===0&&count('tripwire_hook')===3);ok('four same fragments convert to one physical key');bot.closeWindow(bot.currentWindow);
 await cmd('gamemode survival');await cmd('give @s minecraft:diamond_axe');await sleep(200);await bot.equip(bot.inventory.items().find(i=>i.name==='diamond_axe'),'hand');
 await Promise.race([bot.dig(bot.blockAt(new Vec3(0,64,2)),true).catch(()=>{}),sleep(4000)]);bot.stopDigging();
 await cmd('execute if block 0 64 2 minecraft:chest run tell @s CHEST_PROTECTED','CHEST_PROTECTED');ok('registered chest resists a valid-duration mining attempt');
 // Fill inventory without consuming the last key stack; 3 keys -> 2 leaves the slot occupied.
 await cmd('clear @s minecraft:diamond');await cmd('give @s minecraft:stone 2304');await equipKey();await activate();
 const previousDelivered=records().filter(t=>t.state==='DELIVERED').length;
 await wait(()=>records().some(t=>t.state==='READY'&&t.detail==='Sem espaço disponível'),15000);assert.equal(records().filter(t=>t.state==='DELIVERED').length,previousDelivered);ok('full inventory leaves durable pending reward');
 if(bot.currentWindow)bot.closeWindow(bot.currentWindow);await cmd('clear @s minecraft:stone');await cmd('crates pending');await wait(()=>bot.currentWindow);await bot.clickWindow(0,0,0);await wait(()=>records().filter(t=>t.state==='DELIVERED').length===previousDelivered+1);ok('pending reward claimed once');if(bot.currentWindow)bot.closeWindow(bot.currentWindow);
 const last=records().length;await equipKey();await activate();await wait(()=>records().length===last+1&&records().some(t=>t.state==='READY'));const disconnected=records().find(t=>t.state==='READY');bot.quit();await sleep(500);await connect();
 assert.equal(records().find(t=>t.id===disconnected.id).reward.name,'Smoke Diamond');await cmd('crates pending');await wait(()=>bot.currentWindow);await bot.clickWindow(0,0,0);await wait(()=>records().find(t=>t.id===disconnected.id).state==='DELIVERED');ok('disconnect preserves the same transaction and reward');
 bot.quit();
}
main().then(()=>{fs.writeFileSync(config,original);fs.writeFileSync(path.join(run,'smoke-results.json'),JSON.stringify({results},null,2));console.log('SMOKE PASSED',results.length);process.exit(0)}).catch(e=>{console.error('SMOKE FAILED',e);try{fs.writeFileSync(config,original)}catch{};fs.writeFileSync(path.join(run,'smoke-results.json'),JSON.stringify({results,error:e.stack},null,2));if(bot)bot.quit();process.exit(1)});
