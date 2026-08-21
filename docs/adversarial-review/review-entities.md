# АДВЕРСАРИАЛЬНОЕ РЕВЬЮ: сущности и физика полёта (ATA / NeoForge 21.1.230)

Прочитаны целиком: `ShahedEntity.java` (1800), `IskanderEntity.java` (1561), `OrlanEntity.java` (1357),
`ShahedLauncherEntity.java` (481), `OrlanLauncherEntity.java` (262), `PaintStandEntity.java` (395),
`MissileChunkManager.java` (204), `OrlanCameraExplosionHelper.java` (107).
Дополнительно для верификации: `ModEntities.java`, `ShahedConfig.java`, `ServerForgeEvents.java`,
`AirTacticalArsenal.java`, `ClientPacketHandler.java`, `LaunchFromUnifiedTerminalPacket.java`,
`IskanderRenderer.java`, `command/ShahedCommand.java`, `block/entity/IskanderRocketBlockEntity.java`.

Ванильные семантики проверены по декомпилированным исходникам из
`~/.gradle/caches/neoformruntime/intermediate_results/sourcesAndCompiledWithNeoForge_a860bc8a…_output.jar`
(`Entity.java`, `Vec3.java`, `PersistentEntitySectionManager.java`, `ServerLevel.java`).

**19 находок.** Ключевые: перманентная утечка force-loaded чанков на диск (F-1), тихое удаление всех
ракет в полёте при рестарте с фантомными взрывами (F-2), NaN-траектория с бессмертной сущностью (F-3).

---

### F-1: `remove(RemovalReason)` не вызывается при выгрузке чанка / смене измерения / остановке сервера — force-loaded чанки утекают НА ДИСК навсегда
СЕРЬЁЗНОСТЬ: critical
ФАЙЛ: `entity/ShahedEntity.java:874-890`, `entity/IskanderEntity.java:1274-1281`, `entity/OrlanEntity.java:1279-1317`, `chunk/MissileChunkManager.java:135-149`
КОД:
```java
// ShahedEntity.java:874
@Override
public void remove(RemovalReason reason) {
    if (!this.level().isClientSide) {
        ...
        MissileChunkManager.unregisterMissile(this);   // ЕДИНСТВЕННОЕ место освобождения чанков
        PacketDistributor.sendToAllPlayers(new ShahedRemovePacket(this.getId()));
    }
    super.remove(reason);
}
```
```java
// MissileChunkManager.java:130 — форс включается
level.setChunkForced(chunk.x, chunk.z, true);
```
ПОЧЕМУ ЭТО БАГ: ваниль вызывает `remove(RemovalReason)` **только** из `discard()`/`kill()`. Все прочие
пути удаления идут напрямую в `setRemoved(...)`, минуя override:
* `PersistentEntitySectionManager.java:235` → `p_157586_.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);`
* `Entity.java:2627` и `Entity.java:2760` → `this.setRemoved(Entity.RemovalReason.CHANGED_DIMENSION);`
* при остановке сервера сущности **сохраняются**, а не удаляются — `remove()` не зовётся вообще.

`Entity.java:358`: `public void remove(RemovalReason r) { this.setRemoved(r); }` — то есть `setRemoved` есть
низкоуровневый метод, а `remove` лишь обёртка, и её никто не гарантирует.

Далее, `ServerLevel.setChunkForced` (`ServerLevel.java:1394-1408`) пишет в **персистентное** хранилище:
```java
ForcedChunksSavedData forcedchunkssaveddata = this.getDataStorage().computeIfAbsent(ForcedChunksSavedData.factory(), "chunks");
...
flag = forcedchunkssaveddata.getChunks().add(i);
forcedchunkssaveddata.setDirty(flag);
```
Значит утёкшие форсы записываются в `data/chunks.dat` и переживают перезапуск сервера **бесконечно**.
`MissileChunkManager.cleanup()` (строка 151) — единственная страховка — **не вызывается нигде**
(проверено grep по всему `src/main/java`: ноль вызовов; в `AirTacticalArsenal.java:55` обработчик
`ServerStartingEvent` пустой, `ServerStoppingEvent`/`LevelEvent.Unload` не подписаны вообще).

СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ:
1. Запустить Shahed (`/ata shahed ...`), дать ему выйти в CRUISING — он держит 9-12 форс-чанков (`CHUNK_LOAD_RADIUS=1` + `PRELOAD_CHUNKS_AHEAD=3`).
2. Остановить сервер (`/stop`) пока ракета в воздухе.
3. Запустить сервер снова.
ОЖИДАЕМОЕ vs ФАКТИЧЕСКОЕ: ожидается 0 форс-чанков. Фактически `chunks.dat` содержит 9-12 записей,
`/forceload query` в этих координатах покажет их как загруженные, и они тикают вечно. Каждый последующий
цикл «запуск → рестарт» добавляет ещё 9-12. Для Орлана в PATROLLING (`OrlanEntity.java:399`,
`updateChunksWithRadius(this, serverLevel, 3)`) это 7×7+3 = **52 чанка за один дрон**.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
@GameTest
public void forcedChunksReleasedOnUnload(GameTestHelper h) {
    ServerLevel lvl = h.getLevel();
    ShahedEntity s = new ShahedEntity(lvl, new Vec3(0,150,0), new BlockPos(2000,64,0), "low");
    s.isLegitSpawn = true; lvl.addFreshEntity(s);
    h.runAfterDelay(40, () -> {
        int before = lvl.getForcedChunks().size();
        assertTrue(before > 0);
        s.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);   // ровно то, что делает ваниль
        assertEquals(0, lvl.getForcedChunks().size());          // ПАДАЕТ: остаётся `before`
    });
}
```

---

### F-2: `isLegitSpawn` не сохраняется в NBT у Shahed и Iskander — после релога/рестарта все ракеты в полёте удаляются, причём Shahed при этом ИМИТИРУЕТ ВЗРЫВ
СЕРЬЁЗНОСТЬ: critical
ФАЙЛ: `entity/ShahedEntity.java:117`, `entity/ShahedEntity.java:272-275`, `entity/ShahedEntity.java:918-966`, `entity/ShahedEntity.java:874-890`; `entity/IskanderEntity.java:139`, `entity/IskanderEntity.java:515-518`, `entity/IskanderEntity.java:1318-1374`
КОД:
```java
// ShahedEntity.java:272
@Override
public void tick() {
    if (!this.level().isClientSide && !this.isLegitSpawn) {
        this.discard();
        return;
    }
```
```java
// ShahedEntity.java:918 — addAdditionalSaveData: "LegitSpawn" отсутствует
tag.putInt("FlightPhase", this.flightPhase.ordinal());
tag.putInt("PhaseTicks", this.phaseTicks);
tag.putDouble("CurrentSpeed", this.currentSpeed);
...
tag.putString("ShahedColor", getShahedColor());
tag.putBoolean("OnPaintStand", isOnPaintStand());
```
```java
// ShahedEntity.java:876 — а remove() при этом стреляет спецэффектами
if (!this.exploding
        && (reason == RemovalReason.DISCARDED || reason == RemovalReason.KILLED)
        && this.flightPhase != FlightPhase.WARMUP) {
    this.exploding = true;
    spawnShootdownEffects(this.position());
}
```
ПОЧЕМУ ЭТО БАГ: `OrlanEntity`, `ShahedLauncherEntity`, `OrlanLauncherEntity`, `PaintStandEntity`
сохраняют `LegitSpawn` (`OrlanEntity.java:1105/1157`, `ShahedLauncherEntity.java:78/109` и т.д.), а
`ShahedEntity` и `IskanderEntity` — нет. Поле — `public boolean isLegitSpawn = false;`. При загрузке из
NBT `readAdditionalSaveData` восстанавливает `flightPhase` (строка 990-996), но не `isLegitSpawn`, тот
остаётся `false` → первый же тик делает `discard()`.
Далее `discard()` → `remove(DISCARDED)` → условие в строке 878 выполняется (`exploding==false`,
`reason==DISCARDED`, `flightPhase` восстановлена из NBT и равна CRUISING/DIVING ≠ WARMUP) →
`spawnShootdownEffects` → `PacketDistributor.sendToAllPlayers(new ShahedExplosionPacket(pos))` +
~1000 вызовов `sendParticlesForced` (см. F-4) + звук взрыва.
Для Iskander дополнительно не вызывается `releaseLauncher()` (см. F-8) — пусковая блокируется навсегда.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: запустить Shahed, подождать выхода в CRUISING, `/stop`, старт сервера, зайти.
ОЖИДАЕМОЕ vs ФАКТИЧЕСКОЕ: ожидается — дрон продолжает полёт. Фактически — дрон исчезает на первом тике,
и все игроки на сервере (независимо от расстояния и измерения) получают пакет взрыва и звук
`EXPLOSION_1..5` громкостью до 40.0f (`ClientPacketHandler.java:163`). При N ракетах в воздухе —
N фантомных взрывов в один тик.
ТЕСТИРУЕМОСТЬ: gametest (полный цикл) / pure-java (для факта отсутствия ключа в NBT)
НАБРОСОК ТЕСТА:
```java
@Test void legitSpawnSurvivesSerialization() {
    CompoundTag tag = new CompoundTag();
    ShahedEntity s = new ShahedEntity(level, new Vec3(0,150,0), BlockPos.ZERO, "low");
    s.isLegitSpawn = true;
    s.addAdditionalSaveData(tag);
    assertTrue(tag.contains("LegitSpawn"));  // ПАДАЕТ: ключа нет
    ShahedEntity s2 = new ShahedEntity(ModEntities.SHAHED.get(), level);
    s2.readAdditionalSaveData(tag);
    assertTrue(s2.isLegitSpawn);             // ПАДАЕТ: false
}
```

---

### F-3: Деление на ноль в `getTrajectoryHeight` → NaN-позиция → бессмертная ракета, вечно форсящая чанки
СЕРЬЁЗНОСТЬ: critical
ФАЙЛ: `entity/IskanderEntity.java:344-362`, `entity/IskanderEntity.java:308-316`, `entity/IskanderEntity.java:606-619`
КОД:
```java
// IskanderEntity.java:344 — ветка else срабатывает ИМЕННО когда diveStartProgress <= 0.001,
// и тут же делит на diveStartProgress
} else {
    double denom = 1.0 - diveStartProgress;
    if (denom < 0.001) return ballisticTargetY;
    double q2 = Math.max(0, Math.min(1, (progress - diveStartProgress) / denom));

    double arcSlopeEnd = (-4.0 * maxParabolicHeight + (diveStartY - ballisticStartY)) / diveStartProgress;
    double m0 = arcSlopeEnd * denom;
    ...
    return (2 * q2_3 - 3 * q2_2 + 1) * diveStartY
            + (q2_3 - 2 * q2_2 + q2) * m0
            + (-2 * q2_3 + 3 * q2_2) * ballisticTargetY
            + (q2_3 - q2_2) * m1;
}
```
```java
// IskanderEntity.java:314 — здесь diveStartProgress становится ровно 0.0
this.diveStartProgress = (this.totalBallisticTicks > 0 && arcTicks > 0)
        ? arcTicks / this.totalBallisticTicks
        : 0.0;
