# Onzetec Games

Frontend de emuladores de consoles clássicos para **Android TV / Google TV** e **celular**, com
interface "estilo streaming", navegação por controle remoto, gamepad ou toque e núcleos
**libretro** baixados sob demanda. São dois apps feitos do mesmo código:

| App | Para | Arquivo | Pacote |
|---|---|---|---|
| **Onzetec Games (TV)** | Android TV, Google TV, TV Box e Stick | `OnzetecGames.apk` | `com.retroplay.tv` |
| **Onzetec Games (Celular)** | celulares e tablets Android | `OnzetecGames-Celular.apk` | `com.retroplay.mobile` |

Cada um tem a interface desenhada para o aparelho: na TV, menu lateral, foco pelo controle remoto
e telas em 960×540; no celular, barra de navegação inferior (lateral quando deitado), telas em
coluna, teclado do próprio celular, folhas de opções e escolha de vários arquivos de uma vez.
A biblioteca, os núcleos, os saves, os perfis e o jogo em si são os mesmos.

> **Aviso legal:** o Onzetec Games **não inclui jogos nem BIOS**. Use apenas cópias de jogos e
> BIOS que você possui legalmente.

## Consoles suportados

| Console | Núcleo padrão | Alternativa | Extensões |
|---|---|---|---|
| Super Nintendo / Super Famicom | snes9x2010 | snes9x | .sfc .smc .fig .swc .zip .7z |
| Mega Drive / Genesis | genesis_plus_gx | picodrive | .md .gen .smd .bin .zip .7z |
| Master System / Mark III | genesis_plus_gx | picodrive | .sms .zip .7z |
| Arcade (fliperama, inclusive Neo Geo) | mame2000 (MAME 0.37) | fbneo, mame2003_plus | .zip .7z (romsets) — jogos de Neo Geo exigem `neogeo.zip` |
| PlayStation | pcsx_rearmed (PCSX ReARMed) | — | .chd .pbp .cue (+ trilhas .bin) .iso .m3u .zip .7z — BIOS recomendada (`scph5501.bin`) |
| Nintendinho (NES / Famicom) | fceumm (FCEUmm) | nestopia | .nes .unf .unif .zip .7z |
| Game Boy | gambatte | — | .gb .zip .7z (colorido automático nos jogos com paleta oficial) |
| Game Boy Color | gambatte | — | .gbc .zip .7z |
| Game Boy Advance | mgba (mGBA) | vba_next (mais leve) | .gba .zip .7z — roda sem BIOS |
| Atari 2600 | stella2014 (Stella 2014) | stella | .a26 .bin .zip .7z — um .bin de até 64 KB é Atari; maior, Mega Drive |

## Estado do desenvolvimento

| Fase | Conteúdo | Status |
|---|---|---|
| 1 | Esqueleto: Gradle, módulos, Hilt, tema, manifest de TV, banner, menu lateral, splash | ✅ |
| 2 | Emulação mínima (LibretroDroid + download do snes9x2010) | ✅ (falta validar 60 fps na TV) |
| 3 | Biblioteca (Room, pasta Jogos, USB/SAF, varredura, CRC + DAT, capas) | ✅ |
| 4 | UI streaming (hero, fileiras, detalhes, busca, páginas de console) | ✅ |
| 5 | Transferência pelo celular (servidor local, QR code, PIN) | ✅ |
| 6 | Menu de jogo e saves | ✅ |
| 7 | Configurações completas (12 categorias, global → console → jogo) | ✅ |
| 8 | Mega Drive, Master System e Neo Geo (opções por console, BIOS, romsets, núcleos) | ✅ |
| 9 | Polimento (perfis, backup, pasta da rede SMB, Baseline Profile, memória) | ✅ |
| 10 | Release assinado + GitHub Actions | ✅ |
| 11 | Dois apps: TV e celular (interface própria para cada um) | ✅ |
| 12 | Versão web (jogar no navegador) | ⏳ |
| 13 | Site oficial (GitHub Pages) | ⏳ |

