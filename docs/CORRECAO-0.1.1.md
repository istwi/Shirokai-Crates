# Correção de empacotamento — Shirokai Crates 0.1.1

## Causa comprovada nos artefatos publicados

O `shirokai-crates-0.1.0-sources.jar` disponibilizado na release continha `fabric.mod.json` e `shirokaicrates.mixins.json` copiados dos recursos de desenvolvimento. A versão permanecia `${version}` nesse arquivo, e os Mixins eram arquivos `.java`, não classes compiladas. Ao ser colocado em `mods/`, esse arquivo podia ser identificado pelo Fabric como um mod executável e anunciar classes que ele não continha.

O arquivo de fontes publicado foi baixado e inspecionado nesta correção: sua versão era literalmente `${version}` e faltavam as oito classes de Mixins. Isso corresponde aos dois sintomas relatados. O JAR executável de 0.1.0, por outro lado, continha `WorldMixin.class` e a versão `0.1.0`; seu checksum local coincidia com o artefato publicado. O arquivo instalado no servidor do usuário não foi anexado, portanto não foi possível comparar seu hash diretamente.

A expansão de versão já ocorria no JAR executável; o recurso bruto estava indevidamente presente no arquivo de fontes. A correção não consiste em remover a proteção do mundo.

## Alterações

- Versão corrigida: **0.1.1**.
- Exclusão de `fabric.mod.json` e `shirokaicrates.mixins.json` do JAR de fontes. Esse arquivo deixa de ser identificável como mod pelo Fabric.
- Release e artefatos de instalação não oferecem mais `*-sources.jar`; o código completo continua no ZIP do projeto e no GitHub.
- Expansão da versão com charset UTF-8 explícito e testes do conteúdo final, após as tarefas de remapeamento do Loom.
- Três novos testes verificam a versão final e os entrypoints compilados, as oito classes de Mixins para Java 21 e a ausência de descritores de mod no arquivo de fontes.
- O launcher de validação usa Fabric Loader **0.20.1**, escolhe o JAR executável a partir da versão processada e permite um diretório isolado de testes. JARs executáveis anteriores são preservados com sufixo `.disabled` no ambiente de validação, sem sobrescrever backups existentes.

`WorldMixin` permanece registrado como `dev.shirokaicrates.mixin.WorldMixin`, com alvo `net.minecraft.world.World` e injeção em `setBlockState(BlockPos, BlockState, int, int): boolean`. O pacote, o alvo e o corpo da proteção não precisaram ser removidos ou renomeados. Todos os outros Mixins continuam registrados. Não há referências a classes `net.minecraft.client` nos arquivos Java dos Mixins.

Nenhuma dependência de Fabric Permissions API foi adicionada ou substituída. O ambiente manteve `fabric-permissions-api-v0 0.3.1`, usado pelos mods existentes, LuckPerms 5.4.140 e Impactor 5.3.5. Nenhuma dessas APIs é empacotada dentro do JAR Shirokai.

## Arquivo exato para instalar

Instale somente **`shirokai-crates-0.1.1.jar`**.

1. Pare o servidor.
2. Retire de `mods/` as cópias anteriores do Shirokai Crates, incluindo `shirokai-crates-0.1.0-sources.jar`, se presente. Não deixe duas versões do mod instaladas.
3. Coloque `shirokai-crates-0.1.1.jar` em `mods/` e inicie o servidor.
4. Preserve integralmente `config/shirokaicrates/`, o mundo, `playerdata`, `shirokaicrates/transactions`, `blocks.json` e `token-secret.bin`.

Não há migração ou redefinição de dados nesta correção. Não remova o segredo das chaves, os registros físicos ou as pendências. O ZIP do projeto não é um mod instalável.

## Validação

Os resultados desta correção estão em `docs/evidence/patch-0.1.1.json`. O relatório original de 0.1.0 permanece separado em `docs/TESTES.md`; as 55 verificações antigas não são apresentadas como uma nova execução integral.

O build obrigatório foi executado com `./gradlew clean build`, Java 21: **21 testes JUnit passaram, sem falhas nem testes ignorados**. Também passaram **10 verificações conectadas** em dois inícios reais do servidor com Fabric Loader 0.20.1. Não ocorreu `WorldMixin was not found` nem versão literal `${version}` nesses inícios. A parada controlada terminou com todos os mundos salvos, e o cliente entrou novamente após o reinício.

Os hashes de **39 arquivos críticos** do mundo original — configurações Shirokai, playerdata, segredo de assinatura e registros/transações — foram comparados antes e depois: nenhuma diferença. Apenas as cópias de homologação foram modificadas.

 Os testes inspecionam os arquivos remapeados reais de `build/libs/`, não apenas os recursos ou fontes. O servidor dedicado de validação usa Minecraft 1.21.1, Fabric Loader 0.20.1, Cobblemon 1.8.3+1.21.1, Fabric API 0.116.6+1.21.1, LuckPerms 5.4.140 e Impactor 5.3.5.

Os testes conectados usam uma cópia isolada do mundo em outra porta. Não alteram os dados do mundo original. São conferidos login, `/crates setblock shiny1`, uso de uma chave física da versão anterior, consumo de uma chave, roleta, proteção contra mineração e substituição direta do bloco, persistência após reiniciar e resgate da mesma recompensa pendente sem consumir uma segunda chave.

Para reproduzir numa cópia de homologação, configure `SHIROKAI_VALIDATION_DIR` no launcher e `MC_TEST_RUN`/`MC_TEST_PORT` no cliente. Execute `tests/integration/patch-011.cjs prepare`, pare o servidor pelo console após a desconexão do cliente, reinicie e execute `tests/integration/patch-011.cjs verify`. Não use esse teste em um mundo de produção.

### Observação de terceiros

Uma tentativa de parada com o jogador ainda conectado produziu `RejectedExecutionException` no scheduler do LuckPerms 5.4.140 durante seu callback de desconexão. Esse problema de encerramento não é o erro de preparação do WorldMixin e não foi ocultado nem considerado uma parada limpa. A sequência controlada de homologação desconecta o cliente antes de parar o servidor. A correção não modifica bibliotecas de permissões para tentar contornar essa ocorrência.
