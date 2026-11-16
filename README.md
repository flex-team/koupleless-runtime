# Spring Boot 4 / JDK 21 지원 포크

이 브랜치(`flex/boot4`)는 Koupleless runtime `runtime-2.1.x`(`d9a3fa3b09`)를 Spring Boot 4.0 / Tomcat 11 / JDK 21에서 동작하도록 수정한 포크다. 버전은 `2.1.12-flex.boot4.4`이고, SOFAArk 포크 `3.2.1-flex.boot4.1`(같은 owner의 `sofa-ark` 저장소 `flex/boot4` 브랜치)에 의존한다.

## 빌드

SOFAArk 포크를 먼저 같은 로컬 maven 저장소에 install한다.

```
(cd <sofa-ark 포크> && SKIP_TESTS=1 ./build.sh)
./build.sh                       # install, 테스트 포함
SKIP_TESTS=1 ./build.sh
MAVEN_REPO=<로컬 maven 디렉터리> ./build.sh
```

빌드 JDK는 `.sdkmanrc`(17)이고 CI(`.github/workflows/boot4_ci.yml`)는 SOFAArk 포크를 install한 뒤 JDK 21로 Spring Boot 4.0.4·4.1.0 매트릭스를 돌린다.

## upstream 대비 변경

| 변경 | 내용 |
|---|---|
| Spring Boot 4 패키지 이동 | health indicator·auto-configuration·gateway 등에서 Boot 4로 옮겨진 패키지를 쓴다 |
| `Thread.stop` 대체 | JDK 20+에서 `Thread.stop`이 `UnsupportedOperationException`을 던지므로 `ForceStopThreadsOnUninstallEventHandler`가 interrupt로 대신한다 |
| 빌드 설정 | 포크 버전, SOFAArk 포크 버전, `.sdkmanrc`(JDK 17), surefire `loggingRoot`, 0.0.1-SNAPSHOT 고정 모듈 `koupleless-ext-module-auto-convertor`의 deploy 제외, `build.sh`, CI workflow |

### 추가 설정

| 키 | 기본값 | 동작 |
|---|---|---|
| `koupleless.arklet.http.enable` | `true` | `true`가 아니면 arklet HTTP tunnel(기본 1238)을 바인딩·실행하지 않는다. 다른 tunnel(custom 등)은 그대로다. `EnvironmentUtils`로 읽는다(Ark 내부 Properties → System property) |

### 추가 확장점 `StaticBizArgsProvider` (arklet-core `com.alipay.sofa.koupleless.arklet.core.ops`)

정적 설치(`com.alipay.sofa.ark.static.biz.dir`)로 올리는 biz마다 프로그램 인자를 준다.

```java
String[] getArgs(String bizName, String bizVersion, String bizAbsolutePath);
```

- 발견: base `ApplicationContext`의 빈. `StaticBatchInstallEventListener`가 base `ApplicationReadyEvent`에 `getBeanProvider(StaticBizArgsProvider.class).getIfAvailable()`로 찾는다. 빈이 둘 이상이면 `NoUniqueBeanDefinitionException`으로 base 기동이 실패한다.
- 전달: `BatchInstallRequest.bizArgsProvider` → `BatchInstallBizInDirAbsolutePathStrategy`가 biz jar마다 Ark-Biz-Name·Ark-Biz-Version·절대 경로로 호출해 `InstallRequest.args`에 담는다 → `InstallOnlyStrategy` → `ArkClient.installOperation(operation, args, envs)` → biz main.
- 미등록이거나 `null`을 반환하면 args가 `null`이다(upstream 동작).
- provider가 예외를 던지면 어떤 biz도 설치하지 않고 그 예외로 batch install이 실패해 base 기동이 실패한다.
- 탐색(하위 디렉터리 포함, Ark-Biz-Name jar)·priority 그룹 순서·그룹 내 병렬·실패 규칙(`batch deploy failed!`)은 바뀌지 않는다. arklet 운영 API의 batch install(`BatchInstallBizInRequestStrategy`)은 이 확장점을 쓰지 않는다.

### 동작 변경: base 공유 키가 module의 config data 처리에 보인다 (koupleless-base-plugin)

