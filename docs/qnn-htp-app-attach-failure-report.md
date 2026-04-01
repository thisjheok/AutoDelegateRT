# QNN HTP App Attach Failure Report

Date: 2026-04-01

## 1. Goal

The goal of this project was to provide an Android app-level open source library that allows application code to call a simple API such as `createSession()` and have the runtime automatically attempt Qualcomm QNN HTP delegate attachment internally.

In other words, the target outcome was:

- app developers use a simple runtime API
- the runtime attempts QNN HTP delegate attach
- NPU acceleration works directly from a normal Android app process

The longer-term vision was to make NPU acceleration accessible as a reusable open source runtime library rather than a one-off `adb shell` workflow.

## 2. How Far the Attempt Went

To reach that goal, the implementation covered nearly the full app-side QNN attach path:

- `backend-qnn` implemented QNN availability probing
- `runtime` bridged into JNI/native code
- native code:
  - loaded `libQnnTFLiteDelegate.so`
  - loaded `libQnnHtp.so`
  - resolved `tflite_plugin_create_delegate` / destroy symbols
  - created the delegate handle
- Kotlin/TFLite used a custom `Delegate` wrapper to attach the delegate to `Interpreter`

Multiple packaging and loading strategies were tested:

- assets-based `.so` copy into app-internal storage
- packaged `jniLibs/arm64-v8a`
- `ADSP_LIBRARY_PATH` and `LD_LIBRARY_PATH` tuning
- packaged native library probing based on APK contents rather than only extracted files

Additional dependency-chain experiments were also performed:

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

These were integrated through preload logic so that the runtime could attempt to reproduce the DSP/HAL/FastRPC environment from inside the app process.

## 3. Where the Attempt Failed

The implementation eventually reached the following successful milestones:

- QNN availability probe succeeded
- native loading of QNN libraries succeeded
- delegate symbol resolution succeeded
- delegate handle creation succeeded
- preload dependency chain succeeded
- TFLite delegate application phase was reached

However, the final attach still failed during DSP transport and session creation.

The decisive runtime errors were:

- `open_device_node ... Permission denied`
- `unable to acquire dspservice instance`
- `open_hal_session: failed`
- `createUnsignedPD ... not supported by HTP`
- `Transport layer setup failed: 14001`

This means the project did not fail at simple `.so` discovery anymore. It failed at the final DSP/HAL session open step.

## 4. Why the Goal Could Not Be Achieved

The original goal assumed that once the app could correctly package, load, and attach QNN-related libraries, the delegate would eventually work from a normal Android app process.

That assumption turned out to be false on the target device.

The critical finding was:

- QNN HTP worked from `adb shell`
- the same device still rejected final DSP session access from the app process

This strongly suggests that the blocking issue is not primarily delegate code, library loading, or dependency packaging. The real blocker is the execution-context and permission difference between:

- `shell`-side execution
- normal Android app execution

In practice, even after the dependency chain was satisfied, the app process still did not have enough access to open the required DSP/HAL session.

As a result, the project could not achieve its original form of success:

- a normal Android app process directly attaching QNN HTP entirely from inside the app runtime

## 5. How the Failure Was Approached and Mitigated

The failure was not accepted immediately. Several mitigation strategies were explored first.

These included:

- replacing `nativeLibraryDir` file checks with APK-based packaged native library detection
- comparing assets-based and packaged-`jniLibs` approaches
- resolving missing delegate/stub/backend dependencies step by step
- adding `uses-native-library` entries for public vendor native libraries
- reproducing the dependency structure found in working community examples
- preloading HAL/FastRPC/vendor/system dependency chains before delegate creation
- investigating LiteRT and other app-level alternatives
- investigating shell-backed or bridge-backed execution models as future pivots

The result of these attempts was valuable: they removed most uncertainty around packaging and loading, and exposed the actual final bottleneck instead of leaving the system in an ambiguous half-working state.

## 6. What Was Gained Despite the Failure

Although the original target was not achieved, the work produced several meaningful outcomes.

### Problem Isolation Improved Significantly

The work clarified that the core failure is not "QNN does not work on this device" but rather:

- QNN HTP does work on the device
- it works in a `shell` execution context
- it does not complete from a normal app process due to final DSP/HAL access restrictions

This is a much stronger conclusion than a generic delegate failure.

### A Large Portion of the Runtime Stack Was Built

Reusable runtime components were still created:

- QNN availability probe
- JNI/native bridge
- packaged-native-library aware loader
- delegate creation path
- dependency preload framework

Even though the final attach failed, this work remains useful for future backends and pivots.

### The Next Direction Became Clearer

The project now has a more accurate understanding of the landscape:

- adding more `.so` files alone is unlikely to solve the final issue
- direct app-process HTP attach has likely reached a structural limit on this device
- future work should focus on execution-context bridging rather than only dependency packaging

This means the failure produced a clearer basis for a pivot.

## 7. Conclusion

This attempt failed to fully achieve the original goal of direct QNN HTP attach from a normal Android app process.

However, the failure was productive rather than inconclusive.

The project reached all major app-side loading and attach-preparation stages, removed multiple layers of uncertainty, and identified the final blocker as a permission and execution-context limitation rather than a simple implementation bug.

The main lesson is:

- the device supports QNN HTP
- the app can prepare almost the entire stack
- but the last DSP/HAL session boundary is not crossed by a normal app process

As a result, the project should no longer treat this as only a library packaging problem. It should now consider a pivot toward execution-context bridging, shell-backed backends, or other architectures that preserve the app-facing API while moving actual HTP execution outside the standard app process.
