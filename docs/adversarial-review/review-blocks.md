# АДВЕРСАРИАЛЬНОЕ РЕВЬЮ — блоки / мультиблоки / block entities / предметы / персистентность

Проект: `D:\Mods_Projects\SOURCE_ANY\ATA_all\ATA_update` (MC 1.21.1 / NeoForge 21.1.230, modid `ata`)
Домен: `block/`, `block/entity/`, `item/`, `util/LauncherZoneValidator.java`, `data/ata/`
Смежные файлы, задетые доказательствами: `network/RemoveRocketPacket.java`, `network/InstallRocketPacket.java`, `network/LaunchFromUnifiedTerminalPacket.java`, `network/LaunchRocketPacket.java`, `entity/IskanderEntity.java`, `chunk/MissileChunkManager.java`.

Итог: **21 подтверждённая находка** — 5 critical (2 бесконечных дюпа, 2 потери данных при перезагрузке, 1 бесконтрольный спавн ракет), 8 high, 6 medium, 2 low.

---

### F-1: Бесконечный дюп «Искандера» — терминал снимает ракету, но не очищает LoadingHatchBlockEntity
СЕРЬЁЗНОСТЬ: critical
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/network/RemoveRocketPacket.java:159`
КОД:
```java
            level.removeBlock(rocketPos, false);

            launcher.setLoaded(level, launcherPos, false);

            ItemStack rocketItem = new ItemStack(ModItems.ISKANDER.get());
            if (!player.getInventory().add(rocketItem)) {
                player.drop(rocketItem, false);
            }
```
ПОЧЕМУ ЭТО БАГ: ракета физически хранится в **двух** независимых местах — блок `ata:iskander_rocket` над центром ПУ и `ItemStack rocketStack` внутри `LoadingHatchBlockEntity` (`block/entity/LoadingHatchBlockEntity.java:20,38-52`). Загрузка через люк (`block/LoadingHatchBlock.java:93-95`) заполняет **оба**. `RemoveRocketPacket` снимает только блок и `LOADED`, выдаёт игроку **новый** `ItemStack`, но `hatch.clearRocket()` не вызывает и `launcherEntity.setRocketLoaded(false)` тоже. Люк остаётся `isLoaded=true` с непустым `rocketStack`, из которого пустой рукой вынимается вторая копия (`block/LoadingHatchBlock.java:117-132`).
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ:
1. Построить ПУ, подключить кабелем к `unified_terminal`.
2. ПКМ по люку с 1× `ata:iskander` → ракета загружена, в инвентаре 0.
3. ПКМ по терминалу → кнопка «снять ракету» (`RemoveRocketPacket`) → **в инвентаре 1× iskander**, блок ракеты исчез, `LOADED=false`.
4. ПКМ пустой рукой по люку → `hatch.isLoaded()` всё ещё `true` → **в инвентаре 2× iskander**.
5. Повторять шаги 2–4. Каждый цикл: +1 `ata:iskander` (Rarity.EPIC, ядерный боеприпас).
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
@GameTest
public static void removeRocketMustClearHatch(GameTestHelper h) {
    BlockPos c = new BlockPos(3, 2, 3);
    h.setBlock(c, ModBlocks.LAUNCHER.get().defaultBlockState()); // строит мультиблок
    LoadingHatchBlockEntity hatch = (LoadingHatchBlockEntity) h.getBlockEntity(/*hatchPos*/ c.north());
    hatch.loadRocket(new ItemStack(ModItems.ISKANDER.get()));
    IskanderRocketBlock.placeRocket(h.getLevel(), h.absolutePos(c), Direction.NORTH);
    ((LauncherBlock) h.getBlockState(c).getBlock()).setLoaded(h.getLevel(), h.absolutePos(c), true);

    ServerPlayer p = h.makeMockServerPlayer();
    RemoveRocketPacket.handle(new RemoveRocketPacket(h.absolutePos(c)), mockCtx(p));

    // ПАДАЕТ: hatch.isLoaded() == true, суммарно в мире 2 ракеты вместо 1
    h.assertFalse(hatch.isLoaded(), "hatch still loaded after terminal removal");
    h.assertValueEqual(countIskanders(p) + (hatch.isLoaded() ? 1 : 0), 1, "iskander count");
}
```

---

