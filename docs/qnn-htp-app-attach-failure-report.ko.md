# QNN HTP 앱 Attach 실패 보고서

날짜: 2026-04-01

## 1. 목표

본 프로젝트의 목표는 Android 앱 개발자가 `createSession()` 같은 단순한 API만 호출하면, 내부적으로 Qualcomm QNN HTP delegate attach를 자동으로 시도하는 앱 레벨 오픈소스 라이브러리를 만드는 것이었다.

즉, 목표는 다음과 같았다.

- 앱 개발자는 단순한 런타임 API만 사용한다
- 런타임이 내부적으로 QNN HTP delegate attach를 시도한다
- 일반 Android 앱 프로세스 안에서 직접 NPU 가속이 동작한다

장기적으로는 `adb shell` 기반 일회성 실행이 아니라, 재사용 가능한 오픈소스 런타임 라이브러리 형태로 NPU 가속을 제공하는 것이 목표였다.

## 2. 목표 달성을 위해 어디까지 시도했는가

이 목표를 이루기 위해 앱 내부 QNN attach 경로를 거의 전체에 가깝게 구현했다.

- `backend-qnn`에서 QNN availability probe 구현
- `runtime`에서 JNI/native bridge 연결
- native 코드에서:
  - `libQnnTFLiteDelegate.so` 로드
  - `libQnnHtp.so` 로드
  - `tflite_plugin_create_delegate` / destroy symbol 조회
  - delegate handle 생성
- Kotlin/TFLite에서 custom `Delegate` wrapper를 사용해 실제 Interpreter attach 시도

또한 다음과 같은 패키징 및 로딩 전략을 모두 실험했다.

- assets 기반 `.so` 복사 방식
- `jniLibs/arm64-v8a` 기반 packaged native library 방식
- `ADSP_LIBRARY_PATH`, `LD_LIBRARY_PATH` 설정 조정
- `nativeLibraryDir` 실파일 전제가 아닌 APK 포함 여부 기반 packaged native library probe

이와 함께 DSP/HAL/FastRPC 환경을 앱 프로세스 안에서 재현하기 위해 추가 dependency chain도 단계적으로 실험했다.

- `libcdsprpc.so`
- `libadsprpc.so`
- `libsdsprpc.so`
- `vendor.qti.hardware.dsp@1.0.so`
- `libvmmem.so`
- `libbase.so`
- `libhidlbase.so`
- `libhardware.so`
- `libutils.so`
- `libcutils.so`
- `libdmabufheap.so`
- `libvndksupport.so`
- `libdl_android.so`
- `ld-android.so`
- `libc++.so`

이 라이브러리들은 preload 로직에 통합되어, 앱 프로세스 안에서 delegate attach 전에 선행 로드되도록 구성했다.

## 3. 어디에서 실패했는가

구현은 최종적으로 다음 단계까지는 성공했다.

- QNN availability probe 성공
- QNN 관련 native library 로드 성공
- delegate symbol resolution 성공
- delegate handle 생성 성공
- preload dependency chain 성공
- TFLite delegate apply 단계 진입 성공

그러나 실제 attach는 DSP transport 및 session 생성 단계에서 최종적으로 실패했다.

결정적인 로그는 다음과 같았다.

- `open_device_node ... Permission denied`
- `unable to acquire dspservice instance`
- `open_hal_session: failed`
- `createUnsignedPD ... not supported by HTP`
- `Transport layer setup failed: 14001`

즉 이 시점에서 실패 원인은 더 이상 단순한 `.so` 탐색 문제가 아니었고, 최종 DSP/HAL session open 단계에서 막히는 것으로 드러났다.

## 4. 왜 목표를 이루지 못했는가

원래 목표는 앱이 QNN 관련 라이브러리를 올바르게 패키징하고 로드할 수만 있으면, 일반 Android 앱 프로세스에서도 QNN HTP attach가 최종적으로 가능할 것이라는 전제를 두고 있었다.

하지만 이 전제는 대상 디바이스에서 성립하지 않았다.

