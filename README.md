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
| 3 | Biblioteca (Room, pasta Jogos, USB/SAF, varredura, CRC + DAT, capas) | ✅ |
| 4 | UI streaming (hero, fileiras, detalhes, busca, páginas de console) | ✅ |
| 5 | Transferência pelo celular (servidor local, QR code, PIN) | ✅ |
| 6 | Menu de jogo e saves | ✅ |
| 7 | Configurações completas | ⏳ |
| 8 | Mega Drive, Master System e Neo Geo | ⏳ |
| 9 | Polimento (perfis, backup, SMB, baseline profile) | ⏳ |
| 10 | Release assinado + GitHub Actions | ⏳ |

As instruções completas (importar jogos, BIOS do Neo Geo, instalar na TV, baixar o APK pelo
GitHub) serão escritas nas fases 9 e 10.

## Como colocar os jogos na TV

### Pasta **Jogos** (recomendado)

Na primeira vez, abra **Transferir jogos → Permitir acesso aos arquivos** e ative a permissão
"Acesso a todos os arquivos" para o Retrô Play. O app cria esta estrutura no armazenamento da TV:

```
Jogos/
├─ Super Nintendo/   (.sfc .smc .fig .swc .zip .7z)
├─ Mega Drive/       (.md .gen .smd .bin .zip .7z)
├─ Master System/    (.sms .zip .7z)
├─ Neo Geo/          (romsets .zip/.7z do FBNeo, sem descompactar)
├─ BIOS/             (neogeo.zip)
└─ LEIA-ME.txt
```

Copie os jogos para a subpasta do console (pelo pendrive com um gerenciador de arquivos, pelo app
**Downloader** ou pela transferência do celular da Fase 5). Ao abrir o Retrô Play, os jogos
novos são encontrados sozinhos; também dá para tocar em **Verificar agora**.

> Se a TV não tiver a tela dessa permissão, rode no computador (com depuração ADB ativada):
> `adb shell appops set --uid com.retroplay.tv MANAGE_EXTERNAL_STORAGE allow`

### Pelo celular (Wi-Fi)

Com a TV e o celular na **mesma rede Wi-Fi**, abra **Transferir jogos** (ou a tela inicial, se a
biblioteca estiver vazia). A TV mostra um **QR code**, o endereço (ex.: `http://192.168.0.15:8080`)
e um **PIN de 4 dígitos**.

1. Aponte a câmera do celular para o QR code (o PIN já vai junto) — ou digite o endereço no
   navegador e informe o PIN.
2. Toque em **Escolher arquivos** (ou arraste os arquivos, no computador). Dá para mandar vários
   de uma vez; cada um tem barra de progresso.
3. O Retrô Play identifica o console de cada arquivo e o coloca na subpasta certa
   (`Jogos/Super Nintendo`, `Jogos/BIOS` para a `neogeo.zip`…). Se preferir, escolha o console
   antes de enviar. Os jogos aparecem na biblioteca segundos depois.

Segurança: o servidor só fica ligado enquanto essa tela está aberta, exige o PIN em cada envio,
bloqueia por 1 minuto após 5 PINs errados, aceita só arquivos de jogos (até 2 GB cada) e confere
o espaço livre antes de receber. Os arquivos vão direto para o disco, sem ocupar a memória da TV.

### Outras pastas e pendrive/HD USB

Em **Transferir jogos → Adicionar pasta ou pendrive…**, escolha qualquer pasta pelo seletor do
sistema (a permissão fica guardada). Os jogos podem ser **jogados direto do USB** ou, com
**Copiar para a TV**, copiados para a pasta Jogos (o espaço livre é conferido arquivo a arquivo e
o pendrive sai da biblioteca ao final, para não duplicar).

### Como os jogos são identificados

- O console vem da extensão e do conteúdo; o nome da subpasta ajuda em casos ambíguos.
- O **CRC32** de cada ROM é comparado com os DATs **No-Intro** (libretro-database): o jogo ganha
  nome oficial, ano, gênero, desenvolvedora, nº de jogadores e capa. Em `.zip`/`.7z` o CRC é lido
  do índice do arquivo, **sem descompactar**. MD5 é usado só para desempatar.
