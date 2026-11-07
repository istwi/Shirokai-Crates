# SHIROKAI CRATES — Prompt completo para Codex

## 1. Objetivo do projeto

Desenvolva um mod próprio chamado **Shirokai Crates**, destinado ao servidor Minecraft Shirokai.

Quero um sistema de caixas físicas com chaves, fragmentos, recompensas, animações e configurações JSON.

O sistema deve ser inspirado na experiência de mods de crates, como SkiesCrates, mas possuir código próprio, arquitetura independente e identidade visual do Shirokai.

**Mecânica principal:**

1. O administrador coloca um bloco no mundo.
2. Cria um arquivo JSON com as configurações daquela caixa.
3. Olha para o bloco e utiliza `/crates setblock <id>`.
4. O bloco passa a representar a caixa configurada.
5. O jogador segura a chave física correspondente na mão principal.
6. Clica com o botão direito no bloco.
7. Uma chave é consumida.
8. Uma roleta horizontal é exibida.
9. O jogador recebe a recompensa sorteada.

Não quero que os jogadores possam iniciar aberturas através de menus ou comandos.

## 2. Ambiente

O projeto deve utilizar:

- Minecraft 1.21.1.
- Fabric.
- Java 21.
- Cobblemon 1.8.3.
- Impactor API 5.3.5.
- LuckPerms 5.4.140.
- PlaceholderAPI, opcionalmente.

O mod deve funcionar em servidor dedicado, preferencialmente sem exigir instalação no cliente.

Utilize apenas APIs compatíveis com as versões especificadas.

Não empacote bibliotecas incompatíveis ou duplicadas.

## 3. Identificação das caixas pelos arquivos JSON

**Esta é uma regra obrigatória do projeto.**

Cada caixa será configurada em um arquivo JSON independente dentro de:

`config/shirokaicrates/crates/`

O **nome do arquivo, sem a extensão `.json`, será o ID oficial da caixa**.

Exemplos:

| Arquivo | ID da caixa | Comando |
|---|---|---|
| `shiny1.json` | `shiny1` | `/crates setblock shiny1` |
| `shiny2.json` | `shiny2` | `/crates setblock shiny2` |
| `lendaria.json` | `lendaria` | `/crates setblock lendaria` |
| `mega.json` | `mega` | `/crates setblock mega` |
| `evento_natal.json` | `evento_natal` | `/crates setblock evento_natal` |

### Regras

- O ID deve ser obtido automaticamente pelo nome do arquivo.
- Não exigir um campo `id` dentro do JSON.
- Não exigir registro manual dos IDs em outro arquivo.
- Permitir nomes com letras minúsculas, números, `_` e `-`.
- Rejeitar nomes inválidos, duplicados ou conflitantes.
- Não aceitar caminhos arbitrários ou referências fora da pasta de crates.
- Não criar caixas automaticamente quando um ID inexistente for informado.
- Não confundir o nome visual da caixa com seu ID interno.

Exemplo:

Arquivo: `shiny1.json`

Nome exibido: `✦ Caixa Shiny I`

Comando: `/crates setblock shiny1`

O nome visual pode ser alterado livremente sem modificar o ID.

### Autocompletar

Ao digitar:

`/crates setblock `

O Minecraft deve sugerir automaticamente os IDs dos arquivos JSON válidos.

Exemplo:

- `shiny1`
- `shiny2`
- `lendaria`
- `mega`
- `evento_natal`

Não sugerir arquivos inválidos.

## 4. Atualização automática das caixas

Quero que vários blocos possam utilizar a mesma configuração JSON.

Exemplo:

Se eu registrar dez blocos utilizando:

`/crates setblock shiny1`

Todos devem utilizar a configuração de `shiny1.json`.

Se eu alterar as recompensas ou probabilidades desse arquivo e executar:

`/crates reload`

Todos os blocos associados a `shiny1` deverão utilizar a configuração atualizada.

**Não exigir que o administrador registre os blocos novamente.**

Os registros físicos devem armazenar o ID da caixa, e não cópias independentes das recompensas.

O reload deve:

1. Ler os arquivos JSON.
2. Validar todos os arquivos.
3. Construir a nova configuração.
4. Substituir a configuração ativa apenas se a validação for bem-sucedida.
5. Atualizar as caixas vinculadas.
6. Preservar os registros físicos.
7. Informar erros de configuração no console.

Se um arquivo apresentar erro, não apagar os registros existentes.

Não alterar resultados de aberturas já iniciadas antes do reload.

