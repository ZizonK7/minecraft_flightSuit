# FlightSuit - Claude 작업 메모리

이 파일은 Claude가 세션을 넘어 이어서 작업하기 위한 메모입니다. 기획과 결정의 원본은 `DESIGN.md`(특히 4장 요소, 5장 로드맵·진행 현황), 사용 설명은 `README.md`입니다. 여기에는 **작업 방식과 코드 지도, 아직 확인 안 된 것**만 둡니다.

## 작업 방식 (사용자 요청)

- 사용자와는 한국어로 이야기한다. 기획서·README도 한국어. 코드 주석은 영어(기존 스타일).
- 단계(마일스톤)마다 **커밋 → 푸시 → README → DESIGN.md 진행 현황 → 이 파일** 갱신.
- 의도가 갈릴 큰 결정은 사용자에게 묻고, 작은 세부는 정한 뒤 DESIGN.md에 "Claude 판단, 확인 필요"로 표시.
- 성능·청크 때문에 원래 의도를 깎아야 하면 조용히 줄이지 말고 먼저 말한다 (사용자와 약속).
- 전투 규모 원칙: 한곳에 대군을 모으지 않고 **웨이브·증원·장수의 존재감**으로 (사용자 결정).

## 환경 제약

- 사용자 PC(로컬 세션)에서는 `./gradlew build`와 `./gradlew runServer`(서버 부팅 확인, `run/eula.txt`는 사용자가 동의함)가 된다. git은 `-c safe.directory=<repo>`가 필요 (저장소 소유자가 다른 Windows 계정).
- 클라우드 세션에서는 `maven.minecraftforge.net`, Mojang, kosmx 메이븐이 네트워크 정책에 막혀 **Gradle 빌드가 안 된다**. 그래서:
  - 기존 코드에 이미 쓰인 API 패턴을 최대한 따른다.
  - 문법은 JDK 파서로만 확인한다 (스크래치패드의 `ParseOnly.java`, `javax.tools`로 `JavacTask.parse()`만 실행).
  - 빌드·인게임 테스트는 사용자가 한다. 테스트 전인 기능은 DESIGN.md에 "인게임 테스트 전"으로 표시.
- 텍스처는 코드로 그린다 (`tools/*.java`, JDK 21의 단일 파일 실행: `java tools/X.java ...`). 기존 PNG를 덮어쓰지 않도록, 새 텍스처만 그릴 때는 해당 메서드만 호출하는 하네스를 쓴다.
- 3D 모델은 `java tools/ModelGen.java . <hulk|hulkbuster|trunks|shenron|oozaru|weapons|all>`: 큐브 배치·UV·텍스처·미리보기(`docs/reference/*_preview.png`)를 만들고 모델 클래스의 `// <ModelGen:이름>` 블록을 다시 씀 (그 블록은 손으로 고치지 말 것). 무기는 `models/item/3d/` + `textures/item/3d/`, 인벤토리 아이콘은 기존 2D 그림 (없을 때만 렌더해서 만듦). 미리보기 PNG로 모양을 확인하고 나서 빌드.

## 코드 지도 (`src/main/java/com/pfkfks/flightsuit/`)

