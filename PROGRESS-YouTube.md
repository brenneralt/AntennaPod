# Progresso: reprodução de podcasts do YouTube (branch feature/youtube-playback)

## Objetivo
Reproduzir episódios de canais do YouTube adicionados como feed (ex.: Eslen Podcast,
channel_id=UCIHwyJi76D027pbkzct731g). Hoje a lista aparece mas não toca.

## Causa raiz (confirmada)
O RSS do YouTube (`https://www.youtube.com/feeds/videos.xml?channel_id=...`) traz
`<media:content url="https://www.youtube.com/v/<ID>?version=3" type="application/x-shockwave-flash"/>`.
O parser (`parser/feed/.../namespace/Media.java`) cai no fallback "workaround for broken feeds"
e cria `FeedMedia(downloadUrl=<URL /v/>, mimeType=audio/*)`. A URL não é mídia → player falha.

## Plano (decidido)
1. Adicionar NewPipeExtractor v0.27.1 (JitPack) + desugaring `desugar_jdk_libs_nio:2.1.5`.
2. Novo resolvedor em `:net:common` → `net/common/src/main/java/de/danoeh/antennapod/net/common/youtube/YouTubeStreamResolver.java`
   - `isYouTubeMediaUrl(url)` (host youtube.com/youtu.be, path /v/ ou /watch)
   - `resolve(url)` → URL de áudio progressiva (preferir mimeType audio/mp4, maior bitrate),
     cache com TTL ~4h (URLs do YouTube expiram em ~6h), `NewPipe.init` lazy com Downloader OkHttp próprio.
   - Exceção: `Response(int, String, Map<String,List<String>>, String body, String latestUrl)` (5 args);
     `Stream.getContent()` (não `getUrl()`, deprecated); filtrar `DeliveryMethod.PROGRESSIVE_HTTP`.
3. Hook playback: `playback/service/.../internal/ExoPlayerUtils.java` lambda do
   `ResolvingDataSource.Factory` (linhas ~165-179): se YouTube → `dataSpec.withUri(Uri.parse(resolve(...)))`.
   `Resolver.resolveDataSpec` lança IOException (ok). NÃO passar por RedirectChecker (HEAD quebra URL assinada).
   Cache do CacheDataSource fica na URL original → boa invalidação.
4. Hook download: `net/download/service/.../feed/remote/HttpDownloader.java` no início de `download()`:
   resolver `request.getSource()` numa variável local (DownloadRequest NÃO tem setSource).
5. ProGuard: `app/proguard.cfg` adicionar regras do NewPipe:
   `-keep class org.mozilla.javascript.** { *; }`, `-keep class org.mozilla.classfile.ClassFileWriter`,
   `-dontwarn org.mozilla.javascript.tools.**`.
6. Não fazer (por ora): cast (`MediaInfoCreator` usa URL crua), UX de colar @handle do canal
   (via `PodcastSearcherRegistry.lookupUrl`), duração dos itens (fica 0, aceitável).

## Alterações de build já planejadas
- `gradle/libs.versions.toml`: `newpipeextractor = "v0.27.1"` + lib
  `com.github.TeamNewPipe:NewPipeExtractor`; `desugarJdkLibs = "2.1.5"` + `com.android.tools:desugar_jdk_libs_nio`.
- `settings.gradle`: `maven { url 'https://jitpack.io' }` em dependencyResolutionManagement
  (repositório é FAIL_ON_PROJECT_REPOS, só pode no settings).
- `common.gradle`: `coreLibraryDesugaringEnabled true` em compileOptions +
  `coreLibraryDesugaring libs.desugar.jdk.libs.nio` em dependencies (módulos todos aplicam common.gradle).
- `net/common/build.gradle`: `implementation libs.newpipeextractor`.

## Ambiente (feito hoje)
- Android SDK em `/home/brenneralt/android-sdk` (platform-tools, platforms;android-36, build-tools;36.0.0);
  `local.properties` com `sdk.dir` criado (gitignored).
- Build baseline OK: `ANDROID_HOME=/home/brenneralt/android-sdk ./gradlew --console=plain :app:assembleDebug`
  (~8min). Java 21 presente.
- Branch `feature/youtube-playback` criada a partir de develop (0 commits de diferença do upstream).
- Regras do AGENTS.md: strings novas só em `ui/i18n/.../values/strings.xml` (inglês), sem comentários
  novos no código, diff mínimo, nunca truncar saída do compilador, checkstyle `./gradlew checkstyle lint`,
  compilador com `-Werror` + `-Xlint:all` (menos deprecation/serial/unchecked/etc) → código sem warnings.

## Concluído (código + verificação)
- `YouTubeStreamResolver` + hooks playback/download + ajustes de build escritos.
- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL (7m13s), sem warnings no código novo.
- `./gradlew checkstyle` → passou em todos os módulos.
- `./gradlew :net:common:test` → BUILD SUCCESSFUL; 5 testes novos
  (`YouTubeStreamResolverTest`) PASSED em todas as variantes.
- Verificação end-to-end em JVM (`/tmp/opencode/ytnpe/VerifyYouTube.java`, mesma lógica
  do resolvedor): URL do feed `https://www.youtube.com/v/yN-yZ7-PJVk?version=3` →
  resolvida para `googlevideo.com/...itag=140` (audio/mp4 128kbps); probe HTTP 206,
  `Content-Type: audio/mp4` → **URL tocável confirmada**.
  Extração usa cliente OkHttp próprio com UA Firefox ESR (mesmo padrão do NewPipe).

