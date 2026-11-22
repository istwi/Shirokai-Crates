# Chaves e fragmentos das caixas Zorykai

Pacote de recursos para Minecraft Java 1.21.1, preparado para o [JAR visual do Shirokai Crates 0.2.1](../shirokai-crates-0.2.1-zorykai-visual.jar). Cada par tem um emblema em comum. A primeira linha da prévia mostra as chaves e a segunda, os fragmentos.

![Chaves e fragmentos na ordem da tabela](previa.png)

| Caixa (arquivo JSON) | Emblema | `key.customModelData` | `fragment.customModelData` |
|---|---|---:|---:|
| `shiny1` | Estrela prateada | 7201 | 7301 |
| `shiny2` | Duas estrelas douradas | 7202 | 7302 |
| `mega` | Hélice violeta e ciano | 7203 | 7303 |
| `lendaria` | Coroa de âmbar | 7204 | 7304 |
| `evento_natal` | Folhas e frutos de azevinho | 7205 | 7305 |

## Ativar

1. Coloque [o ZIP](Zorykai-Chaves-e-Fragmentos-MC1.21.1.zip) em `.minecraft/resourcepacks` e ative-o no cliente. Para distribuição pelo servidor, adicione o conteúdo de `assets/` ao pacote que seus jogadores já recebem. Se ele já personaliza o gancho ou a ametista, combine os `overrides` por ordem numérica em `tripwire_hook.json` e `amethyst_shard.json`. Confira os limites de retorno ao modelo base em 7206 e 7306.
2. Na raiz do servidor, salve [configurar_modelos.py](configurar_modelos.py) e execute os comandos abaixo com Python 3. O primeiro mostra os arquivos a editar. O segundo cria uma cópia de segurança e insere os modelos em `config/shirokaicrates/crates/`. Para configurar à mão, adicione o campo numérico `customModelData` **dentro** dos objetos `key` e `fragment` de cada caixa usando a tabela. As bases são `minecraft:tripwire_hook` e `minecraft:amethyst_shard`, respectivamente.

   ```sh
   python3 configurar_modelos.py
   python3 configurar_modelos.py --aplicar
   ```

3. Rode `/crates reload`. Emita um par com `/crates givekey SeuNick shiny1 1` e `/crates givefragment SeuNick shiny1 1`. Os itens emitidos após a configuração recebem a nova aparência. Itens antigos continuam válidos com a aparência que já tinham.

SHA-1 do ZIP individual para `resource-pack-sha1` em `server.properties` — `044d4167fec4e88a76ac27d2803e871c0269e096`. Se você juntar vários pacotes, use o SHA-1 do ZIP combinado.
