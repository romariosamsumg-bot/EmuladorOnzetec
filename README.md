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
| 2 | Emulação mínima (LibretroDroid + download do snes9x2010) | ✅ (falta validar 60 fps na TV) |
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

## Testando a emulação (Fase 2)

1. Instale o APK na TV e abra **Consoles → Super Nintendo**.
2. Escolha **ROM de teste do Retrô Play** e aperte OK. Na primeira vez o núcleo `snes9x2010`
   (~1,6 MB) é baixado do buildbot do libretro para o ABI da TV, com barra de progresso, e o
   jogo abre em seguida.
3. Com o gamepad: o D-pad (ou o analógico) move o quadrado; B, A, Y e X mudam a cor de fundo e
   tocam tons diferentes; L, R, Start e Select só mudam a cor. Pelo controle remoto da TV, as
   setas movem o quadrado e o OK equivale ao B.
4. **Voltar** sai do jogo. O menu rápido chega na Fase 6.

Para testar outros jogos SNES (ou Mega Drive / Master System), use **Escolher arquivo…** ou
copie as ROMs para `Android/data/com.retroplay.tv/files/roms/<console>/` (`snes`, `megadrive`,
`mastersystem`). Arquivos `.zip` são descompactados automaticamente para um cache temporário.

### ROM de teste

A ROM de teste é um homebrew **original deste projeto (GPL-3.0)**, gerado pelo script
`tools/testrom/build_test_rom.py`, que monta o código 65816 e SPC700 byte a byte. Para conferir
que ela funciona num núcleo real, no PC:

```bash
curl -LO https://buildbot.libretro.com/nightly/linux/x86_64/latest/snes9x2010_libretro.so.zip && unzip snes9x2010_libretro.so.zip
python3 tools/testrom/build_test_rom.py teste.sfc
python3 tools/testrom/run_in_core.py snes9x2010_libretro.so teste.sfc saida/   # confere vídeo, entrada e áudio
```

## Estrutura

```
app/          Activity, navegação (menu lateral), telas (feature/*), DI (Hilt)
core/common/  Utilitários Android (detecção de ABI, dispatchers)
core/ui/      Tema escuro/OLED, cores de destaque, ícones genéricos
domain/       Kotlin puro: registro de consoles, detecção por extensão, modelos de núcleo
data/         Download/instalação de núcleos (OkHttp + WorkManager), preparo de ROMs (.zip)
tools/        Scripts: ícone/banner da TV, ROM de teste e mini frontend libretro para validá-la
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
./gradlew :data:testDebugUnitTest -Pretroplay.network=true --tests '*BuildbotLiveTest*'  # baixa núcleos reais
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
```

Screenshots das telas geradas pelos testes: `./gradlew :app:testDebugUnitTest -Pretroplay.screenshots=true`
(saída em `app/build/screenshots/`).

## Licença

GPL-3.0 (ver [LICENSE](LICENSE)), por causa do LibretroDroid e dos núcleos libretro.
