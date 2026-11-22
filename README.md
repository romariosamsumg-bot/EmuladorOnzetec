# Retrô Play

Frontend de emuladores de consoles clássicos para **Android TV / Google TV**, com interface
"estilo streaming", navegação 100% por controle remoto ou gamepad e núcleos **libretro**
baixados sob demanda.

> **Aviso legal:** o Retrô Play **não inclui jogos nem BIOS**. Use apenas cópias de jogos e
> BIOS que você possui legalmente.

## Consoles suportados

| Console | Núcleo padrão | Alternativa | Extensões |
|---|---|---|---|
| Super Nintendo / Super Famicom | snes9x2010 | snes9x | .sfc .smc .fig .swc .zip .7z |
| Mega Drive / Genesis | genesis_plus_gx | picodrive | .md .gen .smd .bin .zip .7z |
| Master System / Mark III | genesis_plus_gx | picodrive | .sms .zip .7z |
| Neo Geo (MVS/AES) | fbneo | — | .zip .7z (romsets) — exige `neogeo.zip` |

## Estado do desenvolvimento

| Fase | Conteúdo | Status |
|---|---|---|
| 1 | Esqueleto: Gradle, módulos, Hilt, tema, manifest de TV, banner, menu lateral, splash | ✅ |
| 2 | Emulação mínima (LibretroDroid + download do snes9x2010) | ⏳ |
| 3 | Biblioteca (Room, USB/SAF, varredura, CRC + DAT, capas) | ⏳ |
| 4 | UI streaming (hero, fileiras, detalhes, busca, páginas de console) | ⏳ |
| 5 | Transferência pelo celular (servidor local, QR code, PIN) | ⏳ |
| 6 | Menu de jogo e saves | ⏳ |
| 7 | Configurações completas | ⏳ |
| 8 | Mega Drive, Master System e Neo Geo | ⏳ |
| 9 | Polimento (perfis, backup, SMB, baseline profile) | ⏳ |
| 10 | Release assinado + GitHub Actions | ⏳ |

As instruções completas (importar jogos, BIOS do Neo Geo, instalar na TV, baixar o APK pelo
GitHub) serão escritas nas fases 9 e 10.

## Estrutura

```
app/          Activity, navegação (menu lateral), telas (feature/*), DI (Hilt)
core/common/  Utilitários Android (detecção de ABI, dispatchers)
core/ui/      Tema escuro/OLED, cores de destaque, ícones genéricos
domain/       Kotlin puro: registro extensível de consoles (SystemRegistry), detecção por extensão
tools/        Script que gera o ícone e o banner da TV
```

Para **adicionar um console**: crie um `GameSystem` em
`domain/src/main/kotlin/com/retroplay/domain/system/SystemRegistry.kt` e inclua-o em `all`.

Para **trocar o nome do app**: edite `app_name` em `app/src/main/res/values/strings.xml` e
regere o banner com `python3 tools/generate_brand_assets.py "Novo Nome"`.

## Como compilar (linha de comando)

Requisitos: JDK 17+ e Android SDK (plataforma `android-37.2`).

```bash
echo "sdk.dir=/caminho/para/android-sdk" > local.properties
./gradlew test               # testes unitários + testes de navegação por D-pad (Robolectric)
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
```

Screenshots das telas geradas pelos testes: `./gradlew :app:testDebugUnitTest -Pretroplay.screenshots=true`
(saída em `app/build/screenshots/`).

## Licença

GPL-3.0 (ver [LICENSE](LICENSE)), por causa do LibretroDroid e dos núcleos libretro.
