# АДВЕРСАРИАЛЬНОЕ РЕВЬЮ — домен: конфиг / команды / рецепты / compat / регистрация / сборка

Проект: `D:\Mods_Projects\SOURCE_ANY\ATA_all\ATA_update`
MC 1.21.1 / NeoForge 21.1.230 (FML loader 4.0.42) / Java 21 / GeckoLib 4.7.4 / modid `ata`

Все утверждения проверены чтением исходников проекта и **декомпилированных исходников NeoForge 21.1.230 / FML 4.0.42 / Minecraft 1.21.1** из локального gradle-кэша (пути указаны в находках).

Итог: **28 подтверждённых дефектов** (3 critical, 10 high, 11 medium, 4 low).

---

## 1. КОНФИГ

### F-1: COMMON-конфиг не синхронизируется — клиентский GUI показывает баланс из СВОЕГО файла, сервер применяет свой
СЕРЬЁЗНОСТЬ: critical
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/AirTacticalArsenal.java:47`
КОД:
```java
modContainer.registerConfig(ModConfig.Type.COMMON, ShahedConfig.SPEC, "ata-common.toml");
```
Клиентские потребители (пакет `client.gui` — исполняется только на клиенте):
```java
// client/gui/UnifiedTerminalScreen.java:331-332
int minDist = ShahedConfig.getIskanderMinDistance();
int maxDist = ShahedConfig.getIskanderMaxRange();
if (dist < minDist) { this.errorMessage = ...; return; }
// client/gui/UnifiedTerminalScreen.java:414
double cruiseSpeed = ShahedConfig.getIskanderCruiseSpeed() * 20;
// client/gui/ShahedRouteScreen.java:1149
double cruiseSpeed = ShahedConfig.getShahedCruiseSpeed();
// client/gui/TargetSettingsScreen.java:244-245, 331-332
int minDist = ShahedConfig.getIskanderMinDistance();
int maxDist = ShahedConfig.getIskanderMaxRange();
```
ПОЧЕМУ ЭТО БАГ: NeoForge синхронизирует **только** `ModConfig.Type.SERVER`. Доказательство —
`net/neoforged/neoforge/network/ConfigSync.java:26` (из `neoforge-21.1.230-sources.jar`):
```java
final Map<String, byte[]> configData = ModConfigs.getConfigSet(ModConfig.Type.SERVER).stream()
        .collect(Collectors.toMap(ModConfig::getFileName, mc -> { ... }));
```
COMMON-файл `config/ata-common.toml` читается клиентом из **своей** папки `.minecraft/config`. При этом
сам мод в другом месте делает правильно — `block/UnifiedTerminalBlock.java:110-116` упаковывает
`TARGET_Y_LEVEL`, `FORBIDDEN_ZONES`, `WHITELIST_ZONES` в пакет открытия GUI, то есть автор знал про
проблему, но дальность/скорость оставил на прямом чтении конфига.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: сервер: `iskanderMaxRange=5000`. Клиент (свой файл, дефолт не трогали или
поставили 10000): GUI Unified Terminal показывает «Дальность: 1000 - 10000 блоков» и разрешает нажать
«Пуск» на 8000 блоков. `LaunchRocketPacket.handle` (`network/LaunchRocketPacket.java:62,68`) считает
`maxDist=5000` → игрок получает `message.ata.rocket.too_far` и ракета не летит. Обратный случай хуже:
клиент с меньшим `maxRange` блокирует кнопку для целей, которые сервер разрешил бы.
ТЕСТИРУЕМОСТЬ: manual (мультиплеер) / pure-java (для факта отсутствия синка)
НАБРОСОК ТЕСТА:
```java
// pure-java: доказать, что COMMON не входит в набор синка
assertTrue(ModConfigs.getConfigSet(ModConfig.Type.SERVER).stream()
        .noneMatch(mc -> mc.getFileName().equals("ata-common.toml")));
// manual: dedicated server iskanderMaxRange=5000, client-side ata-common.toml iskanderMaxRange=10000,
//         цель на 8000 → GUI зелёный, сервер отклоняет.
```

---

### F-2: `ShahedConfig.reload()` — пустое тело; `/ata config reload` рапортует об успехе, не делая ничего
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/config/ShahedConfig.java:150-153`
КОД:
```java
public static void reload() {
    // NeoForge auto-reloads the config when the file changes on disk.
    // TODO: verify /shahed config command flow works without explicit reload
}
```
Команда (`command/ShahedCommand.java:408-415`):
```java
.then(Commands.literal("reload")
        .executes(context -> {
            ShahedConfig.reload();
            context.getSource()
                    .sendSuccess(() -> Component.translatable("command.ata.config.reload")
                            .withStyle(s -> s.withColor(0x55FF55)), true);
            return 1;
        })))
```
ПОЧЕМУ ЭТО БАГ: команда возвращает `1` и зелёный success-broadcast всем операторам, но метод — no-op.
Хуже: `ModConfigSpec.ConfigValue.get()` **кэширует** значение (`neoforge-21.1.230-sources.jar`,
`ModConfigSpec.java:1219-1225`):
```java
public T get() {
    if (cachedValue == null) { cachedValue = getRaw(); }
    return cachedValue;
}
```
Сброс кэша делает только `clearCache()` (`ModConfigSpec.java:1281`), который здесь никто не вызывает.
Комментарий про «NeoForge auto-reloads» верен лишь для события файлового watcher'а — команда его не
инициирует.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: админ правит `ata-common.toml` в текстовом редакторе, который пишет через
temp-file+rename (watcher может не сработать), затем выполняет `/ata config reload`. Чат: зелёное
«конфиг перезагружен». Значения в игре — старые.
ТЕСТИРУЕМОСТЬ: pure-java
НАБРОСОК ТЕСТА:
```java
// reload() не трогает ни один ConfigValue
for (var f : ShahedConfig.class.getDeclaredFields())
    if (ModConfigSpec.ConfigValue.class.isAssignableFrom(f.getType())) {
        // после ShahedConfig.reload() cachedValue должен быть null — он не null
        var cv = (ModConfigSpec.ConfigValue<?>) f.get(null);
        var cached = ModConfigSpec.ConfigValue.class.getDeclaredField("cachedValue");
        cached.setAccessible(true);
        cv.get(); ShahedConfig.reload();
        assertNull(cached.get(cv)); // ПАДАЕТ
    }
```

---

### F-3: все `/ata config ... set` пишут только в память — без `save()`, значения теряются при рестарте
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/config/ShahedConfig.java:94-148`, `command/ShahedCommand.java:169,180,191,202,225,234,245,263,272,293,302,320,329,361,371,390,400`
КОД:
```java
// ShahedConfig.java:94-106
public static void setExplosionParticlesEnabled(boolean enabled) { EXPLOSION_PARTICLES_ENABLED.set(enabled); }
public static void setParticleMultiplier(int multiplier) { EXPLOSION_PARTICLE_MULTIPLIER.set(Math.max(0, Math.min(500, multiplier))); }
public static void setExplosionsBreakBlocks(boolean breaks) { EXPLOSION_BREAKS_BLOCKS.set(breaks); }
public static void setCustomExplosionSoundsEnabled(boolean enabled) { CUSTOM_EXPLOSION_SOUNDS.set(enabled); }
public static void setLoggingEnabled(boolean enabled) { LOGGING_ENABLED.set(enabled); }
// ShahedCommand.java:169
ShahedConfig.SPAWN_X.set(value);
```
ПОЧЕМУ ЭТО БАГ: javadoc `ModConfigSpec.ConfigValue.set` (`neoforge-21.1.230-sources.jar`,
`ModConfigSpec.java:1260-1273`) прямым текстом:
```java
/**
 * Directly sets the value, without firing events or writing the config to disk.
 * Make sure to call {@link ModConfigSpec#save()} eventually.
 */
public void set(T value) {
    ...
    loadedConfig.config().set(path, value);
    if (getSpec().restartType == RestartType.NONE) { this.cachedValue = value; }
}
```
`ModConfigSpec.save()` не вызывается **нигде** в проекте (grep по `save()` в `config/` и `command/` — 0 попаданий).
Дополнительно: изменение in-memory конфига делает содержимое `ata-common.toml` рассинхронизированным
с рантаймом; любое срабатывание file-watcher'а (тач файла, редактирование другого ключа) перечитает
файл с диска и **откатит** все изменения, сделанные командами.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: `/ata config spawnX 5000` → success. `/ata config` показывает 5000.
Перезапуск сервера → `/ata config` показывает 0 (дефолт из файла). Все настройки админа потеряны.
ТЕСТИРУЕМОСТЬ: manual / build-check
НАБРОСОК ТЕСТА:
```bash
# grep-проверка: ни одного вызова save() рядом с set()
rg -n "\.set\(" src/main/java/com/synarsis/airtacticalarsenal/config src/main/java/com/synarsis/airtacticalarsenal/command | wc -l   # 17+
rg -n "SPEC\.save\(\)|\.save\(\)" src/main/java/com/synarsis/airtacticalarsenal | wc -l                                            # 0
```

---

### F-4: `defineListAllowEmpty` c валидатором `obj instanceof String` + `ForbiddenZone.parse` → null → зона МОЛЧА игнорируется
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/config/ShahedConfig.java:165-176, 60-91, 259-273`
КОД:
```java
// 165-168
FORBIDDEN_ZONES = BUILDER.defineListAllowEmpty("forbiddenZones",
        Arrays.asList("160,771,-622,1011", "170,-1020,-420,-785", "-750,-600,-1145,-1015"),
        () -> "0,0,0,0",
        obj -> obj instanceof String);
// 77-88
if (BLACKLIST_ENABLED.get()) {
    for (String zoneStr : FORBIDDEN_ZONES.get()) {
        ForbiddenZone zone = ForbiddenZone.parse(zoneStr);
        if (zone != null && zone.contains(x, z)) { inBlacklist = true; break; }
    }
    if (inBlacklist) { return true; }
}
// 259-273
public static ForbiddenZone parse(String str) {
    try {
        String[] parts = str.split(",");
        if (parts.length == 4) { return new ForbiddenZone(...); }
    } catch (NumberFormatException ignored) { }
    return null;
}
```
ПОЧЕМУ ЭТО БАГ: цепочка молчаливой деградации из трёх звеньев.
1. Валидатор `obj instanceof String` пропускает **любую** строку, включая `"0,0,100"`, `"abc"`, `""`,
   `"0, 0, 100, 100, 5"` — конфиг загружается без единого предупреждения.
