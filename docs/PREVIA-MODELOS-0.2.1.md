# Preview no bloco e modelos dos Pokémon — 0.2.1

- Botão esquerdo com a mão principal vazia: abre a prévia da caixa registrada.
- Botão direito com a chave na mão principal: inicia a abertura, como antes.
- Com ferramenta, chave ou outro item na mão, bater no bloco não abre a prévia.
- O bloco permanece protegido, inclusive no criativo. Blocos comuns não abrem menus.

O preview usa a permissão existente `shirokaicrates.preview`, permitida por padrão
quando indefinida. São respeitadas a fase posterior às proteções, a condição de
espectador, o alcance e as barreiras de inventário. Não substitui uma abertura em
andamento nem abre com item no cursor. Repetições rápidas têm intervalo mínimo
de dez ticks. A prévia não consome chave e não cria transações ou prêmios.

Recompensas `type: pokemon` passam a usar o item nativo `cobblemon:pokemon_model`
com o componente de espécie e aspectos do Cobblemon. O preview, roleta e menu
de pendências usam esse modelo, mantendo nome, raridade, chance e descrição.
Pokémon shiny configurados carregam o aspecto shiny. Formas/aspectos são obtidos
da configuração através da API oficial. Itens e outros tipos de recompensa
continuam usando `icon` normalmente.

Não precisa alterar os JSONs: os `icon` antigos de Pokémon podem permanecer.
O modelo é automático para o tipo pokemon. O cliente precisa do Cobblemon,
como já era necessário para entrar nesse servidor. Não precisa de um mod
Shirokai adicional no cliente ou resource pack separado.

As imagens são templates de espécie/aspectos, sem gerar Pokémon a cada frame.
O cache retorna cópias isoladas, é invalidado no reload e usa referências fracas.
Todos os itens de exibição continuam protegidos contra retirada do menu.

## Validação

Os testes de protocolo verificam preview em sobrevivência e criativo, proteção
do bloco com ferramenta, ausência de preview em blocos comuns e com chave,
continuidade da abertura pelo botão direito, impossibilidade de substituir uma
roleta ativa, componentes reais de espécie/shiny no preview e nos nove slots da
roleta, bloqueio de retirada de modelos, entrega única e negativa de permissão.
O adaptador `model-protocol.cjs` lê o componente NBT oficial do Cobblemon no
servidor de integração fixado; não é um cliente modificado genérico. Os testes
conferem os dados enviados ao cliente; a renderização gráfica é feita pelo
próprio Cobblemon e não foi inspecionada num cliente gráfico nesta execução.

```sh
source scripts/cloud-env.sh
./gradlew test build projectArchive --console plain
python3 scripts/run-validation.py
# Em outro terminal, após o servidor iniciar:
node tests/integration/preview-models.cjs
# No console local de validação, negue o nó ao UUID do jogador sintético:
# lp user c65499dc-19f4-32bc-ae59-b5f3bb8ad71f permission set shirokaicrates.preview false
MC_TEST_USERNAME=PreviewGuest node tests/integration/preview-models.cjs denied
```

Execute somente no mundo local de testes. O teste prepara a caixa
`preview_models` e configura o spawn do mundo de validação. Não use em produção.
Para atualizar o servidor real, pare, substitua o JAR antigo pela versão 0.2.1
preservando mundo/configurações e inicie novamente.
