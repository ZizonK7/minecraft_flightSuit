# FlightSuit

라이언·춘식이 같은 캐릭터를 아이언맨 슈트로 디자인해서, 슈트를 입고 날아다니며 마을을 지키고 키우는 마인크래프트 Forge 모드입니다.

- 기획과 의도, 로드맵은 [DESIGN.md](DESIGN.md)에 정리되어 있습니다.
- 개인 프로젝트입니다.

## 환경

| 항목 | 버전 |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.4.10 |
| Java | 17 |
| PlayerAnimator | 1.0.2-rc1+1.20 (몸 동작 애니메이션, 필수 의존 모드) |

## 실행

```bash
./gradlew runClient     # 개발용 클라이언트 실행
./gradlew build         # build/libs/ 에 모드 jar 생성
```

실제 마인크래프트에서 쓰려면 이 모드 jar와 함께 [PlayerAnimator](https://maven.kosmx.dev/dev/kosmx/player-anim/player-animation-lib-forge/) Forge 1.20 버전을 `mods` 폴더에 넣어야 합니다.

## 현재 기능 (M1: 라이언 Mark 1)

크리에이티브 탭 "플라이트 슈트"에서 **슈트가 든 캡슐**을 꺼내면 바로 써 볼 수 있습니다.

### 착용

- **슈트 캡슐 우클릭** 또는 **`G`**: 지상 착용 연출. 팔을 벌리면 부츠 → 레깅스 → 흉갑 → 헬멧 순서로 파츠가 날아와 장착됩니다. 연출 동안 3인칭 정면 카메라로 바뀌고, 이동이 막히며 무적입니다.
- **착용 중 `G`**: 슈트를 캡슐에 다시 수납합니다.

### 파츠별 효과

파츠 하나만 입어도 그 파츠의 기능을 쓸 수 있습니다.

| 파츠 | 효과 |
|---|---|
| 헬멧 | 야간 투시 |
| 흉갑 | 빈손 우클릭으로 리펄서 발사. 슈트 전체의 배터리(아크 리액터) |
| 레깅스 | 이동 속도 +20%, 1칸 단차 오르기, 낙하 피해 절반 |
| 부츠 | 공중에서 점프를 한 번 더 누르면 추진, 추진이 끝나면 활공 |
| 풀세트 | 스페이스 2번으로 비행(호버링). 달리기 + 앞으로 = 고속 비행. 낙하 피해 없음 |

### 에너지

- 흉갑을 입고 있으면 흉갑 배터리(20,000 FE)를 모든 파츠가 함께 씁니다. 흉갑이 없으면 각 파츠의 작은 내장 배터리만 씁니다.
- **에너지 셀**을 우클릭하면 착용한 슈트가 충전됩니다. 스테이션이 생기기 전까지의 임시 충전 수단입니다.
- 크리에이티브 모드에서는 에너지가 줄지 않습니다.

### 제작법

| 아이템 | 재료 |
|---|---|
| 아크 리액터 | 구리, 금, 레드스톤 블록, 다이아몬드 |
| Mark 1 파츠 4종 | 철, 금 + (헬멧: 유리판 / 흉갑: 아크 리액터 / 레깅스: 레드스톤 / 부츠: 블레이즈 가루) |
| 슈트 캡슐 | 철, 레드스톤, 금 |
| 에너지 셀 (2개) | 구리, 레드스톤 블록 |

## 구조

```
src/main/java/com/pfkfks/flightsuit/
  suit/      슈트 아이템, 에너지, 착용 연출(SuitUpManager), 리펄서, 서버 틱 효과
  entity/    착용 연출 때 날아오는 파츠 엔티티
  client/    아머 모델, 몸 동작 애니메이션(SuitAnimator), 비행 조작, 카메라, HUD
  network/   패킷
  registry/  아이템·엔티티 등록
src/main/resources/assets/flightsuit/player_animation/   몸 동작 애니메이션 (PlayerAnimator JSON)
tools/SkinSplitter.java   64x64 스킨 이미지를 파츠별 아머 텍스처와 아이콘으로 분리
docs/reference/           레퍼런스 이미지와 슈트 스킨 원본
```

슈트 외형은 아직 모델링 전이라, 64×64 플레이어 스킨 형식 이미지를 파츠별로 잘라 쓰고 있습니다. 스킨을 바꾸려면 아래처럼 다시 생성하면 됩니다.

```bash
java tools/SkinSplitter.java docs/reference/ryan_mk1_skin_source.png src/main/resources/assets/flightsuit/textures ryan_mk1
```

## 크레딧

- [PlayerAnimator](https://github.com/KosmX/minecraftPlayerAnimator) by KosmX (MIT)