| 패키지 | 내용 |
|---|---|
| `suit/` | 슈트 아이템·에너지·무기, 착용/귀환(SuitUpManager), 스테이션 로봇 팔(StationRig), 원격 조종(RemoteLink, RemoteStorage), 이디스 경고(EdithAlert). M17: 헐크버스터 주먹 `HulkbusterArts`, 트랭크스 검·초사이어인 `SwordArts`, 팬텀 훔치기·템페스트 `PhantomArts`(+`TempestRain`, `CandyShrink`), 훔친 기술 15종 `StolenSkill`(드래곤볼 인물과 공유하는 발사 코드), 날아가는 기탄 `KiShots`, 기탄 막기 판정 `KiGuard`. 수치는 전부 `SuitTuning`. 키 액션 번호는 `SuitWeapons`(ULTIMATE=4, STOLEN_SKILL=5, STEAL=6) |
| `war/StructureJob` | 큰 건물(성채·히어로 시티) 건설: 부지 정리(기둥 단위) → 배치 목록. 설계 버전(`FortressBuilder.LAYOUT`, `HeroCityBuilder.LAYOUT`)이 저장된 것보다 새로우면 가까이 갈 때 같은 높이에 다시 지음 |
| `block/` | 슈트 스테이션, 전력 블록, 스테이션 창고(StationStorageBlock/Entity), 보안 센서(SecuritySensorBlock/Entity) |
| `energy/` | 무선 전력망(PowerGrid, 반경 8), 수치(PowerTuning) |
| `village/` | 마을 회관(VillageHallBlockEntity: 게시판·창고·경보·아침·수업·출생), 주민(ResidentEntity, 직업 ResidentJob), 건축(VillageWorks/Blueprint/Construction), 피해 장부(DamageLedger), 불 감시(FireWatch), AI(`village/ai`) |
| `war/` | 삼국지: Kingdom, General(기술, 지도자), KingdomSoldierEntity/GeneralEntity(역할 WarRole: RAID/GARRISON/ALLY), WarTargets(누가 누구와 싸우나), RaidManager(마을 습격), FortressBuilder/FortressManager(성채 위치·건설·수비대·함락·성채 전투), Diplomacy(의뢰·대화·지원군·지도자·공물), Request/Battle/Standing/FortRecord, Army(원정), WarData(SavedData 전부), WarCommands(`/village ...`, `/flightsuit raid|fort ...`), AI(`war/ai`) |
| `hero/` | 히어로 시티: HeroType(7명 + 요원), CityHeroEntity(저장 안 함, 기술), HeroCityBuilder, HeroCity(위치·건설·관계·아이언맨 작업실·의뢰·악당 웨이브·함락), HeroData(SavedData), HeroCommands(`/village hero|ironman ...`, `/flightsuit hero ...`) |
| `planet/` | 우주·행성: Planet(차원 키), PlanetData(SavedData: 착륙 지점, 플레이어별 집 발사대·스토리 진행, 행성별 월드 상태), LaunchPadBlock/Entity, SpaceshipEntity(상승·하강·착륙), SpaceTravel(`/spaceship launch|return|remote`, 우주 건너기), PlanetStory |
| `cutscene/` | M17 컷신 엔진: `Cutscene`(대본·빌더, 좌표는 앵커 기준·yaw로 회전, y=0은 그 자리 땅), `Cutscenes`(등록, 처음 물을 때 `DbzCutscenes.registerAll`), `CutsceneRunner`(서버: 배우 배치·이동·끝 상태·건너뛰기·한 번만·보는 사람 고정), `CameraEntity`. 클라: `client/CutsceneClient`(카메라를 같은 대본으로 매 프레임 계산, 점프 키 건너뛰기, 화면 흔들림), `client/CutsceneOverlay`. 패킷 `CutsceneS2CPacket`/`CutsceneSkipC2SPacket` |
| `planet/dbz/` | 드래곤볼: DbzSaga(1~4장: 장면 Site/Scene, 웨이브 + 시작 컷신 + 체력 이정표 `atHealth` 컷신, 컷신 대기열, 동행, 나메크 최후 3분, 셀 게임, 원기옥, 나메크 마을 `NAMEK_DOMES`), DbzCutscenes(컷신 대본 25개, 배역 id는 `DbzSaga.castId`), DbzAction(동작 15종, 모델 `client/DbzFighterModel`), DbzMoves(인물별 기술표), WestCity(서쪽 도시), DbzCommands(`/flightsuit dbz stage|cutscene|stop`), DbzCharacter, DbzFighterEntity(저장 안 함, 역할 NPC/ALLY/BOSS/MINION), DbzLandmarks, DbzEarth(볼거리·서쪽 도시 건설·NPC 유지·밤 재배맨. 크레이터 전투는 M17부터 DbzSaga), ScouterItem, SenzuBeanItem, DragonBalls(7개 위치·레이더·신룡·소원 `/shenron`), DragonBallBlock/Item, DragonRadarItem. 스토리 진행은 `planet/PlanetStory` (`/planet`, `/planet replay`, 단계 DbzStage는 M17부터 **이름으로 저장**(`Traveller.stageName`, 옛 숫자는 `LEGACY` 순서로 변환)이라 중간에 넣어도 됨, 선언 순서 = 진행 순서, `goal()` = 다음 목표 → HUD `StoryGoalOverlay` + 금빛 빛기둥). 인물(`DbzCharacter`)도 이름으로 저장, 같은 사람의 다른 형태는 `voice`로 대사를 공유 |
| `thanos/` | 타노스 사가: InfinityStone(+Item), ThanosForce/ThanosForceEntity(Monster, 저장 안 함, 레이드 태그 `RAID_TAG`), TitanSites, ThanosSaga(타이탄 관리·스톤 기록·레드 스컬·타임 스톤·전조), ThanosRaid(최종전) |
| `thief/` | 배트맨 일당: ThiefType, ThiefEntity(저장 안 함), ThiefManager(일정·실제/계산 밤·상자 털기·보상), ThiefData(SavedData: 다음 방문, 오늘 밤 방문, 처리 대기), 배트랭·갈고리 총·연막탄, ThiefCommands |
| `entity/` | 동료 슈트, 원격 몸, 미사일·카드, 좌석(SeatEntity: 지도자가 왕좌에 앉음, 저장 안 함) |
| `client/` | 렌더러, HUD 오버레이(이디스 경고 EdithAlertOverlay 포함), 화면. 전용 모델 슈트는 `SuitModel`(슬롯별 조각, `SuitArmorModels.CUSTOM`에 등록) + 생성 블록 클래스(`HulkbusterModel`, `TrunksSuitModel`), 헐크 `HulkModel/HulkRenderer`, 신룡 `ShenronModel/ShenronRenderer`, M17: 드래곤볼 인물 `DbzFighterModel/DbzFighterRenderer`(동작별 코드 포즈), 거대원숭이 `OozaruModel/OozaruRenderer`, 베로니카 포드, 팬텀 조준 `PhantomAimClient`(짧게 = 조준, 길게 = 훔치기, 휠 = 거리) |
| `town/` | 성채·히어로 시티·서쪽 도시·나메크 마을의 마을 사람 TownsfolkEntity(저장 안 함, 마을 번호 0~2 성채·3 히어로 시티·4 서쪽 도시·5 나메크, 역할 TownRole(`group()`), 일과 TownLife(행성 마을은 `planetTowns`), 배치 TownPlan), 거래 TownTrades(`money(town)` = 오수전/달러/제니), 부탁 TownRequests |
| `guide/` | 안내서 GuideBookItem(처음 접속 때 지급, 화면은 `client/GuideBook`), ItemTips(`tip.flightsuit.*` → Shift 툴팁 `client/ItemTipsClient` + JEI 정보), JeiFlightSuit(@JeiPlugin, JEI는 선택 의존성) |
| `fx/` | 기 기술 그림 KiFx (M17 테스트 뒤): 서버가 광선·기 모으기·기탄·폭발·빛기둥·오라·태양권을 `KiFxS2CPacket`으로 보내고 `client/KiFxClient`가 그림 (도형은 `client/KiDraw`, 가산 합성 렌더 타입 하나) |
| `network/` | 패킷 (ModNetwork에 등록 순서대로) |