- **Neo Geo**: identificado pelo nome do romset (ex.: `kof98.zip` → "The King of Fighters '98 -
  The Slugfest") e pelos CRCs dos arquivos internos, conforme o DAT do **FinalBurn Neo**. O app
  **nunca descompacta nem altera** romsets. Avisos possíveis na página do console:
  - *Romset incompleto* — faltam arquivos dentro do .zip;
  - *Romset de outra versão* — arquivos com nome certo e conteúdo diferente (comum em romsets
    de versões antigas do MAME); troque por um romset compatível com o FBNeo atual;
  - *Precisa do romset principal* — é um clone; coloque o romset "pai" na mesma pasta;
  - *Romset não reconhecido* — o nome do .zip não existe no FBNeo.
- Se não der para saber o console (ex.: arquivo corrompido), ele aparece em **Escolha o
  console**; a escolha é lembrada nas próximas verificações.
- Capas vêm do projeto **libretro-thumbnails** (boxart, tela de título e snap). Sem capa, o app
  gera um card com o nome do jogo e a cor do console. Cache em disco limitado a 200 MB.

O banco de identificação (`data/src/main/assets/db/retroplay-dat.sqlite`) é gerado por
`python3 tools/dat/build_database.py <saida.sqlite>` a partir dos DATs públicos.

## Usando o app

- **Início**: jogo em destaque (o último jogado ou um aleatório) com ▶ Jogar e ＋ Favoritos, e
  fileiras: Continuar jogando, Favoritos, Adicionados recentemente, uma por console, Mais jogados
  e gêneros. O nome do jogo aparece sob o card depois de um instante com o foco nele.
- **Detalhes do jogo** (OK num card): Jogar, Favoritar, Opções do jogo (escolher a capa: caixa,
  tela de título ou cena) e Apagar (pede confirmação; o foco começa em "Cancelar").
- **Buscar**: teclado em grade pelo D-pad (ou busca por voz, se a TV tiver); resultados enquanto
  digita, sem diferenciar acentos.
- **Consoles**: grade de capas por console com filtros A–Z, Recentes, Mais jogados e Favoritos.
- Ao voltar de uma tela, o foco volta ao card de onde você saiu.

## Durante o jogo

- **Menu rápido**: botão **Voltar** do controle remoto, **Guide** do gamepad ou **Select+Start**.
  O jogo fica congelado e mudo enquanto o menu está aberto. Opções: Continuar · Salvar estado
  (espaços 1–9, com miniatura e data) · Carregar estado (inclui o automático) · Rewind ·
  Voltar 5 segundos · Reiniciar · Inserir ficha (Neo Geo) · Acelerar (2x/4x/8x) · Shader (CRT,
  LCD, Nítido) · Proporção (original ou pixel perfeito, no SNES e Mega Drive/Master System) ·
  Controles · Opções do núcleo · Mostrar FPS · Screenshot (vai para Imagens/Retro Play) · Sair.
- **Save automático** ao sair pelo menu e ao apertar Home na TV; ao voltar, o menu aparece.
  Nos detalhes do jogo, **Continuar do save** abre direto no último save; "Continuar jogando"
  na tela inicial mostra a miniatura do último save e o tempo jogado.
- **Atalhos do gamepad**: Select+R1 acelera · Select+L1 (segurar) volta no tempo (com o rewind
  ligado no menu) · Select+R2 / Select+L2 salvam / carregam rápido (espaço 9).
- O botão **Home** da TV não pode ser capturado por apps Android; por isso o menu usa Voltar.

## Testando a emulação (Fase 2)

1. Instale o APK na TV e abra **Consoles → Super Nintendo**.
2. Escolha **ROM de teste do Retrô Play** e aperte OK. Na primeira vez o núcleo `snes9x2010`
   (~1,6 MB) é baixado do buildbot do libretro para o ABI da TV, com barra de progresso, e o
   jogo abre em seguida.
3. Com o gamepad: o D-pad (ou o analógico) move o quadrado; B, A, Y e X mudam a cor de fundo e
   tocam tons diferentes; L, R, Start e Select só mudam a cor. Pelo controle remoto da TV, as
   setas movem o quadrado e o OK equivale ao B.
4. **Voltar** sai do jogo. O menu rápido chega na Fase 6.

Para testar outros jogos, coloque-os na pasta **Jogos** (veja acima) ou use **Escolher
arquivo…** na página do console. `.zip`/`.7z` de consoles domésticos são extraídos para um
cache temporário na hora de jogar.

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
data/         Núcleos (OkHttp + WorkManager), biblioteca (Room), varredura, banco de DATs, preparo de ROMs
tools/        Scripts: ícone/banner, ROM de teste + mini frontend libretro, gerador do banco de DATs
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
./gradlew :data:testDebugUnitTest -Pretroplay.e2e=true --tests '*PhoneTransferE2ETest*'   # página do celular no Chromium (Node + Playwright)
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
```

Screenshots das telas geradas pelos testes: `./gradlew :app:testDebugUnitTest -Pretroplay.screenshots=true`
(saída em `app/build/screenshots/`).

## Licença

GPL-3.0 (ver [LICENSE](LICENSE)), por causa do LibretroDroid e dos núcleos libretro.
Metadados de jogos: libretro-database (CC BY-SA 4.0) e DAT do FinalBurn Neo. Capas:
libretro-thumbnails (baixadas sob demanda, não incluídas no app).