가장 중요한 관찰은 다음과 같다.

- 같은 Galaxy XR 디바이스에서 `adb shell` 기반 QNN HTP 실행은 성공했다
- 그러나 같은 디바이스에서 일반 앱 프로세스 기반 attach는 최종적으로 실패했다

이는 문제의 본질이 delegate 코드나 library loading 자체가 아니라,

- `shell` 실행 컨텍스트와
- 일반 Android 앱 실행 컨텍스트

사이의 권한 및 실행 환경 차이임을 강하게 시사한다.

실제로 dependency chain을 거의 모두 만족시킨 뒤에도 앱 프로세스는 필요한 DSP/HAL 세션을 열 수 없었다.

결과적으로 프로젝트는 원래 의도했던 형태의 목표,

- 일반 Android 앱 프로세스 내부에서 직접 QNN HTP attach를 완성하는 것

을 달성하지 못했다.

## 5. 실패를 어떻게 극복하려 했는가

실패를 바로 수용하지 않고, 병목이 어디까지 해소 가능한지 단계적으로 검증했다.

시도한 대응은 다음과 같다.

- `nativeLibraryDir` 실파일 검사 대신 APK 기반 packaged native library 판단으로 probe 수정
- assets 방식과 packaged `jniLibs` 방식 비교
- missing delegate/stub/backend dependency를 단계적으로 제거
- public vendor native library 접근을 위해 `uses-native-library` 선언 추가
- 커뮤니티 성공 사례를 참고해 HAL/FastRPC/vendor/system dependency chain 확장
- delegate 생성 전에 관련 dependency preload 수행
- LiteRT 및 기타 app-level 대안 가능성 검토
- shell-backed backend, bridge-based execution model 같은 아키텍처 대안 조사

이 과정의 결과로, 단순 패키징/로딩 문제와 최종 권한 문제를 구분할 수 있게 되었고, 모호한 “어딘가 안 됨” 상태가 아니라 실제 최종 병목을 드러낼 수 있었다.

## 6. 실패 속에서 얻은 성과와 성장

원래 목표는 달성하지 못했지만, 기술적으로 의미 있는 성과는 분명히 있었다.

### 문제의 본질을 더 정확히 분리했다

이번 시도를 통해 확인한 핵심은 다음과 같다.

- QNN HTP는 이 디바이스에서 실제로 동작한다
- `shell` 실행 컨텍스트에서는 동작한다
- 일반 앱 프로세스에서는 최종 DSP/HAL 접근 권한 때문에 attach가 완성되지 않는다

즉 “QNN 자체가 안 된다”가 아니라 “실행 컨텍스트가 다르다”는 점을 실험적으로 분리해냈다.

### 런타임 자산을 상당 부분 구축했다

다음과 같은 재사용 가능한 런타임 자산이 남았다.

- QNN availability probe
- JNI/native bridge
- packaged native library 인식 가능한 loader
- delegate 생성 경로
- dependency preload 프레임워크

비록 최종 attach에는 실패했지만, 이 자산은 direct backend든 shell-backed backend든 이후 구조에서 재활용 가능하다.

### 다음 방향이 더 명확해졌다

이제 프로젝트는 보다 정확한 문제 정의를 갖게 되었다.

- 더 많은 `.so`를 넣는 것만으로는 해결될 가능성이 낮다
- direct app-process HTP attach는 현재 디바이스에서 구조적 한계에 부딪혔다
- 다음 단계는 dependency packaging이 아니라 execution-context bridging에 초점을 맞춰야 한다


## 7. 결론

이번 시도는 “일반 Android 앱 프로세스에서 직접 QNN HTP attach를 완성한다”는 원래 목표에는 실패했다.

핵심 결론은 다음과 같다.

- 디바이스는 QNN HTP를 지원한다
- 앱은 거의 전체 stack을 준비할 수 있다
- 하지만 일반 앱 프로세스는 최종 DSP/HAL session 경계를 넘지 못한다
