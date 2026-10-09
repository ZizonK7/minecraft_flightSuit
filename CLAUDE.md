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
| `block/` | 슈트 스테이션, 전력 블록, 스테이션 창고(StationStorageBlock/Entity) |
| `energy/` | 무선 전력망(PowerGrid, 반경 8), 수치(PowerTuning) |
| `village/` | 마을 회관(VillageHallBlockEntity: 게시판·창고·경보·아침·수업·출생), 주민(ResidentEntity, 직업 ResidentJob), 건축(VillageWorks/Blueprint/Construction), 피해 장부(DamageLedger), 불 감시(FireWatch), AI(`village/ai`) |
| `war/` | 삼국지 습격: Kingdom, General(기술), KingdomSoldierEntity, GeneralEntity, RaidManager(일정·웨이브·항복), WarData(SavedData: 마을 기록·진행 중 습격·나라별 관계), RaidState, FireArrows, WarDamage, WarCommands, AI(`war/ai`) |
| `entity/` | 동료 슈트, 원격 몸, 미사일·카드 |
| `client/` | 렌더러, HUD 오버레이(이디스 경고 EdithAlertOverlay 포함), 화면 |
| `network/` | 패킷 (ModNetwork에 등록 순서대로) |

자주 쓰는 연결점:
- 마을 찾기: `Villages.containing(level, pos)`, `Villages.hallAt(level, pos)`. 피해 기록: `Villages.recordDamage`.
- 회관 → 습격 시스템: 회관이 100틱마다 `RaidManager.noteVillage(hall)`로 자신을 알림. 회관이 부서지면 `RaidManager.forgetVillage`.
- 습격병·장수는 `RaidMember`. 무릎 꿇은 포로·아군 장수는 `RaidMember.isNoThreat(entity)`로 각종 표적 규칙에서 빠진다 (경비병, 동료 슈트, 헬멧 HUD, 망루, 주민 도망).
- 주인에게 알림: 채팅 + `EdithAlert.send(...)` (안경/슈트 헬멧이 있으면 HUD 경고창).

## 진행 상태 (2026-10-10)

- M1~M9: 사용자 인게임 테스트 통과 (M9 마지막 수정 일부 재확인 필요, DESIGN.md 참고).
- M8 추가분 (원격 블록 파괴 + 스테이션 창고), M10 (삼국지 습격), M11 (가족·교육): **구현, 인게임 테스트 전**.
- 다음: M12 적 마을·침략·외교 → M13 히어로 시티 → M14 배트맨 일당·보안 → M15 우주선·드래곤볼 행성 → M16 타노스 사가.

## 테스트할 때 쓰는 명령 (치트 필요)

`/flightsuit durability|energy <0-100>`, `/flightsuit village wanderer|birth|grow`, `/flightsuit raid start [wei|shu|wu]`, `/flightsuit raid stop`, `/flightsuit raid general <이름>`. 권한 없이: `/village recruit|release <습격 번호>` (항복 처리, 채팅 버튼이 실행).
