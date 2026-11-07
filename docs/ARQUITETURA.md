# Arquitetura e limites de durabilidade

O carregador cria snapshots imutáveis sem depender de um índice de IDs. Publicar um reload consiste em substituir uma única referência após validar os registros reais de itens, sons, Pokémon, moedas e comandos. A sessão mantém a caixa e o prêmio originais.

O diário escreve um arquivo por transação: arquivo temporário, `force(true)`, renomeação atômica e sincronização do diretório. Um único executor serializa alterações; o estado em memória só muda após persistência bem-sucedida. A transição de `READY` para `DELIVERING` impede dois resgates concorrentes. A falha de armazenamento fecha novas operações de forma controlada.

O consumo da chave é precedido de uma reserva durável. Inventário e recibo são gravados juntos no mesmo playerdata. Após reiniciar, uma reserva com recibo de consumo torna-se pendência; sem recibo, o consumo não foi persistido e a reserva é cancelada. Itens entregues e o recibo de entrega também compartilham um checkpoint. Operações externas interrompidas entram em reconciliação em vez de serem repetidas cegamente.

Pokémon são materializados antes do consumo e seu JSON completo e UUID ficam no diário. As APIs de armazenamento de Pokémon e de economia permanecem responsáveis por seus próprios dados. Não existe uma transação distribuída entre Minecraft, Cobblemon, Impactor e comandos arbitrários. A confirmação da API não equivale a uma prova de tolerância a falhas de hardware de todos esses provedores; esse cenário precisa de testes adicionais no servidor de homologação.

Os checkpoints serializam o NBT na thread principal e escrevem o snapshot isolado em um executor. Após o primeiro checkpoint de um jogador, os salvamentos vanilla desse jogador passam pela mesma fila, evitando que um snapshot antigo sobrescreva um salvamento mais novo. Um login enquanto há gravação pendente carrega o snapshot mais recente em memória; a entrada não aguarda disco. A confirmação assíncrona libera a próxima fase da transação.

Durante esse curto checkpoint, ações de inventário, uso de itens/blocos/entidades e dano ao jogador são suspensos. A barreira não cobre toda a animação. O diário, playerdata, registros físicos, retenção e leitura de reload não fazem I/O por tick na thread principal. A fila é drenada no encerramento. Esse mecanismo precisa de teste de carga em hardware representativo e de auditoria quando outros mods substituem o salvamento de jogadores ou alteram inventários diretamente.

Os testes de cliente ignoram somente pacotes de receitas, metadados de entidades, árvore de comandos e ícones de avanços específicos dos mods que não existem no protocolo vanilla da ferramenta. O autocomplete consulta o servidor diretamente. Cliques, chaves, janelas, inventário e comandos atravessam a conexão real. O resultado desses testes não substitui uma revisão visual da animação em um cliente Cobblemon.
