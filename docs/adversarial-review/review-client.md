# АДВЕРСАРИАЛЬНЫЙ РЕВЬЮ КЛИЕНТА — Air Tactical Arsenal (MC 1.21.1 / NeoForge 21.1.230)

Домен: `client/**`, `mixins/**`, `particle/**`, `assets/ata/shaders/**`.
Метод: полное чтение файлов. Каждая находка подтверждена цитатой.
Найдено **26 подтверждённых дефектов** (4 critical, 12 high, 8 medium, 2 low).

---

## РАЗДЕЛ A. ШЕЙДЕР ПНВ (`NvgShaderHandler` + `assets/ata/shaders`)

### F-1: `processShader()` пересоздаёт PostChain КАЖДЫЙ КАДР при любой ошибке загрузки — неограниченная утечка GPU-ресурсов
СЕРЬЁЗНОСТЬ: critical
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/client/shader/NvgShaderHandler.java:56-74`
КОД:
```java
        if (nvgEnabled) {
            PostChain current = renderer.currentEffect();

            if (current == null || !isOurShader(current)) {

                try {
                    renderer.loadEffect(NVG_SHADER);
                    ...
                } catch (Throwable ignored) {}
            }
        }
```
ПОЧЕМУ ЭТО БАГ: ванильный `GameRenderer.loadEffect(ResourceLocation)` сам ловит `IOException | JsonSyntaxException` внутри себя, выставляет `this.postEffect = null; this.effectActive = false;` и **не бросает наружу**. Значит `catch (Throwable ignored)` здесь никогда не сработает, а `renderer.currentEffect()` вернёт `null` — и условие `current == null` снова истинно на следующем кадре. Получается бесконечный цикл «загрузить → не получилось → загрузить». При этом конструктор `PostChain` до момента падения (компиляция GLSL — см. F-2 — падает уже ПОСЛЕ разбора JSON) успевает создать временный рендер-таргет `"swap"` (`TextureTarget` = реальный GL FBO + цветовая и глубинная текстуры полного разрешения). Ссылка на недостроенный `PostChain` наружу не выходит, `close()` на нём никто не вызовет. Итог: **один полноэкранный FBO + текстура утекают на КАЖДОМ кадре**, пока `nvgEnabled == true`. При 60 FPS и 1920×1080 это ~500 МБ VRAM за 60 секунд. Никакого счётчика неудач/бэкоффа нет — единственный способ остановить утечку — выключить ПНВ или выйти из камеры.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: подключиться к камере Орлана → нажать `N` → если шейдер не компилируется (macOS, любой core-profile-строгий драйвер, либо ресурспак перекрыл `ata:textures/effect/noise.png`) → картинка не меняется, но VRAM растёт линейно; через 30–90 с драйвер отдаёт `GL_OUT_OF_MEMORY` / крэш.
ТЕСТИРУЕМОСТЬ: manual
НАБРОСОК ТЕСТА: временно переименовать `assets/ata/shaders/program/night-vision.fsh` → загрузиться, включить ПНВ, следить за `nvidia-smi`/`GPU-Z`. КРИТЕРИЙ ПРОВАЛА: потребление VRAM растёт монотонно > 5 МБ/с и не возвращается.

---

### F-2: Фрагментный шейдер написан на `#version 120` (`varying`, `gl_FragColor`) — несовместим с core-профилем MC 1.21
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `src/main/resources/assets/ata/shaders/program/night-vision.fsh:1-11`
КОД:
```glsl
#version 120

uniform float NightVisionEnabled;
...
varying vec2 texCoord;
varying vec2 oneTexel;
varying vec4 outPos;
```
и в конце:
```glsl
    gl_FragColor = vec4(texColor.rgb, 1.0);
```
ПОЧЕМУ ЭТО БАГ: Minecraft 1.21 создаёт OpenGL 3.2 **core profile**. В core-профиле GLSL 1.20 отрицается спецификацией: ключевые слова `varying` и встроенная переменная `gl_FragColor` удалены. Все ванильные post-шейдеры 1.21 используют `#version 150`, `in vec2 texCoord`, `out vec4 fragColor`. Кроме того, вершинный шейдер здесь — ванильный `"vertex": "blit"` (`program/night-vision.json:5`), который является `#version 150` и объявляет **только** `texCoord`; объявленные тут `oneTexel` и `outPos` не имеют источника. Дополнительно `if (NightVisionEnabled > 0)` и `sin(Time * 10)` — сравнение/умножение float с int-литералом, допустимое в 120 и **запрещённое** в 150 core. На драйверах NVIDIA/AMD под Windows compat-расширения могут это проглотить, на macOS и на строгих драйверах (Mesa с `MESA_GL_VERSION_OVERRIDE`, Intel Arc) компиляция падает → см. F-1 (бесконечный ретрай + утечка).
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: запустить клиент на macOS или Linux/Mesa → камера Орлана → `N`. Наблюдаемо: ПНВ не включается (кроме белого/чёрного оверлея из `OrlanCameraOverlay.renderNightVision`), в `latest.log` — `Failed to load shader: ata:shaders/post/night-vision.json` + GLSL error, и далее — деградация FPS/VRAM.
ТЕСТИРУЕМОСТЬ: manual
НАБРОСОК ТЕСТА: запустить с `-Dorg.lwjgl.opengl.Display.forceCoreProfile` / на macOS. КРИТЕРИЙ ПРОВАЛА: в логе есть хотя бы одна строка о неудачной компиляции `ata:night-vision`.

---

### F-3: `loadEffect()` вызывается ПОСРЕДИ `LevelRenderer.renderLevel` и переустанавливает биндинги GL
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/client/ClientGameEvents.java:104-108` + `client/shader/NvgShaderHandler.java:61-73`
КОД:
```java
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        NvgShaderHandler.processShader();
    }
```
```java
                    renderer.loadEffect(NVG_SHADER);
                    ...
                    RenderTarget mainTarget = mc.getMainRenderTarget();
                    mainTarget.bindWrite(true);
                    RenderSystem.bindTextureForSetup(mainTarget.getColorTextureId());
                    RenderSystem.bindTextureForSetup(mainTarget.getDepthTextureId());
```
ПОЧЕМУ ЭТО БАГ: `RenderLevelStageEvent.AFTER_PARTICLES` вызывается изнутри `LevelRenderer.renderLevel`, когда основной таргет уже привязан и впереди ещё проходы (weather, clouds, `bufferSource.endBatch()`). В ванилле `loadEffect` вызывается только из `checkEntityPostEffect`/перезагрузки ресурсов, вне кадра. Здесь она (а) `close()`-ит текущий эффект и создаёт новые FBO прямо в середине прохода, (б) `bindWrite(true)` переустанавливает viewport на размер main-таргета, (в) `bindTextureForSetup` меняет привязку текстурного юнита `GL_TEXTURE0` и **не восстанавливает** её. Всё это — на кадре, когда игрок нажал `N`. Кроме того, `processShader()` вызывается **безусловно каждый кадр**, даже когда FPV выключен и ПНВ никогда не включали, — два вызова `renderer.currentEffect()` и `isOurShader()` (`String.contains`) на кадр без раннего выхода по `OrlanCameraHandler.isCameraActive()`.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: FPV → `N` → на кадре включения наблюдается кадр с искажённой геометрией облаков/погоды или мигание. Гарантированно воспроизводимо на кадре переключения; глазами заметно при F3 (замер frametime-спайка).
ТЕСТИРУЕМОСТЬ: manual
НАБРОСОК ТЕСТА: включать/выключать `N` 20 раз подряд, снимая скриншоты каждый кадр. КРИТЕРИЙ ПРОВАЛА: хотя бы один кадр с некорректным viewport/облаками.

---

### F-4: `isOurShader()` определяет «свой» шейдер подстрокой `"ata"` — глушит чужие post-цепочки
СЕРЬЁЗНОСТЬ: low
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/client/shader/NvgShaderHandler.java:24, 45-48`
КОД:
```java
    private static final String SHADER_NAME_MARKER = "ata";
...
    private static boolean isOurShader(PostChain effect) {
        String name = effect.getName();
        return name != null && name.contains(SHADER_NAME_MARKER);
    }
```
ПОЧЕМУ ЭТО БАГ: `PostChain.getName()` возвращает полную строку ResourceLocation JSON-а. Проверка `contains("ata")` (три символа!) совпадает с любым модом, в чьём namespace или пути есть эта последовательность. При `nvgEnabled == false` ветка `else` в `processShader()` (строки 76-83) **безусловно вызывает `renderer.shutdownEffect()`** для такого чужого эффекта — каждый кадр. Также: если при включении ПНВ активен ванильный/чужой эффект (спектейт крипера), `loadEffect` его выбрасывает, а при выключении ПНВ он не восстанавливается никогда.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: спектейт-режим на крипере (ванилла грузит `creeper.json`) → войти в FPV → `N` вкл → `N` выкл → эффект крипера исчез навсегда до смены сущности спектейта.
ТЕСТИРУЕМОСТЬ: manual
НАБРОСОК ТЕСТА: `/gamemode spectator`, `F5` на криперa, включить/выключить ПНВ. КРИТЕРИЙ ПРОВАЛА: зелёный фильтр крипера не вернулся.