## Verificação final (e2e no emulador)
- `./gradlew checkstyle lint` → BUILD SUCCESSFUL (`LINT_EXIT:0`), 7m35s.
- `./gradlew :parser:feed:test` → `AtomParserTest > testYoutubeMediaContent` PASSED: feed Atom do
  YouTube gera `FeedMedia(downloadUrl=https://www.youtube.com/v/<ID>?version=3, mimeType=audio/*)`.
  Prova que o hook do resolvedor é alcançado no play.
- Emulador `antennapod_test` (android-35, x86_64, swiftshader_indirect, KVM OK via ACL).
- App instalado (`./gradlew --console=plain :app:installPlayDebug`, `INSTALL_EXIT:0`).
- **Playback e2e:** abri feed Eslen via intent (`OnlineFeedViewActivity` com `arg.feedurl`), inscrevi,
  abri Ep. 223, toquei "Stream" → botão virou "Pause", `state=PLAYING(3)`, `MediaCodec track type audio`,
  posição avançando 6 → 3019 → 6024 → 9035 → 42094 → 45090 ms, `error=null`, sem ExoPlaybackException.
  **Playback confirmado.**
- **Download e2e:** Ep. 222, toquei "Download" → `HttpDownloader` rodou: URL `/v/` resolvida, HTTP 200,
  `content type: audio/mp4`, tamanho esperado 65038248, retentativas com resume via `Range`
  (0 → 3,1 → 5,9 → 22,5 → 30,9 MB). **Hook de download confirmado funcionando.**
- Ambiente instável: emulador headless com ANRs recorrentes (CPU 99%, 62% kernel, main thread
  apenas esperando render thread — não bloqueada pelo meu código) e WiFi do emulador caindo a cada
  ~2 min → derrubou playback uma vez (`MediaCodecDecoderException: c2.android.aac.decoder` após
  queda de rede) e impediu o `SUCCESS` final do download (`ERROR_IO_WRONG_SIZE`).
  Ambos os comportamentos são do emulador, não do código.

## Próximo
- Commits na branch `feature/youtube-playback` (`8b6a7ce4d`, `6837f1894`).

## Auditoria pós-commit (encontrado e corrigido)
1. **User-Agent sobrescrito:** `AntennapodHttpClient` aplica `UserAgentInterceptor`
   (AntennaPod/0.0.0) depois do nosso `addHeader`, então as requisições do extractor
   saíam como `AntennaPod/0.0.0` em vez de Firefox ESR. Corrigido: `ExtractorDownloader`
   agora usa `newBuilder()` com interceptor próprio que re-aplica o UA após o global.
2. **jsoup divergente:** NewPipeExtractor puxa jsoup 1.24.1, mas o projeto declarava
   1.15.1 (compilava contra 1.15.1 e rodava com 1.24.1). `libs.versions.toml` atualizado
   para 1.24.1; `:app:assembleDebug`, `checkstyle lint` e testes de `:net:common`,
   `:parser:feed`, `:parser:transcript` e `ShownotesCleanerTest` passam.
3. **Build release (R8) quebrava:** Rhino referenciava `javax.script.*` (via
   `rhino-engine`, morto no Android), `java.beans.*` e `jdk.dynalink.*` — classes JDK
   inexistentes no Android → "Missing classes detected while running R8". Corrigido:
   exclusão de `org.mozilla:rhino-engine` em `:net:common` + `-dontwarn` do que sobra
   (núcleo do Rhino). `:app:minifyFreeReleaseWithR8` agora passa; a build release
   completa só falha em `validateSigningFreeRelease` (keystore não commitado —
   esperado). `:app:assembleDebug` e `checkstyle lint` re-executados com sucesso.
4. **Sem impacto (não corrigido):** `MediaSizeLoader` faz HEAD na URL `/v/`; o YouTube
   responde 200 com `Content-Length: 0`, resultando em `checkedOnSizeButUnknown`
   (tamanho exibido como desconhecido). Comportamento pré-existente do app; só um
   request extra por episódio do YouTube.

## Conversão automática de URL do YouTube em URL de feed
- Nova classe `net/common/.../youtube/YouTubeFeedUrl`: `fromUserInput()` normaliza entrada
  (`@handle`, URL sem esquema), `needsLookup()` decide se há conversão pendente,
  `resolve()` converte (`list=PL…` → `playlist_id`, `list=UU`+22 → `channel_id`,
  `/channel/UC…` → `channel_id`; páginas `@handle`/`/c/`/`/user/` → busca do
  `<link rel="canonical">` para obter o `channel_id`).
- Ponto central: `PodcastSearcherRegistry.lookupUrl()`/`urlNeedsLookup()` chamam a
  conversão (usado por `OnlineFeedViewActivity`); `AddFeedFragment.performSearch`,
  diálogo de URL manual e `SearchFragment.searchOnline` também normalizam a entrada.
- `YouTubeFeedUrlTest` (11 testes) passa em `:net:common:test`; `:app:assembleDebug`,
  `checkstyle lint` BUILD SUCCESSFUL.
- Pendente: verificação e2e no aparelho (aparelho desconectado no momento).
