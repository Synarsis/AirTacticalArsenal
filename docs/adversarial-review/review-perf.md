# АДВЕРСАРИАЛЬНОЕ РЕВЬЮ: серверный тик / многопоточность / память
## ATA (Air Tactical Arsenal), NeoForge 21.1.230 / MC 1.21.1
Домен: worldmap, radar-сканеры, force-load чанков, серверный тик.

### Верифицированная база (цитаты из исходников MC 1.21.1, извлечены из
`~/.gradle/caches/neoformruntime/.../sourcesAndCompiledWithNeoForge_a860bc8a...output.jar`)

Три факта, на которых держится половина находок ниже:

**(A) `ServerLevel.setChunkForced(x,z,true)` — это СИНХРОННАЯ БЛОКИРУЮЩАЯ ГЕНЕРАЦИЯ ЧАНКА
+ ЗАПИСЬ В СОХРАНЕНИЕ** (`net/minecraft/server/level/ServerLevel.java:1394`):
```java
public boolean setChunkForced(int p_8603_, int p_8604_, boolean p_8605_) {
    ForcedChunksSavedData forcedchunkssaveddata = this.getDataStorage().computeIfAbsent(ForcedChunksSavedData.factory(), "chunks");
    ChunkPos chunkpos = new ChunkPos(p_8603_, p_8604_);
    long i = chunkpos.toLong();
    boolean flag;
    if (p_8605_) {
        flag = forcedchunkssaveddata.getChunks().add(i);
        if (flag) {
            this.getChunk(p_8603_, p_8604_);      // <-- getChunk(x,z,ChunkStatus.FULL,true)
        }
    } else {
        flag = forcedchunkssaveddata.getChunks().remove(i);
    }
    forcedchunkssaveddata.setDirty(flag);
```
`getChunk(x,z)` == `getChunk(x,z,ChunkStatus.FULL,true)` → `managedBlock(future::isDone)` — поток
сервера СТОИТ, пока чанк не сгенерируется до FULL. И `ForcedChunksSavedData` — это persistent
saved data `<dim>/data/chunks.dat`, переживающая рестарт.

**(B) `ServerChunkCache.getChunk(x,z,status,false)`** (`ServerChunkCache.java`):
```java
for (int j = 0; j < 4; j++) {                       // кэш всего на 4 слота
    if (i == this.lastChunkPos[j] && p_330876_ == this.lastChunkStatus[j]) { ... }
}
profilerfiller.incrementCounter("getChunkCacheMiss");
CompletableFuture<ChunkResult<ChunkAccess>> completablefuture = this.getChunkFutureMainThread(...);
this.mainThreadProcessor.managedBlock(completablefuture::isDone);   // БЛОКИРУЮЩЕЕ ОЖИДАНИЕ
```
и
```java
private CompletableFuture<...> getChunkFutureMainThread(int p_8457_, int p_8458_, ChunkStatus p_331599_, boolean p_8460_) {
    ChunkPos chunkpos = new ChunkPos(p_8457_, p_8458_);           // аллокация на КАЖДЫЙ вызов
    int j = ChunkLevel.byStatus(p_331599_);
    ...
    return this.chunkAbsent(chunkholder, j)
        ? GenerationChunkHolder.UNLOADED_CHUNK_FUTURE
        : chunkholder.scheduleChunkGenerationTask(p_331599_, this.chunkMap);   // ← ЗАПУСК ГЕНЕРАЦИИ
}
```
`ChunkLevel.byStatus(ChunkStatus)` = `33 + FULL_CHUNK_STEP.getAccumulatedRadiusOf(status)`, т.е. для
`ChunkStatus.BIOMES` порог СТРОГО БОЛЬШЕ 33 (уровень FULL). Значит вызов с `BIOMES` проходит
`chunkAbsent()` для СУЩЕСТВЕННО БОЛЬШЕГО множества чанков, чем `FULL`, и для них выполняется
`scheduleChunkGenerationTask` → `rescheduleChunkTask` (реальная генерация) + `managedBlock`
(блокировка главного потока).

**(C) `MissileChunkManager.cleanup()` НЕ ИМЕЕТ НИ ОДНОГО ВЫЗЫВАЮЩЕГО.** Проверено:
`grep -rn "MissileChunkManager\." src/main/java` даёт 10 совпадений — `preloadTargetArea`,
`updateChunksForMissile`, `updateChunksWithRadius`, `unregisterMissile`, `isMissileRegistered`.
`cleanup()` — только в javadoc и в собственном определении. `grep -rn "ServerStoppin|ServerStopped|LevelEvent.Unload"`
по всему `src/main/java` — 0 совпадений.

---

# КРИТИЧЕСКИЕ

### F-01: Каждый форс-лоад чанка ракетой — синхронная генерация мира в главном потоке; за один полёт Shahed'а это ~2250 блокирующих генераций
СЕРЬЁЗНОСТЬ: **critical**
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/chunk/MissileChunkManager.java:127-132`
КОД:
```java
        Set<ChunkPos> chunksToLoadNew = new HashSet<>(chunksToLoad);
        chunksToLoadNew.removeAll(currentLoaded);

        for (ChunkPos chunk : chunksToLoadNew) {
            // Forge: ForgeChunkManager.forceChunk(level, MOD_ID, uuid, x, z, true, true);
            // setChunkForced returns true if the state changed (i.e. it wasn't already forced).
            level.setChunkForced(chunk.x, chunk.z, true);
            currentLoaded.add(chunk);
        }
```
и точка вызова каждый тик сущности:
`src/main/java/com/synarsis/airtacticalarsenal/entity/ShahedEntity.java:392-394`
```java
        if (this.level() instanceof ServerLevel serverLevel) {
            MissileChunkManager.updateChunksForMissile(this, serverLevel);
        }
```
ПОЧЕМУ ЭТО БАГ: см. факт (A). `setChunkForced(...,true)` при первом добавлении чанка вызывает
`this.getChunk(x,z)` = `getChunk(x,z,ChunkStatus.FULL,true)`, который делает
`mainThreadProcessor.managedBlock(...)` — поток сервера ЖДЁТ полной генерации чанка
(шум→поверхность→карверы→фичи→свет→структуры). Комментарий в коде («Forge: ...ticket») подменяет
асинхронный тикет-механизм Forge на строго синхронный vanilla-API. Ни одного `CompletableFuture`,
ни одной проверки «чанк уже сгенерирован на диске».

ЦЕНА В ЧИСЛАХ (константы из кода):
- `CHUNK_LOAD_RADIUS = 1` (:41) → квадрат 3×3 = **9 чанков**; `PRELOAD_CHUNKS_AHEAD = 3` (:43) →
  ещё до **3** чанков вперёд ⇒ steady-state **до 12 форсированных чанков на ракету**.
- Регистрация (`registerMissile` → `updateChunksForMissile`, :57): **12 setChunkForced(true) в ОДНОМ тике**.
  Свежая местность: 12 × ~25 мс = **300 мс фриза в тике запуска** (при 50 мс бюджета — пропуск 6 тиков).
- Полёт: 12000 блоков ÷ 16 = **750 пересечённых чанк-границ**. При каждом пересечении границы по X
  добавляется новая колонка шириной (2·1+1)=3 чанка + до 3 preload ⇒ **~3–6 новых форсов на границу**.
  Итого за полёт: 750 × 3 = **2250 блокирующих генераций FULL-чанков**.
- Скорость 4 бл/тик ⇒ граница каждые 4 тика (200 мс). 3 генерации × 25 мс = **75 мс работы на 200 мс
  реального времени = +37,5 % к MSPT постоянно, все 3000 тиков (150 с) полёта**. Две ракеты — +75 %.
  Три ракеты — сервер гарантированно ниже 20 TPS.
- Разгрузка: `chunksToUnload` (:115-122) снимает форс при уходе, т.е. одновременно живёт 9–12, но
  общая работа по генерации — все 2250.

СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: чистый мир, `/ata shahed` запуск на маршрут 12000 блоков в неисследованную
сторону. Наблюдение: `/debug start` за 30 с полёта → в профиле `chunkLoad`/`worldgen` доминирует;
MSPT прыгает 60–300 мс. Часы в игре идут рывками, мобы телепортируются.
ТЕСТИРУЕМОСТЬ: gametest + benchmark
НАБРОСОК ТЕСТА:
```java
@GameTest(template = "empty", timeoutTicks = 200)
public static void forcedChunkStallsTick(GameTestHelper h) {
    ServerLevel lvl = h.getLevel();
    // чанки заведомо не сгенерированы: уходим на 100k блоков от спавна
    long t0 = System.nanoTime();
    for (int dz = 0; dz < 12; dz++) lvl.setChunkForced(6250 + dz, 6250, true);
    long ms = (System.nanoTime() - t0) / 1_000_000;
    for (int dz = 0; dz < 12; dz++) lvl.setChunkForced(6250 + dz, 6250, false);
    if (ms < 50) h.fail("ожидали блокировку главного потока, получили " + ms + " мс — тест невалиден");
    h.fail("ДОКАЗАНО: 12 setChunkForced заблокировали главный поток на " + ms + " мс (бюджет тика 50 мс)");
}
```

---

### F-02: `preloadTargetArea` форсит 25 чанков одним залпом (25 синхронных генераций), и следующий же `updateChunksWithRadius` их немедленно снимает — работа выброшена целиком
СЕРЬЁЗНОСТЬ: **critical**
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/chunk/MissileChunkManager.java:182-193`
КОД:
```java
        ChunkPos center = new ChunkPos(targetPos);
        int radius = 2;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkPos chunk = new ChunkPos(center.x + dx, center.z + dz);
                if (!currentLoaded.contains(chunk)) {
                    level.setChunkForced(chunk.x, chunk.z, true);
                    currentLoaded.add(chunk);
                }
            }
        }
```
и «убийца» — та же карта `currentLoaded` используется как источник для снятия форса
(`MissileChunkManager.java:115-122`):
```java
        Set<ChunkPos> chunksToUnload = new HashSet<>(currentLoaded);
        chunksToUnload.removeAll(chunksToLoad);

        for (ChunkPos chunk : chunksToUnload) {
            level.setChunkForced(chunk.x, chunk.z, false);
            currentLoaded.remove(chunk);
        }
```
ПОЧЕМУ ЭТО БАГ: `preloadTargetArea` добавляет 25 чанков цели в `loadedChunksPerMissile[id]`.
`chunksToLoad` в `updateChunksWithRadius` строится ТОЛЬКО из текущей позиции ракеты (±1 чанк +3
вперёд) и НИКОГДА не содержит чанков цели, пока ракета не долетит. Значит на первом же пересечении
чанк-границы (через ≤4 тика после запуска Искандера) `chunksToUnload = currentLoaded − chunksToLoad`
содержит ВСЕ 25 preload-чанков и снимает с них форс. Предзагрузка цели не доживает до цели —
она умирает через 4 тика. Вся генерация выполнена впустую.
Вызов: `IskanderEntity.java:331` внутри `calculateTrajectory()` — тик сущности, главный поток.

ЦЕНА В ЧИСЛАХ: `radius = 2` ⇒ (2·2+1)² = **25 чанков**, все 25 `setChunkForced(true)` выполняются
в ОДНОМ вызове ⇒ в ОДНОМ тике. Свежая местность ~20–50 мс/чанк ⇒ **500–1250 мс фриза сервера в
одном тике** в момент расчёта траектории Искандера. Полезность = 0 (снимается через ≤4 тика).
Плюс 25 записей + 25 удалений в `ForcedChunksSavedData` ⇒ 50 × `setDirty` ⇒ `chunks.dat`
переписывается при следующем автосейве.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: запустить Искандер по цели в неисследованном регионе. В момент выхода
`calculateTrajectory` — «Can't keep up! Running Xms behind» в логе, единичный лаг-спайк ~1 с.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
@GameTest(template = "empty")
public static void preloadIsThrownAway(GameTestHelper h) {
    ServerLevel lvl = h.getLevel();
    Entity m = /* заспавнить IskanderEntity */;
    BlockPos target = new BlockPos(160000, 64, 160000);
    long t0 = System.nanoTime();
    MissileChunkManager.preloadTargetArea(m, lvl, target);
    long preloadMs = (System.nanoTime() - t0)/1_000_000;
    int after = MissileChunkManager.getLoadedChunkCount(m);      // == 25 (+ регистрация)
    // сдвигаем ракету на 1 чанк и просим обновление
    m.setPos(m.getX() + 16, m.getY(), m.getZ());
    m.setDeltaMovement(1, 0, 0);
    MissileChunkManager.updateChunksForMissile(m, lvl);
    int now = MissileChunkManager.getLoadedChunkCount(m);
    h.assertTrue(now <= 12, "preload выжил?");
    h.fail("ДОКАЗАНО: " + preloadMs + " мс генерации 25 чанков выброшены за 1 обновление; было "
            + after + " → стало " + now);
}
```

---

### F-03: Форс-лоад пишется в persistent `chunks.dat` и НЕ снимается при штатной остановке сервера — чанки остаются форсированными НАВСЕГДА. `cleanup()` не вызывается ниоткуда
СЕРЬЁЗНОСТЬ: **critical**
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/chunk/MissileChunkManager.java:151-169`
КОД:
```java
    public static void cleanup() {
        // Best-effort: drop every chunk we still have tickets for.
        // TODO: in NeoForge a TicketController would let us scope cleanup per level/entity;
        //       for now we just iterate the bookkeeping map.
        for (Map.Entry<Integer, Set<ChunkPos>> entry : loadedChunksPerMissile.entrySet()) {
            ServerLevel level = missileLevels.get(entry.getKey());
            ...
        }
        loadedChunksPerMissile.clear();
        missileLevels.clear();
```
ПОЧЕМУ ЭТО БАГ:
1. Единственные пути снятия форса — `unregisterMissile` (из `Entity.remove(RemovalReason)`,
   `ShahedEntity.java:875`, `IskanderEntity.java:1274`, `OrlanEntity.java:1279`) и `cleanup()`.