2. `parse` возвращает `null` для `parts.length != 4` вообще без исключения (`NumberFormatException`
   ловится, но `ArrayIndexOutOfBounds`/неверная длина даже не доходят до catch — просто `return null`).
3. В `isInForbiddenZone` `zone != null &&` тихо отбрасывает битую зону. Логирования нет.
Асимметрия последствий: **blacklist fail-open** (битая запретная зона → атака разрешена, админ уверен,
что зона работает), **whitelist fail-closed** (все записи битые → `inWhitelist=false` → строка 73
`return true` → запрещено ВСЁ, включая легитимные цели).
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: админ пишет в toml `forbiddenZones = ["0,0,100,100", "спавн: 200,200,300,300"]`,
включает `/ata config zone blacklist enable`. Запуск в 250,250 проходит — вторая зона молча выброшена.
ТЕСТИРУЕМОСТЬ: pure-java
НАБРОСОК ТЕСТА:
```java
@Test void malformedZoneIsSilentlyDropped() {
    assertNull(ShahedConfig.ForbiddenZone.parse("200,200,300"));      // 3 части
    assertNull(ShahedConfig.ForbiddenZone.parse("abc,1,2,3"));        // NFE проглочен
    assertNull(ShahedConfig.ForbiddenZone.parse("0,0,100,100,5"));    // 5 частей
    assertNull(ShahedConfig.ForbiddenZone.parse(""));
    // и ни один из них не приводит к ошибке загрузки конфига
}
```

---

### F-5: три источника истины для высоты полёта Искандера; два из них — мёртвые
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `config/ShahedConfig.java:209,218`, `entity/IskanderEntity.java:79,293`
КОД:
```java
// ShahedConfig.java:209
ISKANDER_APEX_HEIGHT = BUILDER.comment("Maximum flight height in blocks")
        .defineInRange("iskanderApexHeight", 800.0, 100.0, 1500.0);
// ShahedConfig.java:218
ISKANDER_MAX_FLIGHT_HEIGHT = BUILDER.comment("Maximum flight height in blocks absolute Y coordinate (e.g. 600 means missile apex will not exceed Y=600)")
        .defineInRange("iskanderMaxFlightHeight", 600, 100, 1500);
// IskanderEntity.java:79
private static final double MAX_FLIGHT_HEIGHT = 800.0;
// IskanderEntity.java:293  <-- ЕДИНСТВЕННОЕ реальное применение
double maxAbsoluteHeight = ShahedConfig.getIskanderMaxFlightHeight();
```
ПОЧЕМУ ЭТО БАГ: реально применяется только `iskanderMaxFlightHeight` (дефолт 600).
`ISKANDER_APEX_HEIGHT` / `getIskanderApexHeight()` не используются **нигде** — проверено grep по всему
`src/main/java`: 0 попаданий вне `ShahedConfig.java`. Константа `MAX_FLIGHT_HEIGHT = 800.0` в
`IskanderEntity` не используется ни разу (единственное упоминание — строка объявления 79).
Оба комментария в конфиге начинаются одинаково — «Maximum flight height in blocks», — так что админ,
выставивший `iskanderApexHeight = 1200`, получит ноль эффекта и никакой диагностики.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: `iskanderApexHeight = 1500`, `iskanderMaxFlightHeight = 600`. Ракета всё
равно упирается в Y=600 (`IskanderEntity.java:304 this.diveStartY = Math.min(this.diveStartY, maxAbsoluteHeight);`).
ТЕСТИРУЕМОСТЬ: build-check
НАБРОСОК ТЕСТА:
```bash
rg -n "getIskanderApexHeight|ISKANDER_APEX_HEIGHT" src/main/java --glob '!**/ShahedConfig.java'   # пусто
rg -n "MAX_FLIGHT_HEIGHT" src/main/java/com/synarsis/airtacticalarsenal/entity/IskanderEntity.java # только :79
```

---

### F-6: шесть опций конфига полностью мертвы (задокументированы, доступны, ни на что не влияют)
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/config/ShahedConfig.java:191,192,202,209,220,221`
КОД:
```java
SHAHED_MIN_DISTANCE = BUILDER.comment("Minimum launch distance in blocks").defineInRange("shahedMinDistance", 600, 50, 2000);
SHAHED_MAX_RANGE    = BUILDER.comment("Maximum launch distance in blocks").defineInRange("shahedMaxRange", 12000, 1000, 30000);
SHAHED_DISTANCE_FALLOFF = BUILDER.comment("Enable damage falloff based on distance (realistic)").define("shahedDistanceFalloff", true);
ISKANDER_APEX_HEIGHT = BUILDER.comment("Maximum flight height in blocks").defineInRange("iskanderApexHeight", 800.0, 100.0, 1500.0);
ISKANDER_APEX_PROGRESS = BUILDER.comment("Position of trajectory apex (0.3-0.6, where 0.5 = middle of flight)").defineInRange("iskanderApexProgress", 0.4, 0.2, 0.7);
ISKANDER_HORIZONTAL_TRANSITION_SPEED = BUILDER.comment("Speed of horizontal transition smoothing (0.05-0.3)").defineInRange("iskanderHorizontalTransitionSpeed", 0.1, 0.02, 0.5);
```
ПОЧЕМУ ЭТО БАГ: ни поля, ни геттеры (`getShahedMinDistance`, `getShahedMaxRange`,
`isShahedDistanceFalloffEnabled`, `getIskanderApexHeight`, `getIskanderApexProgress`,
`getIskanderHorizontalTransitionSpeed`) не вызываются нигде за пределами самого `ShahedConfig.java`.
Особо тяжёлый случай — `shahedMinDistance`/`shahedMaxRange`: **проверки дальности запуска Шахеда не
существует вообще**. `LaunchShahedFromTerminalPacket.handle` (строки 44-78) валидирует только
запретную зону и наличие жидкости; ограничение дальности Шахеда, объявленное в конфиге, не
реализовано ни на клиенте, ни на сервере.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: `shahedMaxRange = 1000`. Запуск с терминала на цель в 25 000 блоков —
проходит без ошибки. Плюс F-7 (форс-лоад 25 000 блоков траектории).
ТЕСТИРУЕМОСТЬ: build-check
НАБРОСОК ТЕСТА:
```bash
for m in getShahedMinDistance getShahedMaxRange isShahedDistanceFalloffEnabled \
         getIskanderApexHeight getIskanderApexProgress getIskanderHorizontalTransitionSpeed; do
  echo -n "$m "; rg -c "$m" src/main/java --glob '!**/ShahedConfig.java' || echo 0
done   # все 0
```

---

### F-7: дефолтные `forbiddenZones` — жёстко зашитые координаты чужого сервера
СЕРЬЁЗНОСТЬ: low
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/config/ShahedConfig.java:166`
КОД:
```java
Arrays.asList("160,771,-622,1011", "170,-1020,-420,-785", "-750,-600,-1145,-1015"),
```
ПОЧЕМУ ЭТО БАГ: это конкретные прямоугольники конкретного мира (см. `credits="For KrutEvent"` в
`neoforge.mods.toml:10`). Любой другой сервер, включивший `/ata config zone blacklist enable`,
получает три случайные запретные области посреди своей карты и никакого объяснения.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: чистая установка, `blacklistEnabled=true` → атаки в области
(−622..160, 771..1011) блокируются «неизвестно почему».
ТЕСТИРУЕМОСТЬ: manual

---

## 2. КОМАНДЫ

