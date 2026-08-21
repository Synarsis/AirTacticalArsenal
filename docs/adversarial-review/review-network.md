# АДВЕРСАРИАЛЬНЫЙ РЕВЬЮ СЕТЕВОЙ ПОДСИСТЕМЫ — Air Tactical Arsenal (modid `ata`)

MC 1.21.1 / NeoForge 21.1.230. Прочитаны все 50 файлов в `network/` + `client/ClientPacketHandler.java` + опорные классы
(`config/ShahedConfig`, `entity/OrlanEntity`, `entity/ShahedEntity`, `entity/ShahedLauncherEntity`, `entity/OrlanLauncherEntity`,
`worldmap/ServerTerrainScanner`, `radar/ServerRouteMapScanner`, `util/LauncherZoneValidator`, GUI-скрины).

ГЛАВНЫЙ ВЫВОД ПОДТВЕРЖДЁН: **сервер систематически доверяет клиенту.** Вся защита запретных зон, дистанций и владения
живёт в GUI-скринах на клиенте, а серверные хендлеры её либо не дублируют, либо дублируют частично. Пакет формируется
клиентом руками (mod-сообщения — обычный custom payload), поэтому GUI-проверки не значат ничего. Плюс несколько тривиальных
OOM/DoS через неограниченные размеры коллекций при декодировании и через неограниченную force-загрузку чанков.

Ниже — 21 подтверждённая находка.

---

### F-1: OrlanCameraViewPacket(connect) не проверяет владение дроном — угон камеры + бесплатный креатив-полёт
СЕРЬЁЗНОСТЬ: critical
ФАЙЛ: network/OrlanCameraViewPacket.java:57-79
КОД:
```java
Entity entity = level.getEntity(payload.orlanEntityId());
if (entity instanceof OrlanEntity orlan) {
    if (payload.connect()) {
        orlan.setLinkedPlayer(player.getUUID().toString());
        player.setNoGravity(true);
        player.getAbilities().mayfly = true;
        player.onUpdateAbilities();
        ...
        sendDroneChunks(orlan, player, level, connectRadius);
```
ПОЧЕМУ ЭТО БАГ: нет ни `orlan.isOwnedBy(player)`, ни проверки, что дрон вообще принадлежит игроку. Единственный вход —
`entity instanceof OrlanEntity`. Пакет только `int entityId + boolean connect`. Хендлер безусловно выдаёт игроку
`mayfly=true` и `setNoGravity(true)` и переключает камеру.
СЦЕНАРИЙ АТАКИ: злоумышленник перебирает entityId (0..N) и шлёт `OrlanCameraViewPacket(id, true)`. Как только id попал в
любой живой Orlan (хоть чужой, хоть свой давно сбитый), сервер: (1) выставляет игроку постоянный `mayfly` — читерский
полёт в выживании, работающий даже после выхода из камеры (при disconnect способности восстанавливаются только если
`isLinkedToPlayer`, см. ниже); (2) перехватывает `linkedPlayer` чужого дрона у легитимного владельца; (3) форс-грузит
21×21 чанков вокруг дрона в любой точке карты. Повторяя connect по разным id — массовая force-загрузка чанков = DoS.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
OrlanEntity victimDrone = spawnOrlanOwnedBy(playerA);
ServerPlayer attacker = playerB;
OrlanCameraViewPacket.handle(new OrlanCameraViewPacket(victimDrone.getId(), true), ctx(attacker));
server.tick();
assertTrue(attacker.getAbilities().mayfly);                 // получил полёт чужим дроном
assertEquals(attacker.getUUID().toString(), victimDrone.getLinkedPlayerUUID()); // угнал камеру у A
```

### F-2: UpdateOrlanRoutePacket не проверяет владение — угон чужого дрона в полёте
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: network/UpdateOrlanRoutePacket.java:46-75
КОД:
```java
Entity entity = level.getEntity(payload.orlanEntityId());
if (!(entity instanceof OrlanEntity orlan)) { ... return; }
OrlanEntity.FlightPhase phase = orlan.getFlightPhase();
if (phase != CRUISING && phase != PATROLLING) { ... return; }
...
orlan.updateRouteInFlight(newTarget, intermediateWaypoints);
```
ПОЧЕМУ ЭТО БАГ: в отличие от `OrlanReturnPacket`/`OrlanAltitudePacket` (там есть `isOwnedBy`), здесь проверки владельца НЕТ.
СЦЕНАРИЙ АТАКИ: атакующий шлёт `UpdateOrlanRoutePacket(чужойId, [своиТочки])` → чужой разведдрон в полёте перенаправляется
на маршрут атакующего (увести дрон противника, слить его в стену/зенитку, сорвать разведку).
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
OrlanEntity drone = spawnCruisingOrlanOwnedBy(playerA);
UpdateOrlanRoutePacket.handle(new UpdateOrlanRoutePacket(drone.getId(), List.of(new BlockPos(9999,64,9999))), ctx(playerB));
server.tick();
assertEquals(new BlockPos(9999,64,9999), drone.getCurrentTarget()); // B перенаправил дрон A
```

