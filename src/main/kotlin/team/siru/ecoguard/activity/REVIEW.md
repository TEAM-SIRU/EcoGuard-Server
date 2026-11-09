# 아키택쳐 구조에 관하여
도메인 별 패키지 내부에 dto를 제외하곤 같은 패키지 계층에 Controller, Service, Repository를 두는 구조를 채택하고 있습니다.
이는 유지보수성을 떨어뜨리며 직관적인 계층 분리를 보여주지 못합니다. 물론 현재는 클래스 수 자체가 적어 큰 문제가 없지만 그럼에도 이것은 분리하면 좋겠습니다.
```
presentation/controller
presentation/dto/request
presentation/dto/response
service
service/impl
repository
entity
dto
```
```
controller
dto/request
dto/response
dto/internal
entity
service
service/impl
repository
```
또는 entity와 repository를 persistence 패키지로 묶는 방법도 있습니다.

이러한 형태는 관례적인 것이라 강제성은 없지만 유지보수성을 위해서라도 분리하는 것이 좋습니다.