## 5. Registro de blocos físicos

O administrador poderá transformar um bloco já colocado no mundo em uma caixa.

Exemplos de blocos:

- Baú.
- Baú do Ender.
- Barril.
- Bloco de diamante.
- Bloco de ouro.
- Qualquer outro bloco compatível.

### Comando

`/crates setblock <id>`

Exemplo:

`/crates setblock shiny1`

Funcionamento:

1. Verificar se o administrador possui permissão.
2. Detectar o bloco que está olhando.
3. Verificar se existe `shiny1.json`.
4. Validar se a configuração está carregada.
5. Registrar a dimensão e as coordenadas.
6. Associar o bloco ao ID `shiny1`.
7. Confirmar o registro no chat.

Mensagem:

`✦ Shirokai | Caixa Shiny I registrada com sucesso!`

### Persistência

Salvar:

- Dimensão.
- Coordenadas X, Y e Z.
- ID da caixa.
- Tipo original do bloco.
- Informações necessárias para validar o registro.

Os registros devem permanecer após reiniciar o servidor.

O mesmo arquivo JSON poderá ser utilizado por diversos blocos em diferentes coordenadas e dimensões.

Não transformar automaticamente todos os blocos de um determinado tipo em caixas.

## 6. Remoção e alteração de caixas físicas

Implementar:

`/crates removeblock`

O administrador olha para um bloco registrado e executa o comando.

O sistema remove apenas o registro da caixa.

**Não destruir o bloco físico.**

Também permitir substituir a configuração de um bloco já registrado:

`/crates setblock <outro_id>`

Exemplo:

Um bloco registrado como `shiny1` poderá ser alterado para `shiny2`.

A operação deve atualizar apenas aquele bloco, sem afetar outros registros.

### Proteção

Jogadores comuns não podem:

- Destruir caixas registradas.
- Mover caixas utilizando pistões.
- Alterar o bloco.
- Abrir o inventário vanilla de um baú registrado.
- Remover o registro.
- Utilizar explosões ou outras mecânicas para contornar a proteção.

Integrar de maneira compatível com sistemas de proteção como Flan.

Não alterar globalmente as regras de construção do servidor.

## 7. Chaves físicas

Não quero chaves virtuais.

Cada chave deve existir como um item real no inventário.

Características:

- Item base configurável.
- Nome personalizado.
- Lore.
- Identificador persistente.
- Associação com um ID de caixa.
- Quantidade configurável.
- Suporte a empilhamento.

Exemplo:

`✦ Chave Shiny I`

Descrição:

`Utilize esta chave na Caixa Shiny I.`

### Validação

O jogador precisa segurar a chave correta na mão principal.

Exemplo:

- Chave `shiny1` abre a caixa `shiny1`.
- Chave `shiny2` abre a caixa `shiny2`.
- Chave `lendaria` abre a caixa `lendaria`.

Uma chave não pode abrir uma caixa diferente, exceto quando uma configuração administrativa permitir explicitamente compartilhar o mesmo tipo de chave.

Não permitir falsificação por renomeação em bigorna.

Utilizar identificação persistente validada pelo servidor.

## 8. Abertura com chave na mão

A abertura deve ocorrer exclusivamente ao clicar no bloco físico registrado.

Fluxo:

1. O jogador segura a chave na mão principal.
2. Clica com o botão direito no bloco.
3. O servidor identifica o registro do bloco.
4. Localiza a configuração JSON correspondente.
5. Verifica as permissões.
6. Valida a chave.
7. Verifica se já existe abertura em andamento.
8. Inicia uma transação segura.
9. Consome exatamente uma chave.
10. Sorteia e registra a recompensa.
11. Abre a roleta horizontal.
12. Entrega a recompensa.

### Mensagens

Sem chave:

`✦ Shirokai | Você precisa da chave desta caixa!`

Chave incorreta:

`✦ Shirokai | Esta chave não pertence a esta caixa!`

Caixa indisponível:

`✦ Shirokai | Esta caixa está indisponível no momento.`

### Interações

- Processar apenas a interação válida da mão principal.
- Evitar execução duplicada pela mão secundária.
- Impedir abertura da interface vanilla do bloco.
- Não interferir em blocos não registrados.
- Respeitar proteções existentes.

Não criar um menu intermediário de confirmação.

## 9. Fragmentos de chaves

Criar fragmentos físicos associados aos tipos de caixa.

Regra padrão:

**4 fragmentos iguais = 1 chave completa.**

