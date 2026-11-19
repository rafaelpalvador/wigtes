# Wigters

Widgets de sistema para Android, com visual de "vidro" inspirado nos widgets do iOS.

## Widget: Painel do sistema (v0.1)

- Nome do aparelho, versão do Android e chip (SoC). Toque no cabeçalho para atualizar.
- RAM em uso, brilho da tela, CPU e bateria (com indicador de carregamento).
- Atalhos: lanterna e silencioso (alternam direto); Wi‑Fi, modo avião e internet (abrem o painel do sistema).
- Tempo ligado desde o último reinício.
- Armazenamento usado / total.

Atualiza sozinho a cada 15 min, o mínimo que o Android permite, e na hora quando você toca num atalho ou no cabeçalho.

### Limitações conhecidas
- **CPU:** desde o Android 8 os apps não podem ler o uso real da CPU. O número é a frequência atual dos núcleos em relação à máxima.
- **Wi‑Fi e modo avião:** desde o Android 10 só apps de sistema ligam e desligam direto. Por enquanto abrem o painel; com Shizuku dá para alternar com um toque (próxima etapa).
- **Brilho:** calculado na escala 0–255; em alguns aparelhos a escala é outra.

## Releases (Obtainium)

O workflow `.github/workflows/release.yml` compila e publica o APK sempre que você envia uma tag `v*`:

```bash
git tag v0.1.0 && git push origin v0.1.0
```

Secrets necessários no repositório (Settings › Secrets and variables › Actions):

| Secret | Conteúdo |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 wigters-release.jks` |
| `KEYSTORE_PASSWORD` | senha do keystore |
| `KEY_ALIAS` | `wigters` |
| `KEY_PASSWORD` | senha da chave |

Use sempre a mesma chave, senão o Android recusa as atualizações.

## Próximos passos
- Widget 2: relógio, data, agenda, clima, passos e música tocando.
- Wi‑Fi, dados e modo avião com um toque via Shizuku.
- Tamanhos responsivos (4×4, 4×5, 2×2).