O APK pronto sai pelo GitHub (veja [Gerar o APK pelo GitHub](#gerar-o-apk-pelo-github-sem-instalar-nada)).

## Como instalar na TV (Google TV / Android TV)

Testado para a TCL P7K (Google TV, Android 11+); vale para outras TVs e boxes Android TV.

1. **Ative o modo desenvolvedor**: Configurações → Sistema → Sobre → clique 7 vezes em
   **Build do Android TV OS** até aparecer "Você agora é um desenvolvedor".
2. **Permita instalar apps de fora da Play Store**: Configurações → Apps → Segurança e
   restrições → **Fontes desconhecidas** → ligue para o app que vai instalar o APK
   (Downloader ou o gerenciador de arquivos).
3. Escolha **um** jeito de levar o APK até a TV:
   - **Downloader** (o mais fácil): instale o app *Downloader* pela Play Store da TV, digite o
     endereço do APK e confirme a instalação. O endereço da versão mais recente é sempre:
     `https://github.com/romariosamsumg-bot/EmuladorOnzetec/releases/latest/download/OnzetecGames.apk`
     (se o repositório tiver outro nome, troque o trecho `usuário/repositório`). Dica: encurte o
     link num encurtador de URL para digitar menos com o controle remoto.
   - **Pendrive**: copie o `.apk` para um pendrive, ligue na TV e abra-o com um gerenciador de
     arquivos (ex.: *File Commander* ou *X-plore*).
   - **ADB pelo computador**: em Configurações → Sistema → Opções do desenvolvedor, ligue a
     **Depuração USB** (e a depuração pela rede, se houver). Veja o IP da TV em Configurações →
     Rede e, no computador:
     ```bash
     adb connect 192.168.0.20:5555     # aceite o pedido que aparece na TV
     adb install -r app-release.apk
     ```
4. Abra o **Onzetec Games** na tela inicial da TV (ícone/banner em "Apps").
5. Na primeira vez, em **Transferir jogos**, toque em **Permitir acesso aos arquivos** para o app
   criar a pasta **Jogos**. Os núcleos de emulação são baixados sozinhos na primeira vez que você
   joga cada console (precisa de internet só nessa hora).

Para atualizar, instale o APK novo por cima (os jogos, saves e configurações ficam).

## No celular (Android 8 ou mais novo)

1. Baixe o **`OnzetecGames-Celular.apk`** no celular e abra-o. O Android pede para **permitir
   instalar apps desta fonte** (do navegador ou do gerenciador de arquivos): permita e instale.
   Ele é um app separado do de TV (pode ter os dois no mesmo aparelho).
2. As telas funcionam **em pé ou deitado**: Início, Buscar, Consoles, Favoritos e Ajustes ficam
   na barra de baixo (na lateral, com o celular deitado). O jogo abre sempre deitado, em tela cheia.
3. **Adicionar jogos** (ícone ⇧ na Início, ou Ajustes → Adicionar jogos): toque em **Escolher
   arquivos** e marque um ou vários jogos baixados (Downloads, Drive, cartão de memória). O
   console é reconhecido sozinho, inclusive em `.zip`. Também dá para usar a pasta **Jogos** do
   celular (o app pede a permissão de arquivos) ou outras pastas.
4. Na página de cada console, o botão **Adicionar** escolhe jogos só daquele console.
5. Durante o jogo aparece o **controle na tela**: direcional à esquerda, botões do console à
   direita (SNES com X/A/B/Y, L e R; Mega Drive com 3 ou 6 botões; Master System com 1, 2 e
   Pause; Arcade com A–F e **Ficha**; PlayStation com △○✕□, L1/L2/R1/R2), Select/Start embaixo e
   **☰** no topo para o menu. Vale usar vários dedos. Ele **some sozinho** quando você usa um
   controle físico e volta ao tocar na tela.
6. Ajustes em **Ajustes → Controles**: controle na tela, modelo clássico, tamanho, posições,
   visibilidade e vibração.

Quem usava o APK único antigo no celular: instale o app de celular e, se quiser levar os saves,
use **Configurações → Armazenamento → Fazer backup** no app antigo e **Ajustes → Armazenamento →
Restaurar backup** no novo.

## Gerar o APK pelo GitHub (sem instalar nada)

O repositório tem um robô (GitHub Actions, arquivo `.github/workflows/build-apk.yml`) que roda os
testes e gera o APK sozinho. Você só precisa fazer a parte da **chave de assinatura** uma vez.

### 1. Criar a chave de assinatura (uma vez só)

A chave "assina" o app: as próximas versões só instalam por cima se forem assinadas com a mesma
chave. **Guarde o arquivo e as senhas num lugar seguro** (ex.: pendrive + gerenciador de senhas).
Se perder a chave, para atualizar será preciso desinstalar o app — faça um backup dos saves antes
(Configurações → Armazenamento → Fazer backup).

- **Com o Android Studio**: menu *Build → Generate Signed App Bundle or APK → APK → Create new…*,
  escolha onde salvar `retroplay.jks`, crie uma senha, use o alias `retroplay` e preencha o nome.
- **Pelo terminal** (precisa do Java instalado):
  ```bash
  keytool -genkeypair -v -keystore retroplay.jks -alias retroplay -keyalg RSA -keysize 2048 -validity 10000
  ```
  Ele pergunta a senha e alguns dados (nome; o resto pode deixar em branco).

**Nunca** coloque o `retroplay.jks` dentro do repositório (o `.gitignore` já bloqueia `*.jks`,
`*.keystore` e `keystore.properties`).

### 2. Transformar a chave em texto (base64)

O GitHub guarda segredos como texto. Na pasta onde está o `retroplay.jks`:

- **Windows (PowerShell)**: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\retroplay.jks")) | Set-Clipboard`
- **macOS**: `base64 -i retroplay.jks | pbcopy`
- **Linux**: `base64 -w0 retroplay.jks` (copie o texto que aparecer)

### 3. Cadastrar os 4 segredos no GitHub

No repositório: **Settings → Secrets and variables → Actions → New repository secret**. Crie:

| Nome | Valor |
|---|---|
| `KEYSTORE_BASE64` | o texto copiado no passo 2 |
| `KEYSTORE_PASSWORD` | a senha do arquivo `.jks` |
| `KEY_ALIAS` | `retroplay` (ou o alias que você escolheu) |
| `KEY_PASSWORD` | a senha da chave (no keytool, normalmente a mesma do arquivo) |

Sem esses segredos o robô ainda funciona, mas gera um **APK de debug** (`OnzetecGames-debug.apk`):
instala normalmente, só que como um app separado ("Onzetec Games (teste)") e não serve para
atualizar a versão assinada.

### 4. Gerar uma versão

- **Pelo site**: aba **Actions → Gerar APK → Run workflow**, digite a versão (ex.: `1.0.0`) e
  confirme. Em ~15 minutos aparece em **Releases** a versão com o arquivo `OnzetecGames.apk`.
- **Ou por tag**: crie uma Release em **Releases → Draft a new release** com a tag `v1.0.0` e
  publique; o robô anexa o APK a ela. (Pelo terminal: `git tag v1.0.0 && git push origin v1.0.0`.)
- A cada envio para o branch `main` (e em pull requests) o robô também testa e gera o APK, que
  fica em **Actions → (a execução) → Artifacts** (vem dentro de um `.zip`).

Se algo falhar, a execução mostra em vermelho o passo com problema e guarda os relatórios dos
testes em *Artifacts*. "A chave não abre" significa senha ou alias errados nos segredos.

### 5. Baixar na TV

No app *Downloader* da TV, use o endereço
`https://github.com/romariosamsumg-bot/EmuladorOnzetec/releases/latest/download/OnzetecGames.apk`
(ele aponta sempre para a versão mais recente) e siga [Como instalar na TV](#como-instalar-na-tv-google-tv--android-tv).
Para atualizar, repita: o app novo instala por cima, mantendo jogos, saves e configurações.

## Como colocar os jogos na TV

### Pasta **Jogos** (recomendado)

Na primeira vez, abra **Transferir jogos → Permitir acesso aos arquivos**. O guia abre primeiro
a tela do app nas Configurações: entre em **Permissões → Arquivos e mídia** e escolha **Permitir
tudo** (gerenciar todos os arquivos; "apenas mídia" não basta para ler os jogos). Volte com
**Voltar**: o app percebe na hora e cria a pasta. Caminho manual: **Configurações → Apps →
Onzetec Games → Permissões → Arquivos e mídia → Permitir tudo** (em outras TVs: Apps → Acesso
especial a apps → Acesso a todos os arquivos → Onzetec Games).

O app cria esta estrutura no armazenamento da TV:

```
Jogos/
├─ Super Nintendo/   (.sfc .smc .fig .swc .zip .7z)
├─ Mega Drive/       (.md .gen .smd .bin .zip .7z)
├─ Master System/    (.sms .zip .7z)
├─ Arcade/          (romsets .zip/.7z do MAME 0.37 ou do FBNeo, sem descompactar)
├─ PlayStation/      (.chd .pbp .iso, ou .cue com as trilhas .bin; .m3u para vários discos)
├─ BIOS/             (neogeo.zip para o Neo Geo; scph5501.bin, BIOS do PlayStation)
└─ LEIA-ME.txt
```

Copie os jogos para a subpasta do console (pelo pendrive com um gerenciador de arquivos, pelo app
**Downloader** ou pela transferência do celular da Fase 5). Ao abrir o Onzetec Games, os jogos
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
3. O Onzetec Games identifica o console de cada arquivo e o coloca na subpasta certa
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

### Pasta da rede (SMB)

Jogos num computador, NAS ou roteador com pendrive, na mesma rede: **Configurações → Rede →
Adicionar pasta da rede (SMB)**. Informe o servidor (nome ou IP), o compartilhamento, a subpasta
(opcional) e o usuário/senha (vazio = convidado). **Testar e adicionar** confere a conexão e já
procura os jogos. Funciona com SMB 2/3 (Windows 10/11, Samba, a maioria dos NAS). Na hora de
jogar, o arquivo é copiado para a TV (romsets de Arcade grandes podem demorar alguns segundos).
A senha fica só na TV, fora do backup. Para tirar a pasta, use **Transferir jogos → Remover**.

### Como os jogos são identificados

- O console vem da extensão e do conteúdo; o nome da subpasta ajuda em casos ambíguos.
- O **CRC32** de cada ROM é comparado com os DATs **No-Intro** (libretro-database): o jogo ganha
  nome oficial, ano, gênero, desenvolvedora, nº de jogadores e capa. Em `.zip`/`.7z` o CRC é lido
  do índice do arquivo, **sem descompactar**. MD5 é usado só para desempatar.
- **Arcade**: identificado pelo nome do romset (ex.: `mslug.zip` → "Metal Slug - Super
  Vehicle-001") e pelos CRCs dos arquivos internos, comparados com dois DATs: o do **MAME 0.37**
  (núcleo MAME 2000) e o do **FinalBurn Neo**. O app guarda qual dos dois o romset satisfaz e
  **abre o jogo no núcleo certo sozinho** (romsets do MAME 0.37 no MAME 2000; romsets mais novos,
  como Metal Slug 3 ou KOF 2000, no FBNeo). O app **nunca descompacta nem altera** romsets.
  Avisos possíveis na página do console:
  - *Romset incompleto* — faltam arquivos dentro do .zip;
  - *Romset de outra versão* — arquivos com nome certo e conteúdo diferente (comum em romsets
    de outra versão do MAME); use romsets do MAME 0.37 (b5 a b11) ou do FBNeo atual;
  - *Precisa do romset principal* — é um clone; coloque o romset "pai" em qualquer pasta da
    biblioteca (ao abrir o jogo, o app junta os dois para o núcleo, sem copiar nada);
  - *Romset não reconhecido* — o nome do .zip não existe no MAME 0.37 nem no FBNeo.
- Se não der para saber o console (ex.: arquivo corrompido), ele aparece em **Escolha o
  console**; a escolha é lembrada nas próximas verificações.
- Capas vêm do projeto **libretro-thumbnails** (boxart, tela de título e snap). Sem capa, o app
  gera um card com o nome do jogo e a cor do console. Cache em disco limitado a 200 MB.
- Os cards se ajustam à capa encontrada e ela aparece **inteira**, sem cortes. Enquanto a imagem
  carrega, o card usa o formato típico do console (Super Nintendo deitado, 1,38:1; Mega Drive,
  Master System e Arcade em pé, ~0,7:1); quando ela chega, assume a proporção exata da imagem
  (limitada entre 1:2 e 2:1). Nas fileiras todos têm a mesma altura e muda a largura; nas grades
  muda a altura. Com foco, o nome aparece numa etiqueta na cor de destaque.

### Arcade: romsets e BIOS do Neo Geo

1. Coloque os romsets (`.zip`, como estão, sem descompactar) em `Jogos/Arcade` (pastas antigas
   como `Jogos/Neo Geo` ou `MAME` também funcionam). Use romsets do **MAME 0.37** (b5 a b11) ou
   do **FBNeo**; o app identifica cada um e escolhe o núcleo.
2. Só os jogos **de Neo Geo** (Metal Slug, KOF, Samurai Shodown…) precisam da **`neogeo.zip`**
   (a BIOS, que você precisa ter). Coloque-a em `Jogos/BIOS` ou importe em **Configurações →
   BIOS**; ela deve ser da mesma versão dos romsets (MAME 0.37 ou FBNeo). **Atenção:** a
   `neogeo.zip` do MAME 0.37 é pequena (3 arquivos) e o FBNeo recusa: ele pede a do conjunto
   FBNeo (38 arquivos, com `sp-s3.sp1`, `sm1.sm1` e `sfix.sfix`). Jogos que só o FBNeo roda
   (ex.: Metal Slug 3) precisam da do FBNeo. O app confere a BIOS contra o núcleo que vai abrir o
   jogo: se ela não serve, avisa em português qual trocar (em vez do erro do núcleo), e o cartão do
   Arcade, em Consoles, mostra "BIOS de outra versão". Ele mostra "BIOS ok" quando a `neogeo.zip`
   serve para os dois núcleos. Jogos de outras placas (Street Fighter II, Final
   Fight, Cadillacs and Dinosaurs…) não precisam dela.
3. Se um jogo não abrir, a mensagem diz o motivo: BIOS faltando, romset principal faltando, ou
   romset incompleto/de outra versão.
4. Para trocar o núcleo de um jogo: Detalhes → Opções → Configurações deste jogo → Emulação →
   Núcleo do Arcade (MAME 2000, FinalBurn Neo ou MAME 2003-Plus).
5. Na versão 1.2 o antigo "Neo Geo" virou "Arcade": favoritos, histórico, saves e configurações
   foram levados junto automaticamente.
4. Romsets grandes ocupam muita RAM; se a TV tiver pouca memória livre, o app avisa antes.

### Opções de cada console (Configurações → Emulação → escolha o console no topo)

| Console | Opções |
|---|---|
| Arcade | No FBNeo: modo **MVS** (fliperama), **AES** (console) ou **UniBIOS** e **Free play** para jogos de Neo Geo. No MAME 2000, o "Modo econômico" pula quadros quando a TV não dá conta. Ficha no Select e "Inserir ficha" no menu do jogo; botões A–F (A–D no Neo Geo) + Ficha + Start. |
| Mega Drive | Controle de **3 ou 6 botões**; região Automática / EUA / Japão / Europa. |
| Master System | Som **FM (YM2413)** ligado/desligado; o Pause do console fica no Start. |
| Super Nintendo | Região automática; **Reduzir lentidão** (overclock da CPU e do SuperFX). |
| PlayStation | Recompilador dinâmico ligado (o que deixa o PCSX ReARMed leve em processadores ARM); sem o logo da BIOS; no "Modo econômico", pula quadros quando a TV/celular não dá conta. |

Valem para o console inteiro ou só para um jogo (Detalhes → Opções → Configurações deste jogo),
nos dois núcleos de cada console (ex.: Genesis Plus GX ou PicoDrive). Mega Drive e Master System
usam o mesmo núcleo baixado, mas cada um tem suas próprias configurações. As chaves de cada núcleo
foram conferidas nos núcleos reais com `python3 tools/cores/list_core_options.py <núcleo.so>`.

O banco de identificação (`data/src/main/assets/db/retroplay-dat.sqlite`) é gerado por
`python3 tools/dat/build_database.py <saida.sqlite>` a partir dos DATs públicos.

## Usando o app

O menu lateral mostra o símbolo do Onzetec Games; aberto, mostra o logo completo com "Feito por
Romário Marcelino" logo abaixo. Itens: perfil, Início, Buscar, Consoles, Favoritos, **Mídia**,
Transferir jogos, Configurações e **Sair**.

- **Início** sempre abre do começo: topo da tela, foco no ▶ Jogar do destaque (mesmo vindo de
  outra tela ou do "Sair" do jogo).
- **Sair do app**: o item **Sair** do menu, ou **Voltar** com o foco no menu já na Início, abre
  "Sair do Onzetec Games?" com o foco em **Cancelar** (para não fechar sem querer). No celular,
  Voltar na Início faz a mesma pergunta, e há **Sair** no fim dos Ajustes.
- **Mídia**: prints e highlights salvos durante os jogos, do mais novo para o mais antigo, com
  filtros Tudo / Prints / Highlights. OK abre em tela cheia (o highlight toca em repetição) com
  Fechar e Apagar; no celular também **Compartilhar**. No celular, a Mídia fica no ícone ▶ da
  barra de cima da Início e em Ajustes. Os arquivos ficam também na galeria: Imagens/Onzetec
  Games e Filmes/Onzetec Games (Android 10+; no 8 e 9, na pasta do app).

- **Início**: jogo em destaque (o último jogado ou um aleatório) com ▶ Jogar e ＋ Favoritos, e
  fileiras: Continuar jogando, Favoritos, Adicionados recentemente, uma por console, Mais jogados
  e gêneros. O nome do jogo aparece sob o card depois de um instante com o foco nele.
- **Detalhes do jogo** (OK num card): Jogar, Favoritar, Opções do jogo (Configurações deste jogo,
  Renomear, escolher a capa: caixa, tela de título ou cena) e Apagar (pede confirmação; o foco
  começa em "Cancelar").
- **Abertura e perfis**: o app abre com o logo do Onzetec Games e, em seguida:
  - **na primeira vez**, "Crie seu perfil": escolha um **avatar** (14 ilustrações próprias), um
    **nome** e a **cor do tema** (Laranja Onzetec, Violeta, Ciano, Âmbar, Verde ou Coral). O tema do
    app inteiro passa a usar a cor do perfil;
  - **nas próximas**, **"Quem vai jogar?"**: escolha o perfil, **Adicionar perfil** (até 8) ou
    **Gerenciar perfis** (editar avatar, nome e cor, ou excluir);
  - uma tela de **boas-vindas** ("Olá, Nome!") com o avatar faz a transição para a Início.
  No **menu lateral**, o perfil ativo aparece acima do Início: escolha-o para trocar de perfil
  (Voltar retorna sem trocar). Também dá para editar em **Configurações → Interface → Editar
  perfil**, e desligar a pergunta ao abrir em "Perguntar o perfil ao abrir". Cada perfil tem seus
  favoritos, "Continuar jogando", mais jogados, tempo de jogo e saves; jogos e configurações são
  comuns a todos. Quem já usava o app continua com o mesmo perfil (e seus saves): ele só pede o
  avatar e a cor na primeira abertura da versão 1.8.
- **Buscar**: teclado em grade pelo D-pad (ou busca por voz, se a TV tiver); resultados enquanto
  digita, sem diferenciar acentos.
- **Consoles**: grade de capas por console com filtros A–Z, **Populares**, Recentes, Mais jogados e
  Favoritos.
- **Mais populares**: fileira na Início (todos os consoles intercalados: o 1º de cada um, depois o
  2º...) e filtro "Populares" na página de cada console. Vem de listas curadas embutidas no app
  (`tools/popular/popular.txt`: Super Nintendo 90, Mega Drive 82, Master System 55, Arcade 84,
  PlayStation 89),
  montadas por vendas, aclamação e lembrança da época, com atenção a clássicos do Brasil (Turma
  da Mônica, Sapo Xulé). Não há ranking ao vivo: o app cruza a lista com a sua biblioteca pelo
  título (sem "The", acentos e região), pelo nome do DAT e, no Arcade, pelo romset. Várias
  versões do mesmo jogo aparecem uma vez só (preferência: Brasil, EUA/Mundo, Europa, Japão).
  Para editar a lista: mude o `.txt` e rode `python3 tools/popular/build_popular.py`, que confere
  cada nome no banco de DATs e gera `PopularGamesData.kt`.
- Ao voltar de uma tela, o foco volta ao card de onde você saiu.

## Sensação ao usar (game feel)

O app responde a cada ação com som, movimento e vibração, como um console:

- **Sons próprios** (originais, gerados por `tools/sound/build_sounds.py`): "tic" ao mover o foco,
  "toc" grave ao bater na borda (não há para onde ir), sons de escolher, voltar, ligar/desligar,
  sucesso (salvou, print, highlight), erro (PIN errado, falha) e um som de **abrir o jogo**.
  Tocados com SoundPool (já carregados na memória, sem atraso).
- **Música de fundo** original nos menus (loop de 40 s, 96 BPM): entra e sai com fade, para ao
  abrir um jogo e continua do mesmo ponto na volta; pausa se outro app tocar som.
- **Vibração (haptics)**: no celular, os efeitos prontos do Android (tique, clique, clique forte,
  duplo no erro); na TV, o **controle treme** (se tiver motor) na borda, ao abrir um jogo e nos erros.
- **Física de mola**: os cards de jogo e de console crescem com mola ao receber o foco (passam um
  pouco do tamanho e assentam); no celular, os cards afundam e voltam ao toque.
- **Tremor** no erro (ex.: PIN errado, que também vibra).
- **UI otimista**: Favoritar muda o botão na hora; o banco confirma em seguida.
- **Abrir o jogo**: a tela do jogo entra com zoom e fade enquanto o menu recua.
- Tudo ajustável em **Configurações → Interface → Sons e vibração**: sons da interface, volume
  dos sons, música de fundo, volume da música e vibração.

## Durante o jogo

- **Menu rápido**: botão **Voltar** do controle remoto, **Guide** do gamepad ou **Start + Select**
  juntos (em qualquer ordem). O atalho segue o remapeamento dos botões (controles USB genéricos
  remapeados em Configurações → Controles → Remapear botões também funcionam) e reconhece
  controles que mandam o Select como "Voltar".
  O jogo fica congelado e mudo enquanto o menu está aberto. Opções: **Sair** (primeira da lista:
  salva o automático e volta direto para o Início) · Continuar · Salvar estado
  (espaços 1–9, com miniatura e data) · Carregar estado (inclui o automático) · Rewind ·
  Voltar 5 segundos · Reiniciar · Inserir ficha (Arcade) · Acelerar (2x/4x/8x) · Shader (CRT,
  LCD, Nítido) · Proporção (original ou pixel perfeito, no SNES e Mega Drive/Master System) ·
  Controles · Opções do núcleo · Mostrar FPS · **Tirar print** · **Highlight**.
- **Highlight**: enquanto você joga, o app guarda continuamente os últimos 15 s de vídeo (360p,
  24 quadros/s, sem som; ~5 MB de memória). No menu, **Highlight → 5, 10 ou 15 segundos** salva
  esse trecho em MP4 na **Mídia**. O tempo com o menu aberto não entra no vídeo. Se o aparelho
  for fraco e o jogo ficar lento, desligue em Configurações → Vídeo → Highlights.
- **Save automático** ao sair pelo menu e ao apertar Home na TV; ao voltar, o menu aparece.
  Nos detalhes do jogo, **Continuar do save** abre direto no último save; "Continuar jogando"
  na tela inicial mostra a miniatura do último save e o tempo jogado.
- **Atalhos do gamepad**: Select+R1 acelera · Select+L1 (segurar) volta no tempo (com o rewind
  ligado no menu) · Select+R2 / Select+L2 salvam / carregam rápido (espaço 9).
- O botão **Home** da TV não pode ser capturado por apps Android; por isso o menu usa Voltar.

## Configurações

Categorias à esquerda, opções à direita (nome, descrição e valor atual). Tudo pelo D-pad.

- **Níveis**: nas categorias Vídeo, Áudio, Controles e Emulação há um seletor no topo — Global,
  SNES, MD, SMS, NEO. O valor de um console vale só para ele; o de um jogo (Detalhes → Opções →
  **Configurações deste jogo**) vale só para aquele jogo. A linha mostra **● Personalizado aqui**
  quando o valor foi mudado naquele nível, ou "Herdado do global/console". Na lista de valores,
  **Usar o valor herdado** desfaz a personalização.
- **Vídeo**: filtro (Nenhum, CRT, LCD, Nítido, Suave), proporção (original ou pixel perfeito),
  mostrar FPS, modo econômico.
- **Áudio**: som do jogo, latência (baixa/normal), silenciar no menu.
- **Controles**: controles conectados com perfil (Xbox, PlayStation, 8BitDo, genérico) e porta
  (jogador 1–4 ou automática); **Remapear botões** com o assistente "pressione o botão…" (por
  console e por jogo; OK do remoto pula um botão); turbo por botão; controle remoto como gamepad;
  analógico como direcional; zona morta; layout Nintendo; atalhos e botão modificador (Select ou L3).
- **Emulação**: velocidade e modo da aceleração (alternar/segurar), voltar no tempo e memória
  usada (com aviso de RAM), salvar ao sair, continuar automaticamente, espaços de save, núcleo de
  cada console e **opções de cada núcleo** (lidas do próprio núcleo, com "Restaurar padrões").
- **BIOS**: situação da `neogeo.zip` e importação de um pendrive/pasta.
- **Biblioteca**: pastas monitoradas, procurar agora, procurar ao abrir, ocultar não
  identificados, duplicados (apagar o arquivo repetido) e reconstruir a biblioteca.
- **Núcleos**: versão e tamanho de cada núcleo, baixar/atualizar/remover, voltar à versão
  anterior, baixar todos e canal (Estável: só quando você pede; Nightly: verifica 1× por dia).
- **Interface**: perfis, editar perfil (avatar, nome e cor do tema), perguntar o perfil ao abrir, idioma (Português,
  English, Español), tema escuro ou preto OLED, animações, destaque animado, som de navegação, tamanho das capas e fileiras da Início
  (mostrar/ocultar/ordenar).
- **Rede**: porta e PIN fixo da transferência pelo celular; pasta da rede (SMB); baixar capas e
  capas só no Wi-Fi.
- **Armazenamento**: espaço usado por jogos, saves, capas, núcleos e temporários; limpar cache;
  **backup** e **restauração** (um `.zip` no pendrive com saves de bateria, save states,
  configurações, perfis, favoritos, histórico e nomes/capas escolhidos — jogos e núcleos não
  entram); **exportar/importar só as configurações** (`.json`).
- **Controle parental**: PIN de 4 dígitos para abrir as Configurações e/ou apagar jogos.
- **Sobre**: versão, ABI, aparelho, RAM, exportar o registro de erros, licenças e aviso legal.

Não disponíveis porque o motor de emulação (LibretroDroid) não oferece: esticar a imagem,
intensidade de scanlines, rotação, VSync, volume/taxa de amostragem próprios, pular quadros e
run-ahead.

## Solução de problemas

- **Jogo lento ou com som picotado**: em Vídeo, use o filtro "Nenhum" e ligue o modo econômico;
  em Emulação, desligue "Voltar no tempo"; no SNES, prefira o núcleo Snes9x 2010.
- **Controle não funciona**: veja em Configurações → Controles se ele aparece; remapeie com o
  assistente; o controle remoto também joga (setas + OK).
- **Jogo de Arcade não abre**: a mensagem diz se falta a BIOS (jogos de Neo Geo), o romset
  principal (clones) ou se o romset é de outra versão (use romsets do MAME 0.37 ou do FBNeo).
- **Pasta da rede não conecta**: confira o IP, se o compartilhamento existe e se o SMB 2/3 está
  ligado no computador/NAS; teste o usuário e a senha num computador da mesma rede.
- **Algo travou**: Configurações → Sobre → **Exportar registro de erros** salva um arquivo para
  enviar ao suporte.

## Testando a emulação

1. Coloque um jogo na pasta **Jogos** (veja acima) ou use **Escolher arquivo…** na página do
   console. O app não vem com jogos.
2. Abra o jogo e aperte **Jogar**. Na primeira vez o núcleo do console é baixado do buildbot do
   libretro para o ABI da TV, com barra de progresso, e o jogo abre em seguida.
3. Pelo controle remoto da TV, as setas são o direcional e o OK equivale ao B.
4. **Voltar** abre o menu rápido (Sair é a primeira opção e leva para o Início).
   No gamepad (Bluetooth ou USB), **Start + Select juntos, em qualquer ordem**, também abrem o
   menu. O atalho é configurável em Configurações → Controles → "Abrir o menu pelo controle":
   Start + Select (padrão), L3 + R3, L1 + R1 + Start ou desligado. Controles que mandam o
   Select como "Voltar" também funcionam.

`.zip`/`.7z` de consoles domésticos são extraídos para um cache temporário na hora de jogar.

### ROM de teste (só para desenvolvimento)

O app não inclui mais a ROM de demonstração do Super Nintendo. Para conferir um núcleo real no
PC, `tools/testrom/build_test_rom.py` gera um homebrew **original deste projeto (GPL-3.0)**:

```bash
curl -LO https://buildbot.libretro.com/nightly/linux/x86_64/latest/snes9x2010_libretro.so.zip && unzip snes9x2010_libretro.so.zip
python3 tools/testrom/build_test_rom.py teste.sfc
python3 tools/testrom/run_in_core.py snes9x2010_libretro.so teste.sfc saida/   # confere vídeo, entrada e áudio
```

## Site oficial

A pasta `site/` é o site (HTML, CSS e JavaScript, sem etapa de build), publicado no GitHub Pages
pelo workflow `.github/workflows/pages.yml` a cada push na `main` que mude o site:
`https://romariosamsumg-bot.github.io/EmuladorOnzetec/`.

- **Início** (`index.html`): download do app de TV/Box/Stick e do de celular (sempre a versão mais
  nova das Releases), endereços curtos para o app Downloader (`…/tv/` e `…/celular/`), consoles e
  recursos. Marca o download certo para o aparelho de quem visita.
- **Jogos** (`jogos.html` + `data/jogos.json`): catálogo de **jogos livres** (homebrew/freeware
  com licença que permite redistribuir), com download por jogo, por console e geral. Cada jogo
  precisa de `titulo`, `console`, `licenca`, `fonte` (página oficial do autor) e `url`
  (download, https). Não coloque jogos comerciais.
- **Manual** (`manual.html`): instalação, como colocar jogos, cada tela e opção, dicas e
  solução de problemas.
- **Jogar** (`jogar/`): página da versão web (em construção).
- Sons e música do app (botão ♪, desligado por padrão), animações ao rolar, transições entre
  páginas e um segredo (↑ ↑ ↓ ↓ ← → ← → B A).
- `tools/site/sync_assets.sh` copia logo, capas dos consoles e sons do app para o site.
- Para ativar (uma vez): **Settings → Pages → Source: GitHub Actions**. O Pages gratuito exige o
  repositório público.
- **Cloudflare Pages** (alternativa, funciona com repositório privado):
  `tools/site/package_cloudflare.sh` gera `build/OnzetecGames-site-cloudflare.zip`, com o site na
  raiz do domínio, `_redirects` (`/tv` e `/celular` baixam os APKs) e `_headers` (cache e
  segurança). No painel: Workers & Pages → Create → Pages → **Upload assets** → envie o zip.
  Com `APK_DIR=pasta` (com `OnzetecGames.apk` e `OnzetecGames-Celular.apk`), os APKs vão dentro
  do site em `/downloads/` e nada depende do GitHub.

## Estrutura

```
app/          Activity, telas compartilhadas e da TV (feature/*), ViewModels, DI (Hilt)
  src/tv/       App de TV: entrada (menu lateral, tela 960×540), manifesto com leanback/banner
  src/mobile/   App de celular: Material 3, barra inferior, telas em coluna (mobile/*)
  src/testTv/, src/testMobile/  Testes de tela de cada app (Robolectric)
core/common/  Utilitários Android (detecção de ABI, dispatchers)
core/ui/      Tema escuro/OLED, cores de destaque, ícones genéricos
domain/       Kotlin puro: registro de consoles, detecção por extensão, modelos de núcleo
data/         Núcleos (OkHttp + WorkManager), biblioteca (Room), varredura, banco de DATs, preparo de ROMs
baselineprofile/  Gerador do Baseline Profile (inicialização e Início) para rodar numa TV/emulador
tools/        Scripts: marca (tools/brand: ícone, splash, banner, logo), ROM de teste + mini frontend libretro, gerador do banco de DATs,
              opções de cada núcleo e teste de romsets em núcleos reais (tools/cores)
```

Para **adicionar um console**: crie um `GameSystem` em
`domain/src/main/kotlin/com/retroplay/domain/system/SystemRegistry.kt` e inclua-o em `all`.

Para **trocar o nome do app**: edite `app_name` em `app/src/main/res/values/strings.xml`
(e em `app/src/debug/res/values/strings.xml`). O identificador `com.retroplay.tv` não muda:
trocá-lo faria o Android tratar como outro app (sem atualizar por cima).

**Marca (ícone, splash, banner da TV e logo)**: as artes ficam em `tools/brand/icone.png` (TV
quadrada, fundo azul-marinho) e `tools/brand/logo.png` (logo com fundo transparente). Para
trocar, substitua os arquivos e rode `python3 tools/brand/build_brand.py` (requer Pillow): ele
gera o ícone adaptativo (com versão monocromática para os ícones temáticos), a splash, o banner
640×360 da TV e o logo usado no app.

**Capas dos consoles** (menu Consoles e Início): imagens com quatro quadros em grade 2×2, com uma
faixa preta entre eles, ficam em `tools/brand/`:

- `consoles_grid.png`: Super Nintendo, Mega Drive (em cima); Master System, Arcade (embaixo).
- `consoles_grid2.png`: Nintendinho, Game Boy (em cima); Game Boy Color, Game Boy Advance (embaixo).

O PlayStation e o Atari 2600 vêm de imagens à parte (`console_psx.png`, `console_atari2600.png`),
cortadas no centro. Para trocar, substitua o arquivo e rode
`python3 tools/brand/build_console_covers.py` (ou passe só os arquivos que mudaram). O script acha
as divisórias sozinho e gera `cover_<console>.webp` (3:2, 660×440). Consoles sem capa usam o ícone.

## Informações dos jogos

- **Na Início**, ao focar um jogo numa fileira, o painel do topo mostra o nome, console · ano ·
  gênero · empresa, o resumo e uma cena do jogo ao fundo, como na Netflix. O resumo só é buscado
  depois de o foco parar no jogo.
- **Nos Detalhes**: o resumo (até 5 linhas, com a fonte), as **cenas** (tela de título e uma cena
  de jogo) e **"Ver trailer"**.
- **Resumo:** vem da **Wikipédia** (idioma do app, depois inglês), gratuita e sem chave. Só aceita
  artigos que falam de jogo eletrônico, e só envia o título do jogo e o nome do console. Fica
  guardado no aparelho; sem resultado, tenta de novo em 7 dias; sem internet, não mostra nada.
- **Trailer:** não há fonte de vídeos que o app possa embutir sem chave paga. O botão abre a busca do
  trailer no app do YouTube da TV (ou no navegador).

## Controle na tela (celular)

- **Modelo clássico** (padrão), inspirado no controle original de cada console, sem logotipos:
  losango colorido no SNES, botões pretos no Mega Drive e Master System, alavanca de bolinha e
  botões coloridos no Arcade, símbolos coloridos no PlayStation. Ou o **modelo simples** translúcido.
- **Configurações → Controles:** modelo, tamanho dos botões (80% a 130%), transparência, vibração e
  **Posições dos botões**: escolha o console e arraste cada botão ou o direcional com o dedo
  ("Restaurar padrão" volta ao original). As posições ficam salvas por console.
- **Multitoque:** direcional e vários botões ao mesmo tempo, inclusive com os botões em posições
  personalizadas.

## PlayStation

- **Núcleo:** só o **PCSX ReARMed**, o mais leve: feito para processadores ARM (TVs e celulares),
  com recompilador dinâmico. Roda praticamente todo o catálogo em TVs comuns.
- **Formatos:** `.chd` e `.pbp` (um arquivo por jogo, recomendados), `.cue` + trilhas `.bin`,
  `.iso` e `.m3u`. Jogos de vários discos (ex.: Final Fantasy VII): crie um `.m3u` com o nome de
  cada disco, um por linha. Só o `.m3u` aparece na biblioteca e o menu do jogo ganha **Trocar
  disco** (disco 1/3 → 2/3...). Sem `.m3u`, cada disco aparece como "Jogo (Disco N)".
- **Identificação:** pelo código do disco (ex.: SLUS-00594), lido do `SYSTEM.CNF` da imagem ou do
  `PARAM.SFO` do `.pbp`, sem calcular o CRC de CDs de 700 MB. O banco tem 10.102 discos do DAT
  Redump do libretro-database, 90% com capa. O `.chd` é compactado e é identificado pelo nome do
  arquivo. As trilhas `.bin` citadas por um `.cue` não aparecem como jogos à parte.
- **`.zip` e `.7z`:** o jogo compactado aparece na biblioteca (identificado pelo nome do disco de
  dentro, sem descompactar). Na primeira vez que você joga, o app extrai o disco para o
  armazenamento interno ("Extraindo o jogo… N%") e nas próximas abre direto. Ficam guardados só
  os 2 últimos jogos extraídos, para não encher a TV; o `.zip` original não é alterado. Vários
  discos no mesmo compactado ganham um `.m3u` automático (com "Trocar disco"). `.rar` não é
  aceito. Dica: extrair uma vez no PC/celular economiza espaço na TV (o `.zip` e a cópia
  extraída ocupam o dobro).
- **BIOS (recomendada):** `scph5501.bin` (ou `scph5500`, `scph5502`, `scph1001`, `scph7001`,
  `scph101`) em `Jogos/BIOS`. O app reconhece pelo CRC, mesmo com outro nome, e a coloca onde o
  núcleo procura. Sem BIOS, o núcleo usa uma imitação embutida e alguns jogos podem falhar; o card
  do console mostra "Sem BIOS (recomendada)". Também dá para importar em Configurações → BIOS.
- **Controle:** ✕ ○ □ △, L1/R1, L2/R2, Select e Start. No gamepad, a posição é a do controle original
  (✕ embaixo). No celular, o controle virtual tem o losango e os quatro gatilhos.
- **Pelo celular:** envie o `.chd`/`.pbp`, ou o `.cue` e as trilhas `.bin`; trilhas grandes vão
  direto para a pasta PlayStation, junto do `.cue`.
- **Populares:** 89 jogos de PS1 na lista de populares (Gran Turismo, Crash, Resident Evil, Winning
  Eleven, Tekken 3...).
- **Pendrive e pasta da rede:** o núcleo precisa de um arquivo local; o disco (e as trilhas/discos
  citados) é copiado para o cache antes de abrir, então a primeira abertura de um CD de 700 MB
  pelo pendrive demora. Na pasta Jogos da TV, abre direto.

## Como compilar (linha de comando)

Requisitos: JDK 17+ e Android SDK (plataforma `android-37.2`).

Para um APK release **assinado** no seu computador, crie `keystore.properties` na raiz (o git o
ignora):

```properties
storeFile=/caminho/para/retroplay.jks
storePassword=sua-senha
keyAlias=retroplay
keyPassword=sua-senha
```

e rode `./gradlew :app:assembleTvRelease :app:assembleMobileRelease` (saída em
`app/build/outputs/apk/tv/release/app-tv-release.apk` e `.../mobile/release/app-mobile-release.apk`).
Sem esse arquivo, saem APKs `-unsigned`. A versão vem de `RETROPLAY_VERSION_NAME` e
`RETROPLAY_VERSION_CODE` (o GitHub Actions preenche pela tag e pelo número da execução).

```bash
echo "sdk.dir=/caminho/para/android-sdk" > local.properties
./gradlew test               # testes unitários + telas da TV (src/testTv) e do celular (src/testMobile)
./gradlew :data:testDebugUnitTest -Pretroplay.network=true --tests '*BuildbotLiveTest*'  # baixa núcleos reais
./gradlew :data:testDebugUnitTest -Pretroplay.e2e=true --tests '*PhoneTransferE2ETest*'   # página do celular no Chromium (Node + Playwright)
./gradlew :data:testDebugUnitTest -Pretroplay.smb=true --tests '*SmbE2ETest*'            # pasta da rede contra um Samba local (root + samba instalado)
./gradlew :app:lintTvDebug :app:lintMobileDebug
./gradlew assembleTvDebug assembleMobileDebug   # app/build/outputs/apk/{tv,mobile}/debug/
```

**Baseline Profile**: o app traz um perfil da inicialização e da tela Início
(`app/src/main/baseline-prof.txt`), que o Android usa para compilar esse caminho antes de
abrir o app pela primeira vez. Para medir e gerar um perfil novo com uma TV ou emulador
conectado (Android 9+): `./gradlew :app:generateReleaseBaselineProfile`.

Screenshots das telas geradas pelos testes: `./gradlew :app:testTvDebugUnitTest :app:testMobileDebugUnitTest -Pretroplay.screenshots=true`
(saída em `app/build/screenshots/`).

## Licença

GPL-3.0 (ver [LICENSE](LICENSE)), por causa do LibretroDroid e dos núcleos libretro.
Metadados de jogos: libretro-database (CC BY-SA 4.0) e DAT do FinalBurn Neo. Capas:
libretro-thumbnails (baixadas sob demanda, não incluídas no app).
