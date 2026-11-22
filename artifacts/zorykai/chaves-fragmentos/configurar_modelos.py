"""Aplica os modelos do pacote às caixas existentes. Requer Python 3."""

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import tempfile


CAIXAS = {"shiny1": 1, "shiny2": 2, "mega": 3, "lendaria": 4, "evento_natal": 5}
MODELOS = {"key": ("minecraft:tripwire_hook", 7200),
           "fragment": ("minecraft:amethyst_shard", 7300)}


def objeto_unico(pares):
    dados = dict(pares)
    if len(dados) != len(pares):
        raise ValueError("campo JSON repetido")
    return dados


def inicio_dos_valores(texto):
    """Localiza valores de primeiro nível para inserir o campo sem reformatar o JSON."""
    decoder = json.JSONDecoder()
    cursor = texto.index("{") + 1
    espacos = re.compile(r"\s*")
    while True:
        cursor = espacos.match(texto, cursor).end()
        if texto[cursor] == "}":
            return
        nome, fim_nome = decoder.raw_decode(texto, cursor)
        valor = espacos.match(texto, texto.index(":", fim_nome) + 1).end()
        yield nome, valor
        _, fim = decoder.raw_decode(texto, valor)
        cursor = espacos.match(texto, fim).end()
        if texto[cursor] == "}":
            return
        cursor += 1


def planejar(pasta):
    if not pasta.is_dir():
        raise ValueError(f"Pasta de caixas ausente: {pasta}")
    mudancas = {}
    chaves = {}
    encontradas = set()
    for arquivo in sorted(pasta.glob("*.json")):
        if arquivo.is_symlink():
            raise ValueError(f"{arquivo.name}: link simbólico")
        original = arquivo.read_bytes()
        texto = original.decode("utf-8")
        try:
            caixa = json.loads(texto, object_pairs_hook=objeto_unico)
            if not isinstance(caixa, dict):
                raise ValueError("objeto de caixa inválido")
            indice = CAIXAS.get(arquivo.stem)
            tipo = caixa.get("keyType", arquivo.stem)
            chaves.setdefault(tipo, []).append(arquivo.stem)
            inserir = {}
            for campo, (base, prefixo) in MODELOS.items():
                token = caixa.get(campo)
                if not isinstance(token, dict):
                    raise ValueError(f"campo {campo} ausente ou inválido")
                modelo = token.get("customModelData")
                if indice is None:
                    if token.get("item") == base and modelo in range(prefixo + 1, prefixo + 6):
                        raise ValueError(f"{campo} já usa um modelo reservado pelo pacote ({modelo})")
                    continue
                encontradas.add(arquivo.stem)
                if token.get("item") != base:
                    raise ValueError(f"{campo}.item deve ser {base} para este pacote")
                esperado = prefixo + indice
                if "customModelData" not in token:
                    inserir[campo] = esperado
                elif type(modelo) is not int or modelo != esperado:
                    raise ValueError(f"{campo}.customModelData já está configurado como {modelo}")
            if inserir:
                for nome, posicao in reversed(list(inicio_dos_valores(texto))):
                    if nome in inserir:
                        ponto = posicao + 1
                        recuo = re.match(r"\s*", texto[ponto:]).group()
                        campo_novo = recuo + f'"customModelData": {inserir[nome]},'
                        texto = texto[:ponto] + campo_novo + texto[ponto:]
                mudancas[arquivo] = (original, texto.encode("utf-8"))
        except (ValueError, TypeError) as erro:
            raise ValueError(f"{arquivo.name}: {erro}") from erro
    if not encontradas:
        raise ValueError("Nenhuma das cinco caixas do pacote foi encontrada.")
    for tipo, caixas in chaves.items():
        if len(caixas) > 1 and encontradas.intersection(caixas):
            raise ValueError(f"Chave compartilhada {tipo} nas caixas {', '.join(caixas)}; ajuste os modelos em conjunto.")
    return mudancas


def aplicar(pasta, mudancas):
    backup = Path(tempfile.mkdtemp(prefix="backup-modelos-", dir=pasta.parent))
    print(f"Backup dos originais em {backup}")
    for arquivo, (original, _) in mudancas.items():
        if arquivo.read_bytes() != original:
            raise ValueError(f"{arquivo.name} mudou durante a leitura")
        shutil.copy2(arquivo, backup / arquivo.name)
    for arquivo, (_, editado) in mudancas.items():
        with tempfile.NamedTemporaryFile(dir=pasta, delete=False) as temp:
            nome_temp = Path(temp.name)
            temp.write(editado)
        try:
            shutil.copymode(arquivo, nome_temp)
            os.replace(nome_temp, arquivo)
        finally:
            if nome_temp.exists():
                nome_temp.unlink()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pasta", type=Path, nargs="?", default=Path("config/shirokaicrates/crates"))
    parser.add_argument("--aplicar", action="store_true", help="gravar alterações e salvar backup")
    args = parser.parse_args()
    try:
        mudancas = planejar(args.pasta)
        if not mudancas:
            print("Os modelos já estão configurados.")
            return
        print("Arquivos a atualizar:", ", ".join(p.name for p in mudancas))
        if args.aplicar:
            aplicar(args.pasta, mudancas)
        else:
            print("Simulação concluída. Acrescente --aplicar para gravar.")
    except (ValueError, OSError) as erro:
        parser.exit(1, f"Erro: {erro}\n")


if __name__ == "__main__":
    main()