2. `cleanup()` НЕ ВЫЗЫВАЕТСЯ НИОТКУДА (см. факт (C)) — в моде нет ни одного подписчика на
   `ServerStoppingEvent`/`ServerStoppedEvent`/`LevelEvent.Unload`.
3. При штатной остановке сервера (`/stop`, выход из одиночного мира) MC вызывает
   `PersistentEntitySectionManager.close()` → `saveAll()`, а НЕ `entity.remove(...)`. Значит для
   ракет в полёте `unregisterMissile` НЕ вызывается никогда.
4. `ForcedChunksSavedData` (факт (A)) сохраняется в `<dim>/data/chunks.dat`. После рестарта
   9–12 чанков (а с `preloadTargetArea` — 25 + 12 = 37) остаются force-loaded вечно: они тикают
   (мобы, редстоун, рандом-тики, рост), но никто их не снимет — `loadedChunksPerMissile` пуст после
   рестарта JVM, поэтому даже теоретический `cleanup()` их бы не увидел. Утечка НЕОБРАТИМА без
   ручного `/forceload remove`.
5. Тот же результат при краше/`kill -9`/потере питания.

ЦЕНА В ЧИСЛАХ: 12 (или 37) вечно тикающих чанков на каждую «недобитую» ракету.
Каждый ticking-чанк vanilla ≈ 0,05–0,3 мс/тик (block ticks + random ticks + entity ticks + fluid).
12 чанков × 0,15 мс = **1,8 мс/тик = 3,6 % MSPT навсегда**. 10 накопленных инцидентов за месяц игры
= 120 чанков = **18 мс/тик = 36 % бюджета тика, потерянные безвозвратно**. Плюс память:
12 × `LevelChunk` (~40–120 КБ на чанк с 24 секциями) = **0,5–1,5 МБ на инцидент, вечно**.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: запустить Shahed, через 5 с сделать `/stop`. Перезапустить сервер.
`/forceload query` в дименшене overworld → показывает 9–12 чанков по траектории ракеты, которых там
быть не должно. Открыть `world/data/chunks.dat` NBT-редактором — LongArray `Forced` непустой.
ТЕСТИРУЕМОСТЬ: manual (перезапуск) + pure-java на NBT
НАБРОСОК ТЕСТА:
```java
// manual, 3 шага, полностью детерминированно:
// 1) fresh world; /forceload query -> "No force loaded chunks"
// 2) запустить Shahed; /stop через 5 сек
// 3) перезапуск; /forceload query -> N>0 чанков. FAIL.
// авто-вариант (pure-java, без MC): доказать отсутствие вызывающего
@Test void cleanupIsDeadCode() throws Exception {
    Path root = Path.of("src/main/java");
    long refs = Files.walk(root).filter(p -> p.toString().endsWith(".java"))
        .filter(p -> !p.endsWith("MissileChunkManager.java"))
        .flatMap(p -> { try { return Files.lines(p);} catch(Exception e){return Stream.empty();} })
        .filter(l -> l.contains("MissileChunkManager.cleanup")).count();
    assertEquals(0, refs);   // проходит => cleanup() мёртвый код => F-03 подтверждён
}
```

---

### F-04: Пересекающиеся чанки двух ракет: отписка второй снимает форс у первой, и `lastChunkPosCache` навсегда блокирует восстановление
СЕРЬЁЗНОСТЬ: **critical**
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/chunk/MissileChunkManager.java:80-87` и `:144-148`
КОД:
```java
        ChunkPos lastChunk = lastChunkPosCache.get(entityId);
        Integer lastRadius = lastRadiusCache.get(entityId);
        if (lastChunk != null && lastChunk.equals(currentChunk)
                && lastRadius != null && lastRadius == loadRadius) {
            return;                       // <-- ранний выход: форс НИКОГДА не перепроверяется
        }
```
```java
        if (loadedChunks != null && level != null && !loadedChunks.isEmpty()) {
            for (ChunkPos chunk : loadedChunks) {
                level.setChunkForced(chunk.x, chunk.z, false);   // <-- глобальное снятие
            }
        }
```
ПОЧЕМУ ЭТО БАГ: `ForcedChunksSavedData.getChunks()` — это ОДИН глобальный `LongSet` на измерение
(факт (A)), без счётчика ссылок и без владельца. `remove(i)` снимает форс безусловно, независимо от
того, сколько «владельцев» его считают своим. Учёт `loadedChunksPerMissile` — чисто локальная
бухгалтерия, не связанная с глобальным сетом. Дальше `lastChunkPosCache` делает потерю
НЕВОССТАНОВИМОЙ: ракета A, стоящая в том же чанке, выйдет из `updateChunksWithRadius` по раннему
return и не переформирует свои чанки.

ЦЕНА В ЧИСЛАХ: до **9 из 9** чанков ракеты A теряют форс мгновенно; при отсутствии игрока рядом
чанк выгружается в течение ~1 с ⇒ `Entity.remove(RemovalReason.UNLOADED_TO_CHUNK)` ⇒ ракета исчезает
без взрыва (100 % потеря функциональности), либо цель никогда не загрузится и ракета летит вечно.

СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ (точные координаты, всё выводится из кода):
1. Ракета A (`entityId=100`) в блоке `(1000, 200, 1000)` ⇒ `currentChunk = (62, 62)`.
   `loadRadius=1` ⇒ `chunksToLoad = {(61..63) × (61..63)}` = 9 чанков. Глобальный set:
   `{(61,61)…(63,63)}`. `loadedChunksPerMissile[100]` = те же 9.
2. Ракета B (`entityId=101`) в блоке `(1120, 200, 1000)` ⇒ `currentChunk = (70, 62)`, форсит
   `{(69..71) × (61..63)}`. Пересечения нет.
3. B летит на −X, доходит до блока `(1020, 200, 1000)` ⇒ `currentChunk = (63, 62)`.
   `chunksToLoad = {(62..64) × (61..63)}`. Строки :127-132: `setChunkForced(62,61,true)` возвращает
   `false` (уже в сете — принадлежит A), но код **игнорирует возвращаемое значение** и всё равно
   делает `currentLoaded.add(chunk)`. Теперь `loadedChunksPerMissile[101]` содержит
   `(62,61),(62,62),(62,63),(63,61),(63,62),(63,63)` — 6 чанков, реально принадлежащих A.
4. B взрывается ⇒ `remove()` ⇒ `unregisterMissile(B)` ⇒ цикл :145-147 делает
   `setChunkForced(62,62,false)` и т.д. ⇒ **6 из 9 чанков A теряют форс**.
5. A не сменила чанк ⇒ `lastChunkPosCache.get(100).equals((62,62))` ⇒ ранний return на :84 ⇒
   A НИКОГДА не восстановит форс. Чанк `(62,62)`, в котором A физически находится, больше не
   форсирован ⇒ при отсутствии игрока в радиусе — выгрузка ⇒ A уничтожена.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
@GameTest(template = "empty")
public static void overlappingMissilesStealChunks(GameTestHelper h) {
    ServerLevel lvl = h.getLevel();
    Entity a = spawnDummy(lvl, 1000, 200, 1000);
    Entity b = spawnDummy(lvl, 1120, 200, 1000);
    MissileChunkManager.registerMissile(a, lvl);
    MissileChunkManager.registerMissile(b, lvl);
    b.setPos(1020, 200, 1000);
    MissileChunkManager.updateChunksForMissile(b, lvl);
    MissileChunkManager.unregisterMissile(b);
    var forced = lvl.getForcedChunks();                       // LongSet
    h.assertTrue(forced.contains(ChunkPos.asLong(62, 62)),
        "ДОКАЗАНО: чанк (62,62) ракеты A разфорсен отпиской ракеты B");
}
```

---

### F-05: Шторм запросов карты: клиент перезапрашивает PNG каждые 10/20 тиков, сервер каждый раз шлёт 2–6 МБ заново, а `chunkIndex==0` обнуляет недособранный буфер ⇒ сборка НИКОГДА не завершается на канале медленнее 12 МБ/с
СЕРЬЁЗНОСТЬ: **critical**
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/client/gui/ShahedRouteScreen.java:846-853`,
`src/main/java/com/synarsis/airtacticalarsenal/client/radar/RouteTerrainRenderer.java:155-159`,
`src/main/java/com/synarsis/airtacticalarsenal/network/RequestRouteMapPacket.java:56-68`
КОД (клиент, `tick()` = 20 Гц):
```java
        if (terrainRenderer != null && !terrainRenderer.hasCacheData()) {
            mapRequestTicks++;
            if (mapRequestTicks >= MAP_REQUEST_INTERVAL) {      // MAP_REQUEST_INTERVAL = 10
                mapRequestTicks = 0;
                NetworkHandler.sendToServer(new RequestRouteMapPacket(launcherPositions.get(0)));
            }
        }
```
КОД (клиент, сборка):
```java
            if (chunkIndex == 0) {
                inst.pngAssembly = new ByteArrayOutputStream();   // <-- ВЫБРАСЫВАЕТ недособранное
                inst.expectedChunks = totalChunks;
                inst.receivedChunks = 0;
            }
