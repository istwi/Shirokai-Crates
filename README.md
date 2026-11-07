# Shirokai Crates

Mod de servidor para Minecraft **1.21.1 / Fabric / Java 21**. O administrador registra um bloco existente e o jogador abre a caixa clicando nesse bloco com uma chave física na mão principal. Os menus nunca iniciam uma abertura.

Dependências externas, sem cópias embutidas no JAR:

| Dependência | Versão |
|---|---|
| Fabric Loader | 0.20.1 no teste de compatibilidade; mínimo 0.17.2 |
| Fabric API | 0.116.6+1.21.1 usada na validação |
| Fabric Language Kotlin | 1.13.6+kotlin.2.2.20 |
| Cobblemon | 1.8.3+1.21.1 |
| Impactor | 5.3.5 para Fabric 1.21.1 |
| LuckPerms | 5.4.140 para Fabric 1.21.1 |

O Shirokai Crates não precisa ser instalado no cliente. O cliente continua precisando do Cobblemon para o servidor Cobblemon. PlaceholderAPI é opcional; não há expansão própria de placeholders nesta versão. O Impactor já inclui bibliotecas auxiliares: preserve os JARs oficiais e não adicione cópias de APIs por conta própria.

## Instalação

**Arquivo para instalar: `shirokai-crates-0.1.2.jar`.** Não instale `*-sources.jar` nem o ZIP do projeto.

Para atualizar, pare o servidor, retire de `mods/` os JARs anteriores do Shirokai Crates (inclusive `shirokai-crates-0.1.0-sources.jar`, caso esteja lá) e coloque somente o novo JAR executável. Preserve `config/shirokaicrates/`, o mundo e os dados de jogadores. Não há migração nem redefinição de chaves, registros ou recompensas nesta correção.

A versão 0.1.2 adiciona `/crates create <id>` para gerar caixas editáveis pelo jogo.
A versão 0.1.1 impede que arquivos de fontes sejam identificados como mods e valida o conteúdo dos JARs durante `build`. Detalhes da causa e da validação estão em [docs/CORRECAO-0.1.1.md](docs/CORRECAO-0.1.1.md).

1. Instale as dependências da tabela no servidor Fabric 1.21.1.
2. Coloque `build/libs/shirokai-crates-0.1.2.jar` em `mods/`.
3. Inicie o servidor. Na primeira inicialização, os exemplos são criados em `config/shirokaicrates/`.
4. Edite os JSONs, execute `/crates reload`, coloque um bloco, olhe para ele e execute `/crates setblock shiny1`.
5. Entregue uma chave com `/crates givekey Jogador shiny1 1`.

Os cinco exemplos iniciais possuem apenas itens vanilla para não depender de moedas configuradas. Exemplos de Pokémon, moedas, itens Cobblemon e comandos estão em [examples/reward-types.json](examples/reward-types.json); copie um objeto para `rewards` da caixa desejada. Cadastre `impactor:tokens` no próprio Impactor antes de ativar esse prêmio. Não há conversão automática entre moedas.

## Comandos e permissões

Os comandos abaixo são executados por jogadores. Administradores OP recebem permissões administrativas quando o nó está indefinido. Negativas explícitas do LuckPerms são respeitadas; `shirokaicrates.admin` concede acesso geral.

| Comando | Nó |
|---|---|
| `/crates` | `shirokaicrates.use` |
| `/crates preview <id>` | `shirokaicrates.preview` |
| `/crates fragments` | `shirokaicrates.fragments` |
| `/crates pending` | `shirokaicrates.pending` |
| `/crates create <id>` | `shirokaicrates.admin` |
| `/crates setblock <id>` | `shirokaicrates.admin` |
| `/crates removeblock` | `shirokaicrates.admin` |
| `/crates givekey <jogador> <id> <quantidade>` | `shirokaicrates.give` |
| `/crates givefragment <jogador> <id> <quantidade>` | `shirokaicrates.give` |
| `/crates reload` | `shirokaicrates.reload` |
| `/crates list` | `shirokaicrates.admin` |
| `/crates history <jogador>` | `shirokaicrates.admin` |
| `/crates reconcile <UUID> delivered\|retry\|cancel` | `shirokaicrates.admin` |

Os quatro nós de consulta/uso são permitidos por padrão quando indefinidos. Cada caixa pode exigir um nó adicional em `permission`. O histórico mostra as últimas 30 entradas do jogador. Comandos com `<id>` sugerem os IDs ativos.

## Configuração

Para criar uma caixa dentro do jogo, use `/crates create shiny3` como administrador.
O comando cria `config/shirokaicrates/crates/shiny3.json` e carrega a caixa imediatamente,
com chave, fragmentos e um diamante de exemplo. Não sobrescreve arquivos existentes.
Edite nome, itens, descrições e recompensas nesse JSON e aplique com `/crates reload`.
A chave já fica configurada; para receber o item físico, use `/crates givekey @s shiny3 1`.
O comando não coloca um bloco: olhe para um bloco existente e use `/crates setblock shiny3`.

```text
config/shirokaicrates/
  config.json
  messages.json
  animations/horizontal.json
  crates/
    shiny1.json
    shiny2.json
    lendaria.json
    mega.json
    evento_natal.json
```

