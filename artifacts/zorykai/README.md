# Zorykai — alteração visual

JAR do Shirokai Crates 0.2.1 com a marca visual Zorykai nos menus, mensagens e metadados de apresentação. Prefixo: ✦ ZORYKAI em branco e negrito, seguido de texto cinza quando não há cor definida.

IDs, namespaces, comandos, permissões, placeholders, pacotes, classes e lógica permanecem preservados. O arquivo inclui a integração CUSTOM_MODEL_DATA preparada anteriormente. Nenhum código-fonte foi modificado neste commit.

Substitua o JAR anterior em `mods/` por `shirokai-crates-0.2.1-zorykai-visual.jar`; não instale ambos. Preserve configurações e dados existentes. Textos personalizados em configurações externas não são substituídos pelo JAR.

Verificação realizada: integridade ZIP, estrutura das classes via javap e comparação binária que confirma alterações apenas nos textos selecionados e metadados visuais. Aparência em cliente gráfico ainda não conferida.

Para conferir o arquivo:

```sh
sha256sum -c shirokai-crates-0.2.1-zorykai-visual.jar.sha256
```