### F-2: Запуск с объединённого терминала спавнит ракеты/дроны из воздуха — боеприпас не расходуется
СЕРЬЁЗНОСТЬ: critical
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/network/LaunchFromUnifiedTerminalPacket.java:81-100`
КОД:
```java
            if (missileType == 1 && !launcherPositions.isEmpty()) {
                for (BlockPos launcherPos : launcherPositions) {
                    BlockState launcherState = level.getBlockState(launcherPos);
                    if (launcherState.getBlock() instanceof LauncherBlock launcherBlock) {
                        if (!launcherState.getValue(LauncherBlock.LOADED)) {
                            if (!launcherBlock.isBlocked(level, launcherPos)) {
                                selectedLauncher = launcherPos;
                                break;
                            }
                        }
                    }
                }
                ...
                launcher.setLoaded(level, selectedLauncher, true);
```
ПОЧЕМУ ЭТО БАГ: условие инвертировано — выбирается ПУ, которая **НЕ заряжена**, после чего ей принудительно ставится `LOADED=true` и создаётся `new IskanderEntity(...)` (строка 129) **без списания единственного `ata:iskander`**: ни `hatch`, ни `LauncherBlockEntity.rocketLoaded`, ни инвентарь игрока не проверяются и не изменяются. Для `missileType == 0` (строка 124) `ShahedEntity` создаётся вообще без каких-либо проверок — достаточно любого `launcherPositions` из пакета. Список `launcherPositions` берётся из клиентского пакета и **не сверяется** с `UnifiedTerminalBlockEntity.getConnectedLaunchers()`, `terminalPos` не проверяется на расстояние до игрока.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ:
1. Поставить пустую ПУ (без ракеты), подключить кабелем к терминалу.
2. Открыть терминал, выбрать цель, нажать «пуск».
3. С пустой ПУ стартует полноценный `IskanderEntity` и уничтожает цель. Инвентарь не изменился.
4. Повторять бесконечно. Ни один `ata:iskander` не нужен вообще — весь экономический баланс мода обнуляется.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
@GameTest
public static void emptyLauncherMustNotFire(GameTestHelper h) {
    BlockPos c = h.absolutePos(new BlockPos(3,2,3));
    h.setBlock(new BlockPos(3,2,3), ModBlocks.LAUNCHER.get().defaultBlockState());
    ServerPlayer p = h.makeMockServerPlayer();
    LaunchFromUnifiedTerminalPacket.handle(
        new LaunchFromUnifiedTerminalPacket(500, 500, c.above(), List.of(c), 1), mockCtx(p));
    h.succeedWhen(() -> // ПАДАЕТ: ровно 1 IskanderEntity заспавнен
        h.assertEntitiesPresent(ModEntities.ISKANDER.get(), 0));
}
```

---

### F-3: Потеря данных при перезагрузке — IskanderEntity не сохраняет `launcherPos`, ПУ навсегда блокируется
СЕРЬЁЗНОСТЬ: critical
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/entity/IskanderEntity.java:119-120, 191-205, 1318-1375`
КОД:
```java
    private BlockPos launcherPos = null;
    private boolean launcherReleased = false;
...
    private void releaseLauncher() {
        if (!launcherReleased && launcherPos != null && !this.level().isClientSide) {
            BlockState state = this.level().getBlockState(launcherPos);
            if (state.getBlock() instanceof LauncherBlock launcher) {
                launcher.setLoaded(this.level(), launcherPos, false);
                launcher.setLaunching(this.level(), launcherPos, false);
            }
            ... launcherEntity.setInFlight(false);
```
ПОЧЕМУ ЭТО БАГ: `addAdditionalSaveData` (строка 1318) сохраняет ~30 полей траектории, но **ни `launcherPos`, ни `launcherReleased` в NBT не пишутся** (`grep '"Launcher' IskanderEntity.java` → пусто). После рестарта сервера / выгрузки-загрузки чанка сущность оживает с `launcherPos == null`, и `releaseLauncher()` в `explode()` (строка 1131) становится no-op. При этом на ПУ уже стоят `LOADED=true`, `LAUNCHING=true` (`IskanderRocketBlockEntity.performLaunch` → `block/entity/IskanderRocketBlockEntity.java:169`) и `LauncherBlockEntity.inFlight=true` (строка 189), которые сохраняются в блокстейте и в BE (`block/entity/LauncherBlockEntity.java:147`). Сбросить их изнутри мода больше нечем: `LoadingHatchBlock` отказывает («С этой ПУ уже запущен Искандер!», `block/LoadingHatchBlock.java:78-81`), `LaunchRocketPacket` отказывает по `LOADED` (строка 55), `RemoveRocketPacket` — по отсутствию блока ракеты (строка 149). ПУ окирпичена навсегда; лечится только сломать и построить заново.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ:
1. Зарядить ПУ, запустить ракету по цели за 3000 блоков (полёт десятки секунд).
2. Пока `IskanderEntity` в воздухе — `/stop` и запустить сервер заново (или просто уйти так, чтобы чанк ракеты выгрузился и загрузился).
3. Ракета долетает и взрывается, но ПУ остаётся `launching=true`/`loaded=true`/`inFlight=true` навсегда.
4. Любая попытка зарядить/запустить/снять ракету отклоняется. ПУ = мусор.
ТЕСТИРУЕМОСТЬ: pure-java
НАБРОСОК ТЕСТА:
```java
@Test void launcherPosMustRoundTrip() {
    IskanderEntity e = new IskanderEntity(level, new Vec3(0,64,0), new BlockPos(500,64,500), true);
    e.setLauncherPos(new BlockPos(10, 64, 10));
    CompoundTag tag = new CompoundTag();
    e.addAdditionalSaveData(tag);
    IskanderEntity e2 = new IskanderEntity(ModEntities.ISKANDER.get(), level);
    e2.readAdditionalSaveData(tag);
    // ПАДАЕТ: launcherPos в NBT отсутствует, поле остаётся null
    assertEquals(new BlockPos(10,64,10), getField(e2, "launcherPos"));
}
```

---

### F-4: 4 из 7 зарегистрированных блоков не имеют loot-таблицы и не помечены `.noLootTable()` — при ломании исчезают
СЕРЬЁЗНОСТЬ: critical
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/block/ModBlocks.java:19-34`
КОД:
```java
    public static final DeferredBlock<Block> SHAHED_RADAR = registerBlock("shahed_radar",
            () -> new ShahedRadarBlock(BlockBehaviour.Properties.of().strength(3.0f)...requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> SIREN = registerBlock("siren",
            () -> new SirenBlock(BlockBehaviour.Properties.of().strength(2.0f)...requiresCorrectToolForDrops()));
    ...
    public static final DeferredBlock<Block> SHAHED_TERMINAL = registerBlock("shahed_terminal", ...);
    ...
    public static final DeferredBlock<Block> LOADING_HATCH = registerBlock("loading_hatch", ...);
```
ПОЧЕМУ ЭТО БАГ: в `src/main/resources/data/ata/loot_table/blocks/` лежат **ровно два** файла — `launcher.json` и `unified_terminal.json`. Датагена нет (`grep -r "LootTableProvider\|BlockLootSubProvider" src/` → 0 совпадений, каталога `datagen` нет). Для `shahed_radar`, `siren`, `shahed_terminal`, `loading_hatch` `BlockBehaviour.getLootTable()` формирует ключ `ata:blocks/<name>`, менеджер лута его не находит, отдаёт `LootTable.EMPTY` и логирует ошибку каждый раз. Все четыре блока **крафтятся** (`data/ata/recipe/shahed_radar.json`, `siren.json`, `shahed_terminal.json`, `loading_hatch.json`), стоят iron/comparator/lightning_rod — и безвозвратно исчезают при ломании киркой.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ:
1. Скрафтить `ata:siren` (9 предметов, включая note_block и медь), поставить.
2. Сломать железной киркой в выживании.
3. Ничего не выпадает. То же для `shahed_radar`, `shahed_terminal`, `loading_hatch`. В логе — `Missing loot table 'ata:blocks/siren'`.
ТЕСТИРУЕМОСТЬ: pure-java
НАБРОСОК ТЕСТА:
```java
@Test void everyRegisteredBlockHasLootTableOrIsExplicitlyExempt() {
    for (DeferredBlock<Block> b : List.of(SHAHED_RADAR, SIREN, UNIFIED_TERMINAL, SHAHED_TERMINAL,
                                          LAUNCHER, ISKANDER_ROCKET, LOADING_HATCH)) {
        String name = b.getId().getPath();
        boolean exempt = b.get().defaultBlockState().getBlock().getLootTable() == BuiltInLootTables.EMPTY;
        boolean hasFile = Files.exists(Path.of("src/main/resources/data/ata/loot_table/blocks/" + name + ".json"));
        // ПАДАЕТ на siren, shahed_radar, shahed_terminal, loading_hatch
        assertTrue(exempt || hasFile, name + " drops nothing");
    }
}
```

---

### F-5: `/setblock ata:launcher` бесконтрольно стирает 5×5 чужих блоков без дропа
СЕРЬЁЗНОСТЬ: critical
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/block/LauncherBlock.java:113-120, 165-185`
КОД:
```java
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && state.getValue(PART) == LauncherPart.CENTER) {
            Direction facing = state.getValue(FACING);
            createMultiblock(level, pos, facing);
        }
    }
    ...
    private void createMultiblock(Level level, BlockPos centerPos, Direction facing) {
        ...
                level.setBlock(pos, this.defaultBlockState()
                        .setValue(FACING, facing) ... , 2);
```
ПОЧЕМУ ЭТО БАГ: проверки `canPlaceMultiblock` (строка 122) и `isNearOtherLauncher` (строка 434) живут **только** в `getStateForPlacement` (строки 96-108), то есть срабатывают исключительно на пути `BlockItem`. `onPlace` вызывается при **любой** установке состояния с `PART=CENTER`: `/setblock`, `/fill`, `/clone`, structure block, генерация структур, `ItemStack` из Create-контраптиона, любые сторонние моды через `Level.setBlock`. В этом пути `createMultiblock` безусловно перезаписывает 24 соседних блока `setBlock(..., 2)` — обычной заменой, **без дропа** (это не `destroyBlock`). Заодно обходится и `MIN_LAUNCHER_DISTANCE=10`: две ПУ можно поставить вплотную.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ:
1. Встать в чужом хранилище с сундуками.
2. `/setblock ~ ~ ~ ata:launcher[part=center]`.
3. 24 соседних блока (сундуки с содержимым, ценные блоки) **молча удалены без дропа**, заменены частями ПУ; на месте `hatchPos` появляется `ata:loading_hatch`.
4. Повторить в 3 блоках рядом — обе ПУ существуют, `findCenterByScanning` теперь может вернуть чужой центр (см. F-9).
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
@GameTest
public static void setblockLauncherMustNotEatNeighbours(GameTestHelper h) {
    h.setBlock(new BlockPos(1,2,3), Blocks.DIAMOND_BLOCK);
    h.setBlock(new BlockPos(3,2,3), ModBlocks.LAUNCHER.get().defaultBlockState()
            .setValue(LauncherBlock.PART, LauncherBlock.LauncherPart.CENTER));
    // ПАДАЕТ: алмазный блок затёрт без дропа
    h.assertBlockPresent(Blocks.DIAMOND_BLOCK, new BlockPos(1,2,3));
}
```

---

### F-6: `StructureRemovalHelper.clearAll()` затирает маркеры чужих, ещё не завершённых удалений
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/block/StructureRemovalHelper.java:24-26` + `block/LauncherBlock.java:355-357`
КОД:
```java
    public static void clearAll() {
        REMOVING_POSITIONS.get().clear();
    }
```
```java
        } finally {
            StructureRemovalHelper.clearAll();
        }
```
ПОЧЕМУ ЭТО БАГ: набор `REMOVING_POSITIONS` глобальный на поток, но `removeMultiblock` в `finally` вызывает `clearAll()`, а не симметричное `finishRemoving` по своим 27 позициям. Внешние кадры стека **уже добавили** свои маркеры до входа: `LauncherBlock.onRemove:243` (`startRemoving(pos)` для сломанной части) и `LoadingHatchBlock.onRemove:173` (`startRemoving(hatchPos)`). После `clearAll()` их `finally { finishRemoving(pos) }` работает по пустому набору, а любой повторный `onRemove` по тем же координатам уже не увидит защиты. Стек рекурсии здесь двусторонний: часть → `destroyBlock(центр)` → `removeMultiblock` → `removeBlock(часть)` → `onRemove(часть)` → снова `destroyBlock(центр)`. Единственное, что сейчас не даёт `StackOverflowError`, — то, что маркеры центра ставятся до цикла удаления. Любое перекрытие двух мультиблоков (см. F-5) или срабатывание внутри `createMultiblock` ломает эту хрупкую инвариантность.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ:
1. `/setblock` двумя ПУ так, чтобы их 5×5 пересекались (F-5 позволяет).
2. Сломать общую часть → onRemove(часть) → destroyBlock(центр A) → removeMultiblock(A) → `removeBlock` затрагивает часть B → onRemove(B) → destroyBlock(центр B) → removeMultiblock(B) → `clearAll()` стирает маркеры A → возврат в цикл A, который продолжает `removeBlock` по уже незащищённым позициям → повторный каскад.
3. Наблюдаемо: непредсказуемое число выпавших `ata:launcher`, часть блоков остаётся сиротами, в логах глубокая рекурсия `onRemove`.
ТЕСТИРУЕМОСТЬ: pure-java
НАБРОСОК ТЕСТА:
```java
@Test void clearAllMustNotDropOuterMarkers() {
    BlockPos outer = new BlockPos(0,0,0);
    StructureRemovalHelper.startRemoving(outer);
    StructureRemovalHelper.startRemoving(new BlockPos(5,0,5));
    StructureRemovalHelper.clearAll();               // имитация вложенного removeMultiblock
    // ПАДАЕТ: маркер внешнего кадра уничтожен
    assertTrue(StructureRemovalHelper.isRemoving(outer));
}
```

---

### F-7: Установка ракеты через терминал не заполняет люк — ракета исчезает при разборке ПУ
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/network/InstallRocketPacket.java:80-89`
КОД:
```java
            player.getInventory().items.get(rocketSlot).shrink(1);

            Direction facing = launcherState.getValue(LauncherBlock.FACING);
            if (IskanderRocketBlock.placeRocket(level, launcherPos, facing)) {
                launcher.setLoaded(level, launcherPos, true);
                player.displayClientMessage(Component.translatable("message.ata.rocket.installed"), true);
            } else {
                player.getInventory().add(new ItemStack(ModItems.ISKANDER.get()));
```
ПОЧЕМУ ЭТО БАГ: зеркальная к F-1 асимметрия. Ракета списывается из инвентаря, ставится блок и `LOADED=true`, но `LoadingHatchBlockEntity.loadRocket()` **не вызывается** и `LauncherBlockEntity.setRocketLoaded(true)` тоже. Люк остаётся `isLoaded=false`, `rocketStack=EMPTY`. Поэтому: (а) выгрузка пустой рукой невозможна — `block/LoadingHatchBlock.java:118` отвечает «ПУ пуста»; (б) при сносе ПУ `LauncherBlock.removeMultiblock` возвращает ракету **только** из люка (`block/LauncherBlock.java:328-338`), значит установленная через терминал ракета **безвозвратно уничтожается**; (в) `hatch.isLoaded()` = false позволяет догрузить в люк вторую ракету, тогда как блок ракеты уже стоит — `placeRocket` вернёт `false` (позиция занята), но `heldItem.shrink(1)` в `block/LoadingHatchBlock.java:95` уже выполнен → **потеря второй ракеты**. Плюс на откате (строка 87) выдаётся `new ItemStack(ISKANDER)`, теряя все компоненты/имя оригинала.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ:
1. ПКМ по терминалу → «установить ракету». Ракета уходит из инвентаря, блок появился.
2. Сломать центральный блок ПУ киркой. Выпадает только `ata:launcher`. **Ракета потеряна.**
3. Вариант: после шага 1 ПКМ по люку со второй ракетой → `hatch.isLoaded()==false`, `shrink(1)` срабатывает, `placeRocket` возвращает false (позиция занята), состояние `LOADED` уже true → вторая ракета съедена без следа.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
@GameTest
public static void terminalInstallMustFillHatch(GameTestHelper h) {
    /* построить ПУ */ ServerPlayer p = h.makeMockServerPlayer();
    p.getInventory().add(new ItemStack(ModItems.ISKANDER.get()));
    InstallRocketPacket.handle(new InstallRocketPacket(centerAbs, termAbs), mockCtx(p));
    LoadingHatchBlockEntity hatch = (LoadingHatchBlockEntity) h.getBlockEntity(hatchRel);
    h.assertTrue(hatch.isLoaded(), "hatch must mirror the installed rocket"); // ПАДАЕТ
}
```

---

### F-8: `LaunchRocketPacket` не проверяет ни владельца, ни расстояние, ни принадлежность терминалу — запуск любой ПУ мира
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/network/LaunchRocketPacket.java:44-58, 83-97`
КОД:
```java
            Level level = player.level();
            BlockPos launcherPos = payload.launcherPos();
            ...
            BlockState launcherState = level.getBlockState(launcherPos);
            if (!(launcherState.getBlock() instanceof LauncherBlock launcher)) { ... return; }
            if (!launcherState.getValue(LauncherBlock.LOADED)) { ... return; }
            ...
            rocketEntity.startLaunch(targetPos, launcherPos, player);
```
ПОЧЕМУ ЭТО БАГ: `launcherPos` целиком контролируется клиентом. Не проверяется: (1) расстояние от игрока до ПУ; (2) что ПУ есть в `UnifiedTerminalBlockEntity.getConnectedLaunchers()` терминала, чей `terminalPos` пришёл в пакете (`terminalPos` вообще **нигде не читается** — единственное упоминание в файле это строка 28, геттер стрим-кодека); (3) что `PART == CENTER` (в отличие от `InstallRocketPacket.java:51`, где проверка есть). Побочно: `level.getBlockState(launcherPos)` на сервере **синхронно догружает чанк** по произвольным координатам, а `launcher.isBlocked` (строка 78) сканирует 5×5×40 = **1000** `getBlockState` за пакет — это готовый вектор нагрузочной атаки без рейт-лимита.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ:
1. Игрок A строит и заряжает ПУ на своей базе в 5000 блоков от спавна.
2. Игрок B (модифицированный клиент) шлёт `LaunchRocketPacket(posA, BlockPos.ZERO, x, z)` с координатами базы A.
3. Ракета A стартует по цели, выбранной B. У A расходуется ракета, репутация владельца пишется как B (`ownerUUID` = B, `block/entity/IskanderRocketBlockEntity.java:235`).
4. Он же в цикле шлёт пакеты с координатами по спирали → сервер синхронно грузит чанки и делает 1000 `getBlockState` на пакет.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
@GameTest
public static void remoteLauncherMustRejectFarPlayer(GameTestHelper h) {
    /* заряженная ПУ в (3,2,3) */
    ServerPlayer far = h.makeMockServerPlayer(); far.teleportTo(100000, 64, 100000);
    LaunchRocketPacket.handle(new LaunchRocketPacket(centerAbs, BlockPos.ZERO, 500, 500), mockCtx(far));
    IskanderRocketBlockEntity be = (IskanderRocketBlockEntity) h.getBlockEntity(new BlockPos(3,3,3));
    h.assertFalse(be.isLaunching(), "launch accepted from 140k blocks away"); // ПАДАЕТ
}
```

---

### F-9: `findCenterByScanning` возвращает первый попавшийся центр — снос части может снести чужую ПУ
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/block/LauncherBlock.java:267-282`
КОД:
```java
    private BlockPos findCenterByScanning(Level level, BlockPos pos) {
        if (level == null) return null;
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos checkPos = pos.offset(x, 0, z);
                BlockState checkState = level.getBlockState(checkPos);
                if (checkState.getBlock() instanceof LauncherBlock) {
                    if (checkState.getValue(PART) == LauncherPart.CENTER) {
                        return checkPos;
                    }
                }
            }
        }
        return null;
    }
```
ПОЧЕМУ ЭТО БАГ: (а) поиск не проверяет `FACING`, не сверяет ожидаемый `PART` со смещением и не спрашивает `LauncherBlockEntity`, действительно ли этот центр «владеет» позицией — берётся первый центр в порядке обхода `x=-2..2, z=-2..2`, то есть **северо-западный**. При двух центрах в радиусе 2 (достижимо через F-5, `/fill`, structure block) деталь одной ПУ уводит `onRemove` на **чужой** центр и `level.destroyBlock(centerPos, true)` (строка 248) сносит соседнюю установку с дропом. (б) Поиск идёт только по горизонтали `y=0` — часть ПУ, оказавшаяся выше/ниже центра, не находит его никогда. (в) Если центр отсутствует (заменён другим модом/поршнем через `movable`-механику Create, повреждение мира), возвращается `null` и `onRemove` не делает ничего: 24 куска остаются сиротами, а loot-таблица `data/ata/loot_table/blocks/launcher.json` дропает предмет только при `part=center` → **сироты ломаются в ноль**.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ:
1. `/setblock` две ПУ с перекрытием 5×5 (F-5).
2. Сломать угловую часть, принадлежащую южной ПУ.
3. `findCenterByScanning` возвращает СЗ-центр (чужой) → уничтожается **не та** установка; южная остаётся с дырой и продолжает считаться валидной.
4. Обратный случай: `/setblock <центр> minecraft:bedrock` в чанке, где мультиблок разорван; оставшиеся 24 куска ломаются с нулевым дропом.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
@GameTest
public static void orphanPartMustStillYieldSomething(GameTestHelper h) {
    h.setBlock(new BlockPos(3,2,3), launcherState(LauncherPart.PLATFORM)); // сирота без центра
    h.destroyBlock(new BlockPos(3,2,3), true);
    h.assertItemEntityPresent(ModItems.LAUNCHER_ITEM.get(), new BlockPos(3,2,3), 2.0); // ПАДАЕТ: 0 дропа
}
```

---

### F-10: `SavedCoords` — data component с `int[]` внутри: сломаны `equals`/`hashCode`, компонент мутабелен
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/item/SavedCoords.java:20-22` + `item/ControlTabletItem.java:165-171`
КОД:
```java
public record SavedCoords(Map<Integer, int[]> entries) {

    public static final SavedCoords EMPTY = new SavedCoords(new HashMap<>());
```
```java
    public static void setSavedCoords(ItemStack stack, Map<Integer, int[]> coords) {
        ...
            stack.set(ModDataComponents.SAVED_COORDS.get(), new SavedCoords(new java.util.HashMap<>(coords)));
```
ПОЧЕМУ ЭТО БАГ: в 1.21 data-компонент обязан быть иммутабельным и иметь корректные `equals`/`hashCode` — на них построены `ItemStack.isSameItemSameComponents`, `DataComponentPatch`, стакинг и диффинг инвентаря. Сгенерированный рекордом `equals` делегирует в `HashMap.equals`, который сравнивает значения через `int[].equals` — **сравнение по ссылке**. Два планшета с идентичными координатами никогда не равны, `hashCode` тоже основан на identity-хешах массивов. Следствия: `AbstractContainerMenu.broadcastChanges` считает стак изменившимся **каждый тик**, пока открыт любой контейнер, и заново шлёт `SetSlot` клиенту (лишний трафик + мигание тултипа). Мутабельность тоже реальна: `new HashMap<>(coords)` — **поверхностная** копия, массивы `int[]` остаются общими с вызывающим кодом, любая последующая мутация исходной мапы правит уже сохранённый компонент. `EMPTY` — общий мутабельный `HashMap`, публично доступный на запись.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ:
1. Сохранить координату в `ata:control_tablet` через GUI планшета.
2. Открыть сундук (любой контейнер) и стоять с открытым GUI.
3. Профайлер сети: сервер шлёт `ClientboundContainerSetSlotPacket` для слота планшета каждый тик, потому что `ItemStack.matches` вечно false.
ТЕСТИРУЕМОСТЬ: pure-java
НАБРОСОК ТЕСТА:
```java
@Test void savedCoordsMustBeValueEqual() {
    Map<Integer,int[]> a = Map.of(0, new int[]{10,20});
    Map<Integer,int[]> b = Map.of(0, new int[]{10,20});
    // ПАДАЕТ обе строки
    assertEquals(new SavedCoords(new HashMap<>(a)), new SavedCoords(new HashMap<>(b)));
    assertEquals(new SavedCoords(new HashMap<>(a)).hashCode(), new SavedCoords(new HashMap<>(b)).hashCode());
}

@Test void setSavedCoordsMustDeepCopy() {
    Map<Integer,int[]> src = new HashMap<>(); src.put(0, new int[]{1,2});
    ItemStack s = new ItemStack(ModItems.CONTROL_TABLET.get());
    ControlTabletItem.setSavedCoords(s, src);
    src.get(0)[0] = 999;                                  // мутируем СВОЮ мапу
    assertEquals(1, ControlTabletItem.getSavedCoords(s).get(0)[0]); // ПАДАЕТ: 999
}
```

---

### F-11: `LaunchInterfaceCableItem` сохраняет измерение, но никогда его не проверяет
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/item/LaunchInterfaceCableItem.java:52-53, 71-85`
КОД:
```java
                stack.set(ModDataComponents.REGISTERED_LAUNCHER_DIMENSION.get(),
                        level.dimension().location().toString());
...
            if (!level.isClientSide) {
                double distance = Math.sqrt(pos.distSqr(launcherPos));
                if (distance > MAX_CABLE_DISTANCE) { ... return InteractionResult.FAIL; }
                BlockState launcherState = level.getBlockState(launcherPos);
                if (!(launcherState.getBlock() instanceof LauncherBlock)) { ... }
```
ПОЧЕМУ ЭТО БАГ: `REGISTERED_LAUNCHER_DIMENSION` записывается (строка 52) и стирается (строка 128), но **никогда не читается** — `grep -rn REGISTERED_LAUNCHER_DIMENSION` даёт ровно 4 совпадения: объявление, запись, удаление и тултип-независимую строку. При подключении сравниваются только координаты, `level` берётся тот, в котором игрок стоит **сейчас**. Также не проверяется `PART == CENTER` у сохранённой позиции (в отличие от момента регистрации, `findLauncherCenter:132`).
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ:
1. В Нижнем мире зарегистрировать ПУ по координатам (100, 64, 100).
2. Вернуться в Обычный мир, построить там ПУ так, чтобы её центр тоже оказался в (100, 64, 100), и терминал в 20 блоках.
3. ПКМ кабелем по терминалу Обычного мира → проверка расстояния проходит, `level.getBlockState(100,64,100)` находит **другую** ПУ → терминал подключается к чужой установке, а `LauncherBlockEntity.setConnectedTerminal` пишется поверх существующей связи.
4. Обычный случай: та же связка молча падает с «ПУ по сохранённым координатам не найдена!», хотя реальная ПУ существует — в другом измерении.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
@GameTest
public static void cableMustRefuseCrossDimensionLink(GameTestHelper h) {
    ItemStack cable = new ItemStack(ModItems.LAUNCH_INTERFACE_CABLE.get());
    cable.set(ModDataComponents.REGISTERED_LAUNCHER.get(), h.absolutePos(new BlockPos(3,2,3)));
    cable.set(ModDataComponents.REGISTERED_LAUNCHER_DIMENSION.get(), "minecraft:the_nether");
    /* терминал в overworld в 5 блоках, ПУ в overworld в (3,2,3) */
    InteractionResult r = cable.useOn(ctxOnTerminal());
    h.assertTrue(r == InteractionResult.FAIL, "cross-dimension link accepted"); // ПАДАЕТ
}
```

---

### F-12: `SirenBlock.tick` рассылает пакет каждому игроку сервера, включая другие измерения
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/block/SirenBlock.java:45-49`
КОД:
```java
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED)) {
            NetworkHandler.sendToAllPlayers(new SirenSoundPacket(pos));
            level.scheduleTick(pos, this, 500);
        }
    }