자주 쓰는 연결점:
- 마을 찾기: `Villages.containing(level, pos)`, `Villages.hallAt(level, pos)`. 피해 기록: `Villages.recordDamage`.
- 회관 → 습격 시스템: 회관이 100틱마다 `RaidManager.noteVillage(hall)`로 자신을 알림 (여기서 포로 도착, `Diplomacy.onVillageLoaded`, `HeroCity.onVillageLoaded`로 파견 병사 귀환, `ThiefManager.onVillageLoaded`로 계산된 도둑의 밤 처리). 회관이 부서지면 `RaidManager.forgetVillage`.
- 습격병·장수는 `RaidMember`. 무릎 꿇은 포로·아군 장수는 `RaidMember.isNoThreat(entity)`로 각종 표적 규칙에서 빠진다 (경비병, 동료 슈트, 헬멧 HUD, 망루, 주민 도망).
- 큰 슈트(헐크버스터): `SuitType.size()` → `SuitSize`가 풀세트 착용자(플레이어·동료)의 히트박스를 키우고, 그림은 `SuitSize.drawn()`으로 전체를 키움. 새 큰 슈트를 만들면 모델 쪽 `SIZE`와 `SuitType` 크기를 맞출 것.
- 슈트 배터리 용량은 항상 `SuitEnergy.capacity(stack)` (아이언맨 업그레이드 포함). `SuitArmorItem.getEnergyCapacity()`를 직접 쓰지 말 것.
- 1.20.1에서 goalSelector는 엔티티마다 2틱에 한 번 돈다. `canUse`에 `tickCount % N` 같은 짝수 의존 조건을 쓰지 말 것 (`getRandom().nextInt(reducedTickDelay(N))` 사용). `customServerAiStep`은 매 틱이라 괜찮음.
- 차원: 행성은 데이터팩 차원 (`data/flightsuit/dimension/*.json`). `RemoteLink.start(player, suitLevel, ...)`로 다른 차원의 슈트에 접속할 수 있음 (세션 등록은 순간이동 뒤에 - 세션이 있으면 차원 이동을 막으므로).
- `/flightsuit` 루트를 새로 등록할 때는 루트에도 `.requires(op)`를 붙일 것 (Brigadier는 먼저 등록된 루트의 조건을 유지 - 하나라도 빠지면 등록 순서에 따라 전부 열림).
- 주인에게 알림: 채팅 + `EdithAlert.send(...)` (안경/슈트 헬멧이 있으면 HUD 경고창).
- 새 아이템을 만들면 `tip.flightsuit.<id>` 번역(줄은 `
`, 키 인자는 GuideBook.keys() 순서 %1$s=G … %10$s=웅크리기)을 ko/en에 넣을 것 → Shift 툴팁·JEI 정보 페이지가 저절로 생김. 안내서 쪽수는 `GuideBook.PAGES`.
- **기·에너지 기술의 모습은 파티클로 그리지 말고 `fx/KiFx`로** (사용자 피드백 "다 파티클만 날리고"). 광선은 `SuitSkills.beam`(끝 폭발까지 그려 줌), 날아가는 기탄은 `KiShots.fire`, 기 모으기 `KiFx.charge`(광선이 나가면 저절로 꺼짐), 오라는 드래곤볼 인물이면 `setAura`(동기화), 그 밖은 `KiFx.aura`. 확인은 `/flightsuit dbz fx <이름>`.
- 컷신 카메라(`client/CutsceneClient`)는 대본 위치에서 막히면 앞으로 당기거나 대상 주위로 돌려 피함. 대본의 카메라 위치는 대략이면 됨. 컷신 동안 플레이어와 시야를 막는 생물은 그리지 않음.
- 파티클: `registry/ModParticles` (카드 소용돌이 `card_swirl`은 count 0으로 보내 속도 칸에 궤도 값을 실음).
- JEI: `build.gradle`의 BlameJared 저장소 필터에 `mezz.jei`와 `net.mezzdev.config` 둘 다 있어야 함 (버전 `gradle.properties`의 `jei_version`).
- 테스트 중 치트(`/time set` 등)로 막힌 상황은 코드로 막지 말고 월드를 넘기는 명령을 알려 줄 것 (사용자 결정).

