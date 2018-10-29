set(pathToProject C:/Users/homer/Desktop/HumanDetection/UAVsApp/android-videostreamdecodingsample)
set(pathToOpenCv C:/OpenCV-android-sdk)
# Sets the minimum version of CMake required to build your native library.
# This ensures that a certain set of CMake features is available to
# your build.
cmake_minimum_required(VERSION 3.4.1)


set(CMAKE_VERBOSE_MAKEFILE on)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=gnu++11")

include_directories(${pathToOpenCv}/sdk/native/jni/include)

add_library( lib_opencv SHARED IMPORTED )



# Specifies a library name, specifies whether the library is STATIC or
# SHARED, and provides relative paths to the source code. You can
# define multiple libraries by adding multiple add.library() commands,
# and CMake builds them for you. When you build your app, Gradle
# automatically packages shared libraries with your APK.
add_library( # Specifies the name of the library.
             native-lib
             # Sets the library as a shared library.
             SHARED
             # Provides a relative path to your source file(s).
             src/main/cpp/native-lib.cpp )
include_directories(src/main/cpp/include/)
find_library( # Defines the name of the path variable that stores the
              # location of the NDK library.
              log-lib

              # Specifies the name of the NDK library that
              # CMake needs to locate.
              log )
# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
                       native-lib
                       -ljnigraphics

                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib} )
set_target_properties(lib_opencv PROPERTIES IMPORTED_LOCATION ${pathToProject}/src/main/jni/${ANDROID_ABI}/libopencv_java3.so)
target_link_libraries( native-lib $\{log-lib} lib_opencv)