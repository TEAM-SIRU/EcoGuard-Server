# 아키택쳐 구조에 관하여
일반적인 Spring MVC를 따르는 모놀리식 Spring Boot 애플리케이션은
```
src/main/kotlin/team/siru/ecoguard/domain
src/main/kotlin/team/siru/ecoguard/global
```
두가지 패키지로 구성하고 `domain` 패키지에는 도메인 모델과 관련된 클래스들을, `global` 패키지에는 공통적으로 사용되는 유틸리티 클래스나 설정 파일들을 배치하는 것이 일반적입니다.
그러나 현재 해당 프로젝트는 `src/main/kotlin/team/siru/ecoguard` 바로 아래에 도메인에 따라 패키지를 바로 배치하고 있으며 공용 유틸과 도메인 모델이 섞여 있는 구조를 가지고 있습니다.
Spring MVC의 관례를 무조건 따를 필요는 없지만 현재 구조는 경계가 모호하고 유지보수에 어려움을 줍니다.
참고할만한 아키택쳐로 [DataGSM OAuth Authorization 애플리케이션](https://github.com/themoment-team/datagsm-server/tree/master/datagsm-oauth-authorization/src/main/kotlin/team/themoment/datagsm/oauth/authorization) 이나 [cowork Project 모듈](https://github.com/team-cowork/cowork-server/tree/main/cowork-project/src/main/kotlin/com/cowork/project) 을 참고해보셔도 좋을 것 같습니다.