Exemplos:

- 4 fragmentos `shiny1` → 1 chave `shiny1`.
- 4 fragmentos `shiny2` → 1 chave `shiny2`.
- 4 fragmentos `lendaria` → 1 chave `lendaria`.

A quantidade necessária deve ser configurável por caixa.

### Conversão manual

Comando:

`/crates fragments`

Criar uma GUI simples para converter fragmentos.

Mostrar:

- Tipo de fragmento.
- Quantidade disponível.
- Quantidade necessária.
- Chave resultante.
- Botão de conversão.

Não permitir combinar fragmentos de caixas diferentes.

Não implementar conversão automática nem receitas de crafting nesta versão.

A conversão deve impedir duplicação e perda de itens.

## 10. Animação de roleta horizontal

Implementar exclusivamente uma roleta horizontal nesta primeira versão.

### Funcionamento

- A animação abre após clicar no bloco com a chave correta.
- Os prêmios passam horizontalmente pela interface.
- A velocidade diminui gradualmente.
- O prêmio vencedor fica destacado no centro.
- Sons acompanham a animação.
- Um efeito especial indica o resultado.
- A recompensa é entregue ao jogador.

### Configuração

Permitir configurar:

- Duração.
- Velocidade inicial.
- Desaceleração.
- Sons.
- Efeitos visuais.
- Item de destaque.

O resultado deve ser sorteado no servidor antes da revelação.

A animação não pode alterar o resultado.

### Segurança

Se o jogador:

- Fechar o menu.
- Desconectar.
- Reiniciar o cliente.
- Sofrer desconexão inesperada.

A recompensa deve permanecer registrada.

Se o servidor desligar durante a animação, recuperar a transação após reiniciar.

Não consumir uma segunda chave nem sortear novamente a recompensa.

## 11. Sistema de recompensas

Permitir configurar recompensas individuais em cada JSON.

### Itens Minecraft

- ID do item.
- Quantidade.
- Componentes personalizados, quando suportados.

### Itens Cobblemon

- Pokébolas.
- Doces de experiência.
- Pedras evolutivas.
- Itens equipáveis.
- Itens de breeding.
- Outros itens registrados.

### Pokémon

Permitir recompensar jogadores com Pokémon.

Parâmetros configuráveis:

- Espécie.
- Nível.
- Shiny.
- Natureza.
- IVs.
- Habilidade.
- Outros atributos suportados pela API do Cobblemon.

Exemplo conceitual de recompensa:

```json
{
  "type": "pokemon",
  "species": "rayquaza",
  "level": 70,
  "shiny": true,
  "rarity": "legendary",
  "weight": 1
}
```

O Codex deve adaptar esse formato à API realmente disponível.

Não utilizar comandos inseguros para gerar Pokémon quando existir uma API adequada.

### Economia

Integrar com Impactor API 5.3.5.

Moedas:

- PokéDollars: `impactor:dollars`.
- PokéTokens: `impactor:tokens`.

Não converter moedas automaticamente.

### Comandos

Permitir recompensas baseadas em comandos administrativos previamente configurados.

Validar os comandos e registrar as execuções.

## 12. Probabilidades

Cada recompensa deve possuir um peso configurável.

Exemplo:

| Raridade | Probabilidade |
|---|---:|
| Comum | 60% |
| Rara | 25% |
| Épica | 10% |
| Lendária | 4% |
| Especial | 1% |

Esses valores são exemplos, não limites obrigatórios.

O sorteio deve utilizar os pesos efetivos configurados no JSON.

Validar:

- Pesos inválidos.
- Valores negativos.
- Recompensas inexistentes.
- Caixas sem prêmios válidos.
- Probabilidades inconsistentes.

Não implementar sistema de garantia de raridade (pity).

## 13. Prévia de recompensas

Criar um menu exclusivo para visualizar os prêmios.

Comando:

`/crates preview <id>`

Exemplo:

`/crates preview shiny1`

Mostrar:

- Nome da recompensa.
- Ícone.
- Quantidade.
- Raridade.
- Probabilidade real.
- Atributos de Pokémon, quando aplicável.

Utilizar paginação quando necessário.

A prévia deve ser somente leitura.

Não permitir retirar itens da interface.

Não permitir iniciar uma abertura pela prévia.

## 14. Interface e identidade visual

Seguir a identidade do Shirokai.

Títulos sugeridos:

`✦ SHIROKAI - CAIXAS`

`✦ SHIROKAI - RECOMPENSAS`