## 진행 상태 (2026-10-10)

- **M17과 그 테스트 피드백(기 기술 그림 `fx/KiFx`, 컷신 카메라 가림 방지, 번역 누락): 사용자 인게임 테스트 통과, main에 반영** (결과·Claude 판단은 DESIGN.md 5장 "M17").
- **다음 작업: 재배맨 모델** (사용자: "너무 못생겼어"). 남은 확인은 `docs/TEST_PLAN.md`.
- 다음 후보: Mark 1·2·4·50 궁극기(섬멸 모드), 성채끼리의 전쟁, 행성 지형 개편, 인물별 입체 부품 모델링(꼬리·더듬이·프리저 체형).
- M1~M16과 2026-10-10 1~4차 피드백 반영분: 사용자 인게임 테스트 통과.

## 테스트할 때 쓰는 명령 (치트 필요)

`/flightsuit durability|energy <0-100>`, `/flightsuit village wanderer|birth|grow`, `/flightsuit raid start [wei|shu|wu]`, `/flightsuit raid stop`, `/flightsuit raid general <이름>`, `/flightsuit fort tp|trust|done|request <나라> ...`, `/flightsuit hero tp|trust|request`, `/flightsuit thief now|spawn|when`, `/flightsuit dragonballs give|reset`, `/flightsuit dbz stage <단계>|cutscene <id>|fx <이펙트>|stop`, `/flightsuit thanos now|stones`. 권한 없이: `/planet`, `/planet replay [id]`, `/spaceship ...`, `/shenron <소원>`, `/village recruit|release <습격 번호>` (항복 처리, 채팅 버튼이 실행).
