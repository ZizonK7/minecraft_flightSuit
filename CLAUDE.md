# FlightSuit - Claude 작업 메모리

이 파일은 Claude가 세션을 넘어 이어서 작업하기 위한 메모입니다. 기획과 결정의 원본은 `DESIGN.md`(특히 4장 요소, 5장 로드맵·진행 현황), 사용 설명은 `README.md`입니다. 여기에는 **작업 방식과 코드 지도, 아직 확인 안 된 것**만 둡니다.

## 작업 방식 (사용자 요청)

- 사용자와는 한국어로 이야기한다. 기획서·README도 한국어. 코드 주석은 영어(기존 스타일).
- 단계(마일스톤)마다 **커밋 → 푸시 → README → DESIGN.md 진행 현황 → 이 파일** 갱신.
- 의도가 갈릴 큰 결정은 사용자에게 묻고, 작은 세부는 정한 뒤 DESIGN.md에 "Claude 판단, 확인 필요"로 표시.
- 성능·청크 때문에 원래 의도를 깎아야 하면 조용히 줄이지 말고 먼저 말한다 (사용자와 약속).
- 전투 규모 원칙: 한곳에 대군을 모으지 않고 **웨이브·증원·장수의 존재감**으로 (사용자 결정).

## 환경 제약

- 클라우드 세션에서는 `maven.minecraftforge.net`, Mojang, kosmx 메이븐이 네트워크 정책에 막혀 **Gradle 빌드가 안 된다**. 그래서:
  - 기존 코드에 이미 쓰인 API 패턴을 최대한 따른다.
  - 문법은 JDK 파서로만 확인한다 (스크래치패드의 `ParseOnly.java`, `javax.tools`로 `JavacTask.parse()`만 실행).
  - 빌드·인게임 테스트는 사용자가 한다. 테스트 전인 기능은 DESIGN.md에 "인게임 테스트 전"으로 표시.
- 텍스처는 코드로 그린다 (`tools/*.java`, JDK 21의 단일 파일 실행: `java tools/X.java ...`). 기존 PNG를 덮어쓰지 않도록, 새 텍스처만 그릴 때는 해당 메서드만 호출하는 하네스를 쓴다.

## 코드 지도 (`src/main/java/com/pfkfks/flightsuit/`)

| 패키지 | 내용 |
|---|---|
| `suit/` | 슈트 아이템·에너지·무기, 착용/귀환(SuitUpManager), 스테이션 로봇 팔(StationRig), 원격 조종(RemoteLink, RemoteStorage), 이디스 경고(EdithAlert) |
| `block/` | 슈트 스테이션, 전력 블록, 스테이션 창고(StationStorageBlock/Entity), 보안 센서(SecuritySensorBlock/Entity) |
| `energy/` | 무선 전력망(PowerGrid, 반경 8), 수치(PowerTuning) |
| `village/` | 마을 회관(VillageHallBlockEntity: 게시판·창고·경보·아침·수업·출생), 주민(ResidentEntity, 직업 ResidentJob), 건축(VillageWorks/Blueprint/Construction), 피해 장부(DamageLedger), 불 감시(FireWatch), AI(`village/ai`) |
| `war/` | 삼국지: Kingdom, General(기술, 지도자), KingdomSoldierEntity/GeneralEntity(역할 WarRole: RAID/GARRISON/ALLY), WarTargets(누가 누구와 싸우나), RaidManager(마을 습격), FortressBuilder/FortressManager(성채 위치·건설·수비대·함락·성채 전투), Diplomacy(의뢰·대화·지원군·지도자·공물), Request/Battle/Standing/FortRecord, Army(원정), WarData(SavedData 전부), WarCommands(`/village ...`, `/flightsuit raid|fort ...`), AI(`war/ai`) |
| `hero/` | 히어로 시티: HeroType(7명 + 요원), CityHeroEntity(저장 안 함, 기술), HeroCityBuilder, HeroCity(위치·건설·관계·아이언맨 작업실·의뢰·악당 웨이브·함락), HeroData(SavedData), HeroCommands(`/village hero|ironman ...`, `/flightsuit hero ...`) |
| `planet/` | 우주·행성: Planet(차원 키), PlanetData(SavedData: 착륙 지점, 플레이어별 집 발사대·스토리 진행, 행성별 월드 상태), LaunchPadBlock/Entity, SpaceshipEntity(상승·하강·착륙), SpaceTravel(`/spaceship launch|return|remote`, 우주 건너기), PlanetStory |
| `planet/dbz/` | 드래곤볼 지구: DbzCharacter, DbzFighterEntity(저장 안 함, 역할 NPC/ALLY/BOSS/MINION), DbzLandmarks, DbzEarth(볼거리 건설·NPC 유지·크레이터 전투·밤 재배맨), ScouterItem, SenzuBeanItem, DragonBalls(7개 위치·레이더·신룡·소원 `/shenron`), DragonBallBlock/Item, DragonRadarItem. 스토리 진행은 `planet/PlanetStory` (`/planet`) |
| `thanos/` | 타노스 사가: InfinityStone(+Item), ThanosForce/ThanosForceEntity(Monster, 저장 안 함, 레이드 태그 `RAID_TAG`), TitanSites, ThanosSaga(타이탄 관리·스톤 기록·레드 스컬·타임 스톤·전조), ThanosRaid(최종전) |
| `thief/` | 배트맨 일당: ThiefType, ThiefEntity(저장 안 함), ThiefManager(일정·실제/계산 밤·상자 털기·보상), ThiefData(SavedData: 다음 방문, 오늘 밤 방문, 처리 대기), 배트랭·갈고리 총·연막탄, ThiefCommands |
| `entity/` | 동료 슈트, 원격 몸, 미사일·카드 |
| `client/` | 렌더러, HUD 오버레이(이디스 경고 EdithAlertOverlay 포함), 화면 |
| `network/` | 패킷 (ModNetwork에 등록 순서대로) |