**O nome do arquivo é o ID.** Não adicione um campo `id`. Nomes aceitos: letras minúsculas, números, `_` e `-`, com até 64 caracteres. Arquivos com symlink, campos JSON duplicados, IDs inválidos, pesos não positivos e referências inexistentes são rejeitados. O reload valida a coleção inteira antes de substituir a configuração ativa. Blocos guardam apenas o ID e resolvem a configuração atual; aberturas iniciadas mantêm o prêmio anterior.

Uma caixa contém `name`, `key`, `fragment`, `fragmentsRequired`, `permission` e `rewards`. A quantidade padrão é 4 fragmentos. `keyType` é opcional e, quando explicitamente compartilhado, deve ter a mesma definição de chave em todas as caixas envolvidas. Fragmentos continuam associados ao ID individual da caixa.

Prêmios usam `name`, `type`, `rarity`, `weight` e `icon` opcional. Raridades: `common`, `rare`, `epic`, `legendary`, `special`. A chance exibida é `weight / soma dos pesos`.

| Tipo | Campos adicionais |
|---|---|
| `item` | `item`, `count` (1..2304), `components` opcional |
| `pokemon` | `species`, `level`, `shiny`, `nature`, `ability`, IVs individuais; ou `properties` da API Cobblemon |
| `currency` | `currency`: `impactor:dollars` ou `impactor:tokens`; `amount` positivo |
| `command` | `command` sem `/`; placeholders `{player}`, `{uuid}`, `{transaction}` |

Os componentes são objetos JSON com IDs completos, interpretados pelo parser de itens do Minecraft. Prêmios por comando são comandos de console previamente configurados por administradores, validados no reload. O resultado numérico positivo é a confirmação disponível; comandos com efeitos parciais devem ser auditados em caso de falha.

`animations/horizontal.json` controla duração (ticks), intervalo inicial, desaceleração, sons, item de destaque e partículas. `messages.json` controla as mensagens e o anúncio global. A roleta é visual: o resultado já foi sorteado e persistido antes de aparecer.

## Persistência e operação

Dados ficam separados da configuração em `<mundo>/shirokaicrates/`:

- `blocks.json`: dimensão, coordenadas, ID e tipo do bloco.
- `transactions/<UUID>.json`: jogador, snapshot do prêmio, data, estado e anúncio.
- `token-secret.bin`: segredo de assinatura HMAC dos itens. **Preserve esse arquivo nos backups**; removê-lo invalida chaves e fragmentos existentes.

Os recibos de consumo/entrega ficam junto do inventário no NBT vanilla do jogador. Faça backup consistente do mundo completo, playerdata, dados Cobblemon, Impactor e configurações; restaurar apenas uma parte quebra a correspondência entre recibos e inventários.

Estados: `RESERVED → READY → DELIVERING → DELIVERED`. `READY` pode ser resgatado em `/crates pending`. `UNCERTAIN` preserva o prêmio e bloqueia repetição automática. `CANCELLED` indica uma operação não concluída.

Em `UNCERTAIN`, compare o UUID da transação com os registros do provedor antes de usar `/crates reconcile`. Use `delivered` somente após confirmar a entrega; `retry` somente após confirmar que ela **não** aconteceu; `cancel` encerra administrativamente a pendência. Todas essas decisões são registradas. A API do Impactor confirma depósito e salvamento; a API Cobblemon confirma a aceitação no armazenamento e mantém o UUID e atributos do Pokémon fixos no diário.

A retenção remove apenas histórico terminal, nunca prêmios pendentes. O segredo e os dados não são apagados por reload. Falhas críticas de escrita bloqueiam novas operações; corrija a causa e reinicie. Configuração inválida na inicialização mantém os blocos protegidos e pode ser corrigida com reload. Corrupção do próprio registro físico exige recuperação de backup.

As proteções são locais aos blocos registrados. A interação é registrada numa fase posterior ao callback padrão do Fabric, permitindo que proteções como Flan recusem primeiro a interação. Pistões, automação por hoppers, mudanças do bloco e explosões são bloqueados por mixins específicos de 1.21.1. Baús duplos não podem ser registrados; use um baú individual ou outro bloco. Remova o registro antes de modificar o bloco como administrador.

## Desenvolvimento e testes

```sh
./gradlew test build
```

Requer um **JDK** 21, não apenas um JRE. O wrapper fixa Gradle 8.12 e confere o SHA-256 da distribuição. O workflow de GitHub Actions compila, testa e disponibiliza os artefatos; não publica releases automaticamente.

Para a validação local contra os JARs de produção:

```sh
python3 scripts/run-validation.py
# Em outro terminal, na raiz do repositório:
npm ci --prefix tests/integration
node tests/integration/smoke.cjs
node tests/integration/integrations.cjs
```

Esse servidor usa um mundo de testes em `run/`, somente em loopback, com autenticação offline e o jogador de teste `ShirokaiTest` como OP. Os testes alteram blocos e inventário desse mundo e restauram o JSON da caixa ao terminar. Nunca aponte esses testes para um mundo de produção. O teste de moedas exige `impactor:tokens` configurado no mundo de validação. Os resultados ficam em `run/*results.json`.

Loom 1.9 não remapeia corretamente os metadados de reflexão Kotlin desse Cobblemon em `runServer`; o script usa os artefatos originais e o JAR compilado. Use o checkout existente; cada tarefa na nuvem já possui isolamento e não precisa de Git worktree.

Veja [docs/TESTES.md](docs/TESTES.md) para os resultados executados e limitações.
