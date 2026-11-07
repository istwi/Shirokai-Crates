# Roleta, efeitos, chances e IVs — 0.2.0

## Roleta

A abertura usa um menu de três linhas (27 slots). Os nove prêmios deslizam pela
linha central. A borda é de vidro cinza; os marcadores ficam acima e abaixo do
resultado central. Os outros menus continuam com seis linhas e paginação.
Os itens mostrados não podem ser retirados. O prêmio é reservado antes da animação.

Em `config/shirokaicrates/animations/horizontal.json`, `highlightItem` define os
marcadores. O padrão novo é `minecraft:lime_stained_glass_pane`; configurações
existentes com ouro continuam válidas. Para ficar verde como no exemplo, altere
esse campo e use `/crates reload`. Duração, intervalo, desaceleração e sons
continuam configuráveis.

## Identificação dos blocos

As caixas registradas emitem duas partículas em órbita. Em
`config/shirokaicrates/config.json`, adicione ou ajuste:

```json
"crateEffects": {
  "enabled": true,
  "particle": "end_rod",
  "radius": 0.7,
  "height": 1.2,
  "intervalTicks": 5,
  "viewDistance": 32
}
```

Partículas aceitas: `end_rod`, `enchant`, `happy_villager`, `flame`.
Aplique com `/crates reload`. `enabled: false` desativa somente essa decoração.
O campo `particles` da animação continua controlando a comemoração da vitória.
O efeito usa pacotes de partículas, sem entidades permanentes, e não carrega
chunks. Só é ativado quando há jogador próximo, com até 32 blocos renderizados
por passagem e busca limitada para evitar percorrer registros ilimitados por tick.
`viewDistance` é a distância de ativação (8 a 32 blocos); o alcance de exibição
segue o envio padrão de partículas do Minecraft.

## Chances

`60,000000%` era somente a formatação fixa de seis casas: significava **60%**.
Agora aparecem `60%`, `0,01%`, `0,000001%`, sem zeros extras. Frações periódicas
são arredondadas para exibição; chances muito pequenas nunca aparecem como zero.
O menu usa vírgula decimal; no JSON use ponto.

Há dois modos, sem misturá-los dentro da mesma caixa:

- **`weight`**: mantém o funcionamento das versões anteriores. A chance real é
  `peso / soma dos pesos × 100`. Peso 1 sozinho significa 100%, não 1%.
- **`chance`**: porcentagem direta. Todos os prêmios precisam usar `chance` e
  a soma precisa ser exatamente 100. Uma soma diferente é rejeitada no reload,
  preservando a configuração ativa.

Exemplo: `99.99` no prêmio comum e `0.01` no raro dão **99,99% e 0,01%**.
O raro tem em média uma ocorrência a cada 10.000 aberturas; não existe garantia
após esse número de tentativas. Cada abertura é independente, sem aumento
oculto de chance. `rarity` muda cor/anúncio, não altera a probabilidade.

Os números aceitam até 12 casas decimais. O sorteio usa bilhetes inteiros de
precisão arbitrária e rejeição uniforme; não subtrai pesos em ponto flutuante,
evita perder prêmios muito raros e não depende da ordem dos prêmios para a chance.
A animação é visual: não muda o prêmio já reservado.

## IVs dos Pokémon

Somente recompensas `type: pokemon` aceitam `ivs`:

```json
"ivs": { "mode": "perfect", "perfectCount": 3 }
```

Escolhe três atributos diferentes aleatoriamente e coloca IV **31** neles.
Os demais recebem valores de 0 a 30: são **exatamente três** IVs perfeitos.
`perfectCount` aceita 0 a 6; se omitido no modo `perfect`, o padrão é 3.

```json
"ivs": { "mode": "random" }
```

Os seis atributos recebem valores de 0 a 31, incluindo a possibilidade natural
de IVs perfeitos. Não há quantidade garantida nesse modo.

Sem `ivs`, o comportamento anterior é preservado. Campos individuais como
`hp_iv: 31` e `attack_iv: 31` continuam disponíveis e agora são validados
no intervalo 0 a 31. Não misture `ivs` com campos individuais ou propriedades
que também definem IVs: a configuração será rejeitada em vez de sobrescrever
uma opção silenciosamente.

Os IVs são materializados na reserva e persistidos no snapshot do Pokémon.
Reload, desconexão e reinício não sorteiam novamente os atributos já reservados.
Chaves e registros existentes continuam válidos; não apague o segredo do mundo.

## Exemplo completo

Veja [examples/pokemon-crate.json](../examples/pokemon-crate.json): Pikachu
com IVs aleatórios (99,99%) e Rayquaza shiny com exatamente três IVs perfeitos
(0,01%). Copie para `config/shirokaicrates/crates/pokemon.json`, aplique reload,
registre um bloco com `/crates setblock pokemon` e entregue a chave com
`/crates givekey @s pokemon 1`. Também pode criar a caixa com `/crates create pokemon`
e editar o JSON gerado antes de usar reload.