### F-8: `preloadChunks` без ограничения дальности — форс-лоад миллионов чанков, которые НИКОГДА не освобождаются
СЕРЬЁЗНОСТЬ: critical
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/command/ShahedCommand.java:452-476` (и дубликат `network/LaunchShahedFromTerminalPacket.java:84-108`)
КОД:
```java
private static void preloadChunks(ServerLevel level, Vec3 from, Vec3 to) {
    ChunkPos spawnChunk = new ChunkPos(new BlockPos((int) from.x, (int) from.y, (int) from.z));
    for (int x = -3; x <= 3; ++x)
        for (int z = -3; z <= 3; ++z)
            level.setChunkForced(spawnChunk.x + x, spawnChunk.z + z, true);

    double totalDistance = from.distanceTo(to);
    int numPoints = Math.max(1, (int) (totalDistance / 32.0));
    for (int i = 0; i <= numPoints; ++i) {
        ...
        for (int x = -2; x <= 2; ++x)
            for (int z = -2; z <= 2; ++z)
                level.setChunkForced(pathChunk.x + x, pathChunk.z + z, true);
    }
}
```
ПОЧЕМУ ЭТО БАГ: три отдельных отказа.
1. **Нет верхней границы.** `BlockPosArgument.blockPos()` и `Vec3Argument.vec3()` принимают до ±30 000 000.
   Для `/ata attack shahed 0 200 0 30000000 64 30000000 high` → `totalDistance ≈ 4.24e7`,
   `numPoints ≈ 1 326 000`, внутренний цикл 5×5 → **~33 миллиона вызовов `setChunkForced`**, синхронно,
   в главном потоке, в теле команды. Каждый вызов заставляет `ServerChunkCache.updateChunkForced`
   поставить тикет и **сгенерировать** чанк. Сервер гарантированно виснет и падает по OOM.
2. **Ни один чанк не освобождается.** Grep по проекту: `setChunkForced(..., false)` встречается только
   в `chunk/MissileChunkManager.java:120,146,160` — для чанков, зарегистрированных **самим менеджером**.
   Чанки из `preloadChunks` в бухгалтерию `MissileChunkManager` не попадают, поэтому остаются в
   `ForcedChunksSavedData` уровня навсегда и сохраняются в сейв.
3. **Взаимное разрушение.** `ServerLevel.setChunkForced` — это один общий `LongSet` на уровень, без
   счётчика ссылок. `MissileChunkManager.updateChunksWithRadius` (строка 120) снимает форс с чанка,
   который мог быть форснут `preloadChunks` для другой ракеты — это и есть тот самый «TODO» на
   строках 30-33 `MissileChunkManager.java`, только здесь он проявляется между двумя РАЗНЫМИ
   подсистемами, а не между двумя ракетами.
Даже «легитимный» путь ломается: дефолт `spawnX/Z = 0` + цель на 12 000 блоков → 375 точек × 25 =
**9 375 форс-загруженных и сгенерированных чанков за один запуск дрона**, навсегда.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ:
```
/ata attack shahed 0 200 0 2000000 64 2000000 high
```
→ ~88 000 точек × 25 = 2.2 млн `setChunkForced`. Watchdog «Can't keep up» → «server thread dumped» →
крэш. После рестарта форс-чанки восстанавливаются из сейва → сервер не поднимается.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
@GameTest
public void preloadChunksIsUnbounded(GameTestHelper h) {
    ServerLevel lvl = h.getLevel();
    int before = lvl.getForcedChunks().size();
    // рефлексией дёрнуть ShahedCommand#preloadChunks(lvl, new Vec3(0,200,0), new Vec3(200000,64,200000))
    // ожидание: before == after   →  ФАКТ: after - before > 150_000, и вызов не возвращается за 30 c
    h.assertTrue(lvl.getForcedChunks().size() == before, "preloadChunks утёк форс-чанками");
}
```

---