### F-3: LaunchFromUnifiedTerminalPacket — на сервере нет проверки запретной зоны, дистанции и близости к терминалу
СЕРЬЁЗНОСТЬ: critical
ФАЙЛ: network/LaunchFromUnifiedTerminalPacket.java:65-137
КОД:
```java
int targetX = payload.targetX();
int targetZ = payload.targetZ();
...
} else {                                   // missileType==0 (Shahed), launcherPositions пуст
    launchPos = Vec3.atCenterOf(terminalPos.above(2));
}
int targetY = ShahedConfig.TARGET_Y_LEVEL.get();
BlockPos targetPos = new BlockPos(targetX, targetY, targetZ);
...
ShahedEntity shahed = new ShahedEntity(level, launchPos, targetPos, "low");
shahed.setOwner(player);
shahed.isLegitSpawn = true;
level.addFreshEntity(shahed);
```
ПОЧЕМУ ЭТО БАГ: во всём хендлере НЕТ вызова `ShahedConfig.isInForbiddenZone(...)`, нет проверки MIN/MAX дистанции, нет
проверки что `terminalPos` — реальный терминал и что игрок рядом. Запретная зона проверяется только в GUI
(`UnifiedTerminalScreen.onLaunchRocket`/`isInForbiddenZone`), которое атакующий обходит, формируя пакет вручную. При
`missileType==0` даже пусковая не требуется — снаряд спавнится над произвольным `terminalPos`.
СЦЕНАРИЙ АТАКИ: атакующий шлёт `LaunchFromUnifiedTerminalPacket(targetX=<координаты чужой базы в whitelist/защищённой зоне>,
targetZ, terminalPos=<любая>, [], 0)` → Shahed летит и взрывается на защищённой базе. Ядро анти-грифер защиты (forbidden
zone) полностью обойдено. С `missileType==1` так же запускается Iskander по любой незапретной цели.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
ShahedConfig.FORBIDDEN_ZONES.set(List.of("1000,1000,1100,1100")); // защищённая зона
LaunchFromUnifiedTerminalPacket.handle(
    new LaunchFromUnifiedTerminalPacket(1050, 1050, new BlockPos(0,64,0), List.of(), 0), ctx(attacker));