`✦ SHIROKAI - FRAGMENTOS`

`✦ SHIROKAI - PENDÊNCIAS`

Utilizar:

- Azul.
- Ciano.
- Dourado.
- Roxo para recompensas épicas.
- Cores adequadas às raridades.

Priorizar menus minimalistas.

Não adicionar dezenas de botões desnecessários.

Concentrar informações detalhadas nos tooltips dos itens.

O menu `/crates` deve servir para consultar caixas e informações, sem permitir iniciar aberturas.

## 15. Entrega de recompensas

Entregar recompensas diretamente ao jogador quando possível.

### Itens

Verificar o espaço disponível no inventário.

Se não houver espaço suficiente, registrar o prêmio como pendente.

### Pokémon

Utilizar a API do Cobblemon para entregar o Pokémon.

Respeitar o sistema de equipe e armazenamento.

Tratar falhas sem perder o prêmio.

### Economia

Utilizar as operações da API do Impactor.

Confirmar o resultado antes de finalizar a entrega.

### Recompensas pendentes

Comando:

`/crates pending`

Criar uma GUI para consultar e resgatar prêmios pendentes.

As pendências devem:

- Persistir após reiniciar.
- Possuir identificador único.
- Impedir resgates simultâneos.
- Não desaparecer antes da confirmação da entrega.
- Não permitir duplicação.

Para operações externas cujo resultado seja incerto, registrar o estado e realizar reconciliação segura em vez de repetir automaticamente a entrega.

## 16. Anúncios globais

Anunciar apenas recompensas de raridade épica ou superior.

Exemplo:

`✦ Shirokai | Jogador recebeu Rayquaza Shiny na Caixa Shiny I!`

Incluir:

- Nome do jogador.
- Nome da caixa.
- Recompensa.
- Raridade.

Permitir personalizar as mensagens.

Não anunciar recompensas comuns ou raras.

Não repetir anúncios após reinicializações.

## 17. Histórico de aberturas

Registrar:

- UUID do jogador.
- ID da caixa.
- Recompensa sorteada.
- Raridade.
- Data e horário.
- ID único da transação.
- Estado de entrega.

Comando:

`/crates history <jogador>`

Permitir consultar o histórico administrativo.

Adicionar retenção configurável para impedir crescimento ilimitado.

## 18. Segurança contra duplicação

Esta é uma prioridade máxima.

Implementar proteção contra:

- Chaves falsificadas.
- Consumo duplicado.
- Aberturas simultâneas.
- Cliques repetidos.
- Exploração da mão secundária.
- Desconexão durante animação.
- Reinicialização durante transações.
- Inventário cheio.
- Resgate duplicado de pendências.
- Falhas de armazenamento.
- Alterações de configuração durante aberturas.

Cada abertura deve possuir identificador único e estados persistentes.

Não considerar uma recompensa entregue sem confirmação.

Se não for possível confirmar uma operação externa, preservar o registro para reconciliação segura.

Nenhuma exceção interna pode impedir um jogador de entrar no servidor.

Em caso de falha crítica de persistência, bloquear novas aberturas de forma controlada e preservar os dados existentes.

## 19. Desempenho

O sistema deve funcionar com muitos jogadores simultâneos.

Requisitos:

- Evitar gravações em disco a cada tick.
- Não bloquear a thread principal com I/O.
- Sincronizar corretamente alterações de inventário.
- Limitar animações simultâneas quando necessário.
- Encerrar sessões e tarefas finalizadas.
- Evitar verificações globais desnecessárias.
- Carregar as configurações de forma eficiente.

Utilizar eventos apropriados do Fabric para interações com blocos.

## 20. Comandos completos

### Jogadores

`/crates`

`/crates preview <id>`

`/crates fragments`

`/crates pending`

### Administradores

`/crates setblock <id>`

`/crates removeblock`

`/crates givekey <jogador> <id> <quantidade>`

`/crates givefragment <jogador> <id> <quantidade>`

`/crates reload`

`/crates list`

`/crates history <jogador>`

### Autocompletar

Todos os comandos que utilizam `<id>` devem sugerir automaticamente os IDs dos arquivos JSON carregados.

Não sugerir IDs inexistentes ou inválidos.

## 21. Permissões

Integrar com LuckPerms utilizando uma API compatível com Minecraft 1.21.1.

Permissões sugeridas:

- `shirokaicrates.use`
- `shirokaicrates.preview`
- `shirokaicrates.fragments`
- `shirokaicrates.pending`
- `shirokaicrates.admin`
- `shirokaicrates.give`
- `shirokaicrates.reload`

