cmake_minimum_required(VERSION 3.20)
project(RainInjectable LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
# Static CRT: rain-payload.dll is loaded into a javaw.exe we do not control, so it
# must not depend on the VC++ redistributable being installed.
set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
set(CMAKE_WINDOWS_EXPORT_ALL_SYMBOLS OFF)

if(NOT WIN32)
    message(FATAL_ERROR "RainInjectable targets Windows only.")
endif()

# Force x64 to match modern Minecraft installs (Win64 launchers ship 64-bit JVMs).
if(NOT CMAKE_GENERATOR_PLATFORM)
    message(STATUS "Defaulting to x64 platform (use -A x64 to be explicit).")
endif()

# ---- Injector executable -------------------------------------------------
add_executable(RainInjectable WIN32
    src/main.cpp
    src/gui.cpp
    src/compatibility.cpp
    src/embedded_payload.cpp
    src/injector.cpp
    src/logger.cpp
    src/process_scanner.cpp
    src/status_protocol.cpp
)
target_include_directories(RainInjectable PRIVATE src)
target_compile_definitions(RainInjectable PRIVATE
    UNICODE _UNICODE WIN32_LEAN_AND_MEAN NOMINMAX)
target_link_libraries(RainInjectable PRIVATE
    user32 gdi32 comctl32 shell32 advapi32 psapi dwmapi gdiplus)

enable_testing()
add_executable(compatibility_tests
    ../tests/compatibility_tests.cpp
    src/compatibility.cpp
    src/status_protocol.cpp)
target_include_directories(compatibility_tests PRIVATE src)
target_compile_definitions(compatibility_tests PRIVATE UNICODE _UNICODE WIN32_LEAN_AND_MEAN NOMINMAX)
add_test(NAME compatibility COMMAND compatibility_tests)
add_executable(ui_layout_tests ../tests/ui_layout_tests.cpp)
target_include_directories(ui_layout_tests PRIVATE src)
target_compile_definitions(ui_layout_tests PRIVATE UNICODE _UNICODE WIN32_LEAN_AND_MEAN NOMINMAX)
add_test(NAME ui_layout COMMAND ui_layout_tests)
add_executable(target_diagnostics
    src/target_diagnostics.cpp
    src/process_scanner.cpp
    src/compatibility.cpp
    src/injector.cpp
    src/status_protocol.cpp
    src/logger.cpp)
target_include_directories(target_diagnostics PRIVATE src)
target_compile_definitions(target_diagnostics PRIVATE UNICODE _UNICODE WIN32_LEAN_AND_MEAN NOMINMAX)
target_link_libraries(target_diagnostics PRIVATE psapi shell32 user32)
if(MSVC)
    target_compile_options(target_diagnostics PRIVATE /W4 /permissive- /utf-8)
endif()

if(MSVC)
    target_compile_options(RainInjectable PRIVATE /W4 /permissive- /Zi /utf-8)
    target_link_options   (RainInjectable PRIVATE /DEBUG /SUBSYSTEM:WINDOWS)
endif()

# ---- Payload DLL ---------------------------------------------------------
# We only need jni.h + jni_md.h, so any JDK works (JDK 8 is fine).
# Resolution order: -DJNI_ROOT=<jdk root>, else $JAVA_HOME, else the first
# Adoptium/Temurin or Oracle JDK 8 install found under Program Files.
if(DEFINED JNI_ROOT)
    set(JAVA_INCLUDE "${JNI_ROOT}/include")
elseif(DEFINED ENV{JAVA_HOME})
    set(JAVA_INCLUDE "$ENV{JAVA_HOME}/include")
else()
    file(GLOB _jdk_candidates
        "C:/Program Files/Eclipse Adoptium/jdk-8*"
        "C:/Program Files/Java/jdk1.8*")
    if(_jdk_candidates)
        list(GET _jdk_candidates 0 _jdk_root)
        set(JAVA_INCLUDE "${_jdk_root}/include")
    else()
        message(FATAL_ERROR
            "Could not locate a JDK. Pass -DJNI_ROOT=<jdk root>, set JAVA_HOME, "
            "or install a JDK 8 (any JDK works — only jni.h is needed).")
    endif()
endif()
if(NOT EXISTS "${JAVA_INCLUDE}/jni.h")
    message(FATAL_ERROR
        "Could not find jni.h under ${JAVA_INCLUDE}. "
        "JNI_ROOT/JAVA_HOME must point at a JDK (not a JRE) install root.")
endif()

add_library(rain_payload SHARED
    payload/src/dllmain.cpp
    payload/src/jvm_loader.cpp
)
target_include_directories(rain_payload PRIVATE
    payload/src
    "${JAVA_INCLUDE}"
    "${JAVA_INCLUDE}/win32"
)
target_compile_definitions(rain_payload PRIVATE
    UNICODE _UNICODE WIN32_LEAN_AND_MEAN NOMINMAX)
target_link_libraries(rain_payload PRIVATE user32 psapi)
set_target_properties(rain_payload PROPERTIES OUTPUT_NAME "rain-payload" PREFIX "")

if(MSVC)
    target_compile_options(rain_payload PRIVATE /W4 /permissive- /Zi /utf-8)
    target_link_options   (rain_payload PRIVATE /DEBUG)
endif()

if(NOT DEFINED RUNTIME_JAR OR NOT EXISTS "${RUNTIME_JAR}")
    message(FATAL_ERROR "Pass -DRUNTIME_JAR=<built rain-runtime.jar> to embed the Java runtime.")
endif()
if(NOT DEFINED BADLION_JAR OR NOT EXISTS "${BADLION_JAR}")
    message(FATAL_ERROR "Pass -DBADLION_JAR=<built rain-badlion.jar> to embed the Badlion runtime.")
endif()
add_custom_command(
    OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/embedded_payload.rc"
    COMMAND ${CMAKE_COMMAND}
        "-DPAYLOAD_DLL=$<TARGET_FILE:rain_payload>"
        "-DRUNTIME_JAR=${RUNTIME_JAR}"
        "-DBADLION_JAR=${BADLION_JAR}"
        "-DOUTPUT_RC=${CMAKE_CURRENT_BINARY_DIR}/embedded_payload.rc"
        -P "${CMAKE_CURRENT_SOURCE_DIR}/embed-resources.cmake"
    DEPENDS rain_payload "${RUNTIME_JAR}" "${BADLION_JAR}" "${CMAKE_CURRENT_SOURCE_DIR}/embed-resources.cmake"
    VERBATIM
)
target_sources(RainInjectable PRIVATE "${CMAKE_CURRENT_BINARY_DIR}/embedded_payload.rc")
add_dependencies(RainInjectable rain_payload)

# ---- Stage everything next to the exe ------------------------------------
add_custom_command(TARGET RainInjectable POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
        "$<TARGET_FILE:rain_payload>"
        "$<TARGET_FILE_DIR:RainInjectable>/rain-payload.dll"
    COMMENT "Copying rain-payload.dll next to RainInjectable.exe")