```
ПОЧЕМУ ЭТО БАГ: `NetworkHandler.sendToAllPlayers` → `PacketDistributor.sendToAllPlayers` (`network/NetworkHandler.java:24`) — **без** фильтра по расстоянию и без фильтра по измерению. Пакет содержит только `BlockPos` (без ключа мира), значит клиент не может отличить сирену в Незере от сирены в Обычном мире и воспроизводит звук по совпадающим координатам. Стоимость линейная: 100 сирен × 200 игроков = 20 000 пакетов каждые 500 тиков, плюс `onRemove:65` шлёт стоп-пакет всем при любом сломе. Правильная примитивная альтернатива в 1.21 — `PacketDistributor.sendToPlayersNear(level, null, x, y, z, radius, payload)`.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ:
1. Поставить сирену в Незере на (0, 64, 0), подать редстоун.
2. Игрок в Обычном мире, стоящий рядом с (0, 64, 0), слышит вой сирены из другого измерения.
3. На сервере со 100 сиренами и 200 игроками — 20 000 пакетов раз в 25 секунд плюс постоянная загрузка тик-планировщика.
ТЕСТИРУЕМОСТЬ: manual
НАБРОСОК ТЕСТА:
```java
@GameTest
public static void sirenMustNotSpamOtherDimensions(GameTestHelper h) {
    ServerPlayer netherGuy = spawnPlayerIn(h.getLevel().getServer().getLevel(Level.NETHER));
    CapturingConnection cap = capture(netherGuy);
    h.setBlock(new BlockPos(2,2,2), ModBlocks.SIREN.get().defaultBlockState().setValue(SirenBlock.POWERED, true));
    h.runAfterDelay(2, () -> h.assertValueEqual(cap.count(SirenSoundPacket.class), 0, "siren leaked")); // ПАДАЕТ
}
```

---

### F-13: `ShahedRadarBlock` каждую секунду сканирует AABB 3000×464×3000 на каждый радар
СЕРЬЁЗНОСТЬ: high
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/block/ShahedRadarBlock.java:37, 85-99, 102-124`
КОД:
```java
    private static final int RADAR_RANGE = 1500;
...
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int newPower = calculateSignalStrength(level, pos);
        ...
        level.scheduleTick(pos, this, 20);
    }

    private int calculateSignalStrength(ServerLevel level, BlockPos pos) {
        AABB searchBox = new AABB(
            radarCenter.x - RADAR_RANGE, radarCenter.y - 64, radarCenter.z - RADAR_RANGE,
            radarCenter.x + RADAR_RANGE, radarCenter.y + 400, radarCenter.z + RADAR_RANGE);
        ...
        List<Entity> entities = level.getEntities(null, searchBox);
        for (Entity entity : entities) {
```
ПОЧЕМУ ЭТО БАГ: коробка 3000 × 464 × 3000 блоков = 188 × 29 × 188 секций сущностей. `Level.getEntities(null, box)` обходит **все непустые секции сущностей** в X-полосе шириной 188 секций (`EntitySectionStorage.forEachAccessibleNonEmptySection` итерирует `subSet` по X-слэбам), затем ещё раз фильтрует каждую сущность по AABB, и — главное — **материализует `List<Entity>` со всеми сущностями сервера в этой полосе** (мобы, предметы, вагонетки, всё), прежде чем найти среди них один `ShahedEntity`/`IskanderEntity`. Это делается **раз в секунду на каждый радар** (`scheduleTick(pos, this, 20)`), и вдобавок `level.updateNeighborsAt` вызывается на 7 позиций при любом изменении `POWER` (строки 90-94), что на летящей цели меняется почти каждый тик-цикл. Дешёвая альтернатива — `level.getEntities(EntityTypeTest.forClass(ShahedEntity.class), box, pred, out, limit)` вместо `null`.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ:
1. Поставить 20 радаров на сервере с обычной мобовой популяцией.
2. Наблюдать `/debug start` → секция `tickBlockEntities`/`entityTick`: 20 полных обходов секционного индекса с аллокацией `ArrayList` на тысячи сущностей каждую секунду.
3. MSPT растёт линейно от числа радаров и от числа загруженных сущностей, даже когда в воздухе нет ни одной ракеты.
ТЕСТИРУЕМОСТЬ: manual
НАБРОСОК ТЕСТА:
```java
@GameTest(timeoutTicks = 200)
public static void radarScanMustBeTyped(GameTestHelper h) {
    for (int i = 0; i < 2000; i++) h.spawn(EntityType.ITEM, new BlockPos(3,2,3));
    h.setBlock(new BlockPos(1,2,1), ModBlocks.SHAHED_RADAR.get());
    long t = System.nanoTime();
    h.runAfterDelay(40, () -> {
        long ms = (System.nanoTime()-t)/1_000_000;
        h.assertTrue(ms < 50, "radar scan cost " + ms + "ms"); // ПАДАЕТ
    });
}
```