자주 쓰는 연결점:
- 마을 찾기: `Villages.containing(level, pos)`, `Villages.hallAt(level, pos)`. 피해 기록: `Villages.recordDamage`.
- 회관 → 습격 시스템: 회관이 100틱마다 `RaidManager.noteVillage(hall)`로 자신을 알림 (여기서 포로 도착, `Diplomacy.onVillageLoaded`, `HeroCity.onVillageLoaded`로 파견 병사 귀환, `ThiefManager.onVillageLoaded`로 계산된 도둑의 밤 처리). 회관이 부서지면 `RaidManager.forgetVillage`.
- 습격병·장수는 `RaidMember`. 무릎 꿇은 포로·아군 장수는 `RaidMember.isNoThreat(entity)`로 각종 표적 규칙에서 빠진다 (경비병, 동료 슈트, 헬멧 HUD, 망루, 주민 도망).
- 슈트 배터리 용량은 항상 `SuitEnergy.capacity(stack)` (아이언맨 업그레이드 포함). `SuitArmorItem.getEnergyCapacity()`를 직접 쓰지 말 것.
- 1.20.1에서 goalSelector는 엔티티마다 2틱에 한 번 돈다. `canUse`에 `tickCount % N` 같은 짝수 의존 조건을 쓰지 말 것 (`getRandom().nextInt(reducedTickDelay(N))` 사용). `customServerAiStep`은 매 틱이라 괜찮음.
- 차원: 행성은 데이터팩 차원 (`data/flightsuit/dimension/*.json`). `RemoteLink.start(player, suitLevel, ...)`로 다른 차원의 슈트에 접속할 수 있음 (세션 등록은 순간이동 뒤에 - 세션이 있으면 차원 이동을 막으므로).
- `/flightsuit` 루트를 새로 등록할 때는 루트에도 `.requires(op)`를 붙일 것 (Brigadier는 먼저 등록된 루트의 조건을 유지 - 하나라도 빠지면 등록 순서에 따라 전부 열림).
- 주인에게 알림: 채팅 + `EdithAlert.send(...)` (안경/슈트 헬멧이 있으면 HUD 경고창).

## 진행 상태 (2026-10-10)

- M1~M9: 사용자 인게임 테스트 통과 (M9 마지막 수정 일부 재확인 필요, DESIGN.md 참고).
- M8 추가분 (원격 블록 파괴 + 스테이션 창고), M10 (삼국지 습격), M11 (가족·교육), M12 (성채·외교·원정), M13 (히어로 시티), M14 (배트맨 일당·보안 센서), M15 (우주선·드래곤볼 지구·사이어인 편·드래곤볼), M16 (타이탄·인피니티 스톤·타노스 최종전·Mark 50): **구현, 인게임 테스트 전**.
- 다음: 로드맵 끝. 사용자 테스트 피드백 반영이 우선.

## 테스트할 때 쓰는 명령 (치트 필요)

`/flightsuit durability|energy <0-100>`, `/flightsuit village wanderer|birth|grow`, `/flightsuit raid start [wei|shu|wu]`, `/flightsuit raid stop`, `/flightsuit raid general <이름>`, `/flightsuit fort tp|trust|done|request <나라> ...`, `/flightsuit hero tp|trust|request`, `/flightsuit thief now|spawn|when`, `/flightsuit dragonballs give|reset`, `/flightsuit thanos now|stones`. 권한 없이: `/planet`, `/spaceship ...`, `/shenron <소원>`. 권한 없이: `/village recruit|release <습격 번호>` (항복 처리, 채팅 버튼이 실행).
