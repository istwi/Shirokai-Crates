# Persistência no Windows — 0.1.3

O Java no Windows não permite abrir diretórios usando `FileChannel.open`.
A tentativa de sincronizar as pastas após a gravação causava `AccessDeniedException`
em `world/playerdata` e `world/shirokaicrates`, bloqueando checkpoints e o diário.
O registro do bloco falhava sem mensagem no chat porque a resposta de erro
só era enviada para operações associadas ao bloqueio de inventário.

A sincronização de diretórios é agora centralizada: Linux continua sincronizando
o diretório; Windows não tenta a operação que seu provedor Java não suporta.
A sincronização do conteúdo dos arquivos antes da publicação e a substituição
atômica continuam obrigatórias em ambas as plataformas. No Windows isso não
oferece a mesma garantia de persistência de metadados de diretório em uma queda
de energia; nenhum erro real de gravação de arquivo é ignorado.

A criação de caixas usa esse mesmo tratamento. A criação inicial do segredo
das chaves só aplica permissões POSIX quando o sistema de arquivos as suporta.
Erros ao registrar/remover blocos aparecem no chat. As operações bloqueadas
preservam a causa original no console. Uma falha real de disco continua
bloqueando operações; não existe desbloqueio automático que arrisque prêmios.

## Validação

A suíte local possui 29 testes JUnit, incluindo cinco testes portáveis para
substituição de arquivo, persistência do registro de blocos, checkpoints NBT,
criação do segredo e criação de JSON sem sobrescrita. O workflow Build and test
executa esses cinco testes também em um runner Windows real.

O teste funcional `tests/integration/registration-failure.cjs` provoca uma falha
real de escrita em um mundo Linux de validação que já possui um bloco registrado.
Verifica a mensagem do primeiro erro, das tentativas bloqueadas e da remoção,
sempre preservando o arquivo original (três verificações).
Execute somente em mundo local de testes: ele bloqueia a persistência do mod
até o próximo reinício, restaura as permissões do diretório no encerramento
da tentativa e exige um registro em 0,64,2.

## Atualização

Pare o servidor, faça backup do mundo e configurações, substitua o JAR anterior
por `shirokai-crates-0.1.3.jar` e inicie novamente. O reinício é necessário para
recriar os serviços que já estavam bloqueados; `/crates reload` não faz isso.
Preserve `token-secret.bin`, os registros, transações e playerdata.