---

### F-14: `UnifiedTerminalBlockEntity.connectedLaunchers` не ограничен по размеру и валидируется только при открытии GUI
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/block/entity/UnifiedTerminalBlockEntity.java:22, 28-35, 66-79` + `block/UnifiedTerminalBlock.java:80`
КОД:
```java
    private final List<BlockPos> connectedLaunchers = new ArrayList<>();

    public boolean addConnectedLauncher(BlockPos pos) {
        if (!connectedLaunchers.contains(pos)) {
            connectedLaunchers.add(pos);
```
```java
            terminal.validateConnectedLaunchers();   // единственный вызов во всём моде
```
ПОЧЕМУ ЭТО БАГ: `validateConnectedLaunchers` вызывается **ровно в одном месте** (`grep -rn validateConnectedLaunchers` → 1 совпадение вне самого BE), при ПКМ по терминалу. Между открытиями список копится: любые ПУ, снесённые не через `removeMultiblock` (взрыв, `/fill`, WorldEdit, другой мод), остаются висячими ссылками. Верхней границы нет: `addConnectedLauncher` только дедуплицирует. Каждая запись сериализуется в `saveAdditional:87` **и** уходит в `getUpdateTag:121` → рассылается всем клиентам в зоне видимости при каждом `syncToClient()`. Вдобавок сама валидация делает `level.getBlockState(pos)` по N позициям, **синхронно догружая чанки** каждой ПУ при каждом открытии терминала.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ:
1. Подключить к терминалу 30 ПУ (`MAX_CABLE_DISTANCE=60` при `MIN_LAUNCHER_DISTANCE=10` даёт место для ~100).
2. Взорвать половину ПУ ракетой / `/fill` в air.
3. Не открывая терминал, перезагрузить мир: `ConnectedLaunchers` содержит 30 записей, из них 15 указывают в пустоту; клиенты получают их в `getUpdateTag`.
4. Открытие терминала догружает 30 чанков синхронно в главном потоке.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
@GameTest
public static void terminalMustCapConnections(GameTestHelper h) {
    UnifiedTerminalBlockEntity t = (UnifiedTerminalBlockEntity) h.getBlockEntity(new BlockPos(1,2,1));
    for (int i = 0; i < 10_000; i++) t.addConnectedLauncher(new BlockPos(i, 0, 0));
    h.assertTrue(t.getConnectedLauncherCount() <= 32, "unbounded launcher list"); // ПАДАЕТ: 10000
}
```

---

### F-15: `LaunchInterfaceCableItem` никогда не расходуется — один кабель соединяет бесконечно
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/item/LaunchInterfaceCableItem.java:100-118`
КОД:
```java
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof UnifiedTerminalBlockEntity terminal) {
                    if (terminal.isLauncherConnected(launcherPos)) { ... }
                    else {
                        terminal.addConnectedLauncher(launcherPos);
                        if (launcherBE instanceof LauncherBlockEntity launcherEntity2) {
                            launcherEntity2.setConnectedTerminal(pos);
                        }
                        ...
                    }
                    clearCableData(stack);
```
ПОЧЕМУ ЭТО БАГ: после успешного подключения вызывается только `clearCableData(stack)` — ни `stack.shrink(1)`, ни `stack.hurtAndBreak`. Предмет спроектирован как расходник (крафтится по 2 из 4 меди + 5 редстоуна, `data/ata/recipe/launch_interface_cable.json`, `stacksTo(16)` в `item/ModItems.java:357`), но одной штуки хватает на неограниченное число соединений. Отдельно: компоненты ставятся на **весь стак** (`stack.set(...)` в строках 51-53), то есть привязка 16 кабелей общая — но это не создаёт дюп, только неочевидное поведение.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ:
1. Скрафтить 1× `ata:launch_interface_cable`.
2. Подключить им 20 ПУ к терминалам подряд.
3. В инвентаре по-прежнему 1× кабель. Рецепт бессмыслен.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
@GameTest
public static void cableMustBeConsumed(GameTestHelper h) {
    ItemStack cable = new ItemStack(ModItems.LAUNCH_INTERFACE_CABLE.get(), 2);
    cable.set(ModDataComponents.REGISTERED_LAUNCHER.get(), centerAbs);
    cable.useOn(ctxOnTerminal(cable));
    h.assertValueEqual(cable.getCount(), 1, "cable not consumed"); // ПАДАЕТ: 2
}
```

---

### F-16: `IskanderRocketBlockEntity` не восстанавливает `LAUNCHING` на блокстейте ПУ и не сбрасывает `launcherCenterPos` при отсутствии тега
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/block/entity/IskanderRocketBlockEntity.java:308-321`
КОД:
```java
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        launching = tag.getBoolean("Launching");
        launchTicks = tag.getInt("LaunchTicks");
        targetPos = new BlockPos(tag.getInt("TargetX"), tag.getInt("TargetY"), tag.getInt("TargetZ"));
        if (tag.contains("LauncherX")) {
            launcherCenterPos = new BlockPos(tag.getInt("LauncherX"), tag.getInt("LauncherY"), tag.getInt("LauncherZ"));
        }
        if (tag.contains("OwnerUUID")) {
            ownerUUID = tag.getUUID("OwnerUUID");
            ownerName = tag.getString("OwnerName");
        }
    }
```
ПОЧЕМУ ЭТО БАГ: (а) ни одна из веток `if (tag.contains(...))` не имеет `else`, обнуляющего поле — на пути повторного `loadAdditional` по уже живому BE (а он существует: `ClientboundBlockEntityDataPacket` → `BlockEntity.onDataPacket` → `loadAdditional` на клиенте) старое `launcherCenterPos`/`ownerUUID` сохраняется, хотя сервер их уже не шлёт. Ср. с `LauncherBlockEntity.loadAdditional:186-190`, где для `termX` `else`-ветка есть — то есть автор знал про паттерн, но применил его непоследовательно. (б) `targetPos` пишется тремя `putInt` без флага наличия: BE, созданный до первого `startLaunch`, читает `targetPos = (0,0,0)`, а не `null` — молчаливая цель в координатах спавна вместо ошибки. (в) `cancelLaunch()` (строка 248) не вызывается **нигде** во всём моде (`grep -rn cancelLaunch` → только объявление): отменить обратный отсчёт после старта невозможно ни одним игровым действием.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ:
1. Запустить обратный отсчёт (140 тиков), решить отменить — интерфейса/пакета нет, ракета уйдёт гарантированно.
2. Через `/data merge block` подложить BE с `Launching:1b` без `TargetX` → `targetPos=(0,0,0)`, ракета летит на спавн.
ТЕСТИРУЕМОСТЬ: pure-java
НАБРОСОК ТЕСТА:
```java
@Test void loadAdditionalMustResetAbsentFields() {
    IskanderRocketBlockEntity be = new IskanderRocketBlockEntity(BlockPos.ZERO, state);
    be.startLaunch(new BlockPos(1,2,3), new BlockPos(9,9,9), null);
    be.loadAdditional(new CompoundTag(), registries);   // пустой тег
    assertNull(getField(be, "launcherCenterPos"));      // ПАДАЕТ: (9,9,9)
}
```

---

### F-17: `SprayCanFillingRecipe` съедает лишние красители — крафт на 8 слотах теряет до 7 dye
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/recipe/SprayCanFillingRecipe.java:200-205`
КОД:
```java
        int currentCharge = SprayCanItem.getCharge(canStack);
        int chargeNeeded = SprayCanItem.MAX_CHARGE - currentCharge;
        int dyesNeeded = (int) Math.ceil((float) chargeNeeded / CHARGE_PER_DYE);
        int dyesUsed = Math.min(dyeSlots, dyesNeeded);
        int addCharge = dyesUsed * CHARGE_PER_DYE;
        SprayCanItem.setCharge(result, Math.min(SprayCanItem.MAX_CHARGE, currentCharge + addCharge));
```
ПОЧЕМУ ЭТО БАГ: ванильный крафт снимает **по одному предмету с каждого занятого слота** сетки безусловно; `getRemainingItems` не переопределён и вернуть их не может. Формула же учитывает только `dyesUsed = min(dyeSlots, dyesNeeded)`. Если баллон почти полон (`chargeNeeded = 30`, `dyesNeeded = 1`), а игрок положил 8 красителей, `dyesUsed = 1`, но с гриды исчезнут **все 8** — 7 красителей уничтожены без компенсации. Симметрично, `matches` (строка 164) считает `dyeCount += stack.getCount()` — по количеству в стаке, а `assemble` считает `dyeSlots++` — по числу слотов: две несогласованные метрики в одном рецепте. Плюс `assemble` создаёт `new ItemStack(SPRAY_CAN)`, теряя все прочие компоненты исходного баллона (кастомное имя, зачарования, `HAS_WATER` сбрасывается намеренно — но и всё остальное вместе с ним).
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ:
1. Баллон с зарядом 280/300 и залитой водой.
2. Положить в верстак баллон + 8 чёрных красителей в 8 слотов.
3. Забрать результат: заряд 300/300, красители израсходованы **все 8** вместо 1. Потеря 7 предметов за крафт.
ТЕСТИРУЕМОСТЬ: pure-java
НАБРОСОК ТЕСТА:
```java
@Test void fillingMustNotEatSurplusDye() {
    ItemStack can = new ItemStack(ModItems.SPRAY_CAN.get());
    SprayCanItem.setCharge(can, 280); SprayCanItem.setHasWater(can, true);
    CraftingInput in = CraftingInput.of(3,3, List.of(can, DYE, DYE, DYE, DYE, DYE, DYE, DYE, DYE));
    NonNullList<ItemStack> rem = new SprayCanFillingRecipe(CraftingBookCategory.MISC).getRemainingItems(in);
    long returned = rem.stream().filter(s -> s.is(Items.BLACK_DYE)).count();
    assertEquals(7, returned); // ПАДАЕТ: 0
}
```

---

### F-18: `getStateForPlacement` сканирует 3 125 блоков и вложенно ещё 25 на каждый найденный — на каждый клик установки
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/block/LauncherBlock.java:434-463`
КОД:
```java
    private boolean isNearOtherLauncher(Level level, BlockPos centerPos) {
        int searchRadius = MIN_LAUNCHER_DISTANCE + 2;
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    ...
                    BlockState state = level.getBlockState(checkPos);
                    if (state.getBlock() instanceof LauncherBlock) {
                        BlockPos otherCenter = getCenterPos(level, checkPos, state);
```
ПОЧЕМУ ЭТО БАГ: `searchRadius = 12` → 25 × 5 × 25 = **3 125** вызовов `getBlockState` на каждую попытку установки, и для каждого попавшегося куска ПУ — ещё 25 вызовов внутри `findCenterByScanning`. Радиус 12 гарантированно пересекает границы чанков, значит на сервере эти чтения **синхронно догружают до 4 чанков**. Дополнительно: `getStateForPlacement` вызывается и на **клиенте** (предсказание `BlockItem.getPlacementState`), где невыгруженные чанки читаются как воздух — при рассинхроне клиент показывает поставленный блок, сервер отклоняет (`return null`) и вызывает откат/призрачные блоки. Ещё до этого `canPlaceMultiblock` делает свои 24 `getBlockState`.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ:
1. Зажать ПКМ с `ata:launcher` на границе прогруженной зоны, спамя попытки установки.
2. Каждый клик = 3 125+ `getBlockState` на сервере + догрузка чанков; при отказе — призрачный блок на клиенте до следующего чанк-апдейта.
ТЕСТИРУЕМОСТЬ: manual
НАБРОСОК ТЕСТА:
```java
@GameTest(timeoutTicks = 100)
public static void placementCheckMustBeCheap(GameTestHelper h) {
    CountingLevel lvl = new CountingLevel(h.getLevel());
    ModBlocks.LAUNCHER.get().getStateForPlacement(ctx(lvl, new BlockPos(8,2,8)));
    h.assertTrue(lvl.blockStateReads() < 200, "reads=" + lvl.blockStateReads()); // ПАДАЕТ: ~3150
}
```

---

### F-19: `MissileChunkManager` форсирует чанки через `setChunkForced` — они переживают краш и освобождаются пересекающимися ракетами
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/chunk/MissileChunkManager.java:30-33, 118-131, 135-149`
КОД:
```java
 * TODO: if multiple missiles overlap chunks and one unregisters before the other, the chunk
 *       will be released even though the second missile still needs it.
...
        for (ChunkPos chunk : chunksToUnload) {
            level.setChunkForced(chunk.x, chunk.z, false);
            currentLoaded.remove(chunk);
        }
```
ПОЧЕМУ ЭТО БАГ: (а) `ServerLevel.setChunkForced` пишет в **персистентный** набор `forcedChunks` уровня, который сохраняется в данные мира. Если сервер падает или останавливается, пока ракета в полёте, — форс остаётся в файле мира навсегда, чанки грузятся вечно, `unregisterMissile` их уже не разожмёт (учёт `loadedChunksPerMissile` живёт только в памяти). (б) Учёт ведётся по `missile.getId()` — **int-идентификатор сущности**, который переиспользуется после рестарта: старая запись в `ConcurrentHashMap` может быть перезаписана новой сущностью с тем же id, и чанки предыдущей утекут. (в) Собственный TODO кода признаёт, что при пересечении зон двух ракет разгрузка одной снимает форс, нужный второй, — чанк выгружается под летящей ракетой.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ:
1. Запустить `IskanderEntity` на 5000 блоков.
2. `kill -9` серверу через 5 секунд полёта.
3. Запустить сервер: `/forceload query` показывает 9-16 навсегда форснутых чанков, никто их не снимет.
4. Второй сценарий: запустить две ракеты параллельными курсами в 1 чанке друг от друга; при взрыве первой чанки под второй разжимаются, и её `getBlockState` начинает синхронно догружать/выгружать.
ТЕСТИРУЕМОСТЬ: manual
НАБРОСОК ТЕСТА:
```java
@GameTest
public static void overlappingMissilesMustKeepChunks(GameTestHelper h) {
    Entity a = spawnMissile(h), b = spawnMissileSamePos(h);
    MissileChunkManager.registerMissile(a, h.getLevel());
    MissileChunkManager.registerMissile(b, h.getLevel());
    MissileChunkManager.unregisterMissile(a);
    ChunkPos cp = new ChunkPos(b.blockPosition());
    h.assertTrue(h.getLevel().getForcedChunks().contains(cp.toLong()), "chunk released under live missile"); // ПАДАЕТ
}
```

---

### F-20: Ни `SetLauncherTargetPacket`, ни `RemoveRocketPacket` не проверяют дистанцию/принадлежность — любой игрок правит чужую ПУ
СЕРЬЁЗНОСТЬ: medium
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/network/SetLauncherTargetPacket.java:184-199`
КОД:
```java
    public static void handle(SetLauncherTargetPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            BlockEntity be = player.level().getBlockEntity(payload.launcherPos());
            if (be instanceof LauncherBlockEntity launcher) {
                BlockPos target = payload.hasTarget() ? payload.targetPos() : null;
                launcher.setTarget(target);
```
ПОЧЕМУ ЭТО БАГ: единственная проверка — «в этой позиции есть `LauncherBlockEntity`». Ни расстояния до игрока, ни открытого терминала, ни `connectedLaunchers`. `launcher.setTarget` вызывает `calculateTargetParameters()` (`block/entity/LauncherBlockEntity.java:77`) и `syncToClient()` — то есть сервер пишет в чужой BE и рассылает обновление. `RemoveRocketPacket.java:136` аналогично не проверяет ни дистанцию, ни `PART == CENTER`. `player.level().getBlockEntity(произвольный BlockPos)` синхронно догружает чанк.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ:
1. Игрок B узнаёт координаты ПУ игрока A (по следу запуска или `/tp`).
2. B шлёт `SetLauncherTargetPacket(posA, base_of_A, true)` — цель ПУ A перенацелена на его же базу.
3. A нажимает «пуск» в терминале, не заметив подмены (цель в GUI обновляется корректно, но он смотрит на старое состояние экрана).
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
@GameTest
public static void targetPacketMustCheckRange(GameTestHelper h) {
    ServerPlayer far = h.makeMockServerPlayer(); far.teleportTo(50000, 64, 50000);
    SetLauncherTargetPacket.handle(new SetLauncherTargetPacket(centerAbs, new BlockPos(1,1,1), true), mockCtx(far));
    LauncherBlockEntity be = (LauncherBlockEntity) h.getBlockEntity(new BlockPos(3,2,3));
    h.assertFalse(be.hasTarget(), "target set from 50k blocks away"); // ПАДАЕТ
}
```

---

### F-21: `LoadingHatchBlock.useItemOn` списывает ракету до проверки, что блок ракеты реально встал
СЕРЬЁЗНОСТЬ: low
ФАЙЛ: `src/main/java/com/synarsis/airtacticalarsenal/block/LoadingHatchBlock.java:93-106`
КОД:
```java
            ItemStack rocketCopy = heldItem.copy();
            rocketCopy.setCount(1);
            hatch.loadRocket(rocketCopy);

            heldItem.shrink(1);
            ...
            if (launcherState2.getBlock() instanceof LauncherBlock launcher) {
                Direction facing = launcherState2.getValue(LauncherBlock.FACING);
                IskanderRocketBlock.placeRocket(world, launcherPos, facing);
                launcher.setLoaded(world, launcherPos, true);
            }
```
ПОЧЕМУ ЭТО БАГ: возвращаемое значение `IskanderRocketBlock.placeRocket` (`block/IskanderRocketBlock.java:114-125`, возвращает `false`, если `rocketPos` не воздух) **игнорируется**, а `launcher.setLoaded(..., true)` ставится безусловно. Проверка `launcher.isBlocked` (строка 78) выполняется **раньше** и намеренно пропускает уже стоящий `IskanderRocketBlock` (`block/LauncherBlock.java:481`), то есть не защищает от этого случая. Результат: `LOADED=true` и `hatch.isLoaded=true` без нового блока ракеты — а над центром стоит старый, чужой (например, оставшийся после F-7). Дополнительно: между `heldItem.copy()` и `shrink(1)` состояние гонки отсутствует, но порядок «сначала списать, потом попробовать поставить» — источник любой будущей потери.
СЦЕНАРИЙ ВОСПРОИЗВЕДЕНИЯ:
1. Установить ракету через терминал (F-7): блок стоит, `hatch.isLoaded == false`.
2. ПКМ по люку со второй ракетой: проверка `hatch.isLoaded()` проходит, `isBlocked` пропускает (там `IskanderRocketBlock`), `shrink(1)` съедает вторую ракету, `placeRocket` возвращает `false`.
3. В мире один блок ракеты, в люке один `rocketStack`, у игрока минус две ракеты вместо одной.
ТЕСТИРУЕМОСТЬ: gametest
НАБРОСОК ТЕСТА:
```java
@GameTest
public static void hatchMustNotEatRocketWhenSlotOccupied(GameTestHelper h) {
    IskanderRocketBlock.placeRocket(h.getLevel(), centerAbs, Direction.NORTH); // блок уже стоит
    ServerPlayer p = h.makeMockServerPlayer();
    ItemStack r = new ItemStack(ModItems.ISKANDER.get());
    p.setItemInHand(InteractionHand.MAIN_HAND, r);
    h.getBlockState(hatchRel).useItemOn(r, h.getLevel(), p, InteractionHand.MAIN_HAND, hitOnHatch());
    h.assertValueEqual(r.getCount(), 1, "rocket consumed but not placed"); // ПАДАЕТ: 0
}
```

---

## Сводка по критичности

| № | Класс дефекта | Серьёзность |
|---|---|---|
| F-1 | Бесконечный дюп `ata:iskander` (терминал ↔ люк) | critical |
| F-2 | Спавн ракет/дронов без расхода боеприпаса + невалидированный список ПУ из пакета | critical |
| F-3 | Потеря `launcherPos` при перезагрузке → ПУ окирпичена навсегда | critical |
| F-4 | 4 блока без loot-таблицы и без `.noLootTable()` → исчезают при ломании | critical |
| F-5 | `onPlace` стирает 5×5 чужих блоков без дропа при `/setblock`/`/fill`/структурах | critical |
| F-6 | `clearAll()` рушит защиту от рекурсии внешних кадров | high |
| F-7 | Установка через терминал не заполняет люк → ракета теряется при разборке | high |
| F-8 | `LaunchRocketPacket` без проверок владельца/дистанции/терминала | high |
| F-9 | `findCenterByScanning` находит чужой центр / сироты дропают ноль | high |
| F-10 | `SavedCoords`: сломаны `equals`/`hashCode`, поверхностная копия, мутабельный `EMPTY` | high |
| F-11 | Кабель хранит измерение, но никогда его не проверяет | high |
| F-12 | Сирена шлёт пакет всем игрокам всех измерений | high |
| F-13 | Радар: AABB 3000×464×3000 нетипизированным `getEntities`, раз в секунду | high |
| F-14 | `connectedLaunchers` без лимита, валидация только при открытии GUI | medium |
| F-15 | Кабель не расходуется | medium |
| F-16 | `loadAdditional` без `else`-сброса, `cancelLaunch` мёртв | medium |
| F-17 | Рецепт заправки съедает лишние красители (метрика слотов vs стаков) | medium |
| F-18 | 3 125 `getBlockState` + догрузка чанков на каждый клик установки | medium |
| F-19 | `setChunkForced` персистентен: утечка форс-чанков после краша, конфликт ракет | medium |
| F-20 | `SetLauncherTargetPacket`/`RemoveRocketPacket` без проверки дистанции | medium |
| F-21 | Ракета списывается до проверки результата `placeRocket` | low |

## Что проверено и оказалось корректным (чтобы не тратить время повторно)

- **Дюп через ломание одной из 25 частей ПУ отсутствует.** `data/ata/loot_table/blocks/launcher.json` содержит условие `block_state_property { part: "center" }`, а `removeMultiblock` удаляет остальные части через `level.removeBlock(p, false)` (`block/LauncherBlock.java:350`). Порядок ванильного `ServerPlayerGameMode.destroyBlock` (`onRemove` → затем `playerDestroy` со **старым** стейтом `part=platform`) даёт ровно 1 предмет.
- **Поршни мультиблок не растаскивают:** `LauncherBlock` реализует `EntityBlock`, поэтому `BlockStateBase.hasBlockEntity()` истинно для **всех** его состояний (включая некорневые, где `newBlockEntity` возвращает `null`, `block/LauncherBlock.java:362-368`), и `PistonBaseBlock.isPushable` их отклоняет. `ShahedRadarBlock` дополнительно закрыт через `getPistonPushReaction → BLOCK`.
- **Взрыв внутри мультиблока дюп не даёт:** `Explosion.finalizeExplosion` перечитывает `getBlockState` для каждой позиции из `toBlow`, а первый же обработанный кусок каскадно обращает всю структуру в воздух — последующие позиции читаются уже как `AIR` и дропов не порождают.
- **`getUpdateTag` во всех четырёх BE делегирует в `saveAdditional`** (`IskanderRocketBlockEntity:326`, `LauncherBlockEntity:212`, `LoadingHatchBlockEntity:127`, `UnifiedTerminalBlockEntity:121`) — расхождения клиент/сервер по составу полей нет.
- **`ItemStack` в люке сериализуется правильно для 1.21:** `rocketStack.save(registries)` / `ItemStack.parseOptional(registries, ...)` (`block/entity/LoadingHatchBlockEntity.java:101,117`).
- **Формат рецептов 1.21 корректен** во всех 19 JSON: `result` использует `{"id": ..., "count": ...}`, ключи ингредиентов — `{"item": ...}`. Циклов крафта нет (ни один рецепт не принимает на вход собственный результат). Каталог `data/ata/recipe/` (единственное число) и `data/ata/loot_table/` — верные для 1.21.
- **`ata:iskander_rocket` корректно исключён из лута** двойным `.noLootTable()` (`ModBlocks.java:31` и `IskanderRocketBlock.java:36`) и зарегистрирован без `BlockItem` через `registerBlockNoItem`.