```
ПОЧЕМУ ЭТО БАГ: `if (diveStartProgress > 0.001 && progress <= diveStartProgress)` — первая ветка. Значит
в `else` попадаем в том числе при `diveStartProgress == 0.0`, и строка 350 делит на ноль.
Полная цепочка при `ballisticHorizDistance == 0` (цель ровно над/под пусковой):
- стр. 300: `horizDive = Math.min(horizDive, 0 * 0.40) = 0`
- стр. 308: `arcHorizDist = max(0, 0 - 0) = 0` → `arcTicks = 0` → `diveTicks = 0` → `totalBallisticTicks = 0`
- стр. 314: `diveStartProgress = 0.0`
- стр. 319: `maxParabolicHeight = max(10.0, min(…, отрицательное)) = 10.0`
- `getTrajectoryHeight(1.0)`: `arcSlopeEnd = (-40 + (diveStartY - ballisticStartY)) / 0.0 = -Infinity`,
  `m0 = -Infinity`, коэффициент `(q2³-2q2²+q2)` при `q2=1` равен `0` → **`0 * -Infinity = NaN`**.
Дальше `calculateMotion` (стр. 921-926) даёт `velocity.y = NaN`, `setPos(NaN)`.
Хуже всего — ракета **никогда не взорвётся**:
```java
// IskanderEntity.java:606
if (finalTargetPos != null && currentPos.y <= finalTargetPos.y   // NaN <= x  →  false
        && realMotion.y < 0 && phase >= PHASE_TERMINAL) { this.explode(); return; }