### F-9: `LaunchShahedFromTerminalPacket` не проверяет ни блок терминала, ни дистанцию до игрока
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/network/LaunchShahedFromTerminalPacket.java:39-81`
КОД:
```java
public static void handle(LaunchShahedFromTerminalPacket payload, IPayloadContext context) {
    context.enqueueWork(() -> {
        Player rawPlayer = context.player();
        if (!(rawPlayer instanceof ServerPlayer player)) return;

        int targetX = payload.targetX();
        int targetZ = payload.targetZ();
        BlockPos terminalPos = payload.terminalPos();

        if (ShahedConfig.isInForbiddenZone(targetX, targetZ)) { ... return; }
        if (!player.level().getFluidState(terminalPos).isEmpty()) { ... return; }
        ...
        ShahedEntity shahed = new ShahedEntity(player.level(), spawnPos, targetPos, "high");
```
ПОЧЕМУ ЭТО БАГ: сервер принимает `terminalPos` и координаты цели от клиента и **не проверяет ничего**:
нет проверки, что по `terminalPos` вообще стоит `ShahedTerminalBlock`; нет проверки дистанции игрока
до терминала; нет проверки прав; нет проверки дальности (см. F-6 — `shahedMaxRange` мёртв). Сравните
с `LaunchRocketPacket.handle:49-58`, где хотя бы проверяется класс блока и его состояние `LOADED`.
Единственная защита — `getFluidState(terminalPos).isEmpty()`, то есть проверка воды в произвольной
клиентом точке мира, что не защищает ничего.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: любой игрок без терминала шлёт
`LaunchShahedFromTerminalPacket(targetX, targetZ, new BlockPos(0,200,0))` → дрон запускается,
плюс запускается `preloadChunks` (F-8) на всю длину траектории.
ТЕСТИРУЕМОСТЬ: gametest / manual
НАБРОСОК ТЕСТА:
```java
// сервер: игрок в 10 000 блоков от любого терминала
NetworkHandler.sendToServer(new LaunchShahedFromTerminalPacket(500, 500, BlockPos.ZERO));
// ожидание: отказ. ФАКТ: level.getEntities(ShahedEntity) содержит новую сущность
```

---

### F-10: `LaunchFromUnifiedTerminalPacket` — НЕТ проверки запретной зоны и дальности
СЕРЬЁЗНОСТЬ: critical
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/network/LaunchFromUnifiedTerminalPacket.java:64-133`
КОД (полный список проверок в обработчике — их две, и обе не про зоны):
```java
int targetY = ShahedConfig.TARGET_Y_LEVEL.get();
BlockPos targetPos = new BlockPos(targetX, targetY, targetZ);

BlockPos launchBlockPos = new BlockPos((int) launchPos.x, (int) launchPos.y, (int) launchPos.z);
if (!level.getFluidState(launchBlockPos).isEmpty()) {
    player.displayClientMessage(Component.literal("§cЗапуск невозможен под водой!"), true);
    return;
}

if (missileType == 0) {
    ShahedEntity shahed = new ShahedEntity(level, launchPos, targetPos, "low");
    shahed.setOwner(player);
    shahed.isLegitSpawn = true;
    level.addFreshEntity(shahed);
} else {
    IskanderEntity iskander = new IskanderEntity(level, launchPos, targetPos);
    ...
}
```
ПОЧЕМУ ЭТО БАГ: `ShahedConfig.isInForbiddenZone` в этом файле **не вызывается ни разу**
(единственное обращение к конфигу — строка 114, `TARGET_Y_LEVEL`). Два других пусковых пакета
(`LaunchRocketPacket.java:73`, `LaunchShahedFromTerminalPacket.java:48`) проверку делают. Unified
Terminal — основной GUI мода (`UnifiedTerminalScreen`), и клиент делает проверку зоны сам
(`UnifiedTerminalScreen.java:325`), что для сервера ничего не значит. Итог: **вся система
blacklist/whitelist обходится через штатный интерфейс**.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: `blacklistEnabled=true`, зона `"0,0,100,100"`. Через Unified Terminal
выбираем цель 50,50 (клиент покажет ошибку — но клиент модифицируем/шлём пакет напрямую) →
`LaunchFromUnifiedTerminalPacket(50, 50, terminalPos, launchers, 1)` → Искандер летит в защищённую зону.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
ShahedConfig.BLACKLIST_ENABLED.set(true);
// forbiddenZones = ["0,0,100,100"]
NetworkHandler.sendToServer(new LaunchFromUnifiedTerminalPacket(50, 50, tPos, List.of(lPos), 1));
assertTrue(level.getEntities(ModEntities.ISKANDER.get(), ...).isEmpty()); // ПАДАЕТ
```

---

### F-11: `LaunchFromUnifiedTerminalPacket` запускает Искандер из ПУСТОЙ пусковой (условие инвертировано)
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/network/LaunchFromUnifiedTerminalPacket.java:79-99`
КОД:
```java
if (missileType == 1 && !launcherPositions.isEmpty()) {
    for (BlockPos launcherPos : launcherPositions) {
        BlockState launcherState = level.getBlockState(launcherPos);
        if (launcherState.getBlock() instanceof LauncherBlock launcherBlock) {
            if (!launcherState.getValue(LauncherBlock.LOADED)) {      // <-- ищем НЕзаряженную
                if (!launcherBlock.isBlocked(level, launcherPos)) {
                    selectedLauncher = launcherPos;
                    break;
                }
            }
        }
    }
    if (selectedLauncher == null) { ... return; }

    LauncherBlock launcher = (LauncherBlock) level.getBlockState(selectedLauncher).getBlock();
    launcher.setLoaded(level, selectedLauncher, true);                 // <-- и "заряжаем" её из воздуха
    launchPos = Vec3.atCenterOf(launcher.getLaunchPosition(level, selectedLauncher));
}
```
ПОЧЕМУ ЭТО БАГ: отбирается пусковая с `LOADED == false`, затем ей принудительно ставится `LOADED = true`
и из неё запускается `IskanderEntity` — ракета не расходуется, `IskanderRocketBlockEntity` не
проверяется. Сравните с корректным путём `LaunchRocketPacket.java:55-58`:
```java
if (!launcherState.getValue(LauncherBlock.LOADED)) {
    player.displayClientMessage(Component.translatable("message.ata.rocket.not_loaded"), true);
    return;
}
```
То есть в одном пакете `LOADED == false` — причина отказа, в другом — критерий выбора.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: поставить `ata:launcher` без ракеты, открыть Unified Terminal, выбрать
`missileType = 1` → баллистическая ракета стартует бесплатно, бесконечное число раз.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
h.setBlock(pos, ModBlocks.LAUNCHER.get().defaultBlockState()); // LOADED = false
NetworkHandler.sendToServer(new LaunchFromUnifiedTerminalPacket(2000, 2000, tPos, List.of(pos), 1));
h.assertTrue(level.getEntitiesOfClass(IskanderEntity.class, BIG_AABB).isEmpty(),
             "Искандер запущен из незаряженной пусковой"); // ПАДАЕТ
```

---

### F-12: неограниченный `readInt()` как длина списка в декодере пакета
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/network/LaunchFromUnifiedTerminalPacket.java:45-56`
КОД:
```java
(buf) -> {
    int targetX = buf.readInt();
    int targetZ = buf.readInt();
    BlockPos terminalPos = buf.readBlockPos();
    int listSize = buf.readInt();
    List<BlockPos> launcherPositions = new ArrayList<>();
    for (int i = 0; i < listSize; i++) {
        launcherPositions.add(buf.readBlockPos());
    }
    ...
}
```
ПОЧЕМУ ЭТО БАГ: `listSize` берётся с провода без верхней границы и без проверки на отрицательность.
Все прочие пакеты проекта используют `ByteBufCodecs`/`StreamCodec.composite` с ограниченными кодеками
(см. `LaunchRocketPacket.java:26-32`), здесь — ручной `StreamCodec.of` без лимита. `ByteBufCodecs.list(int)`
существует ровно для этого и не применён.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: клиент шлёт `listSize = 0x7FFFFFFF` → `ArrayList` растёт до исчерпания
буфера, затем `readBlockPos` кидает `IndexOutOfBoundsException` внутри netty-декодера; при этом до
броска уже произведены сотни тысяч аллокаций на каждый пакет. Повтор в цикле = GC-давление на сервере.
ТЕСТИРУЕМОСТЬ: pure-java
НАБРОСОК ТЕСТА:
```java
var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess);
buf.writeInt(0); buf.writeInt(0); buf.writeBlockPos(BlockPos.ZERO); buf.writeInt(Integer.MAX_VALUE);
assertThrows(DecoderException.class, () -> LaunchFromUnifiedTerminalPacket.STREAM_CODEC.decode(buf));
// ФАКТ: летит IndexOutOfBoundsException после ~миллиона аллокаций, а не ранняя валидация длины
```

---

## 3. COMPAT (рефлексия)

### F-13: `CreateCompat.detect()` выставляет `detectionDone = true` ДО заполнения результатов — гонка при первом обращении
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/compat/CreateCompat.java:21-23, 82-116`
КОД:
```java
private static boolean anyCreateDetected = false;
private static boolean detectionDone = false;
private static final Set<String> detectedModIds = new HashSet<>();

public static boolean isAnyCreateLoaded() {
    if (!detectionDone) { detect(); }
    return anyCreateDetected;
}

private static void detect() {
    detectionDone = true;                                  // <-- строка 90, ПЕРВОЕ действие
    ModList modList = ModList.get();
    if (modList == null) return;
    for (String id : KNOWN_CREATE_MODS) { ... }            // строки 94-99
    modList.getMods().forEach(modInfo -> { ... });         // строки 101-107
    createDamageNamespaces.add("create");
    anyCreateDetected = !detectedModIds.isEmpty();         // <-- строка 111, ПОСЛЕДНЕЕ действие
    ...
}
```
ПОЧЕМУ ЭТО БАГ: между строкой 90 и строкой 111 есть окно, в котором `detectionDone == true`, а
`anyCreateDetected` всё ещё `false`. Второй поток, вошедший в `isAnyCreateLoaded()` в этом окне,
пропускает `detect()` и получает `false`. Поля не `volatile`, синхронизации нет, happens-before нет.
Метод вызывается из пяти мест в разных потоках: `ShahedEntity.hurt:1504`, `IskanderEntity` /
`OrlanEntity.java:882` / `OrlanLauncherEntity.java:219` / `PaintStandEntity.java:329` /
`ShahedLauncherEntity.java:350` (server thread) и `CreateBlockingHandler.onRightClickBlock:28`
(вызывается на клиенте и на сервере). Дополнительно: `modList == null` → `return` со строки 93 при уже
выставленном `detectionDone = true` **навсегда** фиксирует «Create нет».
Тот же паттерн, но без ранней записи флага, в `TacZCompat.java:12-18` и `SuperbWarfareCompat.java:39-47`:
```java
public static boolean isTacZLoaded() {
    if (!checkedTacz) { taczLoaded = ModList.get().isLoaded("tacz"); checkedTacz = true; }
    return taczLoaded;
}
```
— там порядок правильный, но поля также не `volatile`, поэтому запись `taczLoaded` может стать видимой
другому потоку позже, чем запись `checkedTacz`.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: одновременный первый тик сервера с `hurt()` от Create-дрели и клиентский
`RightClickBlock` — один из потоков видит `anyCreateDetected == false` и наносит/пропускает урон вопреки
защите. Воспроизводится нестабильно (гонка), но детерминированно доказуемо по байткоду/порядку записей.
ТЕСТИРУЕМОСТЬ: pure-java
НАБРОСОК ТЕСТА:
```java
@Test void detectionFlagSetBeforeResult() throws Exception {
    var f = CreateCompat.class.getDeclaredMethod("detect"); f.setAccessible(true);
    // инструментируем: подменяем ModList.get() на медленный мок,
    // из второго потока в это время зовём isAnyCreateLoaded()
    // ожидание: true (Create загружен). ФАКТ: false
}
```

---

### F-14: `CreateCompat.registerBlockProtection()` не вызывается никогда — 137 строк рефлексии мертвы, блоки НЕ защищены
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/compat/CreateCompat.java:197-348`
КОД:
```java
public static void registerBlockProtection(Collection<Block> ataBlocks) {
    if (!isAnyCreateLoaded()) return;
    boolean registered = tryRegisterViaBlockMovementChecks(ataBlocks);
    if (!registered) { registered = tryRegisterViaContraptionMovementSetting(ataBlocks); }
    if (!registered) {
        LOGGER.warn("[ATA] Не удалось зарегистрировать блоки как неподвижные для Create. " +
                "Блоки ATA могут быть перемещены контрапциями.");
    }
}
```
ПОЧЕМУ ЭТО БАГ: grep по всему `src/main/java` — `registerBlockProtection` встречается **только** в
строке своего объявления. Ни `AirTacticalArsenal` (конструктор, строки 32-48), ни `commonSetup`
(строки 50-52, тело — один `LOGGER.info`), ни `ServerForgeEvents` его не зовут. Следовательно:
* `tryRegisterViaBlockMovementChecks` (строки 212-271) и `tryRegisterViaContraptionMovementSetting`
  (строки 273-348) — 137 строк рефлексии, Proxy и enum-скана, которые никогда не исполняются;
* блоки ATA (пусковая, терминалы, радар, сирена, люк) **свободно перемещаются контрапциями Create**;
* предупреждение в лог тоже не печатается — админ не узнает о проблеме.
`commonSetup` — единственное место, где такой вызов был бы уместен, и оно пустое:
```java
private void commonSetup(FMLCommonSetupEvent event) {
    LOGGER.info("Air Tactical Arsenal common setup");
}
```
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: Create + ATA. Ставим `ata:launcher`, приклеиваем супер-клеем к поршню
контрапции → пусковая уезжает вместе с контрапцией (или дюпается/теряет BlockEntity).
ТЕСТИРУЕМОСТЬ: build-check + manual
НАБРОСОК ТЕСТА:
```bash
rg -n "registerBlockProtection" src/main/java   # ровно 1 попадание — объявление
```

---

### F-15: `createbigcannons` в списке «Create-модов» → Шахед и Искандер неуязвимы для пушек Create Big Cannons
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/compat/CreateCompat.java:50,118-128`; `entity/ShahedEntity.java:1500-1505`
КОД:
```java
// CreateCompat.java:26-57 (фрагмент)
private static final String[] KNOWN_CREATE_MODS = {
        "create",
        ...
        "createbigcannons",     // <-- строка 50
        ...
};
// CreateCompat.java:118-128
public static boolean isCreateDamageSource(DamageSource source) {
    if (!isAnyCreateLoaded()) return false;
    if (source == null) return false;
    try {
        Optional<ResourceKey<DamageType>> key = source.typeHolder().unwrapKey();
        if (key.isPresent()) {
            String namespace = key.get().location().getNamespace();
            if (createDamageNamespaces.contains(namespace)) return true;
            if (namespace.startsWith("create")) return true;
        }
    } catch (Exception ignored) { }
```
```java
// ShahedEntity.java:1500-1505
@Override
public boolean hurt(DamageSource source, float amount) {
    if (this.level().isClientSide) return false;
    if (CreateCompat.isCreateDamageSource(source)) return false;
```
ПОЧЕМУ ЭТО БАГ: `detect()` (строки 94-98) заносит любой найденный id из `KNOWN_CREATE_MODS` в
`createDamageNamespaces`. Все типы урона Create Big Cannons живут в неймспейсе `createbigcannons`
(и он же ловится веткой `namespace.startsWith("create")`), поэтому `isCreateDamageSource` вернёт `true`
для попадания снаряда крупнокалиберной пушки. `hurt` возвращает `false` **до** любой другой логики —
ни `takeDamage`, ни зонная модель повреждений не вызываются.
Тот же эффект для `IskanderEntity` (косвенно), `OrlanEntity.java:882`, `OrlanLauncherEntity.java:219`,
`PaintStandEntity.java:329`, `ShahedLauncherEntity.java:350`.
Ирония: весь смысл секции конфига «Anti-Air Defense» (строки 195-206 `ShahedConfig.java`) — дать
игрокам сбивать дроны. Самый очевидный инструмент ПВО в модпаке с Create — зенитная пушка CBC — отключён.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: Create + Create Big Cannons + ATA. Прямое попадание снаряда в Шахед →
`hurt` возвращает `false`, дрон продолжает полёт с полным HP.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
// мок DamageSource с ResourceKey ResourceLocation.fromNamespaceAndPath("createbigcannons","cannon_shell")
assertFalse(CreateCompat.isCreateDamageSource(cbcSource)); // ПАДАЕТ: возвращает true
```

---

### F-16: TacZ-совместимость целиком на строковых именах классов и некэшированной рефлексии в горячем пути
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/compat/TacZCompat.java:20-87`; вызов — `entity/ShahedEntity.java:1250-1300`
КОД:
```java
public static boolean isTacZBullet(Entity entity) {
    if (!isTacZLoaded()) return false;
    if (entity == null) return false;
    String className = entity.getClass().getName();
    return className.contains("com.tacz.guns.entity.EntityKineticBullet");
}

public static ResourceLocation getGunId(Entity bullet) {
    if (!isTacZBullet(bullet)) return null;
    try {
        var method = bullet.getClass().getMethod("getGunId");   // <-- lookup на КАЖДЫЙ вызов
        return (ResourceLocation) method.invoke(bullet);
    } catch (Exception e) { return null; }
}

public static float getBulletDamage(Entity bullet, Vec3 hitPos) {
    if (!isTacZBullet(bullet)) return 10.0f;
    try {
        var method = bullet.getClass().getMethod("getDamage", Vec3.class);
        ...
    } catch (Exception e) {
        try { var field = bullet.getClass().getDeclaredField("damageAmount"); ... }
        catch (Exception e2) { }
    }
    return 10.0f;                                               // <-- молчаливый фолбэк
}
```
Горячий путь (`ShahedEntity.java:1250-1262`, каждый тик, на каждый Шахед):
```java
double bulletSpeed = 15.0;
AABB searchBox = this.getBoundingBox().inflate(bulletSpeed);
List<Entity> nearbyBullets = this.level().getEntities(this, searchBox, TacZCompat::isTacZBullet);
```
ПОЧЕМУ ЭТО БАГ:
1. `Class.getMethod`/`getDeclaredField` не кэшируются нигде — `Method`-объекты аллоцируются заново на
   каждое попадание (`getBulletDamage`, `getGunIdString`→`getGunId`, `getShooter` — три lookup'а на
   попадание, каждый под `isTacZBullet`, который ещё раз строит строку имени класса).
2. `getEntities(..., TacZCompat::isTacZBullet)` каждый тик выполняет `getClass().getName()` +
   `String.contains` для **всех** сущностей в кубе 30×30×30 вокруг каждого Шахеда.
3. `className.contains(...)` вместо `instanceof`/`Class.forName` ломается при любом переименовании,
   релокации или форке TacZ — и ломается **молча**: `isTacZBullet` вернёт `false`, `hurt` при этом
   пропустит пулю (`ShahedEntity.java:1515-1519` явно возвращает `false` для TacZ-пуль, полагаясь на
   тиковый скан), и Шахед станет полностью неуязвим для TacZ.
4. `getBulletDamage` при провале обеих рефлексий возвращает **фиксированные 10.0f** без единой записи в
   лог — весь балансный блок конфига (`shahedMinCaliberDamage`, `shahedDamageMultiplier`, …) начинает
   работать от константы, а не от реального урона оружия.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: TacZ переименовывает `getDamage(Vec3)` → каждый выстрел из «Барретта» и из
«Макарова» наносит одинаковые `10.0f * category.damageMultiplier`, диагностики нет.
ТЕСТИРУЕМОСТЬ: pure-java
НАБРОСОК ТЕСТА:
```java
Entity fake = mock(Entity.class);          // класс без getGunId/getDamage
when(fake.getClass()).thenReturn(SomeClassNamed_com_tacz_guns_entity_EntityKineticBullet.class);
assertEquals(10.0f, TacZCompat.getBulletDamage(fake, Vec3.ZERO)); // проходит — и это ошибка дизайна
// + JMH: 1e6 вызовов getGunId → >100x медленнее кэшированного MethodHandle
```

---

### F-17: `SuperbWarfareCompat.getRadarType` не требует, чтобы сущность была техникой — снаряды попадают на радар как самолёты
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/compat/SuperbWarfareCompat.java:49-88, 111-125`; вызов — `network/RequestRadarUpdatePacket.java:64`
КОД:
```java
public static byte getRadarType(Entity entity) {
    if (entity == null) return -1;
    if (!isLoaded() && !isVvpLoaded()) return -1;

    String pkg = entity.getClass().getName();
    boolean isSW  = isLoaded()    && pkg.startsWith(SW_PKG);
    boolean isVVP = isVvpLoaded() && pkg.startsWith(VVP_PKG);
    if (!isSW && !isVVP) return -1;          // <-- единственный фильтр: префикс ПАКЕТА

    byte byHierarchy = classifyByHierarchy(entity);
    if (byHierarchy >= 0) return byHierarchy;
    ...
}

private static byte classifyByHierarchy(Entity entity) {
    Class<?> clazz = entity.getClass();
    while (clazz != null && clazz != Object.class) {
        String sn = clazz.getSimpleName().toLowerCase();
        for (String kw : HELI_KEYWORDS)  if (sn.contains(kw)) return TYPE_SW_HELICOPTER;
        for (String kw : PLANE_KEYWORDS) if (sn.contains(kw)) return TYPE_SW_PLANE;
        for (String kw : DRONE_KEYWORDS) if (sn.contains(kw)) return TYPE_SW_DRONE;
        clazz = clazz.getSuperclass();
    }
    return -1;
}
```
ПОЧЕМУ ЭТО БАГ: в этом же файле уже есть корректная проверка «это техника»:
```java
public static boolean isVehicle(Entity entity) {           // строки 111-125
    ...
    while (clazz != null && clazz != Object.class) {
        if (clazz.getSimpleName().toLowerCase().contains("vehicle")) return true;
        clazz = clazz.getSuperclass();
    }
    return false;
}
```
— но `getRadarType` её **не использует**. Единственный фильтр — префикс пакета `com.atsuishio.superbwarfare`
/ `tech.vvp.vvp`, под который подпадают ВСЕ сущности этих модов: снаряды, гранаты, мины, лазеры,
ракеты. Дальше классификация идёт подстрочным `contains` по коротким токенам:
```java
private static final String[] HELI_KEYWORDS  = {"helicopter", "heli", "copter", "rotor"};
private static final String[] PLANE_KEYWORDS = {"airplane", "aircraft", "fixedwing", "plane", "jet", "bomber", "fighter"};
private static final String[] PLANE_NAMES = {"a10", "ju87", "tom6", "kv16", "f16", "mig", "su27", "su25", "b52", ...};
```
Токены `"heli"`, `"jet"`, `"mig"`, `"a10"`, `"b52"`, `"m2"`-подобные — подстроки, а не полные имена:
любой класс/registry-path с `heli` в имени (например ракета, выпущенная вертолётом) даёт метку
«ВЕРТОЛЁТ» (`getTypeName` → строка 129). Плюс порядок проверок фиксирован: PLANE проверяется раньше
DRONE, поэтому имя, содержащее и `plane`, и `fpv`, всегда станет самолётом.
Вызывающий код (`RequestRadarUpdatePacket.java:57-66`) прогоняет это через **каждую** сущность в кубе
3000×414×3000 на каждый запрос радара от клиента:
```java
for (Entity entity : level.getEntities(null, searchBox)) {
    byte type = -1;
    if (entity instanceof ShahedEntity) type = 0;
    ...
    else { byte swType = SuperbWarfareCompat.getRadarType(entity); if (swType >= 0) type = swType; }
```
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: SuperbWarfare + ATA. Вертолёт выпускает залп ракет → на радаре ATA
появляется десяток дополнительных «вертолётов»/«самолётов», живущих 1-2 секунды. Оператор ПВО видит
ложную групповую цель.
ТЕСТИРУЕМОСТЬ: pure-java
НАБРОСОК ТЕСТА:
```java
class HeliRocketEntity extends Entity { }   // в пакете com.atsuishio.superbwarfare.entity.projectile
assertEquals(-1, SuperbWarfareCompat.getRadarType(new HeliRocketEntity(...)));  // ПАДАЕТ: 3 (ВЕРТОЛЁТ)
assertFalse(SuperbWarfareCompat.isVehicle(new HeliRocketEntity(...)));          // проходит — рассогласование
```

---

## 4. РЕЦЕПТЫ И КРАФТ-СОБЫТИЯ

### F-18: `ModCraftingEvents` построен на неверной предпосылке — `ItemCraftedEvent` срабатывает ДО списания ингредиентов
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/event/ModCraftingEvents.java:14-20, 41-69`
КОД (комментарий-предпосылка, строки 16-20):
```java
 * <p>Vanilla crafting only removes ONE item from each occupied slot, and {@code getRemainingItems}
 * cannot remove more (the leftover is grown back), so {@link ...SprayCanFillingRecipe}
 * alone charges +30 per dye <em>slot</em>. After the craft, this handler tops the can's charge up
 * from the dye left over in the grid (the part of each stack beyond the one vanilla already took)
 * and consumes exactly those extra dyes — so a slot of 2 dye gives +60 and is fully spent.
```
КОД (реализация, строки 41-69):
```java
Container grid = event.getInventory();
int leftoverDye = 0;
for (int i = 0; i < grid.getContainerSize(); i++) {
    ItemStack s = grid.getItem(i);
    if (s.is(Items.BLACK_DYE)) { leftoverDye += s.getCount(); }
}
...
int extraDyes = Math.min(leftoverDye, dyesNeeded);
SprayCanItem.setCharge(result, Math.min(SprayCanItem.MAX_CHARGE, charge + extraDyes * CHARGE_PER_DYE));
int toConsume = extraDyes;
for (int i = 0; i < grid.getContainerSize() && toConsume > 0; i++) {
    ItemStack s = grid.getItem(i);
    if (s.is(Items.BLACK_DYE)) { int take = Math.min(s.getCount(), toConsume); grid.removeItem(i, take); toConsume -= take; }
}
```
ПОЧЕМУ ЭТО БАГ: порядок обратный заявленному. Декомпилированный
`net/minecraft/world/inventory/ResultSlot.java` (1.21.1 + NeoForge-патчи, из
`~/.gradle/caches/neoformruntime/.../sourcesAndCompiledWithNeoForge_...jar`):
```java
@Override
protected void checkTakeAchievements(ItemStack p_40185_) {
    if (this.removeCount > 0) {
        p_40185_.onCraftedBy(this.player.level(), this.player, this.removeCount);
        net.neoforged.neoforge.event.EventHooks.firePlayerCraftingEvent(this.player, p_40185_, this.craftSlots);   // строка 50
    }
    ...
}

@Override
public void onTake(Player p_150638_, ItemStack p_150639_) {
    this.checkTakeAchievements(p_150639_);                       // строка 62  <-- СОБЫТИЕ ЗДЕСЬ
    ...
    for (int k = 0; k < craftinginput.height(); k++) {
        for (int l = 0; l < craftinginput.width(); l++) {
            ...
            if (!itemstack.isEmpty()) {
                this.craftSlots.removeItem(i1, 1);               // строка 77  <-- СПИСАНИЕ ЗДЕСЬ, ПОСЛЕ
            }
```
Следовательно `grid.getItem(i).getCount()` в обработчике включает тот краситель, который ваниль ещё
только собирается списать. Обработчик засчитывает его повторно и сам же его удаляет, после чего в
ванильном цикле слот уже пуст (`itemstack.isEmpty()`) и `removeItem` не выполняется.
**Итог: +30 заряда сверх положенного за каждый крафт.**
Пошаговый расчёт (MAX_CHARGE = 300, CHARGE_PER_DYE = 30 — `SprayCanItem.java:42`, `ModCraftingEvents.java:25`):
* баллончик с зарядом 240, 1 краситель в одном слоте;
* `SprayCanFillingRecipe.assemble` (строка 105): `dyeSlots=1`, `dyesNeeded=ceil(60/30)=2`, `dyesUsed=1`
  → результат 270;
* обработчик: `charge=270` (0 < 270 < 300 → не выходит), `leftoverDye=1`, `dyesNeeded=1`, `extraDyes=1`
  → `setCharge(300)`, `grid.removeItem(slot, 1)` → слот пуст;
* ванильный цикл: слот пуст → списания нет.
* Израсходован **1** краситель, начислено **+60** заряда. Дюп ресурса ×2.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: наполнить баллончик водой, довести заряд до 240 (или любого значения, где
`300 − charge > 30`), положить в сетку 1 чёрный краситель → баллончик становится полным, краситель
израсходован один.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
@GameTest void refillDoesNotDoubleCount(GameTestHelper h) {
    ItemStack can = new ItemStack(ModItems.SPRAY_CAN.get());
    SprayCanItem.setHasWater(can, true); SprayCanItem.setCharge(can, 240);
    // сетка: can + 1x BLACK_DYE; выполнить полный ResultSlot#onTake
    // ожидание: charge == 270 и 0 красителя в сетке
    // ФАКТ:    charge == 300 и 0 красителя в сетке
}
```

---

### F-19: `SprayCanFillingRecipe` показывает пустой результат в книге рецептов / JEI
СЕРЬЁЗНОСТЬ: low
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/recipe/SprayCanFillingRecipe.java:43`
КОД:
```java
public class SprayCanFillingRecipe extends CustomRecipe {
```
`net/minecraft/world/item/crafting/CustomRecipe.java`:
```java
@Override
public ItemStack getResultItem(HolderLookup.Provider p_336187_) { return ItemStack.EMPTY; }
@Override
public boolean isSpecial() { return true; }
```
ПОЧЕМУ ЭТО БАГ: `getResultItem` не переопределён, поэтому любая интеграция (JEI/EMI/REI, книга
рецептов) видит пустой выход. Рецепт `data/ata/recipe/spray_can_filling.json` — `{"type": "ata:spray_can_filling"}`
без единого поля, то есть у игрока нет ни одного способа узнать, что баллончик заправляется чёрным
красителем. Симметричный `SprayCanCraftingRecipe` (`getResultItem` = `spray_can` с зарядом 0) в книге
показывает пустой баллончик, хотя `assemble` (строка 36) выдаёт полный на 300 — превью не совпадает с результатом.
ТЕСТИРУЕМОСТЬ: manual

---

## 5. РЕГИСТРАЦИЯ / СБОРКА / АССЕТЫ

### F-20: sounds.json объявляет 3 звука без .ogg; 2 .ogg-файла осиротели; 3 SoundEvent не используются в коде
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `src/main/resources/assets/ata/sounds.json`, `src/main/resources/assets/ata/sounds/`, `src/main/java/com/synarsis/airtacticalarsenal/sound/ModSounds.java`

**ПОЛНАЯ СВЕРКА (13 зарегистрированных SoundEvent × sounds.json × .ogg × использование в коде):**

| SoundEvent (`ModSounds.java`) | запись в `sounds.json` | файл `sounds/*.ogg` | вызов в коде |
|---|---|---|---|
| `shahed_engine` (:15) | ✔ (:2) | ✔ `shahed_engine.ogg` | ✔ 1 |
| `explosion_1` (:17) | ✔ (:15) | ✔ | ✔ 3 |
| `explosion_2` (:18) | ✔ (:28) | ✔ | ✔ 3 |
| `explosion_3` (:19) | ✔ (:41) | ✔ | ✔ 3 |
| `explosion_4` (:20) | ✔ (:54) | ✔ | ✔ 3 |
| `explosion_5` (:21) | ✔ (:67) | ✔ | ✔ 3 |
| `siren` (:23) | ✔ (:80) | ✔ | ✔ 1 |
| `orlan_engine` (:25) | ✔ (:93) | ✔ | ✔ 1 |
| `iskander_launch` (:27) | ✔ (:106) | ✔ | ✔ 1 |
| **`iskander_flight` (:28)** | ✔ (:119) | ✘ **НЕТ ФАЙЛА** | ✘ **0** |
| **`iskander_explosion` (:29)** | ✔ (:132) | ✘ **НЕТ ФАЙЛА** | ✘ **0** |
| `iskander_preparing` (:30) | ✔ (:145) | ✔ | ✔ 1 |
| **`iskander_terminal` (:31)** | ✔ (:158) | ✘ **НЕТ ФАЙЛА** | ✘ **0** |

**Осиротевшие .ogg (есть файл, нет записи в sounds.json → мусор в jar):**
* `sounds/iskander_explosion_far.ogg` (86 199 байт)
* `sounds/shahed_launch.ogg` (52 414 байт)

КОД (`sounds.json:119-131`, ссылка на несуществующий файл):
```json
"iskander_flight": {
  "subtitle": "subtitles.ata.iskander_flight",
  "sounds": [ { "name": "ata:iskander_flight", "stream": true, "volume": 1.2, ... } ]
},
```
ПОЧЕМУ ЭТО БАГ: клиент при загрузке ресурсов печатает
`Unable to play unknown soundEvent`/`File .../sounds/iskander_flight.ogg does not exist` для трёх
записей; звук пуска/полёта/терминальной фазы Искандера отсутствует. Обратная сторона: два .ogg
(138 КБ) попадают в jar, но недостижимы — очевидно, `iskander_explosion_far.ogg` должен был быть
привязан к `iskander_explosion`, но привязка потеряна.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: запуск клиента → в `latest.log` три предупреждения о недостающих файлах;
Искандер летит и взрывается беззвучно.
ТЕСТИРУЕМОСТЬ: build-check
НАБРОСОК ТЕСТА:
```python
import json, os
s = json.load(open('src/main/resources/assets/ata/sounds.json'))
names = {e['name'].split(':')[1] for v in s.values() for e in v['sounds']}
files = {f[:-4] for f in os.listdir('src/main/resources/assets/ata/sounds')}
assert not (names - files), f"объявлены, но отсутствуют: {names - files}"   # {'iskander_flight','iskander_explosion','iskander_terminal'}
assert not (files - names), f"файлы без записи: {files - names}"            # {'iskander_explosion_far','shahed_launch'}
```

---

### F-21: `subtitles.ata.orlan_engine` объявлен в sounds.json, но отсутствует в ОБОИХ языковых файлах
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `src/main/resources/assets/ata/sounds.json:94`; `assets/ata/lang/en_us.json`, `assets/ata/lang/ru_ru.json`
КОД:
```json
"orlan_engine": {
  "subtitle": "subtitles.ata.orlan_engine",
```
Полный список `subtitles.*` в **обоих** lang-файлах (идентичен, 8 ключей):
```
subtitles.ata.explosion
subtitles.ata.iskander_explosion
subtitles.ata.iskander_flight
subtitles.ata.iskander_launch
subtitles.ata.iskander_preparing
subtitles.ata.iskander_terminal
subtitles.ata.shahed_engine
subtitles.ata.siren
```
ПОЧЕМУ ЭТО БАГ: `subtitles.ata.orlan_engine` не переведён нигде → при включённых субтитрах игрок
видит сырой ключ `subtitles.ata.orlan_engine`. Одновременно **три** переведённых субтитра
(`iskander_flight`, `iskander_explosion`, `iskander_terminal`) относятся к звукам, у которых нет .ogg
(F-20), то есть никогда не покажутся.
ТЕСТИРУЕМОСТЬ: build-check
НАБРОСОК ТЕСТА:
```python
subs = {v['subtitle'] for v in json.load(open('.../sounds.json')).values() if 'subtitle' in v}
for lang in ('en_us','ru_ru'):
    d = json.load(open(f'.../lang/{lang}.json'))
    assert subs <= d.keys(), (lang, subs - d.keys())   # {'subtitles.ata.orlan_engine'}
```

---

### F-22: 16 ключей локализации есть в ru_ru.json и отсутствуют в en_us.json
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `src/main/resources/assets/ata/lang/en_us.json` (122 ключа) vs `ru_ru.json` (138 ключей)
ПОЛНЫЙ СПИСОК расхождений (`ru_ru` \ `en_us`; обратное множество пусто):
```
block.ata.loading_hatch
gui.ata.terminal.configure_target
gui.ata.terminal.target
gui.ata.terminal.distance
gui.ata.terminal.cep
gui.ata.terminal.not_set
gui.ata.target_settings.title
gui.ata.target_settings.coord_x
gui.ata.target_settings.coord_y
gui.ata.target_settings.coord_z
message.ata.rocket_loaded
message.ata.rocket_unloaded
message.ata.hatch_empty
message.ata.hatch_full
message.ata.target_set
message.ata.target_cleared
```
ПОЧЕМУ ЭТО БАГ: `en_us.json` — язык по умолчанию и фолбэк для всех локалей. Английский игрок увидит
сырой `block.ata.loading_hatch` вместо названия блока в инвентаре/подсказке, и сырые ключи во всём
экране настройки цели (`gui.ata.target_settings.*`) и в сообщениях загрузки/выгрузки ракеты. То же
самое увидит игрок с любой третьей локалью (`de_de`, `zh_cn`, …), потому что фолбэк идёт в `en_us`.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: клиент с языком English → навести курсор на `ata:loading_hatch` →
подпись «block.ata.loading_hatch».
ТЕСТИРУЕМОСТЬ: build-check
НАБРОСОК ТЕСТА:
```python
en = json.load(open('.../en_us.json')); ru = json.load(open('.../ru_ru.json'))
assert set(ru) == set(en), set(ru) - set(en)   # 16 ключей
```

---

### F-23: блоки `shahed_terminal` и `loading_hatch` не входят ни в одну творческую вкладку
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/item/ModCreativeTabs.java:21-39`; `block/ModBlocks.java:25-26,33-34,42-44`
КОД (`ModCreativeTabs.java` — полный `displayItems`):
```java
.displayItems((parameters, output) -> {
    output.accept(ModBlocks.SHAHED_RADAR.get());
    output.accept(ModBlocks.SIREN.get());
    output.accept(ModBlocks.UNIFIED_TERMINAL.get());
    output.accept(ModBlocks.LAUNCHER.get());
    output.accept(ModItems.ISKANDER.get());
    output.accept(ModItems.SHAHED.get());
    output.accept(ModItems.SHAHED_LAUNCHER.get());
    output.accept(ModItems.CONTROL_TABLET.get());
    output.accept(ModItems.LAUNCH_INTERFACE_CABLE.get());
    output.accept(ModItems.PAINT_STAND.get());
    ItemStack fullSprayCan = new ItemStack(ModItems.SPRAY_CAN.get());
    SprayCanItem.setCharge(fullSprayCan, SprayCanItem.MAX_CHARGE);
    output.accept(fullSprayCan);
    output.accept(ModItems.ORLAN_LAUNCHER.get());
    output.accept(ModItems.ORLAN.get());
    output.accept(ModItems.ORLAN_TABLET.get());
})
```
Зарегистрированные, но не добавленные (`ModBlocks.java`):
```java
public static final DeferredBlock<Block> SHAHED_TERMINAL = registerBlock("shahed_terminal", ...);  // :25
public static final DeferredBlock<Block> LOADING_HATCH   = registerBlock("loading_hatch", ...);    // :33
private static <T extends Block> void registerBlockItem(String name, DeferredBlock<T> block) {
    ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));         // :43 — без .tab
}
```
ПОЧЕМУ ЭТО БАГ: `BlockItem` создаётся с голым `new Item.Properties()`, а в 1.21 принадлежность к
творческой вкладке задаётся только через `CreativeModeTab.displayItems`. Оба предмета существуют в
реестре, имеют рецепты (`data/ata/recipe/shahed_terminal.json`, `data/ata/recipe/loading_hatch.json`)
и лут-таблицы, но в креативе недоступны и в поиске по вкладкам не находятся.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: креатив → вкладка ATA → `shahed_terminal` и `loading_hatch` отсутствуют;
получить их можно только через `/give`.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
var items = CreativeModeTabs.searchTab().getDisplayItems();
assertTrue(items.stream().anyMatch(s -> s.is(ModBlocks.SHAHED_TERMINAL.get().asItem()))); // ПАДАЕТ
assertTrue(items.stream().anyMatch(s -> s.is(ModBlocks.LOADING_HATCH.get().asItem())));   // ПАДАЕТ
```

---

### F-24: `clientTrackingRange(600)` / `(800)` — значение задаётся в ЧАНКАХ; дальнее сопровождение ракет не работает
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/entity/ModEntities.java:18,26,55`
КОД:
```java
EntityType.Builder.<ShahedEntity>of(ShahedEntity::new, MobCategory.MISC)
        .sized(3.0f, 1.5f)
        .clientTrackingRange(600)      // :18
        .updateInterval(1)
...
        .clientTrackingRange(800)      // :26  (Iskander)
...
        .clientTrackingRange(600)      // :55  (Orlan)
```
ПОЧЕМУ ЭТО БАГ: значение — в чанках, а не блоках (`ChunkMap.java:1083`: `int i = entitytype.clientTrackingRange() * 16;`).
600 чанков = 9600 блоков — намерение автора (дальнобойный радар/сопровождение), но
`ChunkMap$TrackedEntity.updatePlayer` (декомпилированный `net/minecraft/server/level/ChunkMap.java:1297-1306`)
режет это дважды:
```java
int i = ChunkMap.this.getPlayerViewDistance(p_140498_);
double d0 = (double)Math.min(this.getEffectiveRange(), i * 16);     // клип по view-distance
...
boolean flag = d1 <= d2
    && this.entity.broadcastToPlayer(p_140498_)
    && ChunkMap.this.isChunkTracked(p_140498_, this.entity.chunkPosition().x, this.entity.chunkPosition().z);
```
`isChunkTracked` требует, чтобы чанк сущности вообще отправлялся игроку. Итог: сопровождение
ограничено обычным view-distance (8-32 чанка), а 600/800 не дают ничего, кроме `updateInterval(1)` —
полный пакет позиции каждый тик для каждой ракеты для каждого игрока в радиусе. Ровно поэтому мод и
вынужден городить собственную систему `preloadChunks`/`MissileChunkManager` (F-8).
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: view-distance 10, Шахед в 500 блоках от игрока → сущность не отслеживается,
клиентский `ShahedEngineSoundClient` и рендер не работают, хотя конфиг обещает дальность 12 000.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
h.getLevel().getServer().getPlayerList().setViewDistance(8);
// заспавнить ShahedEntity в 400 блоках от игрока
assertTrue(chunkMap.entityMap.get(shahed.getId()).seenBy.contains(player.connection)); // ПАДАЕТ
```

---

### F-25: хак с манифестом `MixinConfigs` в build.gradle не работает и его комментарий фактически неверен
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `build.gradle:169-176`; `src/main/templates/META-INF/neoforge.mods.toml:37-38`
КОД:
```groovy
// NeoForge loads mixin configs from the jar manifest's "MixinConfigs" attribute.
// The neoforge.mods.toml [[mixins]] block alone was NOT honoured by NeoForge 21.1.x,
// so the manifest entry is required for ata.mixins.json (ClientChunkCacheMixin) to apply.
tasks.named('jar', Jar).configure {
    manifest {
        attributes('MixinConfigs': 'ata.mixins.json')
    }
}
```
```toml
[[mixins]]
config="ata.mixins.json"
```
ПОЧЕМУ ЭТО БАГ: утверждение в комментарии инвертировано. FML 4.0.42 (входит в NeoForge 21.1.230)
читает конфиги миксинов **исключительно** из `[[mixins]]` в `neoforge.mods.toml`.
Доказательство 1 — `net/neoforged/fml/loading/moddiscovery/ModFileParser.getMixinConfigs`
(дизассемблированный `loader-4.0.42.jar`): единственный источник — `IConfigurable.getConfigList("mixins")`,
затем `getConfigElement("config")` и `getConfigElement("requiredMods")`.
Доказательство 2 — `LoadingModList.lambda$addMixinConfigs$1` вызывает
`DeferredMixinConfigRegistration.addMixinConfig(cfg.config(), modId)` строго из `ModFile.getMixinConfigs()`.
Доказательство 3 — бинарный поиск точной UTF8-константы `MixinConfigs` (`\x00\x0cMixinConfigs`) по
всем `.class` в `loader-4.0.42.jar` и `neoforge-21.1.230-universal.jar` даёт **0 попаданий** — атрибут
манифеста нигде не читается.
Последствия: (а) манифестный атрибут — мёртвый груз; (б) комментарий — ловушка: разработчик,
поверивший ему, удалит `[[mixins]]` из toml как «неработающий», и оба клиентских миксина
(`ClientChunkCacheMixin`, `LevelRendererMixin`) перестанут применяться молча.
ТЕСТИРУЕМОСТЬ: build-check
НАБРОСОК ТЕСТА:
```python
import zipfile
for jar in ('loader-4.0.42.jar', 'neoforge-21.1.230-universal.jar'):
    z = zipfile.ZipFile(jar)
    hits = [n for n in z.namelist() if n.endswith('.class') and b'\x00\x0cMixinConfigs' in z.read(n)]
    assert hits == [], hits     # пусто → атрибут манифеста не читается
```

---

### F-26: CI собирает jar и выбрасывает его; тестов и публикации артефакта нет
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `.github/workflows/build.yml:1-30`
КОД (весь файл, целиком):
```yaml
name: Build
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout repository
        uses: actions/checkout@v4
        with:
          fetch-depth: 0
          fetch-tags: true
      - name: Setup JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4
      - name: Make Gradle wrapper executable
        run: chmod +x ./gradlew
      - name: Build with Gradle
        run: ./gradlew build
```
ПОЧЕМУ ЭТО БАГ:
* нет шага `actions/upload-artifact` → собранный `build/libs/air-tactical-arsenal-0.1.0-dev.jar`
  уничтожается вместе с раннером; проверить сборку руками невозможно;
* `./gradlew build` запускает `test`, но каталога `src/test` в проекте нет (проверено) — то есть
  «зелёный» CI не проверяет ни одной строки поведения;
* сконфигурированный в `build.gradle:74-77` run-config `gameTestServer` в CI не запускается, хотя
  `neoforge.enabledGameTestNamespaces=ata` уже настроен;
* `fetch-depth: 0` + `fetch-tags: true` бессмысленны: версия берётся статически из
  `gradle.properties:30` (`mod_version=0.1.0-dev`), git-теги нигде не читаются.
ТЕСТИРУЕМОСТЬ: build-check
НАБРОСОК ТЕСТА:
```bash
rg -n "upload-artifact|gameTestServer|runGameTestServer" .github/workflows/build.yml   # пусто
ls src/test 2>/dev/null || echo "тестов нет"
```

---

### F-27: публикуемые метаданные: dev-версия, отсутствуют logoFile / displayURL / updateJSONURL, открытый диапазон NeoForge
СЕРЬЁЗНОСТЬ: low
ФАЙЛ: `gradle.properties:30`; `src/main/templates/META-INF/neoforge.mods.toml:1-38`
КОД:
```properties
mod_version=0.1.0-dev
```
```toml
modLoader="javafml"
loaderVersion="${loader_version_range}"
license="${mod_license}"
issueTrackerURL="https://github.com/Synarsis/AirTacticalArsenal/issues"

[[mods]]
modId="${mod_id}"
version="${mod_version}"
displayName="${mod_name}"
credits="For KrutEvent"
authors="Synarsis"
description='''...'''

[[dependencies.${mod_id}]]
    modId="neoforge"
    type="required"
    versionRange="[21.1.0,)"
```
ПОЧЕМУ ЭТО БАГ:
* `mod_version=0.1.0-dev` попадает в имя jar и в `[[mods]] version` — публикация на CurseForge/Modrinth
  под суффиксом `-dev` ломает сортировку версий и правила обновления у лаунчеров;
* отсутствуют `logoFile` (мод показывается без иконки в списке модов), `displayURL` (нет ссылки
  «Homepage» — CurseForge/Modrinth его ожидают), `updateJSONURL` (Forge Update Checker не работает);
* `versionRange="[21.1.0,)"` для `neoforge` не имеет верхней границы: мод объявляет совместимость с
  21.2.x/21.3.x/21.4.x (Minecraft 1.21.2+), где API уже несовместим. Спасает только жёсткий
  `minecraft_version_range=[1.21.1]` (`gradle.properties:19`), то есть одна опечатка в другом месте
  превращается в загрузку на несовместимой версии. MDK-шаблон использует `${neo_version_range}`,
  здесь диапазон захардкожен вручную.
ТЕСТИРУЕМОСТЬ: build-check
НАБРОСОК ТЕСТА:
```bash
rg -n "logoFile|displayURL|updateJSONURL" src/main/templates/META-INF/neoforge.mods.toml   # пусто
grep mod_version gradle.properties                                                          # 0.1.0-dev
```

---

### F-28: `onTacZProjectileHit` — публичный метод-обработчик, который никто не вызывает
СЕРЬЁЗНОСТЬ: low
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/entity/ShahedEntity.java:1206-1218`
КОД:
```java
public void onTacZProjectileHit(Entity projectile, Vec3 hitPos, DamageSource source, float damage) {
    if (!TacZCompat.isTacZLoaded()) return;
    var gunId = TacZCompat.getGunId(projectile);
    var shooter = TacZCompat.getShooter(projectile);
    float finalDamage = TacZCompat.calculateDamage(damage, gunId, 0);
    String sourceName = gunId != null ? gunId.toString() : "tacz_bullet";
    takeDamage(finalDamage, shooter, hitPos, sourceName);
}
```
ПОЧЕМУ ЭТО БАГ: grep по всему `src/main/java` — единственное попадание — строка объявления. Это
остаток Forge-версии (там его звал миксин/событие TacZ). Сейчас урон от TacZ идёт только через
`checkTacZBulletCollisions` (строка 1249), а `hurt` для TacZ-пуль возвращает `false`
(`ShahedEntity.java:1515-1519`) — значит, если тиковый скан промахнётся, попадание пропадает
безвозвратно. Также мертва `TacZCompat.getAmmoId` (0 вызовов вне своего файла).
ТЕСТИРУЕМОСТЬ: build-check
НАБРОСОК ТЕСТА:
```bash
rg -n "onTacZProjectileHit|getAmmoId" src/main/java   # по 1 попаданию — только объявления
```

---

## СВОДКА ПО СЕРЬЁЗНОСТИ

| # | Серьёзность | Кратко |
|---|---|---|
| F-1  | critical | COMMON-конфиг не синхронизируется: GUI показывает клиентский баланс, сервер применяет свой |
| F-8  | critical | `preloadChunks` без границ: до 33 млн `setChunkForced`, чанки не освобождаются никогда |
| F-10 | critical | Unified Terminal обходит blacklist/whitelist — проверки зоны в пакете нет |
| F-11 | high | Искандер запускается из НЕзаряженной пусковой (условие инвертировано) |
| F-2  | high | `reload()` — no-op, команда рапортует об успехе |
| F-3  | high | `.set()` без `save()`: все правки конфига через команды теряются |
| F-4  | high | Битая зона молча выбрасывается; blacklist fail-open, whitelist fail-closed |
| F-9  | high | Пуск Шахеда без проверки терминала/дистанции/прав |
| F-13 | high | `detectionDone = true` до заполнения результатов — гонка в CreateCompat |
| F-14 | high | `registerBlockProtection` не вызывается — блоки не защищены от контрапций |
| F-15 | high | `createbigcannons` в списке → Шахед неуязвим для пушек CBC |
| F-18 | high | `ItemCraftedEvent` до списания ингредиентов → +30 заряда за краситель |
| F-20 | high | 3 звука без .ogg, 2 осиротевших .ogg, 3 неиспользуемых SoundEvent |
| F-5  | medium | Три источника истины для высоты Искандера, два мертвы |
| F-6  | medium | 6 мёртвых опций конфига, включая полностью отсутствующую проверку дальности Шахеда |
| F-12 | medium | Неограниченный `readInt()` как длина списка в декодере |
| F-16 | medium | TacZ: строковое сравнение имён классов + рефлексия без кэша в горячем пути |
| F-17 | medium | Радар классифицирует снаряды SW/VVP как самолёты (нет проверки `isVehicle`) |
| F-21 | medium | `subtitles.ata.orlan_engine` отсутствует в обоих lang-файлах |
| F-22 | medium | 16 ключей есть в ru_ru и отсутствуют в en_us |
| F-23 | medium | `shahed_terminal`, `loading_hatch` — ни в одной творческой вкладке |
| F-24 | medium | `clientTrackingRange` в чанках; дальнее сопровождение не работает |
| F-25 | medium | Манифестный хак MixinConfigs инертен, комментарий фактически неверен |
| F-26 | medium | CI без артефакта, без тестов, без gametest |
| F-7  | low | Дефолтные `forbiddenZones` — координаты чужого сервера |
| F-19 | low | Рецепт заправки показывает пустой результат в JEI/книге |
| F-27 | low | dev-версия, нет logoFile/displayURL/updateJSONURL, открытый диапазон NeoForge |
| F-28 | low | `onTacZProjectileHit`, `getAmmoId` — мёртвый код |