base 설정 `ark.common.env.share.keys`에 적은 키(공유 키)의 base 값을 module이 `spring.config.import` 등 config data 처리 중에도 해석한다. 예: module 설정 `spring.config.import=file:/conf/probe-${env}.yml`의 `${env}`가 base 공유 값으로 풀린다.

- upstream: `ServerlessEnvironmentPostProcessor`(order `HIGHEST_PRECEDENCE + 11`)가 `ConfigDataEnvironmentPostProcessor`(`+10`) 뒤에 공유 source(`MasterBiz-Config resource`)를 붙인다. config data는 처리 시작 시점의 property source만으로 import 위치의 placeholder를 해석하므로 공유 키가 풀리지 않는다. 필수 import면 `ConfigDataResourceNotFoundException`으로 module 기동이 실패하고, optional import면 조용히 빠진다.
- 변경: `MasterBizPropertySourceEnvironmentPostProcessor`(order `ConfigDataEnvironmentPostProcessor.ORDER - 1`, module에서만 동작)가 config data 처리 전에 공유 source를 module Environment 맨 뒤에 붙인다. config data 처리 후 `ServerlessEnvironmentPostProcessor`가 공유 source를 떼어 다시 맨 뒤(`defaultProperties`보다 뒤)에 붙인다. 최종 순서·source 이름·공유 키 목록·base Environment를 읽는 방식은 upstream과 같다.
- import 위치 placeholder의 해석 순서: config data는 처리 시작 시점의 Environment(첫 source인 `configurationProperties`가 모든 source를 순서대로 조회)를 import된 파일보다 먼저 본다. 그래서 명령행·System property·환경변수 값은 공유 값보다 이기지만, 같은 키를 module 설정 파일·jar에 둔 값은 import 해석에서 공유 값에 진다. Spring Boot `defaultProperties`와 같은 동작이다. 최종 Environment에서는 module 설정 파일·jar 값이 이긴다.
- 공유 키는 config data의 다른 처리(`spring.profiles.active`, `spring.config.activate.on-profile` 등)에도 보인다.

## 테스트 현황

`./build.sh`(JDK 17 Corretto 17.0.4.1, Maven 3.9.3, Spring Boot 4.0.4) 결과: 9개 모듈, 테스트 226, 실패 0, 오류 0, skip 0.

upstream 대비 테스트 수정

| 테스트 | 수정 | 사유 |
|---|---|---|
| `MockBizManagerService`(arklet-core, arklet-tunnel-mqtt) | `getBizLock` 구현 | sofa-ark 3.2.x 인터페이스 |
| `SkipInitializerListenerTest` | 기대 감소 수 4 → 3 | Boot 4에서 `DelegatingApplicationContextInitializer` 제거 |
| `StaticBatchInstallEventListenerTest` | master biz 이벤트에 base `ApplicationContext`(`GenericApplicationContext`)를 넣는다 | 리스너가 context에서 `StaticBizArgsProvider` 빈을 찾는다 |

추가 테스트: `ApiClientTunnelTest`, `BatchInstallBizInDirAbsolutePathStrategyTest`, `MasterBizSharedKeysConfigDataTest`.

검증하지 않은 경로: biz 동적 설치·제거(arklet 운영 API) 실기동, fat-jar(비 extracted) 기동 모드(`Master biz environment is null`로 실패한 이력, 미해결), CI 매트릭스의 Spring Boot 4.1.x 로컬 실행.

---

<div align="center">

English | [简体中文](./README-zh_CN.md)

</div>

[![Coverage Status](https://codecov.io/gh/koupleless/runtime/branch/main/graph/badge.svg)](https://codecov.io/gh/koupleless/koupleless/branch/main/graph/badge.svg)
![license](https://img.shields.io/badge/license-Apache--2.0-green.svg)
![Maven Central](https://img.shields.io/maven-central/v/com.alipay.sofa.koupleless/koupleless-runtime)


### Koupleless runtime, main responsibilities

1. Arklet: Module operation and maintenance pipeline, module health check, module metric collection
2. Multi-application runtime environment
3. Multi-application governance dynamic adapter
4. Multi-application testing framework
5. Module scaffold
6. Module and base communication annotations and utility classes"