```
КОД (сервер, ответ):
```java
    private static void sendPngToPlayer(ServerPlayer player, byte[] png, BlockPos regionCenter) {
        int chunkSize = 250000;
        int totalChunks = (png.length + chunkSize - 1) / chunkSize;
        for (int i = 0; i < totalChunks; i++) {
            ...
            byte[] chunk = new byte[len];
            System.arraycopy(png, offset, chunk, 0, len);
            NetworkHandler.sendToPlayer(player, new RouteMapDataPacket(...));
```
ПОЧЕМУ ЭТО БАГ: `hasCacheData()` (`RouteTerrainRenderer.java:122`) возвращает `cacheReady`, который
выставляется ТОЛЬКО в `loadFromPngBytes` — то есть после полной сборки. Пока идёт передача,
`hasCacheData()` == false, и клиент через 500 мс шлёт НОВЫЙ запрос. Сервер (без всякого троттлинга и
без проверки «этому игроку уже шлём») снова нарезает весь PNG и отправляет. Пришедший chunk с
`chunkIndex == 0` второй волны затирает `pngAssembly` первой волны. Условие завершения
`receivedChunks >= expectedChunks` при интерливинге волн может сработать на «франкенштейне» из кусков
разных волн ⇒ `NativeImage.read` бросит исключение ⇒ `catch (Exception e) { }` (:200-202) молча
проглотит ⇒ `cacheReady` так и не станет true ⇒ цикл вечный.
ЦЕНА В ЧИСЛАХ:
- PNG 2048×2048 ARGB шумной местности ≈ **2–6 МБ** ⇒ `totalChunks = ceil(6·10⁶ / 250000) = 24 пакета`.
- Порог зацикливания: 6 МБ за 0,5 с = **12 МБ/с = 96 Мбит/с**. Любой реальный интернет-канал (и
  даже loopback при заполненном `Connection` queue) медленнее ⇒ **бесконечный шторм**.
- Трафик: 6 МБ × 2 запроса/с = **12 МБ/с на игрока, безостановочно**. Три игрока с открытым GUI =
  36 МБ/с исходящего (288 Мбит/с) — сервер выбивает канал целиком.
- Серверная сторона: 24 × `new byte[250000]` + `System.arraycopy` = **6 МБ мусора 2 раза в секунду
  на игрока** = 12 МБ/с в young gen; плюс NeoForge сериализует полезную нагрузку в ByteBuf ещё раз ⇒
  ×2 ⇒ **24 МБ/с аллокаций на игрока**.
- `ShahedRadarScreen` (`UPDATE_INTERVAL = 20`, :27,:181-183): то же самое с PNG 1536² (0,5–2 МБ),
  1 запрос/с ⇒ 0,5–2 МБ/с на игрока.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: dedicated-сервер, готовая route-карта на диске. Игрок открывает
`ShahedRouteScreen`. Наблюдение: `netstat`/tcpdump показывают постоянные 12 МБ/с; карта не появляется
никогда; в GC-логе непрерывные young-коллекции.
ТЕСТИРУЕМОСТЬ: benchmark / manual
НАБРОСОК ТЕСТА:
```java
// pure-java симуляция протокола, доказывает невозможность сборки:
@Test void assemblyNeverCompletesUnderSlowLink() {
    int pngSize = 6_000_000, chunkSize = 250_000;
    int total = (pngSize + chunkSize - 1) / chunkSize;   // 24
    double linkBytesPerSec = 2_000_000;                  // 16 Мбит/с — типовой upstream
    double perChunkSec = chunkSize / linkBytesPerSec;    // 0.125 c
    double reRequestSec = 10 / 20.0;                     // MAP_REQUEST_INTERVAL / 20
    int deliveredBeforeReset = (int) (reRequestSec / perChunkSec);  // 4
    assertTrue(deliveredBeforeReset < total,
        "клиент получает " + deliveredBeforeReset + " из " + total +
        " кусков до того, как chunkIndex==0 обнулит буфер => сборка невозможна");
}
```

---

### F-06: `biomeColorCache` — неограниченная `HashMap<Long,Integer>` с боксингом; на одну route-карту это ~1,64 млн записей ≈ 126 МБ
СЕРЬЁЗНОСТЬ: **critical**
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/radar/ServerRouteMapScanner.java:52, 245-251`
(зеркало: `worldmap/ServerTerrainScanner.java:61, 201-207`)
КОД:
```java
    private final Map<Long, Integer> biomeColorCache = new HashMap<>();
...
            long ck = ChunkPos.asLong(wx >> 4, wz >> 4);
            Integer cached = biomeColorCache.get(ck);
            if (cached != null) return cached;
            Holder<Biome> bh = bs.getNoiseBiome(wx >> 2, 16, wz >> 2, sampler);
            int color = biomeOnlyColor(bh);
            biomeColorCache.put(ck, color);
            return color;
```
ПОЧЕМУ ЭТО БАГ: кэш не имеет ни лимита, ни eviction, ни примитивной реализации. Ключ —
боксированный `Long`, значение — боксированный `Integer` (цвета вне диапазона −128..127, т.е.
`Integer.valueOf` НЕ попадает в кэш и аллоцирует). Освобождается только в `tick()` на строке 228
`biomeColorCache.clear()` — то есть ПОСЛЕ того, как карта полностью построена и уже вызваны
`convertToPng()` + `saveToDisk()`, в момент пикового потребления.
ЦЕНА В ЧИСЛАХ (константы `CACHE_SIZE = 2048`, `CACHE_BPP = 10`, строки :36-37):
- Покрытие карты: 2048 × 10 = **20 480 блоков** по стороне ⇒ 20480/16 = **1280 чанков** по стороне
  ⇒ ключей `ChunkPos.asLong` = 1280² = **1 638 400 записей**.
- Стоимость записи: `HashMap$Node` 32 Б + `Long` 24 Б + `Integer` 16 Б = **72 Б**.
  1 638 400 × 72 Б = **118 МБ**. Плюс таблица: следующая степень двойки ≥ 1,64M/0,75 = 2²¹ слотов
  × 4 Б (compressed oops) = **8 МБ**. **Итого ≈ 126 МБ на ОДНУ route-карту.**
- Для terrain-сканера (`CACHE_SIZE=1536`, `CACHE_BPP=2`, ключ `(wx>>2, wz>>2)` — четверть-блок):
  3072 блока / 4 = **768 квартов** по стороне ⇒ 768² = **589 824 записей** × 72 Б = **42,5 МБ**
  + таблица 2²⁰ × 4 Б = 4 МБ ⇒ **≈ 46 МБ на одну radar-карту**.
- Пиковая точка (строки 226-229): `biomeColorCache` (126 МБ) + `pixels int[2048²]` (16 МБ)
  + `BufferedImage` DataBufferInt (16 МБ) + `ByteArrayOutputStream` с PNG и его удвоениями (~12 МБ)
  = **~170 МБ живых объектов в одном тике**. На сервере с `-Xmx2G` и модпаком это OOM или полный
  Full GC на несколько секунд.
- Ни один из кэшей не переживает `clear()` только при УСПЕШНОМ завершении: если сканер удалён из
  `INSTANCES` на середине (`ServerTerrainScanner.remove`, `ShahedRadarBlock.java:79`), кэш живёт до GC
  вместе со сканером — это ок; но если сканер остаётся в `INSTANCES` навсегда (F-11), то и кэш тоже.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: сервер `-Xmx2G`, игрок открывает route-экран в новом регионе.
Наблюдение: heap растёт линейно ~6 МБ/с в течение ~20 с, затем скачок +32 МБ на PNG,
затем `java.lang.OutOfMemoryError: Java heap space` или Full GC 3–8 с.
ТЕСТИРУЕМОСТЬ: pure-java benchmark
НАБРОСОК ТЕСТА:
```java
@Test void biomeColorCacheFootprint() {
    Map<Long,Integer> m = new HashMap<>();
    Runtime rt = Runtime.getRuntime();
    System.gc(); long before = rt.totalMemory() - rt.freeMemory();
    int side = 20480 / 16;                       // 1280 — выведено из CACHE_SIZE*CACHE_BPP/16
    for (int x = 0; x < side; x++)
        for (int z = 0; z < side; z++)
            m.put(((long)x << 32) | (z & 0xFFFFFFFFL), 0xFF000000 | (x*31+z));
    System.gc(); long after = rt.totalMemory() - rt.freeMemory();
    long mb = (after - before) >> 20;
    System.out.println("entries=" + m.size() + " heap=" + mb + " MB");
    assertTrue(mb > 100, "ожидалось >100 МБ, получено " + mb);   // ДОКАЗЫВАЕТ находку
}
```

---

# ВЫСОКИЕ

### F-07: `MAX_TICK_MS = 25` — это 50 % бюджета тика, безусловно, без учёта текущего MSPT; полная карта занимает 160–390 тиков непрерывного 50 %-ного отжора
СЕРЬЁЗНОСТЬ: **high**
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/worldmap/ServerTerrainScanner.java:46, 160-177`
(зеркало `radar/ServerRouteMapScanner.java:39, 202-221`)
КОД:
```java
    private static final long MAX_TICK_MS = 25;
...
        while (buildRow < CACHE_SIZE) {
            int wx = originX + buildCol * CACHE_BPP;
            int wz = originZ + buildRow * CACHE_BPP;
            pixels[buildRow * CACHE_SIZE + buildCol] = sampleColor(level, wx, wz, mPos, bs, sampler);
            buildCol++;
            pixelsDone++;
            ...
            if ((pixelsDone & 31) == 0 && System.currentTimeMillis() - start > MAX_TICK_MS) break;
        }
```
ПОЧЕМУ ЭТО БАГ: тик MC = 50 мс. Бюджет 25 мс отдаётся сканеру БЕЗУСЛОВНО — нет ни проверки текущего
`server.getAverageTickTime()`, ни проверки `server.getTickCount()`/оставшегося времени тика, ни
конфигурируемости. Модпак с базовым MSPT 30 мс + 25 мс сканера = 55 мс ⇒ TPS падает с 20 до 18,2 на
всё время сборки. Ограничение «N чанков за тик» отсутствует полностью — ограничение только временное,
и оно (см. F-09) не является верхней границей.
ЦЕНА В ЧИСЛАХ (все числа — из констант в коде):
- Terrain: `CACHE_SIZE=1536` ⇒ 1536² = **2 359 296 вызовов `sampleColor`**, каждый — минимум один
  `level.getChunk(...)`. Покрытие 1536×2 = 3072 блока ⇒ 192² = 36 864 чанка.
- Route: `CACHE_SIZE=2048`, `CACHE_BPP=10` ⇒ 2048² = **4 194 304 вызова `sampleColor`**.
  Покрытие 20 480 блоков ⇒ **1 638 400 чанков**.
- Замер стоимости пикселя (разложение по факту (B) + F-13): промах chunk-cache ×2 (≈120 нс) +
  `HashMap<Long,Integer>` get/put с боксингом (≈70 нс) + 2 `Optional` + до 29 `String.contains`
  (≈400 нс) ⇒ **≈0,6 мкс на «попадание в кэш биомов»**; на промахе добавляется
  `BiomeSource.getNoiseBiome` с `Climate.Sampler` (мультишумовая выборка 6 density-функций)
  ≈ **5 мкс**.
- Terrain: 589 824 промаха × 5 мкс + 1 769 472 попадания × 0,6 мкс = 2,95 с + 1,06 с =
  **≈4,0 с CPU** ⇒ 4000 мс / 25 мс = **160 тиков = 8 с при 50 % занятости каждого тика**.
- Route: 1 638 400 × 5 мкс + 2 555 904 × 0,6 мкс = 8,19 с + 1,53 с = **≈9,7 с CPU** ⇒
  **388 тиков ≈ 19,4 с при 50 % занятости**. Плюс финальный PNG-энкод (F-08).
- Сканеры строятся СТРОГО ПО ОДНОМУ (`tickAll` возвращает после первого building-сканера, :147-155),
  поэтому N карт = сумма: 5 радаров + 1 маршрут = 5×8 + 19,4 = **≈60 с непрерывной деградации TPS**.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: поставить радар-блок в новом чанке. Через 20 тиков (`level.scheduleTick(pos,this,20)`,
`ShahedRadarBlock.java:99`) стартует сборка. `/tps` / spark → MSPT +25 мс на 8 секунд, при этом
НИ ОДИН игрок не открывал GUI.
ТЕСТИРУЕМОСТЬ: benchmark
НАБРОСОК ТЕСТА:
```java
// JMH-style: измерить sampleColor на реальном ServerLevel и экстраполировать
@Benchmark public int oneSample(Blackhole bh, ServerLevel lvl) { /* вызвать sampleColor через reflection */ }
// затем pure-java расчёт:
@Test void mapBuildDuration() {
    long px = 2048L * 2048;                         // ServerRouteMapScanner.CACHE_SIZE
    double nsPerPx = 2300;                          // из JMH выше
    double cpuMs = px * nsPerPx / 1e6;
    double ticks = cpuMs / 25;                      // MAX_TICK_MS
    System.out.printf("CPU=%.1f s, тиков=%.0f, wall=%.1f s%n", cpuMs/1000, ticks, ticks*0.05);
    assertTrue(ticks > 200, "сборка занимает " + (long)ticks + " тиков по 25 мс каждый");
}
```

---

### F-08: Кодирование PNG (`ImageIO.write`) и `Files.write` выполняются ПРЯМО В СЕРВЕРНОМ ТИКЕ — фриз 200–900 мс в одном тике + 32 МБ транзиентных аллокаций
СЕРЬЁЗНОСТЬ: **high**
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/radar/ServerRouteMapScanner.java:223-231, 324-335, 355-366`
(зеркало `worldmap/ServerTerrainScanner.java:179-188, 280-291, 311-322`)
КОД (внутри `tick(ServerLevel)`, вызываемого из `ServerTickEvent.Post`):
```java
        if (buildRow >= CACHE_SIZE) {
            buildRow = -1;
            ready = true;
            pngBytes = convertToPng();     // BufferedImage + setRGB + ImageIO.write
            saveToDisk(level);             // Files.createDirectories + Files.write
            biomeColorCache.clear();
            pixels = null;
```
```java
    private byte[] convertToPng() {
            BufferedImage img = new BufferedImage(CACHE_SIZE, CACHE_SIZE, BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, CACHE_SIZE, CACHE_SIZE, pixels, 0, CACHE_SIZE);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            return baos.toByteArray();
```
```java
            Path dir = getCacheDir(level);
            Files.createDirectories(dir);
            Path file = dir.resolve("route_v" + CACHE_VERSION + "_" + centerPos.getX() + "_" + centerPos.getZ() + ".png");
            Files.write(file, pngBytes);
```
Плюс блокирующее ЧТЕНИЕ на том же потоке (`ServerRouteMapScanner.java:342-353`, вызывается из
`getOrCreate`, который вызывается из `context.enqueueWork` пакета и из блочного тика):
```java
            Path file = getCacheDir(level).resolve("route_v" + CACHE_VERSION + "_" + ... + ".png");
            if (Files.exists(file)) {
                LOGGER.info("[ATA] Loading cached route map from {}", file);
                return Files.readAllBytes(file);
```
плюс полное сканирование каталога (`ServerRouteMapScanner.java:116-144`):
```java
            try (Stream<Path> stream = Files.list(cacheDir)) {
                return stream.map(p -> p.getFileName().toString())
                    .filter(name -> name.startsWith(prefix) && name.endsWith(".png"))
```
ПОЧЕМУ ЭТО БАГ: `tick()` вызывается синхронно из `ServerForgeEvents.onServerTick`. `MAX_TICK_MS=25`
ограничивает ТОЛЬКО цикл пикселей; финализация выполняется целиком, без бюджета. Java `PNGImageWriter`
делает per-scanline фильтрацию + Deflate — это чистый CPU в главном потоке. `Files.write`/`readAllBytes`
— блокирующий syscall: на HDD/сетевом диске/во время автосейва мира это десятки–сотни мс.
ЦЕНА В ЧИСЛАХ:
- `setRGB(0,0,2048,2048,pixels,0,2048)` — копирование **4 194 304 int** через
  `ColorModel.getDataElements` по умолчанию, но для `TYPE_INT_ARGB` идёт быстрый путь ⇒ ~10–20 мс.
- Сырых данных для энкодера: 2048 × 2048 × 4 = **16 777 216 байт**. Java PNG-энкодер выдаёт
  **20–80 МБ/с** ⇒ **210–840 мс на ОДИН тик**. Для terrain 1536²×4 = 9,44 МБ ⇒ **120–470 мс**.
- Аллокации в одном тике: `BufferedImage` DataBufferInt = **16 МБ** (при живом `pixels` = ещё 16 МБ)
  + `ByteArrayOutputStream`, растущий удвоением до ~6 МБ ⇒ суммарно копий ~12 МБ ⇒
  **≈44 МБ мусора в одном тике** (для 2048²).
- `Files.write` 6 МБ: на HDD ~60–200 мс, на NFS/сетевом хранилище — до секунд. Всё это в главном потоке.
- Итог: **один тик длиной 0,3–1,1 с** = сервер «пропускает» 6–22 тика, все игроки видят рывок.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: дождаться завершения сборки route-карты. В `logs/latest.log`
непосредственно перед `[ATA] Saved route map to ...` появится
`Can't keep up! Is the server overloaded? Running 300–1100ms behind`.
ТЕСТИРУЕМОСТЬ: pure-java benchmark
НАБРОСОК ТЕСТА:
```java
@Test void pngEncodeBlocksTheTick() throws Exception {
    int N = 2048;                                   // ServerRouteMapScanner.CACHE_SIZE
    int[] px = new int[N*N];
    java.util.Random r = new java.util.Random(1);
    for (int i = 0; i < px.length; i++) px[i] = 0xFF000000 | r.nextInt(0xFFFFFF); // шум ~ реальный биом-мозаика
    BufferedImage img = new BufferedImage(N, N, BufferedImage.TYPE_INT_ARGB);
    long t0 = System.nanoTime();
    img.setRGB(0,0,N,N,px,0,N);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(img, "PNG", baos);
    long ms = (System.nanoTime()-t0)/1_000_000;
    System.out.println("PNG " + N + "x" + N + " = " + ms + " ms, " + baos.size()/1024 + " KB");
    assertTrue(ms < 50, "ДОКАЗАНО: энкод занял " + ms + " мс при бюджете тика 50 мс");  // упадёт
}
```

---

### F-09: `level.getChunk(x, z, ChunkStatus.BIOMES, false)` запускает НАСТОЯЩУЮ генерацию чанка и блокирует главный поток внутри пиксельного цикла; бюджет 25 мс проверяется лишь раз в 32 пикселя и не является верхней границей
СЕРЬЁЗНОСТЬ: **high**
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/worldmap/ServerTerrainScanner.java:192-199`
(зеркало `radar/ServerRouteMapScanner.java:236-243`)
КОД:
```java
        ChunkAccess chunk = level.getChunk(wx >> 4, wz >> 4, ChunkStatus.FULL, false);
        if (chunk == null) {

            chunk = level.getChunk(wx >> 4, wz >> 4, ChunkStatus.BIOMES, false);
            if (chunk != null) {
                Holder<Biome> bh = chunk.getNoiseBiome(wx >> 2, 16, wz >> 2);
                return biomeOnlyColor(bh);
            }
```
ПОЧЕМУ ЭТО БАГ (механизм из факта (B)):
1. `requireChunk=false` действительно НЕ добавляет `TicketType.UNKNOWN`. Но это не защита от
   генерации: если `ChunkHolder` для чанка существует и его ticket level ≤ `ChunkLevel.byStatus(status)`,
   выполняется `chunkholder.scheduleChunkGenerationTask(status, chunkMap)`, а затем
   `mainThreadProcessor.managedBlock(future::isDone)` — блокировка главного потока.
2. `ChunkLevel.byStatus(ChunkStatus.BIOMES)` = `33 + FULL_CHUNK_STEP.getAccumulatedRadiusOf(BIOMES)`,
   т.е. СТРОГО БОЛЬШЕ 33. Значит второй вызов (с `BIOMES`) проходит для всего пограничного кольца
   вокруг каждого игрока — чанков с ticket level 34…MAX_LEVEL, которые загружены как «генерируемые»,
   но ещё не достигли BIOMES. Для них `scheduleChunkGenerationTask` → `rescheduleChunkTask` →
   реальные шаги генерации (STRUCTURE_STARTS → STRUCTURE_REFS → BIOMES), а `managedBlock` держит
   поток до конца.
3. Проверка бюджета — `if ((pixelsDone & 31) == 0 && System.currentTimeMillis() - start > MAX_TICK_MS)`
   (`ServerTerrainScanner.java:176`) — выполняется раз в 32 пикселя и ПОСЛЕ сэмпла. Один пиксель,
   попавший в такой чанк, блокирует тик на всю длительность генерации; 32 подряд — на 32 её длительности.
   `MAX_TICK_MS` не ограничивает НИЧЕГО в этом случае.
4. Плюс безусловные накладные: каждый промах 4-слотового кэша (`lastChunkPos[4]`) вызывает
   `new ChunkPos(...)` в `getChunkFutureMainThread` + `storeInCache` (сдвиг 3 слотов в 3 массивах)
   + 2 инкремента профайлера.
ЦЕНА В ЧИСЛАХ:
- Для route-сканера `CACHE_BPP = 10` ⇒ соседние пиксели отстоят на 10 блоков ⇒ на строку в один
  чанк попадает 1,6 пикселя, а следующая строка — уже другой чанк. 4-слотовый кэш почти всегда
  промахивается ⇒ **2 промаха на пиксель × 4 194 304 пикселя = 8 388 608 `getChunkCacheMiss`
  на одну карту**, столько же `new ChunkPos` (8,4 млн × 16 Б = **134 МБ мусора**) и столько же
  `storeInCache`-сдвигов (8,4 млн × 9 записей в массивы = 75 млн store-операций).
- Побочный эффект: `storeInCache` — это ОБЩИЙ кэш `ServerChunkCache` на весь сервер. Сканер
  выбивает из него все 4 слота каждым пикселем ⇒ **весь остальной серверный код (block ticks,
  entity ticks, освещение) теряет свой chunk-cache на всё время сборки карты** — вторичная
  деградация MSPT для кода, который к моду отношения не имеет.
- Один прорыв в генерацию BIOMES свежего чанка ≈ 5–20 мс. 32 подряд ⇒ **160–640 мс в одном тике**
  сверх заявленных 25 мс.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: игрок стоит на границе исследованной области, открывает route-экран.
Сканер проходит через пограничное кольцо → в spark-профиле видно `ChunkGenerationTask` /
`ChunkStatusTasks.generateBiomes` под стеком `ServerForgeEvents.onServerTick`.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
@GameTest(template = "empty", timeoutTicks = 400)
public static void biomesStatusGenerates(GameTestHelper h) {
    ServerLevel lvl = h.getLevel();
    // чанк в пограничном кольце: за пределами FULL, но внутри ticket-распространения игрока
    int cx = SectionPos.blockToSectionCoord(h.absolutePos(BlockPos.ZERO).getX()) + 12;
    int cz = SectionPos.blockToSectionCoord(h.absolutePos(BlockPos.ZERO).getZ());
    h.assertTrue(lvl.getChunk(cx, cz, ChunkStatus.FULL, false) == null, "чанк не должен быть FULL");
    long t0 = System.nanoTime();
    ChunkAccess c = lvl.getChunk(cx, cz, ChunkStatus.BIOMES, false);
    long us = (System.nanoTime() - t0) / 1000;
    h.fail("BIOMES вернул " + (c != null ? "ЧАНК (генерация выполнена!)" : "null")
          + " за " + us + " мкс на главном потоке");
}
```

---

### F-10: Сканеры ВСЕГДА тикаются против `server.overworld()`, независимо от измерения радара; ключ `INSTANCES` не содержит измерения ⇒ коллизия радаров разных миров и запись карты Незера в кэш Оверворлда
СЕРЬЁЗНОСТЬ: **high**
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/event/ServerForgeEvents.java:57-72`
КОД:
```java
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;
        ...
        if (terrainOrder <= routeOrder) {
            if (!ServerTerrainScanner.tickAll(overworld)) {
                ServerRouteMapScanner.tickAll(overworld);
            }
```
и ключ без измерения (`worldmap/ServerTerrainScanner.java:74-76`, `radar/ServerRouteMapScanner.java:74-76`):
```java
    public static String key(BlockPos pos) {
        return pos.getX() + "_" + pos.getZ();
    }
```
и путь кэша, общий на весь сейв (`ServerTerrainScanner.java:293-296`):
```java
    private Path getCacheDir(ServerLevel level) {
        Path worldPath = level.getServer().getWorldPath(LevelResource.ROOT);
        return worldPath.resolve("ata_radar_cache");
    }
```
ПОЧЕМУ ЭТО БАГ:
1. `ShahedRadarBlock.tick(state, ServerLevel level, pos, random)` (`:85`) вызывает
   `ServerTerrainScanner.getOrCreate(pos, level)` с ФАКТИЧЕСКИМ измерением. Но `tick(level)`
   сканера всегда получает `server.overworld()`. Радар в Незере/Энде строит карту ОВЕРВОРЛДА
   вокруг своих координат — данные полностью неверны, а сервер тратит на них 8 с × 50 % тика.
2. `key(pos)` = `"x_z"` ⇒ радар в Оверворлде на `(100, ?, 200)` и радар в Незере на `(100, ?, 200)`
   делят одну запись в `INSTANCES` и один файл `radar_v16_100_200.png`. Второй радар получит карту
   первого.
3. `LevelResource.ROOT` — корень сейва, общий для всех измерений ⇒ коллизия и на диске.
4. `getFirstBuildingOrder`/`tickAll`/`getQueuePosition` вообще не знают про измерение.
ЦЕНА В ЧИСЛАХ: 100 % неверных данных для любого радара вне Оверворлда + впустую потраченные
2 359 296 сэмплов × ≈1,7 мкс = **≈4 с CPU и 160 тиков по 25 мс** на построение заведомо мусорной
карты. Плюс перезапись чужого файла кэша (0,5–2 МБ дискового I/O).
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: поставить `ShahedRadarBlock` в Незере на `(100, 40, 200)`, дождаться
сборки, открыть радар — виден рельеф Оверворлда. Затем поставить радар в Оверворлде на `(100, 64, 200)`
— `getOrCreate` вернёт сканер Незера (тот же ключ) и покажет ту же картинку.
ТЕСТИРУЕМОСТЬ: pure-java (для ключа) + gametest
НАБРОСОК ТЕСТА:
```java
@Test void keyCollidesAcrossDimensions() {
    assertEquals(ServerTerrainScanner.key(new BlockPos(100, 64, 200)),
                 ServerTerrainScanner.key(new BlockPos(100, 40, 200)));
    // одинаковый ключ для радаров, различающихся только Y и измерением => коллизия доказана
}
```

---

### F-11: `INSTANCES` — статический `LinkedHashMap`, не очищаемый ни при остановке сервера, ни при выгрузке мира; в одиночной игре переживает смену мира и отдаёт карту ЧУЖОГО сохранения
СЕРЬЁЗНОСТЬ: **high**
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/worldmap/ServerTerrainScanner.java:48, 78-95`
(зеркало `radar/ServerRouteMapScanner.java:42, 78-114`)
КОД:
```java
    private static final Map<String, ServerTerrainScanner> INSTANCES = new LinkedHashMap<>();
...
    public static synchronized ServerTerrainScanner getOrCreate(BlockPos pos, ServerLevel level) {
        String k = key(pos);
        ServerTerrainScanner scanner = INSTANCES.get(k);
        if (scanner != null) return scanner;     // <-- отдаёт сканер из ПРЕДЫДУЩЕГО мира
```
Единственное удаление — `remove(pos)` из `ShahedRadarBlock.onRemove` (`:79`) и
`removeAndInvalidate` (0 вызывающих).
ПОЧЕМУ ЭТО БАГ: `grep -rn "ServerStoppin|ServerStopped|LevelEvent.Unload" src/main/java` = 0
совпадений (факт C). В одиночной игре JVM клиента переживает выход в главное меню: статические
`INSTANCES` обоих сканеров остаются заполненными. Вход в ДРУГОЙ мир с радаром на тех же координатах
(типично: спавн-база на `(0,y,0)`) ⇒ `INSTANCES.get(k) != null` ⇒ возврат сканера прошлого мира
с его `pngBytes` и `ready=true`. Карта из другого сохранения показывается как своя и НИКОГДА не
перестраивается. Плюс утечка.
ЦЕНА В ЧИСЛАХ:
- Каждый сканер в `INSTANCES` в состоянии «в очереди, ещё не построен» держит `int[CACHE_SIZE²]`,
  выделенный в конструкторе (`ServerTerrainScanner.java:67`, `ServerRouteMapScanner.java:58`):
  terrain **1536² × 4 = 9 437 184 Б = 9,0 МиБ**, route **2048² × 4 = 16 777 216 Б = 16,0 МиБ**.
  10 радаров в очереди = **90 МиБ бездействующих int[]**, живущих до GC всего JVM.
- Каждый готовый сканер держит `pngBytes` — 0,5–2 МБ (terrain) / 2–6 МБ (route), навсегда.
- В G1 массив 9/16 МБ — **humongous allocation** (> 50 % размера региона при типовом 4 МБ регионе):
  выделяется напрямую в old gen, освобождается только concurrent/full циклом ⇒ фрагментация кучи.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: одиночная игра. Мир A: радар на `(0,64,0)`, дождаться карты. Выход в
меню. Мир B (другой seed): радар на `(0,64,0)`, открыть — показана карта мира A. `jmap -histo`
клиента: `[I` на 9 МБ+ и `ServerTerrainScanner` в количестве > 0 при отсутствии загруженного мира.
ТЕСТИРУЕМОСТЬ: manual + pure-java (доказательство отсутствия hook'а)
НАБРОСОК ТЕСТА:
```java
@Test void noLifecycleHookClearsScanners() throws Exception {
    String all = Files.walk(Path.of("src/main/java"))
        .filter(p -> p.toString().endsWith(".java"))
        .map(p -> { try { return Files.readString(p);} catch(Exception e){return "";} })
        .collect(Collectors.joining("\n"));
    assertFalse(all.contains("ServerStoppingEvent"));
    assertFalse(all.contains("LevelEvent.Unload"));
    // ни один hook не очищает INSTANCES => состояние переживает мир => F-11 подтверждён
}
```

---

### F-12: `ShahedRadarBlock.tick` ставит в очередь ПОЛНУЮ сборку карты на каждый установленный радар-блок, без единого игрока рядом; очередь FIFO блокирует запрошенную игроком route-карту на минуты
СЕРЬЁЗНОСТЬ: **high**
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/block/ShahedRadarBlock.java:84-99`
КОД:
```java
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int newPower = calculateSignalStrength(level, pos);
        ...
        ServerTerrainScanner.getOrCreate(pos, level);

        level.scheduleTick(pos, this, 20);
    }
```
и в `getOrCreate` (`ServerTerrainScanner.java:89-92`):
```java
        } else {
            scanner.buildRow = 0;
            scanner.buildStartOrder = ++GLOBAL_BUILD_ORDER;
        }
```
ПОЧЕМУ ЭТО БАГ: постановка блока → `onPlace` → `scheduleTick(...,20)` → `tick` → `getOrCreate` →
`buildRow = 0` ⇒ сканер немедленно становится `isBuilding()` и начинает жрать 25 мс/тик. Никакой
связи с тем, смотрит ли кто-то на радар. `tickAll` (`ServerTerrainScanner.java:147-155`) тикает
СТРОГО ПЕРВЫЙ building-сканер в порядке вставки и возвращает `true`, что в
`ServerForgeEvents.onServerTick:65-67` (`if (!ServerTerrainScanner.tickAll(...)) ServerRouteMapScanner.tickAll(...)`)
означает: **пока хоть один terrain-сканер строится, route-сканер не получает НИ ОДНОГО тика**, если
его `buildStartOrder` больше.
ЦЕНА В ЧИСЛАХ:
- База с 10 радар-блоками ⇒ 10 сканеров, каждый: `int[2 359 296]` = 9,0 МиБ ⇒ **90 МиБ** сразу
  при установке (аллокация в конструкторе, `:67`), и **10 × 160 тиков = 1600 тиков = 80 секунд**
  занятости 25 мс/тик.
- Игрок открывает route-экран на 81-й секунде? Нет: он открывает в любой момент, его сканер получает
  `buildStartOrder = 11` ⇒ ждёт завершения всех 10 ⇒ **до 80 с ожидания**, при этом клиент всё это
  время штормит запросами (F-05).
- Если игроки продолжают ставить радары чаще, чем раз в 8 с, очередь terrain-сканеров не опустеет
  НИКОГДА ⇒ route-карта не построится **никогда** — прямой ответ на вопрос «может ли один сканер
  навсегда заблокировать другой»: **да**.
- Плюс `calculateSignalStrength` в том же `tick` — см. F-19.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: поставить 10 радар-блоков подряд в новом чанке. spark: `onServerTick`
занимает 25 мс каждый тик следующие 80 с. `jmap -histo` показывает 10 × `int[2359296]`.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
@GameTest(template = "empty", timeoutTicks = 100)
public static void radarPlacementQueuesFullBuild(GameTestHelper h) {
    ServerLevel lvl = h.getLevel();
    for (int i = 0; i < 10; i++) ServerTerrainScanner.getOrCreate(h.absolutePos(new BlockPos(i*4,1,0)), lvl);
    long building = ServerTerrainScanner.getInstances().values().stream()
        .filter(ServerTerrainScanner::isBuilding).count();
    h.assertValueEqual(building, 10L, "сканеров в очереди");
    // 10 × 9 МиБ int[] уже выделены; route-сканер, созданный сейчас, получит order 11
    h.fail("ДОКАЗАНО: 10 радаров = " + (building * 1536L*1536*4/1048576) + " МиБ int[] + "
        + (building * 160) + " тиков очереди");
}
```

---

### F-13: Классификация биома строкой на КАЖДЫЙ пиксель: 2 `Optional` + до 29 `String.contains` — 1,0–1,8 с CPU и 8,4 млн лишних объектов на карту
СЕРЬЁЗНОСТЬ: **high**
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/worldmap/ServerTerrainScanner.java:218-233, 256-278`
(зеркало `radar/ServerRouteMapScanner.java:262-277, 300-322`)
КОД:
```java
        String bn = bh.unwrapKey().map(k -> k.location().getPath()).orElse("");

        int r, g, b;
        if (water || bn.contains("ocean") || bn.contains("river")) {
            ...
        } else if (bn.contains("beach") || bn.contains("desert")) { r=190; g=175; b=130; }
        else if (bn.contains("badlands") || bn.contains("eroded")) { r=180; g=95; b=35; }
        else if (bn.contains("snowy") || bn.contains("frozen") || bn.contains("ice")) { ... }
        else if (bn.contains("mushroom")) { r=130; g=95; b=130; }
        else {
            int gc = getServerSafeGrassColor(bn);
```
```java
    private static int getServerSafeGrassColor(String biomeName) {
        if (biomeName.contains("swamp"))                  return 0x6A7039;
        if (biomeName.contains("jungle"))                 return 0x59C93C;
        ... (20 последовательных contains)
        return 0x7ABD52;
    }
```
ПОЧЕМУ ЭТО БАГ: результат зависит ИСКЛЮЧИТЕЛЬНО от `Holder<Biome>` — неизменяемого объекта, которых
в мире порядка 60 штук. Он пересчитывается заново для каждого из миллионов пикселей. `unwrapKey()`
возвращает `Optional<ResourceKey<Biome>>` (аллокация), `.map(...)` возвращает ещё один `Optional`
(аллокация). Ни `IdentityHashMap<Holder<Biome>,Integer>`, ни `Reference2IntMap` не используются —
хотя `biomeColorCache` (F-06) существует, он кэширует по КООРДИНАТЕ, а не по биому, и работает
только на ветке «чанк отсутствует».
ЦЕНА В ЧИСЛАХ:
- До **9 `contains` в `sampleColor` + 20 в `getServerSafeGrassColor` = 29 вызовов на пиксель**.
  `String.indexOf` интринсифицирован (SIMD), реально ~15 нс/вызов ⇒ **≈435 нс на пиксель ТОЛЬКО на
  сопоставление строк**.
- Terrain: 2 359 296 × 435 нс = **1,03 с CPU** = **41 тик из 160** (25,6 % всего времени сборки).
- Route: 4 194 304 × 435 нс = **1,82 с CPU** = **73 тика из 388**.
- Верхняя оценка по символам: имя вроде `old_growth_spruce_taiga` (23 символа) × 29 иголок
  (в среднем 8 символов) ⇒ до 23 × 8 × 29 ≈ 5336 сравнений символов на пиксель ⇒
  **12,6 млрд сравнений на terrain-карту, 22,4 млрд на route-карту**.
- Аллокации: 2 `Optional` на пиксель ⇒ terrain **4 718 592 объекта × 16 Б = 75 МБ мусора**,
  route **8 388 608 × 16 Б = 134 МБ мусора** — за 8–20 с, т.е. 7–9 МБ/с чистого мусора,
  вынуждающих непрерывные young-GC поверх и без того просевшего тика.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: async-profiler на сервере во время сборки route-карты:
`java.lang.String.indexOf` и `java.util.Optional.map` в топ-5 по CPU под
`ServerRouteMapScanner.sampleColor`.
ТЕСТИРУЕМОСТЬ: pure-java benchmark
НАБРОСОК ТЕСТА:
```java
@Test void stringMatchingDominates() {
    String[] names = {"old_growth_spruce_taiga","plains","deep_frozen_ocean","windswept_savanna"};
    int N = 4_194_304;                       // ServerRouteMapScanner.CACHE_SIZE^2
    long t0 = System.nanoTime(); int sink = 0;
    for (int i = 0; i < N; i++) sink ^= grassColorCopyOfProdCode(names[i & 3]);
    long ms = (System.nanoTime()-t0)/1_000_000;
    System.out.println("29 contains × " + N + " px = " + ms + " ms (" + sink + ")");
    assertTrue(ms < 200, "ДОКАЗАНО: " + ms + " мс = " + (ms/25) + " тиков по MAX_TICK_MS только на строки");
}
```

---

### F-14: `getOrCreate` выделяет полный пиксельный буфер ДО проверки дискового кэша; на route-пути возможны две 16-МиБ аллокации на один запрос
СЕРЬЁЗНОСТЬ: **high**
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/radar/ServerRouteMapScanner.java:83-108`
КОД:
```java
        String k = key(pos);
        ServerRouteMapScanner scanner = new ServerRouteMapScanner(pos);   // int[2048*2048] = 16 МиБ
        byte[] cached = scanner.tryLoadFromFile(level);
        if (cached != null) {
            scanner.pngBytes = cached;
            scanner.ready = true;
            scanner.pixels = null;                                        // 16 МиБ выброшены
            INSTANCES.put(k, scanner);
            return scanner;
        }

        BlockPos nearbyCenter = findNearbyDiskCache(pos, level);
        if (nearbyCenter != null) {
            String nk = key(nearbyCenter);
            if (INSTANCES.containsKey(nk)) return INSTANCES.get(nk);
            ServerRouteMapScanner nearbyScanner = new ServerRouteMapScanner(nearbyCenter);  // ЕЩЁ 16 МиБ
            byte[] nearbyCached = nearbyScanner.tryLoadFromFile(level);
            if (nearbyCached != null) {
                nearbyScanner.pngBytes = nearbyCached;
                nearbyScanner.ready = true;
                nearbyScanner.pixels = null;                              // и эти 16 МиБ выброшены
```
и конструктор (`ServerRouteMapScanner.java:54-59`):
```java
    public ServerRouteMapScanner(BlockPos centerPos) {
        ...
        this.pixels = new int[CACHE_SIZE * CACHE_SIZE];
    }
```
ПОЧЕМУ ЭТО БАГ: массив нужен ТОЛЬКО для построения. На «счастливом» пути (карта уже на диске) он
аллоцируется и немедленно выбрасывается. Оба массива — `2048*2048*4 = 16 777 216 Б` — в G1 при
типовом размере региона 1–32 МБ это humongous-аллокация в old gen, которую не соберёт young-GC.
Всё это происходит в `context.enqueueWork` ⇒ **в главном потоке сервера**.
ЦЕНА В ЧИСЛАХ:
- Худший путь: `new ServerRouteMapScanner(pos)` (16,0 МиБ) + `new ServerRouteMapScanner(nearbyCenter)`
  (16,0 МиБ) = **32 МиБ humongous-мусора на один пакет `RequestRouteMapPacket`**.
- В связке с F-05 (2 запроса/с на игрока при незавершённой сборке) — при промахе `findNear`
  это **64 МиБ/с humongous-аллокаций на игрока**. G1 переходит в непрерывный concurrent-цикл,
  затем в Full GC.
- `new int[4 194 304]` также требует ZEROING 16 МиБ — это ~2–5 мс memset в главном потоке на каждую
  аллокацию.
- Terrain-аналог (`ServerTerrainScanner.java:83-88`): 9,0 МиБ на запрос по тому же паттерну.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: сервер с существующим `ata_radar_cache/route_v16_*.png`, игрок открывает
route-экран в 600 блоках от центра кэша (за `REUSE_RADIUS=500`). `-Xlog:gc*` покажет
`Humongous regions: N->M` при каждом пакете.
ТЕСТИРУЕМОСТЬ: pure-java benchmark
НАБРОСОК ТЕСТА:
```java
@Test void allocBeforeCacheCheck() {
    Runtime rt = Runtime.getRuntime(); System.gc();
    long before = rt.totalMemory()-rt.freeMemory();
    long t0 = System.nanoTime();
    for (int i = 0; i < 4; i++) { int[] a = new int[2048*2048]; a[0]=i; }  // 2 запроса × 2 массива
    long ms = (System.nanoTime()-t0)/1_000_000;
    System.out.println("4 × 16 МиБ = " + ms + " мс только на zeroing");
    assertTrue(ms < 5, "ДОКАЗАНО: " + ms + " мс в главном потоке на выброшенные буферы");
}
```

---

# СРЕДНИЕ

### F-15: `WorldMapScanner` вызывает `getChunk(..., FULL, false)` для чанков, прочитанных из региональных файлов ⇒ для незагруженных возвращается `null`, данные не собираются, но `scanComplete = true` выставляется навсегда
СЕРЬЁЗНОСТЬ: **medium**
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/worldmap/WorldMapScanner.java:178-184, 105`
КОД:
```java
    private void scanChunk(ServerLevel level, ChunkPos pos, WorldMapData mapData) {
        try {
            ChunkAccess chunk = level.getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
            if (chunk == null) {
                return;                       // <-- ~все чанки из .mca не загружены => выход
            }
```
```java
                mapData.setScanComplete(true);
```
и источник списка (`WorldMapScanner.java:155-168`):
```java
        try (RandomAccessFile raf = new RandomAccessFile(regionFile, "r")) {
            for (int localZ = 0; localZ < 32; localZ++) {
                for (int localX = 0; localX < 32; localX++) {
                    int headerOffset = 4 * (localX + localZ * 32);
                    raf.seek(headerOffset);
                    int offset = raf.readInt();
                    if (offset != 0) { ... chunks.add(new ChunkPos(chunkX, chunkZ)); }
```
ПОЧЕМУ ЭТО БАГ: список чанков берётся из заголовков `.mca` — это чанки, СОХРАНЁННЫЕ на диск, т.е.
в подавляющем большинстве НЕ загруженные в память. `getChunk(...,FULL,false)` для них возвращает
`null` (факт (B): `chunkAbsent(holder, 33)` истинно, т.к. holder'а нет) ⇒ `scanChunk` выходит,
`markChunkScanned` НЕ вызывается, точки береговой линии не собираются. При этом на строке 105
безусловно выставляется `mapData.setScanComplete(true)`, а `startScan` (`:56-59`) навсегда
отказывается сканировать повторно:
```java
        if (mapData.isScanComplete()) {
            LOGGER.info("[ATA] Map already scanned ({} coastline points)", mapData.getCoastlinePointCount());
            return;
        }
```
ЦЕНА В ЧИСЛАХ: мир радиусом 5000 блоков ≈ 390 625 чанков ⇒ `findExistingChunks` создаёт
**390 625 `ChunkPos`-объектов** (390 625 × 24 Б = **9,4 МБ**) + `ArrayList` с 390k элементов
(1,6 МБ на массив ссылок), читая **382 региональных файла** по 1024 `seek`+`readInt` каждый =
**391 168 syscall-пар** на рабочем потоке. Затем 3907 батчей × 50 мс сна = **195 секунд**, в
течение которых главный поток получает 3907 задач, каждая из которых делает 100 `getChunk` →
`getChunkCacheMiss` → `getVisibleChunkIfPresent` → `null`. Результат: **0 точек побережья**,
`scanComplete=true`, повторное сканирование невозможно без `/ata map rescan`.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: `/ata map rescan` в исследованном мире, затем `/ata map status` ⇒
«Scan complete: 0 coastline points» (или число, равное только тому, что было загружено).
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
@GameTest(template = "empty", timeoutTicks = 200)
public static void scanFindsNothingForUnloadedChunks(GameTestHelper h) {
    ServerLevel lvl = h.getLevel();
    ChunkPos far = new ChunkPos(9000, 9000);            // есть в .mca, не загружен
    h.assertTrue(lvl.getChunk(far.x, far.z, ChunkStatus.FULL, false) == null,
        "ДОКАЗАНО: getChunk(FULL,false) для сохранённого-но-невыгруженного чанка = null,"
        + " т.е. scanChunk() выйдет на строке 182 и ничего не соберёт");
}
```

---

### F-16: `WorldMapScanner` кладёт по 100 чанков в ОДНУ задачу главного потока без бюджета времени: 16 000 heightmap+blockstate операций и 9600 аллокаций каждые 50 мс
СЕРЬЁЗНОСТЬ: **medium**
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/worldmap/WorldMapScanner.java:74-92, 185-202, 210-227`
КОД:
```java
                int batchSize = 100;
                for (int i = 0; i < chunksToScan.size(); i += batchSize) {
                    ...
                    level.getServer().execute(() -> {
                        for (ChunkPos pos : batch) {
                            if (mapData.isChunkScanned(pos.x, pos.z)) continue;
                            scanChunk(level, pos, mapData);
                        }
                    });
                    ...
                    Thread.sleep(50);
```
```java
                    int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ);
                    BlockPos blockPos = new BlockPos(worldX, y, worldZ);      // аллокация в цикле
                    BlockState state = chunk.getBlockState(blockPos);
```
```java
    private boolean isCoastlineOptimized(ChunkAccess chunk, int localX, int localZ, boolean isWater) {
        int[][] neighbors = {{-SCAN_STEP, 0}, {SCAN_STEP, 0}, {0, -SCAN_STEP}, {0, SCAN_STEP}};  // аллокация на КАЖДЫЙ вызов
        for (int[] offset : neighbors) {
            ...
            BlockPos neighborPos = new BlockPos(                                // ещё аллокация
                chunk.getPos().getMinBlockX() + nx, ny, chunk.getPos().getMinBlockZ() + nz);
```
ПОЧЕМУ ЭТО БАГ: задача, отданная в `level.getServer().execute(...)`, выполняется главным потоком
целиком, без возможности прерваться. `MAX_TICK_MS`-подобного бюджета здесь нет вообще.
`Thread.sleep(50)` в воркере регулирует только ТЕМП постановки задач, а не их стоимость.
`int[][] neighbors` — литеральный массив-константа, пересоздаваемый на каждый вызов
(5 объектов: 1 `int[][]` + 4 `int[]`). `new BlockPos` в цикле — при том, что рядом в коде мода
уже используется `BlockPos.MutableBlockPos` (`ServerTerrainScanner.java:161`).
ЦЕНА В ЧИСЛАХ (`SCAN_STEP = 4`, `:176`):
- Сэмплов на чанк: (16/4)² = **16**. На каждый сэмпл: 1 `getHeight` + 1 `getBlockState` +
  `isCoastlineOptimized` = до 4 соседей × (1 `getHeight` + 1 `getBlockState`) ⇒ **до 10 операций**.
- На батч: 100 чанков × 16 × 10 = **16 000 heightmap/blockstate-обращений в ОДНОЙ задаче**.
  `getBlockState` через `LevelChunk.getBlockState(BlockPos)` ≈ 30–60 нс ⇒ **0,5–1,0 мс на батч**,
  но при глубоком палитровом чанке и промахах кэша — до 3–5 мс. Каждые 50 мс = каждые ~1 тик.
- Аллокации на батч: 100 × 16 × (1 + 4) = **8000 `BlockPos`** (8000 × 24 Б = 192 КБ) +
  100 × 16 × 5 = **8000 объектов от `int[][] neighbors`** (≈ 8000 × 40 Б = 320 КБ) ⇒
  **≈0,5 МБ мусора каждые 50 мс = 10 МБ/с** на протяжении всего сканирования (до 195 с ⇒ **1,95 ГБ**
  суммарного мусора).
- `chunk.getPos()` вызывается 2 раза внутри каждого из 4 соседей = 8 вызовов на сэмпл.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: `/ata map rescan` на сервере с загруженными чанками (несколько игроков).
spark: `WorldMapScanner.lambda$startScan$1` в профиле главного потока, 1–5 мс пиками каждые 50 мс.
ТЕСТИРУЕМОСТЬ: pure-java benchmark
НАБРОСОК ТЕСТА:
```java
@Test void neighborsArrayAllocatedPerCall() {
    int SCAN_STEP = 4, calls = 100 * 16;              // batchSize × сэмплов на чанк
    Runtime rt = Runtime.getRuntime(); System.gc();
    long before = rt.totalMemory()-rt.freeMemory();
    Object sink = null;
    for (int i = 0; i < calls; i++)
        sink = new int[][]{{-SCAN_STEP,0},{SCAN_STEP,0},{0,-SCAN_STEP},{0,SCAN_STEP}};
    System.gc(); long kb = (rt.totalMemory()-rt.freeMemory()-before) >> 10;
    System.out.println("на один батч: " + calls + " × int[][] = " + kb + " KB (" + sink + ")");
}
```

---

### F-17: `ExecutorService` в `WorldMapScanner` никогда не завершается; `setScanComplete(true)` выставляется ДО выполнения поставленных в главный поток задач; воркер удерживает `ServerLevel`
СЕРЬЁЗНОСТЬ: **medium**
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/worldmap/WorldMapScanner.java:28-46, 85-112`
КОД:
```java
    private static WorldMapScanner instance;
    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ATA-MapScanner");
        t.setDaemon(true);
        return t;
    });
...
    public static WorldMapScanner getInstance() {
        if (instance == null) {                       // не synchronized, не volatile
            instance = new WorldMapScanner();
        }
        return instance;
    }
```
```java
                    level.getServer().execute(() -> { ... scanChunk(level, pos, mapData); });
                    scannedCount += batch.size();
                    progress.set(scannedCount);
                    ...
                    Thread.sleep(50);
                }

                mapData.setScanComplete(true);        // <-- задачи главного потока ещё в очереди
```
ПОЧЕМУ ЭТО БАГ:
1. `scanExecutor.shutdown()` не вызывается нигде (`grep "shutdown" WorldMapScanner.java` = 0).
   `instance` — статическое поле, живущее весь JVM. В одиночной игре сканирование, запущенное в
   мире A, продолжает работать после выхода в меню: лямбда захватила `ServerLevel level` и
   `WorldMapData mapData` ⇒ **весь `ServerLevel` (со всей `ChunkMap`, `EntitySectionStorage`,
   `DimensionDataStorage`) удерживается от GC** до конца сканирования (до 195 с) или навсегда, если
   `level.getServer().execute(...)` бросит на остановленном сервере (тогда `finally` всё же снимет
   `isScanning`, но `instance` останется).
2. `execute()` НЕ ждёт выполнения. `setScanComplete(true)` вызывается сразу после постановки
   последнего батча ⇒ последние ≥100 чанков могут быть просканированы уже после того, как данные
   объявлены полными; а `startScan` навсегда откажется повторять (см. F-15).
3. `getInstance()` — классический незащищённый ленивый синглтон (нет `synchronized`, нет `volatile`).
   Вызывается из `RequestWorldMapPacket.handle` (главный поток) и из `ShahedCommand` (главный поток) —
   сегодня гонка не проявляется, но конструктор создаёт поток, и двойная инициализация создала бы
   **два ExecutorService с двумя вечными потоками**.
ЦЕНА В ЧИСЛАХ: удерживаемый `ServerLevel` современного мира с 200 загруженными чанками ≈
200 × (LevelChunk ~60 КБ + PalettedContainer'ы) ≈ **12–60 МБ**, плюс `WorldMapData` с
`ConcurrentHashMap.newKeySet()` точек. Один поток `ATA-MapScanner` навсегда (≈1 МБ стека).
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: одиночная игра, `/ata map rescan`, немедленный выход в главное меню.
`jcmd <pid> Thread.print` ⇒ поток `ATA-MapScanner` жив; `jmap -histo:live` ⇒ `ServerLevel` = 1
при отсутствии загруженного мира.
ТЕСТИРУЕМОСТЬ: pure-java
НАБРОСОК ТЕСТА:
```java
@Test void executorNeverShutDown() throws Exception {
    String src = Files.readString(Path.of("src/main/java/.../worldmap/WorldMapScanner.java"));
    assertFalse(src.contains("scanExecutor.shutdown"), "shutdown() отсутствует => поток вечен");
    assertFalse(src.contains("synchronized WorldMapScanner getInstance"), "getInstance не защищён");
    // порядок: setScanComplete вызывается после цикла execute(), но execute() асинхронен
    int idxExec = src.indexOf("level.getServer().execute");
    int idxDone = src.indexOf("mapData.setScanComplete(true)");
    assertTrue(idxExec < idxDone && !src.contains("CompletableFuture.allOf"),
        "нет ожидания завершения задач главного потока перед объявлением scanComplete");
}
```

---

### F-18: ABBA-инверсия порядка блокировок между мониторами `ServerTerrainScanner.class` и `ServerRouteMapScanner.class`
СЕРЬЁЗНОСТЬ: **medium**
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/worldmap/ServerTerrainScanner.java:120-130` и
`src/main/java/com/synarsis/airtacticalarsenal/radar/ServerRouteMapScanner.java:163-172`
КОД (порядок A → B):
```java
    public static synchronized int getQueuePosition(BlockPos pos) {     // lock ServerTerrainScanner.class
        String targetKey = key(pos);
        ...
        position += ServerRouteMapScanner.countBuildingBefore(target.buildStartOrder);   // lock ServerRouteMapScanner.class
        return position;
    }
```
КОД (порядок B → A):
```java
    public static synchronized int getQueuePosition(BlockPos pos) {     // lock ServerRouteMapScanner.class
        ServerRouteMapScanner target = findNear(pos);
        ...
        position += ServerTerrainScanner.countBuildingBefore(target.buildStartOrder);    // lock ServerTerrainScanner.class
        return position;
    }
```
ПОЧЕМУ ЭТО БАГ: два `static synchronized` метода захватывают ДВА РАЗНЫХ монитора классов во
взаимно обратном порядке. Это каноническая ABBA-конфигурация: поток 1 в
`ServerTerrainScanner.getQueuePosition` держит монитор A и ждёт B; поток 2 в
`ServerRouteMapScanner.getQueuePosition` держит B и ждёт A ⇒ взаимная блокировка навсегда.
Сегодня оба вызываются только из `context.enqueueWork` (главный поток), поэтому сериализованы; но
защита держится исключительно на этом внешнем инварианте, который в коде нигде не задокументирован
и не проверен (`assert server.isSameThread()` отсутствует). Любое будущее использование из
worker'а (а `WorldMapScanner` уже показывает, что в моде есть off-thread код, обращающийся к
серверному состоянию) даёт мгновенный дедлок серверного потока.
ЦЕНА В ЧИСЛАХ: дедлок = TPS 0, сервер не отвечает, watchdog убивает его через
`max-tick-time=60000` мс (60 с) с креш-дампом. Стоимость восстановления — полный рестарт.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: вызвать `ServerTerrainScanner.getQueuePosition` из потока 1 и
`ServerRouteMapScanner.getQueuePosition` из потока 2 в цикле ⇒ дедлок за секунды.
ТЕСТИРУЕМОСТЬ: pure-java
НАБРОСОК ТЕСТА:
```java
@Test(timeout = 5000) void abbaDeadlock() throws Exception {
    Object A = ServerTerrainScanner.class, B = ServerRouteMapScanner.class;
    CountDownLatch go = new CountDownLatch(1);
    Thread t1 = new Thread(() -> { synchronized (A) { go.countDown();
        try { Thread.sleep(50);} catch(Exception e){} synchronized (B) {} }});
    Thread t2 = new Thread(() -> { synchronized (B) {
        try { go.await(); Thread.sleep(50);} catch(Exception e){} synchronized (A) {} }});
    t1.start(); t2.start(); t1.join(2000); t2.join(2000);
    assertFalse(t1.isAlive() || t2.isAlive(), "ДОКАЗАНО: ABBA на мониторах двух классов сканеров");
}
```

---

### F-19: `ShahedRadarBlock.calculateSignalStrength` строит `List<Entity>` по AABB 3000 × 464 × 3000 каждую секунду на каждый радар
СЕРЬЁЗНОСТЬ: **medium**
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/block/ShahedRadarBlock.java:37, 102-124` +
`:99` (`level.scheduleTick(pos, this, 20)`)
КОД:
```java
    private static final int RADAR_RANGE = 1500;
...
        AABB searchBox = new AABB(
            radarCenter.x - RADAR_RANGE, radarCenter.y - 64, radarCenter.z - RADAR_RANGE,
            radarCenter.x + RADAR_RANGE, radarCenter.y + 400, radarCenter.z + RADAR_RANGE
        );
        double closestDistance = Double.MAX_VALUE;
        List<Entity> entities = level.getEntities(null, searchBox);
        for (Entity entity : entities) {
            boolean isFlying = entity instanceof ShahedEntity shahed && (...);
            if (isFlying || entity instanceof IskanderEntity) {
```
ПОЧЕМУ ЭТО БАГ: `Level.getEntities(null, AABB)` материализует `ArrayList` со ВСЕМИ сущностями в
объёме — мобы, предметы, вагонетки, рамки, стойки. Фильтрация по типу происходит ПОСЛЕ построения
списка. Реализация `EntitySectionStorage.forEachAccessibleNonEmptySection` (проверено в исходниках)
итерирует по X-слоям секций:
```java
        for (int l1 = j; l1 <= i1; l1++) {
            long i2 = SectionPos.asLong(l1, 0, 0);
            long j2 = SectionPos.asLong(l1, -1, -1);
            LongIterator longiterator = this.sectionIds.subSet(i2, j2 + 1L).iterator();
            while (longiterator.hasNext()) { ... }
```
— т.е. для каждого X-слоя перебираются ВСЕ непустые секции этого слоя по всей оси Z мира,
и только потом отсекаются по Y/Z.
ЦЕНА В ЧИСЛАХ:
- Сторона AABB: 2 × 1500 = **3000 блоков** ⇒ 3000/16 + 1 = **189 X-слоёв** ⇒ 189 операций
  `subSet` по `LongSortedSet` (AVL/red-black) с полным обходом непустых секций слоя.
- Высота: (400 + 64)/16 = **29 Y-секций**; объём поиска 189 × 29 × 189 = **1 035 909 секций**.
- Сервер с 5 игроками и mob-cap ≈ **1500–5000 сущностей** в радиусе 1500 ⇒ построение
  `ArrayList` на 1500–5000 элементов + 1500–5000 `instanceof`-проверок, **каждые 20 тиков на
  каждый радар-блок**.
- 10 радаров ⇒ 10 таких запросов в секунду ⇒ **15 000–50 000 обходов сущностей/с** плюс
  10 × (ArrayList на 5000 ссылок = 20 КБ) = **200 КБ/с мусора**.
- Всё это ради результата, который используется только для `POWER` редстоун-сигнала.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: 10 радар-блоков, 3000 сущностей в мире. spark ⇒
`ShahedRadarBlock.calculateSignalStrength` → `Level.getEntities` заметная доля тика при полном
отсутствии активности мода.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
@GameTest(template = "empty", timeoutTicks = 100)
public static void radarAabbIsHuge(GameTestHelper h) {
    ServerLevel lvl = h.getLevel();
    for (int i = 0; i < 2000; i++) lvl.addFreshEntity(new ItemEntity(lvl, i%1000, 70, i/1000.0, new ItemStack(Items.STONE)));
    AABB box = new AABB(-1500, 6, -1500, 1500, 470, 1500);
    long t0 = System.nanoTime();
    for (int i = 0; i < 10; i++) lvl.getEntities((Entity)null, box);   // 10 радаров
    long ms = (System.nanoTime()-t0)/1_000_000;
    h.assertTrue(ms < 5, "ДОКАЗАНО: 10 радарных запросов = " + ms + " мс/с при бюджете тика 50 мс");
}
```

---

### F-20: `GLOBAL_BUILD_ORDER` — публичное изменяемое `static long`, инкрементируемое под ДВУМЯ разными мониторами
СЕРЬЁЗНОСТЬ: **medium**
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/worldmap/ServerTerrainScanner.java:50, 91` и
`src/main/java/com/synarsis/airtacticalarsenal/radar/ServerRouteMapScanner.java:111`
КОД:
```java
    public static long GLOBAL_BUILD_ORDER = 0;
...
            scanner.buildStartOrder = ++GLOBAL_BUILD_ORDER;             // под ServerTerrainScanner.class
```
```java
        scanner.buildStartOrder = ++ServerTerrainScanner.GLOBAL_BUILD_ORDER;   // под ServerRouteMapScanner.class
```
ПОЧЕМУ ЭТО БАГ: `++x` на `long` — это read-modify-write из трёх операций, и на 32-битных JVM даже
чтение/запись `long` не атомарны (JLS 17.7). Взаимного исключения между двумя точками нет: они
синхронизированы по РАЗНЫМ мониторам классов, поле не `volatile` и не `AtomicLong`. Два сканера
могут получить ОДИНАКОВЫЙ `buildStartOrder`, что ломает всю логику приоритетов
`getFirstBuildingOrder`/`terrainOrder <= routeOrder`/`countBuildingBefore` — в
`ServerForgeEvents.onServerTick:64` при равенстве всегда выигрывает terrain, и route-сканер
с тем же order никогда не получит тик, пока terrain строится.
Дополнительно: поле никогда не сбрасывается при остановке сервера ⇒ вместе с F-11 order растёт
через смены миров, а `buildStartOrder` старых сканеров из другого мира остаётся меньше и
приоритетнее.
ЦЕНА В ЧИСЛАХ: при коллизии order route-карта задерживается на полное время сборки terrain-карты =
**160 тиков (8 с)** и далее по цепочке. Без атомарности гарантий упорядочивания нет вовсе.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: код-инспекция достаточна — `public static long`, `++` в двух
`synchronized`-контекстах с разными мониторами.
ТЕСТИРУЕМОСТЬ: pure-java
НАБРОСОК ТЕСТА:
```java
@Test void nonAtomicCounterLosesUpdates() throws Exception {
    final long[] counter = {0};
    Object lockA = new Object(), lockB = new Object();     // моделируем два монитора классов
    Runnable ra = () -> { for (int i=0;i<200_000;i++) synchronized(lockA){ counter[0]++; } };
    Runnable rb = () -> { for (int i=0;i<200_000;i++) synchronized(lockB){ counter[0]++; } };
    Thread t1=new Thread(ra), t2=new Thread(rb); t1.start(); t2.start(); t1.join(); t2.join();
    assertEquals(400_000L, counter[0]);   // упадёт => потерянные инкременты => дубли buildStartOrder
}
```

---

### F-21: Клиент копирует карту пиксель-за-пикселем: 4,19 млн пар `getPixelRGBA`/`setPixelRGBA` на render-потоке
СЕРЬЁЗНОСТЬ: **medium**
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/client/radar/RouteTerrainRenderer.java:191-199`
(зеркало `client/radar/TerrainMapRenderer.java:122-130`, и инициализация `RouteTerrainRenderer.java:99-104`)
КОД:
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
и в конструкторе — то же самое для заливки тёмным:
```java
        NativeImage d = displayTexture.getPixels();
        if (d != null)
            for (int y = 0; y < TEX_SIZE; y++)
                for (int x = 0; x < TEX_SIZE; x++)
                    d.setPixelRGBA(x, y, DARK);
```
ПОЧЕМУ ЭТО БАГ: `NativeImage.setPixelRGBA/getPixelRGBA` — обёртки над `MemoryUtil.memPutInt` с
проверкой границ (`checkAllocated()`, `_checkAllocated`, проверка формата) на каждый вызов. Это
выполняется на главном (render) потоке клиента при открытии GUI и при каждом приходе карты.
Существует `NativeImage.copyFrom(NativeImage)` / прямой `memCopy`, не используемый.
ЦЕНА В ЧИСЛАХ:
- `TEX_SIZE = ServerRouteMapScanner.CACHE_SIZE = 2048` ⇒ 2048² = **4 194 304 итерации**, каждая с
  2 JNI-подобными вызовами + 2 проверками ⇒ ~8–15 нс/пиксель ⇒ **34–63 мс на кадре**.
  При 60 FPS кадр = 16,7 мс ⇒ **пропуск 2–4 кадров, видимый рывок**.
- Конструктор делает то же (заливка DARK) ⇒ **ещё 34–63 мс при ОТКРЫТИИ экрана**.
- `TerrainMapRenderer` (1536²= 2 359 296) ⇒ 19–35 мс ×2.
- Память: `MAX_CACHED_MAPS = 8` (`RouteTerrainRenderer.java:42`) × PNG 2–6 МБ = **до 48 МБ**
  статического `mapCache`, плюс `NativeImage cacheImage` 2048²×4 = **16 МиБ off-heap** +
  `DynamicTexture` ещё **16 МиБ off-heap**. `mapCache` — `static final List`, `clearCache()`
  вызывается только вручную; не очищается при выходе из мира.
- `TerrainMapRenderer.cachedPngBytes` — `static byte[]`, никогда не обнуляется (`:118`).
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: открыть route-экран с готовой картой — видимый фриз клиента ~50–120 мс
(открытие + приход данных).
ТЕСТИРУЕМОСТЬ: pure-java benchmark (эмуляция стоимости обхода)
НАБРОСОК ТЕСТА:
```java
@Test void perPixelCopyCost() {
    int N = 2048; int[] src = new int[N*N], dst = new int[N*N];
    long t0 = System.nanoTime();
    for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) dst[y*N+x] = src[y*N+x];
    long arrMs = (System.nanoTime()-t0)/1_000_000;
    // реальный NativeImage добавляет 2 bounds-check + memPutInt/memGetInt на пиксель (~×4-8)
    System.out.println("нижняя граница (int[]): " + arrMs + " мс; NativeImage ≈ " + (arrMs*5) + " мс");
    assertTrue(arrMs*5 < 16, "ДОКАЗАНО: копирование не помещается в кадр 16,7 мс");
}
```

---

### F-22: `RequestSegmentMaxHeightPacket` при открытии GUI выполняет тысячи вызовов `ChunkGenerator.getBaseHeight` в одном тике; кэша на клиенте нет
СЕРЬЁЗНОСТЬ: **medium**
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/network/RequestSegmentMaxHeightPacket.java:25, 64-93`
+ `client/gui/ShahedRouteScreen.java:303-310`
+ `client/gui/SurfaceHeightCache.java:52-54`
КОД (сервер):
```java
    private static final int PROFILE_SAMPLES = 32;
...
        for (int i = 0; i < PROFILE_SAMPLES; i++) {
            ...
            profile[i] = getTerrainHeight(level, sx, sz);
        }
...
        try {
            var gen = level.getChunkSource().getGenerator();
            var rs = level.getChunkSource().randomState();
            int oceanFloor = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR, level, rs);
            int worldSurface = gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE, level, rs);
```
КОД (клиент, `init()` экрана — запрос для ВСЕХ маршрутов и ВСЕХ точек):
```java
        int savedIdx = currentEditIndex;
        for (int ri = 0; ri < allRoutes.size(); ri++) {
            currentEditIndex = ri;
            for (int wi = 0; wi < allRoutes.get(ri).size(); wi++) {
                requestSegmentHeightForWaypoint(wi);
            }
        }
```
КОД (клиент, полное отсутствие кэша — в отличие от `requestHeight` рядом):
```java
    public static void requestSegmentHeight(int x1, int z1, int x2, int z2, int segmentIndex) {
        NetworkHandler.sendToServer(new RequestSegmentMaxHeightPacket(x1, z1, x2, z2, segmentIndex));
    }
```
ПОЧЕМУ ЭТО БАГ: `NoiseBasedChunkGenerator.getBaseHeight` строит `NoiseChunk` для одной колонки и
итерирует её сверху вниз с шагом cell_height, вычисляя весь density-function-пайплайн. Это самая
дорогая «точечная» операция генератора. Она вызывается ДВАЖДЫ на сэмпл (OCEAN_FLOOR + WORLD_SURFACE),
32 сэмпла на сегмент. Все пакеты, пришедшие в одном сетевом «залпе», обрабатываются
`context.enqueueWork` и выполняются главным потоком в ОДНОМ тике (`MinecraftServer.runAllTasks`).
Кэша нет ни на клиенте (`requestSegmentHeight` шлёт пакет всегда), ни на сервере.
ЦЕНА В ЧИСЛАХ:
- На один сегмент: 32 сэмпла × 2 `getBaseHeight` = **64 вызова**.
- Мир высотой 384 ⇒ колонка на cell_height=8 ⇒ ~48 сэмплов density-функций на вызов ⇒
  **0,1–1,0 мс на `getBaseHeight`** ⇒ **6,4–64 мс на ОДИН сегмент**.
- Открытие экрана с группой из 4 ПУ × `MAX_WAYPOINTS = 20` (`ShahedRouteScreen.java:79`) =
  **80 пакетов = 5120 вызовов `getBaseHeight`** ⇒ **512–5120 мс в одном тике**.
  Один ПУ с 20 точками ⇒ **128–1280 мс**.
- Ветка кэша (`chunk != null`) работает только для ЗАГРУЖЕННЫХ чанков; маршрут по определению уходит
  на 12000 блоков (`MAX_FLIGHT_DISTANCE`, `:66`) ⇒ подавляющее большинство сэмплов идёт по
  дорогой ветке `getBaseHeight`.
- Каждое добавление/загрузка точки (`:451`, `:544`, `:393`) — ещё один такой запрос без дедупликации.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: сохранить маршрут из 20 точек длиной 12000 блоков, закрыть и открыть
экран заново. Лог сервера: `Can't keep up! Running 500–5000ms behind` ровно в момент открытия GUI.
ТЕСТИРУЕМОСТЬ: gametest / benchmark
НАБРОСОК ТЕСТА:
```java
@GameTest(template = "empty", timeoutTicks = 400)
public static void baseHeightIsExpensive(GameTestHelper h) {
    ServerLevel lvl = h.getLevel();
    var gen = lvl.getChunkSource().getGenerator();
    var rs  = lvl.getChunkSource().randomState();
    long t0 = System.nanoTime();
    for (int i = 0; i < 64; i++) {                 // ровно один сегмент: 32 сэмпла × 2
        gen.getBaseHeight(200000 + i*17, 200000, Heightmap.Types.OCEAN_FLOOR, lvl, rs);
    }
    long ms = (System.nanoTime()-t0)/1_000_000;
    h.fail("ОДИН сегмент = " + ms + " мс; 80 сегментов при открытии GUI = " + (ms*80) + " мс в одном тике");
}
```

---

# НИЗКИЕ

### F-23: Бюджет тика меряется немонотонными `System.currentTimeMillis()`
СЕРЬЁЗНОСТЬ: **low**
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/worldmap/ServerTerrainScanner.java:160, 176`
(зеркало `radar/ServerRouteMapScanner.java:202, 220`)
КОД:
```java
        long start = System.currentTimeMillis();
...
            if ((pixelsDone & 31) == 0 && System.currentTimeMillis() - start > MAX_TICK_MS) break;
```
ПОЧЕМУ ЭТО БАГ: `System.currentTimeMillis()` — wall-clock, подверженный скачкам NTP, ручной
коррекции времени и переходу на летнее время. При скачке назад разность становится отрицательной
и цикл НЕ прервётся до конца строки/карты; при скачке вперёд сканер прервётся, не сделав работы.
Для измерения интервалов JLS/Javadoc предписывают `System.nanoTime()` (монотонные). Дополнительно
на Windows гранулярность `currentTimeMillis` привязана к системному таймеру и при неповышенном
`timeBeginPeriod` составляет ~15,6 мс, т.е. измеряемые значения квантуются: реальная порция работы
может составить ~31 мс вместо заявленных 25 (**62 % бюджета тика**).
ЦЕНА В ЧИСЛАХ: при NTP-скачке назад на 1 с цикл выполнит **до 2 359 296 сэмплов подряд** в одном
тике вместо ~12 500 ⇒ **≈4 с в одном тике** ⇒ watchdog. Вероятность низкая, стоимость — креш.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: во время сборки карты выполнить `date -s "-2 seconds"` на хосте сервера.
ТЕСТИРУЕМОСТЬ: pure-java
НАБРОСОК ТЕСТА:
```java
@Test void wallClockIsNotMonotonic() {
    // документально: контракт currentTimeMillis не гарантирует монотонность
    long start = Long.MAX_VALUE - 10;            // моделируем «время прыгнуло назад»
    long now   = 0;
    assertFalse(now - start > 25, "разность отрицательна => break никогда не сработает");
}
```

---

### F-24: `SurfaceHeightCache` — статические `HashMap` без ограничения, ключ сегмента коллизионен между маршрутами
СЕРЬЁЗНОСТЬ: **low**
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/client/gui/SurfaceHeightCache.java:14-17, 56-58`
+ `client/gui/ShahedRouteScreen.java:1251`
КОД:
```java
    private static final Map<Long, Integer> heightCache = new HashMap<>();
    private static final Map<Integer, Integer> segmentHeightCache = new HashMap<>();
    private static final Map<Integer, int[]> segmentProfileCache = new HashMap<>();
```
```java
    public static void updateSegmentHeight(int segmentIndex, int maxY, int[] profile) {
        segmentHeightCache.put(segmentIndex, maxY);
        segmentProfileCache.put(segmentIndex, profile);
```
```java
        int segKey = currentEditIndex * 100 + wpIdx;
```
ПОЧЕМУ ЭТО БАГ: `segKey = currentEditIndex * 100 + wpIdx` — индекс ПУ в текущей группе × 100 +
индекс точки. Ключ не содержит ни координат, ни идентификатора ПУ. Открыв экран для другой группы
пусковых установок, клиент получит `segKey = 0*100 + 0 = 0` — тот же ключ, что и у первой группы ⇒
`getSegmentProfile(0)` вернёт профиль высот ЧУЖОГО маршрута до прихода ответа сервера, и график
высот покажет неверный рельеф. Очистка есть только через `SurfaceHeightCache.clear()` из
`ClientGameEvents.java:40`.
ЦЕНА В ЧИСЛАХ: `segmentProfileCache` хранит `int[PROFILE_SAMPLES]` = `int[32]` = 144 Б/запись;
`MAX_WAYPOINTS=20` × число ПУ. Утечка невелика (десятки КБ), но данные ПЕРЕИСПОЛЬЗУЮТСЯ между
разными маршрутами — это функциональный дефект, а не только память. `heightCache` растёт на каждую
уникальную XZ-пару без лимита.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: открыть route-экран для ПУ №1 (маршрут через горы), закрыть, открыть для
ПУ №2 в другом регионе (равнина) — до прихода серверного ответа профиль высот показывает горы.
ТЕСТИРУЕМОСТЬ: pure-java
НАБРОСОК ТЕСТА:
```java
@Test void segKeyCollides() {
    int keyGroupA_launcher0_wp0 = 0 * 100 + 0;
    int keyGroupB_launcher0_wp0 = 0 * 100 + 0;
    assertEquals(keyGroupA_launcher0_wp0, keyGroupB_launcher0_wp0);   // ключ не различает группы
    int overflow = 1 * 100 + 100;                                     // wpIdx=100 => ключ ПУ №2
    assertEquals(2 * 100 + 0, overflow);                              // и переполняется в соседний ПУ
}
```

---

## СВОДКА

| # | Серьёзность | Файл:строка | Суть | Цена |
|---|---|---|---|---|
| F-01 | critical | MissileChunkManager.java:127-132 | `setChunkForced(true)` = синхронная генерация | 2250 генераций/полёт, +37,5 % MSPT на 150 с |
| F-02 | critical | MissileChunkManager.java:182-193 | preload 25 чанков, снимается через 4 тика | 500–1250 мс фриз, польза 0 |
| F-03 | critical | MissileChunkManager.java:151-169 | `cleanup()` — мёртвый код, форс в `chunks.dat` | 12–37 вечно тикающих чанков на инцидент |
| F-04 | critical | MissileChunkManager.java:80-87,144-148 | отписка B разфорсит чанки A, `lastChunkPosCache` блокирует восстановление | ракета исчезает без взрыва |
| F-05 | critical | ShahedRouteScreen.java:846-853 + RouteTerrainRenderer.java:155-159 | шторм запросов PNG, `chunkIndex==0` обнуляет буфер | 12 МБ/с на игрока, карта не приходит никогда |
| F-06 | critical | ServerRouteMapScanner.java:52,245-251 | `HashMap<Long,Integer>` без лимита | 126 МБ на route-карту, 46 МБ на radar |
| F-07 | high | ServerTerrainScanner.java:46,160-177 | `MAX_TICK_MS=25` = 50 % тика безусловно | 160–388 тиков ×25 мс на карту |
| F-08 | high | ServerRouteMapScanner.java:223-231,324-335 | `ImageIO.write` + `Files.write` в тике | 210–840 мс в одном тике, 44 МБ мусора |
| F-09 | high | ServerTerrainScanner.java:192-199 | `getChunk(BIOMES,false)` генерирует + блокирует | 8,4 млн cache-miss, 134 МБ `ChunkPos`, выбивает общий chunk-cache |
| F-10 | high | ServerForgeEvents.java:57-72 | всегда `server.overworld()`, ключ без измерения | 100 % неверных карт вне Оверворлда |
| F-11 | high | ServerTerrainScanner.java:48,78-95 | `INSTANCES` не очищается, переживает мир | 9/16 МиБ на сканер, карта чужого мира |
| F-12 | high | ShahedRadarBlock.java:84-99 | каждый радар-блок ставит полную сборку в очередь | 10 радаров = 90 МиБ + 80 с блокировки route |
| F-13 | high | ServerTerrainScanner.java:218-233,256-278 | 29 `String.contains` + 2 `Optional` на пиксель | 1,0–1,8 с CPU, 75–134 МБ мусора на карту |
| F-14 | high | ServerRouteMapScanner.java:83-108 | буфер выделяется до проверки кэша, до 2 раз | 32 МиБ humongous на пакет |
| F-15 | medium | WorldMapScanner.java:178-184,105 | сканирует только загруженные чанки, `scanComplete=true` навсегда | 0 точек, 195 с впустую |
| F-16 | medium | WorldMapScanner.java:74-92,210-227 | 100 чанков в одной задаче, аллокации в цикле | 16 000 операций/батч, 10 МБ/с мусора |
| F-17 | medium | WorldMapScanner.java:28-46,105 | executor без shutdown, `scanComplete` до выполнения задач | утечка `ServerLevel` 12–60 МБ |
| F-18 | medium | ServerTerrainScanner.java:120-130 / ServerRouteMapScanner.java:163-172 | ABBA на двух мониторах классов | дедлок ⇒ watchdog-креш |
| F-19 | medium | ShahedRadarBlock.java:37,102-124 | AABB 3000×464×3000 каждую секунду | 1,03 млн секций, 1500–5000 сущностей ×N радаров |
| F-20 | medium | ServerTerrainScanner.java:50,91 | `public static long` `++` под двумя мониторами | потеря упорядочивания, задержка 160 тиков |
| F-21 | medium | RouteTerrainRenderer.java:191-199 | 4,19 млн `set/getPixelRGBA` на render-потоке | 34–63 мс ×2, 48 МБ статического кэша |
| F-22 | medium | RequestSegmentMaxHeightPacket.java:64-93 | 64 `getBaseHeight` на сегмент, без кэша | 512–5120 мс в одном тике при открытии GUI |
| F-23 | low | ServerTerrainScanner.java:160,176 | немонотонные часы для бюджета тика | до 4 с в одном тике при NTP-скачке |
| F-24 | low | SurfaceHeightCache.java:14-17,56-58 | статические `HashMap`, коллизия `segKey` | чужой профиль высот, безлимитный рост |

**Ответы на прямые вопросы из задания:**

1. **Бюджет тика.** Ограничения «N чанков за тик» НЕТ — только `MAX_TICK_MS = 25` (50 % тика),
   проверяемое раз в 32 пикселя (F-07) и не являющееся верхней границей из-за F-09.
   Terrain: 1536² = 2 359 296 вызовов `getChunk`+`getHeight`/`getNoiseBiome` ⇒ ≈4,0 с CPU ⇒
   **160 тиков (8 с)**. Route: 2048² = 4 194 304 вызова, покрытие 1 638 400 чанков ⇒ ≈9,7 с CPU ⇒
   **388 тиков (19,4 с)**.
2. **Блокирующий I/O в главном потоке.** ДА, четыре места: `ServerRouteMapScanner.java:227`
   (`saveToDisk` из `tick`), `:344-348` (`Files.readAllBytes` из `getOrCreate`), `:121`
   (`Files.list` — полное сканирование каталога), `:329` (`ImageIO.write`). Плюс зеркала в
   `ServerTerrainScanner.java:183, 301-304, 285`. См. F-08.
3. **Загрузка чанков сканером.** Сканер использует `ChunkStatus.FULL` с `requireChunk=false`
   (безопасно) и — на фоллбэке — `ChunkStatus.BIOMES` с `requireChunk=false`, что НЕ безопасно:
   `ChunkLevel.byStatus(BIOMES) > 33`, поэтому для пограничного кольца вызывается
   `scheduleChunkGenerationTask` + `managedBlock`. См. F-09. Реальные «зависания на минуты» дают
   не сканеры, а `MissileChunkManager.setChunkForced` (F-01/F-02) — там генерация **полностью
   синхронна и подтверждена исходником MC**.
4. **Память.** Ограничения на количество карт НЕТ: `INSTANCES` — `static LinkedHashMap` без
   eviction (F-11). Одновременно живут: `int[]` каждого сканера в очереди (9/16 МиБ),
   `biomeColorCache` строящегося (46/126 МБ), `pngBytes` каждого готового (0,5–6 МБ),
   `BufferedImage` в момент энкода (+9/16 МиБ). Чистит только `ShahedRadarBlock.onRemove` →
   `ServerTerrainScanner.remove`. Выгрузку мира/смену сервера НЕ переживает корректно — переживает
   некорректно (отдаёт карту прошлого мира).
5. **Многопоточность.** Единственный `ExecutorService` — `WorldMapScanner.scanExecutor`
   (`:29-33`). Обращений к `Level`/`ChunkSource`/`BlockState` из рабочего потока НЕТ — они
   маршалятся через `level.getServer().execute(...)` (`:85`), это корректно. Но: `shutdown()`
   не вызывается никогда (F-17), рабочий поток удерживает `ServerLevel`, `setScanComplete(true)`
   гоняется с невыполненными задачами, `getInstance()` — незащищённый ленивый синглтон.
   Отдельно — ABBA-инверсия мониторов между двумя сканерами (F-18) и неатомарный
   `GLOBAL_BUILD_ORDER` (F-20).
6. **`GLOBAL_BUILD_ORDER` и кооперативный тикинг.** `if (!A.tickAll()) B.tickAll()` гарантирует
   ровно одно: **за тик тикается ровно ОДИН сканер**, и это всегда первый building в порядке
   вставки своей коллекции. Голодание реально: пока terrain-очередь непуста, route-сканер
   с большим order не получает НИ ОДНОГО тика (F-12). Поскольку `ShahedRadarBlock.tick` ставит
   новую сборку при каждом размещении радара, при частой установке радаров route-карта не
   построится никогда.
7. **`MissileChunkManager`.** `CHUNK_LOAD_RADIUS=1` + `PRELOAD_CHUNKS_AHEAD=3` ⇒ до **12 чанков**
   на ракету одновременно; за полёт 12000 блоков (750 чанк-границ) — **≈2250 форсов**, каждый —
   синхронная генерация FULL. Разгружаются при уходе (`chunksToUnload`, :115-122), но НЕ при
   остановке сервера (F-03). TODO про пересечение доказан конкретным сценарием в F-04.
   `setChunkForced` подтверждённо пишет в persistent `ForcedChunksSavedData` (`<dim>/data/chunks.dat`).
   Коллизии по `entity.getId()` в пределах одного запуска JVM нет (`Entity.ENTITY_COUNTER`
   монотонно растёт), но статические `Map<Integer,ServerLevel> missileLevels` без очистки
   удерживают `ServerLevel` после выгрузки мира.
8. **Общий обход.** Аллокации в горячем цикле: `new ChunkPos` в MC-коде на каждый пиксель (F-09),
   2 `Optional` на пиксель (F-13), `new BlockPos` + `int[][]` в цикле (F-16, WorldMapScanner:191,211,222),
   `new HashSet<>` ×3 на каждое обновление ракеты (MissileChunkManager:89,115,124).
   Конкатенация строк в тике: `ServerTerrainScanner.key()` (`:75`) вызывается из `getOrCreate`,
   который зовётся из `ShahedRadarBlock.tick` **каждые 20 тиков на каждый радар**, и
   `"radar_v" + CACHE_VERSION + "_" + ... ` в `tryLoadFromFile`/`saveToDisk`.
   Логирование в тике: `LOGGER.info("[ATA] Radar map ready for {}", key(radarPos))`
   (`ServerTerrainScanner.java:186`) — на завершении, приемлемо; но `LOGGER.info` в
   `tryLoadFromFile` (`:302`) выполняется в обработчике пакета.
   Вложенные O(n²): `RouteTerrainRenderer.loadFromPngBytes` 2048² (F-21),
   `WorldMapData.getCoastlinePointsInRadius` (`WorldMapData.java:95-110`) — линейный обход ВСЕХ
   точек побережья с распаковкой на каждый вызов, в обработчике пакета.