// IskanderEntity.java:612
if (trajectoryCalculated && totalBallisticTicks > 0 && ...) {    // totalBallisticTicks == 0 → пропуск
```
Сущность зависает с NaN-координатами навсегда. `blockPosition()` даёт `Mth.floor(NaN) == 0` → каждый тик
`MissileChunkManager.updateChunksForMissile` (стр. 544) форсит чанки вокруг (0,0) — и по F-1 они никогда
не освобождаются.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: `/ata shahed iskander 100 70 100 100 64 100` (start.x/z == target.x/z).
Команда не имеет никакой проверки дистанции (`command/ShahedCommand.java:76-93`). То же достигается
крафченым пакетом `LaunchFromUnifiedTerminalPacket` (см. F-9).
ОЖИДАЕМОЕ vs ФАКТИЧЕСКОЕ: ожидается взрыв в точке цели. Фактически `entity.getY() == NaN`,
`/data get entity <uuid> Pos` возвращает `[0.0d, NaN, 0.0d]`, сущность живёт до ручного `/kill`,
force-load на (0,0) остаётся навсегда даже после `/kill` (F-1 при UNLOADED_TO_CHUNK).
ТЕСТИРУЕМОСТЬ: pure-java (функция чистая относительно полей)
НАБРОСОК ТЕСТА:
```java
@Test void trajectoryHeightNeverNaN() {
    // повторяет арифметику IskanderEntity.getTrajectoryHeight при diveStartProgress == 0
    double diveStartProgress = 0.0, maxParabolicHeight = 10.0;
    double diveStartY = 64, ballisticStartY = 220, ballisticTargetY = 64, horizDive = 0;
    double progress = 1.0;
    double denom = 1.0 - diveStartProgress;              // 1.0, guard не срабатывает
    double q2 = (progress - diveStartProgress) / denom;  // 1.0
    double arcSlopeEnd = (-4.0*maxParabolicHeight + (diveStartY-ballisticStartY)) / diveStartProgress;
    double m0 = arcSlopeEnd * denom;
    double m1 = -Math.tan(Math.toRadians(75.0)) * horizDive;
    double q2_2 = q2*q2, q2_3 = q2_2*q2;
    double y = (2*q2_3-3*q2_2+1)*diveStartY + (q2_3-2*q2_2+q2)*m0
             + (-2*q2_3+3*q2_2)*ballisticTargetY + (q2_3-q2_2)*m1;
    assertFalse(Double.isNaN(y));   // ПАДАЕТ: y == NaN
}
```

---

### F-4: `sendParticlesForced` рассылает ~1000 отдельных пакетов ВСЕМ игрокам на каждый взрыв, и каждый вызов заново обходит реестр Орланов с парсингом UUID
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `entity/ShahedEntity.java:1569-1577`, `entity/ShahedEntity.java:699-775`, `event/OrlanCameraExplosionHelper.java:34-47`, `event/OrlanCameraExplosionHelper.java:69-83`
КОД:
```java
// ShahedEntity.java:1569
private <T extends ParticleOptions> void sendParticlesForced(ServerLevel level, T particle, double x, double y,
        double z, int count, double xOffset, double yOffset, double zOffset, double speed) {
    for (ServerPlayer player : level.players()) {
        level.sendParticles(player, particle, true, x, y, z, count, xOffset, yOffset, zOffset, speed);
    }
    com.synarsis.airtacticalarsenal.event.OrlanCameraExplosionHelper.sendParticleToFarCameraPlayers(
            level, particle, x, y, z, count, xOffset, yOffset, zOffset, speed);
}
```
```java
// ShahedEntity.java:699 — вызов в цикле по 400 итераций
int debrisCount = (int) (400 * mult);
for (int i = 0; i < debrisCount; i++) {
    ...
    sendParticlesForced(serverLevel, ModParticles.DEBRIS.get(), pos.x, pos.y + 1, pos.z, 1, vx, vy, vz, 1.0);
}
```
```java
// OrlanCameraExplosionHelper.java:69 — вызывается на КАЖДУЮ частицу
private static List<ServerPlayer> getFarCameraPlayersNear(ServerLevel level, double x, double y, double z) {
    List<ServerPlayer> result = new ArrayList<>();
    for (OrlanEntity orlan : OrlanEntity.getActiveOrlansWithCamera()) {   // новый ArrayList + обход реестра
        ...
        ServerPlayer player = resolvePlayer(level, orlan.getLinkedPlayerUUID());  // UUID.fromString + поиск в PlayerList
```
ПОЧЕМУ ЭТО БАГ: одна детонация Shahed выполняет `sendParticlesForced` 400 (debris) + 200 (flame) +
160 (smoke) + 1 + 120 (spark) + 2 + 30×3 (bigFire) + 15 (fireball) ≈ **1008 раз**. Каждый вызов:
(а) линейно обходит `level.players()` и конструирует отдельный `ClientboundLevelParticlesPacket` на
игрока, (б) вызывает `getActiveOrlansWithCamera()`, который аллоцирует `ArrayList` и обходит
`activeOrlanRegistry`, (в) для каждого камера-Орлана делает `UUID.fromString` + `getPlayerList().getPlayer`.
На сервере с 20 игроками — **≈20 000 пакетов частиц в один тик** на один взрыв.
`spawnShootdownEffects` (строки 1397-1484) содержит точную копию тех же циклов, то есть сбитый дрон стоит
столько же. Iskander (`IskanderEntity.java:1148-1172`) — ещё 500+300 итераций.
Умножается на F-2: после рестарта сервера с 5 ракетами в воздухе это ~100 000 пакетов в один тик.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: 20 игроков онлайн, `explosionParticleMultiplier=100`, взорвать один Shahed.
ОЖИДАЕМОЕ vs ФАКТИЧЕСКОЕ: ожидается ~10-20 пакетов (агрегированные `count`, как делает ваниль).
Фактически ~20 000 пакетов и заметный спайк tick-time (>200 мс).
ТЕСТИРУЕМОСТЬ: gametest (замер) / pure-java (подсчёт вызовов через мок)
НАБРОСОК ТЕСТА:
```java
@Test void explosionParticleCallCountIsBounded() {
    AtomicInteger calls = new AtomicInteger();
    // мок ServerLevel.sendParticles инкрементирует calls
    shahed.explode();
    assertTrue(calls.get() < 100, "got " + calls.get());  // ПАДАЕТ: ~1008 * players
}
```

---

### F-5: Клиент заново симулирует всю физику полёта Shahed, имея неинициализированные серверные поля — расхождение траектории плюс двойное интегрирование поверх lerp
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `entity/ShahedEntity.java:287-293`, `entity/ShahedEntity.java:316-362`, `entity/ShahedEntity.java:465`, `entity/ShahedEntity.java:555`, `entity/ShahedEntity.java:134-137`
КОД:
```java
// ShahedEntity.java:287 — клиент уже сдвинулся по lerp
if (this.level().isClientSide && this.lerpSteps > 0) {
    double dx = (this.lerpX - this.getX()) / this.lerpSteps;
    ...
    this.setPos(this.getX() + dx, this.getY() + dy, this.getZ() + dz);
    this.lerpSteps--;
}
...
// ShahedEntity.java:318 — и тут же считает СВОЮ физику
switch (this.flightPhase) {
    case CLIMBING:  motion = tickClimbingPhase(targetVec, currentPos); break;
    ...
// ShahedEntity.java:349
if (this.level().isClientSide) {
    ...
    this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);   // сдвиг №2
    return;
}
```
```java
// ShahedEntity.java:465 — CLIMBING на клиенте берёт launchDirection...
Vec3 horizontalDir = this.launchDirection;
// ...который на клиенте равен Vec3.ZERO (ShahedEntity.java:135: private Vec3 launchDirection = Vec3.ZERO;)
```
```java
// ShahedEntity.java:555 — CRUISING на клиенте берёт targetAltitude...
double heightDiff = this.targetAltitude - this.getY();
// ...который на клиенте равен 165.0 (ShahedEntity.java:137), а не синхронизированному TARGET_CRUISE_ALT
```
ПОЧЕМУ ЭТО БАГ: `launchDirection`, `launchStartPos`, `currentSpeed`, `targetAltitude`, `waypoints`,
`currentWaypointIndex` — **обычные поля, не `EntityDataAccessor`**. На клиенте сущность создаётся
конструктором `(EntityType, Level)` (строка 170), эти поля остаются в дефолтах. Синхронизируются только
`TARGET_POS`, `FLIGHT_PHASE_ID`, `SYNC_X_ROT`, `SYNC_Y_ROT`, `TARGET_CRUISE_ALT` (строки 250-268), причём
`TARGET_CRUISE_ALT` читается **только** в геттере `getTargetAltitude()` (строка 1085), а физика в
`tickCruisingPhase` использует сырое поле.
Следствия:
1. В фазе CLIMBING клиентский дрон летит **строго вверх** (`horizontalDir = (0,0,0)`, остаётся только
   `verticalSpeed = currentSpeed*sin(25°)`), тогда как сервер летит вперёд-вверх.
2. В фазе CRUISING клиент выравнивается на Y=165 независимо от заданной оператором высоты
   (`setTargetAltitude` в диапазоне 80..220, `ShahedEntity.java:1079`).
3. Позиция за тик меняется дважды: сначала на `1/lerpSteps` шага lerp, затем на полный `motion`.
   `lerpTo` (строка 240) ставит `lerpSteps = steps + 1`, а `ClientPacketHandler.java:56` вызывает
   `shahed.lerpTo(..., 3)` каждые `SYNC_INTERVAL=40` тиков поверх ванильного трекера с `updateInterval(1)`.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: запустить Shahed с `altitude=220` и смотреть на него в фазе CLIMBING.
ОЖИДАЕМОЕ vs ФАКТИЧЕСКОЕ: ожидается плавный набор под 25°. Фактически модель дёргается: она уходит
вертикально вверх на `0.63 блок/тик`, затем рывком возвращается назад на серверную позицию каждый тик.
Разница между клиентской и серверной высотой в CRUISING достигает `220 - 165 = 55 блоков`.
ТЕСТИРУЕМОСТЬ: pure-java (проверка, что клиентская симуляция вообще запускается) / manual (визуально)
НАБРОСОК ТЕСТА:
```java
@Test void clientDoesNotSimulatePhysics() {
    ShahedEntity client = new ShahedEntity(ModEntities.SHAHED.get(), clientLevel);
    client.getEntityData().set(FLIGHT_PHASE_ID, FlightPhase.CLIMBING.ordinal());
    Vec3 before = client.position();
    client.tick();
    assertEquals(before, client.position());   // ПАДАЕТ: y вырос, x/z нет
}
```

---

### F-6: `activeOrlanRegistry` — статическая карта, из которой запись удаляется только в `remove()`; при выгрузке чанка/смене мира она держит `OrlanEntity` → `Level` → весь мир
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `entity/OrlanEntity.java:56`, `entity/OrlanEntity.java:79-81`, `entity/OrlanEntity.java:435-437`, `entity/OrlanEntity.java:1279-1283`
КОД:
```java
// OrlanEntity.java:56
private static final Map<Integer, OrlanEntity> activeOrlanRegistry = new java.util.concurrent.ConcurrentHashMap<>();
// OrlanEntity.java:435 — единственное добавление, каждый серверный тик
if (!activeOrlanRegistry.containsKey(this.getId())) {
    activeOrlanRegistry.put(this.getId(), this);
}
// OrlanEntity.java:1282 — единственное удаление
activeOrlanRegistry.remove(this.getId());
```
ПОЧЕМУ ЭТО БАГ: удаление живёт внутри `remove(RemovalReason)`, который (см. F-1) не вызывается при
`UNLOADED_TO_CHUNK` и `CHANGED_DIMENSION`. Приземлившийся Орлан (`FlightPhase.LANDED`) сам снимает себя
с чанк-менеджера (`OrlanEntity.java:392-396`), после чего чанк спокойно выгружается — и запись в
`activeOrlanRegistry` остаётся навсегда. `OrlanEntity` держит ссылку на `Level` (поле `Entity.level`),
то есть утекает весь `ServerLevel` со всеми загруженными чанками и сущностями.
`clearRegistry()` (строка 79) — **не вызывается нигде** (проверено grep по всему `src/main/java`).
В однопользовательской игре JVM переживает выход в главное меню, поэтому «выход из мира → вход в другой
мир» оставляет старый `ServerLevel` в heap, а `getActiveOrlansByOwner`/`getActiveOrlansWithCamera`
(строки 58, 69) продолжают его обходить. `OrlanCameraExplosionHelper.getFarCameraPlayersNear`
(`OrlanCameraExplosionHelper.java:71-73`) вызывает `orlan.level()` на мёртвой сущности мёртвого мира.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: посадить Орлан, отойти на 200+ блоков чтобы чанк выгрузился, выйти в меню,
зайти в другой мир, снять heap dump.
ОЖИДАЕМОЕ vs ФАКТИЧЕСКОЕ: ожидается 0 достижимых `ServerLevel` от предыдущего мира. Фактически
`activeOrlanRegistry` → `OrlanEntity` → `ServerLevel` предыдущего мира удерживается как GC root.
ТЕСТИРУЕМОСТЬ: gametest + heap-инспекция / pure-java на факт отсутствия вызова `clearRegistry`
НАБРОСОК ТЕСТА:
```java
@Test void registryEmptyAfterUnload() {
    OrlanEntity o = spawnOrlan(); o.tick();
    assertFalse(OrlanEntity.getActiveOrlansWithCamera().isEmpty() && registrySize() == 0);
    o.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
    assertEquals(0, registrySize());   // ПАДАЕТ: 1
}
```

---

### F-7: Хитбокс Iskander смещён на 4 блока относительно видимой модели; `sized(1.0, 7.5)` полностью игнорируется
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `entity/IskanderEntity.java:68`, `entity/IskanderEntity.java:441-476`, `entity/ModEntities.java:23-29`, `client/renderer/IskanderRenderer.java:15,39`
КОД:
```java
// IskanderEntity.java:68
private static final double MODEL_Y_OFFSET = 4.0;
// IskanderEntity.java:441 — AABB строится вокруг pos, БЕЗ вычитания MODEL_Y_OFFSET
@Override
protected AABB makeBoundingBox() {
    int phase = this.entityData.get(FLIGHT_PHASE);
    Vec3 pos = this.position();
    if (phase == PHASE_PREPARING) {
        return new AABB(pos.x - 0.4, pos.y, pos.z - 0.4, pos.x + 0.4, pos.y + 4.0, pos.z + 0.4);
    }
    ...
    double endX = pos.x + dir.x * length;   // length = 2.0, width = 0.4
```
```java
// client/renderer/IskanderRenderer.java:39 — модель рисуется на 4 блока НИЖЕ origin
poseStack.translate(0, -MODEL_CENTER_OFFSET, 0);   // MODEL_CENTER_OFFSET = 4.0
```
```java
// IskanderEntity.java:787 — сам код признаёт смещение
Vec3 visualCenter = new Vec3(pos.x, pos.y - MODEL_Y_OFFSET, pos.z);
```
ПОЧЕМУ ЭТО БАГ: в полёте видимая ракета занимает примерно `[y-4-1.5, y-4+1.5]`, а её AABB —
`[y-0.4, y+2.4]` вдоль вектора движения. Пересечения нет вообще: минимум **2.6 блока зазора**.
Последствия:
1. `ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity)` (строка 598) считает столкновения
   по AABB → ракета «врезается» в объекты на 4 блока выше видимой геометрии и проходит сквозь то, во что
   визуально попадает.
2. Условие подрыва `currentPos.y <= finalTargetPos.y` (строка 606) сравнивает **origin**, а не носовую
   часть: к моменту взрыва видимая модель уже на 4 блока **под землёй**.
3. Заявленный в `ModEntities.java:26` размер `.sized(1.0f, 7.5f)` не используется вообще — override
   `makeBoundingBox()` его подменяет длиной 2.0 и полушириной 0.4.
4. `setPos` (строка 473) переопределён и зовёт `makeBoundingBox()` — то есть AABB зависит от
   `getDeltaMovement()`, а `setDeltaMovement` вызывается **после** `setPos` в `tickClientSide`
   (строки 700-701 — `setDeltaMovement` перед `setPos`, но в `tick()` строки 585 и 621 порядок обратный),
   поэтому AABB на сервере строится по motion **прошлого** тика.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: в фазе BALLISTIC встать так, чтобы модель прошла сквозь игрока.
ОЖИДАЕМОЕ vs ФАКТИЧЕСКОЕ: ожидается детонация при визуальном контакте. Фактически детонация происходит
когда игрок находится на 4 блока выше видимой ракеты; при прямом визуальном попадании — ничего.
ТЕСТИРУЕМОСТЬ: pure-java
НАБРОСОК ТЕСТА:
```java
@Test void boundingBoxMatchesRenderedModel() {
    IskanderEntity r = new IskanderEntity(ModEntities.ISKANDER.get(), level);
    r.setPos(0, 100, 0);
    r.setDeltaMovement(0, -4, 0);
    r.setPos(0, 100, 0);                     // пересчитать AABB
    AABB box = r.getBoundingBox();
    double visualCenterY = 100 - 4.0;        // MODEL_Y_OFFSET / MODEL_CENTER_OFFSET
    assertTrue(box.minY <= visualCenterY && visualCenterY <= box.maxY);  // ПАДАЕТ
}
```

---

### F-8: `releaseLauncher()` вызывается только из `explode()` — любое другое удаление Iskander навсегда блокирует пусковую установку
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `entity/IskanderEntity.java:191-205`, `entity/IskanderEntity.java:1128-1131`, `entity/IskanderEntity.java:1274-1281`
КОД:
```java
// IskanderEntity.java:191
private void releaseLauncher() {
    if (!launcherReleased && launcherPos != null && !this.level().isClientSide) {
        BlockState state = this.level().getBlockState(launcherPos);
        if (state.getBlock() instanceof LauncherBlock launcher) {
            launcher.setLoaded(this.level(), launcherPos, false);
            launcher.setLaunching(this.level(), launcherPos, false);
        }
        net.minecraft.world.level.block.entity.BlockEntity be = this.level().getBlockEntity(launcherPos);
        if (be instanceof com.synarsis.airtacticalarsenal.block.entity.LauncherBlockEntity launcherEntity) {
            launcherEntity.setInFlight(false);
        }
        launcherReleased = true;
    }
}
```
```java
// IskanderEntity.java:1128 — единственный вызов
private void explode() {
    if (!this.level().isClientSide) {
        releaseLauncher();
```
```java
// IskanderEntity.java:1274 — remove() его НЕ зовёт
@Override
public void remove(RemovalReason reason) {
    if (!this.level().isClientSide) {
        MissileChunkManager.unregisterMissile(this);
        PacketDistributor.sendToAllPlayers(new IskanderRemovePacket(this.getId()));
    }
    super.remove(reason);
}
```
ПОЧЕМУ ЭТО БАГ: пути удаления мимо `explode()`: (а) `discard()` из `tick()` при `!isLegitSpawn` после
рестарта (F-2), (б) `/kill`, (в) выгрузка чанка/смена измерения (F-1). Во всех случаях пусковая остаётся
в состоянии `LOADED=true, LAUNCHING=true, inFlight=true` — блок нельзя перезарядить, а
`LaunchFromUnifiedTerminalPacket.java:82-97` пропускает такую пусковую при выборе (`!LOADED` не
выполняется), то есть ПУ выбывает из игры безвозвратно.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: зарядить и запустить Iskander, `/stop` через 3 секунды, старт сервера.
ОЖИДАЕМОЕ vs ФАКТИЧЕСКОЕ: ожидается — пусковая свободна (`LOADED=false`). Фактически
`/data get block <launcher> ` показывает `inFlight=true` вечно, блоксостояние `LOADED=true, LAUNCHING=true`.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
@GameTest public void launcherReleasedOnDiscard(GameTestHelper h) {
    // поставить LauncherBlock, запустить ракету, затем discard() вместо взрыва
    iskander.discard();
    assertFalse(h.getLevel().getBlockState(launcherPos).getValue(LauncherBlock.LOADED)); // ПАДАЕТ
}
```

---

### F-9: `LaunchFromUnifiedTerminalPacket` не валидирует НИЧЕГО на сервере: ни дистанцию, ни чёрный/белый список, ни расстояние игрока до терминала
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `network/LaunchFromUnifiedTerminalPacket.java:65-136`
КОД:
```java
public static void handle(LaunchFromUnifiedTerminalPacket payload, IPayloadContext context) {
    context.enqueueWork(() -> {
        Player rawPlayer = context.player();
        if (!(rawPlayer instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();
        ...
        int targetX = payload.targetX();
        int targetZ = payload.targetZ();
        ...
        int targetY = ShahedConfig.TARGET_Y_LEVEL.get();
        BlockPos targetPos = new BlockPos(targetX, targetY, targetZ);
        ...
        if (missileType == 0) {
            ShahedEntity shahed = new ShahedEntity(level, launchPos, targetPos, "low");
```
ПОЧЕМУ ЭТО БАГ: параллельные обработчики валидацию делают —
`network/LaunchShahedFromTerminalPacket.java:48` (`ShahedConfig.isInForbiddenZone`) и
`network/LaunchRocketPacket.java:61-73` (`getIskanderMinDistance` / `getIskanderMaxRange` /
`isInForbiddenZone`). Проверки в `client/gui/UnifiedTerminalScreen.java:325-332` — **клиентские**, то есть
удаляются в модифицированном клиенте. Единственный серверный чек здесь — «не под водой» (строки 118-122).
Последствия: (а) обход чёрного списка зон целиком; (б) `targetX/targetZ` — сырые `int`, можно передать
координаты за границей мира; (в) `targetX == launchPos.x && targetZ == launchPos.z` даёт NaN-ракету из
F-3; (г) нет проверки расстояния игрока до `terminalPos` — можно стрелять с другого конца карты, зная
координаты чужого терминала; (д) `launcherPositions` берутся из пакета, а не с сервера — можно указать
чужие пусковые.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: отправить `LaunchFromUnifiedTerminalPacket(targetX = launcherX,
targetZ = launcherZ, terminalPos = <чужой терминал>, launcherPositions=[<чужая ПУ>], missileType=1)`.
ОЖИДАЕМОЕ vs ФАКТИЧЕСКОЕ: ожидается отказ (мин. дистанция 1000 по `iskanderMinDistance`). Фактически
запуск проходит, `ballisticHorizDistance == 0` → NaN-ракета (F-3) → вечный force-load.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
@GameTest public void unifiedTerminalRejectsOutOfRange(GameTestHelper h) {
    int before = countEntities(IskanderEntity.class);
    LaunchFromUnifiedTerminalPacket.handle(
        new LaunchFromUnifiedTerminalPacket(launcherX, launcherZ, terminalPos, List.of(launcherPos), 1), ctx);
    assertEquals(before, countEntities(IskanderEntity.class));   // ПАДАЕТ: ракета создана
}
```

---

### F-10: Iskander рассылает полный spawn-пакет ВСЕМ игрокам КАЖДЫЙ тик (`SYNC_INTERVAL = 1`), поверх ванильного трекера
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `entity/IskanderEntity.java:112`, `entity/IskanderEntity.java:634-647`, `entity/ModEntities.java:23-29`
КОД:
```java
// IskanderEntity.java:112
private static final int SYNC_INTERVAL = 1;
// IskanderEntity.java:634
this.syncTickCounter++;
if (this.syncTickCounter >= SYNC_INTERVAL) {
    this.syncTickCounter = 0;
    PacketDistributor.sendToAllPlayers(
            new IskanderSpawnPacket(this.getId(), this.position(), target, phase, flightTicks, launcherPos,
                    this.startPos, this.launchEndPos, this.finalTargetPos));

    if (launcherPos != null && phase >= PHASE_LAUNCH) {
        PacketDistributor.sendToAllPlayers(new LauncherStateUpdatePacket(
                launcherPos, LauncherStateUpdatePacket.LauncherState.IN_FLIGHT, 0, 0, flightTicks));
    }
}
```
ПОЧЕМУ ЭТО БАГ: `PacketDistributor.sendToAllPlayers` игнорирует расстояние **и измерение**. Пакет несёт
3×`Vec3` + `BlockPos`×2 + 2×`int` ≈ 100 байт. Плюс второй пакет `LauncherStateUpdatePacket`. Итого
2 пакета × 20 тиков/сек × N игроков **на каждую ракету**. Для 50 игроков — 2000 пакетов/сек на одну
ракету, отправляемых в том числе игрокам в Незере за 10 000 блоков.
Всё это дублирует ванильный трекер: `ModEntities.java:27-28` уже задаёт
`.clientTrackingRange(800).updateInterval(1).setShouldReceiveVelocityUpdates(true)`, то есть позиция и
так синхронизируется каждый тик штатным механизмом с фильтрацией по дальности.
Для Shahed то же самое, но `SYNC_INTERVAL = 40` (`ShahedEntity.java:154`), для Орлана — 20
(`OrlanEntity.java:168`), то есть у Iskander значение отличается в 40 раз без каких-либо оснований.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: запустить одну ракету на сервере с 20 игроками, снять сетевой профиль.
ОЖИДАЕМОЕ vs ФАКТИЧЕСКОЕ: ожидается — трафик только игрокам в радиусе 800 блоков. Фактически — 40
дополнительных пакетов в секунду каждому игроку сервера, включая находящихся в других измерениях.
ТЕСТИРУЕМОСТЬ: gametest (счётчик пакетов)
НАБРОСОК ТЕСТА:
```java
@GameTest public void noPacketsToFarPlayers(GameTestHelper h) {
    ServerPlayer far = spawnPlayerAt(50_000, 64, 50_000);
    int before = countPacketsSentTo(far);
    tickIskanderFor(20);
    assertEquals(before, countPacketsSentTo(far));   // ПАДАЕТ: +40
}
```

---

### F-11: У Shahed нет никакого лимита времени жизни — «застрявший» дрон вечно держит force-loaded чанки
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `entity/ShahedEntity.java:270-435`, `entity/ShahedEntity.java:392-394`, `entity/ShahedEntity.java:407-421`
КОД:
```java
// ShahedEntity.java:392 — форс чанков каждый тик
if (this.level() instanceof ServerLevel serverLevel) {
    MissileChunkManager.updateChunksForMissile(this, serverLevel);
}
...
// ShahedEntity.java:407 — подрыв возможен ТОЛЬКО если isDiving
if (this.isDiving) {
    double surfaceY = findSurfaceY(target);
    if (this.getY() <= surfaceY + 2.0) { this.explode(); return; }
    ...
}
```
ПОЧЕМУ ЭТО БАГ: `Projectile`/`Entity` не имеют автодеспавна. В `ShahedEntity` `tickCount` используется
только для `% 2/3/4/5` в частицах (строки 1350, 1360, 1367, 1639, 1692, 1713) — счётчика жизни нет.
Сравните: у Орлана есть `MAX_FLIGHT_TICKS = 24000` (`OrlanEntity.java:114`) и переход в RETURNING
(строки 415-418), у Iskander — обязательный подрыв по `progress >= 1.0` (строка 615). У Shahed — ничего.
Переход в DIVING требует одновременно `horizontalDist < diveTransitionDistance` **и** отклонения курса
< 15° (`ShahedEntity.java:564-583`). При `TURN_RATE_DEG = 1.5` (строка 146) и скорости 1.5 бл/тик радиус
разворота ≈ 57 блоков — если цель окажется внутри круга разворота (например, при запуске из
UnifiedTerminal без проверки минимальной дистанции, F-9), дрон будет **бесконечно кружить**, каждый тик
форся 9-12 чанков.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: `/ata shahed <start> <target на 50 блоков от start> low` (команда не проверяет
`getShahedMinDistance()`).
ОЖИДАЕМОЕ vs ФАКТИЧЕСКОЕ: ожидается подрыв или самоуничтожение по таймауту. Фактически дрон нарезает
круги неограниченно долго; `/forceload query` показывает постоянно движущийся кластер из 9-12 чанков.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
@GameTest(timeoutTicks = 30000) public void shahedSelfTerminates(GameTestHelper h) {
    ShahedEntity s = launchAt(new Vec3(0,150,0), new BlockPos(50,64,0));
    h.runAfterDelay(24000, () -> assertTrue(s.isRemoved()));   // ПАДАЕТ: жив
}
```

---

### F-12: Перекрывающиеся ракеты снимают форс с чужих чанков — вторая ракета летит через невыгруженную местность и никогда не восстановит форс
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `chunk/MissileChunkManager.java:30-33`, `chunk/MissileChunkManager.java:115-132`
КОД:
```java
// MissileChunkManager.java:115
Set<ChunkPos> chunksToUnload = new HashSet<>(currentLoaded);
chunksToUnload.removeAll(chunksToLoad);
for (ChunkPos chunk : chunksToUnload) {
    level.setChunkForced(chunk.x, chunk.z, false);   // глобальный флаг, не per-entity ticket
    currentLoaded.remove(chunk);
}
Set<ChunkPos> chunksToLoadNew = new HashSet<>(chunksToLoad);
chunksToLoadNew.removeAll(currentLoaded);            // ← «уже загружен по нашему учёту» ⇒ пропустить
for (ChunkPos chunk : chunksToLoadNew) {
    level.setChunkForced(chunk.x, chunk.z, true);
    currentLoaded.add(chunk);
}
```
ПОЧЕМУ ЭТО БАГ: TODO в шапке класса (строки 30-33) признаёт проблему, но она не смягчена. `ChunkPos`
хранится в per-missile сете, а `setChunkForced` — глобальный булев флаг уровня. Сценарий: ракета A и
ракета B обе держат чанк C. A уходит вперёд → `setChunkForced(C, false)` — чанк реально разгружается.
У B чанк C всё ещё в `currentLoaded`, поэтому `chunksToLoadNew.removeAll(currentLoaded)` его отфильтрует
и **B никогда не форснёт C заново**. B летит через невыгруженный чанк: `findSurfaceY`
(`ShahedEntity.java:652-663`) вернёт `target.getY()`, `getBlockState` вернёт void air, проверка
столкновения (`ShahedEntity.java:415-420`) не сработает — дрон пролетит сквозь гору.
Симметрично `preloadTargetArea` (`MissileChunkManager.java:171-193`) добавляет 25 чанков цели в
`currentLoaded`; при первом же пересечении границы чанка `chunksToUnload` вычтет их из
`chunksToLoad` и **снимет форс со всей зоны цели** — предзагрузка бессмысленна.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: запустить два Shahed по одному маршруту с разницей 5 тиков.
ОЖИДАЕМОЕ vs ФАКТИЧЕСКОЕ: ожидается корректная работа обоих. Фактически второй дрон летит через
невыгруженные чанки; `MissileChunkManager.getLoadedChunkCount(b)` рапортует 9, а
`level.getForcedChunks()` содержит только чанки первого.
ТЕСТИРУЕМОСТЬ: pure-java (логика учёта отделима) / gametest
НАБРОСОК ТЕСТА:
```java
@GameTest public void overlappingMissilesKeepChunks(GameTestHelper h) {
    MissileChunkManager.registerMissile(a, lvl);
    MissileChunkManager.registerMissile(b, lvl);   // тот же чанк
    a.setPos(a.getX() + 64, a.getY(), a.getZ());
    MissileChunkManager.updateChunksForMissile(a, lvl);
    assertTrue(lvl.getForcedChunks().contains(b.chunkPosition().toLong()));   // ПАДАЕТ
}
```

---

### F-13: Клиент Iskander считает траекторию сам, включая собственный случайный КВО и собственный `ballisticStartTick`
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `entity/IskanderEntity.java:650-652`, `entity/IskanderEntity.java:850-910`, `entity/IskanderEntity.java:415-423`, `entity/IskanderEntity.java:526-540`
КОД:
```java
// IskanderEntity.java:650 — клиентский тик безусловно зовёт calculateMotion
private void tickClientSide(int flightTicks, int phase, BlockPos target) {
    Vec3 visualMotion = calculateMotion(flightTicks, phase);
```
```java
// IskanderEntity.java:855 — а calculateMotion имеет побочные эффекты
if (this.startPos == null) {
    this.startPos = this.position();
    BlockPos target = this.entityData.get(TARGET_POS);
    calculateTrajectoryParameters(this.startPos, target);   // → applyKVO()
}
...
// IskanderEntity.java:901
if (launchEndPos == null) {
    launchEndPos = currentPos;
    launchEndPosSet = true;
    transitionTicks = 0;
    calculateBallisticTrajectory(flightTicks);              // ballisticStartTick = КЛИЕНТСКИЙ тик
}
```
```java
// IskanderEntity.java:415 — КВО берётся из this.random, на клиенте это другой поток случайности
private void applyKVO() {
    if (!kvoApplied) {
        double kvoDeviation = ShahedConfig.getIskanderKvoDeviation();
        double offsetX = (this.random.nextDouble() - 0.5) * 2 * kvoDeviation;
        double offsetZ = (this.random.nextDouble() - 0.5) * 2 * kvoDeviation;
        this.finalTargetPos = this.finalTargetPos.add(offsetX, 0, offsetZ);
```
ПОЧЕМУ ЭТО БАГ: `calculateTrajectoryParameters` → `applyKVO()` использует `this.random` — на клиенте это
независимый ГПСЧ, поэтому клиентская `finalTargetPos` отличается от серверной на величину до
`±iskanderKvoDeviation` (по умолчанию ±3 блока) по каждой оси, пока не придёт `syncTrajectoryData`.
Аналогично `calculateBallisticTrajectory(flightTicks)` на клиенте ставит `ballisticStartTick` в
**клиентский** `clientFlightTicks`, а не в `prepareTicks + launchTicks` (сервер, `IskanderEntity.java:265`
явно чинит это только в `syncTrajectoryData`). А `clientFlightTicks` (строки 526-537) — собственный
счётчик, ресинхронизируемый только при расхождении > 10 тиков:
```java
if (Math.abs(this.clientFlightTicks - flightTicks) > 10) { this.clientFlightTicks = flightTicks; }
else { this.clientFlightTicks++; }
```
При `iskanderCruiseSpeed = 4.0` допуск в 10 тиков — **40 блоков** отклонения отрисовки.
Также `calculateTrajectoryParameters` → `findGroundLevel` (строка 402) сканирует 150 блоков на клиенте.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: наблюдать ракету в фазе BALLISTIC, сравнить `/data get entity` (сервер) и
позицию отрисовки.
ОЖИДАЕМОЕ vs ФАКТИЧЕСКОЕ: ожидается расхождение < 1 блока. Фактически до 40 блоков по горизонтали
плюс до 6 блоков от разного КВО.
ТЕСТИРУЕМОСТЬ: pure-java (детерминизм КВО) / manual (визуально)
НАБРОСОК ТЕСТА:
```java
@Test void kvoIsDeterministicAcrossSides() {
    IskanderEntity srv = new IskanderEntity(serverLevel, start, target);
    IskanderEntity cli = new IskanderEntity(clientLevel, start, target);
    assertEquals(srv.getFinalTargetPos(), cli.getFinalTargetPos());   // ПАДАЕТ
}
```

---

### F-14: Высота крейсера Shahed, заданная оператором, затирается каждый тик самоссылочной формулой; ветка `currentWaypointIndex == 0` — тождественный тернарник
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `entity/ShahedEntity.java:508-524`, `entity/ShahedEntity.java:1061-1082`, `entity/ShahedLauncherEntity.java:313-319`, `network/ChangeShahedAltitudePacket.java:43-45`
КОД:
```java
// ShahedEntity.java:508
double prevAlt;
Vec3 prevPos;
if (this.currentWaypointIndex == 0) {
    prevAlt = this.launchStartPos.y > 0 ? this.targetAltitude : this.targetAltitude;   // обе ветки одинаковы
    prevPos = this.launchStartPos;
} else {
    BlockPos prevWp = this.waypoints.get(this.currentWaypointIndex - 1);
    prevAlt = prevWp.getY();
    prevPos = Vec3.atCenterOf(prevWp);
}
...
double segProgress = segLen > 1 ? Math.min(1.0, distFromPrev / segLen) : 1.0;
this.targetAltitude = prevAlt + (wp.getY() - prevAlt) * segProgress;   // prevAlt == this.targetAltitude
```
ПОЧЕМУ ЭТО БАГ: на нулевом сегменте `prevAlt` — это **мутирующее поле** `this.targetAltitude`, а не
константная высота начала сегмента. Формула вырождается в рекуррентный фильтр
`alt ← alt + (wpY − alt)·p`, который геометрически стягивает высоту к `wp.getY()` вместо линейной
интерполяции. Поскольку точки маршрута берутся с карты и несут поверхностный Y (обычно 64-80), дрон
**снижается к рельефу** во время крейсерского полёта.
Одновременно это уничтожает настройку высоты: `ShahedLauncherEntity.java:313-319` сначала зовёт
`setWaypoints` (который ставит `targetAltitude = waypoints.get(0).getY()`, `ShahedEntity.java:1066`),
затем `setTargetAltitude(launchAltitude)`; но в CRUISING формула выше немедленно уводит высоту обратно к
Y точки маршрута. То же с `ChangeShahedAltitudePacket.java:45` — изменение высоты в полёте отменяется на
следующем тике, если есть маршрут.
Тождественный тернарник в строке 511 — прямое свидетельство, что задуманная логика («если стартовая
высота известна — использовать её») была потеряна.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: маршрут из 2 точек с Y=70, `launchAltitude = 200`.
ОЖИДАЕМОЕ vs ФАКТИЧЕСКОЕ: ожидается полёт на Y=200. Фактически `targetAltitude` за ~100 тиков
сходится к 70, дрон снижается к земле и, вероятно, врезается в рельеф.
ТЕСТИРУЕМОСТЬ: pure-java
НАБРОСОК ТЕСТА:
```java
@Test void operatorAltitudeIsHonoured() {
    ShahedEntity s = launched();
    s.setWaypoints(List.of(new BlockPos(1000, 70, 0)));
    s.setTargetAltitude(200);
    for (int i = 0; i < 200; i++) s.tick();
    assertEquals(200.0, s.getTargetAltitude(), 5.0);   // ПАДАЕТ: ≈70
}
```

---

### F-15: `tickClimbingPhase` Орлана использует константу `PATROL_ALTITUDE` вместо `this.targetAltitude` — переход в CRUISING не зависит от заданной высоты
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `entity/OrlanEntity.java:548-583`, `entity/OrlanEntity.java:1343-1346`, `network/OrlanAltitudePacket.java:41-44`
КОД:
```java
// OrlanEntity.java:556 — угол набора считается от targetAltitude
double remainingHeight = this.targetAltitude - this.getY();
...
// OrlanEntity.java:575 — а условие завершения набора от КОНСТАНТЫ
boolean reachedAltitude = this.getY() >= PATROL_ALTITUDE - 10.0;   // PATROL_ALTITUDE = 200.0
boolean reachedDistance = distanceFromLaunch >= CLIMB_DISTANCE;
if (reachedAltitude && reachedDistance) {
    transitionToPhase(FlightPhase.CRUISING);
}
```
ПОЧЕМУ ЭТО БАГ: `setTargetAltitude` (строка 1343) допускает 100..350, `OrlanAltitudePacket.java:43` —
тот же диапазон. Две несогласованные величины в одном методе:
* `targetAltitude = 350` → на Y=190 `remainingHeight = 160 > 80`, угол 25°, но условие `reachedAltitude`
  уже истинно → переход в CRUISING на Y≈190, дальше набор идёт только через `verticalCorrection`,
  ограниченный `±0.3` (строка 617) → 160 блоков за 533+ тика.
* `targetAltitude = 100` → `remainingHeight` отрицательный уже на старте → `climbAngleRad = 3°`, но
  `reachedAltitude` требует Y ≥ 190 → дрон **перелетает заданную высоту на 90 блоков**, потом снижается.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: запустить Орлан, планшетом выставить высоту 120, наблюдать фазу CLIMBING.
ОЖИДАЕМОЕ vs ФАКТИЧЕСКОЕ: ожидается переход в CRUISING около Y=120. Фактически переход около Y=190,
затем снижение до 120 — лишние ~140 секунд полёта из бюджета `MAX_FLIGHT_TICKS`.
ТЕСТИРУЕМОСТЬ: pure-java
НАБРОСОК ТЕСТА:
```java
@Test void climbEndsAtRequestedAltitude() {
    OrlanEntity o = launched();
    o.setTargetAltitude(120);
    while (o.getFlightPhase() == FlightPhase.CLIMBING) o.tick();
    assertTrue(o.getY() < 135, "y=" + o.getY());   // ПАДАЕТ: ≈190
}
```

---

### F-16: Режим пикирования Shahed («high» / «low») не влияет ни на что — обе ветки присваивают одинаковые константы в трёх местах
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `entity/ShahedEntity.java:185-191`, `entity/ShahedEntity.java:299-305`, `entity/ShahedEntity.java:977-983`
КОД:
```java
// ShahedEntity.java:185 (конструктор)
if (diveMode.equals("high")) {
    this.diveDistance = 300.0;
    this.diveTransitionDistance = 350.0;
} else {
    this.diveDistance = 300.0;
    this.diveTransitionDistance = 350.0;
}
```
```java
// ShahedEntity.java:299 (каждый тик — ещё и лишняя работа в горячем цикле)
if (mode.equals("high")) {
    this.diveDistance = 300.0;
    this.diveTransitionDistance = 350.0;
} else {
    this.diveDistance = 300.0;
    this.diveTransitionDistance = 350.0;
}
```
ПОЧЕМУ ЭТО БАГ: `DIVE_ANGLE_DEG = 75.0` (строка 79) и `DIVE_ACTIVATION_DISTANCE = 20.0` (строка 77) не
используются нигде в `tickDivingPhase` (строки 588-623) — там жёстко зашиты пороги 5.0 и 30.0.
Единственные величины, зависящие от режима, — `diveDistance`/`diveTransitionDistance` — одинаковы.
Режим протаскивается через весь стек (`ShahedLauncherEntity.diveMode`, NBT `DiveMode`,
`ShahedSpawnPacket`, GUI, `ShahedEngineSoundClient`) и нигде не меняет поведение полёта.
`WARMUP_DURATION_TICKS = 100` (строка 61) — аналогично мёртвая константа: фаза WARMUP немедленно
переходит в LAUNCHING в первом же тике (строка 321), задержка в 5 секунд не наступает никогда.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: запустить два Shahed с идентичными параметрами, кроме `diveMode`.
ОЖИДАЕМОЕ vs ФАКТИЧЕСКОЕ: ожидаются разные профили пикирования. Фактически траектории побайтово
идентичны.
ТЕСТИРУЕМОСТЬ: pure-java
НАБРОСОК ТЕСТА:
```java
@Test void diveModeAffectsTrajectory() {
    ShahedEntity lo = new ShahedEntity(lvl, start, target, "low");
    ShahedEntity hi = new ShahedEntity(lvl, start, target, "high");
    assertNotEquals(lo.getDiveTransitionDistance(), hi.getDiveTransitionDistance());  // ПАДАЕТ: 350 == 350
}
```

---

### F-17: `findSurfaceY` сканирует 384 блока ДВАЖДЫ за тик на каждый пикирующий Shahed
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `entity/ShahedEntity.java:652-663`, `entity/ShahedEntity.java:335`, `entity/ShahedEntity.java:408`
КОД:
```java
// ShahedEntity.java:652
private double findSurfaceY(BlockPos target) {
    if (this.level() == null) return target.getY();
    for (int y = 320; y >= -64; y--) {                       // 385 итераций, всегда сверху
        BlockPos checkPos = new BlockPos(target.getX(), y, target.getZ());
        if (!this.level().getBlockState(checkPos).isAir()) { return y + 1; }
    }
    return target.getY();
}
```
```java
// ShahedEntity.java:335 — вызов №1 (ветка DIVING switch)
Vec3 diveTarget = new Vec3(targetVec.x, findSurfaceY(target), targetVec.z);
// ShahedEntity.java:408 — вызов №2 (проверка подрыва) в том же тике
double surfaceY = findSurfaceY(target);
```
ПОЧЕМУ ЭТО БАГ: результат детерминирован (одна и та же колонка `target`) и не меняется в течение полёта,
но пересчитывается 2× за тик — до **770 `getBlockState` на тик на одну ракету**, каждый с поиском
чанка + секции. Ваниль для этого предоставляет `level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)` —
O(1) по хитмапу. При роевой атаке в 20 Shahed — 15 400 `getBlockState` на тик.
Дополнительно: `IskanderEntity.findGroundLevel` (строки 402-413) сканирует 150 блоков и вызывается из
`calculateTrajectoryParameters`, в том числе **на клиенте** (см. F-13).
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: запустить 20 Shahed на одну цель, профилировать тик в фазе DIVING.
ОЖИДАЕМОЕ vs ФАКТИЧЕСКОЕ: ожидается O(1) через хитмап. Фактически 15 400 обращений к блокам/тик.
ТЕСТИРУЕМОСТЬ: gametest (счётчик через мок `Level`)
НАБРОСОК ТЕСТА:
```java
@Test void surfaceLookupIsCached() {
    CountingLevel lvl = new CountingLevel();
    ShahedEntity s = divingShahed(lvl);
    lvl.resetCounter(); s.tick();
    assertTrue(lvl.blockStateCalls() < 50, "" + lvl.blockStateCalls());  // ПАДАЕТ: ~770
}
```

---

### F-18: `syncNearbyEntities` шлёт `ClientboundRemoveEntitiesPacket` для сущностей, которыми владеет ванильный трекер — они пропадают у оператора навсегда
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `entity/OrlanEntity.java:999-1053`, `entity/OrlanEntity.java:1055-1070`, `entity/OrlanEntity.java:518-520`
КОД:
```java
// OrlanEntity.java:1004
double scanRadius = chunkRadius * 16.0;                     // до 10*16 = 160
AABB scanBox = new AABB(
        this.getX() - scanRadius, this.getY() - 300, this.getZ() - scanRadius,
        this.getX() + scanRadius, this.getY() + 300, this.getZ() + scanRadius);
List<Entity> nearbyEntities = level.getEntities(this, scanBox, e -> e != viewer && e.isAlive() && ...);
...
// OrlanEntity.java:1040
Set<Integer> removed = new HashSet<>();
for (int eid : trackedCameraEntities) {
    if (!currentNearby.contains(eid)) { removed.add(eid); }
}
if (!removed.isEmpty()) {
    viewer.connection.send(new ClientboundRemoveEntitiesPacket(removed.stream().mapToInt(Integer::intValue).toArray()));
}
```
ПОЧЕМУ ЭТО БАГ: `trackedCameraEntities` — собственный набор мода, никак не пересечённый с
`ChunkMap.TrackedEntity` ванили. Если оператор физически стоит рядом с сущностью, которую дрон тоже
«увидел», мод пришлёт `ClientboundAddEntityPacket` (строка 1019) поверх уже существующей записи, а при
выходе сущности из зоны сканирования — `ClientboundRemoveEntitiesPacket`. Ванильный трекер считает, что
сущность по-прежнему отслеживается, и `ClientboundAddEntityPacket` больше не пришлёт (он шлётся только в
`TrackedEntity#updatePlayer` при **первом** входе в радиус). Сущность становится невидимой на клиенте до
выхода игрока за `clientTrackingRange` и обратно.
Тот же эффект даёт `clearCameraTracking` (строка 1055-1070): при отвязке камеры он шлёт remove на все
`trackedCameraEntities` без последующего ресинка (ресинк есть только на пути `remove()`,
`OrlanEntity.java:1302`).
Дополнительно это дорого: бокс 320×600×320 блоков сканируется каждые 3 тика (строка 518), и на каждую
найденную сущность отправляется `ClientboundTeleportEntityPacket` + (при движении)
`ClientboundSetEntityMotionPacket` (строки 1033-1036).
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: войти в камеру Орлана, стоя рядом с другим игроком/мобом; дать дрону улететь
за 160 блоков; выйти из камеры.
ОЖИДАЕМОЕ vs ФАКТИЧЕСКОЕ: ожидается, что сосед виден. Фактически он исчезает с экрана и не возвращается
до перезахода или ухода за пределы трекинга.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
@GameTest public void cameraDoesNotDesyncVanillaTracker(GameTestHelper h) {
    ServerPlayer op = ...; Entity cow = h.spawn(EntityType.COW, opPos);
    enterCamera(op, orlan);
    flyOrlanFarAway();  // корова покидает scanBox
    assertTrue(clientOf(op).level().getEntity(cow.getId()) != null);   // ПАДАЕТ: null
}
```

---

### F-19: Взрыв Shahed рассылается всем игро­кам без проверки измерения; клиент на каждый взрыв создаёт по два «сырых» `Thread`
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `entity/ShahedEntity.java:692`, `entity/ShahedEntity.java:1399`, `network/ClientPacketHandler.java:86-158`, `network/ClientPacketHandler.java:160-172`
КОД:
```java
// ShahedEntity.java:692 (и 1399 в spawnShootdownEffects)
PacketDistributor.sendToAllPlayers(new ShahedExplosionPacket(pos));
```
```java
// ClientPacketHandler.java:124 — расстояние считается, измерение — нет
Vec3 listenerPos = (mc.cameraEntity != null) ? mc.cameraEntity.position() : mc.player.position();
double distance = listenerPos.distanceTo(explosionPos);
float explosionVolume = calculateExplosionVolume(distance);
```
```java
// ClientPacketHandler.java:115 и 139 — по потоку на каждый взрыв
new Thread(() -> {
    try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
    Minecraft.getInstance().execute(() -> activeSounds.remove(entityId));
}).start();
...
new Thread(() -> {
    try {
        Thread.sleep((long) delay * 50L);
        mc.execute(() -> { ... mc.level.playLocalSound(...); });
    } catch (InterruptedException ignored) {}
}).start();
```
ПОЧЕМУ ЭТО БАГ:
1. Нет проверки `mc.level.dimension()`. Игрок в Незере на координатах (100, 64, 100) получит пакет взрыва
   из Оверворлда с теми же координатами и услышит его с громкостью `40.0f`
   (`ClientPacketHandler.java:163`, `calculateExplosionVolume` для `distance <= 350`). Незер сжат 1:8,
   так что «совпадения» координат — норма, а не экзотика.
2. `new Thread(...)` без пула, без имени, без ограничения. Каждый взрыв Shahed создаёт 1-2 потока,
   живущих 0-3 секунды. Массированный налёт из 30 дронов = до 60 одновременных потоков ОС только под
   `Thread.sleep`. Для задержки уже есть `mc.execute` + счётчик тиков либо `ClientTickEvent`.
3. `handleShahedSpawn` (строки 46-72) создаёт `ShahedEngineSoundClient` даже когда
   `mc.level.getEntity(entityId)` вернул `null` — то есть зацикленный звук двигателя стартует у игроков
   в других измерениях и за пределами трекинга.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ: игрок A в Оверворлде подрывает Shahed на (0,80,0); игрок B стоит в Незере
на (0,80,0).
ОЖИДАЕМОЕ vs ФАКТИЧЕСКОЕ: B ничего не должен слышать. Фактически B слышит взрыв на полной громкости
и до этого — гул двигателя дрона.
ТЕСТИРУЕМОСТЬ: manual (звук) / pure-java (для факта отсутствия проверки измерения)
НАБРОСОК ТЕСТА:
```java
@Test void explosionIgnoredInOtherDimension() {
    setClientLevel(NETHER);
    int soundsBefore = playedSounds();
    ClientPacketHandler.handleExplosion(new Vec3(0, 80, 0));   // игрок стоит там же
    assertEquals(soundsBefore, playedSounds());                // ПАДАЕТ: +1
}
```

---

## Сводка по приоритетам

| # | Серьёзность | Суть | Основной ущерб |
|---|---|---|---|
| F-1 | critical | `remove()` не вызывается при UNLOADED_TO_CHUNK / CHANGED_DIMENSION / shutdown | перманентная утечка force-loaded чанков **на диск** |
| F-2 | critical | `isLegitSpawn` не в NBT (Shahed, Iskander) | все ракеты гибнут при рестарте + фантомные взрывы всему серверу |
| F-3 | critical | деление на `diveStartProgress == 0` → NaN | бессмертная ракета + вечный force-load на (0,0) |
| F-4 | high | ~1000 индивидуальных пакетов частиц × N игроков на взрыв | спайк тика, сетевой шторм |
| F-5 | high | клиент симулирует физику Shahed без серверных полей | дрожание, расхождение до 55 блоков |
| F-6 | high | `activeOrlanRegistry` не чистится | утечка `ServerLevel` |
| F-7 | high | хитбокс Iskander смещён на 4 блока, `sized()` игнорируется | попадания не работают, подрыв под землёй |
| F-8 | high | `releaseLauncher()` только в `explode()` | ПУ блокируется навсегда |
| F-9 | high | нет серверной валидации в `LaunchFromUnifiedTerminalPacket` | обход чёрного списка, триггер F-3 |
| F-10 | high | `SYNC_INTERVAL = 1` + `sendToAllPlayers` | 40 лишних пакетов/сек каждому игроку на ракету |
| F-11 | high | у Shahed нет лимита жизни | вечно кружащий дрон держит чанки |
| F-12 | medium | общие чанки снимаются с форса чужой ракетой | полёт сквозь невыгруженную местность |
| F-13 | medium | клиентский КВО и `ballisticStartTick` | расхождение отрисовки до 40 блоков |
| F-14 | medium | самоссылочная формула высоты + тождественный тернарник | заданная высота Shahed игнорируется |
| F-15 | medium | `PATROL_ALTITUDE` вместо `targetAltitude` в наборе высоты | перелёт/недолёт на 90-160 блоков |
| F-16 | medium | `diveMode` high/low не влияет ни на что | мёртвая функция во всём стеке |
| F-17 | medium | 770 `getBlockState`/тик на пикирующую ракету | лаг при роевой атаке |
| F-18 | medium | конфликт `syncNearbyEntities` с ванильным трекером | сущности исчезают у оператора |
| F-19 | medium | нет проверки измерения + `new Thread` на взрыв | звук взрыва в чужом измерении, десятки потоков |
