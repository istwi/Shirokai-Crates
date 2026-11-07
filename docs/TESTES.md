# Relatório de validação original — 0.1.0

A validação da correção 0.1.1 está em [CORRECAO-0.1.1.md](CORRECAO-0.1.1.md). Os resultados abaixo são históricos da versão 0.1.0; não representam repetição de todas as suítes na versão corrigida.

**55 verificações aprovadas na implementação original:** 18 testes JUnit e 37 verificações conectadas a um servidor dedicado real. Nenhum teste JUnit falhou, apresentou erro ou foi ignorado no resultado final. O resumo legível por máquina está em [evidence/results.json](evidence/results.json).

Servidor validado: Minecraft 1.21.1, Java 21, Fabric Loader 0.17.2, Fabric API 0.116.6+1.21.1, Cobblemon 1.8.3+1.21.1, Kotlin 1.13.6+kotlin.2.2.20, Impactor 5.3.5 e LuckPerms 5.4.140. As versões dos mods de teste foram baixadas dos artefatos oficiais com verificação dos hashes publicados.

| Conjunto | Verificações | Resultado |
|---|---:|---|
| JUnit: configurações, IDs, validação, registros físicos, snapshots, probabilidades, estados, concorrência, retenção e falha de disco | 18 | Aprovado |
| Cliente: comandos, autocomplete, blocos, reload, chaves, falsificação por nome, roleta, prévia, fragmentos, inventário cheio, pendências e desconexão | 20 | Aprovado |
| APIs: item Cobblemon, Pokémon materializado, anúncios, duas moedas, comando e negativa explícita LuckPerms | 7 | Aprovado |
| Pistão com bloco normalmente móvel, explosão real de TNT e dois jogadores abrindo o mesmo bloco | 3 | Aprovado |
| SIGKILL durante roleta, preservação do bloco/chave/prêmio, resgate após reinício e rejeição de segundo resgate | 4 | Aprovado |
| Falha real de escrita no diretório do diário, preservação da chave e login após o erro | 2 | Aprovado |
| Hopper tentando extrair um diamante do inventário de um baú registrado | 1 | Aprovado |

O teste de queda encerrou **à força** somente o servidor local de validação após a transação chegar a `READY`, durante a roleta. Após reiniciar, o UUID e o snapshot do prêmio permaneceram iguais, a chave continuou consumida e o resgate entregou exatamente dois diamantes. Uma segunda tentativa não entregou mais itens.

A falha de persistência foi produzida removendo temporariamente a permissão de escrita do diretório de transações. A abertura foi recusada sem consumir a chave; o jogador conseguiu desconectar e entrar novamente. As permissões do diretório foram restauradas e o servidor foi reiniciado para sair do bloqueio de segurança.

O teste de economia verificou a confirmação de depósito e salvamento pela API para `impactor:dollars` e `impactor:tokens`. A existência de ambas as moedas também foi confirmada no arquivo persistido da conta de teste. O Pokémon foi materializado antes da reserva, incluindo UUID, espécie, nível, shiny, natureza e IVs, e foi aceito pelo armazenamento do Cobblemon.

## Reproduzir

```sh
./gradlew test build projectArchive
python3 scripts/run-validation.py
# Em outro terminal, na raiz do checkout:
npm ci --prefix tests/integration
node tests/integration/smoke.cjs
node tests/integration/integrations.cjs
node tests/integration/protection.cjs
node tests/integration/automation.cjs
node tests/integration/restart.cjs prepare
# O comando anterior mata o servidor de validação. Inicie-o novamente antes de verificar:
python3 scripts/run-validation.py
node tests/integration/restart.cjs verify
node tests/integration/failure.cjs
# Reinicie o servidor depois do teste de falha para liberar novas aberturas.
```

Execute as suítes **sequencialmente**. O teste de integração exige `impactor:tokens` cadastrado e `luckperms.*` concedido ao jogador sintético `ShirokaiTest` pelo console. O script inicializa esse jogador como OP no mundo de validação; o LuckPerms exige sua própria concessão para editar nós. Preserve as proteções antispam do LuckPerms. Não execute esses scripts contra um servidor real de jogadores.

## Limites da validação

- A apresentação visual e os sons não foram inspecionados por um humano num cliente gráfico Cobblemon. A abertura, o protocolo de janela e a entrega foram exercitados pelo cliente automatizado.
- Flan não estava instalado no servidor de teste. A ordenação da interação após a fase padrão de proteções foi implementada; a combinação exata do modpack ainda precisa de homologação.
- Houve dois clientes simultâneos e 32 jogadores independentes nos testes de concorrência do diário. Isso não representa um teste de carga com dezenas de clientes conectados.
- O desligamento abrupto foi testado durante a animação, com prêmio de item. Não foram injetadas quedas em cada instrução de gravação nem em cada janela de falha dos provedores externos. Estados incertos ficam reservados para reconciliação administrativa.
- A durabilidade dos backends próprios do Cobblemon, Impactor e comandos não constitui uma transação distribuída. Veja [ARQUITETURA.md](ARQUITETURA.md).
- Os testes de rede adaptam o cliente de protocolo para ignorar receitas, metadados de entidades, árvore de comandos e ícones de avanços personalizados dos mods. Os comandos, o autocomplete, os cliques e o inventário da caixa são reais.

Nenhuma release no GitHub nem nova publicação do ambiente foi realizada.