Não incluir versões incompatíveis da Fabric Permissions API.

Verificar especificamente o risco de `NoSuchMethodError` em chamadas de permissão, problema já observado em outro mod do Shirokai.

## 22. Estrutura dos arquivos

Organizar as configurações assim:

```text
config/
└── shirokaicrates/
    ├── config.json
    ├── messages.json
    ├── crates/
    │   ├── shiny1.json
    │   ├── shiny2.json
    │   ├── lendaria.json
    │   ├── mega.json
    │   └── evento_natal.json
    └── animations/
        └── horizontal.json
```

Criar exemplos válidos para cada arquivo.

Cada arquivo dentro de `crates/` deve representar uma caixa independente.

O ID será sempre derivado do nome do arquivo.

Separar dados persistentes dos jogadores, histórico e registros dos blocos físicos das configurações administrativas.

## 23. Testes obrigatórios

Executar testes para:

1. Carregar `shiny1.json`.
2. Registrar um bloco com `/crates setblock shiny1`.
3. Rejeitar IDs inexistentes.
4. Autocompletar os IDs disponíveis.
5. Registrar vários blocos com o mesmo ID.
6. Reiniciar e preservar os registros.
7. Alterar `shiny1.json` e executar reload.
8. Confirmar que todos os blocos associados foram atualizados.
9. Rejeitar configurações inválidas sem perder registros.
10. Trocar o ID de um bloco registrado.
11. Desregistrar sem destruir o bloco.
12. Clicar sem chave.
13. Clicar com chave incorreta.
14. Clicar com chave correta.
15. Consumir exatamente uma chave.
16. Impedir interação duplicada pela mão secundária.
17. Impedir acesso ao inventário vanilla da caixa.
18. Impedir destruição por jogadores comuns.
19. Converter quatro fragmentos em uma chave.
20. Executar a roleta horizontal.
21. Sortear recompensas pelos pesos configurados.
22. Entregar itens Minecraft.
23. Entregar Pokémon Cobblemon.
24. Entregar PokéDollars e PokéTokens.
25. Registrar recompensas pendentes.
26. Resgatar pendências.
27. Impedir duplicação após desconexão.
28. Recuperar transações após reinicialização.
29. Anunciar apenas recompensas épicas ou superiores.
30. Testar permissões LuckPerms.
31. Testar múltiplos jogadores simultaneamente.
32. Garantir que falhas internas não bloqueiem o login.

Não afirmar que um teste passou sem executá-lo.

## 24. Build e releases

Antes de implementar:

1. Inspecionar o repositório.
2. Validar as APIs disponíveis.
3. Verificar dependências.
4. Planejar a arquitetura.
5. Identificar limitações.
6. Implementar por etapas.

Depois:

1. Executar testes automatizados.
2. Compilar com Java 21.
3. Verificar compatibilidade com Fabric 1.21.1.
4. Gerar um JAR instalável.
5. Utilizar o workflow de GitHub Actions existente ou criar um apropriado.
6. Publicar uma release versionada quando houver autorização e credenciais disponíveis.
7. Documentar comandos, permissões e configurações.

Não copiar código proprietário de outros mods.

## 25. Entrega final

Quero receber:

- Código-fonte completo.
- JAR compilado.
- Arquivos JSON de exemplo.
- Documentação dos comandos.
- Documentação das permissões.
- Instruções de instalação.
- Relatório dos testes executados.
- Release versionada, quando possível.

### Regras mais importantes

**1. O nome do arquivo JSON define o ID da caixa.**

Exemplo: `shiny1.json` → `/crates setblock shiny1`.

**2. O administrador escolhe qualquer bloco já colocado no mundo e registra a caixa olhando para ele.**

**3. O jogador só abre a caixa clicando nesse bloco com a chave física correta na mão principal.**

**4. Vários blocos podem compartilhar o mesmo arquivo JSON.**

**5. Alterações no JSON devem ser aplicadas com `/crates reload`, sem registrar os blocos novamente.**

**6. As recompensas precisam ser protegidas contra perda e duplicação.**

**7. O mod deve funcionar em Minecraft 1.21.1 Fabric, Java 21 e Cobblemon 1.8.3, preferencialmente apenas no servidor.**

Desenvolva o Shirokai Crates priorizando estabilidade, segurança, desempenho, facilidade de configuração e uma experiência simples para os jogadores.