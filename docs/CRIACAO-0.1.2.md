# Criação de caixas — 0.1.2

Como administrador, execute `/crates create shiny3`. O JSON é criado em
`config/shirokaicrates/crates/shiny3.json`, com chave, fragmentos e um diamante
como recompensa inicial. A caixa é carregada imediatamente. IDs aceitam 1 a 64
letras minúsculas, números, `_` e `-`. Arquivos existentes não são sobrescritos.

Para receber a chave física: `/crates givekey @s shiny3 1`.
Para receber fragmentos: `/crates givefragment @s shiny3 4`.
Olhe para um bloco existente e execute `/crates setblock shiny3`.
Edite nomes, descrições, itens e recompensas no JSON e aplique `/crates reload`.
Criar a caixa não entrega itens nem coloca um bloco automaticamente.

## Validação executada

24 testes JUnit passaram, sem falhas ou testes ignorados. Incluem criação do JSON,
edição e releitura, IDs inválidos, preservação de arquivos e rejeição de symlinks e criação concorrente sem sobrescrita.

11 verificações funcionais passaram em servidor local Fabric 0.20.1 / Minecraft
1.21.1 com Cobblemon, Impactor e LuckPerms: criação pelo comando, preservação de
arquivo existente, ID inválido, chave física, fragmentos físicos, abertura do
bloco com entrega da recompensa editada, bloqueio para jogador sem permissão e
persistência da caixa e recompensa após reinício normal do servidor. Também foram
verificadas a preservação da configuração ativa quando outro JSON é inválido,
a rejeição de itens inexistentes durante o carregamento e a proteção contra
recriar uma caixa carregada cujo JSON foi removido.
Resultados: [evidence/creation-0.1.2.json](evidence/creation-0.1.2.json).

Para reproduzir em um mundo local novo e exclusivo para testes:

```sh
source scripts/cloud-env.sh
./gradlew -PintegrationRuntime test build projectArchive --console plain
python3 scripts/run-validation.py
# Em outro terminal, na raiz do checkout, depois do servidor iniciar:
node tests/integration/creation.cjs
MC_TEST_USERNAME=CreationGuest node tests/integration/creation.cjs denied
# Pare o servidor com stop e inicie novamente com run-validation.py; depois:
node tests/integration/creation.cjs verify
```

Não execute contra mundos de produção. A fase inicial exige que a caixa
`creation_test` ainda não exista. A suíte smoke também foi reexecutada (20 verificações). As demais suítes de
integração não foram reexecutadas nesta alteração. A publicação no GitHub é
feita após a validação; a configuração do ambiente não é alterada.

## Correções da revisão

A criação agora escreve em arquivo temporário, sincroniza os dados e publica o
JSON completo com criação atômica de link, sem substituir arquivos existentes.
A leitura e escrita de configuração usa o mesmo executor do reload, fora da
thread principal; validação de registros Minecraft e publicação continuam na
thread do servidor. Create e reload compartilham o bloqueio de configuração.
Uma caixa já carregada não pode ser recriada mesmo se o arquivo tiver sido apagado.
