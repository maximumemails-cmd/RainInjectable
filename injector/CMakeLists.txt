cmake_minimum_required(VERSION 3.20)
project(LionInjectable LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_WINDOWS_EXPORT_ALL_SYMBOLS OFF)

if(NOT WIN32)
    message(FATAL_ERROR "LionInjectable targets Windows only.")
endif()

# Force x64 to match modern Minecraft installs (Win64 launchers ship 64-bit JVMs).
if(NOT CMAKE_GENERATOR_PLATFORM)
    message(STATUS "Defaulting to x64 platform (use -A x64 to be explicit).")
endif()

# ---- Injector executable -------------------------------------------------
add_executable(LionInjectable WIN32
    src/main.cpp
    src/gui.cpp
    src/injector.cpp
    src/logger.cpp
    src/process_scanner.cpp
)
target_include_directories(LionInjectable PRIVATE src)
target_compile_definitions(LionInjectable PRIVATE
    UNICODE _UNICODE WIN32_LEAN_AND_MEAN NOMINMAX)
target_link_libraries(LionInjectable PRIVATE
    user32 gdi32 comctl32 shell32 advapi32 psapi dwmapi gdiplus)

if(MSVC)
    target_compile_options(LionInjectable PRIVATE /W4 /permissive- /Zi /utf-8)
    target_link_options   (LionInjectable PRIVATE /DEBUG /SUBSYSTEM:WINDOWS)
endif()

# ---- Payload DLL ---------------------------------------------------------
# Requires JAVA_HOME to point at a JDK (we need jni.h + jni_md.h).
if(NOT DEFINED ENV{JAVA_HOME})
    message(FATAL_ERROR
        "JAVA_HOME environment variable is not set — required so we can find jni.h.\n"
        "Install a JDK 17+ and set JAVA_HOME to its install root.")
endif()
set(JAVA_INCLUDE "$ENV{JAVA_HOME}/include")
if(NOT EXISTS "${JAVA_INCLUDE}/jni.h")
    message(FATAL_ERROR "Could not find jni.h under ${JAVA_INCLUDE}. Is JAVA_HOME a JDK (not a JRE)?")
endif()

add_library(payload SHARED
    payload/src/dllmain.cpp
    payload/src/jvm_loader.cpp
    payload/src/native_bridge.cpp
    payload/src/agent_attach.cpp
)
target_include_directories(payload PRIVATE
    payload/src
    "${JAVA_INCLUDE}"
    "${JAVA_INCLUDE}/win32"
)
target_compile_definitions(payload PRIVATE
    UNICODE _UNICODE WIN32_LEAN_AND_MEAN NOMINMAX)
target_link_libraries(payload PRIVATE user32 psapi)
set_target_properties(payload PROPERTIES OUTPUT_NAME "payload" PREFIX "")

if(MSVC)
    target_compile_options(payload PRIVATE /W4 /permissive- /Zi /utf-8)
    target_link_options   (payload PRIVATE /DEBUG)
endif()

# ---- Stage everything next to the exe ------------------------------------
add_custom_command(TARGET LionInjectable POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
        "$<TARGET_FILE:payload>"
        "$<TARGET_FILE_DIR:LionInjectable>/payload.dll"
    COMMENT "Copying payload.dll next to LionInjectable.exe")