server.tick();
assertEquals(1, countEntities(ShahedEntity.class)); // взлетел, хотя цель в forbidden zone
```

### F-4: LaunchShahedFromTerminalPacket — нет проверки MAX_RANGE + неограниченная force-загрузка чанков = DoS/зависание сервера
СЕРЬЁЗНОСТЬ: critical
ФАЙЛ: network/LaunchShahedFromTerminalPacket.java:44-108
КОД:
```java
int targetX = payload.targetX();
int targetZ = payload.targetZ();
if (ShahedConfig.isInForbiddenZone(targetX, targetZ)) { ... return; }
...
preloadChunks(serverLevel, spawnPos, Vec3.atCenterOf(targetPos));
...
private static void preloadChunks(ServerLevel level, Vec3 from, Vec3 to) {
    ...
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
ПОЧЕМУ ЭТО БАГ: `targetX/targetZ` — сырые `int` из пакета, НЕ проверяются на `SHAHED_MAX_RANGE` (SHAHED_MAX_RANGE вообще
не используется нигде на сервере — grep подтверждает единственные ссылки в config). При цели далеко от spawn (`SPAWN_X=0`)
`numPoints = distance/32` растёт линейно с расстоянием; на дистанции ~3·10^7 это ~10^6 итераций × 25 чанков =
десятки миллионов `setChunkForced(...,true)`. Чанки форсятся синхронно в тике сервера и не разгружаются, если сущность
до них не долетит.
СЦЕНАРИЙ АТАКИ: атакующий шлёт `LaunchShahedFromTerminalPacket(targetX=29000000, targetZ=29000000, terminalPos)` (цель
далеко и не в forbidden zone → проверка пройдена). Сервер входит в многомиллионный цикл force-load → мгновенный OOM либо
многосекундное зависание главного потока (watchdog crash). Повторить = гарантированный отказ сервера.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
long t0=System.nanoTime();
LaunchShahedFromTerminalPacket.handle(
    new LaunchShahedFromTerminalPacket(29_000_000, 29_000_000, new BlockPos(0,70,0)), ctx(attacker));
server.tick();
// падает по OOM либо тик длится десятки секунд (форс десятков млн чанков)
```

### F-5: OOM при декодировании — `new ArrayList<>(n)` / `new int[len]` с неограниченным n из пакета
СЕРЬЁЗНОСТЬ: critical
ФАЙЛ: network/SaveShahedRoutePacket.java:32-38; SaveOrlanRoutePacket.java:32-38; LaunchShahedByRoutePacket.java:37-43; UpdateOrlanRoutePacket.java:32-38; SaveTabletCoordsPacket.java:33-44
КОД (SaveShahedRoutePacket):
```java
(buf) -> {
    BlockPos pos = buf.readBlockPos();
    int n = buf.readInt();
    List<BlockPos> wps = new ArrayList<>(n);      // n не ограничен
    for (int i = 0; i < n; i++) wps.add(buf.readBlockPos());
    return new SaveShahedRoutePacket(pos, wps);
}
```
КОД (SaveTabletCoordsPacket):
```java
int len = buf.readInt();
int[] arr = new int[len];                          // len не ограничен
```
ПОЧЕМУ ЭТО БАГ: `n`/`len` — сырой `readInt()` без валидатора. `new ArrayList<>(Integer.MAX_VALUE)` пытается аллоцировать
`Object[2^31]` ДО чтения тела → мгновенный `OutOfMemoryError` в сетевом декодере. `new int[Integer.MAX_VALUE]` то же самое.
Ни один из этих playToServer-кодеков не использует `ByteBufCodecs.list(max)` / bounded-обёртку.
СЦЕНАРИЙ АТАКИ: атакующий шлёт `save_shahed_route` (или `save_tablet_coords`) с полем размера `0x7FFFFFFF`. Сервер при
декодировании мгновенно валится по OOM (или входит в GC-шторм). 1 маленький пакет = падение сервера.
ТЕСТИРУЕМОСТЬ: pure-java
НАБРОСОК ТЕСТА:
```java
RegistryFriendlyByteBuf buf = newBuf();
buf.writeBlockPos(BlockPos.ZERO);
buf.writeInt(Integer.MAX_VALUE);   // объявленный размер списка
assertThrows(OutOfMemoryError.class, () -> SaveShahedRoutePacket.STREAM_CODEC.decode(buf));
```

### F-6: RemoveRocketPacket — кража чужой ракеты Iskander (нет проверки владения/близости), выдача предмета атакующему
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: network/RemoveRocketPacket.java:36-77
КОД:
```java
BlockPos launcherPos = payload.launcherPos();
BlockState launcherState = level.getBlockState(launcherPos);
if (!(launcherState.getBlock() instanceof LauncherBlock launcher)) { ... return; }
if (!launcherState.getValue(LauncherBlock.LOADED)) { ... return; }
...
level.removeBlock(rocketPos, false);
launcher.setLoaded(level, launcherPos, false);
ItemStack rocketItem = new ItemStack(ModItems.ISKANDER.get());
if (!player.getInventory().add(rocketItem)) player.drop(rocketItem, false);
```
ПОЧЕМУ ЭТО БАГ: нет проверки, что игрок рядом с `launcherPos` и что это его пусковая. `launcherPos` полностью
контролируется атакующим. Ракета снимается с ЛЮБОЙ заряженной чужой ПУ в мире и `ISKANDER` кладётся в инвентарь атакующего.
СЦЕНАРИЙ АТАКИ: атакующий узнаёт/перебирает координаты чужих заряженных ПУ и шлёт `RemoveRocketPacket(pos)` по каждой →
получает бесплатный Iskander за штуку и разоружает оборону противника. Прямая кража предметов + грифинг.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
loadLauncherAt(pos, ownedBy=playerA);          // заряжен Iskander
int before = countItem(playerB, ModItems.ISKANDER.get());
RemoveRocketPacket.handle(new RemoveRocketPacket(pos), ctx(playerB));
server.tick();
assertEquals(before+1, countItem(playerB, ModItems.ISKANDER.get())); // украл ракету A
assertFalse(level.getBlockState(pos).getValue(LauncherBlock.LOADED));
```

### F-7: LaunchRocketPacket — запуск чужого Iskander (нет проверки близости/владения ПУ)
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: network/LaunchRocketPacket.java:39-100
КОД:
```java
BlockPos launcherPos = payload.launcherPos();
int targetX = payload.targetX();
int targetZ = payload.targetZ();
BlockState launcherState = level.getBlockState(launcherPos);
if (!(launcherState.getBlock() instanceof LauncherBlock launcher)) { ... }
if (!launcherState.getValue(LauncherBlock.LOADED)) { ... }
double dist = Math.sqrt(...); // проверка min/max дистанции ЦЕЛИ, но не игрока
...
rocketEntity.startLaunch(targetPos, launcherPos, player);
```
ПОЧЕМУ ЭТО БАГ: проверяются тип блока, LOADED, дистанция цели и forbidden zone — но НЕ близость игрока к ПУ и НЕ владение.
Любой игрок может выстрелить любой заряженной ПУ в мире по любой незапретной цели.
СЦЕНАРИЙ АТАКИ: атакующий шлёт `LaunchRocketPacket(чужаяПУ, terminalPos, tx, tz)` → чужой Iskander улетает по цели
атакующего (растрата боезапаса противника, ложные удары). Хотя forbidden zone здесь проверяется, отсутствие
proximity/ownership позволяет дистанционно расстреливать чужие ПУ.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
loadLauncherAt(pos, ownedBy=playerA);
LaunchRocketPacket.handle(new LaunchRocketPacket(pos, BlockPos.ZERO, tx, tz), ctx(playerBfarAway));
server.tick();
assertTrue(((IskanderRocketBlockEntity)level.getBlockEntity(pos.above())).isLaunching());
```

### F-8: SaveShahedRoutePacket / SaveOrlanRoutePacket — перезапись чужого маршрута (нет проверки владения)
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: network/SaveShahedRoutePacket.java:46-78; network/SaveOrlanRoutePacket.java:46-78
КОД:
```java
ShahedLauncherEntity launcher = findLauncher(level, payload.launcherPos());
if (launcher == null) { ... return; }
double playerDistance = player.position().distanceTo(launcher.position());
if (playerDistance > 30.0) { ... return; }
launcher.setSavedRoute(payload.waypoints());     // без проверки владельца
```
ПОЧЕМУ ЭТО БАГ: единственная защита — «в пределах 30 блоков». Ни `ShahedLauncherEntity`, ни `OrlanLauncherEntity` не имеют
проверки владельца (grep по классам не находит owner-guard в этих хендлерах). Любой, кто подошёл на 30 блоков к чужой ПУ,
переписывает её сохранённый маршрут произвольными точками.
СЦЕНАРИЙ АТАКИ: атакующий подходит к чужой ПУ, шлёт `SaveShahedRoutePacket(pos, [точки на своей же базе противника])`.
В связке с F-9 (LaunchShahedByRoute тоже без forbidden-zone) — заряженный дрон противника уводится/запускается по
маршруту атакующего.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
ShahedLauncherEntity pu = spawnLauncherOwnedBy(playerA, pos);
movePlayerNear(playerB, pos, 5);
SaveShahedRoutePacket.handle(new SaveShahedRoutePacket(pos, List.of(new BlockPos(1,64,1))), ctx(playerB));
server.tick();
assertEquals(List.of(new BlockPos(1,64,1)), pu.getSavedRoute()); // B переписал маршрут A
```

### F-9: LaunchShahedByRoutePacket — нет проверки forbidden zone, нет проверки владения ПУ
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: network/LaunchShahedByRoutePacket.java:51-99
КОД:
```java
for (BlockPos pos : payload.launcherPositions()) {
    ShahedLauncherEntity launcher = findLauncher(level, pos);
    if (launcher == null) { failed++; continue; }
    if (!launcher.hasDrone()) { failed++; continue; }
    if (!launcher.hasSavedRoute()) { failed++; continue; }
    ...
    double playerDistance = player.position().distanceTo(launcher.position());
    if (playerDistance > 30.0) { failed++; continue; }
    BlockPos firstTarget = route.get(0);
    if (!LauncherZoneValidator.isTargetInValidZone(...)) { failed++; continue; }
    launcher.startLaunchSequenceWithRoute(player, finalTarget, "low", intermediateWaypoints, altitude);
```
ПОЧЕМУ ЭТО БАГ: проверяется угол зоны запуска (LauncherZoneValidator) и дистанция игрока 30 блоков, но НЕТ
`ShahedConfig.isInForbiddenZone(finalTarget)` и НЕТ владения ПУ. Финальная цель берётся из сохранённого маршрута, который
атакующий может сам же и перезаписать (F-8). Итог — обход forbidden zone через маршрутный запуск + запуск чужих ПУ.
СЦЕНАРИЙ АТАКИ: (1) `SaveShahedRoutePacket(чужаяПУ, [точка в защищённой зоне])`; (2) `LaunchShahedByRoutePacket([чужаяПУ], 0)`
→ дрон летит в forbidden zone с чужой ПУ. Защита зон обойдена полностью.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
ShahedConfig.FORBIDDEN_ZONES.set(List.of("1000,1000,1100,1100"));
ShahedLauncherEntity pu = spawnLauncherWithDrone(pos, yawTowards(1050,1050));
movePlayerNear(playerB, pos, 5);
SaveShahedRoutePacket.handle(new SaveShahedRoutePacket(pos, List.of(new BlockPos(1050,64,1050))), ctx(playerB));
LaunchShahedByRoutePacket.handle(new LaunchShahedByRoutePacket(pos, 0), ctx(playerB));
server.tick();
assertEquals(1, countEntities(ShahedEntity.class)); // запущен в forbidden zone
```

### F-10: LaunchOrlanFromTabletPacket — нет проверки forbidden zone и владения ПУ
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: network/LaunchOrlanFromTabletPacket.java:40-114
КОД:
```java
OrlanLauncherEntity launcher = findLauncher(level, payload.launcherPos());
if (launcher == null) { ... return; }
if (!launcher.hasDrone()) { ... return; }
double playerDistance = player.position().distanceTo(launcher.position());
if (playerDistance > 30.0) { ... return; }
...
if (firstDistance < 600) { ... return; }
if (!LauncherZoneValidator.isTargetInValidZone(...)) { ... return; }
...
OrlanEntity orlan = new OrlanEntity(level, launchPos, targetPos, launcherYaw, launcher.blockPosition());
orlan.setOwnerUUID(player.getUUID().toString());
```
ПОЧЕМУ ЭТО БАГ: как и F-9 — проверка дистанции игрока 30 блоков и угла, но НЕТ `isInForbiddenZone` и НЕТ владения ПУ.
Любой в 30 блоках запускает чужой Orlan и становится его владельцем.
СЦЕНАРИЙ АТАКИ: атакующий подходит к чужой заряженной ПУ Орлана и шлёт `LaunchOrlanFromTabletPacket(pos, tx, tz)` →
угоняет дрон противника (становится ownerUUID) и/или отправляет разведку в защищённую зону.
ТЕСТИРУЕМОСТЬ: gametest

### F-11: SetLauncherTargetPacket — установка цели ЛЮБОЙ пусковой в мире (нет дистанции, нет владения)
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: network/SetLauncherTargetPacket.java:45-62
КОД:
```java
BlockEntity be = player.level().getBlockEntity(payload.launcherPos());
if (be instanceof LauncherBlockEntity launcher) {
    BlockPos target = payload.hasTarget() ? payload.targetPos() : null;
    launcher.setTarget(target);
```
ПОЧЕМУ ЭТО БАГ: полностью отсутствует проверка дистанции игрока к `launcherPos` и владения. `launcherPos`/`targetPos` —
произвольные из пакета.
СЦЕНАРИЙ АТАКИ: атакующий шлёт `SetLauncherTargetPacket(чужаяПУ, произвольнаяЦель, true)` → переустанавливает цель на
чужой ПУ через всю карту. Грифинг/саботаж наведения.
ТЕСТИРУЕМОСТЬ: gametest

### F-12: RequestRadarMapPacket / RequestRouteMapPacket — сканер террейна по произвольным координатам, безлимитный рост INSTANCES = OOM
СЕРЬЁЗНОСТЬ: critical
ФАЙЛ: network/RequestRadarMapPacket.java:29-52; network/RequestRouteMapPacket.java:29-53; worldmap/ServerTerrainScanner.java:48,63-94; radar/ServerRouteMapScanner.java:42,78+
КОД (RequestRadarMapPacket):
```java
BlockPos radarPos = payload.radarPos();
ServerTerrainScanner scanner = ServerTerrainScanner.getOrCreate(radarPos, level);
```
КОД (ServerTerrainScanner):
```java
private static final int CACHE_SIZE = 1536;               // 1536×1536 пикселей
private static final Map<String, ServerTerrainScanner> INSTANCES = new LinkedHashMap<>();
public ServerTerrainScanner(BlockPos radarPos) {
    ...
    this.pixels = new int[CACHE_SIZE * CACHE_SIZE];       // ~9.4 МБ на инстанс
}
public static synchronized ServerTerrainScanner getOrCreate(BlockPos pos, ServerLevel level) {
    String k = key(pos);                                  // key = x+"_"+z
    ...
    INSTANCES.put(k, scanner);                            // без ограничения размера карты
```
ПОЧЕМУ ЭТО БАГ: (1) `radarPos` не валидируется — нет проверки, что рядом есть радар-блок и что игрок рядом; (2) каждый
уникальный `(x,z)` создаёт новый сканер с `int[1536*1536]` (~9.4 МБ; у Route CACHE_SIZE=2048 → `int[2048*2048]`=~16.7 МБ),
сканирующий тысячи столбцов террейна (генерация чанков), и кладётся в `INSTANCES` без верхней границы и без эвикции.
`getQueuePosition`/build выполняется в тик-петле сервера.
СЦЕНАРИЙ АТАКИ: атакующий в цикле шлёт `RequestRadarMapPacket(new BlockPos(i*10000, 64, j*10000))` с разными i,j → сервер
плодит сканеры: сотни × (9–16 МБ) = гигабайты heap + непрерывная генерация террейна по всей карте → OOM/зависание.
Нет rate-limit. Один клиент кладёт сервер.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
for (int i=0;i<500;i++)
    RequestRadarMapPacket.handle(new RequestRadarMapPacket(new BlockPos(i*10000,64,i*10000)), ctx(attacker));
// heap растёт ~500×9.4МБ ≈ 4.7ГБ → OutOfMemoryError; INSTANCES.size()==500 без эвикции
```

### F-13: RequestSurfaceHeightPacket — безлимитная генерация/загрузка чанков по произвольным координатам
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: network/RequestSurfaceHeightPacket.java:37-59
КОД:
```java
int x = payload.x();
int z = payload.z();
ChunkAccess chunk = level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false);
if (chunk != null) { ... } else {
    surfaceY = level.getChunkSource().getGenerator().getBaseHeight(
            x, z, Heightmap.Types.WORLD_SURFACE, level, level.getChunkSource().randomState());
}
```
ПОЧЕМУ ЭТО БАГ: `x,z` произвольны, без проверки близости и без rate-limit. `getBaseHeight` (генерация шума террейна)
дёргается синхронно в тике на любую точку. Спам пакетами → CPU-DoS главного потока сервера.
СЦЕНАРИЙ АТАКИ: атакующий шлёт тысячи `RequestSurfaceHeightPacket` в тик с разными координатами → сервер тратит весь тик
на noise-генерацию, TPS падает. (Аналогично F-14.)
ТЕСТИРУЕМОСТЬ: gametest

### F-14: RequestSegmentMaxHeightPacket — 32 сэмпла генерации террейна на пакет, без ограничений/rate-limit
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: network/RequestSegmentMaxHeightPacket.java:47-96
КОД:
```java
int[] profile = sampleHeightProfile(level, payload.x1(), payload.z1(), payload.x2(), payload.z2());
...
for (int i = 0; i < PROFILE_SAMPLES; i++) {          // PROFILE_SAMPLES = 32
    ...
    profile[i] = getTerrainHeight(level, sx, sz);    // getBaseHeight × 2 при незагруженном чанке
}
```
ПОЧЕМУ ЭТО БАГ: координаты `x1,z1,x2,z2` произвольны; каждый пакет = 32 точки × до 2 `getBaseHeight` = 64 генераций шума,
синхронно в тике, без rate-limit и без проверки близости.
СЦЕНАРИЙ АТАКИ: спам пакетами с далёкими незагруженными сегментами → 64 noise-вычисления на пакет × тысячи пакетов =
залипание главного потока, падение TPS/watchdog. Один клиент — DoS.
ТЕСТИРУЕМОСТЬ: gametest

### F-15: RequestRadarUpdatePacket — сканер сущностей 3000×3000 по произвольным координатам (радар-хак + DoS)
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: network/RequestRadarUpdatePacket.java:41-83
КОД:
```java
BlockPos radarPos = payload.radarPos();
Vec3 radarCenter = Vec3.atCenterOf(radarPos);
AABB searchBox = new AABB(
        radarCenter.x - RADAR_RANGE, radarCenter.y - 64, radarCenter.z - RADAR_RANGE,
        radarCenter.x + RADAR_RANGE, radarCenter.y + 350, radarCenter.z + RADAR_RANGE); // RADAR_RANGE=1500
...
for (Entity entity : level.getEntities(null, searchBox)) { ... targets.add(...); }
```
ПОЧЕМУ ЭТО БАГ: нет проверки, что по `radarPos` стоит радар-блок и что игрок рядом. `level.getEntities` по AABB 3000×3000×414
в произвольной точке — и дорого (итерация по сущностям), и раскрывает позиции/скорости чужих снарядов/дронов где угодно
(радар-хак). Rate-limit отсутствует.
СЦЕНАРИЙ АТАКИ: атакующий без радара шлёт `RequestRadarUpdatePacket(любаяТочка)` и получает полный список целей (id, тип,
координаты, курс, скорость) в радиусе 1500 вокруг любой точки — разведка чужих запусков. Спам = нагрузка на getEntities.
ТЕСТИРУЕМОСТЬ: gametest

### F-16: RequestWorldMapPacket — линейный проход по всем береговым точкам без проверки близости/rate-limit
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: network/RequestWorldMapPacket.java:35-56; worldmap/WorldMapData.java:95-110
КОД:
```java
Set<Long> coastlinePoints = mapData.getCoastlinePointsInRadius(
        payload.centerX(), payload.centerZ(), payload.radius());
...
// getCoastlinePointsInRadius:
for (Long packed : coastlinePoints) { ... if (dx*dx + dz*dz <= radiusSq) result.add(packed); }
```
ПОЧЕМУ ЭТО БАГ: `radius` из пакета не ограничен; проход линейный по всему множеству береговых точек на каждый запрос, без
rate-limit и без проверки близости игрока. При `radius=Integer.MAX_VALUE` возвращается весь набор → крупный
server→client payload и полное раскрытие карты побережья.
СЦЕНАРИЙ АТАКИ: спам `RequestWorldMapPacket(cx, cz, Integer.MAX_VALUE)` → нагрузка на сервер + утечка всей карты клиенту.
ТЕСТИРУЕМОСТЬ: gametest

### F-17: InstallRocketPacket — установка ракеты в чужую ПУ через всю карту (нет дистанции/владения)
СЕРЬЁЗНОСТЬ: low
ФАЙЛ: network/InstallRocketPacket.java:37-90
КОД:
```java
BlockPos launcherPos = payload.launcherPos();
BlockState launcherState = level.getBlockState(launcherPos);
if (!(launcherState.getBlock() instanceof LauncherBlock launcher)) { ... }
...
player.getInventory().items.get(rocketSlot).shrink(1);
...
if (IskanderRocketBlock.placeRocket(level, launcherPos, facing)) { launcher.setLoaded(...); }
```
ПОЧЕМУ ЭТО БАГ: нет проверки близости/владения — из своего инвентаря можно зарядить любую чужую ПУ в мире. Менее опасно
(атакующий тратит свой предмет), но нарушает границы взаимодействия и позволяет странные манипуляции (например,
подготовить чужую ПУ к запуску по F-7).
ТЕСТИРУЕМОСТЬ: gametest

### F-18: RemoveLauncherFromTabletPacket / SaveTabletCoordsPacket — пишут в первый попавшийся планшет в инвентаре
СЕРЬЁЗНОСТЬ: low
ФАЙЛ: network/RemoveLauncherFromTabletPacket.java:32-77; network/SaveTabletCoordsPacket.java:52-80
КОД (RemoveLauncherFromTablet):
```java
if (tablet == null) {
    for (ItemStack stack : player.getInventory().items) {
        if (stack.getItem() instanceof OrlanTabletItem) { tablet = stack; isOrlan = true; break; }
        else if (stack.getItem() instanceof ControlTabletItem) { tablet = stack; break; }
    }
}
```
ПОЧЕМУ ЭТО БАГ: сервер не проверяет, что именно этот планшет открыт/используется; берёт первый подходящий из инвентаря.
`launcherPos`/`savedCoords` из пакета не валидируются. Не эксплойт против других игроков (пишет только в свой инвентарь),
но клиент может произвольно менять NBT планшета данными из пакета без проверки корректности (мусорные привязки/координаты).
ТЕСТИРУЕМОСТЬ: gametest

### F-19: OrlanEntity.isOwnedBy возвращает true для «бесхозного» дрона — обход owner-guard
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: entity/OrlanEntity.java:1080-1083
КОД:
```java
public boolean isOwnedBy(Player player) {
    String owner = getOwnerUUIDString();
    return owner.isEmpty() || owner.equals(player.getUUID().toString());
}
```
ПОЧЕМУ ЭТО БАГ: при пустом owner метод возвращает `true` для ЛЮБОГО игрока. Все owner-проверки, полагающиеся на
`isOwnedBy` (`OrlanAltitudePacket`, `OrlanReturnPacket`, `OrlanCameraViewPacket` disconnect-ветка), становятся ничтожны
для любого Orlan, у которого `OWNER_UUID_DATA` пуст (например, заспавненного не через легит-путь, или после
некорректной сериализации). В связке с F-1/F-10 расширяет площадь угона.
СЦЕНАРИЙ АТАКИ: получить/создать бесхозный Orlan (owner=="") → любой игрок управляет им как своим (высота, возврат, отвязка).
ТЕСТИРУЕМОСТЬ: pure-java
НАБРОСОК ТЕСТА:
```java
OrlanEntity o = new OrlanEntity(level, ...); // owner == ""
assertTrue(o.isOwnedBy(anyRandomPlayer));    // баг: чужой считается владельцем
```

### F-20: Комментарий в NetworkHandler о «TODO-stubs» ложный — хендлеры полностью реализованы и БОЕВЫЕ (ложная маркировка безопасности)
СЕРЬЁЗНОСТЬ: low
ФАЙЛ: network/NetworkHandler.java:72-89
КОД:
```java
// GUI-driven client→server packets (handlers are TODO-stubs until the matching
// server-side block entities / scanners are ported, but the payloads must be
// registered or PacketDistributor.sendToServer would throw at runtime).
registrar.playToServer(SetLauncherTargetPacket.TYPE, ...);
registrar.playToServer(SaveShahedRoutePacket.TYPE, ...);
registrar.playToServer(LaunchShahedByRoutePacket.TYPE, ...);
registrar.playToServer(LaunchOrlanFromTabletPacket.TYPE, ...);
...
```
ПОЧЕМУ ЭТО БАГ: комментарий утверждает, что эти хендлеры — «заглушки-TODO». По факту (F-8..F-11) все они ПОЛНОСТЬЮ
реализованы: спавнят сущности, запускают дроны, пишут маршруты, устанавливают цели. Ложная маркировка усыпляет ревьюера
и скрывает, что именно эта группа боевых client→server пакетов не имеет проверок forbidden-zone/владения. Функциональность
не «сломана» — она опасно активна. (Замечание строго поведенческое: комментарий вводит в заблуждение относительно
security-поверхности, а не про стиль.)
ТЕСТИРУЕМОСТЬ: manual

### F-21: PROTOCOL_VERSION="1" зарегистрирован НЕ как `.optional()` — все пакеты обязательны
СЕРЬЁЗНОСТЬ: low
ФАЙЛ: network/NetworkHandler.java:10,28
КОД:
```java
private static final String PROTOCOL_VERSION = "1";
...
final PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);   // без .optional()
```
ПОЧЕМУ ЭТО БАГ (поведение): регистратор не помечен `.optional()`, значит все 46 payload-ов обязательны с обеих сторон.
Клиент без мода или со старой версией протокола будет отклонён при подключении к серверу с модом (и наоборот). Это
корректно для required-мода, но противоречит формулировке задания про `.optional()`: в текущем коде `.optional()` НЕТ,
то есть «мягкой» деградации для vanilla-клиентов не существует — это осознанное, но жёсткое поведение, которое стоит
зафиксировать (ошибка подключения, а не тихая работа без функций).
ТЕСТИРУЕМОСТЬ: manual

---

## СВОДКА ПО ПРИОРИТЕТАМ

CRITICAL (падение сервера / полный обход анти-грифер защиты):
- F-1  OrlanCameraView без owner-check → угон камеры + читерский mayfly/nogravity + force-load DoS
- F-3  LaunchFromUnifiedTerminal без forbidden-zone/дистанции → удар по защищённым базам
- F-4  LaunchShahedFromTerminal без MAX_RANGE → многомиллионный force-load чанков = OOM/зависание
- F-5  OOM при декодировании (`new ArrayList<>(n)` / `new int[len]`) в 5 playToServer-пакетах
- F-12 RequestRadarMap/RouteMap без валидации → безлимитный рост сканеров (гигабайты) + генерация террейна

HIGH:
- F-2  UpdateOrlanRoute без owner-check → угон дрона в полёте
- F-6  RemoveRocket без owner/дистанции → кража чужого Iskander в свой инвентарь
- F-7  LaunchRocket без owner/дистанции → запуск чужой ПУ
- F-8  SaveShahed/OrlanRoute без owner → перезапись чужого маршрута
- F-9  LaunchShahedByRoute без forbidden-zone/owner → обход зон + запуск чужих ПУ
- F-13 RequestSurfaceHeight — CPU-DoS генерацией террейна
- F-14 RequestSegmentMaxHeight — CPU-DoS (64 noise/пакет)

MEDIUM:
- F-10 LaunchOrlanFromTablet без forbidden-zone/owner
- F-11 SetLauncherTarget без дистанции/owner
- F-15 RequestRadarUpdate — радар-хак + нагрузка
- F-16 RequestWorldMap — утечка карты + нагрузка
- F-19 isOwnedBy==true при пустом owner

LOW:
- F-17 InstallRocket без дистанции/owner
- F-18 Remove/SaveTablet пишут в первый планшет инвентаря без валидации
- F-20 Ложный «TODO-stub» комментарий над боевыми хендлерами
- F-21 Регистратор не `.optional()`

## СИСТЕМНЫЕ НАБЛЮДЕНИЯ
- Rate limiting отсутствует полностью: grep по network/ не находит cooldown/throttle/lastRequest. Каждый серверный
  хендлер можно вызывать неограниченно за тик (усиливает F-4, F-12..F-16).
- Ни один client→server кодек не использует `ByteBufCodecs.list(max)` / bounded-обёртки; все списки/массивы читаются по
  сырому `readInt()` (см. F-5). Это системный дефект кодеков, а не единичный.
- Регистрация полная: все 46 payload-ов зарегистрированы (проверено сверкой TYPE-классов с register()), «неотправляемых»/
  незарегистрированных пакетов нет — краша при отправке из-за отсутствия регистрации не будет.
- Тред-безопасность серверных хендлеров в норме: все используют `context.enqueueWork(...)`. Замечание про Minecraft.getInstance()
  в общих классах (ShahedSpawnPacket, Open*Packet и т.п.) не реализуется в NoClassDefFoundError, т.к. эти handle()
  зарегистрированы playToClient и на выделенном сервере не вызываются (клиентские лямбды не загружаются).
- Комментарий в NetworkHandler:36-38 про OrlanCameraViewPacket ВЕРЕН по направлению (пакет действительно client→server,
  отправляется из OrlanTabletScreen:474 и OrlanCameraHandler:94, зарегистрирован playToServer). Ошибка не в направлении, а
  в отсутствии owner-проверки внутри (F-1).
- Два класса `ClientPacketHandler`: `network/ClientPacketHandler` (@OnlyIn CLIENT, роутер звуков/кэшей Shahed/Orlan/launcher)
  и `client/ClientPacketHandler` (тонкие обёртки открытия tablet-скринов). Разделение безобидное: разные обязанности,
  оба чисто клиентские; `client/`-версия по факту НЕ используется (её openShahed/OrlanTabletScreen никем не вызываются —
  Open*Packet.handle создают скрины напрямую), т.е. это мёртвый код-дубль, но не дефект безопасности.