---

## РАЗДЕЛ B. МИКСИНЫ

### F-5: Три `@Redirect` на `LocalPlayer.getX/Y/Z()` внутри `setupRender` + `"required": true` / `defaultRequire: 1` — игра НЕ ЗАПУСТИТСЯ при конфликте
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/mixins/LevelRendererMixin.java:26-45` и `src/main/resources/ata.mixins.json:2,12-14`
КОД:
```java
    @Redirect(method = "setupRender", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getX()D"))
    private double ata$renderCenterX(LocalPlayer player) {
```
```json
{
  "required": true,
  ...
  "injectors": {
    "defaultRequire": 1
  }
}
```
ПОЧЕМУ ЭТО БАГ: три проблемы сразу.
1. **Хрупкость таргета.** Инжект привязан не к сигнатуре метода, а к конкретному *вызову внутри тела* `setupRender`. Любой мод, переписывающий тело `setupRender` (Sodium/Embeddium заменяют секционный рендер целиком; Optifine — тем более), удаляет эти три `INVOKE` — `@Redirect` не находит точку, `defaultRequire: 1` требует минимум одно применение, `"required": true` запрещает мягкий отказ → `MixinApplyError` → **клиент падает на старте**, а не «просто ломается FPV».
2. **`@Redirect` эксклюзивен.** Два мода не могут редиректить один и тот же `INVOKE`. Любой мод-шейдер/оптимизация, редиректящий `minecraft.player.getX()` в `setupRender`, даёт неразрешимый конфликт — снова крэш на старте.
3. Три отдельных редиректа вместо одного (или `@ModifyVariable` на локальную переменную) утраивают площадь поражения: достаточно, чтобы хотя бы **один** из трёх не применился.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: положить в `mods/` Embeddium (или Sodium через Sinytra) для 1.21.1 рядом с ATA → запустить клиент. Наблюдаемо: клиент не доходит до главного меню, в `crash-report` — `Critical injection failure: Redirector ata$renderCenterX(...) in ata.mixins.json:LevelRendererMixin failed injection check, (0/1)`.
ТЕСТИРУЕМОСТЬ: manual
НАБРОСОК ТЕСТА: собрать сборку ATA + Embeddium 1.21.1, запустить. КРИТЕРИЙ ПРОВАЛА: ненулевой exit code клиента / crash-report с `failed injection check`.

---

### F-6: Оба миксина берут «клиентскую» позицию дрона, тогда как сервер шлёт центр по своей — рассинхрон центра чанк-кэша
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/mixins/ClientChunkCacheMixin.java:23-33`
КОД:
```java
    @ModifyVariable(method = "updateViewCenter", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int ata$viewCenterX(int x) {
        Entity cam = ata$fpvCamera();
        return cam != null ? cam.chunkPosition().x : x;
    }
```
ПОЧЕМУ ЭТО БАГ: сервер в `OrlanCameraViewPacket.sendDroneChunks` (строка 110) авторитетно шлёт `new ClientboundSetChunkCacheCenterPacket(droneChunk.x, droneChunk.z)`, вычисленный по **серверной** позиции Орлана, и затем в `OrlanEntity` (строки 509-516) досылает дельту чанков относительно `lastSentChunkPos` — тоже серверного. Миксин безусловно затирает пришедший аргумент клиентской `cam.chunkPosition()`. Клиентская сущность Орлана интерполируется (`setServerPosition`, `OrlanSpawnPacket` c `SYNC_INTERVAL`), поэтому на скорости дрона центр расходится с серверным на 1+ чанк. Все чанки, которые сервер прислал по своему центру и которые попали на границу `Storage.inRange` относительно клиентского центра, `ClientChunkCache.replace()` **отбросит** с логом `Ignoring chunk since it's not in the view range` — и повторно они не придут никогда (сервер считает их отправленными, `lastSentChunkPos` уже сдвинут). Дыры в мире вокруг дрона.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: FPV на крейсерской скорости над незагруженной территорией 2–3 минуты. Наблюдаемо: под дроном появляются «дырки» из пустых чанков, которые не заполняются; в `latest.log` — многократные `Ignoring chunk since it's not in the view range`.
ТЕСТИРУЕМОСТЬ: manual
НАБРОСОК ТЕСТА: `grep "not in the view range" logs/latest.log` после 3 минут FPV. КРИТЕРИЙ ПРОВАЛА: > 0 совпадений.

---

## РАЗДЕЛ C. `OrlanCameraHandler` / `ClientGameEvents` — «залипшее» состояние

### F-7: Поворот игрока (`XRot`/`YRot`) переписывается каждый кадр и НИКОГДА не восстанавливается при выходе
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/client/ClientGameEvents.java:63-82`
КОД:
```java
        if (OrlanCameraHandler.updateTrackingAngles((float) event.getPartialTick())) {
            mc.player.setXRot(OrlanCameraHandler.getCameraPitch());
            mc.player.setYRot(OrlanCameraHandler.getCameraYaw());
        } else {
            OrlanCameraHandler.setCameraYaw(mc.player.getYRot());
            OrlanCameraHandler.setCameraPitch(mc.player.getXRot());
            mc.player.setXRot(OrlanCameraHandler.getCameraPitch());
        }
```
ПОЧЕМУ ЭТО БАГ: `setCameraPitch` жёстко клампит в `[MIN_CAMERA_PITCH=20, MAX_CAMERA_PITCH=90]` (`OrlanCameraHandler.java:30-31, 242-244`). Результат:
1. Клампованное значение записывается **обратно в тело игрока** — `mc.player.setXRot(...)`. Ни `stopCameraView()`, ни `forceStopCameraLocal()` (строки 78-125) не сохраняют и не восстанавливают исходный `XRot`/`YRot`. После выхода из FPV игрок гарантированно смотрит в землю минимум на 20°.
2. `LocalPlayer.sendPosition()` сравнивает `getYRot()/getXRot()` с `lastSentYRot/XRot` и при расхождении шлёт `ServerboundMovePlayerPacket.Rot` — то есть тело реально крутится на сервере под управлением рендер-события. При активном lock-on (`updateTrackingAngles` возвращает true) yaw тела крутится вслед за целью каждый кадр → поток пакетов поворота 20/с и видимое всем остальным игрокам вращение «замороженного» тела.
3. Мутация состояния сущности из `ViewportEvent.ComputeCameraAngles` (рендер-поток, частота = FPS) вместо тика: при 200 FPS клампинг применяется в 10 раз чаще, чем при 20 FPS.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: посмотреть в небо (питч ≈ −90) → подключиться к Орлану через планшет → сразу нажать `LAlt`. Наблюдаемо: после выхода камера игрока смотрит вниз под ≈20–30°, а не в небо. Во время FPV поднять взгляд выше горизонта невозможно вообще.
ТЕСТИРУЕМОСТЬ: manual
НАБРОСОК ТЕСТА: записать `mc.player.getXRot()` до входа (`-80`) и после выхода. КРИТЕРИЙ ПРОВАЛА: `after != before`.

---

### F-8: `startCameraView()` включает `cameraActive` ДО проверки существования сущности — до 5 секунд «сломанного» полусостояния
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/client/OrlanCameraHandler.java:52-76`, `46-47`, `187-194`
КОД:
```java
    public static void startCameraView(int orlanEntityId) {
        cameraActive = true;
        linkedOrlanId = orlanEntityId;
        ...
        if (mc.level != null) {
            Entity orlan = mc.level.getEntity(orlanEntityId);
            if (orlan != null) {
                mc.setCameraEntity(orlan);
            }

        }
        mc.options.setCameraType(CameraType.FIRST_PERSON);
    }
```
```java
    private static final int NULL_ENTITY_GRACE_TICKS = 100;
```
ПОЧЕМУ ЭТО БАГ: вызывающая сторона — `OrlanTabletScreen.connectCamera` (`client/gui/OrlanTabletScreen.java:473-477`) — шлёт пакет на сервер и **немедленно локально** вызывает `startCameraView`. Сущность Орлана на клиенте может отсутствовать (она приходит `ClientboundAddEntityPacket`-ом из обработчика того же пакета на сервере, т.е. минимум round-trip позже). Тогда `mc.setCameraEntity` не вызывается вовсе, а `cameraActive == true`. Все потребители сразу переходят в FPV-режим, глядя **глазами самого игрока**:
* `ClientGameEvents.onComputeCameraAngles` начинает клампить питч игрока (F-7);
* `ClientGameEvents.onRenderHand:111-115` отменяет отрисовку руки;
* `ClientGameEvents.onPreGuiLayer:117-128` отменяет **весь** HUD;
* `OrlanCameraOverlay.render` выходит (`getLinkedOrlan()` вернёт null), т.е. и HUD дрона тоже не рисуется.
`tick()` будет повторять попытку до `nullEntityTicks > 100`, то есть **5 секунд** полностью чёрного/пустого интерфейса, прежде чем `pendingStop` завершит сессию. Если сервер отклонил подключение — 5 секунд гарантированно.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: на сервере с пингом ≥100 мс открыть планшет Орлана, кликнуть по дрону, находящемуся дальше render distance от тела (сущность ещё не отслеживается клиентом). Наблюдаемо: HUD и рука пропадают, вид остаётся от лица игрока, взгляд принудительно наклоняется вниз, состояние держится ~5 с.
ТЕСТИРУЕМОСТЬ: manual
НАБРОСОК ТЕСТА: `/tp` дрон на 500 блоков, затем подключиться. КРИТЕРИЙ ПРОВАЛА: между кликом и появлением картинки с дрона >1 с без HUD.

---

### F-9: Повторный `startCameraView()` при активной камере: затирает `savedCameraType` и оставляет предыдущий дрон привязанным на сервере
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/client/OrlanCameraHandler.java:52-76`, `42`; `client/gui/OrlanTabletScreen.java:473-477`
КОД:
```java
    private static CameraType savedCameraType = CameraType.FIRST_PERSON;
...
    public static void startCameraView(int orlanEntityId) {
        cameraActive = true;
        linkedOrlanId = orlanEntityId;
        ...
        savedCameraType = mc.options.getCameraType();
```
```java
    private void connectCamera(int entityId) {
        NetworkHandler.sendToServer(new OrlanCameraViewPacket(entityId, true));
        OrlanCameraHandler.startCameraView(entityId);
        this.onClose();
    }
```
ПОЧЕМУ ЭТО БАГ: `startCameraView` **не проверяет `cameraActive`** и не отправляет `OrlanCameraViewPacket(старыйId, false)`. Планшет открывается предметом из руки, а FPV не блокирует использование предмета (отменяется только *рендер* руки). Последствия при подключении к дрону B, будучи в камере дрона A:
1. `savedCameraType = mc.options.getCameraType()` = `FIRST_PERSON` (его выставил первый вход) → исходный вид от третьего лица потерян навсегда.
2. На сервере `orlanA.setLinkedPlayer(uuid)` **остаётся**, к нему добавляется `orlanB.setLinkedPlayer(uuid)`. Оба Орлана в своём `tick()` (см. `entity/OrlanEntity.java:449-521`) каждый тик делают `linkedPlayer.absMoveTo(cameraPhysicsPos...)` со **своими разными** `cameraPhysicsPos`, и оба вызывают `sendDroneChunksDelta(...)` со своими центрами. Тело игрока телепортируется между двумя точками каждый тик, а `ClientboundSetChunkCacheCenterPacket` мечется между чанками двух дронов → массовый сброс чанков.
3. При выходе (`stopCameraView`) отвязывается только B. A останется привязан, пока не приземлится/не будет уничтожен, продолжая дёргать позицию игрока и центр чанков.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: запустить два Орлана → F5 (третье лицо) → планшет → подключиться к Орлану #1 → ПКМ планшетом (открывается поверх FPV) → подключиться к Орлану #2. Наблюдаемо: мир вокруг мерцает пустыми чанками, тело игрока трясётся; после `LAlt` игрок остаётся в первом лице и мерцание продолжается до конца полёта Орлана #1.
ТЕСТИРУЕМОСТЬ: manual
НАБРОСОК ТЕСТА: описанная последовательность; проверить `/data get entity @e[type=ata:orlan]` — КРИТЕРИЙ ПРОВАЛА: у двух сущностей одновременно непустой `LinkedPlayer`.

---

### F-10: `stopCameraView()` вызывает `setCameraEntity(null)` без null-проверки (в отличие от `forceStopCameraLocal`)
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/client/OrlanCameraHandler.java:98-102` vs `118-124`
КОД:
```java
        try {
            Minecraft mc = Minecraft.getInstance();
            mc.setCameraEntity(mc.player);          // <-- без проверки
            mc.options.setCameraType(savedCameraType);
        } catch (Exception ignored) {}
```
```java
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {                // <-- а тут проверка есть
                mc.setCameraEntity(mc.player);
            }
            mc.options.setCameraType(savedCameraType);
        } catch (Exception ignored) {}
```
ПОЧЕМУ ЭТО БАГ: `Minecraft.setCameraEntity(null)` в 1.21 присваивает поле `cameraEntity = null` (в vanilla есть fallback только в геттере частично). Путь достижимости: `OrlanCameraHandler.tick()` вызывается из `ClientTickEvent.Post` и при `pendingStop == true` первым делом делает `stopCameraView()` — **до** проверки `mc.level == null`. Если игрок нажал `LAlt` (выставив `pendingStop = true`) и в тот же тик соединение оборвалось (`mc.player == null`), `setCameraEntity(null)` оставит клиент без камеры. Асимметрия с `forceStopCameraLocal` доказывает, что автор знал о необходимости проверки, но не продублировал её. Плюс `catch (Exception ignored)` проглотит NPE, но состояние `cameraEntity == null` останется.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: FPV → `LAlt` → в тот же тик разрыв соединения (kill сервера). Наблюдаемо: `cameraEntity` остаётся null; при последующем подключении первый кадр рендерится из нулевой камеры.
ТЕСТИРУЕМОСТЬ: pure-java
НАБРОСОК ТЕСТА: юнит-тест на порядок операций в `tick()`:
```java
// pendingStop=true, mc.player=null  ->  stopCameraView() выполняется ДО guard'а mc.level==null
assertThrows(NullPointerException.class, () -> OrlanCameraHandler.tick());
```
КРИТЕРИЙ ПРОВАЛА: `tick()` доходит до `mc.setCameraEntity(mc.player)` при `mc.player == null`.

---

### F-11: Хардкод клавиш `LAlt`/`R`/`N` в `InputEvent.Key` без проверки `mc.screen == null` — срабатывают при вводе текста
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/client/ClientGameEvents.java:91-102`
КОД:
```java
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getKey() == GLFW.GLFW_KEY_LEFT_ALT && event.getAction() == GLFW.GLFW_PRESS) {
            OrlanCameraHandler.handleExitKey();
        }
        if (event.getKey() == GLFW.GLFW_KEY_R) {
            OrlanCameraHandler.handleLockKey(event.getAction());
        }
        if (event.getKey() == GLFW.GLFW_KEY_N && event.getAction() == GLFW.GLFW_PRESS) {
            OrlanCameraHandler.toggleNvg();
        }
    }
```
ПОЧЕМУ ЭТО БАГ: два дефекта.
1. `InputEvent.Key` в NeoForge 1.21 диспатчится из `KeyboardHandler.keyPress` **до** обработки открытого экрана — то есть срабатывает даже когда сфокусирован `EditBox`. Сам код мода это признаёт: `OrlanCameraHandler.tickAltitude():363` явно проверяет `if (mc.screen != null) return;`, а обработчик клавиш — нет. Пока активна FPV-камера, набор текста в поле «Имя маршрута»/координат в любом экране ATA приводит к выходу из камеры (`LAlt`), запуску захвата цели (`R`) и переключению ПНВ (`N`).
2. Клавиши не зарегистрированы как `KeyMapping` — их нельзя переназначить, они жёстко конфликтуют: `R` в ванилле не занята, но занята в десятках модов (перезарядка в Superb Warfare — с которым мод явно интегрируется, см. `ShahedRadarScreen:501` `SuperbWarfareCompat.isLoaded()`), `N` — в JEI/REI/Xaero. `LAlt` — стандартный модификатор.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: FPV → ПКМ планшетом Орлана (экран открывается) → в поле имени напечатать «Разведка Норд». Наблюдаемо: на букве `н`/`N` (латинская раскладка) переключается ПНВ, на `R` начинается «ЗАХВАТ», а `LAlt` (частая часть комбинации смены раскладки Alt+Shift) немедленно рвёт FPV-сессию.
ТЕСТИРУЕМОСТЬ: manual
НАБРОСОК ТЕСТА: FPV + открытый `OrlanRouteScreen`, напечатать в `EditBox` строку `rn`. КРИТЕРИЙ ПРОВАЛА: `OrlanCameraHandler.isNvgActive()` изменился или `isLocking()` == true.

---

### F-12: `onPreGuiLayer` отменяет ВСЕ слои HUD, а не только те, что заменяет оверлей
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/client/ClientGameEvents.java:117-128`
КОД:
```java
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPreGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (!OrlanCameraHandler.isCameraActive()) return;

        if (event.getName().equals(VanillaGuiLayers.CROSSHAIR)) {
            ...
        }

        event.setCanceled(true);
    }
```
ПОЧЕМУ ЭТО БАГ: `setCanceled(true)` стоит **вне** `if`, то есть отменяется каждый слой: `CHAT`, `PLAYER_LIST`, `SUBTITLE_OVERLAY`, `BOSS_OVERLAY`, `TITLE`, `SCOREBOARD`, `NOTIFICATION_TOAST`, `SLEEP_OVERLAY`. Во время FPV игрок не видит ни чата (включая свои же отправленные сообщения и системные предупреждения), ни списка игроков, ни тостов о достижениях/рецептах, ни титров. На сервере это означает пропуск административных сообщений и приглашений. Кроме того, поскольку `Pre` для `CROSSHAIR` всегда отменён, `onPostGuiLayer` (строки 130-151) для `CROSSHAIR` **никогда** не вызывается — индикатор заряда баллончика мёртв во время FPV (что не критично, но код-путь недостижим).
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: FPV → второй игрок пишет в чат / `/say тест`. Наблюдаемо: сообщение не появляется на экране совсем (не «пропадает через 10 с», а не рисуется вовсе). Нажатие `T` тоже бесполезно — экран чата откроется, но `InputEvent.Key` (F-11) параллельно ломает состояние.
ТЕСТИРУЕМОСТЬ: manual
НАБРОСОК ТЕСТА: FPV + `/say hello`. КРИТЕРИЙ ПРОВАЛА: строка не видна на экране.

---

## РАЗДЕЛ D. КЛИЕНТСКИЕ КЭШИ И «ЗАЛИПШЕЕ» СОСТОЯНИЕ МЕЖДУ МИРАМИ

### F-13: `TerrainMapRenderer.cachedPngBytes` — static, НИКОГДА не очищается → карта радара из ПРОШЛОГО МИРА
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/client/radar/TerrainMapRenderer.java:768-769, 792-794, 848-850`
КОД:
```java
    private static byte[] cachedPngBytes;
    private static int cachedRadarX, cachedRadarZ;
...
        if (cachedPngBytes != null && cachedRadarX == radarX && cachedRadarZ == radarZ) {
            loadFromPngBytes(cachedPngBytes);
        }
```
ПОЧЕМУ ЭТО БАГ: `ClientGameEvents.onClientDisconnect:36-41` очищает `ClientPacketHandler`, `RouteTerrainRenderer.clearCache()` и `SurfaceHeightCache.clear()`, но **`TerrainMapRenderer` в этом списке отсутствует**. Ключом кэша служат только мировые координаты `radarX/radarZ` — без идентификатора мира/измерения/сида. Итог:
1. Полный PNG карты 1536×1536 висит в куче до перезапуска клиента (клиент → главное меню → 30 МБ+ так и не освобождаются).
2. Открытие радара с теми же координатами **в другом мире** (или в Нижнем мире, или на другом сервере) немедленно показывает рельеф старого мира, `cacheReady = true`, и запрос новой карты **не отправляется** — `ShahedRadarScreen.init:135` шлёт `RequestRadarMapPacket` только при `!terrainMap.hasCacheData()`. Карта неверная навсегда: `forceRebuild()` (строка 897) не вызывается ниоткуда.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: мир A → поставить радар на `0/64/0` → открыть, дождаться карты → выйти в меню → создать мир B → поставить радар на `0/64/0` → открыть. Наблюдаемо: мгновенно (без сканирования) отображается рельеф мира A.
ТЕСТИРУЕМОСТЬ: manual
НАБРОСОК ТЕСТА: описанная последовательность с двумя мирами разного сида. КРИТЕРИЙ ПРОВАЛА: карта радара в мире B идентична карте мира A и «Сканирование %» не появляется.

---

### F-14: `ShahedRadarScreen.removed()` безусловно обнуляет `currentInstance` — радар перестаёт получать цели
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/client/gui/ShahedRadarScreen.java:109-116, 118-126, 141-146`
КОД:
```java
    public static void open(BlockPos pos) {
        if (currentInstance != null) {
            currentInstance.closeTerrainMap();
        }
        ShahedRadarScreen screen = new ShahedRadarScreen(pos);
        currentInstance = screen;
        Minecraft.getInstance().setScreen(screen);
    }
...
    public static void updateTargets(BlockPos radarPos, List<RadarUpdatePacket.TargetData> data) {
        if (currentInstance == null || !currentInstance.radarPos.equals(radarPos)) return;
...
    @Override
    public void removed() {
        super.removed();
        closeTerrainMap();
        currentInstance = null;
    }
```
ПОЧЕМУ ЭТО БАГ: `Minecraft.setScreen(newScreen)` сначала вызывает `oldScreen.removed()`, и только потом присваивает новый экран. В `open()` `currentInstance` выставляется на новый экран **до** `setScreen`. Поэтому при последовательности «открыт радар S1 → пришёл ещё один `OpenRadarScreenPacket` → `open()`» происходит: `currentInstance = S2` → `setScreen(S2)` → `S1.removed()` → **`currentInstance = null`**. После этого `updateTargets` выходит на первой же строке, и радар S2 **никогда не показывает целей** — при этом `tick()` продолжает раз в секунду слать `RequestRadarUpdatePacket` (строки 178-184), а сервер продолжает слать ответы в пустоту.
Достижимость: два быстрых ПКМ по блоку радара (второй — до прихода первого ответа с сервера, т.е. в пределах round-trip ~100 мс на любом реальном сервере). Также `removed()` не проверяет `currentInstance == this`, поэтому обнуление происходит и при любом другом порядке.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: на сервере с пингом 80+ мс дважды быстро кликнуть ПКМ по блоку радара при летящих Шахедах. Наблюдаемо: правая панель «ЦЕЛИ» пуста, статус «✔ ЧИСТО», хотя дроны в радиусе 1500 блоков.
ТЕСТИРУЕМОСТЬ: manual
НАБРОСОК ТЕСТА: два ПКМ подряд по радару, затем `/summon ata:shahed ~ ~50 ~`. КРИТЕРИЙ ПРОВАЛА: цель не появляется на радаре в течение 3 с, но появляется после Esc + одиночного ПКМ.

---

### F-15: Коллизия ключей `SurfaceHeightCache` между `ShahedRouteScreen` и `OrlanRouteScreen` — чужой профиль рельефа
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/client/gui/SurfaceHeightCache.java:16-17`;
`client/gui/ShahedRouteScreen.java:1251, 1285`; `client/gui/OrlanRouteScreen.java:1016, 1049`
КОД:
```java
    private static final Map<Integer, Integer> segmentHeightCache = new HashMap<>();
    private static final Map<Integer, int[]> segmentProfileCache = new HashMap<>();
```
```java
// ShahedRouteScreen.java:1251
        int segKey = currentEditIndex * 100 + wpIdx;
        SurfaceHeightCache.requestSegmentHeight(prev.getX(), prev.getZ(), wp.getX(), wp.getZ(), segKey);
```
```java
// OrlanRouteScreen.java:1016 — ТОТ ЖЕ САМЫЙ ключ
        int segKey = currentEditIndex * 100 + wpIdx;
        SurfaceHeightCache.requestSegmentHeight(prev.getX(), prev.getZ(), wp.getX(), wp.getZ(), segKey);
```
ПОЧЕМУ ЭТО БАГ: оба экрана пишут и читают **один и тот же статический** `segmentProfileCache` по идентичной формуле `launcherIndex * 100 + waypointIndex`. Кэш очищается только в `ClientGameEvents.onClientDisconnect` (`SurfaceHeightCache.clear()`), а не в `Screen.removed()`. Следствия:
1. Открыли экран маршрута Шахеда (профиль сегмента `0*100+1` закэширован для трассы Шахеда) → закрыли → открыли экран маршрута Орлана: `renderHeightProfile` (`OrlanRouteScreen:1049`) немедленно берёт `getSegmentProfile(1)` = **профиль совершенно другого маршрута другой ПУ** и рисует его как рельеф под Орланом, включая красную индикацию «опасно» (`danger = terrainH >= droneH - 5`). Игрок планирует высоты по чужому рельефу.
2. Внутри одного экрана: перетащили точку #3 на другое место → `requestSegmentHeightForWaypoint(3)` уходит на сервер, но до ответа `render()` рисует **старый** профиль для нового положения. Ответа может не быть вовсе, если игрок закрыл/переоткрыл экран — тогда старый профиль остаётся навсегда.
3. `int[] profile` массивы никогда не удаляются — карта растёт до конца сессии.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: открыть «МАРШРУТ ШАХЕДА», расставить 5 точек, дождаться отрисовки рельефа, закрыть → открыть «МАРШРУТ ОРЛАНА» с ПУ в другой части карты. Наблюдаемо: панель «ПРОФИЛЬ РЕЛЬЕФА» мгновенно (без сетевого запроса) показывает рельеф, идентичный шахедовскому.
ТЕСТИРУЕМОСТЬ: pure-java
НАБРОСОК ТЕСТА:
```java
SurfaceHeightCache.updateSegmentHeight(101, 200, new int[]{200,200,200}); // Shahed li=1 wp=1
assertNull(SurfaceHeightCache.getSegmentProfile(101)); // должно быть изолировано по системе
```
КРИТЕРИЙ ПРОВАЛА: тест падает (данные видны обоим экранам).

---

### F-16: `ClientRocketPacketHandler.clearAll()` не вызывается НИОТКУДА + `IskanderPreparingSoundClient.tick()` пуст при `looping = true` → вечный звук
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/network/ClientRocketPacketHandler.java:74-81`;
`client/sound/IskanderPreparingSoundClient.java:30, 70-76`
КОД:
```java
    public static void clearAll() {          // <-- ни одного вызова во всём проекте
        for (IskanderPreparingSoundClient sound : activeSounds.values()) {
            if (!sound.isStopped()) {
                sound.stopSound();
            }
        }
        activeSounds.clear();
    }
```
```java
        this.looping = true;
...
    @Override
    public void tick() {
        if (this.stopped) {
            return;
        }

    }                                        // <-- пустое тело: САМ никогда не остановится
```
ПОЧЕМУ ЭТО БАГ: `IskanderPreparingSoundClient` — зацикленный `AbstractTickableSoundInstance`, чей `tick()` не содержит **ни одного** условия остановки: ни проверки расстояния, ни проверки существования блока, ни таймаута (сравните с `OrlanEngineSoundClient.tick():55-79`, где есть `MAX_NOT_FOUND`, и с `ShahedEngineSoundClient`, где есть `MAX_LIFETIME_TICKS`). Остановка возможна **только** внешним вызовом `stopSound()` из `handleLaunchState(..., launching=false)` или `stopPreparingSound(pos)`. Если этот пакет не придёт — блок сломали, чанк выгрузился, игрок отошёл, сервер упал, игрок разорвал соединение — звук играет **бесконечно**, переживая выход в главное меню и вход в другой мир (`ClientGameEvents.onClientDisconnect:36-41` вызывает только `network.ClientPacketHandler.clearAll()`, а не `ClientRocketPacketHandler.clearAll()`). Кроме того, `activeSounds` (`Map<BlockPos, ...>`) накапливает записи с координатами прошлого мира.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: установить ракету Искандер, начать подготовку к пуску (звук пошёл) → Esc → «Выйти в меню» → зайти в другой мир. Наблюдаемо: гул подготовки продолжает играть в новом мире (и в главном меню) до перезапуска клиента.
ТЕСТИРУЕМОСТЬ: manual
НАБРОСОК ТЕСТА: старт подготовки → `Ctrl` + выход в меню. КРИТЕРИЙ ПРОВАЛА: звук слышен в главном меню.

---

### F-17: `ClientIskanderPacketHandler.clearAll()` тоже не вызывается; `launchSoundPlayed` растёт вечно → пропажа звука пуска
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/network/ClientIskanderPacketHandler.java:31, 58-68, 145-153`
КОД:
```java
    private static final Set<Integer> launchSoundPlayed = new HashSet<>();
...
        if (flightPhase >= 1) {
            if (!launchSoundPlayed.contains(entityId)) {
                launchSoundPlayed.add(entityId);
                ...
            }
        }
...
    public static void clearAll() {           // <-- ни одного вызова во всём проекте
        launchSoundPlayed.clear();
```
ПОЧЕМУ ЭТО БАГ: `launchSoundPlayed` — множество **сетевых entity-id**, которые уникальны только в пределах одной сессии мира и переиспользуются после реконнекта/смены мира. Из него удаляют только в `handleIskanderRemove` (строка 72), т.е. если пакет удаления потерян/не пришёл (выгрузка чанка, дисконнект), id остаётся навсегда. При следующем входе в мир Искандер, которому сервер выдал тот же id, **никогда не получит звук пуска** — тихий запуск ракеты. `clearAll()`, который это чинит, не вызывается ни из `ClientGameEvents.onClientDisconnect`, ни откуда-либо ещё.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: пустить Искандер, выйти из мира до попадания ракеты (пакет `IskanderRemove` не придёт) → зайти обратно → пустить ещё один Искандер. Наблюдаемо: с некоторой вероятностью (совпадение id) звук пуска отсутствует, хотя ракета летит.
ТЕСТИРУЕМОСТЬ: pure-java
НАБРОСОК ТЕСТА:
```java
ClientIskanderPacketHandler.handleIskanderSpawn(42, pos, tgt, 1, 0, lp, s, e, f);
// имитация смены мира — clearAll() не вызывается ниоткуда
ClientIskanderPacketHandler.handleIskanderSpawn(42, pos2, tgt2, 1, 0, lp2, s2, e2, f2);
```
КРИТЕРИЙ ПРОВАЛА: второй вызов не создаёт `IskanderLaunchSoundClient`.

---

### F-18: `SirenSoundInstance.activeSounds` — static `Map<BlockPos, …>`, не чистится при выходе из мира
СЕРЬЁЗНОСТЬ: low
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/client/sound/SirenSoundInstance.java:18, 71-73`
КОД:
```java
    private static final Map<BlockPos, SirenSoundInstance> activeSounds = new HashMap<>();
...
        SirenSoundInstance sound = new SirenSoundInstance(pos);
        activeSounds.put(pos, sound);
        mc.getSoundManager().play(sound);
```
ПОЧЕМУ ЭТО БАГ: запись удаляется только в `stopSiren(pos)` или в `tick()` при `volume <= 0.001f`. Поскольку `this.looping = false` (строка 35), после окончания одноразового звука `SoundEngine` перестаёт тикать инстанс, `isStopped()` навсегда возвращает `false`, и запись `BlockPos → SirenSoundInstance` **остаётся в статической карте до конца процесса**. `ClientGameEvents.onClientDisconnect` эту карту не трогает. Карта накапливает по записи на каждую уникальную позицию сирены во всех посещённых мирах.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: сработать 200 сирен в разных точках, выйти в меню, зайти в другой мир, снова 200 сирен. Наблюдаемо: `activeSounds.size() == 400` (heap dump), координаты из обоих миров.
ТЕСТИРУЕМОСТЬ: manual
НАБРОСОК ТЕСТА: heap dump после смены мира, найти `SirenSoundInstance$activeSounds`. КРИТЕРИЙ ПРОВАЛА: size > 0 после `LoggingOut`.

---

## РАЗДЕЛ E. ПРОИЗВОДИТЕЛЬНОСТЬ РЕНДЕРА

### F-19: `OrlanCameraOverlay` выполняет рейкаст на 500 блоков КАЖДЫЙ КАДР
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/client/OrlanCameraOverlay.java:161, 365-375`
КОД:
```java
        BlockHitResult aimResult = raycastFromCamera(mc, orlan);
```
```java
    private static BlockHitResult raycastFromCamera(Minecraft mc, OrlanEntity orlan) {
        if (mc.level == null) return null;
        ...
        Vec3 endPos = eyePos.add(lookDir.scale(500.0));
        BlockHitResult result = mc.level.clip(new ClipContext(
                eyePos, endPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, orlan));
        return result;
    }
```
ПОЧЕМУ ЭТО БАГ: `render()` вызывается из `ClientGameEvents.onPreGuiLayer` (слой `CROSSHAIR`) — то есть **раз в кадр**, ~60–240 раз в секунду. `Level.clip` с `ClipContext.Block.OUTLINE` — это DDA-обход до **500 блоков** с вызовом `getBlockState(...)` + построением/выборкой `VoxelShape` (`getShape`) в каждом воксельном шаге. Для дрона на высоте 200 м, смотрящего под 20°, длина трассы близка к максимальной. Плюс на каждом кадре аллоцируются `new ClipContext`, два `Vec3` и результат. Дополнительно в том же `render()` — 15+ вызовов `String.format` (строки 111, 129-131, 144, 148, 157, 165, 168, 170, 184, 212, 236, 244, 249, 352-359), каждый из которых компилирует формат и создаёт `Formatter` + `StringBuilder`.
Это накладывается на самый нагруженный режим мода (FPV с перестроенной секционной сеткой из-за миксинов F-5/F-6).
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: FPV, дрон на высоте 300 м над открытой местностью, смотреть к горизонту (питч ≈ 20°). Наблюдаемо: F3 показывает падение FPS относительно того же вида без FPV; профайлер (`/debug start`) показывает `gui` → `clip` как заметную долю кадра.
ТЕСТИРУЕМОСТЬ: manual
НАБРОСОК ТЕСТА: `/debug start` на 10 с в FPV, затем `/debug stop`; открыть отчёт. КРИТЕРИЙ ПРОВАЛА: `Level.clip` присутствует в стеке GUI-рендера с долей > 2 %.

---

### F-20: `RouteTerrainRenderer` — 8.4 млн попиксельных операций `NativeImage` на рендер-потоке (текстура 2048×2048 = 16.8 МБ)
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/client/radar/RouteTerrainRenderer.java:94-116, 191-199`; константа — `radar/ServerRouteMapScanner.java:36` (`CACHE_SIZE = 2048`)
КОД:
```java
        displayTexture = new DynamicTexture(TEX_SIZE, TEX_SIZE, false);
        NativeImage d = displayTexture.getPixels();
        if (d != null)
            for (int y = 0; y < TEX_SIZE; y++)
                for (int x = 0; x < TEX_SIZE; x++)
                    d.setPixelRGBA(x, y, DARK);
        displayTexture.upload();
```
```java
            NativeImage d = displayTexture.getPixels();
            if (d != null) {
                for (int y = 0; y < TEX_SIZE; y++) {
                    for (int x = 0; x < TEX_SIZE; x++) {
                        d.setPixelRGBA(x, y, cacheImage.getPixelRGBA(x, y));
                    }
                }
                displayTexture.upload();
            }
```
ПОЧЕМУ ЭТО БАГ: `TEX_SIZE = 2048`. Конструктор выполняет **4 194 304** вызовов `setPixelRGBA` (каждый — `checkAllocated()` + `MemoryUtil.memPutInt`), а `loadFromPngBytes` — ещё 4 194 304 пар `getPixelRGBA`/`setPixelRGBA`, вместо однократного `NativeImage.copyFrom(...)`/`memCopy`. Обе процедуры выполняются на главном (рендер) потоке: конструктор — из `ShahedRouteScreen.init():197` и `OrlanRouteScreen.init():184`, копирование — из обработчика пакета `RouteMapDataPacket` через `enqueueWork` (тоже главный поток). Итого фриз при каждом открытии экрана маршрута и второй фриз при приходе карты. Память: `DynamicTexture` 2048²×4 = 16.8 МБ нативно + 16.8 МБ VRAM, `cacheImage` — ещё 16.8 МБ, плюс статический `mapCache` до 8 полных PNG (`MAX_CACHED_MAPS = 8`, строка 42).
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: открыть «МАРШРУТ ШАХЕДА» → закрыть → открыть. Наблюдаемо: заметный однократный фриз (~50–150 мс) при каждом открытии; в момент прихода карты — второй фриз.
ТЕСТИРУЕМОСТЬ: manual
НАБРОСОК ТЕСТА: замер `System.nanoTime()` вокруг `new RouteTerrainRenderer(...)`. КРИТЕРИЙ ПРОВАЛА: > 16 мс (один кадр при 60 FPS).

---

### F-21: `drawLine` — Bresenham без клампинга концов, при неограниченном панорамировании счёт итераций не ограничен
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/client/gui/ShahedRouteScreen.java:1203-1230, 588-591`
(идентичный код: `client/gui/OrlanRouteScreen.java:981-1000, 428-429`)
КОД:
```java
    private void drawLine(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        ...
        int maxSteps = dx + dy + 1;
        while (steps < maxSteps) {

            if (x1 >= clipMinX && x1 < clipMaxX && y1 >= clipMinY && y1 < clipMaxY) {
                g.fill(x1, y1, x1 + 1, y1 + 1, color);
            }
```
```java
        if (button == 2 && isDragging) {
            viewCenterX -= dragX * scale;
            viewCenterZ -= dragY * scale;
```
ПОЧЕМУ ЭТО БАГ: отсечение (`clipMinX..clipMaxX`) применяется **внутри** цикла, а не к концам отрезка **до** цикла. Экранные координаты берутся из `worldToMapX/Z` = `mapCenter + (world - viewCenter) / scale`, а `viewCenterX/Z` ничем не ограничены: `mouseDragged` при `scale = MAX_SCALE = 80` за один протяг на 300 px сдвигает центр на 24 000 блоков, и таких протягов можно сделать сколько угодно. После этого достаточно прокрутить до `MIN_SCALE = 2.0`, и точка маршрута окажется на экранной координате в десятки тысяч пикселей. Каждый отрезок тогда исполняет `dx + dy` итераций впустую. Отрезков: до 20 точек × до 8 ПУ (`MULTI_COLORS.length`) + дуга зоны пуска (`renderLaunchZone`, 8 сегментов + 2 луча) = ~170 вызовов **на кадр**. При смещении 20 000 px это ~3.4 млн холостых итераций за кадр — и цифра растёт линейно с числом протягов.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: экран «МАРШРУТ ШАХЕДА» с 8 ПУ → зум до максимума (скролл вниз) → 10 раз протянуть карту СКМ в одну сторону → зум обратно в максимум приближения. Наблюдаемо: FPS падает до однозначных значений, интерфейс перестаёт реагировать.
ТЕСТИРУЕМОСТЬ: manual
НАБРОСОК ТЕСТА: описанная последовательность с F3. КРИТЕРИЙ ПРОВАЛА: FPS < 20 при полностью статичном экране GUI.

---

### F-22: `ShahedTerminalScreen.render()` парсит EditBox и разбирает строки зон каждый кадр; при пустом поле бросает `NumberFormatException` 60 раз/с
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/client/gui/ShahedTerminalScreen.java:251-261, 205-218, 150-163`
КОД:
```java
        try {
            int targetX = Integer.parseInt(this.xCoordInput.getValue());
            int targetZ = Integer.parseInt(this.zCoordInput.getValue());
            if (this.isInForbiddenZone(targetX, targetZ)) {
```
```java
    private boolean zoneContains(String zoneStr, int x, int z) {
        try {
            String[] parts = zoneStr.split(",");
            if (parts.length == 4) {
                int x1 = Math.min(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[2].trim()));
```
```java
    private boolean isValidNumber(String input) {
        if (input.isEmpty()) {
            return true;
        }
        if (input.equals("-")) {
            return true;
        }
```
ПОЧЕМУ ЭТО БАГ:
1. Фильтр `isValidNumber` **явно разрешает** пустую строку и `"-"`. Значит при пустом поле координат `render()` каждый кадр выполняет `Integer.parseInt("")` → создаётся `NumberFormatException` с полным заполнением стек-трейса (`fillInStackTrace` — самая дорогая часть исключения) **60–240 раз в секунду**, и результат молча выбрасывается (`catch (NumberFormatException ignored)`).
2. Когда поля заполнены — на каждый кадр для **каждой** зоны из `blacklistZones`/`whitelistZones` выполняется `String.split(",")` (компиляция/обход регулярного выражения + аллокация `String[]` и четырёх подстрок) и **восемь** `Integer.parseInt` + четыре `trim()`. При 20 настроенных зонах это 20 split + 160 parseInt на кадр.
3. Плюс `Component.translatable(...).getString()` в строках 235, 239, 241, 244, 255 — по `MutableComponent` + разрешение перевода на кадр.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: открыть терминал Шахеда, очистить поле «X» (Ctrl+A, Delete) и оставить экран открытым. Наблюдаемо: профайлер аллокаций (`-XX:+PrintGCDetails` / JFR) показывает поток `java.lang.NumberFormatException`; при 20 запретных зонах и заполненных полях — измеримое падение FPS.
ТЕСТИРУЕМОСТЬ: manual
НАБРОСОК ТЕСТА: JFR-запись 10 с с открытым терминалом и пустым полем X, фильтр по `jdk.JavaExceptionThrow`. КРИТЕРИЙ ПРОВАЛА: > 100 событий `NumberFormatException`.

---

### F-23: `ShahedRadarScreen.render()` каждый кадр аллоцирует и сортирует список целей + гоняет Stream
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/client/gui/ShahedRadarScreen.java:200, 461, 472-485, 507`
КОД:
```java
        targets.values().forEach(t -> t.interpolate(0.25f));      // строка 200, каждый кадр
...
        long activeCount = targets.values().stream().filter(t -> t.staleTicks < RadarTarget.STALE_REMOVE).count();
...
    private List<RadarTarget> getSortedTargets() {
        List<RadarTarget> sorted = new ArrayList<>();
        for (RadarTarget t : targets.values()) {
            if (t.position != null) sorted.add(t);
        }
        sorted.sort((a, b) -> { ... });
        return sorted;
    }
...
        List<RadarTarget> sorted = getSortedTargets();            // строка 507, каждый кадр
```
ПОЧЕМУ ЭТО БАГ: `getSortedTargets()` вызывается из `renderRightPanel`, который вызывается из `render()` — то есть **новый `ArrayList` + полная сортировка O(n log n) с боксингом `Double.compare` каждый кадр**, хотя данные меняются раз в 20 тиков (`UPDATE_INTERVAL = 20`, строка 27). Плюс `stream().filter().count()` (строка 461) — цепочка объектов Stream на кадр. Плюс `interpolate` со `scale`/`subtract`/`add`, каждый из которых создаёт новый `Vec3` (3 аллокации × количество целей × FPS). Плюс `getRadarScreenPos()` возвращает `new int[]{...}` и вызывается по три раза за кадр (строки 301, 320, 379). При налёте роя Шахедов (радиус обнаружения 1500 блоков, десятки целей) это сотни аллокаций на кадр в экране, который явно не ставит игру на паузу (`isPauseScreen() == false`, строка 729).
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: `/summon` 60 Шахедов в радиусе радара, открыть радар. Наблюдаемо: заметная нагрузка на GC (частые молодые сборки), рывки интерфейса.
ТЕСТИРУЕМОСТЬ: manual
НАБРОСОК ТЕСТА: JFR `jdk.ObjectAllocationSample` за 10 с при 60 целях. КРИТЕРИЙ ПРОВАЛА: `ShahedRadarScreen.getSortedTargets` в топ-5 источников аллокаций.

---

### F-24: `init()` экранов маршрута повторно шлёт пакет на каждую точку при КАЖДОМ ресайзе окна; `tick()` опрашивает карту вечно
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/client/gui/ShahedRouteScreen.java:303-310, 841-854` (то же в `OrlanRouteScreen.java:256-263, 620-634`)
КОД:
```java
        int savedIdx = currentEditIndex;
        for (int ri = 0; ri < allRoutes.size(); ri++) {
            currentEditIndex = ri;
            for (int wi = 0; wi < allRoutes.get(ri).size(); wi++) {
                requestSegmentHeightForWaypoint(wi);
            }
        }
        currentEditIndex = savedIdx;
```
```java
        if (terrainRenderer != null && !terrainRenderer.hasCacheData()) {
            mapRequestTicks++;
            if (mapRequestTicks >= MAP_REQUEST_INTERVAL) {
                mapRequestTicks = 0;
                NetworkHandler.sendToServer(new RequestRouteMapPacket(launcherPositions.get(0)));
            }
        }
```
ПОЧЕМУ ЭТО БАГ:
1. `Screen.init()` вызывается заново при **любом** изменении размера окна и при изменении GUI-масштаба (`Screen.resize` → `init(minecraft, w, h)` → `clearWidgets()` + `init()`). Цикл в строках 303-310 при этом заново отправляет `RequestSegmentMaxHeightPacket` для **каждой** точки **каждого** маршрута: до 8 ПУ × 20 точек = **160 пакетов на один ресайз окна**. Растягивание окна мышью генерирует событие resize на каждый пиксель → тысячи пакетов, отправленных на сервер за секунду, каждый из которых на сервере запускает сканирование рельефа сегмента.
2. `tick()` при неполученной карте шлёт `RequestRouteMapPacket` каждые 10 тиков **бессрочно**. `RouteTerrainRenderer.handleServerData` (строки 151-167) собирает PNG по чанкам, но если хоть один чанк потерян или `chunkIndex == 0` пришёл дважды, `receivedChunks` никогда не достигнет `expectedChunks`, `pngAssembly` остаётся ненулевым и растёт, `cacheReady` остаётся `false` — опрос продолжается вечно, а сервер каждые полсекунды заново рассылает многомегабайтную карту.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: открыть «МАРШРУТЫ ШАХЕДОВ» с 4 ПУ и заполненными маршрутами → потянуть за угол окна и растягивать его 3 секунды. Наблюдаемо: TPS сервера проседает, в сетевом профайлере — сотни `ata:request_segment_max_height`.
ТЕСТИРУЕМОСТЬ: manual
НАБРОСОК ТЕСТА: счётчик отправленных `RequestSegmentMaxHeightPacket` за один resize. КРИТЕРИЙ ПРОВАЛА: > 0 пакетов на событие изменения размера.

---

### F-25: `ClientWaypointManager.load()` — синхронный дисковый ввод-вывод прямо в `Screen.init()`
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/client/gui/ShahedRouteScreen.java:180-182`;
`client/gui/TargetSettingsScreen.java:66-67`; `client/ClientWaypointManager.java:49-113`
КОД:
```java
        com.synarsis.airtacticalarsenal.client.ClientWaypointManager.load();
        cachedSavedRoutes = com.synarsis.airtacticalarsenal.client.ClientWaypointManager
                .getRoutes(...SystemType.SHAHED);
```
```java
        try (FileInputStream fis = new FileInputStream(file)) {
            Properties props = new Properties();
            props.load(fis);
            for (String key : props.stringPropertyNames()) {
                ...
                        String[] parts = key.split("\\.");
```
ПОЧЕМУ ЭТО БАГ: `load()` открывает файл, парсит `Properties`, разбивает каждый ключ регулярным выражением и разбирает координаты. Выполняется на главном потоке в `init()` — то есть при каждом открытии экрана **и при каждом ресайзе окна** (см. F-24). На HDD/сетевом диске это десятки миллисекунд фриза. Кроме того, `saveToFile()` (строки 129-154) вызывается синхронно из обработчика кнопки — полная перезапись файла на каждое сохранение/удаление маршрута. Ошибки логируются `e.printStackTrace()` (строки 111, 152) вместо логгера — уходят в stderr мимо `latest.log`.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: сохранить 200 маршрутов → открыть экран маршрута → потянуть окно за угол. Наблюдаемо: рывки на каждом кадре ресайза.
ТЕСТИРУЕМОСТЬ: pure-java
НАБРОСОК ТЕСТА: замерить `ClientWaypointManager.load()` на файле с 200 маршрутами. КРИТЕРИЙ ПРОВАЛА: > 16 мс.

---

## РАЗДЕЛ F. ПРОЧЕЕ

### F-26: `new Thread(...)` на каждый пакет взрыва — неограниченное создание потоков из сети
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/network/ClientPacketHandler.java:115-121, 139-157`;
идентично `network/ClientIskanderPacketHandler.java:110-128`
КОД:
```java
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {
                }
                Minecraft.getInstance().execute(() -> activeSounds.remove(entityId));
            }).start();
```
```java
        new Thread(() -> {
            try {
                Thread.sleep((long) delay * 50L);
                mc.execute(() -> { ... mc.level.playLocalSound(...); });
            } catch (InterruptedException ignored) {
            }
        }).start();
```
ПОЧЕМУ ЭТО БАГ: до **двух голых потоков на каждый пакет взрыва**, без пула, без имени, без `setDaemon(true)`. Частота задаётся сервером (рой Шахедов + Искандеры дают десятки взрывов в секунду), клиент не может отказать. Каждый поток — ~1 МБ зарезервированного стека и запись в таблице потоков ОС; при 50 взрывах/с и жизни потока 3 с одновременно живут ~300 потоков. Так как потоки **не демоны**, они удерживают JVM от завершения — выход из игры зависает до 3 секунд после последнего взрыва. Задача тривиально решается одним `ScheduledExecutorService` или счётчиком тиков. Дополнительно: `Minecraft.getInstance().execute(...)` захватывает `mc` из закрытия и выполняется даже после смены мира — проверка `if (mc.level == null) return;` спасает только частично (в новом мире `level != null`, и звук взрыва из старого мира проиграется по старым координатам).
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: запустить залп из 30 Шахедов по одной цели → в момент детонаций посмотреть на счётчик потоков (`jcmd <pid> Thread.print | grep -c "Thread-"`) → сразу выйти в главное меню. Наблюдаемо: счётчик анонимных потоков резко растёт; при выходе через 1 с после взрывов слышны звуки взрывов уже в новом мире/меню.
ТЕСТИРУЕМОСТЬ: manual
НАБРОСОК ТЕСТА: `jcmd <pid> Thread.print` во время залпа. КРИТЕРИЙ ПРОВАЛА: > 20 потоков с именем вида `Thread-N` в состоянии `TIMED_WAITING (sleeping)`.

---

## СВОДКА ПО ПРИОРИТЕТАМ

| # | Серьёзность | Кратко | Файл |
|---|---|---|---|
| F-1 | critical | Утечка GPU-FBO каждый кадр при неудачной загрузке шейдера | `NvgShaderHandler.java:56` |
| F-2 | high | `#version 120` фрагментный шейдер в core-профиле 1.21 | `night-vision.fsh:1` |
| F-3 | high | `loadEffect` посреди `renderLevel` + смена биндингов GL | `ClientGameEvents.java:104` |
| F-4 | low | «Свой шейдер» = подстрока `"ata"` | `NvgShaderHandler.java:24` |
| F-5 | high | 3× `@Redirect` + `required:true` → крэш на старте с Sodium/Embeddium | `LevelRendererMixin.java:26` |
| F-6 | medium | Клиентский центр чанков расходится с серверным → потерянные чанки | `ClientChunkCacheMixin.java:23` |
| F-7 | high | Поворот игрока клампится и не восстанавливается после FPV | `ClientGameEvents.java:63` |
| F-8 | high | `cameraActive=true` до проверки сущности → 5 с сломанного HUD | `OrlanCameraHandler.java:52` |
| F-9 | high | Повторный вход: два привязанных дрона + потерян `savedCameraType` | `OrlanCameraHandler.java:52` |
| F-10 | medium | `setCameraEntity(null)` без проверки (асимметрия с `forceStopCameraLocal`) | `OrlanCameraHandler.java:100` |
| F-11 | high | Хардкод `LAlt`/`R`/`N`, без `mc.screen == null` | `ClientGameEvents.java:91` |
| F-12 | medium | Отменяются ВСЕ слои HUD (чат, тосты, титры) | `ClientGameEvents.java:127` |
| F-13 | high | `TerrainMapRenderer.cachedPngBytes` живёт между мирами | `TerrainMapRenderer.java:768` |
| F-14 | high | `removed()` обнуляет `currentInstance` → радар без целей | `ShahedRadarScreen.java:145` |
| F-15 | high | Коллизия ключей `SurfaceHeightCache` Шахед↔Орлан | `SurfaceHeightCache.java:16` |
| F-16 | high | Вечный зацикленный звук подготовки Искандера | `IskanderPreparingSoundClient.java:70` |
| F-17 | medium | `launchSoundPlayed` не чистится → пропажа звука пуска | `ClientIskanderPacketHandler.java:31` |
| F-18 | low | `SirenSoundInstance.activeSounds` не чистится | `SirenSoundInstance.java:18` |
| F-19 | high | Рейкаст 500 блоков каждый кадр в FPV-оверлее | `OrlanCameraOverlay.java:161` |
| F-20 | high | 8.4 млн попиксельных операций 2048² на рендер-потоке | `RouteTerrainRenderer.java:98` |
| F-21 | medium | Неограниченный Bresenham при неограниченном панорамировании | `ShahedRouteScreen.java:1203` |
| F-22 | medium | `parseInt` + `split` в `render()`, NFE 60 раз/с | `ShahedTerminalScreen.java:251` |
| F-23 | medium | Аллокация + сортировка списка целей каждый кадр | `ShahedRadarScreen.java:507` |
| F-24 | medium | 160 пакетов на каждый ресайз окна; вечный опрос карты | `ShahedRouteScreen.java:303` |
| F-25 | medium | Дисковый I/O в `Screen.init()` | `ShahedRouteScreen.java:180` |
| F-26 | medium | Голые `new Thread()` на каждый пакет взрыва | `ClientPacketHandler.java:115` |

### Что проверено и НЕ является дефектом (чтобы не тратили время повторно)
* `getModelResource` в GeckoLib-моделях: все `ResourceLocation` вынесены в `static final` (`ShahedModel.java:13-16`, `OrlanModel.java:13-15`, `IskanderRocketBlockRenderer.java:44-46`) — аллокаций на кадр нет.
* `TargetSettingsScreen`: `setMaxLength(10)` + `setFilter(this::isValidNumber)` действительно отсекает `99999999999`, а `recalculateParameters()` ловит NFE — переполнения int нет. (Но три ветки на строках 247-259 все присваивают `errorMessage = ""` — пользователь получает неактивную кнопку «ПРИМЕНИТЬ» вообще без объяснения причины.)
* Все обработчики пакетов используют `context.enqueueWork(...)` — гонок с рендер-потоком при перестроении виджетов нет.
* `RouteTerrainRenderer.close()` / `TerrainMapRenderer.close()` действительно вызываются из `Screen.removed()`, а `RouteTerrainRenderer.mapCache` очищается на `LoggingOut` — эти два кэша утечки текстур не дают (в отличие от F-13).
* Сервер действительно вручную досылает чанки вокруг дрона (`OrlanCameraViewPacket.sendDroneChunks`, `OrlanEntity:509-516`) и снимает `noGravity`/`mayfly` при смерти игрока (`OrlanEntity:456-463`) и при удалении дрона (`OrlanEntity:1289-1304`) — эти пути закрыты.
* `OrlanCameraHandler.updateSmoothedPosition()` / `getSmoothX/Y/Z()` (строки 205-232) — мёртвый код, не вызывается ниоткуда: обещанное сглаживание позиции камеры не работает вовсе.
